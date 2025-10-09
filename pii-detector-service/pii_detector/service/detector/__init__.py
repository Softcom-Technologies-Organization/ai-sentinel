"""
PII Detector Package.

This package contains the implementation of the PII detection functionality.
Exports all public classes for backward compatibility.
"""

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

# Export managers
from .memory_manager import MemoryManager
from .model_manager import ModelManager
from .entity_processor import EntityProcessor

# Export main detector
from .pii_detector import PIIDetector

# Export multi-detector (already exists)
from .multi_detector import MultiModelPIIDetector

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
]
