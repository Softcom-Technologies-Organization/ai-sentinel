"""
Test suite for MultiPassGlinerDetector class.

This module contains comprehensive tests for the MultiPassGlinerDetector class,
covering multi-pass detection, conflict resolution, and span aggregation.

Uses mocking to avoid GPU/model dependencies.
"""

import logging
import pytest
from typing import Dict, List
from unittest.mock import Mock, MagicMock, patch

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import ModelNotLoadedError, PIIDetectionError
from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
    MultiPassGlinerDetector,
    SpanKey,
    AggregatedSpan,
)
from pii_detector.infrastructure.detector.conflict_resolver import ConflictResolver


class TestSpanKey:
    """Test cases for SpanKey dataclass."""

    def test_should_be_hashable(self):
        """Test SpanKey can be used as dict key."""
        key1 = SpanKey(start=0, end=10)
        key2 = SpanKey(start=0, end=10)
        key3 = SpanKey(start=5, end=15)

        test_dict = {key1: "value1"}
        test_dict[key2] = "value2"  # Should overwrite
        test_dict[key3] = "value3"

        assert len(test_dict) == 2
        assert test_dict[key1] == "value2"

    def test_should_compare_equal_with_same_values(self):
        """Test SpanKey equality."""
        key1 = SpanKey(start=10, end=20)
        key2 = SpanKey(start=10, end=20)

        assert key1 == key2
        assert hash(key1) == hash(key2)

    def test_should_compare_not_equal_with_different_values(self):
        """Test SpanKey inequality."""
        key1 = SpanKey(start=0, end=10)
        key2 = SpanKey(start=0, end=15)

        assert key1 != key2


class TestAggregatedSpan:
    """Test cases for AggregatedSpan dataclass."""

    def test_should_detect_conflict_with_multiple_types(self):
        """Test has_conflict returns True for multiple labels."""
        span = AggregatedSpan(
            start=0,
            end=11,
            text="192.168.1.1",
            labels=[("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)]
        )

        assert span.has_conflict() is True

    def test_should_not_detect_conflict_with_single_type(self):
        """Test has_conflict returns False for single label."""
        span = AggregatedSpan(
            start=0,
            end=16,
            text="john@example.com",
            labels=[("EMAIL", 0.95)]
        )

        assert span.has_conflict() is False

    def test_should_not_detect_conflict_with_same_type_multiple_scores(self):
        """Test has_conflict returns False when same type detected multiple times."""
        span = AggregatedSpan(
            start=0,
            end=10,
            text="John Doe",
            labels=[("PERSON_NAME", 0.90), ("PERSON_NAME", 0.85)]
        )

        assert span.has_conflict() is False


class TestMultiPassDetectorInitialization:
    """Test cases for MultiPassGlinerDetector initialization."""

    @pytest.fixture
    def mock_config(self):
        """Create a mock DetectionConfig."""
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.3
        config.model_id = "test-model"
        config.device = "cpu"
        return config

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_initialize_with_default_config(
        self, mock_gliner_class, mock_load_config, mock_config
    ):
        """Test initialization with default configuration."""
        mock_load_config.return_value = {"parallel_processing": {"enabled": True, "max_workers": 10}}
        mock_gliner = Mock()
        mock_gliner_class.return_value = mock_gliner

        detector = MultiPassGlinerDetector(config=mock_config)

        assert detector.config is not None
        assert detector._pass_categories is None  # Loaded on first detect
        assert detector._conflict_resolver is None
        mock_gliner_class.assert_called_once()

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_initialize_with_custom_config(
        self, mock_gliner_class, mock_load_config
    ):
        """Test initialization with custom configuration."""
        mock_load_config.return_value = {"parallel_processing": {"enabled": False, "max_workers": 5}}
        mock_gliner = Mock()
        mock_gliner_class.return_value = mock_gliner

        # Use a mock config to avoid DetectionConfig.__post_init__
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.5
        detector = MultiPassGlinerDetector(config=config)

        assert detector.config.threshold == 0.5
        assert detector.parallel_enabled is False
        assert detector.max_workers == 5

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_use_default_parallel_config_on_failure(
        self, mock_gliner_class, mock_load_config
    ):
        """Test defaults are used when config loading fails."""
        mock_load_config.side_effect = Exception("Config not found")
        mock_gliner = Mock()
        mock_gliner_class.return_value = mock_gliner

        # Use a mock config to avoid DetectionConfig.__post_init__
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.3
        detector = MultiPassGlinerDetector(config=config)

        assert detector.parallel_enabled is True
        assert detector.max_workers == 10


class TestCategoryLoading:
    """Test cases for category loading from database."""

    @pytest.fixture
    def mock_detector(self):
        """Create detector with mocked dependencies."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector') as mock_gliner:
            mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
            mock_gliner.return_value = Mock()
            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            return detector

    @patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter')
    def test_should_load_categories_from_database(self, mock_adapter_factory, mock_detector):
        """Test successful category loading from database."""
        mock_adapter = Mock()
        mock_adapter.fetch_config.return_value = {}  # Mock global config
        mock_adapter.fetch_pii_type_configs.return_value = {
            "PERSON_NAME": {
                "enabled": True,
                "category": "IDENTITY",
                "detector_label": "person name",
            },
            "EMAIL": {
                "enabled": True,
                "category": "CONTACT",
                "detector_label": "email address",
            },
            "CREDIT_CARD_NUMBER": {
                "enabled": True,
                "category": "FINANCIAL",
                "detector_label": "credit card number",
            },
        }
        mock_adapter_factory.return_value = mock_adapter

        mock_detector._load_categories_from_database()

        assert mock_detector._pass_categories is not None
        
        # Flatten all labels from all batches
        all_labels = []
        for batch in mock_detector._pass_categories.values():
            all_labels.extend(batch.keys())
            
        assert "person name" in all_labels
        assert "email address" in all_labels
        assert "credit card number" in all_labels
        
        assert mock_detector._pii_type_to_category["PERSON_NAME"] == "IDENTITY"
        assert mock_detector._conflict_resolver is not None

    @patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter')
    def test_should_fallback_when_database_unavailable(self, mock_adapter_factory, mock_detector):
        """Test fallback categories are used when database fails."""
        mock_adapter_factory.side_effect = Exception("DB connection failed")

        mock_detector._load_categories_from_database()

        # Should use fallback
        assert mock_detector._pass_categories is not None
        assert len(mock_detector._pass_categories) > 0
        assert mock_detector._conflict_resolver is not None

    @patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter')
    def test_should_skip_disabled_types(self, mock_adapter_factory, mock_detector):
        """Test disabled PII types are not loaded."""
        mock_adapter = Mock()
        mock_adapter.fetch_config.return_value = {}  # Mock global config
        mock_adapter.fetch_pii_type_configs.return_value = {
            "EMAIL": {
                "enabled": True,
                "category": "CONTACT",
                "detector_label": "email address",
            },
            "PHONE": {
                "enabled": False,  # Disabled
                "category": "CONTACT",
                "detector_label": "phone number",
            },
        }
        mock_adapter_factory.return_value = mock_adapter

        mock_detector._load_categories_from_database()

        # Flatten all labels from all batches
        all_labels = []
        for batch in mock_detector._pass_categories.values():
            all_labels.extend(batch.keys())

        assert "email address" in all_labels
        assert "phone number" not in all_labels

    def test_should_use_fallback_categories(self, mock_detector):
        """Test fallback categories have expected structure."""
        mock_detector._use_fallback_categories()

        assert "IDENTITY" in mock_detector._pass_categories
        assert "CONTACT" in mock_detector._pass_categories
        assert "FINANCIAL" in mock_detector._pass_categories
        assert "MEDICAL" in mock_detector._pass_categories
        assert "IT" in mock_detector._pass_categories
        assert mock_detector._conflict_resolver is not None


class TestSinglePassDetection:
    """Test cases for single pass detection."""

    @pytest.fixture
    def detector_with_model(self):
        """Create detector with mocked model."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector') as mock_gliner_class:
            mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}

            mock_model = Mock()
            mock_gliner = Mock()
            mock_gliner.model = mock_model
            mock_gliner_class.return_value = mock_gliner

            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            detector._pass_categories = {
                "IDENTITY": {"person name": "PERSON_NAME"},
                "CONTACT": {"email address": "EMAIL", "phone number": "PHONE_NUMBER"},
            }
            detector._pii_type_to_category = {
                "PERSON_NAME": "IDENTITY",
                "EMAIL": "CONTACT",
                "PHONE_NUMBER": "CONTACT",
            }
            detector._conflict_resolver = Mock(spec=ConflictResolver)

            return detector

    def test_should_call_gliner_with_category_labels(self, detector_with_model):
        """Test GLiNER is called with correct labels for category."""
        detector_with_model._gliner_detector.model.predict_entities.return_value = []

        detector_with_model._run_single_pass(
            text="John Doe",
            threshold=0.3,
            detection_id="test-001",
            category="IDENTITY",
            pass_categories=detector_with_model._pass_categories
        )

        detector_with_model._gliner_detector.model.predict_entities.assert_called_once()
        call_args = detector_with_model._gliner_detector.model.predict_entities.call_args
        assert "person name" in call_args[0][1]  # labels argument

    def test_should_convert_raw_entities_to_pii_entities(self, detector_with_model):
        """Test raw GLiNER output is converted to PIIEntity."""
        detector_with_model._gliner_detector.model.predict_entities.return_value = [
            {"text": "John Doe", "label": "person name", "start": 0, "end": 8, "score": 0.92}
        ]

        result = detector_with_model._run_single_pass(
            text="John Doe is here",
            threshold=0.3,
            detection_id="test-002",
            category="IDENTITY",
            pass_categories=detector_with_model._pass_categories
        )

        assert len(result) == 1
        assert isinstance(result[0], PIIEntity)
        assert result[0].pii_type == "PERSON_NAME"
        assert result[0].text == "John Doe"
        assert result[0].score == 0.92
        assert result[0].source == DetectorSource.GLINER

    def test_should_extract_text_using_positions(self, detector_with_model):
        """Test text is extracted using start/end positions."""
        full_text = "Contact john@example.com for info"
        detector_with_model._gliner_detector.model.predict_entities.return_value = [
            {"text": full_text, "label": "email address", "start": 8, "end": 24, "score": 0.95}
        ]

        result = detector_with_model._run_single_pass(
            text=full_text,
            threshold=0.3,
            detection_id="test-003",
            category="CONTACT",
            pass_categories=detector_with_model._pass_categories
        )

        assert len(result) == 1
        assert result[0].text == "john@example.com"  # Extracted, not full text
        assert result[0].start == 8
        assert result[0].end == 24

    def test_should_handle_empty_results(self, detector_with_model):
        """Test handling of empty detection results."""
        detector_with_model._gliner_detector.model.predict_entities.return_value = []

        result = detector_with_model._run_single_pass(
            text="No PII here",
            threshold=0.3,
            detection_id="test-004",
            category="IDENTITY",
            pass_categories=detector_with_model._pass_categories
        )

        assert result == []

    def test_should_return_empty_for_unknown_category(self, detector_with_model):
        """Test unknown category returns empty list."""
        result = detector_with_model._run_single_pass(
            text="Some text",
            threshold=0.3,
            detection_id="test-005",
            category="UNKNOWN_CATEGORY",
            pass_categories=detector_with_model._pass_categories
        )

        assert result == []


class TestSpanAggregation:
    """Test cases for span aggregation."""

    @pytest.fixture
    def detector(self):
        """Create detector with minimal setup."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector'):
            mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            return MultiPassGlinerDetector(config=config)

    def test_should_group_entities_by_position(self, detector):
        """Test entities with same position are grouped."""
        entities = [
            PIIEntity(text="192.168.1.1", pii_type="IP_ADDRESS", type_label="IP_ADDRESS",
                      start=0, end=11, score=0.90),
            PIIEntity(text="192.168.1.1", pii_type="AVS_NUMBER", type_label="AVS_NUMBER",
                      start=0, end=11, score=0.85),
        ]

        spans = detector._aggregate_by_span(entities)

        assert len(spans) == 1
        assert len(spans[0].labels) == 2

    def test_should_detect_conflicts_for_same_span(self, detector):
        """Test conflicts are properly identified."""
        entities = [
            PIIEntity(text="test", pii_type="TYPE_A", type_label="TYPE_A",
                      start=0, end=4, score=0.90),
            PIIEntity(text="test", pii_type="TYPE_B", type_label="TYPE_B",
                      start=0, end=4, score=0.85),
        ]

        spans = detector._aggregate_by_span(entities)

        assert spans[0].has_conflict() is True

    def test_should_preserve_scores(self, detector):
        """Test scores are preserved during aggregation."""
        entities = [
            PIIEntity(text="data", pii_type="TYPE_A", type_label="TYPE_A",
                      start=0, end=4, score=0.95),
            PIIEntity(text="data", pii_type="TYPE_B", type_label="TYPE_B",
                      start=0, end=4, score=0.88),
        ]

        spans = detector._aggregate_by_span(entities)

        labels = spans[0].labels
        assert ("TYPE_A", 0.95) in labels
        assert ("TYPE_B", 0.88) in labels

    def test_should_handle_multiple_spans(self, detector):
        """Test multiple non-overlapping spans are kept separate."""
        entities = [
            PIIEntity(text="John", pii_type="PERSON_NAME", type_label="PERSON_NAME",
                      start=0, end=4, score=0.90),
            PIIEntity(text="john@example.com", pii_type="EMAIL", type_label="EMAIL",
                      start=10, end=26, score=0.95),
        ]

        spans = detector._aggregate_by_span(entities)

        assert len(spans) == 2


class TestConflictResolution:
    """Test cases for conflict resolution."""

    @pytest.fixture
    def detector_with_resolver(self):
        """Create detector with mocked conflict resolver."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector'):
            mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            detector._conflict_resolver = Mock(spec=ConflictResolver)
            return detector

    def test_should_pick_highest_score_for_single_label(self, detector_with_resolver):
        """Test single-label spans pick highest score."""
        spans = [
            AggregatedSpan(
                start=0, end=16, text="john@example.com",
                labels=[("EMAIL", 0.95), ("EMAIL", 0.90)]  # Same type, different scores
            )
        ]

        result = detector_with_resolver._resolve_conflicts(spans, "test-001")

        assert len(result) == 1
        assert result[0].pii_type == "EMAIL"
        assert result[0].score == 0.95

    def test_should_delegate_to_conflict_resolver_for_multi_label(
        self, detector_with_resolver
    ):
        """Test multi-label conflicts are delegated to ConflictResolver."""
        spans = [
            AggregatedSpan(
                start=0, end=11, text="192.168.1.1",
                labels=[("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)]
            )
        ]

        detector_with_resolver._conflict_resolver.resolve.return_value = ("IP_ADDRESS", 0.90)
        detector_with_resolver._conflict_resolver.build_pii_entity.return_value = PIIEntity(
            text="192.168.1.1",
            pii_type="IP_ADDRESS",
            type_label="IP_ADDRESS",
            start=0,
            end=11,
            score=0.90
        )

        result = detector_with_resolver._resolve_conflicts(spans, "test-002")

        detector_with_resolver._conflict_resolver.resolve.assert_called_once()
        assert len(result) == 1

    def test_should_return_one_entity_per_span(self, detector_with_resolver):
        """Test exactly one entity is returned per span."""
        spans = [
            AggregatedSpan(start=0, end=10, text="span1",
                          labels=[("TYPE_A", 0.9)]),
            AggregatedSpan(start=15, end=25, text="span2",
                          labels=[("TYPE_B", 0.85), ("TYPE_C", 0.80)]),
        ]

        detector_with_resolver._conflict_resolver.resolve.return_value = ("TYPE_B", 0.85)
        detector_with_resolver._conflict_resolver.build_pii_entity.return_value = PIIEntity(
            text="span2", pii_type="TYPE_B", type_label="TYPE_B",
            start=15, end=25, score=0.85
        )

        result = detector_with_resolver._resolve_conflicts(spans, "test-003")

        assert len(result) == 2


class TestOverlapRemoval:
    """Test cases for overlapping span removal."""

    @pytest.fixture
    def detector(self):
        """Create detector with minimal setup."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector'):
            mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            return MultiPassGlinerDetector(config=config)

    def test_should_remove_contained_spans(self, detector):
        """Test smaller contained spans are removed."""
        entities = [
            PIIEntity(text="john@example.com", pii_type="EMAIL", type_label="EMAIL",
                      start=0, end=16, score=0.95),
            PIIEntity(text="@example.com", pii_type="DOMAIN", type_label="DOMAIN",
                      start=4, end=16, score=0.85),  # Contained in EMAIL
        ]

        result = detector._resolve_overlapping_spans(entities)

        assert len(result) == 1
        assert result[0].pii_type == "EMAIL"

    def test_should_keep_wider_span(self, detector):
        """Test wider span wins over narrower."""
        entities = [
            PIIEntity(text="John Doe", pii_type="PERSON_NAME", type_label="PERSON_NAME",
                      start=0, end=8, score=0.90),
            PIIEntity(text="John", pii_type="FIRST_NAME", type_label="FIRST_NAME",
                      start=0, end=4, score=0.92),  # Narrower, higher score
        ]

        result = detector._resolve_overlapping_spans(entities)

        assert len(result) == 1
        assert result[0].text == "John Doe"  # Wider wins

    def test_should_handle_partial_overlaps(self, detector):
        """Test partial overlaps are handled correctly."""
        entities = [
            PIIEntity(text="ABC123", pii_type="TYPE_A", type_label="TYPE_A",
                      start=0, end=6, score=0.90),
            PIIEntity(text="123XYZ", pii_type="TYPE_B", type_label="TYPE_B",
                      start=3, end=9, score=0.85),  # Partial overlap
        ]

        result = detector._resolve_overlapping_spans(entities)

        # For partial overlaps, wider or higher score should win
        assert len(result) >= 1

    def test_should_keep_non_overlapping_spans(self, detector):
        """Test non-overlapping spans are all kept."""
        entities = [
            PIIEntity(text="John", pii_type="PERSON_NAME", type_label="PERSON_NAME",
                      start=0, end=4, score=0.90),
            PIIEntity(text="john@example.com", pii_type="EMAIL", type_label="EMAIL",
                      start=10, end=26, score=0.95),
        ]

        result = detector._resolve_overlapping_spans(entities)

        assert len(result) == 2


class TestDetectPII:
    """Test cases for the main detect_pii method."""

    @pytest.fixture
    def fully_mocked_detector(self):
        """Create detector with all dependencies mocked."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector') as mock_gliner_class:
            mock_llm_config.return_value = {"parallel_processing": {"enabled": False}}  # Sequential

            mock_model = Mock()
            mock_gliner = Mock()
            mock_gliner.model = mock_model
            mock_gliner_class.return_value = mock_gliner

            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            detector._pass_categories = {
                "IDENTITY": {"person name": "PERSON_NAME"},
                "CONTACT": {"email address": "EMAIL"},
            }
            detector._pii_type_to_category = {
                "PERSON_NAME": "IDENTITY",
                "EMAIL": "CONTACT",
            }
            detector._conflict_resolver = Mock(spec=ConflictResolver)

            return detector

    @pytest.fixture
    def detector_without_preloaded_categories(self):
        """Create detector without preloaded categories to test dynamic config."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector') as mock_gliner_class:
            mock_llm_config.return_value = {"parallel_processing": {"enabled": False}}

            mock_model = Mock()
            mock_gliner = Mock()
            mock_gliner.model = mock_model
            mock_gliner_class.return_value = mock_gliner

            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            
            # Intentionally do NOT preload categories or conflict_resolver
            # to simulate the bug scenario
            
            return detector

    def test_should_raise_when_model_not_loaded(self, fully_mocked_detector):
        """Test ModelNotLoadedError when model is None."""
        fully_mocked_detector._gliner_detector.model = None

        with pytest.raises(ModelNotLoadedError):
            fully_mocked_detector.detect_pii("test text")

    def test_should_detect_pii_across_categories(self, fully_mocked_detector):
        """Test detection runs across all categories."""
        fully_mocked_detector._gliner_detector.model.predict_entities.side_effect = [
            # IDENTITY pass
            [{"text": "John Doe", "label": "person name", "start": 0, "end": 8, "score": 0.92}],
            # CONTACT pass
            [{"text": "john@example.com", "label": "email address", "start": 12, "end": 28, "score": 0.95}],
        ]

        result = fully_mocked_detector.detect_pii("John Doe at john@example.com")

        assert len(result) == 2
        pii_types = [e.pii_type for e in result]
        assert "PERSON_NAME" in pii_types
        assert "EMAIL" in pii_types

    def test_should_handle_empty_detection(self, fully_mocked_detector):
        """Test handling when no PII is detected."""
        fully_mocked_detector._gliner_detector.model.predict_entities.return_value = []

        result = fully_mocked_detector.detect_pii("No PII in this text")

        assert result == []

    def test_should_use_provided_threshold(self, fully_mocked_detector):
        """Test custom threshold is passed to detection."""
        fully_mocked_detector._gliner_detector.model.predict_entities.return_value = []

        fully_mocked_detector.detect_pii("text", threshold=0.7)

        call_args = fully_mocked_detector._gliner_detector.model.predict_entities.call_args
        assert call_args[1]["threshold"] == 0.7

    def test_should_filter_categories(self, fully_mocked_detector):
        """Test running only specified categories."""
        fully_mocked_detector._gliner_detector.model.predict_entities.return_value = []

        fully_mocked_detector.detect_pii("text", categories=["IDENTITY"])

        # Should only call once (for IDENTITY)
        assert fully_mocked_detector._gliner_detector.model.predict_entities.call_count == 1

    def test_Should_InitializeConflictResolver_When_PiiTypeConfigsProvided(
        self, detector_without_preloaded_categories
    ):
        """
        Regression test for bug: _conflict_resolver is None when using dynamic pii_type_configs.
        
        Bug scenario:
        1. detect_pii() called with pii_type_configs parameter
        2. Categories built dynamically via _build_categories_from_config()
        3. But _conflict_resolver NOT initialized
        4. Later, _resolve_conflicts() tries to call _conflict_resolver.resolve()
        5. AttributeError: 'NoneType' object has no attribute 'resolve'
        
        This test reproduces the exact flow from the bug logs.
        """
        detector = detector_without_preloaded_categories
        
        # Simulate detection with conflicts (two types for same span)
        detector._gliner_detector.model.predict_entities.side_effect = [
            # IDENTITY pass
            [{"text": "192.168.1.1", "label": "person name", "start": 0, "end": 11, "score": 0.85}],
            # DIGITAL pass - conflict on same span
            [{"text": "192.168.1.1", "label": "ip address", "start": 0, "end": 11, "score": 0.90}],
        ]
        
        # Provide dynamic pii_type_configs (as done by gRPC service)
        pii_type_configs = {
            "PERSON_NAME": {
                "enabled": True,
                "category": "IDENTITY",
                "detector": "GLINER",
                "detector_label": "person name",
            },
            "IP_ADDRESS": {
                "enabled": True,
                "category": "DIGITAL",
                "detector": "GLINER",
                "detector_label": "ip address",
            },
        }
        
        # This should NOT crash with AttributeError
        # Before fix: 'NoneType' object has no attribute 'resolve'
        result = detector.detect_pii(
            "Test text with 192.168.1.1 data",
            threshold=0.3,
            pii_type_configs=pii_type_configs
        )
        
        # Should successfully resolve conflict and return entities
        assert len(result) >= 1
        assert detector._conflict_resolver is not None
        
        # Verify conflict resolver was properly initialized with category mapping
        assert "PERSON_NAME" in detector._pii_type_to_category
        assert "IP_ADDRESS" in detector._pii_type_to_category


class TestModelManagement:
    """Test cases for model loading and management."""

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_delegate_download_to_gliner(self, mock_gliner_class, mock_llm_config):
        """Test download_model delegates to underlying detector."""
        mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
        mock_gliner = Mock()
        mock_gliner_class.return_value = mock_gliner

        # Use a mock config to avoid DetectionConfig.__post_init__
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.3
        detector = MultiPassGlinerDetector(config=config)
        detector.download_model()

        mock_gliner.download_model.assert_called_once()

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_delegate_load_to_gliner(self, mock_gliner_class, mock_llm_config):
        """Test load_model delegates to underlying detector."""
        mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
        mock_gliner = Mock()
        mock_gliner_class.return_value = mock_gliner

        # Use a mock config to avoid DetectionConfig.__post_init__
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.3
        detector = MultiPassGlinerDetector(config=config)
        detector.load_model()

        mock_gliner.load_model.assert_called_once()

    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector')
    def test_should_have_model_id_property(self, mock_gliner_class, mock_llm_config):
        """Test model_id property returns multi-pass prefix."""
        mock_llm_config.return_value = {"parallel_processing": {"enabled": True}}
        mock_gliner = Mock()
        mock_gliner.model_id = "piiranha-v1"
        mock_gliner_class.return_value = mock_gliner

        # Use a mock config to avoid DetectionConfig.__post_init__
        config = Mock(spec=DetectionConfig)
        config.threshold = 0.3
        detector = MultiPassGlinerDetector(config=config)

        assert detector.model_id == "multi-pass-piiranha-v1"


class TestMasking:
    """Test cases for PII masking."""

    @pytest.fixture
    def detector_for_masking(self):
        """Create detector for masking tests."""
        with patch('pii_detector.application.config.detection_policy._load_llm_config') as mock_llm_config, \
             patch('pii_detector.infrastructure.detector.multi_pass_gliner_detector.GLiNERDetector') as mock_gliner_class:
            mock_llm_config.return_value = {"parallel_processing": {"enabled": False}}

            mock_model = Mock()
            mock_gliner = Mock()
            mock_gliner.model = mock_model
            mock_gliner_class.return_value = mock_gliner

            # Use a mock config to avoid DetectionConfig.__post_init__
            config = Mock(spec=DetectionConfig)
            config.threshold = 0.3
            detector = MultiPassGlinerDetector(config=config)
            detector._pass_categories = {"CONTACT": {"email address": "EMAIL"}}
            detector._pii_type_to_category = {"EMAIL": "CONTACT"}
            detector._conflict_resolver = Mock(spec=ConflictResolver)

            return detector

    def test_should_mask_pii_with_type_placeholders(self, detector_for_masking):
        """Test PII is replaced with [TYPE] placeholders."""
        text = "Contact john@example.com for info"
        detector_for_masking._gliner_detector.model.predict_entities.return_value = [
            {"text": "john@example.com", "label": "email address", "start": 8, "end": 24, "score": 0.95}
        ]

        masked_text, entities = detector_for_masking.mask_pii(text)

        assert "[EMAIL]" in masked_text
        assert "john@example.com" not in masked_text
        assert len(entities) == 1