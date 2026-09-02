"""
Composite PII detector orchestrating Regex, Presidio and Ministral detection.

This module provides CompositePIIDetector that combines:
- RegexDetector: deterministic pattern matching for structured PII
- PresidioDetector: rule/NER based detection
- MinistralDetector: specialised LLM PII extractor (opt-in)

Business value:
- Leverages strengths of deterministic (precision) and LLM (contextual) detection
- Runtime-configurable activation of each detector via database config
- Extensible architecture for adding new detector types
"""

from __future__ import annotations

import logging
import time
from typing import Dict, List, Optional, Tuple

from pii_detector.domain.entity.detector_failure import (
    DetectorFailure,
    DetectorFailureCode,
)
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.port.pii_detector_protocol import PIIDetectorProtocol
from pii_detector.domain.service.detection_merger import DetectionMerger
from pii_detector.infrastructure.detector.regex_detector import RegexDetector

try:
    from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector
    PRESIDIO_AVAILABLE = True
except ImportError as e:
    PRESIDIO_AVAILABLE = False
    PresidioDetector = None


logger = logging.getLogger(__name__)


class CompositePIIDetector:
    """
    Composite detector orchestrating Regex, Presidio and Ministral detection.

    Business rules:
    - Each detector runs independently and is selectively activated per request
      from the database configuration (no service restart required).
    - Results are merged using the DetectionMerger fusion strategy.

    Architecture:
    - Implements PIIDetectorProtocol for transparent integration
    - Uses DetectionMerger for result fusion
    """

    def __init__(
        self,
        regex_detector: Optional[RegexDetector] = None,
        presidio_detector: Optional[PresidioDetector] = None,
        ministral_detector: Optional[PIIDetectorProtocol] = None,
        merger: Optional[DetectionMerger] = None,
        enable_regex: bool = True,
        enable_presidio: bool = True,
        enable_ministral: bool = False
    ):
        """
        Initialize composite detector.

        Args:
            regex_detector: Regex-based detector
            presidio_detector: Presidio-based detector
            ministral_detector: Ministral-PII LLM detector (optional, opt-in source)
            merger: Detection merger for result fusion
            enable_regex: Enable regex detection (default: True)
            enable_presidio: Enable Presidio detection (default: True)
            enable_ministral: Enable Ministral-PII detection (default: False, opt-in per DB flag)
        """
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

        # Initialize regex detector if enabled
        self.regex_detector = self._init_regex_detector(regex_detector, enable_regex)
        # Set enable_regex based on actual availability
        self.enable_regex = enable_regex and self.regex_detector is not None

        # Initialize Presidio detector if enabled
        self.presidio_detector, self.enable_presidio = self._init_presidio_detector(
            presidio_detector, enable_presidio
        )

        # Initialize Ministral-PII detector slot (opt-in, specialised LLM
        # extractor, disabled by default per DB flag)
        self.ministral_detector = ministral_detector
        self.enable_ministral = enable_ministral and self.ministral_detector is not None

        # Initialize merger
        self._merger = merger or DetectionMerger(log_provenance=True)

        self.logger.info(
            "CompositePIIDetector initialized: "
            f"Regex={'enabled' if self.enable_regex else 'disabled'}, "
            f"Presidio={'enabled' if self.enable_presidio else 'disabled'}, "
            f"Ministral={'enabled' if self.enable_ministral else 'disabled'}"
        )

    def _init_regex_detector(
        self,
        regex_detector: Optional[RegexDetector],
        enable_regex: bool,
    ) -> Optional[RegexDetector]:
        """Return the regex detector, building a default one when enabled."""
        if regex_detector is not None or not enable_regex:
            return regex_detector
        try:
            detector = RegexDetector()
            self.logger.info("RegexDetector initialized successfully")
            return detector
        except Exception as e:
            self.logger.warning(f"Failed to initialize RegexDetector: {e}")
            return None

    def _init_presidio_detector(
        self,
        presidio_detector: Optional[PresidioDetector],
        enable_presidio: bool,
    ) -> Tuple[Optional[PresidioDetector], bool]:
        """Return ``(detector, enabled)``, building a default when enabled."""
        enabled = enable_presidio and PRESIDIO_AVAILABLE
        if presidio_detector is not None or not enabled:
            return presidio_detector, enabled
        try:
            detector = PresidioDetector()
            self.logger.info("PresidioDetector initialized successfully")
            return detector, True
        except Exception as e:
            self.logger.warning(f"Failed to initialize PresidioDetector: {e}")
            return None, False

    @property
    def model_id(self) -> str:
        """Get composite model identifier."""
        return "composite-regex-presidio-ministral"

    def download_model(self) -> None:
        """Download models for all underlying detectors."""
        if self.regex_detector:
            self.regex_detector.download_model()  # No-op for regex

        if self.presidio_detector:
            # Presidio doesn't need download
            pass

        if self.ministral_detector and self.enable_ministral:
            try:
                self.ministral_detector.download_model()  # No-op for remote endpoint
            except Exception as e:
                self.logger.warning(f"Ministral detector download failed: {e}")

    def load_model(self) -> None:
        """Load models for all underlying detectors."""
        if self.regex_detector:
            self.regex_detector.load_model()  # No-op for regex

        if self.presidio_detector:
            # Presidio models are loaded on first use
            pass

        if self.ministral_detector and self.enable_ministral:
            try:
                self.ministral_detector.load_model()  # No-op for remote endpoint
            except Exception as e:
                # Do not block other detectors if Ministral fails to load.
                # It will be skipped at request time.
                self.logger.error(f"Ministral detector load failed: {e}")
                self.enable_ministral = False

    def detect_pii(
        self,
        text: str,
        threshold: Optional[float] = None,
        enable_regex: Optional[bool] = None,
        enable_presidio: Optional[bool] = None,
        enable_ministral: Optional[bool] = None,
        pii_type_configs: Optional[dict] = None,
        ministral_chunk_size: Optional[int] = None,
        ministral_overlap: Optional[int] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        ministral_concurrency: Optional[int] = None,
        lm_studio_model: Optional[str] = None,
    ) -> List[PIIEntity]:
        """
        Detect PII using the Regex, Presidio and Ministral detectors.

        Business process:
        1. Execute each enabled detector
        2. Merge results with priority-based fusion
        3. Return deduplicated entities

        Business rule: Dynamic configuration allows runtime control of detectors
        based on database configuration without service restart. Detectors remain
        loaded in memory for performance but can be selectively activated per request.

        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            enable_regex: Override regex detector activation (None=use default)
            enable_presidio: Override Presidio detector activation (None=use default)
            enable_ministral: Override Ministral detector activation (None=use default)
            pii_type_configs: Optional fresh PII type configurations from database

        Returns:
            Merged list of PII entities
        """
        entities, _stats = self.detect_pii_with_stats(
            text, threshold, enable_regex, enable_presidio, enable_ministral,
            pii_type_configs, ministral_chunk_size, ministral_overlap,
            lm_studio_host, lm_studio_port, ministral_concurrency,
            lm_studio_model,
        )
        return entities

    def detect_pii_with_stats(
        self,
        text: str,
        threshold: Optional[float] = None,
        enable_regex: Optional[bool] = None,
        enable_presidio: Optional[bool] = None,
        enable_ministral: Optional[bool] = None,
        pii_type_configs: Optional[dict] = None,
        ministral_chunk_size: Optional[int] = None,
        ministral_overlap: Optional[int] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        ministral_concurrency: Optional[int] = None,
        lm_studio_model: Optional[str] = None,
    ) -> Tuple[List[PIIEntity], List[Dict]]:
        """Detect PII and return per-detector execution stats alongside results.

        Same contract as :meth:`detect_pii` but additionally returns one stats
        entry per detector that actually ran (even with 0 detections). Each
        stats entry is a plain dict (picklable across the worker-pool boundary)::

            {"source": DetectorSource, "duration_ms": int, "entities_found": int,
             "error": str}

        ``error`` is empty when the detector ran normally and carries the failure
        reason otherwise, so a detector that could not run is never mistaken for
        one that found no PII.

        Stats are returned by value (never stored on the instance) because the
        composite is a singleton shared across concurrent gRPC worker threads;
        per-instance mutable state would be a race condition.

        Returns:
            Tuple of (merged_entities, detector_stats).
        """
        if not text:
            return [], []

        use_regex, use_presidio, use_ministral = self._resolve_detector_flags(
            enable_regex, enable_presidio, enable_ministral
        )
        self._log_active_detectors(use_regex, use_presidio, use_ministral)

        results_per_detector, stats = self._collect_detection_results(
            text, threshold, use_regex, use_presidio, use_ministral,
            pii_type_configs, ministral_chunk_size, ministral_overlap,
            lm_studio_host, lm_studio_port, ministral_concurrency,
            lm_studio_model,
        )

        if not results_per_detector:
            self.logger.warning("No detectors available")
            return [], stats

        merged_entities = self._merger.merge(results_per_detector)
        self._log_detection_summary(results_per_detector, merged_entities)
        return merged_entities, stats

    def check_detectors_health(
        self,
        enable_regex: Optional[bool] = None,
        enable_presidio: Optional[bool] = None,
        enable_ministral: Optional[bool] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        lm_studio_model: Optional[str] = None,
    ) -> List[Dict]:
        """Report whether each ENABLED detector can actually run right now.

        Answers the pre-flight question "will the detectors the operator switched
        on really contribute?". Only enabled detectors are reported: a detector
        deliberately turned off is not a problem to warn about. Each entry is a
        plain dict::

            {"source": DetectorSource, "reachable": bool, "endpoint": str,
             "error": str, "error_code": str, "error_params": Dict[str, str]}

        ``error`` is a diagnostic detail for the logs; ``error_code`` is what the
        dashboard translates into the operator's language.

        Local detectors (Regex, Presidio) are ready as soon as they were
        instantiated — an enabled-but-missing instance is itself a fault worth
        reporting, since it silently contributes nothing. The remote Ministral
        endpoint is probed over the network.
        """
        use_regex, use_presidio, use_ministral = self._resolve_detector_flags(
            enable_regex, enable_presidio, enable_ministral
        )
        health: List[Dict] = []
        if use_regex:
            health.append(self._local_health(DetectorSource.REGEX, self.regex_detector))
        if use_presidio:
            health.append(
                self._local_health(DetectorSource.PRESIDIO, self.presidio_detector)
            )
        if use_ministral:
            health.append(
                self._ministral_health(lm_studio_host, lm_studio_port, lm_studio_model)
            )
        return health

    @staticmethod
    def _local_health(
        source: DetectorSource, detector: Optional[PIIDetectorProtocol]
    ) -> Dict:
        """Health of an in-process detector.

        An enabled-but-missing instance is itself a fault worth reporting, since it
        silently contributes nothing. Beyond that, a detector whose models load
        lazily is only truly ready once they loaded, so it gets a chance to say so
        through the optional ``check_health`` probe — the same escape hatch the
        remote detectors use, kept out of PIIDetectorProtocol so a detector that
        never claimed to support probing is not blocked on it.
        """
        if detector is None:
            return CompositePIIDetector._not_instantiated_health(source)
        probe = getattr(detector, "check_health", None)
        if probe is None:
            return CompositePIIDetector._health_entry(source, "", None)
        endpoint, failure = probe()
        return CompositePIIDetector._health_entry(source, endpoint, failure)

    def list_lm_studio_models(
        self,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
    ) -> Dict:
        """Ministral-PII models LM Studio has on disk (see ``MinistralDetector.list_models``).

        Without a Ministral detector there is nothing to list, which is reported
        as an error rather than an empty catalogue the operator could mistake for
        "no model downloaded".
        """
        lister = getattr(self.ministral_detector, "list_models", None)
        if lister is None:
            return {
                "endpoint": "",
                "family": "",
                "models": [],
                "error": "Ministral detector not instantiated",
            }
        return lister(lm_studio_host=lm_studio_host, lm_studio_port=lm_studio_port)

    def _ministral_health(
        self,
        lm_studio_host: Optional[str],
        lm_studio_port: Optional[int],
        lm_studio_model: Optional[str] = None,
    ) -> Dict:
        """Probe the remote Ministral endpoint for the configured host/port."""
        if self.ministral_detector is None:
            return self._not_instantiated_health(DetectorSource.MINISTRAL)
        # check_health is not part of PIIDetectorProtocol: a detector that cannot
        # be probed is reported as ready rather than blocking a scan on a check it
        # never claimed to support.
        probe = getattr(self.ministral_detector, "check_health", None)
        if probe is None:
            return self._health_entry(DetectorSource.MINISTRAL, "", None)
        endpoint, failure = probe(lm_studio_host, lm_studio_port, lm_studio_model)
        return self._health_entry(DetectorSource.MINISTRAL, endpoint, failure)

    @staticmethod
    def _not_instantiated_health(source: DetectorSource) -> Dict:
        """Health of a detector switched on but never built."""
        return CompositePIIDetector._health_entry(
            source,
            "",
            DetectorFailure(
                code=DetectorFailureCode.NOT_INSTANTIATED,
                message=f"{source.name} detector not instantiated",
            ),
        )

    @staticmethod
    def _health_entry(
        source: DetectorSource, endpoint: str, failure: Optional[DetectorFailure]
    ) -> Dict:
        """Health entry for one detector, reachable when there is no failure."""
        return {
            "source": source,
            "reachable": failure is None,
            "endpoint": endpoint or "",
            "error": failure.message if failure else "",
            "error_code": failure.code.value if failure else "",
            "error_params": dict(failure.params) if failure else {},
        }

    def _resolve_detector_flags(
        self,
        enable_regex: Optional[bool],
        enable_presidio: Optional[bool],
        enable_ministral: Optional[bool] = None,
    ) -> Tuple[bool, bool, bool]:
        """Resolve runtime overrides into concrete detector activation flags."""
        use_regex = enable_regex if enable_regex is not None else self.enable_regex
        use_presidio = enable_presidio if enable_presidio is not None else self.enable_presidio
        use_ministral = enable_ministral if enable_ministral is not None else self.enable_ministral
        return use_regex, use_presidio, use_ministral

    def _log_active_detectors(
        self, use_regex: bool, use_presidio: bool, use_ministral: bool = False
    ) -> None:
        """Log which detectors are active for debugging."""
        if not self.logger.isEnabledFor(logging.DEBUG):
            return
        names = [
            n for flag, n in [
                (use_regex, "Regex"),
                (use_presidio, "Presidio"),
                (use_ministral, "Ministral"),
            ] if flag
        ]
        self.logger.debug("Detecting PII with active detectors: %s", ', '.join(names) or 'NONE')

    def _collect_detection_results(
        self,
        text: str,
        threshold: Optional[float],
        use_regex: bool,
        use_presidio: bool,
        use_ministral: bool,
        pii_type_configs: Optional[dict],
        ministral_chunk_size: Optional[int] = None,
        ministral_overlap: Optional[int] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        ministral_concurrency: Optional[int] = None,
        lm_studio_model: Optional[str] = None,
    ) -> Tuple[List[Tuple[PIIDetectorProtocol, List[PIIEntity]]], List[Dict]]:
        """Run each enabled detector, collect results and per-detector stats.

        Detectors run sequentially here, so the wall-clock measured around each
        ``_run_*_detection`` call is the real busy time of that detector for
        this request. Returns the (detector, entities) tuples used by the merger
        and a parallel list of stats dicts (one per detector that actually ran).

        A detector that blows up degrades gracefully (the others still run and the
        request still succeeds) but never silently: its failure reason is recorded
        in its stats entry, so callers can tell "found nothing" apart from "could
        not run".
        """
        results: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]] = []
        stats: List[Dict] = []

        def _run(detector: PIIDetectorProtocol, source: DetectorSource, fn) -> None:
            started = time.perf_counter()
            entities, error = self._run_detector_safely(source, fn)
            duration_ms = int((time.perf_counter() - started) * 1000)
            stats.append({
                "source": source,
                "duration_ms": duration_ms,
                "entities_found": len(entities),
                "error": error,
            })
            results.append((detector, entities))

        if use_regex and self.regex_detector:
            _run(self.regex_detector, DetectorSource.REGEX,
                 lambda: self._run_regex_detection(text, threshold))
        if use_presidio and self.presidio_detector:
            _run(self.presidio_detector, DetectorSource.PRESIDIO,
                 lambda: self._run_presidio_detection(text, threshold))
        if use_ministral and self.ministral_detector:
            _run(self.ministral_detector, DetectorSource.MINISTRAL,
                 lambda: self._run_ministral_detection(
                     text, threshold, pii_type_configs, ministral_chunk_size,
                     ministral_overlap, lm_studio_host, lm_studio_port,
                     ministral_concurrency, lm_studio_model))
        return results, stats

    def _run_detector_safely(
        self, source: DetectorSource, fn
    ) -> Tuple[List[PIIEntity], str]:
        """Run one detector, converting a failure into ``([], reason)``.

        Isolating the failure keeps the other detectors and the request itself
        alive, while the returned reason travels in the detector's stats entry up
        to the caller. Returning it instead of only logging it is the whole point:
        a log line on the detector host is invisible to the operator reading the
        scan report.
        """
        try:
            return fn(), ""
        except Exception as exc:
            self.logger.error(
                "%s_DETECTION_FAILED: %s", source.name, exc, exc_info=True
            )
            return [], f"{type(exc).__name__}: {exc}"

    def _log_detection_summary(
        self,
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]],
        merged_entities: List[PIIEntity],
    ) -> None:
        """Log per-detector entity counts after merge."""
        self._log_per_detector_counts(results_per_detector, merged_entities)
        self._log_finding_tracker(results_per_detector, merged_entities)

    def _resolve_detector_label(self, detector: PIIDetectorProtocol) -> str:
        """Return a stable, human-readable label for a detector instance."""
        if detector is self.regex_detector:
            return "Regex"
        if detector is self.presidio_detector:
            return "Presidio"
        if detector is self.ministral_detector:
            return "Ministral"
        return "Unknown"

    def _log_per_detector_counts(
        self,
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]],
        merged_entities: List[PIIEntity],
    ) -> None:
        counts = {
            self.regex_detector: 0,
            self.presidio_detector: 0,
            self.ministral_detector: 0,
        }
        for detector, entities in results_per_detector:
            if detector in counts:
                counts[detector] = len(entities)
        self.logger.debug(
            f"Composite detection complete: {len(merged_entities)} entities "
            f"(Regex: {counts[self.regex_detector]}, "
            f"Presidio: {counts[self.presidio_detector]}, "
            f"Ministral: {counts[self.ministral_detector]})"
        )

    def _log_finding_tracker(
        self,
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]],
        merged_entities: List[PIIEntity],
    ) -> None:
        for detector, entities in results_per_detector:
            branch = self._resolve_detector_label(detector).upper()
            self.logger.info(
                "[FINDING_TRACKER] step=COMPOSITE_%s_RAW count=%d",
                branch, len(entities),
            )
        total_in = sum(len(e) for _, e in results_per_detector)
        self.logger.info(
            "[FINDING_TRACKER] step=COMPOSITE_AFTER_MERGE in=%d out=%d dropped=%d",
            total_in, len(merged_entities), total_in - len(merged_entities),
        )

    def mask_pii(
        self, text: str, threshold: Optional[float] = None
    ) -> Tuple[str, List[PIIEntity]]:
        """
        Mask PII in text using composite detection.

        Args:
            text: Text to mask
            threshold: Optional confidence threshold

        Returns:
            Tuple of (masked_text, detected_entities)
        """
        entities = self.detect_pii(text, threshold)
        masked_text = self._apply_masks(text, entities)

        return masked_text, entities

    def _run_regex_detection(
        self, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """
        Run regex-based detection.

        Failures are handled by :meth:`_run_detector_safely`, which records them
        in this detector's stats entry.

        Args:
            text: Text to analyze
            threshold: Optional confidence threshold

        Returns:
            List of detected entities
        """
        return self.regex_detector.detect_pii(text, threshold)

    def _run_ministral_detection(
        self,
        text: str,
        threshold: Optional[float],
        pii_type_configs: Optional[dict] = None,
        chunk_size: Optional[int] = None,
        overlap: Optional[int] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        concurrency: Optional[int] = None,
        lm_studio_model: Optional[str] = None,
    ) -> List[PIIEntity]:
        """Run Ministral-PII detection.

        Failures (e.g. the remote LM Studio endpoint being unreachable) are
        handled by :meth:`_run_detector_safely`: the request survives without
        Ministral's contribution, and the reason is recorded in this detector's
        stats entry so the caller can report a degraded scan.

        ``chunk_size``/``overlap`` here are the Ministral-specific chunking knobs
        (DB columns ministral_chunk_size / ministral_overlap);
        ``lm_studio_host``/``lm_studio_port`` locate the LM Studio endpoint (DB
        columns lm_studio_host / lm_studio_port). They and ``pii_type_configs``
        are forwarded only when the detector's ``detect_pii`` declares them
        (signature inspection).
        """
        import inspect
        sig = inspect.signature(self.ministral_detector.detect_pii)
        kwargs: dict = {}
        if 'pii_type_configs' in sig.parameters:
            kwargs['pii_type_configs'] = pii_type_configs
        if 'chunk_size' in sig.parameters and chunk_size is not None:
            kwargs['chunk_size'] = chunk_size
        if 'overlap' in sig.parameters and overlap is not None:
            kwargs['overlap'] = overlap
        if 'lm_studio_host' in sig.parameters and lm_studio_host is not None:
            kwargs['lm_studio_host'] = lm_studio_host
        if 'lm_studio_port' in sig.parameters and lm_studio_port is not None:
            kwargs['lm_studio_port'] = lm_studio_port
        if 'concurrency' in sig.parameters and concurrency is not None:
            kwargs['concurrency'] = concurrency
        if 'lm_studio_model' in sig.parameters and lm_studio_model:
            kwargs['lm_studio_model'] = lm_studio_model
        return self.ministral_detector.detect_pii(text, threshold, **kwargs)

    def _run_presidio_detection(
        self, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """
        Run Presidio-based detection.

        Failures are handled by :meth:`_run_detector_safely`, which records them
        in this detector's stats entry.

        Args:
            text: Text to analyze
            threshold: Optional confidence threshold

        Returns:
            List of detected entities
        """
        return self.presidio_detector.detect_pii(text, threshold)

    def _apply_masks(self, text: str, entities: List[PIIEntity]) -> str:
        """
        Apply masks to detected entities.

        Args:
            text: Original text
            entities: Detected entities

        Returns:
            Masked text with PII replaced by type labels
        """
        if not entities:
            return text

        # Sort by start position for linear scan
        sorted_entities = sorted(entities, key=lambda x: x.start)

        parts = []
        last_pos = 0

        for entity in sorted_entities:
            # Skip if entity overlaps with previous one
            if entity.start < last_pos:
                continue

            parts.append(text[last_pos:entity.start])
            parts.append(f"[{entity.pii_type}]")
            last_pos = entity.end

        # Append remaining text
        parts.append(text[last_pos:])

        return "".join(parts)


def should_use_composite_detector() -> bool:
    """
    Determine if composite detector should be used.

    Always returns True to enable dynamic runtime activation of detectors.

    Business Rule: CompositePIIDetector is always instantiated to allow runtime
    activation/deactivation of Regex, Presidio and Ministral detectors via
    database config. The actual activation is controlled by the
    `pii_detection_config` table fields (presidio_enabled, regex_enabled,
    ministral_enabled), fetched at request time via `fetch_config_from_db=true`
    in gRPC.

    Returns:
        True (always) to enable runtime detector activation via database
    """
    logger.debug(
        "Composite detector always enabled for runtime activation via database config"
    )
    return True


def _load_detection_config() -> Tuple[bool, bool]:
    """
    Return default activation states for detectors.

    Note: Actual activation is now controlled by database config at runtime.
    These defaults are used when database config is not available.
    Both detectors are disabled by default to avoid unexpected behavior.

    Returns:
        Tuple of (regex_enabled_default, presidio_enabled_default)
        Both False to require explicit database activation.
    """
    # Default: both disabled (require explicit database activation)
    logger.debug(
        "Default detector activation: Regex=False, Presidio=False "
        "(use database config for runtime activation)"
    )
    return False, False


def _create_regex_detector_if_enabled(regex_enabled: bool) -> Optional[RegexDetector]:
    """
    Create RegexDetector instance (always created for runtime activation).

    IMPORTANT: This function now ALWAYS creates RegexDetector instance regardless
    of the regex_enabled parameter. The parameter is kept for backward compatibility
    but only used for logging purposes.

    Business rule: Detectors are instantiated at startup to avoid recreation overhead,
    but can be selectively activated at runtime via database configuration without
    requiring service restart.

    Args:
        regex_enabled: TOML configuration flag (for logging only)

    Returns:
        RegexDetector instance or None (only None if creation fails)
    """
    try:
        detector = RegexDetector()
        if regex_enabled:
            logger.debug("Created RegexDetector for composite (enabled by default in TOML)")
        else:
            logger.debug("Created RegexDetector for composite (disabled by default in TOML, can be activated at runtime)")
        return detector
    except Exception as e:
        logger.warning(f"Failed to create RegexDetector: {e}")
        return None


def _create_presidio_detector_if_enabled(presidio_enabled: bool) -> Optional[PresidioDetector]:
    """
    Create PresidioDetector instance (always created for runtime activation if available).

    IMPORTANT: This function now ALWAYS creates PresidioDetector instance (if library available)
    regardless of the presidio_enabled parameter. The parameter is kept for backward compatibility
    but only used for logging purposes.

    Business rule: Detectors are instantiated at startup to avoid recreation overhead,
    but can be selectively activated at runtime via database configuration without
    requiring service restart.

    Args:
        presidio_enabled: TOML configuration flag (for logging only)

    Returns:
        PresidioDetector instance or None (None only if library unavailable or creation fails)
    """
    if not PRESIDIO_AVAILABLE:
        logger.warning("PresidioDetector not available (library not installed)")
        return None

    try:
        detector = PresidioDetector()
        if presidio_enabled:
            logger.debug("Created PresidioDetector for composite (enabled by default in TOML)")
        else:
            logger.debug("Created PresidioDetector for composite (disabled by default in TOML, can be activated at runtime)")
        return detector
    except Exception as e:
        logger.warning(f"Failed to create PresidioDetector: {e}")
        return None


def _create_ministral_detector_if_available() -> Optional[PIIDetectorProtocol]:
    """
    Create a ``MinistralDetector`` instance. Always instantiated so it can be
    toggled on at runtime via the ``ministral_enabled`` DB flag. Ministral-PII
    is served by a remote OpenAI-compatible endpoint, so there is no local model
    to load (``download_model`` / ``load_model`` are no-ops). Returns ``None`` if
    instantiation fails, keeping startup resilient.
    """
    try:
        from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector

        detector = MinistralDetector()
        logger.debug("Created MinistralDetector instance (remote endpoint)")
        return detector
    except Exception as exc:
        logger.warning(f"Failed to create MinistralDetector: {exc}")
        return None


def create_composite_detector() -> CompositePIIDetector:
    """
    Factory function to create composite detector with default configuration.

    Returns:
        Configured CompositePIIDetector instance
    """
    # Load detection configuration
    regex_enabled, presidio_enabled = _load_detection_config()

    # Create detectors based on configuration
    regex_detector = _create_regex_detector_if_enabled(regex_enabled)
    presidio_detector = _create_presidio_detector_if_enabled(presidio_enabled)
    ministral_detector = _create_ministral_detector_if_available()

    # Create merger with provenance logging
    merger = DetectionMerger(log_provenance=True)

    # Create composite detector
    composite = CompositePIIDetector(
        regex_detector=regex_detector,
        presidio_detector=presidio_detector,
        ministral_detector=ministral_detector,
        merger=merger,
        enable_regex=regex_enabled and regex_detector is not None,
        enable_presidio=presidio_enabled and presidio_detector is not None,
        # Ministral-PII: disabled at startup, activated at request time via DB
        # flag (remote endpoint, nothing to load eagerly).
        enable_ministral=False,
    )

    return composite
