"""
Detector Factory for PII detection.

Provides a centralized registry for creating PII detector instances,
implementing the Factory pattern to decouple detector instantiation
from orchestration logic.
"""
from __future__ import annotations

import logging
from typing import Callable, Dict, Optional

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.port.pii_detector_protocol import PIIDetectorProtocol
from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector

logger = logging.getLogger(__name__)


class DetectorFactory:
    """
    Factory for creating PII detector instances.
    
    Implements the Factory pattern to centralize detector creation logic,
    allowing new detector types to be added without modifying the orchestration code.
    
    Business value:
    - Decouples detector instantiation from MultiModelPIIDetector
    - Enables plugin-like architecture for detector types
    - Simplifies configuration-driven detector selection
    
    Example:
        >>> factory = DetectorFactory()
        >>> factory.register("gliner-pii", lambda config: GLiNERDetector(config=config))
        >>> detector = factory.create("gliner-pii", config=my_config)
    """
    
    def __init__(self):
        """Initialize empty detector registry."""
        self._registry: Dict[str, Callable[..., PIIDetectorProtocol]] = {}
        self._logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self._logger.debug("Initialized DetectorFactory with empty registry")
    
    def register(
        self,
        detector_type: str,
        builder: Callable[..., PIIDetectorProtocol]
    ) -> None:
        """
        Register a detector builder function.
        
        Args:
            detector_type: Unique identifier for this detector type
            builder: Callable that creates a detector instance
            
        Raises:
            ValueError: If detector_type is already registered
            
        Example:
            >>> factory.register(
            ...     "gliner-pii",
            ...     lambda config: GLiNERDetector(config=config)
            ... )
        """
        if detector_type in self._registry:
            raise ValueError(f"Detector type '{detector_type}' is already registered")
        
        self._registry[detector_type] = builder
        self._logger.info(f"Registered detector type: {detector_type}")
    
    def create(
        self,
        model_id: str,
        config: Optional[DetectionConfig] = None,
        **kwargs
    ) -> PIIDetectorProtocol:
        """
        Create a detector instance based on model ID.
        
        Determines detector type from model_id pattern and creates
        the appropriate detector using registered builders.
        
        Args:
            model_id: Model identifier (determines detector type)
            config: Optional detection configuration
            **kwargs: Additional parameters passed to builder
            
        Returns:
            Configured detector instance
            
        Raises:
            ValueError: If no suitable detector type found for model_id
            
        Business rules:
        - "gliner" in model_id → GLiNER detector
        - Otherwise → default PII detector
        """
        detector_type = self._determine_detector_type(model_id)
        
        if detector_type not in self._registry:
            raise ValueError(
                f"No detector registered for type '{detector_type}'. "
                f"Available types: {list(self._registry.keys())}"
            )
        
        builder = self._registry[detector_type]
        
        # Build detector with provided parameters
        if config is None:
            config = DetectionConfig(model_id=model_id)
        
        self._logger.debug(f"Creating detector of type '{detector_type}' for model '{model_id}'")
        return builder(config=config, **kwargs)
    
    def _determine_detector_type(self, model_id: str) -> str:
        """
        Determine detector type from model identifier.

        Args:
            model_id: Model identifier

        Returns:
            Detector type key for registry lookup

        Business rules:
        - "multipass" in model_id → MultiPassGlinerDetector
        - "gliner" in model_id → GLiNERDetector
        - Otherwise → default PII detector
        """
        model_id_lower = model_id.lower()

        if "multipass" in model_id_lower:
            return "multipass-gliner"
        elif "gliner" in model_id_lower:
            return "gliner"
        else:
            return "default"
    
    def is_registered(self, detector_type: str) -> bool:
        """
        Check if a detector type is registered.
        
        Args:
            detector_type: Type to check
            
        Returns:
            True if type is registered, False otherwise
        """
        return detector_type in self._registry
    
    def get_registered_types(self) -> list[str]:
        """
        Get list of all registered detector types.
        
        Returns:
            List of registered detector type identifiers
        """
        return list(self._registry.keys())
    
    def unregister(self, detector_type: str) -> None:
        """
        Remove a detector type from the registry.
        
        Args:
            detector_type: Type to unregister
            
        Raises:
            ValueError: If detector_type is not registered
        """
        if detector_type not in self._registry:
            raise ValueError(f"Detector type '{detector_type}' is not registered")
        
        del self._registry[detector_type]
        self._logger.info(f"Unregistered detector type: {detector_type}")


def create_default_factory() -> DetectorFactory:
    """
    Create factory with default detector types registered.

    Registers built-in detector types:
    - "gliner": GLiNERDetector for GLiNER models
    - "multipass-gliner": MultiPassGlinerDetector for multi-category parallel detection
    - "regex": RegexDetector for regex-based pattern matching
    - "default": PIIDetector for standard HuggingFace models

    Returns:
        Configured DetectorFactory instance
    """
    from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector
    from pii_detector.infrastructure.detector.multi_pass_gliner_detector import MultiPassGlinerDetector
    from pii_detector.infrastructure.detector.pii_detector import PIIDetector
    from pii_detector.infrastructure.detector.regex_detector import RegexDetector

    factory = DetectorFactory()

    # Register GLiNER detector
    factory.register(
        "gliner",
        lambda config: GLiNERDetector(config=config)
    )

    # Register Multi-Pass GLiNER detector
    # This detector runs GLiNER in parallel across themed label categories
    # and resolves conflicts deterministically
    factory.register(
        "multipass-gliner",
        lambda config: MultiPassGlinerDetector(config=config)
    )

    # Register regex detector
    factory.register(
        "regex",
        lambda config: RegexDetector(config=config)
    )

    # Register default PII detector
    factory.register(
        "default",
        lambda config: PIIDetector(config=config)
    )

    logger.info("Created default factory with 'gliner', 'multipass-gliner', 'regex', and 'default' detector types")
    return factory
