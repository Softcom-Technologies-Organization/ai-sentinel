"""
Centralized configuration module for PII Detector microservice.

This module provides a unified interface to configuration:
- Server configuration (gRPC settings)
- Model configuration (HuggingFace authentication)
- Detection configuration (Multi-detector settings)

Only includes actually used environment variables.

Usage:
    from config import get_config
    
    config = get_config()
    
    # Access server settings
    print(f"Reflection enabled: {config.server.enable_reflection}")
    
    # Access model settings
    print(f"API key set: {bool(config.model.huggingface_api_key)}")
    
    # Access detection settings
    print(f"Multi-detector enabled: {config.detection.multi_detector_enabled}")
"""

from dataclasses import dataclass
from typing import Optional

from .detection_config import DetectionConfig
from pii_detector.application.config.model_config import ModelConfig
from .server_config import ServerConfig


@dataclass
class AppConfig:
    """
    Unified application configuration.
    
    Attributes:
        server: gRPC server configuration
        model: ML model configuration
        detection: PII detection logic configuration
    """
    
    server: ServerConfig
    model: ModelConfig
    detection: DetectionConfig
    
    @classmethod
    def from_env(cls) -> "AppConfig":
        """
        Load complete application configuration from environment variables.
        
        Returns:
            AppConfig instance with all configuration domains loaded
            
        Raises:
            ValueError: If any configuration domain fails validation
        """
        return cls(
            server=ServerConfig.from_env(),
            model=ModelConfig.from_env(),
            detection=DetectionConfig.from_env(),
        )
    
    def validate_all(self) -> None:
        """
        Validate all configuration domains.
        
        Raises:
            ValueError: If any configuration domain fails validation
        """
        validation_errors = []
        
        try:
            self.server.validate()
        except ValueError as e:
            validation_errors.append(f"Server config error: {e}")
        
        try:
            self.model.validate()
        except ValueError as e:
            validation_errors.append(f"Model config error: {e}")
        
        try:
            self.detection.validate()
        except ValueError as e:
            validation_errors.append(f"Detection config error: {e}")
        
        if validation_errors:
            raise ValueError(
                "Configuration validation failed:\n" + "\n".join(validation_errors)
            )


# Global configuration instance (singleton pattern)
_config_instance: Optional[AppConfig] = None


def get_config() -> AppConfig:
    """
    Get the global configuration instance.
    
    Returns:
        AppConfig instance with all configuration loaded and validated
        
    Raises:
        ValueError: If configuration validation fails
    """
    global _config_instance
    
    if _config_instance is None:
        _config_instance = AppConfig.from_env()
        _config_instance.validate_all()
    
    return _config_instance


def reload_config() -> AppConfig:
    """
    Reload configuration from environment variables.
    
    Returns:
        New AppConfig instance with reloaded configuration
        
    Raises:
        ValueError: If configuration validation fails
    """
    global _config_instance
    
    _config_instance = AppConfig.from_env()
    _config_instance.validate_all()
    
    return _config_instance


# Export all configuration classes and functions
__all__ = [
    "AppConfig",
    "ServerConfig",
    "ModelConfig",
    "DetectionConfig",
    "get_config",
    "reload_config",
]
