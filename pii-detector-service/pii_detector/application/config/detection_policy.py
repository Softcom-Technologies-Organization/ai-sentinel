"""
Configuration for PII detection behavior.

This module defines the DetectionConfig dataclass that controls
various aspects of PII detection, such as model selection, device
allocation, thresholds, and text processing parameters.

Configuration values are loaded from config/detection-settings.toml 
and config/models/*.toml by default, but can be overridden via 
constructor parameters.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Optional, List, Dict, Any

try:
    import tomllib  # Python 3.11+
except ImportError:
    import tomli as tomllib  # Fallback for Python 3.9-3.10


def _load_llm_config() -> dict:
    """Load LLM configuration from TOML files.
    
    Loads configuration from:
    - config/detection-settings.toml: Global detection settings
    - config/models/*.toml: Individual model configurations
    
    Returns:
        Dictionary with merged configuration values
        
    Raises:
        FileNotFoundError: If config files are not found
        ValueError: If TOML file is malformed
    """
    # Locate config directory relative to pii-detector-service root
    # From pii-detector-service/pii_detector/service/detector/models/detection_config.py
    # Go up 5 levels to reach pii-detector-service/
    config_dir = Path(__file__).parent.parent.parent.parent / "config"
    
    # Load global detection settings
    detection_settings_path = config_dir / "detection-settings.toml"
    if not detection_settings_path.exists():
        raise FileNotFoundError(
            f"Configuration file not found: {detection_settings_path}. "
            "Please ensure config/detection-settings.toml exists in the project root."
        )
    
    with open(detection_settings_path, "rb") as f:
        config = tomllib.load(f)
    
    # Load model configurations from config/models/ directory
    models_dir = config_dir / "models"
    if not models_dir.exists():
        raise FileNotFoundError(
            f"Models directory not found: {models_dir}. "
            "Please ensure config/models/ directory exists with model configuration files."
        )
    
    # Merge all model configurations
    config["models"] = {}
    for model_file in models_dir.glob("*.toml"):
        model_name = model_file.stem  # filename without .toml extension
        with open(model_file, "rb") as f:
            model_config = tomllib.load(f)
            config["models"][model_name] = model_config
    
    if not config["models"]:
        raise ValueError(
            f"No model configuration files found in {models_dir}. "
            "Please add at least one model configuration file (e.g., gliner-pii.toml)."
        )
    
    return config


def get_enabled_models(config: dict) -> List[Dict[str, Any]]:
    """Get list of enabled LLM models sorted by priority.
    
    Args:
        config: Configuration dictionary from TOML files
        
    Returns:
        List of enabled model configurations, sorted by priority (lowest number = highest priority)
        Empty list if LLM detection is disabled or no models are enabled
    """
    if "models" not in config:
        raise ValueError(
            "No [models] section found in configuration. "
            "Please add model configurations in config/models/ directory."
        )
    
    # Check if LLM detection is globally disabled
    detection_config = config.get("detection", {})
    llm_detection_enabled = detection_config.get("llm_detection_enabled", True)
    
    if not llm_detection_enabled:
        # LLM detection disabled globally - return empty list
        return []
    
    # Filter non-LLM models (regex-detector, presidio-detector, etc.)
    non_llm_models = ["regex-detector", "presidio-detector"]
    
    enabled_models = []
    for model_name, model_config in config["models"].items():
        # Skip non-LLM detectors
        if model_name in non_llm_models:
            continue
            
        if model_config.get("enabled", False):
            model_info = {
                "name": model_name,
                "model_id": model_config["model_id"],
                "priority": model_config.get("priority", 999),
                "device": model_config.get("device"),
                "max_length": model_config.get("max_length", 256),
                "threshold": model_config.get("threshold"),
                "description": model_config.get("description", ""),
                "custom_filenames": model_config.get("download", {}).get("custom_filenames"),
            }
            enabled_models.append(model_info)
    
    # Check if at least one detection method is active
    if not enabled_models:
        regex_enabled = detection_config.get("regex_detection_enabled", False)
        presidio_enabled = detection_config.get("presidio_detection_enabled", False)
        
        if not regex_enabled and not presidio_enabled:
            raise ValueError(
                "No enabled models found in configuration. "
                "Please set enabled = true for at least one model in config/models/. "
                "Alternatively, enable regex_detection_enabled or presidio_detection_enabled "
                "in config/detection_config.toml."
            )
    
    # Sort by priority (lowest number = highest priority)
    enabled_models.sort(key=lambda m: m["priority"])
    
    return enabled_models


@dataclass
class DetectionConfig:
    """Configuration for PII detection.
    
    Loads default values from config/detection_config.toml and
    config/models/*.toml. All parameters can be overridden via 
    constructor to support runtime customization.
    
    Attributes:
        model_id: Hugging Face model identifier
        device: Device allocation (None for auto-detect, "cpu", or "cuda")
        max_length: Maximum token length for model context window
        threshold: Confidence threshold for entity detection (0.0 to 1.0)
        batch_size: Batch size for processing multiple texts
        stride_tokens: Token overlap for chunk splitting
        long_text_threshold: Character threshold to trigger chunked processing
    """

    model_id: Optional[str] = None
    device: Optional[str] = None
    max_length: Optional[int] = None
    threshold: Optional[float] = None
    batch_size: Optional[int] = None
    stride_tokens: Optional[int] = None
    long_text_threshold: Optional[int] = None
    custom_filenames: Optional[Dict[str, str]] = None
    
    def __post_init__(self):
        """Load defaults from TOML if values not provided.
        
        For single-model configurations, uses the highest priority enabled model.
        For multi-model setups, the multi_detector.py will handle aggregation.
        
        Raises:
            FileNotFoundError: If config files are not found
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
            if self.custom_filenames is None:
                self.custom_filenames = primary_model.get("custom_filenames")
                
        except FileNotFoundError as e:
            raise FileNotFoundError(
                "Configuration files not found. "
                "Please create the following structure:\n\n"
                "config/\n"
                "├── detection-settings.toml  # Global settings\n"
                "└── models/\n"
                "    ├── gliner-pii.toml      # Model configurations\n"
                "    └── ...\n\n"
                "See the old config/llm.toml for reference."
            ) from e
            
        except KeyError as e:
            missing_key = str(e).strip("'")
            raise ValueError(
                f"Missing required configuration key: {missing_key} in configuration files. "
                f"Please check config/detection_config.toml and config/models/*.toml structure."
            ) from e
            
        except Exception as e:
            raise ValueError(
                f"Failed to load configuration: {e}. "
                f"Please verify the TOML files are valid."
            ) from e
