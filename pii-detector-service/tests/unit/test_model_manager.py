"""
Test suite for model_manager module.

This module contains comprehensive tests for the ModelManager class,
covering model downloading, loading, and error handling scenarios.
"""

from unittest.mock import Mock, patch

import pytest

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.exception.exceptions import ModelLoadError
from pii_detector.infrastructure.model_management.model_manager import ModelManager


class TestModelManagerInit:
    """Test cases for ModelManager initialization."""
    
    def test_should_initialize_with_config(self):
        """Test successful initialization with DetectionConfig."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        
        manager = ModelManager(config)
        
        assert manager.config == config
        assert manager.logger is not None
        assert manager.logger.name == "pii_detector.infrastructure.model_management.model_manager.ModelManager"
    
    def test_should_store_config_reference(self):
        """Test that config is stored as instance variable."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test/model"
        
        manager = ModelManager(config)
        
        assert manager.config.model_id == "test/model"


class TestDownloadModel:
    """Test cases for download_model method."""
    
    @patch('pii_detector.infrastructure.model_management.model_manager.hf_hub_download')
    def test_should_download_default_files(self, mock_hf_download):
        """Test downloading model with default filenames."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.custom_filenames = None
        manager = ModelManager(config)
        
        with patch.object(manager, '_get_api_key', return_value="test-key"):
            with patch.object(manager.logger, 'info'):
                manager.download_model()
        
        expected_files = ["config.json", "model.safetensors", "tokenizer.json", "tokenizer_config.json"]
        assert mock_hf_download.call_count == 4
        
        for i, filename in enumerate(expected_files):
            call_kwargs = mock_hf_download.call_args_list[i][1]
            assert call_kwargs["repo_id"] == "test-model"
            assert call_kwargs["filename"] == filename
            assert call_kwargs["token"] == "test-key"
    
    @patch('pii_detector.infrastructure.model_management.model_manager.hf_hub_download')
    def test_should_use_custom_filenames_when_provided(self, mock_hf_download):
        """Test downloading with custom filenames."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.custom_filenames = {
            "config.json": "custom_config.json",
            "model.safetensors": "custom_model.bin"
        }
        manager = ModelManager(config)
        
        with patch.object(manager, '_get_api_key', return_value="test-key"):
            with patch.object(manager.logger, 'info') as mock_info:
                manager.download_model()
        
        # Verify custom filenames were used
        call_filenames = [call[1]["filename"] for call in mock_hf_download.call_args_list]
        assert "custom_config.json" in call_filenames
        assert "custom_model.bin" in call_filenames
        
        # Verify logging
        assert any("Using custom filenames" in str(call) for call in mock_info.call_args_list)
    
    @patch('pii_detector.infrastructure.model_management.model_manager.hf_hub_download')
    def test_should_raise_error_on_download_failure(self, mock_hf_download):
        """Test that exceptions are re-raised on download failure."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.custom_filenames = None
        manager = ModelManager(config)
        
        error = Exception("Network error")
        mock_hf_download.side_effect = error
        
        with patch.object(manager, '_get_api_key', return_value="test-key"):
            with patch.object(manager.logger, 'error') as mock_error:
                with pytest.raises(Exception) as exc_info:
                    manager.download_model()
                
                assert exc_info.value == error
                mock_error.assert_called_once()
    
    @patch('pii_detector.infrastructure.model_management.model_manager.hf_hub_download')
    def test_should_log_download_progress(self, mock_hf_download):
        """Test that download progress is logged."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.custom_filenames = None
        manager = ModelManager(config)
        
        with patch.object(manager, '_get_api_key', return_value="test-key"):
            with patch.object(manager.logger, 'info') as mock_info, \
                 patch.object(manager.logger, 'debug') as mock_debug:
                manager.download_model()
        
        # Verify logging
        assert any("Downloading model files" in str(call) for call in mock_info.call_args_list)
        assert any("Model download completed successfully" in str(call) for call in mock_info.call_args_list)
        assert mock_debug.call_count == 4  # One for each file


class TestLoadModelComponents:
    """Test cases for load_model_components method."""
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoTokenizer')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_load_tokenizer_and_model(self, mock_torch, mock_tokenizer_class, mock_model_class):
        """Test successful loading of model components."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.max_length = 256
        config.device = 'cpu'
        manager = ModelManager(config)
        
        mock_tokenizer = Mock()
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_tokenizer_class.from_pretrained.return_value = mock_tokenizer
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        with patch.object(manager.logger, 'info'):
            tokenizer, model = manager.load_model_components()
        
        assert tokenizer == mock_tokenizer
        assert model == mock_model
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoTokenizer')
    def test_should_raise_model_load_error_on_failure(self, mock_tokenizer_class, mock_model_class):
        """Test ModelLoadError when loading fails."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = ModelManager(config)
        
        mock_tokenizer_class.from_pretrained.side_effect = Exception("Load error")
        
        with patch.object(manager.logger, 'error') as mock_error:
            with pytest.raises(ModelLoadError) as exc_info:
                manager.load_model_components()
            
            assert "Failed to load model components" in str(exc_info.value)
            mock_error.assert_called_once()


class TestLoadTokenizer:
    """Test cases for _load_tokenizer method."""
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoTokenizer')
    def test_should_load_tokenizer_with_correct_parameters(self, mock_tokenizer_class):
        """Test tokenizer is loaded with correct configuration."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.max_length = 512
        manager = ModelManager(config)
        
        mock_tokenizer = Mock()
        mock_tokenizer_class.from_pretrained.return_value = mock_tokenizer
        
        result = manager._load_tokenizer()
        
        mock_tokenizer_class.from_pretrained.assert_called_once_with(
            "test-model",
            legacy=False,
            model_max_length=512,
            padding=True,
            truncation=True
        )
        assert result == mock_tokenizer


class TestLoadModel:
    """Test cases for _load_model method."""
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_load_model_for_cpu(self, mock_torch, mock_model_class):
        """Test model loading for CPU device."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = 'cpu'
        manager = ModelManager(config)
        
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        result = manager._load_model()
        
        mock_model_class.from_pretrained.assert_called_once()
        call_kwargs = mock_model_class.from_pretrained.call_args[1]
        assert call_kwargs["torch_dtype"] == 'float32'
        assert call_kwargs["low_cpu_mem_usage"] is True
        
        mock_model.to.assert_called_once_with('cpu')
        mock_model.eval.assert_called_once()
        assert result == mock_model
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_load_model_for_cuda(self, mock_torch, mock_model_class):
        """Test model loading for CUDA device."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = 'cuda'
        manager = ModelManager(config)
        
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = True
        mock_torch.float16 = 'float16'
        
        result = manager._load_model()
        
        call_kwargs = mock_model_class.from_pretrained.call_args[1]
        assert call_kwargs["torch_dtype"] == 'float16'
        
        mock_model.to.assert_called_once_with('cuda')
        mock_model.eval.assert_called_once()
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_auto_detect_device_when_none(self, mock_torch, mock_model_class):
        """Test device auto-detection when config.device is None."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = None
        manager = ModelManager(config)
        
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        result = manager._load_model()
        
        mock_model.to.assert_called_once_with('cpu')
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_fallback_without_low_cpu_mem_usage(self, mock_torch, mock_model_class):
        """Test fallback when accelerate is not available."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = 'cpu'
        manager = ModelManager(config)
        
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        # First call raises ImportError, second call succeeds
        mock_model_class.from_pretrained.side_effect = [
            ImportError("accelerate not installed"),
            mock_model
        ]
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        with patch.object(manager.logger, 'warning') as mock_warning:
            result = manager._load_model()
        
        # Should have tried twice
        assert mock_model_class.from_pretrained.call_count == 2
        
        # Second call should not have low_cpu_mem_usage
        second_call_kwargs = mock_model_class.from_pretrained.call_args_list[1][1]
        assert "low_cpu_mem_usage" not in second_call_kwargs
        
        mock_warning.assert_called_once()
        assert result == mock_model
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_disable_gradients(self, mock_torch, mock_model_class):
        """Test that gradients are disabled for inference."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = 'cpu'
        manager = ModelManager(config)
        
        mock_model = Mock()
        mock_param1 = Mock()
        mock_param2 = Mock()
        mock_model.parameters.return_value = [mock_param1, mock_param2]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        manager._load_model()
        
        assert mock_param1.requires_grad is False
        assert mock_param2.requires_grad is False


class TestEdgeCases:
    """Test cases for edge cases and integration scenarios."""
    
    @patch('pii_detector.infrastructure.model_management.model_manager.hf_hub_download')
    def test_should_handle_custom_filenames_partially_specified(self, mock_hf_download):
        """Test custom filenames with only some files specified."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.custom_filenames = {"config.json": "my_config.json"}
        manager = ModelManager(config)
        
        with patch.object(manager, '_get_api_key', return_value="test-key"):
            with patch.object(manager.logger, 'info'):
                manager.download_model()
        
        # Check that custom name was used for config but defaults for others
        filenames = [call[1]["filename"] for call in mock_hf_download.call_args_list]
        assert "my_config.json" in filenames
        assert "model.safetensors" in filenames  # default
        assert "tokenizer.json" in filenames  # default
    
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoModelForTokenClassification')
    @patch('pii_detector.infrastructure.model_management.model_manager.AutoTokenizer')
    @patch('pii_detector.infrastructure.model_management.model_manager.torch')
    def test_should_handle_complete_workflow(self, mock_torch, mock_tokenizer_class, mock_model_class):
        """Test complete workflow from initialization to loading."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test/model"
        config.max_length = 256
        config.device = 'cpu'
        
        manager = ModelManager(config)
        
        mock_tokenizer = Mock()
        mock_model = Mock()
        mock_param = Mock()
        mock_model.parameters.return_value = [mock_param]
        mock_model.to.return_value = mock_model
        mock_model.eval.return_value = mock_model
        mock_tokenizer_class.from_pretrained.return_value = mock_tokenizer
        mock_model_class.from_pretrained.return_value = mock_model
        mock_torch.cuda.is_available.return_value = False
        mock_torch.float32 = 'float32'
        
        with patch.object(manager.logger, 'info'):
            tokenizer, model = manager.load_model_components()
        
        assert tokenizer == mock_tokenizer
        assert model == mock_model
        assert manager.config.model_id == "test/model"
