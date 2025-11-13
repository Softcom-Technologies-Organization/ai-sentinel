"""
Composite PII detector orchestrating ML-based and regex-based detection.

This module provides CompositePIIDetector that combines:
- MultiModelPIIDetector: Orchestrates multiple ML models (GLiNER, Piiranha, etc.)
- RegexDetector: Deterministic pattern matching for structured PII

Business value:
- Leverages strengths of both ML (contextual understanding) and regex (precision)
- Configurable fusion strategies for optimal accuracy
- Extensible architecture for adding new detector types
"""

from __future__ import annotations

import logging
from typing import List, Optional, Tuple

from domain.service.detection_merger import DetectionMerger
from domain.port.pii_detector_protocol import PIIDetectorProtocol
from infrastructure.detector.regex_detector import RegexDetector
from service.detector.models import PIIEntity

try:
    from infrastructure.detector.presidio_detector import PresidioDetector
    PRESIDIO_AVAILABLE = True
except ImportError:
    PRESIDIO_AVAILABLE = False
    PresidioDetector = None


logger = logging.getLogger(__name__)


class CompositePIIDetector:
    """
    Composite detector orchestrating ML and regex-based PII detection.
    
    This detector implements a hybrid approach that combines:
    1. ML-based detection (via MultiModelPIIDetector) for contextual PII
    2. Regex-based detection for structured, deterministic patterns
    
    Business rules:
    - ML detectors run in parallel via MultiModelPIIDetector
    - Regex detector runs independently for fast pattern matching
    - Results are merged using configurable fusion strategies
    - Overlaps are resolved based on detector priorities
    
    Architecture:
    - Implements PIIDetectorProtocol for transparent integration
    - Delegates ML orchestration to MultiModelPIIDetector
    - Uses DetectionMerger for result fusion
    """
    
    def __init__(
        self,
        ml_detector: Optional[PIIDetectorProtocol] = None,
        regex_detector: Optional[RegexDetector] = None,
        presidio_detector: Optional[PresidioDetector] = None,
        merger: Optional[DetectionMerger] = None,
        enable_regex: bool = True,
        enable_presidio: bool = True
    ):
        """
        Initialize composite detector.
        
        Args:
            ml_detector: ML-based detector (MultiModelPIIDetector or single detector). Can be None if only Presidio/Regex is used.
            regex_detector: Regex-based detector
            presidio_detector: Presidio-based detector
            merger: Detection merger for result fusion
            enable_regex: Enable regex detection (default: True)
            enable_presidio: Enable Presidio detection (default: True)
        """
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        
        # Initialize ML detector
        self.ml_detector = ml_detector
        if self.ml_detector is None:
            self.logger.info("No ML detector provided - using rule-based detection only (Presidio/Regex)")
        
        # Initialize regex detector if enabled
        self.regex_detector = regex_detector
        if enable_regex and self.regex_detector is None:
            try:
                self.regex_detector = RegexDetector()
                self.logger.info("RegexDetector initialized successfully")
            except Exception as e:
                self.logger.warning(f"Failed to initialize RegexDetector: {e}")
        
        # Set enable_regex based on actual availability
        self.enable_regex = enable_regex and self.regex_detector is not None
        
        # Initialize Presidio detector if enabled
        self.enable_presidio = enable_presidio and PRESIDIO_AVAILABLE
        self.presidio_detector = presidio_detector
        if self.enable_presidio and self.presidio_detector is None:
            try:
                self.presidio_detector = PresidioDetector()
                self.logger.info("PresidioDetector initialized successfully")
            except Exception as e:
                self.logger.warning(f"Failed to initialize PresidioDetector: {e}")
                self.enable_presidio = False
        
        # Initialize merger
        self._merger = merger or DetectionMerger(log_provenance=True)
        
        self.logger.info(
            f"CompositePIIDetector initialized: "
            f"ML={'enabled' if ml_detector else 'disabled'}, "
            f"Regex={'enabled' if self.enable_regex else 'disabled'}, "
            f"Presidio={'enabled' if self.enable_presidio else 'disabled'}"
        )
    
    @property
    def model_id(self) -> str:
        """Get composite model identifier."""
        if self.ml_detector:
            return f"composite-{self.ml_detector.model_id}"
        return "composite-regex-only"
    
    def download_model(self) -> None:
        """Download models for all underlying detectors."""
        if self.ml_detector:
            try:
                self.ml_detector.download_model()
            except Exception as e:
                self.logger.warning(f"ML detector download failed: {e}")
        
        if self.regex_detector:
            self.regex_detector.download_model()  # No-op for regex
        
        if self.presidio_detector:
            # Presidio doesn't need download
            pass
    
    def load_model(self) -> None:
        """Load models for all underlying detectors."""
        if self.ml_detector:
            try:
                self.ml_detector.load_model()
            except Exception as e:
                self.logger.error(f"ML detector load failed: {e}")
                raise
        
        if self.regex_detector:
            self.regex_detector.load_model()  # No-op for regex
        
        if self.presidio_detector:
            # Presidio models are loaded on first use
            pass
    
    def detect_pii(
        self, text: str, threshold: Optional[float] = None
    ) -> List[PIIEntity]:
        """
        Detect PII using both ML and regex detectors.
        
        Business process:
        1. Execute ML detection (if enabled)
        2. Execute regex detection (if enabled)
        3. Merge results with priority-based fusion
        4. Return deduplicated entities
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            Merged list of PII entities
        """
        if not text:
            return []
        
        # Collect results from all detectors
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]] = []
        
        # Execute ML detection
        if self.ml_detector:
            ml_entities = self._run_ml_detection(text, threshold)
            results_per_detector.append((self.ml_detector, ml_entities))
        
        # Execute regex detection
        if self.enable_regex and self.regex_detector:
            regex_entities = self._run_regex_detection(text, threshold)
            results_per_detector.append((self.regex_detector, regex_entities))
        
        # Execute Presidio detection
        if self.enable_presidio and self.presidio_detector:
            presidio_entities = self._run_presidio_detection(text, threshold)
            results_per_detector.append((self.presidio_detector, presidio_entities))
        
        # Merge results
        if not results_per_detector:
            self.logger.warning("No detectors available")
            return []
        
        merged_entities = self._merger.merge(results_per_detector)
        
        # Count entities per detector for logging
        ml_count = len(results_per_detector[0][1]) if self.ml_detector else 0
        regex_count = 0
        presidio_count = 0
        
        for detector, entities in results_per_detector:
            if detector == self.regex_detector:
                regex_count = len(entities)
            elif detector == self.presidio_detector:
                presidio_count = len(entities)
        
        self.logger.info(
            f"Composite detection complete: {len(merged_entities)} entities "
            f"(ML: {ml_count}, Regex: {regex_count}, Presidio: {presidio_count})"
        )
        
        return merged_entities
    
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
    
    def _run_ml_detection(
        self, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """
        Run ML-based detection with error handling.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            List of detected entities (empty if detection fails)
        """
        try:
            return self.ml_detector.detect_pii(text, threshold)
        except Exception as e:
            self.logger.error(f"ML detection failed: {e}")
            return []
    
    def _run_regex_detection(
        self, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """
        Run regex-based detection with error handling.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            List of detected entities (empty if detection fails)
        """
        try:
            return self.regex_detector.detect_pii(text, threshold)
        except Exception as e:
            self.logger.error(f"Regex detection failed: {e}")
            return []
    
    def _run_presidio_detection(
        self, text: str, threshold: Optional[float]
    ) -> List[PIIEntity]:
        """
        Run Presidio-based detection with error handling.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            
        Returns:
            List of detected entities (empty if detection fails)
        """
        try:
            return self.presidio_detector.detect_pii(text, threshold)
        except Exception as e:
            self.logger.error(f"Presidio detection failed: {e}")
            return []
    
    def _apply_masks(self, text: str, entities: List[PIIEntity]) -> str:
        """
        Apply masks to detected entities.
        
        Args:
            text: Original text
            entities: Detected entities
            
        Returns:
            Masked text with PII replaced by type labels
        """
        # Sort by position (reverse order for in-place replacement)
        sorted_entities = sorted(entities, key=lambda x: x.start, reverse=True)
        
        masked_text = text
        for entity in sorted_entities:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[:entity.start] + mask + masked_text[entity.end:]
        
        return masked_text


def should_use_composite_detector() -> bool:
    """
    Determine if composite detector should be used.
    
    Returns True if any of these conditions are met:
    - Regex detection is enabled in global configuration (detection-settings.toml)
    - OR Presidio detection is enabled in global configuration (detection-settings.toml)
    
    Note: Individual detector 'enabled' flags in model configs are no longer used.
    Only detection-settings.toml controls which detectors are active.
    
    Returns:
        True if composite detector should be used
    """
    try:
        from application.config.detection_policy import _load_llm_config
        
        config = _load_llm_config()
        detection_config = config.get("detection", {})
        
        # Check detection flags from detection-settings.toml only
        regex_detection_enabled = detection_config.get("regex_detection_enabled", False)
        presidio_detection_enabled = detection_config.get("presidio_detection_enabled", False)
        
        # Use composite if either regex or presidio is enabled
        if regex_detection_enabled or presidio_detection_enabled:
            enabled_detectors = []
            if regex_detection_enabled:
                enabled_detectors.append("Regex")
            if presidio_detection_enabled:
                enabled_detectors.append("Presidio")
            logger.info(f"Composite detector enabled with: {', '.join(enabled_detectors)}")
            return True
        
        logger.info("Neither Regex nor Presidio detection enabled in detection-settings.toml")
        return False
        
    except Exception as e:
        logger.warning(f"Failed to determine composite detector status: {e}")
        return False


def _load_detection_config() -> Tuple[bool, bool]:
    """
    Load detection configuration flags from detection-settings.toml.
    
    Returns:
        Tuple of (regex_enabled, presidio_enabled)
    """
    from application.config.detection_policy import _load_llm_config
    
    try:
        config = _load_llm_config()
        detection_config = config.get("detection", {})
        regex_enabled = detection_config.get("regex_detection_enabled", False)
        presidio_enabled = detection_config.get("presidio_detection_enabled", False)
        return regex_enabled, presidio_enabled
    except Exception as e:
        logger.warning(f"Failed to load detection config: {e}")
        return False, False


def _create_regex_detector_if_enabled(regex_enabled: bool) -> Optional[RegexDetector]:
    """
    Create RegexDetector instance if enabled in configuration.
    
    Args:
        regex_enabled: Whether regex detection is enabled
        
    Returns:
        RegexDetector instance or None
    """
    if not regex_enabled:
        logger.info("RegexDetector disabled in configuration")
        return None
    
    try:
        detector = RegexDetector()
        logger.info("Created RegexDetector for composite")
        return detector
    except Exception as e:
        logger.warning(f"Failed to create RegexDetector: {e}")
        return None


def _create_presidio_detector_if_enabled(presidio_enabled: bool) -> Optional[PresidioDetector]:
    """
    Create PresidioDetector instance if enabled and available.
    
    Args:
        presidio_enabled: Whether Presidio detection is enabled
        
    Returns:
        PresidioDetector instance or None
    """
    if not presidio_enabled:
        logger.info("PresidioDetector disabled in configuration")
        return None
    
    if not PRESIDIO_AVAILABLE:
        logger.warning("PresidioDetector enabled in config but not available")
        return None
    
    try:
        detector = PresidioDetector()
        logger.info("Created PresidioDetector for composite")
        return detector
    except Exception as e:
        logger.warning(f"Failed to create PresidioDetector: {e}")
        return None


def create_composite_detector(
    ml_detector: Optional[PIIDetectorProtocol] = None
) -> CompositePIIDetector:
    """
    Factory function to create composite detector with default configuration.
    
    Args:
        ml_detector: Optional ML detector to use (creates default if None)
        
    Returns:
        Configured CompositePIIDetector instance
    """
    # Load detection configuration
    regex_enabled, presidio_enabled = _load_detection_config()
    
    # Create detectors based on configuration
    regex_detector = _create_regex_detector_if_enabled(regex_enabled)
    presidio_detector = _create_presidio_detector_if_enabled(presidio_enabled)
    
    # Create merger with provenance logging
    merger = DetectionMerger(log_provenance=True)
    
    # Create composite detector
    composite = CompositePIIDetector(
        ml_detector=ml_detector,
        regex_detector=regex_detector,
        presidio_detector=presidio_detector,
        merger=merger,
        enable_regex=regex_enabled and regex_detector is not None,
        enable_presidio=presidio_enabled and presidio_detector is not None
    )
    
    return composite
