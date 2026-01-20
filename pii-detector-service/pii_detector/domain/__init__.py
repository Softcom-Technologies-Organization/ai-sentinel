"""
Domain layer for PII detection.
Exports core business entities, ports (interfaces), domain services, and exceptions.

This layer contains pure business logic with NO external dependencies.
"""

# Import and re-export domain entities
from .entity.pii_entity import PIIEntity
from .entity.pii_type import PIIType
# Import and re-export domain exceptions
from .exception.exceptions import (
    PIIDetectionError,
    ModelNotLoadedError,
    ModelLoadError,
    APIKeyError,
)
# Import and re-export domain ports (interfaces - Dependency Inversion Principle)
from .port.pii_detector_protocol import PIIDetectorProtocol
# Import and re-export domain services (pure business logic)
from .service.detection_merger import DetectionMerger
from .service.entity_processor import EntityProcessor

__all__ = [
    # ===== ENTITIES =====
    "PIIEntity",
    "PIIType",
    
    # ===== PORTS (INTERFACES) =====
    "PIIDetectorProtocol",
    
    # ===== DOMAIN SERVICES =====
    "DetectionMerger",
    "EntityProcessor",
    
    # ===== EXCEPTIONS =====
    "PIIDetectionError",
    "ModelNotLoadedError",
    "ModelLoadError",
    "APIKeyError",
]
