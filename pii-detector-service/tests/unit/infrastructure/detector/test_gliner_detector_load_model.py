"""
Tests for GLiNER detector load_model method.

These tests validate the behavior of load_model() method
before and after refactoring to ensure no regression.
"""

import pytest
from unittest.mock import Mock, MagicMock, patch
from typing import Any

from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector
from pii_detector.application.config.detection_policy import DetectionConfig


class TestGLiNERDetectorLoadModel:
    """Test suite for load_model method refactoring."""

    @pytest.fixture
    def mock_config(self):
        """Create a mock configuration."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = "cpu"
        config.threshold = 0.5
        return config

    @pytest.fixture
    def detector(self, mock_config):
        """Create detector with mocked model manager."""
        with patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager'):
            return GLiNERDetector(config=mock_config)

    def test_should_LoadModel_When_TokenizerAvailableInDataProcessor(self, detector):
        """
        Test successful model loading when tokenizer is available in data_processor.
        
        This is the happy path where GLiNER model has tokenizer properly configured.
        """
        # Arrange
        mock_model = Mock()
        mock_tokenizer = Mock()
        mock_tokenizer.name = "bert-tokenizer"
        
        # Configure model with tokenizer in data_processor.config
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = mock_tokenizer
        
        detector.model_manager.load_model = Mock(return_value=mock_model)
        
        # Mock semantic chunker creation
        mock_chunker = Mock()
        mock_chunker.get_chunk_info = Mock(return_value={"library": "semchunk"})
        
        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker', 
                   return_value=mock_chunker) as mock_create:
            # Act
            detector.load_model()
            
            # Assert
            assert detector.model == mock_model
            assert detector.semantic_chunker == mock_chunker
            
            # Verify create_chunker was called with correct params
            mock_create.assert_called_once()
            call_kwargs = mock_create.call_args.kwargs
            assert call_kwargs['tokenizer'] == mock_tokenizer
            assert call_kwargs['chunk_size'] == 378  # GLiNER's internal token limit
            assert call_kwargs['overlap'] == 100
            assert call_kwargs['use_semantic'] is False  # Character-based chunking

    def test_should_LoadModel_When_TokenizerNotInDataProcessor_UsesFallback(self, detector):
        """
        Test model loading when tokenizer is not in data_processor (fallback path).
        
        Should fallback to loading tokenizer from model name using AutoTokenizer.
        """
        # Arrange
        mock_model = Mock()
        
        # Configure model WITHOUT tokenizer in data_processor
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = None
        
        # Configure model name for fallback
        mock_model.config = Mock()
        mock_model.config.model_name = "bert-base-cased"
        
        detector.model_manager.load_model = Mock(return_value=mock_model)
        
        # Mock AutoTokenizer
        mock_auto_tokenizer = Mock()
        
        # Mock semantic chunker
        mock_chunker = Mock()
        mock_chunker.get_chunk_info = Mock(return_value={"library": "semchunk"})
        
        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   return_value=mock_chunker):
            with patch('transformers.AutoTokenizer.from_pretrained',
                       return_value=mock_auto_tokenizer) as mock_from_pretrained:
                # Act
                detector.load_model()
                
                # Assert
                assert detector.model == mock_model
                assert detector.semantic_chunker == mock_chunker
                
                # Verify AutoTokenizer.from_pretrained was called with model name
                mock_from_pretrained.assert_called_once_with("bert-base-cased")

    def test_should_LoadModel_When_ChunkerUsesFallbackLibrary(self, detector):
        """
        Test model loads successfully even when chunker uses fallback library.

        The detector now uses character-based chunking which doesn't require semchunk.
        """
        # Arrange
        mock_model = Mock()
        mock_tokenizer = Mock()
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = mock_tokenizer

        detector.model_manager.load_model = Mock(return_value=mock_model)

        # Mock chunker that returns fallback library (not semchunk)
        mock_chunker = Mock()
        mock_chunker.get_chunk_info = Mock(return_value={"library": "fallback"})

        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   return_value=mock_chunker):
            # Act - should complete without error
            detector.load_model()

            # Assert
            assert detector.model == mock_model
            assert detector.semantic_chunker == mock_chunker

    def test_should_RaiseError_When_ChunkerCreationFails(self, detector):
        """
        Test error handling when text chunker creation fails.
        """
        # Arrange
        mock_model = Mock()
        mock_tokenizer = Mock()
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = mock_tokenizer

        detector.model_manager.load_model = Mock(return_value=mock_model)

        # Mock create_chunker to raise an exception
        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   side_effect=Exception("Chunker initialization failed")):
            # Act & Assert
            with pytest.raises(RuntimeError,
                              match="Text chunker initialization failed"):
                detector.load_model()

    def test_should_RaiseError_When_ModelLoadingFails(self, detector):
        """
        Test error handling when model loading fails.
        """
        # Arrange
        detector.model_manager.load_model = Mock(
            side_effect=Exception("Model loading failed")
        )
        
        # Act & Assert
        with pytest.raises(Exception, match="Model loading failed"):
            detector.load_model()

    def test_should_HandleGetAttrFailure_When_AccessingTokenizer(self, detector):
        """
        Test handling when getattr fails while accessing tokenizer.
        
        Should fallback to AutoTokenizer.
        """
        # Arrange
        mock_model = Mock()
        
        # Configure model to raise AttributeError on getattr
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        # Make getattr return None (simulating missing tokenizer attribute)
        type(mock_model.data_processor.config).tokenizer = property(lambda self: None)
        
        mock_model.config = Mock()
        mock_model.config.model_name = "bert-base-cased"
        
        detector.model_manager.load_model = Mock(return_value=mock_model)
        
        mock_auto_tokenizer = Mock()
        mock_chunker = Mock()
        mock_chunker.get_chunk_info = Mock(return_value={"library": "semchunk"})
        
        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   return_value=mock_chunker):
            with patch('transformers.AutoTokenizer.from_pretrained',
                       return_value=mock_auto_tokenizer):
                # Act
                detector.load_model()
                
                # Assert - should complete successfully using fallback
                assert detector.model == mock_model
                assert detector.semantic_chunker == mock_chunker

    def test_should_LogSuccess_When_ModelLoadsCorrectly(self, detector, caplog):
        """
        Test that success messages are logged when model loads successfully.
        """
        # Arrange
        mock_model = Mock()
        mock_tokenizer = Mock()
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = mock_tokenizer

        detector.model_manager.load_model = Mock(return_value=mock_model)

        mock_chunker = Mock()
        mock_chunker.get_chunk_info = Mock(return_value={"library": "semchunk"})

        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   return_value=mock_chunker):
            # Act
            with caplog.at_level("INFO"):
                detector.load_model()

            # Assert - check log messages
            assert "GLiNER model loaded successfully" in caplog.text
            assert "Text chunker initialized successfully" in caplog.text

    def test_should_LogError_When_TextChunkerInitFails(self, detector, caplog):
        """
        Test that error is logged when text chunker initialization fails.
        """
        # Arrange
        mock_model = Mock()
        mock_tokenizer = Mock()
        mock_model.data_processor = Mock()
        mock_model.data_processor.config = Mock()
        mock_model.data_processor.config.tokenizer = mock_tokenizer

        detector.model_manager.load_model = Mock(return_value=mock_model)

        with patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker',
                   side_effect=Exception("Chunker failed")):
            # Act & Assert
            with caplog.at_level("ERROR"):
                with pytest.raises(RuntimeError):
                    detector.load_model()

            # Assert - check error was logged
            assert "CRITICAL: Failed to initialize text chunker" in caplog.text
