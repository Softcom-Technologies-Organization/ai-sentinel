"""Domain exceptions."""

from .exceptions import (
    PIIDetectionError,
    ModelNotLoadedError,
    ModelLoadError,
)

__all__ = [
    "PIIDetectionError",
    "ModelNotLoadedError",
    "ModelLoadError",
]
