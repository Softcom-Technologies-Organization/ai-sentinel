"""
PII Detection gRPC Service with Improved Memory Management.

This module implements the gRPC service for PII detection with optimizations
for memory usage when processing large volumes of data.
"""

import gc
import logging
import os
import threading
import time
from concurrent import futures
from typing import Dict, List, Optional, Tuple

import grpc
import psutil
# Import gRPC reflection for service discovery
from grpc_reflection.v1alpha import reflection

# Import DetectorSource for mapping
from pii_detector.domain.entity.detector_source import DetectorSource
# Import PIIType for proper normalization
from pii_detector.domain.entity.pii_type import PIIType
# Import the generated gRPC code
from pii_detector.proto.generated import pii_detection_pb2, \
    pii_detection_pb2_grpc

# Composite detector (Regex + Presidio + Ministral)
try:
    from pii_detector.application.orchestration.composite_detector import (
        CompositePIIDetector,
        create_composite_detector,
        should_use_composite_detector
    )
except Exception:  # pragma: no cover - safe import guard
    CompositePIIDetector = None  # type: ignore
    create_composite_detector = None  # type: ignore
    should_use_composite_detector = None  # type: ignore

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Singleton instance for the PII detector
_detector_instance = None
_detector_lock = threading.Lock()

def get_detector_instance():
    """Get or create a singleton instance of PIIDetector."""
    global _detector_instance
    if _detector_instance is None:
        with _detector_lock:
            if _detector_instance is None:
                _initialize_detector_instance()
    return _detector_instance


def _initialize_detector_instance():
    """Initialize the global composite detector instance."""
    global _detector_instance

    _detector_instance = _create_composite_detector()

    _detector_instance.download_model()
    _detector_instance.load_model()
    logger.info("Singleton PII detector initialized successfully")


def _create_composite_detector():
    """Create and return the composite detector instance (Regex + Presidio + Ministral)."""
    composite = create_composite_detector()
    logger.info("Composite detector (Regex + Presidio + Ministral) enabled")
    return composite


# Filter-reason sentinel flagging a dropped open-vocabulary MINISTRAL label with
# no pii_type_config row, reported in the debug breakdown of filter reasons.
_UNKNOWN_MINISTRAL_LABEL_REASON = "unknown_ministral_label"


class PIIDetectionServicer(pii_detection_pb2_grpc.PIIDetectionServiceServicer):
    """
    Implementation of the PIIDetectionService gRPC service with memory management.
    """

    def __init__(self, max_text_size=1_000_000, enable_memory_monitoring=True):
        """
        Initialize the service with memory management features.
        
        Args:
            max_text_size: Maximum size of text to process in a single request (in characters)
            enable_memory_monitoring: Enable periodic memory monitoring
        """
        self.max_text_size = max_text_size
        self.enable_memory_monitoring = enable_memory_monitoring
        self.request_counter = 0
        self.gc_frequency = 10  # Run garbage collection every N requests
        
        # Load throughput logging configuration
        self.log_throughput = self._load_log_throughput_config()

        # Use singleton detector instance
        self.detector = get_detector_instance()

        # Auto-tune the Ministral chunk-prompt concurrency once, here in the parent
        # process, BEFORE the worker pool forks. This servicer is constructed a
        # single time at server start, so the bench runs exactly once and never
        # contends with a real scan. Placing it in _initialize_detector_instance
        # would re-run it in every spawn-mode worker.
        self._run_startup_concurrency_autotune()

        # Optional inference worker pool (env PII_WORKER_PROCESSES > 1).
        # N worker processes give data parallelism across concurrent requests.
        # The pool is created after the singleton detector above is built so
        # workers inherit it.
        self._worker_pool = None
        self._init_worker_pool()

        # Background poller for operator-triggered (on-demand) concurrency
        # benchmarks. Started AFTER the worker pool forks so the thread lives in
        # the parent only (never duplicated into pool workers).
        self._start_bench_poller()

        # Start memory monitoring thread if enabled
        if self.enable_memory_monitoring:
            self._start_memory_monitoring()

    def _start_bench_poller(self) -> None:
        """Start the daemon thread that runs on-demand concurrency benchmarks.

        Skipped when disabled by env or when no DB credentials are configured
        (nothing to poll, and it keeps unit tests that construct the servicer
        free of a background DB-polling thread).
        """
        enabled = os.getenv("PII_BENCH_POLLER_ENABLED", "true").strip().lower()
        if enabled not in ("1", "true", "yes", "on"):
            logger.info("On-demand benchmark poller disabled via env")
            return
        if not (os.getenv("DB_USER") and os.getenv("DB_PASSWORD")):
            logger.info(
                "On-demand benchmark poller not started (no DB credentials)"
            )
            return
        thread = threading.Thread(target=self._bench_poller_loop, daemon=True)
        thread.start()
        logger.info("On-demand concurrency benchmark poller started")

    def _bench_poller_loop(self) -> None:
        """Poll the DB for a benchmark request and run it, updating progress.

        The request flag is claimed atomically (single conditional UPDATE) so a
        request runs exactly once. Progress/status are written to the DB as the
        bench advances; the UI polls them for its progress bar. Fully guarded so
        a transient DB/HTTP error never kills the poller.
        """
        from pii_detector.infrastructure.adapter.out.database_config_adapter import (
            get_database_config_adapter,
        )
        from pii_detector.infrastructure.model_management.concurrency_autotuner import (
            run_ondemand_autotune,
        )

        poll_seconds = int(os.getenv("PII_BENCH_POLL_SECONDS", "3"))
        adapter = get_database_config_adapter()
        while True:
            try:
                max_concurrency = adapter.claim_bench_job()
                if max_concurrency is not None:
                    logger.info(
                        "[AUTOTUNE] on-demand benchmark requested (up to C=%d); running",
                        max_concurrency,
                    )
                    outcome = run_ondemand_autotune(
                        self.detector,
                        on_progress=adapter.update_bench_progress,
                        max_concurrency=max_concurrency,
                        should_stop=adapter.is_bench_cancel_requested,
                    )
                    if outcome.ran and outcome.chosen is not None:
                        adapter.complete_bench_job(outcome.chosen, outcome.signature)
                        logger.info(
                            "[AUTOTUNE] on-demand benchmark done: concurrency=%d",
                            outcome.chosen,
                        )
                    elif outcome.reason == "cancelled":
                        adapter.cancel_bench_job()
                        logger.info("[AUTOTUNE] on-demand benchmark cancelled by operator")
                    else:
                        adapter.fail_bench_job(f"Benchmark failed: {outcome.reason}")
                        logger.warning(
                            "[AUTOTUNE] on-demand benchmark failed: %s", outcome.reason
                        )
            except Exception:  # pragma: no cover - defensive
                logger.exception("[AUTOTUNE] bench poller iteration failed")
            time.sleep(poll_seconds)

    def _init_worker_pool(self) -> None:
        """Create and warm up the inference worker pool when enabled by env."""
        try:
            from pii_detector.infrastructure.model_management.detector_worker_pool import (
                DetectorWorkerPool,
                pool_size_from_env,
            )

            pool_size = pool_size_from_env()
            if pool_size <= 1:
                return
            torch_threads = int(os.getenv('TORCH_NUM_THREADS', '1'))
            self._worker_pool = DetectorWorkerPool(pool_size, torch_threads)
            self._worker_pool.warm_up()
            logger.info(
                "Inference worker pool active: %d processes x %d torch threads",
                pool_size, torch_threads)
        except Exception:
            # Defensive: a pool failure must never prevent the service from
            # starting — fall back to the historical single-detector path.
            logger.exception(
                "Failed to start inference worker pool; falling back to "
                "in-process detection")
            self._worker_pool = None
    
    def _run_startup_concurrency_autotune(self) -> None:
        """Micro-benchmark the LM Studio endpoint and persist the best concurrency.

        Fully guarded: any failure (import, DB, HTTP) is swallowed so a tuning
        problem never prevents the service from starting. The heavy lifting and
        all skip/fail-open logic live in the concurrency_autotuner module.
        """
        try:
            from pii_detector.infrastructure.model_management.concurrency_autotuner import (
                run_startup_autotune,
            )

            run_startup_autotune(self.detector)
        except Exception:  # pragma: no cover - defensive
            logger.warning(
                "Concurrency auto-tune skipped (unexpected error)", exc_info=True
            )

    def _load_log_throughput_config(self) -> bool:
        """
        Load log_throughput flag from configuration.
        
        Returns:
            True if throughput logging is enabled, False otherwise
        """
        try:
            from pii_detector.application.config.detection_policy import _load_llm_config
            config = _load_llm_config()
            detection_config = config.get("detection", {})
            return detection_config.get("log_throughput", True)
        except Exception:
            return True  # Default: enabled
    
    def _start_memory_monitoring(self):
        """Start a background thread to monitor memory usage."""
        monitor_thread = threading.Thread(target=self._monitor_memory_loop, daemon=True)
        monitor_thread.start()
        logger.info("Memory monitoring thread started")
    
    def _monitor_memory_loop(self):
        """Main loop for memory monitoring thread."""
        while True:
            try:
                self._check_and_log_memory()
                time.sleep(30)  # Check every 30 seconds
            except Exception as e:
                logger.exception(f"Error in memory monitoring: {str(e)}")
                time.sleep(30)
    
    def _check_and_log_memory(self):
        """Check current memory usage and log with appropriate level."""
        process = psutil.Process(os.getpid())
        memory_info = process.memory_info()
        memory_percent = process.memory_percent()
        
        memory_mb = memory_info.rss / 1024 / 1024
        logger.info(f"Memory usage: {memory_mb:.2f} MB ({memory_percent:.1f}%)")
        
        if memory_percent > 80:
            self._handle_high_memory(memory_percent)
    
    def _handle_high_memory(self, memory_percent: float):
        """Handle high memory usage by triggering garbage collection."""
        logger.warning(f"High memory usage detected: {memory_percent:.1f}%")
        gc.collect()
        logger.info("Forced garbage collection completed")

    def _process_in_chunks(self, content, threshold):
        """
        Process large text in chunks to avoid memory spikes.
        
        Args:
            content: The text to process
            threshold: Detection threshold
            
        Returns:
            Combined results from all chunks
        """
        chunk_size = 50000
        overlap = 100
        all_entities = []
        offset = 0
        
        while offset < len(content):
            chunk_entities = self._process_single_chunk(
                content, offset, chunk_size, overlap, threshold
            )
            all_entities.extend(chunk_entities)
            offset += chunk_size
            self._perform_chunk_gc_if_needed(offset, len(content))
        
        return all_entities

    def _process_single_chunk(
        self, content: str, offset: int, chunk_size: int, overlap: int, threshold: float
    ) -> List:
        """Process a single chunk of content and return adjusted entities."""
        chunk_start, chunk_end = self._calculate_chunk_boundaries(
            content, offset, chunk_size, overlap
        )
        chunk = content[chunk_start:chunk_end]
        
        raw_entities = self.detector.detect_pii(chunk, threshold)
        return self._filter_and_adjust_entities(raw_entities, chunk_start, offset, overlap)

    def _calculate_chunk_boundaries(
        self, content: str, offset: int, chunk_size: int, overlap: int
    ) -> tuple[int, int]:
        """Calculate start and end boundaries for a chunk."""
        chunk_start = max(0, offset - overlap if offset > 0 else 0)
        chunk_end = min(len(content), offset + chunk_size)
        return chunk_start, chunk_end

    def _filter_and_adjust_entities(
        self, entities: List, chunk_start: int, offset: int, overlap: int
    ) -> List:
        """Filter entities in overlap region and adjust their positions."""
        adjusted_entities = []
        overlap_threshold = overlap if offset > 0 else 0
        
        for entity in entities:
            if self._should_include_entity(entity, overlap_threshold):
                adjusted_entity = self._adjust_entity_position(entity, chunk_start)
                adjusted_entities.append(adjusted_entity)
        
        return adjusted_entities

    def _should_include_entity(self, entity, overlap_threshold: int) -> bool:
        """Check if entity should be included (not in overlap region)."""
        start_in_chunk = entity.start if hasattr(entity, 'start') else entity['start']
        return start_in_chunk >= overlap_threshold

    def _adjust_entity_position(self, entity, chunk_start: int):
        """Adjust entity position based on chunk offset."""
        if hasattr(entity, 'start') and hasattr(entity, 'end'):
            entity.start += chunk_start
            entity.end += chunk_start
        else:
            entity['start'] += chunk_start
            entity['end'] += chunk_start
        return entity

    def _perform_chunk_gc_if_needed(self, current_offset: int, content_length: int) -> None:
        """Perform garbage collection if more chunks remain."""
        if current_offset < content_length:
            gc.collect(0)

    def DetectPII(self, request, context):
        """Implement the DetectPII RPC method with memory management.
        
        Business process:
        1. Validate and extract request parameters
        2. Fetch dynamic configuration from database if requested
        3. Execute PII detection on content with dynamic detector activation
        4. Apply PII type-specific filtering and thresholds
        5. Build response with entities, nbOfDetectedPIIBySeverity, and masked content
        6. Handle errors and cleanup
        
        Args:
            request: gRPC request containing content, threshold, and fetch_config_from_db flag
            context: gRPC context for setting response codes
            
        Returns:
            PIIDetectionResponse with detected entities and nbOfDetectedPIIBySeverity
        """
        start_time = time.time()
        request_id = self._generate_request_id(start_time)
        pii_type_configs = None
        detector_flags = None

        try:
            self.request_counter += 1
            content, threshold = self._extract_and_validate_request(request, context, request_id)

            if content is None:
                return pii_detection_pb2.PIIDetectionResponse()

            # Fetch dynamic configuration from database if requested
            if request.fetch_config_from_db:
                threshold, pii_type_configs, detector_flags = self._fetch_and_apply_config(threshold, request_id)

            # Phase 1: detection (composite detector).
            detection_start = time.monotonic()
            entities, detector_stats = self._execute_detection(
                content, threshold, request_id, detector_flags, pii_type_configs
            )
            logger.info(
                "[FINDING_TRACKER] [%s] step=GRPC_AFTER_DETECTION count=%d",
                request_id, len(entities),
            )
            self._log_throughput(
                "detection",
                request_id=request_id,
                chars=len(content),
                duration_s=time.monotonic() - detection_start,
                entities_in=len(entities),
            )

            # Apply PII type-specific filtering if configs were fetched
            entities_before_type_filter = len(entities)
            if pii_type_configs:
                entities = self._filter_entities_by_type_config(
                    entities, pii_type_configs, request_id,
                )
            logger.info(
                "[FINDING_TRACKER] [%s] step=GRPC_AFTER_TYPE_CONFIG_FILTER in=%d out=%d dropped=%d",
                request_id, entities_before_type_filter, len(entities),
                entities_before_type_filter - len(entities),
            )

            # Phase 1bis: deterministic precision post-filter, the final
            # filtering stage of the pipeline. It rejects technical artefacts
            # cross-label (UUID, ObjectId, digests, versions...) plus the
            # mechanically-impossible findings per pii_type (checksum / parse
            # failures). Only invoked when the DB flag is ON (zero-overhead path
            # otherwise). Rejections are exposed in the discarded_entities
            # channel.
            discarded_by_prefilter = []
            if self._is_postfilter_enabled(detector_flags):
                entities_before_prefilter = len(entities)
                prefilter_start = time.monotonic()
                entities, discarded_by_prefilter = self._apply_format_postfilter(
                    entities, content, request_id
                )
                prefilter_elapsed = time.monotonic() - prefilter_start
                self._log_throughput(
                    "format_prefilter",
                    request_id=request_id,
                    chars=len(content),
                    duration_s=prefilter_elapsed,
                    entities_in=entities_before_prefilter,
                    entities_kept=len(entities),
                    entities_rejected=entities_before_prefilter - len(entities),
                )
                # Surface the pre-filter as a pseudo-detector so callers can see
                # how many PII it discarded. entities_found = examined count,
                # entities_discarded = rejected count. Only when it examined > 0.
                if entities_before_prefilter > 0:
                    detector_stats.append({
                        "source": DetectorSource.POSTFILTER,
                        "duration_ms": int(prefilter_elapsed * 1000),
                        "entities_found": entities_before_prefilter,
                        "entities_discarded": len(discarded_by_prefilter),
                    })

            # Phase 2: total elapsed time (chars/sec end-to-end).
            self._log_throughput(
                "total",
                request_id=request_id,
                chars=len(content),
                duration_s=time.monotonic() - detection_start,
                entities_final=len(entities),
            )

            response = self._build_detection_response(
                content, entities, request_id,
                discarded_by_prefilter,
                detector_stats,
            )
            logger.info(
                "[FINDING_TRACKER] [%s] step=GRPC_FINAL_RESPONSE count=%d",
                request_id, len(response.entities) if hasattr(response, "entities") else len(entities),
            )
            self._log_request_completion(request_id, start_time)
            self._perform_periodic_gc()

            return response
            
        except Exception as e:
            return self._handle_detection_error(e, request_id, start_time, context)
        finally:
            self._cleanup_request_resources(request_id, start_time)

    def CheckDetectorsHealth(self, request, context):
        """Implement the CheckDetectorsHealth RPC method.

        Business process:
        1. Read the enabled detectors and the LM Studio endpoint from the database
        2. Ask the composite detector whether each enabled detector can run
        3. Return one entry per enabled detector

        The probe deliberately runs here rather than in the backend: the LM Studio
        host/port are configured from THIS service's network standpoint, so probing
        anywhere else could report a reachable endpoint the detectors cannot
        actually use.

        Returns:
            DetectorsHealthResponse with one DetectorHealth per enabled detector,
            empty when the configuration cannot be read.
        """
        request_id = self._generate_request_id(time.time())
        response = pii_detection_pb2.DetectorsHealthResponse()

        detector_flags = self._fetch_detector_flags(request_id)
        if detector_flags is None:
            # Claiming every detector is healthy would be a lie, and claiming they
            # are all down would block scans over a configuration read failure the
            # detection path already reports on its own. Report nothing instead.
            logger.warning(
                f"[{request_id}] Detector health check skipped: "
                "detector configuration unavailable"
            )
            return response

        # A probe failure must not escape as a gRPC error: the backend treats an
        # unusable health check as "cannot tell" and starts the scan anyway, so an
        # escaping exception would buy nothing while losing the reason. Report
        # nothing, exactly like an unreadable configuration above.
        probe = getattr(self.detector, "check_detectors_health", None)
        if probe is None:
            logger.warning(
                f"[{request_id}] Detector health check skipped: "
                "detector does not support health probing"
            )
            return response
        try:
            probed = probe(
                enable_regex=detector_flags.get('regex_enabled'),
                enable_presidio=detector_flags.get('presidio_enabled'),
                enable_ministral=detector_flags.get('ministral_enabled'),
                lm_studio_host=detector_flags.get('lm_studio_host'),
                lm_studio_port=detector_flags.get('lm_studio_port'),
            )
        except Exception as exc:
            logger.exception(
                f"[{request_id}] Detector health check failed: "
                f"{type(exc).__name__}: {exc}"
            )
            return response

        for health in probed:
            entry = response.detectors.add()
            source = health["source"]
            entry.source = getattr(
                pii_detection_pb2.DetectorSource, source.name,
                pii_detection_pb2.DetectorSource.UNKNOWN_SOURCE,
            )
            entry.reachable = bool(health["reachable"])
            entry.endpoint = str(health.get("endpoint") or "")
            entry.error = str(health.get("error") or "")
            entry.error_code = str(health.get("error_code") or "")
            for name, value in (health.get("error_params") or {}).items():
                entry.error_params[str(name)] = str(value)

        unreachable = sum(1 for entry in response.detectors if not entry.reachable)
        logger.info(
            f"[{request_id}] Detector health: {len(response.detectors)} enabled, "
            f"{unreachable} unreachable"
        )
        return response

    def _generate_request_id(self, start_time: float) -> str:
        """Generate unique request identifier for logging.
        
        Args:
            start_time: Request start timestamp
            
        Returns:
            Unique request ID string
        """
        return f"req_{self.request_counter + 1}_{int(start_time * 1000) % 10000}"

    def _extract_and_validate_request(self, request, context, request_id: str):
        """Extract and validate request parameters with comprehensive logging.
        
        Business rules:
        - Content cannot be empty
        - Content size must not exceed configured maximum
        - Threshold defaults to 0.5 if not specified or invalid
        
        Args:
            request: gRPC request object
            context: gRPC context for error reporting
            request_id: Request identifier for logging
            
        Returns:
            Tuple of (content, threshold) or (None, None) if validation fails
        """
        content = request.content
        threshold = request.threshold if request.threshold > 0 else 0.5
        
        self._log_request_info(request_id, content, threshold, context)
        
        validation_error = self._validate_content(content, request_id)
        if validation_error:
            self._set_validation_error(context, validation_error, request_id)
            return None, None
        
        return content, threshold

    def _log_request_info(self, request_id: str, content: str, threshold: float, context) -> None:
        """Log incoming request information for debugging and monitoring.
        
        Args:
            request_id: Request identifier
            content: Request content
            threshold: Detection threshold
            context: gRPC context for peer information
        """
        peer_info = context.peer() if hasattr(context, 'peer') else "unknown"
        
        logger.debug(f"[{request_id}] Received DetectPII request #{self.request_counter}")
        logger.debug(f"[{request_id}] Client: {peer_info}")
        logger.info(f"[{request_id}] gRPC content length: {len(content)} chars, threshold={threshold}")

    def _fetch_and_apply_config(self, default_threshold: float, request_id: str) -> tuple[float, Optional[dict], Optional[dict]]:
        """
        Fetch configuration from database and apply to current detection.

        Business rule: Configuration is fetched at scan start to ensure
        consistency throughout the entire scan. Detector flags are applied
        dynamically without service restart.

        Args:
            default_threshold: Default threshold to use if fetch fails
            request_id: Request identifier for logging

        Returns:
            Tuple of (threshold, pii_type_configs, detector_flags) where:
            - threshold: Default threshold value to use for detection
            - pii_type_configs: Dictionary of PII type configs or None
            - detector_flags: Dictionary with presidio_enabled, regex_enabled,
              ministral_enabled, ministral_chunk_size/overlap, postfilter_enabled or None
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import (
                get_database_config_adapter,
            )
            
            logger.debug(f"[{request_id}] Fetching config from database...")
            adapter = get_database_config_adapter()
            db_config = adapter.fetch_config()
            
            # Fetch PII type-specific configs
            pii_type_configs = adapter.fetch_pii_type_configs()
            
            if db_config is None:
                logger.debug(
                    f"[{request_id}] Using default threshold {default_threshold} "
                    "(database config not available)"
                )
                return default_threshold, pii_type_configs, None

            # Extract threshold from database config
            threshold = float(db_config.get('default_threshold', default_threshold))

            detector_flags = self._extract_detector_flags(db_config)

            logger.info(
                f"[{request_id}] Applied database config: threshold={threshold}, "
                f"presidio={detector_flags['presidio_enabled']}, "
                f"regex={detector_flags['regex_enabled']}, "
                f"ministral={detector_flags['ministral_enabled']}, "
                f"postfilter={detector_flags['postfilter_enabled']}, "
                f"lm_studio={detector_flags['lm_studio_host']}:{detector_flags['lm_studio_port']}"
            )

            if pii_type_configs:
                logger.debug(
                    f"[{request_id}] Loaded {len(pii_type_configs)} PII type-specific configs"
                )

            return threshold, pii_type_configs, detector_flags
            
        except Exception as e:
            logger.warning(
                f"[{request_id}] Failed to fetch database config: {e}. "
                f"Using default threshold {default_threshold}"
            )
            return default_threshold, None, None

    @staticmethod
    def _extract_detector_flags(db_config: dict) -> dict:
        """Map a ``pii_detection_config`` row to the detector activation flags.

        Returns:
            Dictionary with presidio_enabled, regex_enabled, ministral_enabled,
            the Ministral chunking/concurrency knobs, the LM Studio endpoint and
            postfilter_enabled.
        """
        return {
            'presidio_enabled': db_config.get('presidio_enabled', True),
            'regex_enabled': db_config.get('regex_enabled', False),
            'ministral_enabled': db_config.get('ministral_enabled', False),
            # Ministral-PII chunking knobs, threaded by _build_detection_kwargs
            # into the composite's detect_pii_with_stats and routed on to the
            # Ministral detector's detect_pii.
            'ministral_chunk_size': db_config.get('ministral_chunk_size'),
            'ministral_overlap': db_config.get('ministral_overlap'),
            'postfilter_enabled': db_config.get('postfilter_enabled', False),
            # LM Studio endpoint (host/port) serving the Ministral-PII model,
            # threaded by _build_detection_kwargs into the composite and routed
            # on to the Ministral detector so an operator can retarget the
            # endpoint without a service restart. Re-read on every scan/resume
            # (fetch_config_from_db is set per request by the backend).
            'lm_studio_host': db_config.get('lm_studio_host'),
            'lm_studio_port': db_config.get('lm_studio_port'),
            # Number of chunk prompts the Ministral detector sends to LM Studio
            # concurrently (DB column ministral_concurrency, auto-tuned at
            # startup). Threaded by _build_detection_kwargs into the composite
            # and on to the Ministral detector's chunk loop.
            'ministral_concurrency': db_config.get('ministral_concurrency'),
        }

    def _fetch_detector_flags(self, request_id: str) -> Optional[dict]:
        """Read only the detector activation flags from the database.

        Lighter counterpart of :meth:`_fetch_and_apply_config` for the health
        pre-flight, which needs the flags and the LM Studio endpoint but not the
        per-type configs. Returns ``None`` when the configuration is unreadable.
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import (
                get_database_config_adapter,
            )

            db_config = get_database_config_adapter().fetch_config()
            if db_config is None:
                return None
            return self._extract_detector_flags(db_config)
        except Exception as e:
            logger.warning(
                f"[{request_id}] Failed to fetch detector flags from database: {e}"
            )
            return None

    def _validate_content(self, content: str, request_id: str) -> Optional[str]:
        """Validate content against business rules.
        
        Args:
            content: Content to validate
            request_id: Request identifier for logging
            
        Returns:
            Error message if validation fails, None otherwise
        """
        if not content:
            return "Content cannot be empty"
        
        if len(content) > self.max_text_size:
            return f"Content too large: {len(content)} characters (max: {self.max_text_size})"
        
        return None

    def _set_validation_error(self, context, error_message: str, request_id: str) -> None:
        """Set validation error in gRPC context and log.
        
        Args:
            context: gRPC context
            error_message: Error description
            request_id: Request identifier for logging
        """
        logger.error(f"[{request_id}] Validation failed: {error_message}")
        context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
        context.set_details(error_message)

    def _execute_detection(
        self,
        content: str,
        threshold: float,
        request_id: str,
        detector_flags: Optional[dict] = None,
        pii_type_configs: Optional[Dict] = None,
    ) -> Tuple[List, List[Dict]]:
        """Execute PII detection with dynamic detector activation and log performance metrics.

        Business rule: Detector activation flags from database override default configuration
        to enable runtime reconfiguration without service restart.

        Fresh PII type configs are passed to the Presidio detector to avoid
        stale config cache issues when database configurations change.

        Args:
            content: Text to analyze
            threshold: Detection confidence threshold
            request_id: Request identifier for logging
            detector_flags: Optional dict with presidio_enabled, regex_enabled, ministral_enabled
            pii_type_configs: Optional fresh PII type configs from database for Presidio

        Returns:
            Tuple of (detected PII entities, per-detector run stats). The stats
            list is empty when the active detector path does not produce them,
            keeping ``detector_stats`` empty and the response backward-compatible.
        """
        processing_start = time.time()
        logger.debug("[%s] Starting PII detection processing...", request_id)

        self._pass_fresh_configs_to_presidio(pii_type_configs, request_id)

        call_kwargs = self._build_detection_kwargs(
            detector_flags, pii_type_configs, request_id
        )
        if self._worker_pool is not None:
            # Delegate the CPU-bound inference to a pool worker; this gRPC
            # thread just blocks on the result, so K concurrent requests run
            # on K worker processes in parallel. Stats travel back through the
            # process boundary (picklable dicts + DetectorSource Enum).
            entities, detector_stats = self._worker_pool.detect_with_stats(
                content, threshold, call_kwargs
            )
        elif hasattr(self.detector, 'detect_pii_with_stats'):
            # In-process composite path: stats are returned by value (no shared
            # mutable instance state), safe under concurrent gRPC threads.
            entities, detector_stats = self.detector.detect_pii_with_stats(
                content, threshold, **call_kwargs
            )
        else:
            entities = self.detector.detect_pii(content, threshold, **call_kwargs)
            detector_stats = []

        processing_time = time.time() - processing_start
        self._log_detection_metrics(request_id, content, entities, processing_time)
        self._log_detected_entities(request_id, entities)

        return entities, detector_stats

    def _build_detection_kwargs(
        self,
        detector_flags: Optional[dict],
        pii_type_configs: Optional[Dict],
        request_id: str
    ) -> dict:
        """Build keyword arguments for detect_pii based on detector capabilities."""
        if not hasattr(self.detector, 'detect_pii'):
            return {}

        import inspect
        sig = inspect.signature(self.detector.detect_pii)
        kwargs = {}

        if 'pii_type_configs' in sig.parameters:
            kwargs['pii_type_configs'] = pii_type_configs

        if detector_flags and 'enable_presidio' in sig.parameters:
            logger.debug(
                f"[{request_id}] Applying dynamic detector flags: "
                f"Presidio={detector_flags.get('presidio_enabled')}, "
                f"Regex={detector_flags.get('regex_enabled')}, "
                f"Ministral={detector_flags.get('ministral_enabled')}"
            )
            kwargs['enable_presidio'] = detector_flags.get('presidio_enabled')
            kwargs['enable_regex'] = detector_flags.get('regex_enabled')
            self._add_supported_ministral_kwargs(kwargs, sig, detector_flags)

        return kwargs

    @staticmethod
    def _add_supported_ministral_kwargs(kwargs: dict, sig, detector_flags: dict) -> None:
        """Forward the Ministral knobs the detector signature actually accepts."""
        if 'enable_ministral' in sig.parameters:
            kwargs['enable_ministral'] = detector_flags.get('ministral_enabled')
        # Ministral-PII chunking knobs (DB columns ministral_chunk_size /
        # ministral_overlap) forwarded so operator-configured values reach
        # the detector.
        if 'ministral_chunk_size' in sig.parameters:
            kwargs['ministral_chunk_size'] = detector_flags.get('ministral_chunk_size')
        if 'ministral_overlap' in sig.parameters:
            kwargs['ministral_overlap'] = detector_flags.get('ministral_overlap')
        # LM Studio endpoint (DB columns lm_studio_host / lm_studio_port)
        # forwarded so the Ministral detector targets the operator-configured
        # endpoint for this scan.
        if 'lm_studio_host' in sig.parameters:
            kwargs['lm_studio_host'] = detector_flags.get('lm_studio_host')
        if 'lm_studio_port' in sig.parameters:
            kwargs['lm_studio_port'] = detector_flags.get('lm_studio_port')
        # Ministral chunk-prompt concurrency (DB column ministral_concurrency),
        # forwarded so the operator/auto-tuned value reaches the detector.
        if 'ministral_concurrency' in sig.parameters:
            kwargs['ministral_concurrency'] = detector_flags.get('ministral_concurrency')
    
    def _pass_fresh_configs_to_presidio(self, pii_type_configs: Optional[Dict], request_id: str) -> None:
        """
        Pass fresh PII type configs to Presidio detector to avoid stale cache.
        
        Handles both direct PresidioDetector and CompositePIIDetector cases.
        
        Business rule: When database configs change, we must use fresh configs
        at request time instead of relying on cached configs from initialization.
        
        Args:
            pii_type_configs: Fresh PII type configs from database
            request_id: Request identifier for logging
        """
        if not pii_type_configs:
            logger.debug(f"[{request_id}] No fresh configs to pass to Presidio")
            return
        
        try:
            # Import here to avoid circular dependencies
            from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector
            
            # Case 1: Direct PresidioDetector
            if isinstance(self.detector, PresidioDetector):
                logger.debug(
                    f"[{request_id}] Passing fresh configs to direct PresidioDetector "
                    f"({len(pii_type_configs)} types)"
                )
                # Configs will be passed in detect_pii() call
                return
            
            # Case 2: CompositePIIDetector containing Presidio
            if CompositePIIDetector and isinstance(self.detector, CompositePIIDetector):
                # Try to access Presidio detector from composite
                presidio_detector = getattr(self.detector, 'presidio_detector', None)
                
                if presidio_detector and isinstance(presidio_detector, PresidioDetector):
                    logger.debug(
                        f"[{request_id}] Passing fresh configs to Presidio inside CompositePIIDetector "
                        f"({len(pii_type_configs)} types)"
                    )
                    # Configs will be passed through composite's detect_pii() call
                    return
                else:
                    logger.debug(
                        f"[{request_id}] CompositePIIDetector has no Presidio detector"
                    )
            
            # Case 3: Other detector types (e.g. Ministral) that don't use Presidio configs
            logger.debug(
                f"[{request_id}] Detector type {type(self.detector).__name__} "
                "doesn't use Presidio configs"
            )
            
        except Exception as e:
            logger.warning(
                f"[{request_id}] Failed to pass fresh configs to Presidio: {e}"
            )

    def _log_detection_metrics(
        self, request_id: str, content: str, entities: List, processing_time: float
    ) -> None:
        """Log detection performance metrics if throughput logging is enabled.
        
        Args:
            request_id: Request identifier
            content: Processed content
            entities: Detected entities
            processing_time: Processing duration in seconds
        """
        chars = len(content) if content else 0
        throughput = chars / processing_time if processing_time > 0 else 0.0
        # [VELOCITY] tag makes per-request throughput grep-able for benchmark
        # post-processing on both server-side stdout and the client-side log
        # consumer. Emitted at INFO level (vs DEBUG previously) because the
        # benchmark UI displays a corpus-wide ETA based on this metric and
        # needs to consume it from the container log stream without changing
        # the global log level.
        logger.info(
            "[VELOCITY] req=%s chars=%d duration_s=%.3f velocity_chars_per_s=%.0f entities=%d",
            request_id,
            chars,
            processing_time,
            throughput,
            len(entities),
        )

    def _log_detected_entities(self, request_id: str, entities: List) -> None:
        """Log the per-type breakdown of detected entities for debugging.

        Only aggregate counts are logged; raw PII values are never emitted.

        Args:
            request_id: Request identifier
            entities: Detected PII entities
        """
        if not entities:
            return

        entity_types = {}
        for entity in entities:
            entity_type = entity['type_label']
            entity_types[entity_type] = entity_types.get(entity_type, 0) + 1

        logger.debug(f"[{request_id}] Entity types found: {dict(entity_types)}")

    @staticmethod
    def _log_throughput(
        phase: str,
        request_id: str,
        chars: int,
        duration_s: float,
        **kwargs,
    ) -> None:
        """Forward a throughput record to the async logger (non-blocking).

        Errors during enqueueing must never fail the request; defensive
        ``except`` keeps the scan resilient against an observability
        outage (spec section 3.1).
        """
        try:
            from pii_detector.infrastructure.observability.throughput_logger import (
                get_logger as get_throughput_logger,
            )

            get_throughput_logger().log_phase(
                phase,
                request_id=request_id,
                chars=chars,
                duration_s=duration_s,
                **kwargs,
            )
        except Exception:  # pragma: no cover - defensive
            logger.debug(
                "[THROUGHPUT] log_phase failed silently (phase=%s)",
                phase,
                exc_info=True,
            )

    @staticmethod
    def _is_postfilter_enabled(detector_flags: Optional[dict]) -> bool:
        """Return True iff the database flag activates the precision post-filter.

        Defaults to False so the post-filter stays disabled when the DB
        config is unavailable or pre-migration (PLAN.md section 1.6). The
        flag keeps its historical ``postfilter_enabled`` name (no DB
        migration): it now gates the final precision post-filter.
        """
        if detector_flags is None:
            return False
        return bool(detector_flags.get("postfilter_enabled", False))

    def _apply_format_postfilter(
        self, entities: List, content: str, request_id: str
    ) -> tuple:
        """Run the deterministic precision post-filter on the merged entity list.

        The validator is built lazily via the singleton accessor so that
        when ``postfilter_enabled=false`` the module is never imported and
        nothing is allocated (zero overhead). An entity is rejected either
        by the cross-label technical-artifact denylist (whatever its label)
        or by the per-``pii_type`` checksum/parse strategy registered for
        its normalised type; everything else passes through untouched.

        Returns:
            ``(kept_entities, rejections)`` where ``rejections`` is a list
            of ``(entity, verdict)`` tuples for entities rejected as
            FALSE_POSITIVE (surfaced in the response's
            ``discarded_entities`` field).
        """
        if not entities:
            return entities, []
        try:
            # Lazy import so the no-postfilter path never pulls the strategies
            # / stdnum into hot code.
            from pii_detector.infrastructure.postfilter.format_postfilter_validator import (
                get_instance as get_format_prefilter,
            )

            validator = get_format_prefilter()
            before_count = len(entities)
            prefilter_start = time.monotonic()
            filtered, rejections = validator.filter_with_verdicts(
                content, entities
            )
            elapsed = time.monotonic() - prefilter_start
            rejected = before_count - len(filtered)
            logger.info(
                "[%s] [PREFILTER] post-filter: %d->%d entities "
                "(rejected=%d, elapsed=%.3fs)",
                request_id,
                before_count,
                len(filtered),
                rejected,
                elapsed,
            )
            return filtered, rejections
        except Exception as exc:
            # Defense-in-depth: fail-open at the orchestrator level too. If
            # the validator itself blows up (import error, configuration,
            # ...) we keep the original entities and surface a WARN.
            logger.warning(
                "[%s] [PREFILTER] post-filter failed (%s: %s); "
                "keeping original entities",
                request_id,
                exc.__class__.__name__,
                exc,
            )
            return entities, []

    def _filter_entities_by_type_config(
        self, entities: List, pii_type_configs: dict, request_id: str,
    ) -> List:
        """
        Filter detected entities based on PII type-specific configurations.

        Business rules:
        1. If a PII type is disabled in config, filter out all entities of that type
        2. If entity score is below type-specific threshold, filter it out
        3. If no config exists for a type, keep the entity (allow by default),
           except for MINISTRAL: an unconfigured open-vocabulary label is dropped

        Args:
            entities: List of detected PII entities
            pii_type_configs: Dictionary mapping PII type to config
            request_id: Request identifier for logging

        Returns:
            Filtered list of entities
        """
        if not entities or not pii_type_configs:
            return entities

        logger.debug(
            f"[{request_id}] POST-FILTER START: {len(entities)} entities to filter"
        )
        logger.debug(
            f"[{request_id}] Available PII type configs in DB: {sorted(pii_type_configs.keys())}"
        )

        filter_reasons = {}
        filtered_entities = []

        for idx, entity in enumerate(entities):
            kept, reason = self._evaluate_entity_filter(entity, pii_type_configs, idx, request_id)
            if kept:
                filtered_entities.append(entity)
            elif reason:
                filter_reasons[reason] = filter_reasons.get(reason, 0) + 1

        filtered_count = len(entities) - len(filtered_entities)
        logger.debug(
            f"[{request_id}] POST-FILTER COMPLETE: Filtered {filtered_count} of {len(entities)} entities. "
            f"Kept: {len(filtered_entities)}"
        )
        if filter_reasons:
            logger.debug(f"[{request_id}] Filter reasons breakdown: {dict(filter_reasons)}")

        self._log_parity_debug(
            request_id, entities, filtered_entities, filtered_count, filter_reasons
        )

        return filtered_entities

    @staticmethod
    def _log_parity_debug(
        request_id: str, entities: List, filtered_entities: List,
        filtered_count: int, filter_reasons: dict,
    ) -> None:
        """TEMPORARY: parity recall investigation — remove with git revert."""
        in_per_type: Dict[str, int] = {}
        in_per_source: Dict[str, int] = {}
        out_per_type: Dict[str, int] = {}
        for ent in entities:
            t = _normalize_pii_type_for_grpc(ent.get('type'))
            in_per_type[t] = in_per_type.get(t, 0) + 1
            raw_source = ent.get('source', 'UNKNOWN')
            src = raw_source.value if isinstance(raw_source, DetectorSource) else str(raw_source)
            in_per_source[src] = in_per_source.get(src, 0) + 1
        for ent in filtered_entities:
            t = _normalize_pii_type_for_grpc(ent.get('type'))
            out_per_type[t] = out_per_type.get(t, 0) + 1
        logger.info(
            "[PARITY_DEBUG] [%s] POST_FILTER input=%d output=%d filtered=%d "
            "in_per_type=%s in_per_source=%s out_per_type=%s reasons=%s",
            request_id, len(entities), len(filtered_entities), filtered_count,
            in_per_type, in_per_source, out_per_type, dict(filter_reasons)
        )

    def _evaluate_entity_filter(
        self, entity: dict, pii_type_configs: dict, idx: int, request_id: str
    ) -> tuple:
        """Evaluate whether a single entity passes type-config filters.

        Returns:
            Tuple of (kept: bool, filter_reason: Optional[str])
        """
        entity_type_raw = entity.get('type')
        entity_type = _normalize_pii_type_for_grpc(entity_type_raw)
        entity_type_upper = entity_type.upper()
        entity_score = float(entity.get('score', 0.0))
        raw_source = entity.get('source', 'UNKNOWN')
        entity_source = raw_source.value if isinstance(raw_source, DetectorSource) else str(raw_source)

        logger.debug(
            f"[{request_id}] Entity #{idx+1}: raw_type='{entity_type_raw}' → "
            f"normalized='{entity_type}' → uppercase='{entity_type_upper}' | "
            f"score={entity_score:.3f}"
        )

        # Prefer detector-specific composite key (e.g. "REGEX:IP_ADDRESS"),
        # fall back to plain pii_type key for backward compatibility.
        type_config = pii_type_configs.get(f"{entity_source}:{entity_type_upper}")
        if not type_config:
            type_config = pii_type_configs.get(entity_type_upper)

        # A config row scoped to a *different* specific detector does not apply to
        # this entity's source. Drop it here so the entity is treated as having no
        # applicable config: the plain-key fallback above can resolve to another
        # detector's row (e.g. LOCATION is only configured, disabled, for
        # PRESIDIO), and keeping on that basis would let an open-vocabulary
        # MINISTRAL label bypass both the disabled flag and the
        # unconfigured-MINISTRAL drop, leaking into findings.
        if type_config:
            config_detector = type_config.get('detector', 'ALL')
            if config_detector != 'ALL' and config_detector != entity_source:
                logger.debug(
                    f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): config "
                    f"detector={config_detector} doesn't match entity source={entity_source}; "
                    f"treating as unconfigured for this source"
                )
                type_config = None

        if not type_config:
            return self._decide_unconfigured_entity(
                entity_source, entity_type_upper, idx, request_id
            )

        logger.debug(
            f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): Config FOUND → "
            f"enabled={type_config.get('enabled')}, "
            f"threshold={type_config.get('threshold')}, "
            f"detector={type_config.get('detector')}, "
            f"detector_label={type_config.get('detector_label')}"
        )

        if not type_config.get('enabled', True):
            logger.debug(
                f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): ❌ FILTERED OUT "
                f"(disabled in config for detector={type_config.get('detector', 'ALL')})"
            )
            return False, f"{entity_type_upper}:disabled"

        type_threshold = float(type_config.get('threshold', 0.5))
        if entity_score < type_threshold:
            logger.debug(
                f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): ❌ FILTERED OUT "
                f"(score {entity_score:.3f} < threshold {type_threshold:.3f})"
            )
            return False, f"{entity_type_upper}:below_threshold"

        logger.debug(
            f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): ✅ KEPT "
            f"(enabled=true, score {entity_score:.3f} >= threshold {type_threshold:.3f})"
        )
        return True, None

    @staticmethod
    def _decide_unconfigured_entity(
        entity_source: str, entity_type_upper: str, idx: int, request_id: str
    ) -> tuple:
        """Decide the fate of an entity with no *applicable* type config.

        No applicable config means either no row for this type at all, or only a
        row scoped to a different detector. Ministral is open-vocabulary: such a
        label is a model proposal, not a finding — drop it and surface it for
        operator review via the sentinel reason. Every other source keeps the
        historical allow-by-default.

        Returns:
            Tuple of (kept: bool, filter_reason: Optional[str]).
        """
        if entity_source == DetectorSource.MINISTRAL.value:
            logger.debug(
                f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): ❌ DROPPED "
                f"(unconfigured MINISTRAL label, surfaced for discovery)"
            )
            return False, f"{entity_type_upper}:{_UNKNOWN_MINISTRAL_LABEL_REASON}"
        logger.debug(
            f"[{request_id}] Entity #{idx+1} ({entity_type_upper}): ✅ KEPT (no config)"
        )
        return True, None

    def _build_detection_response(
        self, content: str, entities: List, request_id: str,
        discarded_entities: Optional[List] = None,
        detector_stats: Optional[List] = None,
    ) -> pii_detection_pb2.PIIDetectionResponse:
        """Build complete detection response with entities, nbOfDetectedPIIBySeverity, and masked content.

        Args:
            content: Original content
            entities: Detected PII entities
            request_id: Request identifier for logging
            discarded_entities: Optional list of ``(entity, verdict)`` tuples
                rejected by the precision post-filter, exposed in ``discarded_entities``
            detector_stats: Optional list of per-detector run-stats dicts
                (``source``/``duration_ms``/``entities_found``) exposed in
                ``detector_stats``. Empty for paths that don't produce them.

        Returns:
            Complete PIIDetectionResponse
        """
        logger.debug(f"[{request_id}] Building gRPC response...")
        response = pii_detection_pb2.PIIDetectionResponse()

        self._add_entities_to_response(response, entities, request_id, content)
        self._add_summary_to_response(response, entities, request_id)
        self._add_masked_content_to_response(response, content, entities, request_id)
        if discarded_entities:
            self._add_discarded_entities_to_response(
                response, discarded_entities, request_id
            )
        if detector_stats:
            self._add_detector_stats_to_response(
                response, detector_stats, request_id
            )
        return response

    @staticmethod
    def _add_detector_stats_to_response(
        response: pii_detection_pb2.PIIDetectionResponse,
        detector_stats: List, request_id: str
    ) -> None:
        """Add per-detector run stats to ``detector_stats``.

        Each item is a dict ``{"source": DetectorSource, "duration_ms": int,
        "entities_found": int, "error": str}`` produced by the composite detector
        (one entry per detector that actually ran for this request). A non-empty
        ``error`` is what lets the backend report a detector that could not run
        instead of silently treating its absence of findings as a clean result.

        Args:
            response: Response object to populate
            detector_stats: Per-detector run-stats dicts
            request_id: Request identifier for logging
        """
        for stat in detector_stats:
            try:
                proto_stat = response.detector_stats.add()
                source = stat.get("source")
                if isinstance(source, DetectorSource):
                    proto_stat.source = getattr(
                        pii_detection_pb2.DetectorSource, source.name,
                        pii_detection_pb2.DetectorSource.UNKNOWN_SOURCE,
                    )
                else:
                    source_str = str(source or "UNKNOWN_SOURCE").upper()
                    proto_stat.source = getattr(
                        pii_detection_pb2.DetectorSource, source_str,
                        pii_detection_pb2.DetectorSource.UNKNOWN_SOURCE,
                    )
                proto_stat.duration_ms = int(stat.get("duration_ms", 0))
                proto_stat.entities_found = int(stat.get("entities_found", 0))
                proto_stat.entities_discarded = int(stat.get("entities_discarded", 0))
                proto_stat.error = str(stat.get("error") or "")
            except (ValueError, TypeError) as e:
                # Observability payload only: never fail the response because a
                # single stats entry cannot be serialized.
                logger.warning(
                    f"[{request_id}] Failed to convert detector stat to "
                    f"protobuf: {e}. Stat: {stat}"
                )

    def _add_entities_to_response(
        self, response: pii_detection_pb2.PIIDetectionResponse, entities: List, request_id: str, content: str = ""
    ) -> None:
        """Add detected entities to response, limiting to 1000 to avoid huge responses.
        
        Business rule: Convert all numeric values to native Python types to ensure
        Protobuf compatibility (numpy types cause serialization errors).
        PII types are normalized to match Java PiiType enum expectations.
        
        Args:
            response: Response object to populate
            entities: Detected entities
            request_id: Request identifier for logging
        """
        entities_to_add = min(len(entities), 1000)
        logger.debug(f"[{request_id}] Adding {entities_to_add} entities to response")
        
        for entity in entities[:1000]:
            try:
                self._populate_proto_entity(response.entities.add(), entity)
            except (ValueError, TypeError) as e:
                logger.exception(
                    f"[{request_id}] Failed to convert entity to protobuf: {e} "
                    f"(type={entity.get('type')})"
                )
                raise

        if len(entities) > 1000:
            logger.warning(f"[{request_id}] Truncated entities list from {len(entities)} to 1000")

    @staticmethod
    def _populate_proto_entity(pii_entity, entity) -> None:
        """Fill a proto ``PIIEntity`` from a domain entity (dict-style access).

        Business rule: Convert all numeric values to native Python types to
        ensure Protobuf compatibility (numpy types cause serialization
        errors). PII types are normalized to match Java PiiType enum.
        """
        pii_entity.text = str(entity['text'])
        # Normalize PII type to match Java enum (EMAIL not PIIType.EMAIL)
        pii_entity.type = _normalize_pii_type_for_grpc(entity.get('type'))
        pii_entity.type_label = str(entity['type_label'])
        # Convert to native Python types for Protobuf compatibility
        # (numpy.int64/float64 from Presidio/other detectors cause errors)
        pii_entity.start = int(entity['start'])
        pii_entity.end = int(entity['end'])
        pii_entity.score = float(entity['score'])

        # Detection source: Map Domain Enum to Proto Enum
        domain_source = entity.get('source')
        if isinstance(domain_source, DetectorSource):
            # Map directly using name if names match
            pii_entity.source = getattr(pii_detection_pb2.DetectorSource, domain_source.name, pii_detection_pb2.DetectorSource.UNKNOWN_SOURCE)
        else:
            # Fallback for string or unknown
            source_str = str(domain_source or entity.get('detector') or 'UNKNOWN').upper()
            if source_str == 'UNKNOWN':
                source_str = 'UNKNOWN_SOURCE'
            pii_entity.source = getattr(pii_detection_pb2.DetectorSource, source_str, pii_detection_pb2.DetectorSource.UNKNOWN_SOURCE)

    def _add_discarded_entities_to_response(
        self, response: pii_detection_pb2.PIIDetectionResponse,
        discarded_entities: List, request_id: str
    ) -> None:
        """Add precision post-filter rejections to ``discarded_entities``.

        Each item is a ``(entity, verdict)`` tuple produced by the format
        post-filter's ``filter_with_verdicts``. Same 1000-entry cap as the kept
        entities to bound the response size. The proto verdict fields keep the
        historical ``judge_*`` names but carry the post-filter verdict.

        Args:
            response: Response object to populate
            discarded_entities: ``(entity, verdict)`` tuples rejected by the post-filter
            request_id: Request identifier for logging
        """
        to_add = min(len(discarded_entities), 1000)
        logger.debug(
            f"[{request_id}] Adding {to_add} post-filter-discarded entities to response"
        )
        for entity, verdict in discarded_entities[:1000]:
            try:
                discarded = response.discarded_entities.add()
                self._populate_proto_entity(discarded.entity, entity)
                discarded.judge_verdict = str(getattr(verdict, 'verdict', 'FALSE_POSITIVE'))
                discarded.judge_confidence = float(getattr(verdict, 'confidence', 0.0) or 0.0)
                discarded.judge_reason = str(getattr(verdict, 'reason', '') or '')
            except (ValueError, TypeError) as e:
                # Measurement payload only: never fail the whole response
                # because one rejected entity cannot be serialized.
                logger.warning(
                    f"[{request_id}] Failed to convert discarded entity to "
                    f"protobuf: {e} (type={entity.get('type')})"
                )
        if len(discarded_entities) > 1000:
            logger.warning(
                f"[{request_id}] Truncated discarded entities list from "
                f"{len(discarded_entities)} to 1000"
            )

    def _add_summary_to_response(
        self, response: pii_detection_pb2.PIIDetectionResponse, entities: List, request_id: str
    ) -> None:
        """Add entity type nbOfDetectedPIIBySeverity to response.
        
        Business rule: Summary keys are normalized to match Java PiiType enum.
        Uses the same normalization as entities to ensure consistency.
        
        Args:
            response: Response object to populate
            entities: Detected entities
            request_id: Request identifier for logging
        """
        logger.debug(f"[{request_id}] Creating response nbOfDetectedPIIBySeverity...")
        summary = {}
        for entity in entities:
            # Use same normalization function as entities (EMAIL not PIIType.EMAIL)
            pii_type = _normalize_pii_type_for_grpc(entity.get('type'))
            summary[pii_type] = summary.get(pii_type, 0) + 1
        
        logger.debug(f"[{request_id}] Adding nbOfDetectedPIIBySeverity to response: {dict(summary)}")
        for pii_type, count in summary.items():
            response.summary[pii_type] = count

    def _add_masked_content_to_response(
        self,
        response: pii_detection_pb2.PIIDetectionResponse,
        content: str,
        entities: List,
        request_id: str
    ) -> None:
        """Add masked content to response, skipping for large texts to save memory.
        
        Business rule: Only mask content up to 10K characters to avoid memory issues.
        
        Args:
            response: Response object to populate
            content: Original content
            entities: Detected entities
            request_id: Request identifier for logging
        """
        if len(content) <= 10000:
            masking_start = time.time()
            logger.debug(f"[{request_id}] Generating masked content...")
            
            masked_content = self.detector._apply_masks(content, entities)
            response.masked_content = masked_content
            
            masking_time = time.time() - masking_start
            logger.debug(f"[{request_id}] Masking completed in {masking_time:.3f}s")
        else:
            logger.debug(f"[{request_id}] Skipping masking for large content")
            response.masked_content = "[Content too large for masking]"

    def _log_request_completion(self, request_id: str, start_time: float) -> None:
        """Log request completion with timing information.
        
        Args:
            request_id: Request identifier
            start_time: Request start timestamp
        """
        total_time = time.time() - start_time
        logger.info(f"[{request_id}] Request completed successfully in {total_time:.3f}s")

    def _perform_periodic_gc(self) -> None:
        """Trigger garbage collection periodically to manage memory."""
        if self.request_counter % self.gc_frequency == 0:
            gc.collect()
            logger.debug(f"Garbage collection triggered after {self.request_counter} requests")

    def _handle_detection_error(
        self, exception: Exception, request_id: str, start_time: float, context
    ) -> pii_detection_pb2.PIIDetectionResponse:
        """Handle and log detection errors.
        
        Args:
            exception: Exception that occurred
            request_id: Request identifier
            start_time: Request start timestamp
            context: gRPC context for error reporting
            
        Returns:
            Empty PIIDetectionResponse
        """
        error_time = time.time() - start_time
        error_message = f"Error processing request: {str(exception)}"
        
        logger.error(f"[{request_id}] {error_message}")
        logger.error(f"[{request_id}] Error occurred after {error_time:.3f}s")
        logger.error(f"[{request_id}] Exception type: {type(exception).__name__}")
        logger.error(f"[{request_id}] Exception details: {str(exception)}")
        
        import traceback
        logger.debug(f"[{request_id}] Stack trace:\n{traceback.format_exc()}")
        
        context.set_code(grpc.StatusCode.INTERNAL)
        context.set_details(error_message)
        
        return pii_detection_pb2.PIIDetectionResponse()

    def _cleanup_request_resources(self, request_id: str, start_time: float) -> None:
        """Cleanup request resources and log final timing.
        
        Args:
            request_id: Request identifier
            start_time: Request start timestamp
        """
        final_time = time.time() - start_time
        logger.debug(f"[{request_id}] Request cleanup completed, total time: {final_time:.3f}s")


class MemoryLimitedServer:
    """
    gRPC server wrapper with memory usage limits and request queuing.
    """
    
    def __init__(self, port: int = 50051, max_workers: int = 5, 
                 max_queued_requests: int = 100, memory_limit_percent: float = 85.0):
        """
        Initialize the memory-limited server.
        
        Args:
            port: Port to listen on
            max_workers: Maximum number of worker threads
            max_queued_requests: Maximum number of queued requests
            memory_limit_percent: Maximum memory usage percentage before rejecting requests
        """
        self.port = port
        self.max_workers = max_workers
        self.max_queued_requests = max_queued_requests
        self.memory_limit_percent = memory_limit_percent
        self.server = None
        self.executor = None
        
    def _check_memory(self):
        """Check if memory usage is within limits."""
        process = psutil.Process(os.getpid())
        memory_percent = process.memory_percent()
        return memory_percent < self.memory_limit_percent
    
    def stop(self, grace: int = 5):
        """
        Stop the gRPC server gracefully.

        Args:
            grace: Grace period in seconds for pending requests to complete.
        """
        if self.server:
            logger.info(f"Stopping gRPC server with {grace}s grace period...")
            self.server.stop(grace)
            logger.info("gRPC server stopped")
        if self.executor:
            logger.info("Shutting down thread pool executor...")
            self.executor.shutdown(wait=True)
            logger.info("Thread pool executor shut down")

    def serve(self):
        """Start the gRPC server with memory limits."""
        # Create a custom thread pool executor with bounded queue
        self.executor = futures.ThreadPoolExecutor(
            max_workers=self.max_workers,
            thread_name_prefix='grpc-worker'
        )
        
        # Create server with custom executor.
        # Keepalive options are critical for long-running PII scans (200+ seconds
        # on 100k-char Excel docs). Without these, an HTTP/2 PING from the client
        # at default cadence (Armeria 30s when enabled, or any client-side
        # liveness probe) triggers GOAWAY ENHANCE_YOUR_CALM via the default
        # `grpc.http2.max_pings_without_data=2` guard. We loosen those guards
        # and explicitly accept long idle streams since each inference can hold
        # a single stream open for several minutes with no DATA frames in transit.
        self.server = grpc.server(
            self.executor,
            options=[
                ('grpc.max_receive_message_length', 10 * 1024 * 1024),  # 10MB max message size
                ('grpc.max_send_message_length', 10 * 1024 * 1024),
                ('grpc.max_concurrent_streams', 100),
                # Accept PINGs without data (long inferences send no DATA frames
                # for minutes, so the default max_pings_without_data=2 trips).
                ('grpc.http2.max_pings_without_data', 0),
                ('grpc.http2.min_time_between_pings_ms', 10_000),
                # Allow clients to send keepalive pings even when there's no
                # active RPC (some clients open the connection eagerly before
                # the first call).
                ('grpc.keepalive_permit_without_calls', 1),
                # No server-initiated GOAWAY based on connection age; benchmark
                # runs intentionally keep the same connection alive for an hour.
                ('grpc.max_connection_age_ms', 0x7FFFFFFF),
                ('grpc.max_connection_idle_ms', 0x7FFFFFFF),
            ]
        )
        
        # Add service
        servicer = PIIDetectionServicer(
            max_text_size=1_000_000,  # 1M characters max
            enable_memory_monitoring=True
        )
        pii_detection_pb2_grpc.add_PIIDetectionServiceServicer_to_server(
            servicer, self.server
        )
        
        # Enable gRPC reflection for service discovery
        SERVICE_NAMES = (
            pii_detection_pb2.DESCRIPTOR.services_by_name['PIIDetectionService'].full_name,
            reflection.SERVICE_NAME,
        )
        reflection.enable_server_reflection(SERVICE_NAMES, self.server)
        
        # Add insecure port with IPv6 first, then fallback to IPv4 on Windows/IPv6-disabled hosts
        bind_targets = [f"[::]:{self.port}", f"0.0.0.0:{self.port}"]
        bound = 0
        for target in bind_targets:
            try:
                res = self.server.add_insecure_port(target)
                if res:
                    bound = res
                    logger.info(f"gRPC bound to {target} (fd={res})")
                    break
                else:
                    logger.debug(f"gRPC failed to bind {target}")
            except Exception as e:
                logger.debug(f"Exception while binding {target}: {e}")
        if not bound:
            raise RuntimeError(f"Failed to bind gRPC server on any address for port {self.port}. Tried: {bind_targets}")
        
        # Start server
        self.server.start()
        logger.info(f"Memory-limited server started on port {self.port}")
        logger.info(f"Configuration: max_workers={self.max_workers}, "
                   f"memory_limit={self.memory_limit_percent}%")
        
        return self.server


def _normalize_pii_type_for_grpc(pii_type) -> str:
    """
    Normalize PII type to string format expected by Java PiiType enum.
    
    Business rule: Java expects enum name only (e.g., 'EMAIL'), not the full
    Python repr (e.g., 'PIIType.EMAIL'). This function handles both PIIType
    enum objects and string values.
    
    Args:
        pii_type: PIIType enum object or string
        
    Returns:
        Normalized PII type name in UPPERCASE (e.g., 'EMAIL', 'CREDIT_CARD', 'SSN')
    """
    if pii_type is None or pii_type == "":
        return "UNKNOWN"
    
    # If it's a PIIType enum, extract just the name (EMAIL, not PIIType.EMAIL)
    if isinstance(pii_type, PIIType):
        return pii_type.name
    
    # If it's already a string, normalize to UPPER_SNAKE_CASE
    # Zero-shot labels may contain spaces/hyphens (e.g., "person name" → "PERSON_NAME")
    # which must be converted to underscores for valid token format [TYPE]
    return str(pii_type).upper().replace(" ", "_").replace("-", "_")


def serve(port: int = 50051, max_workers: int = 5):
    """
    Start the gRPC server with memory management.
    
    Args:
        port: The port to listen on.
        max_workers: The maximum number of worker threads.
    """
    server = MemoryLimitedServer(
        port=port,
        max_workers=max_workers,
        max_queued_requests=100,
        memory_limit_percent=85.0
    )
    return server.serve()


if __name__ == '__main__':
    # Start the server with conservative settings
    server = serve(max_workers=3)  # Reduced from 10 to 3 workers
    
    # Keep the server running until interrupted
    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
        server.stop(grace=5)
        logger.info("Server stopped gracefully")