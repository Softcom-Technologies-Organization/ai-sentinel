"""
Configuration for PII detection behavior.

This module defines the DetectionConfig dataclass that controls
various aspects of PII detection, such as model selection, device
allocation, thresholds, and text processing parameters.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class DetectionConfig:
    """Configuration for PII detection."""

    model_id: str = "iiiorg/piiranha-v1-detect-personal-information"
    device: Optional[str] = None
    max_length: int = 256
    threshold: float = 0.5
    batch_size: int = 4
    # Token-based splitting settings: overlap avoids boundary truncation
    stride_tokens: int = 64
    # Note: Character-based chunking options removed in favor of token-based splitting per model context
    long_text_threshold: int = 10000
