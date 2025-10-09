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
from typing import Optional, List, Dict, Any

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


def get_enabled_models(config: dict) -> List[Dict[str, Any]]:
    """Get list of enabled models sorted by priority.
    
    Args:
        config: Configuration dictionary from llm.toml
        
    Returns:
        List of enabled model configurations, sorted by priority (lowest number = highest priority)
    """
    if "models" not in config:
        raise ValueError(
            "No [models] section found in config/llm.toml. "
            "Please add model configurations. See config/README.md for details."
        )
    
    enabled_models = []
    for model_name, model_config in config["models"].items():
        if model_config.get("enabled", False):
            model_info = {
                "name": model_name,
                "model_id": model_config["model_id"],
                "priority": model_config.get("priority", 999),
                "device": model_config.get("device"),
                "max_length": model_config.get("max_length", 256),
                "threshold": model_config.get("threshold"),
                "description": model_config.get("description", ""),
            }
            enabled_models.append(model_info)
    
    if not enabled_models:
        raise ValueError(
            "No enabled models found in config/llm.toml. "
            "Please set enabled = true for at least one model."
        )
    
    # Sort by priority (lowest number = highest priority)
    enabled_models.sort(key=lambda m: m["priority"])
    
    return enabled_models


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
        
        For single-model configurations, uses the highest priority enabled model.
        For multi-model setups, the multi_detector.py will handle aggregation.
        
        Raises:
            FileNotFoundError: If config/llm.toml is not found
            KeyError: If required configuration keys are missing in TOML
            ValueError: If TOML file is malformed or contains invalid values
        """
        try:
            config = _load_llm_config()
            
            # Get enabled models
            enabled_models = get_enabled_models(config)
            
            # Use the highest priority model (first in sorted list) as default
            primary_model = enabled_models[0]
            
            # Apply defaults only for None values
            if self.model_id is None:
                self.model_id = primary_model["model_id"]
            if self.device is None:
                self.device = primary_model["device"]
            if self.max_length is None:
                self.max_length = primary_model["max_length"]
            if self.threshold is None:
                # Use model-specific threshold or fall back to default
                model_threshold = primary_model.get("threshold")
                if model_threshold is not None:
                    self.threshold = model_threshold
                else:
                    self.threshold = config["detection"].get("default_threshold", 0.5)
            if self.batch_size is None:
                self.batch_size = config["detection"].get("batch_size", 4)
            if self.stride_tokens is None:
                self.stride_tokens = config["detection"].get("stride_tokens", 64)
            if self.long_text_threshold is None:
                self.long_text_threshold = config["detection"].get("long_text_threshold", 10000)
                
        except FileNotFoundError as e:
            raise FileNotFoundError(
                f"Configuration file 'config/llm.toml' not found. "
                f"Please create the file with the following structure:\n\n"
                f"[detection]\n"
                f"default_threshold = 0.5\n"
                f"batch_size = 4\n"
                f"stride_tokens = 64\n"
                f"long_text_threshold = 10000\n\n"
                f"[models.piiranha-v1]\n"
                f"enabled = true\n"
                f'model_id = "iiiorg/piiranha-v1-detect-personal-information"\n'
                f"priority = 1\n"
                f'device = "cpu"\n'
                f"max_length = 256\n\n"
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
