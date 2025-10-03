"""
Tests for the centralized configuration module.

This test suite validates that all configuration domains can be loaded,
validated, and accessed correctly.
"""

import os
import pytest
from pii_detector.config import (
    AppConfig,
    ServerConfig,
    ModelConfig,
    DetectionConfig,
    get_config,
    reload_config,
)


class TestServerConfig:
    """Test ServerConfig domain."""
    
    def test_from_env_with_defaults(self):
        """Test ServerConfig loads with default values."""
        config = ServerConfig.from_env()
        
        assert config.enable_reflection is True  # Default is enabled
    
    def test_validation_passes(self):
        """Test ServerConfig validation passes."""
        config = ServerConfig.from_env()
        # Should not raise
        config.validate()


class TestModelConfig:
    """Test ModelConfig domain."""
    
    def test_from_env_with_defaults(self):
        """Test ModelConfig loads with default values."""
        # Set required env var
        os.environ["HUGGING_FACE_API_KEY"] = "test_key"
        
        config = ModelConfig.from_env()
        
        assert config.huggingface_api_key == "test_key"
    
    def test_validation_missing_api_key(self):
        """Test ModelConfig validation catches missing API key."""
        config = ModelConfig(huggingface_api_key="")
        
        with pytest.raises(ValueError, match="HUGGING_FACE_API_KEY is required"):
            config.validate()


class TestDetectionConfig:
    """Test DetectionConfig domain."""
    
    def test_from_env_with_defaults(self):
        """Test DetectionConfig loads with default values."""
        config = DetectionConfig.from_env()
        
        assert config.multi_detector_enabled is False
        assert config.multi_detector_models == []
        assert config.multi_detector_log_provenance is False
        assert config.pii_extra_models == []
    
    def test_validation_passes(self):
        """Test DetectionConfig validation passes for valid config."""
        config = DetectionConfig.from_env()
        # Should not raise
        config.validate()


class TestAppConfig:
    """Test unified AppConfig."""
    
    def test_from_env_loads_all_domains(self):
        """Test AppConfig loads all configuration domains."""
        # Set required env vars
        os.environ["HUGGING_FACE_API_KEY"] = "test_key"
        
        config = AppConfig.from_env()
        
        assert isinstance(config.server, ServerConfig)
        assert isinstance(config.model, ModelConfig)
        assert isinstance(config.detection, DetectionConfig)
    
    def test_validate_all_runs_all_validations(self):
        """Test validate_all runs validation on all domains."""
        # Set required env vars
        os.environ["HUGGING_FACE_API_KEY"] = "test_key"
        
        config = AppConfig.from_env()
        
        # Should not raise with valid configuration
        config.validate_all()


class TestConfigSingleton:
    """Test singleton pattern for global config."""
    
    def test_get_config_returns_singleton(self):
        """Test get_config returns the same instance."""
        # Set required env vars
        os.environ["HUGGING_FACE_API_KEY"] = "test_key"
        
        config1 = get_config()
        config2 = get_config()
        
        assert config1 is config2
    
    def test_reload_config_creates_new_instance(self):
        """Test reload_config creates new instance."""
        # Set required env vars
        os.environ["HUGGING_FACE_API_KEY"] = "test_key"
        os.environ["ENABLE_REFLECTION"] = "1"
        
        config1 = get_config()
        
        # Change environment
        os.environ["ENABLE_REFLECTION"] = "0"
        
        config2 = reload_config()
        
        assert config2.server.enable_reflection is False
