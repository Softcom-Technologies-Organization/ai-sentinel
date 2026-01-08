"""Unit tests for model cache functionality."""
from unittest.mock import Mock, patch, MagicMock

from pii_detector.infrastructure.model_management.model_cache import (
    get_env_extra_models,
    ensure_models_cached,
    DEFAULT_EXTRA_MODELS,
)


class TestGetEnvExtraModels:
    """Tests for get_env_extra_models function."""

    def test_Should_ReturnEmptyList_When_ConfigHasEmptyList(self):
        """Empty list in config should return empty list, not fall back to defaults."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = []
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()
        
        assert result == []
        assert result != DEFAULT_EXTRA_MODELS

    def test_Should_ReturnConfiguredModels_When_ConfigHasModels(self):
        """Non-empty list in config should return the configured models."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = ["custom/model-1", "custom/model-2"]
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()
        
        assert result == ["custom/model-1", "custom/model-2"]

    def test_Should_ReturnDefaults_When_ConfigIsNone(self):
        """None in config should fall back to default models."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = None
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS

    def test_Should_ReturnDefaults_When_ConfigNotAvailable(self):
        """ValueError when getting config should fall back to defaults."""
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', side_effect=ValueError("Config error")):
            result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS

    def test_Should_ReturnDefaults_When_AttributeError(self):
        """AttributeError should fall back to defaults."""
        mock_config = Mock()
        del mock_config.detection
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()
        
        assert result == DEFAULT_EXTRA_MODELS


class TestEnsureModelsCached:
    """Tests for ensure_models_cached function."""

    def test_Should_SkipDownload_When_NoHuggingFaceToken(self, caplog):
        """No token should skip download and log info message."""
        import logging
        caplog.set_level(logging.INFO)
        
        mock_config = Mock()
        mock_config.model.huggingface_api_key = None
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            ensure_models_cached(["test/model"])
        
        assert "HUGGING_FACE_API_KEY not set" in caplog.text

    def test_Should_DownloadModels_When_TokenAvailable(self):
        """With token, should download each model."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_snapshot_download = MagicMock()
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config), \
             patch('huggingface_hub.snapshot_download', mock_snapshot_download):
            ensure_models_cached(["model1/test", "model2/test"])
        
        assert mock_snapshot_download.call_count == 2
        mock_snapshot_download.assert_any_call(repo_id="model1/test", token="test_token")
        mock_snapshot_download.assert_any_call(repo_id="model2/test", token="test_token")

    def test_Should_NotDownloadAnything_When_EmptyModelList(self):
        """Empty model list should not attempt any downloads."""
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        mock_snapshot_download = MagicMock()
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config), \
             patch('huggingface_hub.snapshot_download', mock_snapshot_download):
            ensure_models_cached([])
        
        mock_snapshot_download.assert_not_called()

    def test_Should_ContinueOnError_When_DownloadFails(self, caplog):
        """Download errors should be logged but not raise exceptions."""
        import logging
        caplog.set_level(logging.WARNING)
        
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        
        def mock_download(repo_id, token):
            if repo_id == "failing/model":
                raise Exception("Download failed")
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config), \
             patch('huggingface_hub.snapshot_download', side_effect=mock_download):
            ensure_models_cached(["failing/model", "working/model"])
        
        assert "Failed to cache model failing/model" in caplog.text

    def test_Should_HandleGracefully_When_HuggingfaceHubNotInstalled(self, caplog):
        """Missing huggingface_hub package should log warning and skip download."""
        import logging
        caplog.set_level(logging.WARNING)
        
        mock_config = Mock()
        mock_config.model.huggingface_api_key = "test_token"
        
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config), \
             patch('builtins.__import__', side_effect=ImportError("No module named 'huggingface_hub'")):
            ensure_models_cached(["test/model"])
        
        assert "huggingface_hub not available" in caplog.text
