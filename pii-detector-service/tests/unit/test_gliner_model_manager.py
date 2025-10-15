"""
Test suite for gliner_model_manager module.

This module contains comprehensive tests for the GLiNERModelManager class,
covering model downloading, loading, and error handling scenarios.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock

from pii_detector.service.detector.gliner_model_manager import GLiNERModelManager
from pii_detector.service.detector.models.detection_config import DetectionConfig
from pii_detector.service.detector.models.exceptions import ModelLoadError


class TestGLiNERModelManagerInit:
    """Test cases for GLiNERModelManager initialization."""
    
    def test_should_initialize_with_config(self):
        """Test successful initialization with DetectionConfig."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        
        manager = GLiNERModelManager(config)
        
        assert manager.config == config
        assert manager.logger is not None
        assert manager.logger.name == "pii_detector.service.detector.gliner_model_manager.GLiNERModelManager"
    
    def test_should_store_config_reference(self):
        """Test that config is stored as instance variable."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "urchade/gliner_multi_pii-v1"
        
        manager = GLiNERModelManager(config)
        
        assert manager.config.model_id == "urchade/gliner_multi_pii-v1"


class TestDownloadModel:
    """Test cases for download_model method."""
    
    def test_should_log_info_message_when_called(self):
        """Test that download_model logs information about deferred download."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model-id"
        manager = GLiNERModelManager(config)
        
        with patch.object(manager.logger, 'info') as mock_info:
            manager.download_model()
            
            mock_info.assert_called_once()
            call_args = str(mock_info.call_args)
            assert "test-model-id" in call_args
            assert "will be downloaded on first load" in call_args
    
    def test_should_not_raise_exception(self):
        """Test that download_model completes without error."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "any-model"
        manager = GLiNERModelManager(config)
        
        # Should not raise any exception
        manager.download_model()
    
    def test_should_handle_different_model_ids(self):
        """Test logging with various model identifiers."""
        test_models = [
            "urchade/gliner_multi_pii-v1",
            "org/model-name",
            "user/special_model_123"
        ]
        
        for model_id in test_models:
            config = Mock(spec=DetectionConfig)
            config.model_id = model_id
            manager = GLiNERModelManager(config)
            
            with patch.object(manager.logger, 'info') as mock_info:
                manager.download_model()
                
                call_args = str(mock_info.call_args)
                assert model_id in call_args


class TestLoadModel:
    """Test cases for load_model method."""
    
    @patch('gliner.GLiNER')
    def test_should_load_model_successfully(self, mock_gliner_class):
        """Test successful model loading with GLiNER."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        mock_model_instance = Mock()
        mock_gliner_class.from_pretrained.return_value = mock_model_instance
        
        with patch.object(manager.logger, 'info') as mock_info:
            result = manager.load_model()
            
            assert result == mock_model_instance
            mock_gliner_class.from_pretrained.assert_called_once_with("test-model")
            assert mock_info.call_count == 2
            assert "Loading GLiNER model: test-model" in str(mock_info.call_args_list[0])
            assert "GLiNER model loaded successfully" in str(mock_info.call_args_list[1])
    
    @patch('gliner.GLiNER')
    def test_should_call_from_pretrained_with_model_id(self, mock_gliner_class):
        """Test that from_pretrained is called with correct model_id."""
        expected_model_id = "urchade/gliner_multi_pii-v1"
        config = Mock(spec=DetectionConfig)
        config.model_id = expected_model_id
        manager = GLiNERModelManager(config)
        
        mock_gliner_class.from_pretrained.return_value = Mock()
        
        manager.load_model()
        
        mock_gliner_class.from_pretrained.assert_called_once_with(expected_model_id)
    
    @patch('builtins.__import__', side_effect=ImportError("No module named 'gliner'"))
    def test_should_raise_model_load_error_when_gliner_not_installed(self, mock_import):
        """Test ModelLoadError is raised when GLiNER import fails."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        with patch.object(manager.logger, 'error') as mock_error:
            with pytest.raises(ModelLoadError) as exc_info:
                manager.load_model()
            
            assert "GLiNER library not installed" in str(exc_info.value)
            assert "pip install gliner" in str(exc_info.value)
            mock_error.assert_called_once()
            assert "GLiNER library not installed" in str(mock_error.call_args)
    
    @patch('gliner.GLiNER')
    def test_should_raise_model_load_error_when_loading_fails(self, mock_gliner_class):
        """Test ModelLoadError is raised when model loading encounters an error."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        error_message = "Connection timeout"
        mock_gliner_class.from_pretrained.side_effect = Exception(error_message)
        
        with patch.object(manager.logger, 'error') as mock_error:
            with pytest.raises(ModelLoadError) as exc_info:
                manager.load_model()
            
            assert "Failed to load GLiNER model" in str(exc_info.value)
            assert error_message in str(exc_info.value)
            mock_error.assert_called_once()
            assert error_message in str(mock_error.call_args)
    
    @patch('builtins.__import__', side_effect=ImportError("Original import error"))
    def test_should_preserve_original_exception_chain_on_import_error(self, mock_import):
        """Test that original ImportError is preserved in exception chain."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        with pytest.raises(ModelLoadError) as exc_info:
            manager.load_model()
        
        assert isinstance(exc_info.value.__cause__, ImportError)
    
    @patch('gliner.GLiNER')
    def test_should_preserve_original_exception_chain_on_load_error(self, mock_gliner_class):
        """Test that original Exception is preserved in exception chain."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        original_error = RuntimeError("Model file not found")
        mock_gliner_class.from_pretrained.side_effect = original_error
        
        with pytest.raises(ModelLoadError) as exc_info:
            manager.load_model()
        
        assert exc_info.value.__cause__ == original_error
    
    @patch('gliner.GLiNER')
    def test_should_log_loading_info_before_loading(self, mock_gliner_class):
        """Test that info is logged before attempting to load model."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "specific-model-id"
        manager = GLiNERModelManager(config)
        
        mock_gliner_class.from_pretrained.return_value = Mock()
        
        with patch.object(manager.logger, 'info') as mock_info:
            manager.load_model()
            
            first_call = str(mock_info.call_args_list[0])
            assert "Loading GLiNER model: specific-model-id" in first_call
    
    @patch('gliner.GLiNER')
    def test_should_log_success_after_loading(self, mock_gliner_class):
        """Test that success is logged after model is loaded."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        mock_gliner_class.from_pretrained.return_value = Mock()
        
        with patch.object(manager.logger, 'info') as mock_info:
            manager.load_model()
            
            second_call = str(mock_info.call_args_list[1])
            assert "GLiNER model loaded successfully" in second_call
    
    @patch('gliner.GLiNER')
    def test_should_return_model_instance(self, mock_gliner_class):
        """Test that the method returns the loaded model instance."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        expected_model = Mock(name="GLiNERModel")
        mock_gliner_class.from_pretrained.return_value = expected_model
        
        result = manager.load_model()
        
        assert result is expected_model


class TestEdgeCases:
    """Test cases for edge cases and error scenarios."""
    
    def test_should_handle_empty_model_id(self):
        """Test behavior with empty model_id."""
        config = Mock(spec=DetectionConfig)
        config.model_id = ""
        
        manager = GLiNERModelManager(config)
        
        assert manager.config.model_id == ""
    
    def test_should_handle_none_model_id_in_logging(self):
        """Test download_model handles None model_id gracefully."""
        config = Mock(spec=DetectionConfig)
        config.model_id = None
        manager = GLiNERModelManager(config)
        
        # Should not raise
        with patch.object(manager.logger, 'info'):
            manager.download_model()
    
    @patch('gliner.GLiNER')
    def test_should_handle_unicode_in_error_messages(self, mock_gliner_class):
        """Test handling of unicode characters in error messages."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        unicode_error = "Erreur de chargement: modèle introuvable 🚫"
        mock_gliner_class.from_pretrained.side_effect = Exception(unicode_error)
        
        with pytest.raises(ModelLoadError) as exc_info:
            manager.load_model()
        
        assert unicode_error in str(exc_info.value)


class TestIntegration:
    """Integration test cases for complete workflows."""
    
    @patch('gliner.GLiNER')
    def test_should_complete_full_workflow_successfully(self, mock_gliner_class):
        """Test complete workflow from initialization to model loading."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "urchade/gliner_multi_pii-v1"
        
        mock_model = Mock(name="LoadedGLiNERModel")
        mock_gliner_class.from_pretrained.return_value = mock_model
        
        # Initialize
        manager = GLiNERModelManager(config)
        
        # Download (no-op)
        manager.download_model()
        
        # Load
        loaded_model = manager.load_model()
        
        assert loaded_model is mock_model
        assert manager.config.model_id == "urchade/gliner_multi_pii-v1"
    
    @patch('gliner.GLiNER')
    def test_should_handle_multiple_load_attempts(self, mock_gliner_class):
        """Test that load_model can be called multiple times."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        manager = GLiNERModelManager(config)
        
        mock_model1 = Mock(name="Model1")
        mock_model2 = Mock(name="Model2")
        mock_gliner_class.from_pretrained.side_effect = [mock_model1, mock_model2]
        
        result1 = manager.load_model()
        result2 = manager.load_model()
        
        assert result1 is mock_model1
        assert result2 is mock_model2
        assert mock_gliner_class.from_pretrained.call_count == 2
