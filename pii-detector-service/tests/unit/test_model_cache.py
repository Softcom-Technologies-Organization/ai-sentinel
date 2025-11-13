"""
Test suite for model_cache module.

This module contains comprehensive tests for the model caching functionality,
covering configuration loading, model pre-downloading, and error handling.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
from typing import List

from pii_detector.infrastructure.model_management.model_cache import (
    get_env_extra_models,
    ensure_models_cached,
    DEFAULT_EXTRA_MODELS
)


class TestConstants:
    """Test cases for module constants."""
    
    def test_should_have_default_extra_models(self):
        """Test that DEFAULT_EXTRA_MODELS contains expected models."""
        assert isinstance(DEFAULT_EXTRA_MODELS, list)
        assert len(DEFAULT_EXTRA_MODELS) > 0
        assert "Ar86Bat/multilang-pii-ner" in DEFAULT_EXTRA_MODELS


class TestGetEnvExtraModels:
    """Test cases for get_env_extra_models function."""
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_return_models_from_config_when_available(self, mock_get_config):
        """Test reading models from configuration."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = ["model1", "model2"]
        mock_get_config.return_value = mock_config
        
        result = get_env_extra_models()
        
        assert result == ["model1", "model2"]
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_return_defaults_when_config_raises_value_error(self, mock_get_config):
        """Test fallback to defaults when config raises ValueError."""
        mock_get_config.side_effect = ValueError("Config error")
        
        result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS
        # Verify it's a copy, not the original
        assert result is not DEFAULT_EXTRA_MODELS
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_return_defaults_when_config_raises_attribute_error(self, mock_get_config):
        """Test fallback to defaults when config raises AttributeError."""
        mock_get_config.side_effect = AttributeError("Missing attribute")
        
        result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_return_defaults_when_models_is_none(self, mock_get_config):
        """Test fallback to defaults when pii_extra_models is None."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = None
        mock_get_config.return_value = mock_config
        
        result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_return_defaults_when_models_is_empty(self, mock_get_config):
        """Test fallback to defaults when pii_extra_models is empty list."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = []
        mock_get_config.return_value = mock_config
        
        result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS


class TestEnsureModelsCached:
    """Test cases for ensure_models_cached function."""
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_skip_when_no_token(self, mock_get_config):
        """Test that function returns early when API key is not set."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = None
        mock_get_config.return_value = mock_config
        
        # Should not raise and should log info
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(["model1"])
            
            mock_logger.info.assert_called_once()
            assert "HUGGING_FACE_API_KEY not set" in str(mock_logger.info.call_args)
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_skip_when_config_raises_value_error(self, mock_get_config):
        """Test that function returns early when config raises ValueError."""
        mock_get_config.side_effect = ValueError("Config error")
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(["model1"])
            
            mock_logger.info.assert_called_once()
            assert "HUGGING_FACE_API_KEY not set" in str(mock_logger.info.call_args)
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_skip_when_config_raises_attribute_error(self, mock_get_config):
        """Test that function returns early when config raises AttributeError."""
        mock_get_config.side_effect = AttributeError("Missing attribute")
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(["model1"])
            
            mock_logger.info.assert_called_once()
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_download_single_model_when_token_available(self, mock_get_config, mock_snapshot):
        """Test successful download of a single model."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(["model1"])
            
            mock_snapshot.assert_called_once_with(repo_id="model1", token="test_token")
            # Should log preloading and success
            assert mock_logger.info.call_count >= 2
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_download_multiple_models(self, mock_get_config, mock_snapshot):
        """Test successful download of multiple models."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        models = ["model1", "model2", "model3"]
        ensure_models_cached(models)
        
        assert mock_snapshot.call_count == 3
        for i, model in enumerate(models):
            assert mock_snapshot.call_args_list[i][1]["repo_id"] == model
            assert mock_snapshot.call_args_list[i][1]["token"] == "test_token"
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_continue_on_download_failure(self, mock_get_config, mock_snapshot):
        """Test that function continues when one model download fails."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        # First download fails, second succeeds
        mock_snapshot.side_effect = [Exception("Download error"), None]
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(["model1", "model2"])
            
            # Should attempt both downloads
            assert mock_snapshot.call_count == 2
            # Should log warning for the failure
            mock_logger.warning.assert_called_once()
            assert "Failed to cache model model1" in str(mock_logger.warning.call_args)
    
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_handle_huggingface_hub_import_error(self, mock_get_config):
        """Test handling when huggingface_hub is not available."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        # Mock the import to fail by making the module raise ImportError
        import sys
        with patch.dict(sys.modules, {'huggingface_hub': None}):
            with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
                # Should not raise
                ensure_models_cached(["model1"])
                
                # Should log warning about missing module
                mock_logger.warning.assert_called_once()
                assert "huggingface_hub not available" in str(mock_logger.warning.call_args)
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_handle_empty_model_list(self, mock_get_config, mock_snapshot):
        """Test handling of empty model list."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        ensure_models_cached([])
        
        # Should not call snapshot_download for empty list
        mock_snapshot.assert_not_called()
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_pass_token_to_snapshot_download(self, mock_get_config, mock_snapshot):
        """Test that API token is correctly passed to snapshot_download."""
        expected_token = "my_secret_token_123"
        mock_config = Mock()
        mock_config.model.huggingface_api_key = expected_token
        mock_get_config.return_value = mock_config
        
        ensure_models_cached(["test-model"])
        
        mock_snapshot.assert_called_once()
        call_kwargs = mock_snapshot.call_args[1]
        assert call_kwargs["token"] == expected_token
        assert call_kwargs["repo_id"] == "test-model"
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_log_each_model_preload_attempt(self, mock_get_config, mock_snapshot):
        """Test that each model preload is logged."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        models = ["model1", "model2"]
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            ensure_models_cached(models)
            
            # Should log preloading for each model
            info_calls = [str(call) for call in mock_logger.info.call_args_list]
            for model in models:
                assert any(f"Preloading Hugging Face model into cache: {model}" in call for call in info_calls)
                assert any(f"Model cached: {model}" in call for call in info_calls)


class TestEdgeCases:
    """Test cases for edge cases and integration scenarios."""
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_handle_all_downloads_failing(self, mock_get_config, mock_snapshot):
        """Test when all model downloads fail."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        mock_snapshot.side_effect = Exception("Network error")
        
        with patch('pii_detector.infrastructure.model_management.model_cache.logger') as mock_logger:
            # Should not raise, just log warnings
            ensure_models_cached(["model1", "model2"])
            
            # Should warn for each failed download
            assert mock_logger.warning.call_count == 2
    
    @patch('huggingface_hub.snapshot_download')
    @patch('pii_detector.infrastructure.model_management.model_cache.get_config')
    def test_should_handle_special_characters_in_model_id(self, mock_get_config, mock_snapshot):
        """Test handling of model IDs with special characters."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_get_config.return_value = mock_config
        
        special_model = "org/model-name_v2.0"
        ensure_models_cached([special_model])
        
        mock_snapshot.assert_called_once_with(repo_id=special_model, token="test_token")
