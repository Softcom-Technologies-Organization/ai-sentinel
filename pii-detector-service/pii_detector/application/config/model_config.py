"""
Configuration for machine learning model settings.

This module centralizes model-related configuration.
Models used (nvidia/gliner-PII, etc.) are public and do not require authentication.
"""

from dataclasses import dataclass


@dataclass
class ModelConfig:
    """Configuration for ML models. No authentication required for public models."""

    @classmethod
    def from_env(cls) -> "ModelConfig":
        """Load model configuration from environment variables."""
        return cls()

    def validate(self) -> None:
        """Validate model configuration values."""
        pass
