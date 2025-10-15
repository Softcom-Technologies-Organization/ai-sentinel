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
from typing import Dict, List, Optional

import grpc
import psutil
# Import gRPC reflection for service discovery
from grpc_reflection.v1alpha import reflection

# Import the generated gRPC code
from pii_detector.proto.generated import pii_detection_pb2, pii_detection_pb2_grpc

# Import the PII detector
from pii_detector.service.detector.pii_detector import PIIDetector
from pii_detector.service.detector.pii_detector import PIIEntity as DetectedPIIEntity
# Import GLiNER detector for GLiNER models
try:
    from pii_detector.service.detector.gliner_detector import GLiNERDetector
except Exception:  # pragma: no cover - safe import guard
    GLiNERDetector = None  # type: ignore
# Optional pre-caching of additional HF models (extensible)
try:
    from pii_detector.service.detector.model_cache import ensure_models_cached, get_env_extra_models
except Exception:  # pragma: no cover - safe import guard
    ensure_models_cached = None
    get_env_extra_models = None

# Optional multi-model composite (opt-in via config)
try:
    from pii_detector.service.detector.multi_detector import (
        MultiModelPIIDetector,
        get_multi_model_ids_from_config,
        should_use_multi_detector
    )
except Exception:  # pragma: no cover - safe import guard
    MultiModelPIIDetector = None  # type: ignore
    get_multi_model_ids_from_config = None  # type: ignore
    should_use_multi_detector = None  # type: ignore

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
    """Initialize the global detector instance with appropriate configuration."""
    global _detector_instance
    
    _pre_cache_models()
    
    if _should_use_multi_detector():
        _detector_instance = _create_multi_detector()
    else:
        _detector_instance = _create_single_detector()
    
    _detector_instance.download_model()
    _detector_instance.load_model()
    logger.info("Singleton PII detector initialized successfully")


def _pre_cache_models() -> None:
    """Pre-cache additional HuggingFace models if available."""
    try:
        if ensure_models_cached and get_env_extra_models:
            ensure_models_cached(get_env_extra_models())
    except Exception as e:  # pragma: no cover - defensive
        logger.warning(f"Pre-caching extra models failed (continuing): {e}")


def _should_use_multi_detector() -> bool:
    """Determine if multi-model detector should be used."""
    if not (MultiModelPIIDetector and get_multi_model_ids_from_config and should_use_multi_detector):
        return False
    
    try:
        return should_use_multi_detector()
    except Exception as e:
        logger.warning(f"Failed to determine multi-detector status: {e}")
        return False


def _create_multi_detector():
    """Create and return a multi-model detector instance."""
    try:
        model_ids = get_multi_model_ids_from_config()
        detector = MultiModelPIIDetector(model_ids=model_ids)
        logger.info(f"Multi-model detection enabled with {len(model_ids)} models: {model_ids}")
        return detector
    except Exception as e:  # pragma: no cover - defensive fallback
        logger.warning(f"Failed to initialize multi-model detector, falling back to single model: {e}")
        return PIIDetector()


def _create_single_detector():
    """Create and return a single-model detector instance."""
    logger.info("Using single-model detector (either multi-detector disabled or only 1 model enabled)")
    
    from pii_detector.service.detector.models.detection_config import DetectionConfig
    config = DetectionConfig()
    
    if _is_gliner_model(config.model_id):
        logger.info(f"Detected GLiNER model: {config.model_id}")
        return GLiNERDetector(config=config)
    
    logger.info(f"Using standard transformer detector for: {config.model_id}")
    return PIIDetector()


def _is_gliner_model(model_id: str) -> bool:
    """Check if the model is a GLiNER model."""
    return GLiNERDetector is not None and "gliner" in model_id.lower()


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
        
        # Start memory monitoring thread if enabled
        if self.enable_memory_monitoring:
            self._start_memory_monitoring()
    
    def _load_log_throughput_config(self) -> bool:
        """
        Load log_throughput flag from configuration.
        
        Returns:
            True if throughput logging is enabled, False otherwise
        """
        try:
            from pii_detector.service.detector.models.detection_config import _load_llm_config
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
                logger.error(f"Error in memory monitoring: {str(e)}")
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
        2. Execute PII detection on content
        3. Build response with entities, summary, and masked content
        4. Handle errors and cleanup
        
        Args:
            request: gRPC request containing content and threshold
            context: gRPC context for setting response codes
            
        Returns:
            PIIDetectionResponse with detected entities and summary
        """
        start_time = time.time()
        request_id = self._generate_request_id(start_time)
        
        try:
            self.request_counter += 1
            content, threshold = self._extract_and_validate_request(request, context, request_id)
            
            if content is None:
                return pii_detection_pb2.PIIDetectionResponse()
            
            entities = self._execute_detection(content, threshold, request_id)
            response = self._build_detection_response(content, entities, request_id)
            self._log_request_completion(request_id, start_time)
            self._perform_periodic_gc()
            
            return response
            
        except Exception as e:
            return self._handle_detection_error(e, request_id, start_time, context)
        finally:
            self._cleanup_request_resources(request_id, start_time)

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
        
        logger.info(f"[{request_id}] Received DetectPII request #{self.request_counter}")
        logger.info(f"[{request_id}] Client: {peer_info}")
        logger.info(f"[{request_id}] Content length: {len(content)} characters")
        logger.info(f"[{request_id}] Threshold: {threshold}")
        
        if len(content) > 100:
            logger.debug(f"[{request_id}] Content preview: {content[:100]}...")
        else:
            logger.debug(f"[{request_id}] Content: {content}")

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

    def _execute_detection(self, content: str, threshold: float, request_id: str) -> List:
        """Execute PII detection and log performance metrics.
        
        Args:
            content: Text to analyze
            threshold: Detection confidence threshold
            request_id: Request identifier for logging
            
        Returns:
            List of detected PII entities
        """
        processing_start = time.time()
        logger.info(f"[{request_id}] Starting PII detection processing...")
        
        entities = self.detector.detect_pii(content, threshold)
        processing_time = time.time() - processing_start
        
        self._log_detection_metrics(request_id, content, entities, processing_time)
        self._log_detected_entities(request_id, entities)
        
        return entities

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
        if self.log_throughput:
            throughput = len(content) / processing_time if processing_time > 0 else 0
            logger.info(
                f"[{request_id}] PII detection completed in {processing_time:.3f}s, "
                f"found {len(entities)} entities, "
                f"throughput: {throughput:.0f} chars/s"
            )
        else:
            logger.info(f"[{request_id}] PII detection completed in {processing_time:.3f}s")
            logger.info(f"[{request_id}] Found {len(entities)} PII entities")

    def _log_detected_entities(self, request_id: str, entities: List) -> None:
        """Log summary and sample of detected entities for debugging.
        
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
        
        for i, entity in enumerate(entities[:3]):
            logger.debug(
                f"[{request_id}] Entity {i+1}: {entity['type_label']} - "
                f"'{entity['text']}' (score: {entity['score']:.3f})"
            )

    def _build_detection_response(
        self, content: str, entities: List, request_id: str
    ) -> pii_detection_pb2.PIIDetectionResponse:
        """Build complete detection response with entities, summary, and masked content.
        
        Args:
            content: Original content
            entities: Detected PII entities
            request_id: Request identifier for logging
            
        Returns:
            Complete PIIDetectionResponse
        """
        logger.debug(f"[{request_id}] Building gRPC response...")
        response = pii_detection_pb2.PIIDetectionResponse()
        
        self._add_entities_to_response(response, entities, request_id)
        self._add_summary_to_response(response, entities, request_id)
        self._add_masked_content_to_response(response, content, entities, request_id)
        
        return response

    def _add_entities_to_response(
        self, response: pii_detection_pb2.PIIDetectionResponse, entities: List, request_id: str
    ) -> None:
        """Add detected entities to response, limiting to 1000 to avoid huge responses.
        
        Args:
            response: Response object to populate
            entities: Detected entities
            request_id: Request identifier for logging
        """
        entities_to_add = min(len(entities), 1000)
        logger.debug(f"[{request_id}] Adding {entities_to_add} entities to response")
        
        for entity in entities[:1000]:
            pii_entity = response.entities.add()
            pii_entity.text = entity['text']
            pii_entity.type = entity['type']
            pii_entity.type_label = entity['type_label']
            pii_entity.start = entity['start']
            pii_entity.end = entity['end']
            pii_entity.score = entity['score']
        
        if len(entities) > 1000:
            logger.warning(f"[{request_id}] Truncated entities list from {len(entities)} to 1000")

    def _add_summary_to_response(
        self, response: pii_detection_pb2.PIIDetectionResponse, entities: List, request_id: str
    ) -> None:
        """Add entity type summary to response.
        
        Args:
            response: Response object to populate
            entities: Detected entities
            request_id: Request identifier for logging
        """
        logger.debug(f"[{request_id}] Creating response summary...")
        summary = {}
        for entity in entities:
            pii_type = entity['type_label']
            summary[pii_type] = summary.get(pii_type, 0) + 1
        
        logger.debug(f"[{request_id}] Adding summary to response: {dict(summary)}")
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

    def StreamDetectPII(self, request, context):
        """
        Stream progressive PII detection updates per chunk and a final summary.
        """
        start_time = time.time()
        request_id = self._generate_stream_request_id(start_time)

        try:
            self.request_counter += 1

            if not self._validate_stream_request(request, context, request_id):
                return

            content, threshold = request.content, self._get_threshold(request)
            
            for update in self._stream_detection_chunks(content, threshold, request_id, context):
                yield update
            
            yield self._build_final_stream_update(content, threshold, request_id)

        except Exception as e:
            self._handle_stream_error(e, request_id, context)
        finally:
            self._cleanup_stream_resources()
    
    def _generate_stream_request_id(self, start_time: float) -> str:
        """Generate unique request identifier for streaming."""
        return f"stream_{self.request_counter + 1}_{int(start_time * 1000) % 10000}"
    
    def _get_threshold(self, request) -> float:
        """Extract threshold from request with default."""
        return request.threshold if request.threshold > 0 else 0.5
    
    def _validate_stream_request(self, request, context, request_id: str) -> bool:
        """Validate streaming request parameters.
        
        Returns:
            True if validation passed, False otherwise
        """
        if not request.content:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details("Content cannot be empty")
            return False

        if len(request.content) > self.max_text_size:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details(
                f"Content too large: {len(request.content)} characters (max: {self.max_text_size})"
            )
            return False
        
        return True
    
    def _stream_detection_chunks(self, content: str, threshold: float, request_id: str, context):
        """Stream detection updates for each chunk of content.
        
        Yields:
            PIIDetectionUpdate messages for each processed chunk
        """
        cfg = self.detector.config
        step = max(1, cfg.chunk_size - cfg.chunk_overlap)
        total_chunks = max(1, (len(content) + step - 1) // step)

        logger.info(f"[{request_id}] Starting streaming detection: len={len(content)}, step={step}, total_chunks={total_chunks}")

        all_entities = []
        chunk_index = 0

        for start in range(0, len(content), step):
            if self._should_stop_streaming(context, request_id, chunk_index):
                return

            chunk_entities = self._process_stream_chunk(content, start, cfg.chunk_size, threshold)
            added_in_chunk = self._add_unique_entities(chunk_entities, start, all_entities)
            
            yield self._create_chunk_update(added_in_chunk, chunk_index, total_chunks)
            
            self._cleanup_chunk_resources()
            chunk_index += 1
        
        # Store all_entities for final update
        self._stream_all_entities = all_entities
    
    def _should_stop_streaming(self, context, request_id: str, chunk_index: int) -> bool:
        """Check if streaming should stop due to client cancellation."""
        if hasattr(context, 'is_active') and not context.is_active():
            logger.info(f"[{request_id}] Client cancelled stream; stopping early at chunk {chunk_index}")
            return True
        return False
    
    def _process_stream_chunk(self, content: str, start: int, chunk_size: int, threshold: float) -> List:
        """Process a single chunk and return detected entities."""
        end = min(start + chunk_size, len(content))
        chunk = content[start:end]

        raw_results = self.detector.pipeline(chunk)
        return self.detector.entity_processor.process_entities(raw_results, threshold)
    
    def _add_unique_entities(self, chunk_entities: List, start: int, all_entities: List) -> List:
        """Add unique entities from chunk to all_entities, adjusting positions."""
        added_in_chunk = []
        for e in chunk_entities:
            adj = DetectedPIIEntity(
                text=e.text,
                pii_type=e.pii_type,
                type_label=e.type_label,
                start=e.start + start,
                end=e.end + start,
                score=e.score,
            )
            if not self.detector._is_duplicate_entity(adj, all_entities):
                all_entities.append(adj)
                added_in_chunk.append(adj)
        
        return added_in_chunk
    
    def _create_chunk_update(self, added_entities: List, chunk_index: int, total_chunks: int):
        """Create update message for processed chunk."""
        progress = int(((chunk_index + 1) * 100) / total_chunks)
        update = pii_detection_pb2.PIIDetectionUpdate(
            chunk_index=chunk_index,
            total_chunks=total_chunks,
            progress_percent=progress,
            final=False,
        )
        
        for ae in added_entities:
            update.entities.append(
                pii_detection_pb2.PIIEntity(
                    text=ae.text,
                    type=ae.pii_type,
                    type_label=ae.type_label,
                    start=ae.start,
                    end=ae.end,
                    score=ae.score,
                )
            )
        
        return update
    
    def _cleanup_chunk_resources(self) -> None:
        """Free memory resources after chunk processing."""
        self.detector.memory_manager.clear_cache(self.detector.device)
        gc.collect(0)
    
    def _build_final_stream_update(self, content: str, threshold: float, request_id: str):
        """Build final update with masked content and summary."""
        all_entities = getattr(self, '_stream_all_entities', [])
        
        masked_content = self.detector._apply_masks(content, all_entities)
        summary = self._build_entity_summary(all_entities)
        
        cfg = self.detector.config
        step = max(1, cfg.chunk_size - cfg.chunk_overlap)
        total_chunks = max(1, (len(content) + step - 1) // step)
        
        final_update = pii_detection_pb2.PIIDetectionUpdate(
            chunk_index=max(0, total_chunks - 1),
            total_chunks=total_chunks,
            progress_percent=100,
            masked_content=masked_content,
            final=True,
        )
        
        for k, v in summary.items():
            final_update.summary[k] = v
        
        return final_update
    
    def _build_entity_summary(self, all_entities: List) -> Dict[str, int]:
        """Build summary dictionary of entity types and counts."""
        summary: dict[str, int] = {}
        for e in all_entities:
            key = e.type_label
            summary[key] = summary.get(key, 0) + 1
        return summary
    
    def _handle_stream_error(self, exception: Exception, request_id: str, context) -> None:
        """Handle streaming detection error."""
        logger.error(f"[{request_id}] Streaming detection failed: {str(exception)}")
        context.set_code(grpc.StatusCode.INTERNAL)
        context.set_details(f"Streaming detection failed: {str(exception)}")
    
    def _cleanup_stream_resources(self) -> None:
        """Cleanup resources after streaming."""
        self.detector.memory_manager.clear_cache(self.detector.device)
        gc.collect(0)
        # Clean up temporary stream state
        if hasattr(self, '_stream_all_entities'):
            delattr(self, '_stream_all_entities')


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
        
    def _check_memory(self):
        """Check if memory usage is within limits."""
        process = psutil.Process(os.getpid())
        memory_percent = process.memory_percent()
        return memory_percent < self.memory_limit_percent
    
    def serve(self):
        """Start the gRPC server with memory limits."""
        # Create a custom thread pool executor with bounded queue
        executor = futures.ThreadPoolExecutor(
            max_workers=self.max_workers,
            thread_name_prefix='grpc-worker'
        )
        
        # Create server with custom executor
        self.server = grpc.server(
            executor,
            options=[
                ('grpc.max_receive_message_length', 10 * 1024 * 1024),  # 10MB max message size
                ('grpc.max_send_message_length', 10 * 1024 * 1024),
                ('grpc.max_concurrent_streams', 100),
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
