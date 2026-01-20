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

from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.port.pii_detector_protocol import PIIDetectorProtocol
from pii_detector.domain.service.detection_merger import DetectionMerger
from pii_detector.infrastructure.detector.regex_detector import RegexDetector

try:
    from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector
    PRESIDIO_AVAILABLE = True
    print("[PRESIDIO_IMPORT] SUCCESS - PresidioDetector imported successfully")
except ImportError as e:
    PRESIDIO_AVAILABLE = False
    PresidioDetector = None
    print(f"[PRESIDIO_IMPORT] FAILED - ImportError: {e}")
    print(f"[PRESIDIO_IMPORT] PRESIDIO_AVAILABLE set to False")


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
        self, 
        text: str, 
        threshold: Optional[float] = None,
        enable_ml: Optional[bool] = None,
        enable_regex: Optional[bool] = None,
        enable_presidio: Optional[bool] = None,
        pii_type_configs: Optional[dict] = None,
        chunk_size: Optional[int] = None
    ) -> List[PIIEntity]:
        """
        Detect PII using both ML and regex detectors.
        
        Business process:
        1. Execute ML detection (if enabled)
        2. Execute regex detection (if enabled)
        3. Execute Presidio detection (if enabled)
        4. Merge results with priority-based fusion
        5. Return deduplicated entities
        
        Business rule: Dynamic configuration allows runtime control of detectors
        based on database configuration without service restart. Detectors remain
        loaded in memory for performance but can be selectively activated per request.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            enable_ml: Override ML detector activation (None=use default, True/False=force)
            enable_regex: Override regex detector activation (None=use default, True/False=force)
            enable_presidio: Override Presidio detector activation (None=use default, True/False=force)
            pii_type_configs: Optional fresh PII type configurations from database
            
        Returns:
            Merged list of PII entities
        """
        if not text:
            return []
        
        # Determine which detectors to use (runtime override or default config)
        use_ml = enable_ml if enable_ml is not None else (self.ml_detector is not None)
        use_regex = enable_regex if enable_regex is not None else self.enable_regex
        use_presidio = enable_presidio if enable_presidio is not None else self.enable_presidio
        
        # Log active detector configuration for debugging
        active_detectors = []
        if use_ml:
            active_detectors.append("ML")
        if use_regex:
            active_detectors.append("Regex")
        if use_presidio:
            active_detectors.append("Presidio")
        
        if self.logger.isEnabledFor(logging.DEBUG):
            detectors_str = ', '.join(active_detectors) if active_detectors else 'NONE'
            self.logger.debug("Detecting PII with active detectors: %s", detectors_str)
        
        # Collect results from all detectors
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]] = []
        
        # Execute ML detection
        if use_ml and self.ml_detector:
            ml_entities = self._run_ml_detection(text, threshold, pii_type_configs, chunk_size)
            results_per_detector.append((self.ml_detector, ml_entities))
        
        # Execute regex detection
        if use_regex and self.regex_detector:
            regex_entities = self._run_regex_detection(text, threshold)
            results_per_detector.append((self.regex_detector, regex_entities))
        
        # Execute Presidio detection
        if use_presidio and self.presidio_detector:
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
        self, 
        text: str, 
        threshold: Optional[float],
        pii_type_configs: Optional[dict] = None,
        chunk_size: Optional[int] = None
    ) -> List[PIIEntity]:
        """
        Run ML-based detection with error handling.
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold
            pii_type_configs: Optional PII type configs for dynamic settings
            chunk_size: Optional chunk size for optimizing passes
            
        Returns:
            List of detected entities (empty if detection fails)
        """
        try:
            # Check if ML detector supports parameters using inspection
            import inspect
            sig = inspect.signature(self.ml_detector.detect_pii)
            
            kwargs = {}
            if 'pii_type_configs' in sig.parameters:
                kwargs['pii_type_configs'] = pii_type_configs
                
            if 'chunk_size' in sig.parameters and chunk_size is not None:
                kwargs['chunk_size'] = chunk_size
                
            return self.ml_detector.detect_pii(text, threshold, **kwargs)
            
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
    activation/deactivation of Regex and Presidio detectors via database config.
    The actual activation is controlled by `pii_detection_config` table fields:
    - gliner_enabled
    - presidio_enabled  
    - regex_enabled
    
    These are fetched at request time via `fetch_config_from_db=true` in gRPC.
    
    Returns:
        True (always) to enable runtime detector activation via database
    """
    logger.info(
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
            logger.info("Created RegexDetector for composite (enabled by default in TOML)")
        else:
            logger.info("Created RegexDetector for composite (disabled by default in TOML, can be activated at runtime)")
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
            logger.info("Created PresidioDetector for composite (enabled by default in TOML)")
        else:
            logger.info("Created PresidioDetector for composite (disabled by default in TOML, can be activated at runtime)")
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