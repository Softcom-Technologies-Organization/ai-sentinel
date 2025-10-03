"""
Configuration for PII detection logic settings.

This module centralizes detection-related configuration.
Only includes actually used environment variables.
"""

import os
from dataclasses import dataclass
from typing import List


def _parse_bool(value: str) -> bool:
    """Parse boolean from environment variable string."""
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _parse_model_list(raw: str) -> List[str]:
    """Parse comma-separated model list from environment variable."""
    if not raw or not raw.strip():
        return []
    return [m.strip() for m in raw.split(",") if m.strip()]


@dataclass
class DetectionConfig:
    """
    Configuration for PII detection behavior.
    
    Attributes:
        multi_detector_enabled: Enable multi-model ensemble detection
        multi_detector_models: List of model IDs for multi-detector
        multi_detector_log_provenance: Enable detailed provenance logging
        pii_extra_models: Additional models to cache at startup
    """
    
    multi_detector_enabled: bool
    multi_detector_models: List[str]
    multi_detector_log_provenance: bool
    pii_extra_models: List[str]
    
    @classmethod
    def from_env(cls) -> "DetectionConfig":
        """
        Load detection configuration from environment variables.
        
        Returns:
            DetectionConfig instance populated from environment
            
        Environment Variables:
            MULTI_DETECTOR_ENABLED: Enable multi-model detection (default: false)
            MULTI_DETECTOR_MODELS: Comma-separated model IDs (default: empty)
            MULTI_DETECTOR_LOG_PROVENANCE: Log provenance details (default: false)
            PII_EXTRA_MODELS: Extra models to cache (default: empty)
        """
        return cls(
            multi_detector_enabled=_parse_bool(
                os.getenv("MULTI_DETECTOR_ENABLED", "")
            ),
            multi_detector_models=_parse_model_list(
                os.getenv("MULTI_DETECTOR_MODELS", "")
            ),
            multi_detector_log_provenance=_parse_bool(
                os.getenv("MULTI_DETECTOR_LOG_PROVENANCE", "")
            ),
            pii_extra_models=_parse_model_list(
                os.getenv("PII_EXTRA_MODELS", "")
            ),
        )
    
    def validate(self) -> None:
        """
        Validate detection configuration values.
        
        Raises:
            ValueError: If configuration values are invalid
        """
        # No validation needed for these simple settings
        pass
