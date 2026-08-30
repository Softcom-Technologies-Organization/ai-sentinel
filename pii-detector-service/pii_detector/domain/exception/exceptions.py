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


class DetectorUnavailableError(PIIDetectionError):
    """Raised when a detector cannot run at all, e.g. its remote inference
    endpoint is unreachable.

    Distinct from a detector that ran and found nothing: callers surface this so
    an operator sees the detector they enabled did not contribute, instead of
    reading an incomplete report as a clean one.
    """
    pass
