"""
Configuration for PII detection behavior.

This module defines the DetectionConfig dataclass that controls
various aspects of PII detection, such as model selection, device
allocation, thresholds, and text processing parameters.

Configuration values are loaded from config/llm.toml by default,
but can be overridden via constructor parameters.
"""

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

try:
    import tomllib  # Python 3.11+
except ImportError:
    import tomli as tomllib  # Fallback for Python 3.9-3.10


def _load_llm_config() -> dict:
    """Load LLM configuration from TOML file.
    
    Returns:
        Dictionary with configuration values from llm.toml
        
    Raises:
        FileNotFoundError: If config/llm.toml is not found
        ValueError: If TOML file is malformed
    """
    # Locate config file relative to project root
    config_path = Path(__file__).parent.parent.parent.parent.parent / "config" / "llm.toml"
    
    if not config_path.exists():
        raise FileNotFoundError(
            f"Configuration file not found: {config_path}. "
            "Please ensure config/llm.toml exists in the project root."
        )
    
    with open(config_path, "rb") as f:
        config = tomllib.load(f)
    
    return config


@dataclass
class DetectionConfig:
    """Configuration for PII detection.
    
    Loads default values from config/llm.toml. All parameters can be
    overridden via constructor to support runtime customization.
    
    Attributes:
        model_id: Hugging Face model identifier
        device: Device allocation (None for auto-detect, "cpu", or "cuda")
        max_length: Maximum token length for model context window
        threshold: Confidence threshold for entity detection (0.0 to 1.0)
        batch_size: Batch size for processing multiple texts
        stride_tokens: Token overlap for chunk splitting
        long_text_threshold: Character threshold to trigger chunked processing
    """

    model_id: str = None
    device: Optional[str] = None
    max_length: int = None
    threshold: float = None
    batch_size: int = None
    stride_tokens: int = None
    long_text_threshold: int = None
    
    def __post_init__(self):
        """Load defaults from TOML if values not provided.
        
        Raises:
            FileNotFoundError: If config/llm.toml is not found
            KeyError: If required configuration keys are missing in TOML
            ValueError: If TOML file is malformed or contains invalid values
        """
        try:
            config = _load_llm_config()
            
            # Apply defaults only for None values
            if self.model_id is None:
                self.model_id = config["model"]["model_id"]
            if self.device is None:
                self.device = config["model"].get("device")
            if self.max_length is None:
                self.max_length = config["detection"]["max_length"]
            if self.threshold is None:
                self.threshold = config["detection"]["threshold"]
            if self.batch_size is None:
                self.batch_size = config["detection"]["batch_size"]
            if self.stride_tokens is None:
                self.stride_tokens = config["detection"]["stride_tokens"]
            if self.long_text_threshold is None:
                self.long_text_threshold = config["detection"]["long_text_threshold"]
                
        except FileNotFoundError as e:
            raise FileNotFoundError(
                f"Configuration file 'config/llm.toml' not found. "
                f"Please create the file with the following structure:\n\n"
                f"[model]\n"
                f'model_id = "iiiorg/piiranha-v1-detect-personal-information"\n'
                f"device = null\n\n"
                f"[detection]\n"
                f"max_length = 256\n"
                f"threshold = 0.5\n"
                f"batch_size = 4\n"
                f"stride_tokens = 64\n"
                f"long_text_threshold = 10000\n\n"
                f"See config/README.md for more details."
            ) from e
            
        except KeyError as e:
            missing_key = str(e).strip("'")
            raise ValueError(
                f"Missing required configuration key: {missing_key} in config/llm.toml. "
                f"Please check the file structure. See config/README.md for the expected format."
            ) from e
            
        except Exception as e:
            raise ValueError(
                f"Failed to load configuration from config/llm.toml: {e}. "
                f"Please verify the TOML file is valid. See config/README.md for help."
            ) from e
