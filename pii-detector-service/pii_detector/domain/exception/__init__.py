"""Domain exceptions."""

from .exceptions import (
    PIIDetectionError,
    ModelNotLoadedError,
    ModelLoadError,
    APIKeyError,
)

__all__ = [
    "PIIDetectionError",
    "ModelNotLoadedError",
    "ModelLoadError",
    "APIKeyError",
]
