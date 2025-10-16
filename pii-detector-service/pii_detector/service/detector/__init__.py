"""
PII Detector Package.

This package contains the implementation of the PII detection functionality.
Exports all public classes for backward compatibility.
"""

from .detection_merger import DetectionMerger
from .detector_factory import DetectorFactory, create_default_factory
from .entity_processor import EntityProcessor
# Export managers
from .memory_manager import MemoryManager
from .model_manager import ModelManager
# Export models
from .models import (
  PIIType,
  PIIEntity,
  DetectionConfig,
  PIIDetectionError,
  ModelNotLoadedError,
  ModelLoadError,
  APIKeyError,
)
# Export multi-detector (already exists)
from .multi_detector import MultiModelPIIDetector
# Export main detector
from .pii_detector import PIIDetector
# Export protocol
from .pii_detector_protocol import PIIDetectorProtocol

__all__ = [
    # Models
    "PIIType",
    "PIIEntity",
    "DetectionConfig",
    # Exceptions
    "PIIDetectionError",
    "ModelNotLoadedError",
    "ModelLoadError",
    "APIKeyError",
    # Managers
    "MemoryManager",
    "ModelManager",
    "EntityProcessor",
    # Detectors
    "PIIDetector",
    "MultiModelPIIDetector",
    # Protocol
    "PIIDetectorProtocol",
    # Merger
    "DetectionMerger",
    # Factory
    "DetectorFactory",
    "create_default_factory",
]
