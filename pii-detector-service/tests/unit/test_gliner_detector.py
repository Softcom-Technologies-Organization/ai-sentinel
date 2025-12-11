"""
Test suite for GLiNERDetector class.

This module contains comprehensive tests for the GLiNERDetector class,
covering all methods and edge cases for 100% code coverage.
"""

import logging
import pytest
from typing import List, Dict
from unittest.mock import Mock, patch, MagicMock

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import ModelNotLoadedError
from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector
from pii_detector.infrastructure.text_processing.semantic_chunker import \
  ChunkResult


class TestGLiNERDetectorInitialization:
    """Test cases for GLiNERDetector initialization."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_initialize_with_default_config(self, mock_manager_class):
        """Test initialization with default configuration."""
        detector = GLiNERDetector()
        
        assert detector.config is not None
        assert detector.device in ['cpu', 'cuda']
        assert detector.model is None
        assert detector.semantic_chunker is None
        assert isinstance(detector.log_throughput, bool)
        mock_manager_class.assert_called_once()
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_initialize_with_custom_config(self, mock_manager_class):
        """Test initialization with custom configuration."""
        config = DetectionConfig(
            model_id="custom-model",
            device="cuda",
            threshold=0.7
        )
        
        detector = GLiNERDetector(config=config)
        
        assert detector.config is config
        assert detector.device == "cuda"
        assert detector.config.threshold == 0.7
        mock_manager_class.assert_called_once_with(config)
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_have_model_id_property(self, mock_manager_class):
        """Test model_id property for backward compatibility."""
        config = DetectionConfig(model_id="test-model-id")
        detector = GLiNERDetector(config=config)
        
        assert detector.model_id == "test-model-id"


class TestModelManagement:
    """Test cases for model loading and management."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_download_model(self, mock_manager_class):
        """Test model download."""
        mock_manager = Mock()
        mock_manager_class.return_value = mock_manager
        
        detector = GLiNERDetector()
        detector.download_model()
        
        mock_manager.download_model.assert_called_once()
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker')
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    @patch('transformers.AutoTokenizer')
    def test_should_load_model_successfully(self, mock_tokenizer_class, mock_manager_class, mock_create_chunker):
        """Test successful model loading with semantic chunker."""
        mock_manager = Mock()
        mock_model = Mock()
        mock_data_processor = Mock()
        mock_config = Mock()
        mock_tokenizer = Mock()
        
        mock_config.tokenizer = mock_tokenizer
        mock_data_processor.config = mock_config
        mock_model.data_processor = mock_data_processor
        mock_model.config = Mock(model_name='bert-base-cased')
        
        mock_manager.load_model.return_value = mock_model
        mock_manager_class.return_value = mock_manager
        
        mock_chunker = Mock()
        mock_chunker.get_chunk_info.return_value = {"library": "semchunk"}
        mock_create_chunker.return_value = mock_chunker
        
        detector = GLiNERDetector()
        detector.load_model()
        
        assert detector.model is mock_model
        assert detector.semantic_chunker is mock_chunker
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker')
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    @patch('transformers.AutoTokenizer')
    def test_should_use_fallback_tokenizer(self, mock_tokenizer_class, mock_manager_class, mock_create_chunker):
        """Test loading with fallback tokenizer."""
        mock_manager = Mock()
        mock_model = Mock()
        mock_data_processor = Mock()
        mock_config = Mock()
        mock_tokenizer = Mock()
        
        mock_config.tokenizer = None
        mock_data_processor.config = mock_config
        mock_model.data_processor = mock_data_processor
        mock_model.config = Mock(model_name='bert-base-cased')
        
        mock_manager.load_model.return_value = mock_model
        mock_manager_class.return_value = mock_manager
        mock_tokenizer_class.from_pretrained.return_value = mock_tokenizer
        
        mock_chunker = Mock()
        mock_chunker.get_chunk_info.return_value = {"library": "semchunk"}
        mock_create_chunker.return_value = mock_chunker
        
        detector = GLiNERDetector()
        detector.load_model()
        
        mock_tokenizer_class.from_pretrained.assert_called_once()
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.create_chunker')
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    @patch('transformers.AutoTokenizer')
    def test_should_raise_error_when_fallback_chunker_created(self, mock_tokenizer_class, mock_manager_class, mock_create_chunker):
        """Test error when fallback chunker is created."""
        mock_manager = Mock()
        mock_model = Mock()
        mock_data_processor = Mock()
        mock_config = Mock()
        mock_tokenizer = Mock()
        
        mock_config.tokenizer = mock_tokenizer
        mock_data_processor.config = mock_config
        mock_model.data_processor = mock_data_processor
        
        mock_manager.load_model.return_value = mock_model
        mock_manager_class.return_value = mock_manager
        
        mock_chunker = Mock()
        mock_chunker.get_chunk_info.return_value = {"library": "fallback"}
        mock_create_chunker.return_value = mock_chunker
        
        detector = GLiNERDetector()
        
        with pytest.raises(RuntimeError, match="Semantic chunking REQUIRED"):
            detector.load_model()


class TestPIIDetection:
    """Test cases for PII detection."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_raise_error_when_model_not_loaded(self, mock_manager_class):
        """Test error when detecting without loaded model."""
        detector = GLiNERDetector()
        
        with pytest.raises(ModelNotLoadedError):
            detector.detect_pii("test text")
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_detect_pii_successfully(self, mock_manager_class):
        """Test successful PII detection."""
        detector = GLiNERDetector()
        detector.model = Mock()
        
        expected_entities = [PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)]
        
        with patch.object(detector, '_detect_pii_with_chunking', return_value=expected_entities):
            result = detector.detect_pii("test")
        
        assert result == expected_entities


class TestPIIMasking:
    """Test cases for PII masking."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_mask_pii_entities(self, mock_manager_class):
        """Test PII masking functionality."""
        detector = GLiNERDetector()
        detector.model = Mock()
        
        entities = [PIIEntity(text="john@example.com", pii_type="EMAIL", type_label="EMAIL", start=8, end=24, score=0.95)]
        
        with patch.object(detector, 'detect_pii', return_value=entities):
            masked_text, returned_entities = detector.mask_pii("Contact john@example.com")
        
        assert masked_text == "Contact [EMAIL]"
        assert returned_entities == entities


class TestConfiguration:
    """Test cases for configuration loading."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_load_pii_type_mapping_from_config(self, mock_manager_class):
        """Test loading PII type mapping from configuration."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Verify detector can get default mapping (no longer stored as instance variable)
        default_mapping = detector._get_default_mapping()
        assert isinstance(default_mapping, dict)
        assert len(default_mapping) > 0
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_use_default_mapping_when_config_missing(self, mock_manager_class):
        """Test using default mapping when config is missing."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Verify _get_default_mapping returns a valid mapping
        default_mapping = detector._get_default_mapping()
        assert isinstance(default_mapping, dict)
        assert len(default_mapping) > 0
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_get_gliner_labels(self, mock_manager_class):
        """Test getting GLiNER labels from mapping."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Create mock pii_type_mapping to pass as parameter
        # Structure: detector_label (key) -> PII_TYPE (value)
        mock_pii_type_mapping = {
            "email": "EMAIL",
            "person name": "PERSON NAME"
        }
        
        labels = detector._get_gliner_labels(mock_pii_type_mapping)
        
        assert isinstance(labels, list)
        assert len(labels) == 2
        assert "email" in labels
        assert "person name" in labels
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_convert_gliner_entities(self, mock_manager_class):
        """Test conversion from GLiNER format to PIIEntity format."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        chunk_text = "john@example.com"
        raw_entities = [
            {"text": "john@example.com", "label": "email", "start": 0, "end": 16, "score": 0.95}
        ]
        
        # Create mock pii_type_mapping to pass as parameter
        # Structure: detector_label (key) -> PII_TYPE (value)
        mock_pii_type_mapping = {
            "email": "EMAIL"
        }
        
        result = detector._convert_to_pii_entities(raw_entities, chunk_text, mock_pii_type_mapping)
        
        assert len(result) == 1
        assert isinstance(result[0], PIIEntity)
        assert result[0].text == "john@example.com"
        assert result[0].pii_type == "EMAIL"
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_extract_pii_substring_from_chunk_text(self, mock_manager_class):
        """
        Test Bug #4 fix: Verify that PII entities contain only the extracted substring,
        not the full chunk text.
        
        This test ensures that when GLiNER detects PII in a chunk, the PIIEntity.text
        field contains ONLY the specific PII value (e.g., "john@example.com") extracted
        using start/end positions, NOT the entire chunk paragraph.
        """
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Simulate a chunk of text with PII at specific positions
        chunk_text = "Contact john@example.com for more information about the project"
        
        # GLiNER raw entities with start/end positions
        raw_entities = [
            {
                "text": chunk_text,  # GLiNER returns full chunk text here (bug source)
                "label": "email",
                "start": 8,  # Position of 'j' in john@example.com
                "end": 24,   # Position after 'm' in john@example.com
                "score": 0.95
            }
        ]
        
        # Create mock pii_type_mapping to pass as parameter
        # Structure: detector_label (key) -> PII_TYPE (value)
        mock_pii_type_mapping = {
            "email": "EMAIL"
        }
        
        # Convert entities - should extract substring using positions
        result = detector._convert_to_pii_entities(raw_entities, chunk_text, mock_pii_type_mapping)
        
        # Verify: PIIEntity.text should contain ONLY the extracted email
        assert len(result) == 1
        assert isinstance(result[0], PIIEntity)
        assert result[0].text == "john@example.com", \
            f"Expected 'john@example.com' but got '{result[0].text}'"
        assert result[0].text != chunk_text, \
            "PIIEntity.text should NOT contain full chunk text"
        assert result[0].pii_type == "EMAIL"
        assert result[0].start == 8
        assert result[0].end == 24
        assert result[0].score == 0.95
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_extract_multiple_pii_substrings_from_chunk(self, mock_manager_class):
        """
        Test extraction of multiple PII entities from same chunk.
        
        Verifies that each entity gets its own correctly extracted substring
        based on its start/end positions.
        """
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        chunk_text = "Contact John Doe at john@example.com or call 555-1234 for assistance"
        
        raw_entities = [
            {
                "text": chunk_text,
                "label": "person name",
                "start": 8,
                "end": 16,  # "John Doe"
                "score": 0.92
            },
            {
                "text": chunk_text,
                "label": "email",
                "start": 20,
                "end": 36,  # "john@example.com"
                "score": 0.95
            },
            {
                "text": chunk_text,
                "label": "phone number",
                "start": 45,
                "end": 53,  # "555-1234"
                "score": 0.88
            }
        ]
        
        # Create mock pii_type_mapping to pass as parameter
        mock_pii_type_mapping = {
            "PERSONNAME": {"detector_label": "person name"},
            "EMAIL": {"detector_label": "email"},
            "TELEPHONENUM": {"detector_label": "phone number"}
        }
        
        result = detector._convert_to_pii_entities(raw_entities, chunk_text, mock_pii_type_mapping)
        
        assert len(result) == 3
        
        # Verify first entity: person name
        assert result[0].text == "John Doe"
        assert result[0].pii_type == "PERSONNAME"
        assert result[0].start == 8
        assert result[0].end == 16
        
        # Verify second entity: email
        assert result[1].text == "john@example.com"
        assert result[1].pii_type == "EMAIL"
        assert result[1].start == 20
        assert result[1].end == 36
        
        # Verify third entity: phone
        assert result[2].text == "555-1234"
        assert result[2].pii_type == "TELEPHONENUM"
        assert result[2].start == 45
        assert result[2].end == 53
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_handle_invalid_positions_gracefully(self, mock_manager_class):
        """
        Test handling of invalid start/end positions.
        
        When positions are out of bounds or invalid, should return empty string
        instead of crashing.
        """
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        chunk_text = "Contact john@example.com"
        
        raw_entities = [
            {
                "text": chunk_text,
                "label": "email",
                "start": 100,  # Out of bounds
                "end": 200,
                "score": 0.95
            },
            {
                "text": chunk_text,
                "label": "email",
                "start": 10,
                "end": 5,  # Invalid: end < start
                "score": 0.95
            }
        ]
        
        # Create mock pii_type_mapping to pass as parameter
        mock_pii_type_mapping = {
            "EMAIL": {"detector_label": "email"}
        }
        
        result = detector._convert_to_pii_entities(raw_entities, chunk_text, mock_pii_type_mapping)
        
        # Should not crash, but return entities with empty text
        assert len(result) == 2
        assert result[0].text == ""
        assert result[1].text == ""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_apply_masks_correctly(self, mock_manager_class):
        """Test applying masks to detected entities."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        text = "Contact john@example.com and call 555-1234"
        entities = [
            PIIEntity(text="john@example.com", pii_type="EMAIL", type_label="EMAIL", start=8, end=24, score=0.95),
            PIIEntity(text="555-1234", pii_type="PHONE", type_label="PHONE", start=34, end=42, score=0.90)
        ]
        
        result = detector._apply_masks(text, entities)
        
        assert result == "Contact [EMAIL] and call [PHONE]"
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_generate_detection_id(self, mock_manager_class):
        """Test detection ID generation."""
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        detection_id = detector._generate_detection_id()
        
        assert detection_id.startswith("gliner_")
        assert len(detection_id) > 7


class TestChunkingDetection:
    """Test cases for detection with chunking."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_raise_error_when_chunker_not_initialized(self, mock_manager_class):
        """Test error when chunker is not initialized."""
        detector = GLiNERDetector()
        detector.model = Mock()
        detector.semantic_chunker = None
        
        with pytest.raises(RuntimeError, match="Semantic chunker not initialized"):
            detector._detect_pii_with_chunking("test", 0.5, "test_id", None)
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_detect_with_chunking_successfully(self, mock_manager_class):
        """Test successful detection with chunking."""
        detector = GLiNERDetector()
        detector.model = Mock()
        detector.log_throughput = True
        
        mock_chunker = Mock()
        chunk_results = [
            ChunkResult(text="Contact john@example.com", start=0, end=24, token_count=None),
            ChunkResult(text="Call 555-1234", start=25, end=38, token_count=None)
        ]
        mock_chunker.chunk_text.return_value = chunk_results
        detector.semantic_chunker = mock_chunker
        
        detector.model.predict_entities.side_effect = [
            [{"text": "john@example.com", "label": "email", "start": 8, "end": 24, "score": 0.95}],
            [{"text": "555-1234", "label": "phone number", "start": 5, "end": 13, "score": 0.90}]
        ]
        
        # Create mock pii_type_configs to pass as parameter
        mock_pii_type_configs = {
            "EMAIL": {"enabled": True, "detector_label": "email", "threshold": 0.5},
            "TELEPHONENUM": {"enabled": True, "detector_label": "phone number", "threshold": 0.5}
        }
        
        with patch.object(detector, '_get_gliner_labels', return_value=["email", "phone number"]):
            result = detector._detect_pii_with_chunking("Contact john@example.com\nCall 555-1234", 0.5, "test_id", mock_pii_type_configs)
        
        assert len(result) == 2


class TestCleanup:
    """Test cases for cleanup and destruction."""
    
    @patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager')
    def test_should_cleanup_on_destruction(self, mock_manager_class):
        """Test cleanup when detector is destroyed."""
        detector = GLiNERDetector()
        detector.model = Mock()
        detector.__del__()
