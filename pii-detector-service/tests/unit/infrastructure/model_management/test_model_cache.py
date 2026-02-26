"""Unit tests for model cache functionality."""
from unittest.mock import Mock, patch, MagicMock

from pii_detector.infrastructure.model_management.model_cache import (
    get_env_extra_models,
    ensure_models_cached,
    DEFAULT_EXTRA_MODELS,
)


class TestGetEnvExtraModels:
    """Tests for get_env_extra_models function."""

    def test_should_return_empty_list_when_config_has_empty_list(self):
        """Empty list in config should return empty list, not fall back to defaults."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = []

        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()

        assert result == []
        assert result != DEFAULT_EXTRA_MODELS

    def test_should_return_configured_models_when_config_has_models(self):
        """Non-empty list in config should return the configured models."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = ["custom/model-1", "custom/model-2"]

        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()

        assert result == ["custom/model-1", "custom/model-2"]

    def test_should_return_defaults_when_config_is_none(self):
        """None in config should fall back to default models."""
        mock_config = Mock()
        mock_config.detection.pii_extra_models = None

        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()

        assert result == DEFAULT_EXTRA_MODELS

    def test_should_return_defaults_when_config_not_available(self):
        """ValueError when getting config should fall back to defaults."""
        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', side_effect=ValueError("Config error")):
            result = get_env_extra_models()

        assert result == DEFAULT_EXTRA_MODELS

    def test_should_return_defaults_when_attribute_error(self):
        """AttributeError should fall back to defaults."""
        mock_config = Mock()
        del mock_config.detection

        with patch('pii_detector.infrastructure.model_management.model_cache.get_config', return_value=mock_config):
            result = get_env_extra_models()

        assert result == DEFAULT_EXTRA_MODELS


class TestEnsureModelsCached:
    """Tests for ensure_models_cached function."""

    def test_should_download_models_when_called(self):
        """Should download each model without authentication."""
        mock_snapshot_download = MagicMock()

        with patch('huggingface_hub.snapshot_download', mock_snapshot_download):
            ensure_models_cached(["model1/test", "model2/test"])

        assert mock_snapshot_download.call_count == 2
        mock_snapshot_download.assert_any_call(repo_id="model1/test")
        mock_snapshot_download.assert_any_call(repo_id="model2/test")

    def test_should_not_download_anything_when_empty_model_list(self):
        """Empty model list should not attempt any downloads."""
        mock_snapshot_download = MagicMock()

        with patch('huggingface_hub.snapshot_download', mock_snapshot_download):
            ensure_models_cached([])

        mock_snapshot_download.assert_not_called()

    def test_should_continue_on_error_when_download_fails(self, caplog):
        """Download errors should be logged but not raise exceptions."""
        import logging
        caplog.set_level(logging.WARNING)

        def mock_download(repo_id):
            if repo_id == "failing/model":
                raise Exception("Download failed")

        mock_snapshot = MagicMock(side_effect=mock_download)
        with patch('huggingface_hub.snapshot_download', mock_snapshot):
            ensure_models_cached(["failing/model", "working/model"])

        assert "Failed to cache model failing/model" in caplog.text
        assert mock_snapshot.call_count == 2

    def test_should_handle_gracefully_when_huggingface_hub_not_installed(self, caplog):
        """Missing huggingface_hub package should log warning and skip download."""
        import logging
        caplog.set_level(logging.WARNING)

        with patch('builtins.__import__', side_effect=ImportError("No module named 'huggingface_hub'")):
            ensure_models_cached(["test/model"])

        assert "huggingface_hub not available" in caplog.text
