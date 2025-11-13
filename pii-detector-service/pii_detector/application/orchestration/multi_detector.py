"""
Composite multi-model PII detector.

Goal: Provide an opt-in, clean way to run several local HF models in parallel and
merge the results into the existing PIIEntity structure without duplicates.

Design principles:
- Keep current single-model behavior untouched by default (opt-in via env).
- Reuse existing PIIDetector for each backend model to avoid duplicating logic.
- Simple parallelization per model using ThreadPoolExecutor.
- Deterministic deduplication by (start, end, pii_type, text) keeping max score.
- Minimal public API parity: download_model, load_model, detect_pii, mask_pii.

Environment variables:
- MULTI_DETECTOR_ENABLED: "true" (case-insensitive) to activate composite.
- MULTI_DETECTOR_MODELS: semicolon-separated list of HF repo IDs.
  Defaults to [primary_model, "Ar86Bat/multilang-pii-ner"].
- MULTI_DETECTOR_LOG_PROVENANCE: "true" to log source model for each entity and merge decisions.
"""
from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Iterable, List, Optional, Tuple, Dict

from domain.service.detection_merger import DetectionMerger
from application.factory.detector_factory import DetectorFactory, create_default_factory
from infrastructure.detector.pii_detector import DetectionConfig, PIIEntity
from domain.port.pii_detector_protocol import PIIDetectorProtocol
from application.config import get_config as get_app_config

logger = logging.getLogger(__name__)


def _get_provenance_logging() -> bool:
    """Get provenance logging setting from centralized configuration."""
    try:
        cfg = get_app_config()
        return cfg.detection.multi_detector_log_provenance
    except (ValueError, AttributeError, ImportError):
        return False


# Toggle detailed provenance logging of model source for each entity
PROVENANCE_LOG_PROVENANCE = _get_provenance_logging()


def get_multi_model_ids_from_config() -> List[str]:
    """Resolve multi-model list from llm.toml configuration.

    Returns list of model_ids for all enabled models, sorted by priority.
    """
    from application.config.detection_policy import _load_llm_config, get_enabled_models
    
    try:
        config = _load_llm_config()
        enabled_models = get_enabled_models(config)
        
        # Return list of model_ids sorted by priority
        return [model["model_id"] for model in enabled_models]
        
    except Exception as e:
        logger.error(f"Failed to load model configuration: {e}")
        # Fallback to default single model
        return ["iiiorg/piiranha-v1-detect-personal-information"]


def should_use_multi_detector() -> bool:
    """Determine if multi-detector should be used based on llm.toml configuration.
    
    Returns True if:
    - multi_detector_enabled is true in config
    - AND at least 2 models are enabled
    """
    from application.config.detection_policy import _load_llm_config, get_enabled_models
    
    try:
        config = _load_llm_config()
        
        # Check if multi-detector is enabled
        multi_enabled = config.get("detection", {}).get("multi_detector_enabled", False)
        if not multi_enabled:
            return False
        
        # Check if at least 2 models are enabled
        enabled_models = get_enabled_models(config)
        return len(enabled_models) >= 2
        
    except Exception as e:
        logger.warning(f"Failed to determine multi-detector status: {e}")
        return False


class MultiModelPIIDetector:
    """Composite detector that orchestrates multiple PIIDetector backends.

    This class mirrors the subset of PIIDetector's API used by the gRPC service
    to minimize integration changes.
    """

    def __init__(
        self,
        model_ids: Iterable[str],
        device: Optional[str] = None,
        merger: Optional[DetectionMerger] = None,
        factory: Optional[DetectorFactory] = None
    ):
        self.model_ids = list(model_ids)
        self.device = device
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self._factory = factory or create_default_factory()
        self.detectors = [
            self._create_detector(m, device) for m in self.model_ids
        ]
        self._merger = merger or DetectionMerger(log_provenance=PROVENANCE_LOG_PROVENANCE)
        self.logger.info(f"Initialized MultiModelPIIDetector with models: {self.model_ids}")
    
    def _create_detector(self, model_id: str, device: Optional[str] = None) -> PIIDetectorProtocol:
        """
        Create appropriate detector using the factory.
        
        Delegates detector creation to DetectorFactory, decoupling
        instantiation logic from orchestration.
        
        Args:
            model_id: Model identifier
            device: Device allocation
            
        Returns:
            Detector instance conforming to PIIDetectorProtocol
        """
        config = DetectionConfig(model_id=model_id, device=device)
        self.logger.debug(f"Creating detector for {model_id}")
        return self._factory.create(model_id=model_id, config=config)

    # Lifecycle operations -------------------------------------------------
    def download_model(self) -> None:
        for det in self.detectors:
            try:
                det.download_model()
            except Exception as e:
                # Do not fail whole composition; log and continue
                self.logger.warning(f"Download failed for {det.model_id}: {e}")

    def load_model(self) -> None:
        for det in self.detectors:
            try:
                det.load_model()
            except Exception as e:
                self.logger.warning(f"Load failed for {det.model_id}: {e}")

    # Inference operations -------------------------------------------------
    def detect_pii(self, text: str, threshold: Optional[float] = None) -> List[PIIEntity]:
        """Run detection in parallel across models and merge results without duplicates.
        
        Business process:
        1. Execute detection across all models in parallel
        2. Collect and log detection results with provenance tracking
        3. Delegate merging and overlap resolution to DetectionMerger
        
        Args:
            text: Text to analyze for PII
            threshold: Optional confidence threshold for detection
            
        Returns:
            Deduplicated and overlap-resolved list of PII entities
        """
        results_per_detector = self._collect_detection_results(text, threshold)
        return self._merger.merge(results_per_detector)

    def _collect_detection_results(
        self, text: str, threshold: Optional[float]
    ) -> List[Tuple[PIIDetectorProtocol, List[PIIEntity]]]:
        """Execute detection in parallel and collect results with provenance logging.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            List of tuples (detector, entities) for each model
        """
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]] = []
        
        with ThreadPoolExecutor(max_workers=max(1, len(self.detectors))) as executor:
            futures = {executor.submit(self._run_detector_safely, det, text, threshold): det 
                      for det in self.detectors}
            
            for future in as_completed(futures):
                detector = futures[future]
                entities = future.result()
                results_per_detector.append((detector, entities))
                self._log_detection_provenance(detector, entities)
        
        return results_per_detector

    def _run_detector_safely(
        self, detector: PIIDetectorProtocol, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """Execute detection on a single detector with error handling.
        
        Args:
            detector: Detector instance to run
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            List of detected entities, empty list if detection fails
        """
        try:
            return detector.detect_pii(text, threshold)
        except Exception as e:  # pragma: no cover - defensive path
            self.logger.warning(f"Detection failed for {detector.model_id}: {e}")
            return []

    def _log_detection_provenance(self, detector: PIIDetectorProtocol, entities: List[PIIEntity]) -> None:
        """Log provenance information for detected entities if enabled.
        
        Args:
            detector: Detector that produced the entities
            entities: Detected entities to log
        """
        if not PROVENANCE_LOG_PROVENANCE:
            return
        
        for entity in entities:
            self._log_entity_provenance(detector.model_id, entity)

    def _log_entity_provenance(self, model_id: str, entity: PIIEntity) -> None:
        """Log provenance for a single entity with safe attribute access.
        
        Args:
            model_id: Model identifier
            entity: Entity to log
        """
        try:
            self.logger.info(
                "[PII-PROVENANCE] model=%s type=%s text=%s start=%s end=%s score=%.4f",
                model_id,
                getattr(entity, 'pii_type', getattr(entity, 'type', '')),
                getattr(entity, 'text', ''),
                getattr(entity, 'start', -1),
                getattr(entity, 'end', -1),
                float(getattr(entity, 'score', 0.0)),
            )
        except Exception:
            # Never break detection due to logging errors
            pass

    def mask_pii(self, text: str, threshold: Optional[float] = None) -> Tuple[str, List[PIIEntity]]:
        entities = self.detect_pii(text, threshold)
        # Apply masking in descending order of start index to preserve spans
        entities_sorted = sorted(entities, key=lambda x: x.start, reverse=True)
        masked_text = text
        for entity in entities_sorted:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[: entity.start] + mask + masked_text[entity.end :]
        return masked_text, entities

    @property
    def model_id(self) -> str:
        # Return primary model id for compatibility (first in list)
        return self.detectors[0].model_id if self.detectors else ""
