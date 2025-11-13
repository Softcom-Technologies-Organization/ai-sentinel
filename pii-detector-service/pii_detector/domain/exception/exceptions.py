"""
Custom exceptions for PII detection errors.

This module defines all exceptions specific to PII detection operations,
providing clear error types for different failure scenarios.
"""


class PIIDetectionError(Exception):
    """Base exception for PII detection errors."""
    pass


class ModelNotLoadedError(ValueError):
    """Raised when attempting to use an unloaded model."""
    pass


class ModelLoadError(PIIDetectionError):
    """Raised when model loading fails."""
    pass


class APIKeyError(ValueError):
    """Raised when Hugging Face API key is missing or invalid."""
    pass
