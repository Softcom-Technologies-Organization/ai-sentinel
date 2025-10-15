"""
Data models for PII detection.

This package contains all data structures used by the PII detector:
- PIIType: Enumeration of PII types
- PIIEntity: Detected PII entity data structure
- DetectionConfig: Configuration for detection behavior
- Exceptions: Custom exceptions for PII detection errors
"""

from .detection_config import DetectionConfig
from .exceptions import (
  PIIDetectionError,
  ModelNotLoadedError,
  ModelLoadError,
  APIKeyError,
)
from .pii_entity import PIIEntity
from .pii_type import PIIType

__all__ = [
    "PIIType",
    "PIIEntity",
    "DetectionConfig",
    "PIIDetectionError",
    "ModelNotLoadedError",
    "ModelLoadError",
    "APIKeyError",
]
