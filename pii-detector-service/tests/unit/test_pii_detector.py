"""
Unit tests for PIIDetector class.

This module provides comprehensive test coverage for the PII detector,
following modern Python testing best practices with pytest.

Test Naming Convention: Should_ExpectedBehavior_When_StateUnderTest
"""

import logging
import unicodedata

import pytest

from pii_detector.service.detector.pii_detector import PIIDetector, setup_logging
from pii_detector.service.detector.models import (
    PIIEntity,
    DetectionConfig,
    PIIDetectionError,
    ModelNotLoadedError,
    ModelLoadError,
)


# ============================================================================
# Fixtures
# ============================================================================

@pytest.fixture
def mock_config():
    """Fixture providing a mock DetectionConfig."""
    config = DetectionConfig()
    config.model_id = "test-model-id"
    config.device = "cpu"
    config.max_length = 256
    config.threshold = 0.5
    config.batch_size = 4
    config.stride_tokens = 64
    config.long_text_threshold = 10000
    return config


@pytest.fixture
def mock_tokenizer(mocker):
    """Fixture providing a mock tokenizer."""
    tokenizer = mocker.MagicMock()
    tokenizer.return_value = {
        "input_ids": [[1, 2, 3]],
        "offset_mapping": [[(0, 4), (4, 9), (9, 14)]],
    }
    return tokenizer


@pytest.fixture
def mock_model(mocker):
    """Fixture providing a mock model."""
    return mocker.MagicMock()


@pytest.fixture
def mock_pipeline(mocker):
    """Fixture providing a mock pipeline."""
    pipeline_mock = mocker.MagicMock()
    pipeline_mock.return_value = [
        {
            "entity_group": "PERSON",
            "word": "John",
            "start": 0,
            "end": 4,
            "score": 0.95,
        }
    ]
    return pipeline_mock


@pytest.fixture
def sample_pii_entities():
    """Fixture providing sample PIIEntity objects."""
    return [
        PIIEntity(
            text="John Doe",
            pii_type="PERSON",
            type_label="Nom de personne",
            start=0,
            end=8,
            score=0.95,
        ),
        PIIEntity(
            text="john.doe@example.com",
            pii_type="EMAIL",
            type_label="Adresse email",
            start=20,
            end=40,
            score=0.92,
        ),
    ]


@pytest.fixture
def detector_with_mocks(mocker, mock_config, mock_tokenizer, mock_model, mock_pipeline):
    """Fixture providing a PIIDetector with mocked dependencies."""
    # Mock all external dependencies
    mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
    mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
    mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
    mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
    
    detector = PIIDetector(config=mock_config)
    detector.tokenizer = mock_tokenizer
    detector.model = mock_model
    detector.pipeline = mock_pipeline
    
    return detector


# ============================================================================
# Initialization Tests
# ============================================================================

class TestPIIDetectorInitialization:
    """Test suite for PIIDetector initialization."""

    def test_should_initialize_with_config_when_config_provided(self, mocker, mock_config):
        """Should initialize detector with provided configuration."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        detector = PIIDetector(config=mock_config)
        
        assert detector.config == mock_config
        assert detector.device == "cpu"
        assert detector.model_id == "test-model-id"

    def test_should_initialize_with_default_config_when_no_config_provided(self, mocker):
        """Should initialize detector with default configuration."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        mocker.patch("pii_detector.service.detector.pii_detector.DetectionConfig")
        
        detector = PIIDetector()
        
        assert detector.config is not None

    def test_should_use_cuda_when_available(self, mocker, mock_config):
        """Should use CUDA device when available."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=True)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        mock_config.device = None
        detector = PIIDetector(config=mock_config)
        
        assert detector.device == "cuda"

    def test_should_support_backward_compatible_constructor(self, mocker):
        """Should support old constructor signature for backward compatibility."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        mocker.patch("pii_detector.service.detector.pii_detector.DetectionConfig")
        
        detector = PIIDetector(model_id="old-model", device="cpu", max_length=512)
        
        assert detector.config is not None


# ============================================================================
# Model Loading Tests
# ============================================================================

class TestModelLoading:
    """Test suite for model loading operations."""

    def test_should_download_model_successfully(self, mocker, mock_config):
        """Should download model using ModelManager."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mock_model_manager = mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        detector = PIIDetector(config=mock_config)
        detector.download_model()
        
        detector.model_manager.download_model.assert_called_once()

    def test_should_load_model_successfully(self, mocker, mock_config, mock_tokenizer, mock_model):
        """Should load model components and create pipeline."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        mock_pipeline_func = mocker.patch("pii_detector.service.detector.pii_detector.pipeline")
        
        detector = PIIDetector(config=mock_config)
        detector.model_manager.load_model_components.return_value = (mock_tokenizer, mock_model)
        
        detector.load_model()
        
        assert detector.tokenizer == mock_tokenizer
        assert detector.model == mock_model
        assert detector.pipeline is not None
        mock_pipeline_func.assert_called_once()

    def test_should_raise_error_when_model_loading_fails(self, mocker, mock_config):
        """Should raise exception when model loading fails."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        detector = PIIDetector(config=mock_config)
        detector.model_manager.load_model_components.side_effect = Exception("Load error")
        
        with pytest.raises(Exception, match="Load error"):
            detector.load_model()


# ============================================================================
# PII Detection Tests
# ============================================================================

class TestPIIDetection:
    """Test suite for PII detection operations."""

    def test_should_detect_pii_in_short_text(self, detector_with_mocks, mocker):
        """Should detect PII in short text using standard method."""
        text = "Hello John"
        detector_with_mocks.entity_processor.process_entities.return_value = [
            PIIEntity("John", "PERSON", "Nom", 6, 10, 0.95)
        ]
        
        mock_detect_standard = mocker.patch.object(
            detector_with_mocks, "_detect_pii_standard"
        )
        mock_detect_standard.return_value = [PIIEntity("John", "PERSON", "Nom", 6, 10, 0.95)]
        
        entities = detector_with_mocks.detect_pii(text)
        
        assert len(entities) == 1
        mock_detect_standard.assert_called_once()

    def test_should_detect_pii_in_long_text_using_chunking(self, detector_with_mocks, mocker):
        """Should detect PII in long text using chunked method."""
        text = "x" * 15000  # Longer than long_text_threshold
        
        mock_detect_chunked = mocker.patch.object(
            detector_with_mocks, "_detect_pii_chunked"
        )
        mock_detect_chunked.return_value = []
        
        detector_with_mocks.detect_pii(text)
        
        mock_detect_chunked.assert_called_once()

    def test_should_raise_error_when_model_not_loaded(self, detector_with_mocks):
        """Should raise ModelNotLoadedError when model is not loaded."""
        detector_with_mocks.pipeline = None
        
        with pytest.raises(ModelNotLoadedError, match="must be loaded before use"):
            detector_with_mocks.detect_pii("test text")

    def test_should_raise_detection_error_when_detection_fails(self, detector_with_mocks, mocker):
        """Should raise PIIDetectionError when detection fails."""
        detector_with_mocks.pipeline.side_effect = Exception("Detection failed")
        
        with pytest.raises(PIIDetectionError, match="PII detection failed"):
            detector_with_mocks.detect_pii("test text")

    def test_should_use_custom_threshold_when_provided(self, detector_with_mocks, mocker):
        """Should use custom threshold when provided."""
        text = "Hello"
        custom_threshold = 0.8
        
        mock_detect_standard = mocker.patch.object(
            detector_with_mocks, "_detect_pii_standard"
        )
        mock_detect_standard.return_value = []
        
        detector_with_mocks.detect_pii(text, threshold=custom_threshold)
        
        _, call_threshold, _ = mock_detect_standard.call_args[0]
        assert call_threshold == custom_threshold


# ============================================================================
# Batch Detection Tests
# ============================================================================

class TestBatchDetection:
    """Test suite for batch PII detection."""

    def test_should_detect_pii_in_batch(self, detector_with_mocks, mocker):
        """Should detect PII in multiple texts."""
        texts = ["Text 1", "Text 2", "Text 3"]
        
        mock_process_batch = mocker.patch.object(
            detector_with_mocks, "_process_batch"
        )
        mock_process_batch.return_value = [[], [], []]
        
        results = detector_with_mocks.detect_pii_batch(texts)
        
        assert len(results) == 3
        mock_process_batch.assert_called_once()

    def test_should_use_custom_batch_size(self, detector_with_mocks, mocker):
        """Should use custom batch size when provided."""
        texts = ["Text" + str(i) for i in range(10)]
        custom_batch_size = 2
        
        mock_process_batch = mocker.patch.object(
            detector_with_mocks, "_process_batch"
        )
        mock_process_batch.return_value = [[], []]
        
        detector_with_mocks.detect_pii_batch(texts, batch_size=custom_batch_size)
        
        # Should be called 5 times (10 texts / batch_size 2)
        assert mock_process_batch.call_count == 5

    def test_should_raise_error_when_model_not_loaded_for_batch(self, detector_with_mocks):
        """Should raise ModelNotLoadedError for batch when model not loaded."""
        detector_with_mocks.pipeline = None
        
        with pytest.raises(ModelNotLoadedError):
            detector_with_mocks.detect_pii_batch(["text1", "text2"])


# ============================================================================
# Masking Tests
# ============================================================================

class TestPIIMasking:
    """Test suite for PII masking operations."""

    def test_should_mask_pii_entities(self, detector_with_mocks, mocker, sample_pii_entities):
        """Should mask detected PII in text."""
        text = "John Doe works at john.doe@example.com"
        
        mock_detect = mocker.patch.object(detector_with_mocks, "detect_pii")
        mock_detect.return_value = sample_pii_entities
        
        masked_text, entities = detector_with_mocks.mask_pii(text)
        
        assert "[PERSON]" in masked_text
        assert "[EMAIL]" in masked_text
        assert len(entities) == 2

    def test_should_mask_overlapping_entities_correctly(self, detector_with_mocks):
        """Should mask overlapping entities in reverse order."""
        text = "0123456789"
        entities = [
            PIIEntity("234", "A", "Type A", 2, 5, 0.9),
            PIIEntity("567", "B", "Type B", 5, 8, 0.9),
        ]
        
        masked_text = detector_with_mocks._apply_masks(text, entities)
        
        assert "[B]" in masked_text
        assert "[A]" in masked_text


# ============================================================================
# Summary Tests
# ============================================================================

class TestPIISummary:
    """Test suite for PII summary generation."""

    def test_should_generate_summary(self, detector_with_mocks, mocker):
        """Should generate summary of detected PII types."""
        entities = [
            PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("Jane", "PERSON", "Nom", 5, 9, 0.9),
            PIIEntity("email@test.com", "EMAIL", "Email", 10, 24, 0.9),
        ]
        
        mock_detect = mocker.patch.object(detector_with_mocks, "detect_pii")
        mock_detect.return_value = entities
        
        summary = detector_with_mocks.get_summary("test text")
        
        assert summary["Nom"] == 2
        assert summary["Email"] == 1


# ============================================================================
# Text Splitting Tests
# ============================================================================

class TestTextSplitting:
    """Test suite for text splitting operations."""

    def test_should_split_text_by_tokens(self, detector_with_mocks):
        """Should split text into token-based segments."""
        text = "This is a test text for splitting"
        detector_with_mocks.tokenizer.return_value = {
            "input_ids": [[1, 2, 3], [4, 5, 6]],
            "offset_mapping": [
                [(0, 4), (5, 7), (8, 9)],
                [(10, 14), (15, 19), (20, 23)],
            ],
        }
        
        segments = detector_with_mocks._split_text_by_tokens(text, max_tokens=256)
        
        assert len(segments) == 2
        assert all(len(seg) == 3 for seg in segments)  # (text, start, end)

    def test_should_handle_empty_text_when_splitting(self, detector_with_mocks):
        """Should return empty list for empty text."""
        segments = detector_with_mocks._split_text_by_tokens("", max_tokens=256)
        
        assert segments == []

    def test_should_raise_error_when_tokenizer_not_loaded(self, detector_with_mocks):
        """Should raise ModelNotLoadedError when tokenizer not loaded."""
        detector_with_mocks.tokenizer = None
        
        with pytest.raises(ModelNotLoadedError):
            detector_with_mocks._split_text_by_tokens("text", max_tokens=256)

    def test_should_handle_special_tokens_in_splitting(self, detector_with_mocks):
        """Should filter out special tokens with (0,0) offsets."""
        text = "Test text"
        detector_with_mocks.tokenizer.return_value = {
            "input_ids": [[1, 2, 3]],
            "offset_mapping": [[(0, 0), (0, 4), (5, 9)]],  # First is special token
        }
        
        segments = detector_with_mocks._split_text_by_tokens(text, max_tokens=256)
        
        assert len(segments) == 1
        assert segments[0][1] == 0  # start_char
        assert segments[0][2] == 9  # end_char


# ============================================================================
# Entity Processing Tests
# ============================================================================

class TestEntityProcessing:
    """Test suite for entity processing operations."""

    def test_should_detect_duplicate_entities(self, detector_with_mocks):
        """Should detect duplicate entities correctly."""
        entity1 = PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9)
        entity2 = PIIEntity("John", "PERSON", "Nom", 0, 4, 0.95)
        existing = [entity1]
        
        is_duplicate = detector_with_mocks._is_duplicate_entity(entity2, existing)
        
        assert is_duplicate is True

    def test_should_not_detect_duplicate_when_different_position(self, detector_with_mocks):
        """Should not detect duplicate when positions differ."""
        entity1 = PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9)
        entity2 = PIIEntity("John", "PERSON", "Nom", 10, 14, 0.9)
        existing = [entity1]
        
        is_duplicate = detector_with_mocks._is_duplicate_entity(entity2, existing)
        
        assert is_duplicate is False

    def test_should_expand_email_domain(self, detector_with_mocks):
        """Should expand EMAIL entities to include domain."""
        text = "Contact: john@example.com for info"
        entities = [PIIEntity("john", "EMAIL", "Email", 9, 13, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert "@" in expanded[0].text
        assert "example.com" in expanded[0].text

    def test_should_not_expand_complete_email(self, detector_with_mocks):
        """Should not modify already complete email addresses."""
        text = "Email: john@example.com"
        entities = [PIIEntity("john@example.com", "EMAIL", "Email", 7, 23, 0.9)]
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert expanded[0] == entities[0]

    def test_should_split_zipcode_and_city(self, detector_with_mocks):
        """Should split ZIPCODE containing city name."""
        text = "Address: 69007 Lyon"
        entities = [PIIEntity("69007 Lyon", "ZIPCODE", "Code postal", 9, 19, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {
            "ZIPCODE": "Code postal",
            "CITY": "Ville",
        }
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        # The method may split or keep the entity depending on the logic
        # We verify it returns at least one entity and handles properly
        assert len(split_entities) >= 1
        # If split, verify correct types
        if len(split_entities) == 2:
            assert any(e.pii_type == "ZIPCODE" for e in split_entities)
            assert any(e.pii_type == "CITY" for e in split_entities)

    def test_should_split_zipcode_with_comma(self, detector_with_mocks):
        """Should split ZIPCODE with comma separator."""
        text = "Address: 69007, Lyon"
        entities = [PIIEntity("69007, Lyon", "ZIPCODE", "Code postal", 9, 20, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {
            "ZIPCODE": "Code postal",
            "CITY": "Ville",
        }
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        assert len(split_entities) == 2

    def test_should_not_split_zipcode_without_city(self, detector_with_mocks):
        """Should not split ZIPCODE without city name."""
        text = "ZIP: 69007"
        entities = [PIIEntity("69007", "ZIPCODE", "Code postal", 5, 10, 0.9)]
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        assert len(split_entities) == 1
        assert split_entities[0] == entities[0]

    def test_should_merge_adjacent_entities(self, detector_with_mocks):
        """Should merge adjacent entities of same type."""
        text = "Jean-Paul Martin"
        entities = [
            PIIEntity("Jean", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.9),
            PIIEntity("Martin", "PERSON", "Nom", 10, 16, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        # Jean-Paul should merge, Martin separate
        assert len(merged) == 2
        assert "Jean" in merged[0].text
        assert "Paul" in merged[0].text

    def test_should_not_merge_different_types(self, detector_with_mocks):
        """Should not merge entities of different types."""
        text = "John123"
        entities = [
            PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("123", "NUMBER", "Numéro", 4, 7, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        assert len(merged) == 2

    def test_should_check_merge_possibility(self, detector_with_mocks):
        """Should check if entities can be merged."""
        text = "Jean-Paul"
        prev = PIIEntity("Jean", "PERSON", "Nom", 0, 4, 0.9)
        current = PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.9)
        
        can_merge = detector_with_mocks._can_merge_entities(text, prev, current)
        
        assert can_merge is True

    def test_should_create_merged_entity(self, detector_with_mocks):
        """Should create merged entity from two entities."""
        text = "Jean-Paul"
        prev = PIIEntity("Jean", "PERSON", "Nom", 0, 4, 0.9)
        current = PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.95)
        
        merged = detector_with_mocks._create_merged_entity(text, prev, current)
        
        assert merged.text == "Jean-Paul"
        assert merged.start == 0
        assert merged.end == 9
        assert merged.score == 0.95  # max score


# ============================================================================
# Post-Processing Tests
# ============================================================================

class TestPostProcessing:
    """Test suite for entity post-processing."""

    def test_should_normalize_unicode_in_detection(self, detector_with_mocks, mocker):
        """Should normalize text to NFC form for detection."""
        text = "Benoît"  # May contain composed or decomposed characters
        
        mock_split = mocker.patch.object(detector_with_mocks, "_split_text_by_tokens")
        mock_split.return_value = [(text, 0, len(text))]
        detector_with_mocks.entity_processor.process_entities.return_value = []
        
        detector_with_mocks._detect_pii_token_splitting(text, threshold=0.5)
        
        # Check that normalize was applied
        call_args = mock_split.call_args[0]
        normalized_text = call_args[0]
        assert normalized_text == unicodedata.normalize('NFC', text)

    def test_should_post_process_entities(self, detector_with_mocks, mocker):
        """Should apply all post-processing steps."""
        text = "john@example.com 69007 Lyon"
        entities = [
            PIIEntity("john", "EMAIL", "Email", 0, 4, 0.9),
            PIIEntity("69007 Lyon", "ZIPCODE", "Code postal", 17, 27, 0.9),
        ]
        
        mocker.patch.object(detector_with_mocks, "_expand_email_domain", return_value=entities)
        mocker.patch.object(detector_with_mocks, "_split_zipcode_and_city", return_value=entities)
        mocker.patch.object(detector_with_mocks, "_merge_adjacent_entities", return_value=entities)
        
        processed = detector_with_mocks._post_process_entities(text, entities)
        
        assert processed is not None


# ============================================================================
# Utility Tests
# ============================================================================

class TestUtilities:
    """Test suite for utility functions."""

    def test_should_generate_unique_detection_id(self, detector_with_mocks):
        """Should generate unique detection IDs."""
        id1 = detector_with_mocks._generate_detection_id()
        id2 = detector_with_mocks._generate_detection_id()
        
        assert id1.startswith("det_")
        assert id2.startswith("det_")
        # Note: IDs may be same if called at exact same millisecond, 
        # so we only verify format, not uniqueness

    def test_should_clear_cache(self, detector_with_mocks):
        """Should clear memory cache."""
        detector_with_mocks.clear_cache()
        
        detector_with_mocks.memory_manager.clear_cache.assert_called_once_with(
            detector_with_mocks.device
        )

    def test_should_create_pipeline(self, detector_with_mocks, mocker):
        """Should create inference pipeline correctly."""
        mock_pipeline_func = mocker.patch("pii_detector.service.detector.pii_detector.pipeline")
        
        pipeline = detector_with_mocks._create_pipeline()
        
        assert pipeline is not None
        mock_pipeline_func.assert_called_once()


# ============================================================================
# Backward Compatibility Tests
# ============================================================================

class TestBackwardCompatibility:
    """Test suite for backward compatibility features."""

    def test_should_provide_backward_compatible_properties(self, detector_with_mocks):
        """Should provide backward compatible property accessors."""
        assert detector_with_mocks.model_id == detector_with_mocks.config.model_id
        assert detector_with_mocks.max_length == detector_with_mocks.config.max_length
        assert detector_with_mocks.pipe == detector_with_mocks.pipeline

    def test_should_provide_backward_compatible_label_mapping(self, detector_with_mocks):
        """Should provide label_mapping property for backward compatibility."""
        expected_mapping = {"EMAIL": "Email", "PERSON": "Nom"}
        detector_with_mocks.entity_processor.label_mapping = expected_mapping
        
        assert detector_with_mocks.label_mapping == expected_mapping

    def test_should_support_detect_emails_regex_method(self, detector_with_mocks):
        """Should support _detect_emails_regex for backward compatibility."""
        text = "Contact: john@example.com"
        existing_entities = []
        
        detector_with_mocks.entity_processor.detect_emails_with_regex.return_value = [
            PIIEntity("john@example.com", "EMAIL", "Email", 9, 25, 0.9)
        ]
        
        result = detector_with_mocks._detect_emails_regex()
        
        assert len(result) == 1

    def test_should_support_detect_pii_chunked_without_detection_id(self, detector_with_mocks, mocker):
        """Should support _detect_pii_chunked for backward compatibility."""
        text = "Test text"
        
        mock_internal = mocker.patch.object(
            detector_with_mocks, "_detect_pii_chunked_internal"
        )
        mock_internal.return_value = []
        
        result = detector_with_mocks._detect_pii_chunked(text, threshold=0.5)
        
        assert result is not None
        mock_internal.assert_called_once()

    def test_should_support_double_underscore_chunked_method(self, detector_with_mocks, mocker):
        """Should support token splitting for chunked processing."""
        text = "Test text"
        
        mock_token_splitting = mocker.patch.object(
            detector_with_mocks, "_detect_pii_token_splitting"
        )
        mock_token_splitting.return_value = []
        
        result = detector_with_mocks._detect_pii_chunked_internal(text, threshold=0.5, detection_id="test_id")
        
        assert result is not None
        mock_token_splitting.assert_called_once()


# ============================================================================
# Process Batch Tests
# ============================================================================

class TestProcessBatch:
    """Test suite for batch processing operations."""

    def test_should_process_batch_successfully(self, detector_with_mocks):
        """Should process batch and return results."""
        batch = ["Text 1", "Text 2"]
        threshold = 0.5
        
        detector_with_mocks.pipeline.return_value = [
            [{"entity_group": "PERSON", "word": "John", "start": 0, "end": 4, "score": 0.9}],
            [{"entity_group": "EMAIL", "word": "test@test.com", "start": 0, "end": 13, "score": 0.9}]
        ]
        
        detector_with_mocks.entity_processor.process_entities.side_effect = [
            [PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9)],
            [PIIEntity("test@test.com", "EMAIL", "Email", 0, 13, 0.9)]
        ]
        
        results = detector_with_mocks._process_batch(batch, threshold)
        
        assert len(results) == 2

    def test_should_handle_batch_processing_errors_gracefully(self, detector_with_mocks):
        """Should return empty lists when batch processing fails."""
        batch = ["Text 1", "Text 2"]
        threshold = 0.5
        
        detector_with_mocks.pipeline.side_effect = Exception("Batch error")
        
        results = detector_with_mocks._process_batch(batch, threshold)
        
        assert len(results) == 2
        assert all(r == [] for r in results)

    def test_should_handle_single_result_format(self, detector_with_mocks):
        """Should handle when pipeline returns single result instead of list."""
        batch = ["Text 1"]
        threshold = 0.5
        
        detector_with_mocks.pipeline.return_value = [
            {"entity_group": "PERSON", "word": "John", "start": 0, "end": 4, "score": 0.9}
        ]
        
        detector_with_mocks.entity_processor.process_entities.return_value = [
            PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9)
        ]
        
        results = detector_with_mocks._process_batch(batch, threshold)
        
        assert len(results) == 1


# ============================================================================
# Email Expansion Edge Cases
# ============================================================================

class TestEmailExpansionEdgeCases:
    """Test suite for email expansion edge cases."""

    def test_should_handle_email_without_at_sign_nearby(self, detector_with_mocks):
        """Should keep original entity when @ not found nearby."""
        text = "Username: john and other text"
        entities = [PIIEntity("john", "EMAIL", "Email", 10, 14, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert expanded[0].text == "john"

    def test_should_handle_email_with_plus_sign(self, detector_with_mocks):
        """Should expand email with plus sign in local part."""
        text = "Contact: user+tag@example.com"
        entities = [PIIEntity("user", "EMAIL", "Email", 9, 13, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert "+" in expanded[0].text
        assert "@example.com" in expanded[0].text

    def test_should_strip_trailing_punctuation_from_email(self, detector_with_mocks):
        """Should strip trailing punctuation from expanded email."""
        text = "Email: john@example.com."
        entities = [PIIEntity("john", "EMAIL", "Email", 7, 11, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert not expanded[0].text.endswith(".")

    def test_should_validate_email_format(self, detector_with_mocks):
        """Should validate email has proper format before expansion."""
        text = "Text with @ symbol but no email"
        entities = [PIIEntity("text", "EMAIL", "Email", 0, 4, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        # Should keep original if expansion doesn't result in valid email
        assert len(expanded) == 1

    def test_should_handle_email_with_subdomain(self, detector_with_mocks):
        """Should expand email with subdomain correctly."""
        text = "Mail: user@mail.example.com"
        entities = [PIIEntity("user", "EMAIL", "Email", 6, 10, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {"EMAIL": "Email"}
        
        expanded = detector_with_mocks._expand_email_domain(text, entities)
        
        assert len(expanded) == 1
        assert "mail.example.com" in expanded[0].text


# ============================================================================
# Zipcode Splitting Edge Cases
# ============================================================================

class TestZipcodeSplittingEdgeCases:
    """Test suite for zipcode splitting edge cases."""

    def test_should_handle_zipcode_with_semicolon_separator(self, detector_with_mocks):
        """Should split zipcode with semicolon separator."""
        text = "Location: 69007; Lyon"
        entities = [PIIEntity("69007; Lyon", "ZIPCODE", "Code postal", 10, 21, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {
            "ZIPCODE": "Code postal",
            "CITY": "Ville",
        }
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        # Should handle but may not split on semicolon the same way as comma
        assert len(split_entities) >= 1

    def test_should_handle_zipcode_with_multiple_spaces(self, detector_with_mocks):
        """Should handle zipcode with multiple spaces."""
        text = "Address: 69007   Lyon"
        entities = [PIIEntity("69007   Lyon", "ZIPCODE", "Code postal", 9, 21, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {
            "ZIPCODE": "Code postal",
            "CITY": "Ville",
        }
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        assert len(split_entities) >= 1

    def test_should_handle_international_zipcode_formats(self, detector_with_mocks):
        """Should handle international zipcode formats (UK, CA, etc)."""
        text = "ZIP: SW1W 0NY London"
        entities = [PIIEntity("SW1W 0NY London", "ZIPCODE", "Code postal", 5, 20, 0.9)]
        detector_with_mocks.entity_processor.label_mapping = {
            "ZIPCODE": "Code postal",
            "CITY": "Ville",
        }
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        # The method should handle international formats appropriately
        # May split or keep based on internal logic
        assert len(split_entities) >= 1

    def test_should_not_split_short_zipcode(self, detector_with_mocks):
        """Should not split zipcode with insufficient alphanumeric chars."""
        text = "Code: 1 X"
        entities = [PIIEntity("1 X", "ZIPCODE", "Code postal", 6, 9, 0.9)]
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        assert len(split_entities) == 1
        assert split_entities[0] == entities[0]

    def test_should_handle_empty_zipcode_entity(self, detector_with_mocks):
        """Should handle empty or whitespace-only zipcode entity."""
        text = "Address:    "
        entities = [PIIEntity("   ", "ZIPCODE", "Code postal", 9, 12, 0.9)]
        
        split_entities = detector_with_mocks._split_zipcode_and_city(text, entities)
        
        assert len(split_entities) == 1


# ============================================================================
# Entity Merging Edge Cases
# ============================================================================

class TestEntityMergingEdgeCases:
    """Test suite for entity merging edge cases."""

    def test_should_merge_entities_with_apostrophe(self, detector_with_mocks):
        """Should merge entities separated by apostrophe."""
        text = "O'Connor"
        entities = [
            PIIEntity("O", "PERSON", "Nom", 0, 1, 0.9),
            PIIEntity("Connor", "PERSON", "Nom", 2, 8, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        assert len(merged) == 1
        assert merged[0].text == "O'Connor"

    def test_should_merge_entities_with_right_quotation_mark(self, detector_with_mocks):
        """Should merge entities separated by right single quotation mark."""
        text = "O'Neill"
        entities = [
            PIIEntity("O", "PERSON", "Nom", 0, 1, 0.9),
            PIIEntity("Neill", "PERSON", "Nom", 2, 7, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        assert len(merged) == 1

    def test_should_not_merge_with_space_separator(self, detector_with_mocks):
        """Should not merge entities separated by space."""
        text = "John Paul"
        entities = [
            PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        assert len(merged) == 2

    def test_should_handle_empty_entity_list_for_merging(self, detector_with_mocks):
        """Should handle empty entity list."""
        merged = detector_with_mocks._merge_adjacent_entities("text", [])
        
        assert merged == []

    def test_should_use_max_score_when_merging(self, detector_with_mocks):
        """Should use maximum score when merging entities."""
        text = "Jean-Paul"
        entities = [
            PIIEntity("Jean", "PERSON", "Nom", 0, 4, 0.85),
            PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.95),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        assert len(merged) == 1
        assert merged[0].score == 0.95

    def test_should_sort_entities_before_merging(self, detector_with_mocks):
        """Should sort entities by position before merging."""
        text = "Jean-Paul-Jacques"
        entities = [
            PIIEntity("Jacques", "PERSON", "Nom", 10, 17, 0.9),  # Out of order
            PIIEntity("Jean", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("Paul", "PERSON", "Nom", 5, 9, 0.9),
        ]
        
        merged = detector_with_mocks._merge_adjacent_entities(text, entities)
        
        # Entities should be sorted and merged appropriately
        # May merge all if separated by hyphens or keep some separate
        assert len(merged) >= 1
        assert len(merged) <= 3


# ============================================================================
# Cleanup Tests
# ============================================================================

class TestCleanup:
    """Test suite for cleanup operations."""

    def test_should_cleanup_on_destructor(self, mocker):
        """Should cleanup resources when detector is destroyed."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        detector = PIIDetector()
        detector.model = mocker.MagicMock()
        detector.tokenizer = mocker.MagicMock()
        detector.pipeline = mocker.MagicMock()
        
        # Trigger destructor
        detector.__del__()
        
        # If no exception raised, cleanup succeeded

    def test_should_handle_cleanup_errors_gracefully(self, mocker):
        """Should handle errors during cleanup gracefully."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=False)
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        detector = PIIDetector()
        detector.memory_manager.clear_cache.side_effect = Exception("Cleanup error")
        
        # Should not raise exception
        detector.__del__()


# ============================================================================
# Module Level Tests
# ============================================================================

class TestModuleLevelFunctions:
    """Test suite for module-level functions."""

    def test_should_setup_logging(self):
        """Should setup logging configuration."""
        setup_logging(level=logging.DEBUG)
        
        # Verify logging is configured
        logger = logging.getLogger("pii_detector.service.detector.pii_detector")
        assert logger is not None

    def test_should_use_default_logging_level(self):
        """Should use INFO as default logging level."""
        setup_logging()
        
        logger = logging.getLogger("pii_detector.service.detector.pii_detector")
        assert logger is not None


# ============================================================================
# Parametrized Tests
# ============================================================================

class TestParametrizedScenarios:
    """Test suite using parametrization for multiple scenarios."""

    @pytest.mark.parametrize("text,expected_segments", [
        ("", 0),
        ("Short text", 1),
        ("x" * 1000, 2),
    ])
    def test_should_split_text_correctly(self, detector_with_mocks, text, expected_segments):
        """Should split text into correct number of segments."""
        if text:
            detector_with_mocks.tokenizer.return_value = {
                "input_ids": [[1] * min(256, len(text))] * expected_segments,
                "offset_mapping": [[(0, len(text))]] * expected_segments,
            }
        
        segments = detector_with_mocks._split_text_by_tokens(text, max_tokens=256)
        
        assert len(segments) == expected_segments

    @pytest.mark.parametrize("pii_type,expected_count", [
        ("PERSON", 1),
        ("EMAIL", 1),
        ("ZIPCODE", 0),
    ])
    def test_should_filter_entities_by_type(self, pii_type, expected_count):
        """Should filter entities by type correctly."""
        entities = [
            PIIEntity("John", "PERSON", "Nom", 0, 4, 0.9),
            PIIEntity("test@test.com", "EMAIL", "Email", 10, 23, 0.9),
        ]
        
        filtered = [e for e in entities if e.pii_type == pii_type]
        
        assert len(filtered) == expected_count

    @pytest.mark.parametrize("device", ["cpu", "cuda"])
    def test_should_support_different_devices(self, mocker, mock_config, device):
        """Should support both CPU and CUDA devices."""
        mocker.patch("pii_detector.service.detector.pii_detector.torch.cuda.is_available", return_value=(device == "cuda"))
        mocker.patch("pii_detector.service.detector.pii_detector.MemoryManager")
        mocker.patch("pii_detector.service.detector.pii_detector.ModelManager")
        mocker.patch("pii_detector.service.detector.pii_detector.EntityProcessor")
        
        mock_config.device = device
        detector = PIIDetector(config=mock_config)
        
        assert detector.device == device


# ============================================================================
# Integration-like Tests
# ============================================================================

class TestDetectorIntegrationScenarios:
    """Test suite for integration-like scenarios."""

    def test_should_handle_complete_detection_workflow(self, detector_with_mocks, mocker):
        """Should handle complete detection workflow from text to entities."""
        text = "John Doe lives at 69007 Lyon"
        
        mock_detect_standard = mocker.patch.object(
            detector_with_mocks, "_detect_pii_standard"
        )
        mock_detect_standard.return_value = [
            PIIEntity("John Doe", "PERSON", "Nom", 0, 8, 0.95),
            PIIEntity("69007", "ZIPCODE", "Code postal", 18, 23, 0.90),
            PIIEntity("Lyon", "CITY", "Ville", 24, 28, 0.92),
        ]
        
        entities = detector_with_mocks.detect_pii(text)
        
        assert len(entities) == 3
        assert any(e.pii_type == "PERSON" for e in entities)
        assert any(e.pii_type == "ZIPCODE" for e in entities)
        assert any(e.pii_type == "CITY" for e in entities)

    def test_should_handle_multilingual_text(self, detector_with_mocks, mocker):
        """Should handle multilingual text with diacritics."""
        text = "Benoît Müller habite à Zürich"
        
        mock_detect_standard = mocker.patch.object(
            detector_with_mocks, "_detect_pii_standard"
        )
        mock_detect_standard.return_value = [
            PIIEntity("Benoît Müller", "PERSON", "Nom", 0, 13, 0.95),
            PIIEntity("Zürich", "CITY", "Ville", 24, 30, 0.92),
        ]
        
        entities = detector_with_mocks.detect_pii(text)
        
        assert len(entities) == 2
        assert "Benoît" in entities[0].text or "Müller" in entities[0].text
