"""
Test suite for MultiModelPIIDetector class.

This module contains comprehensive tests for the MultiModelPIIDetector class,
covering multi-model orchestration, parallel detection, deduplication, and overlap resolution.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
from typing import List

from pii_detector.application.orchestration.multi_detector import (
    MultiModelPIIDetector,
    get_multi_model_ids_from_config,
    should_use_multi_detector,
    _get_provenance_logging
)
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.application.config.detection_policy import DetectionConfig


@pytest.fixture
def mock_logger():
    """Fixture to mock logger for MultiModelPIIDetector initialization."""
    with patch('pii_detector.application.orchestration.multi_detector.logging.getLogger') as mock:
        yield mock


class TestHelperFunctions:
    """Test cases for helper functions."""
    
    @patch('pii_detector.config.get_config')
    def test_should_get_provenance_logging_when_enabled(self, mock_get_config):
        """Test getting provenance logging when enabled in config."""
        mock_config = Mock()
        mock_config.detection.multi_detector_log_provenance = True
        mock_get_config.return_value = mock_config
        
        result = _get_provenance_logging()
        
        assert result is True
    
    @patch('pii_detector.config.get_config')
    def test_should_get_provenance_logging_when_disabled(self, mock_get_config):
        """Test getting provenance logging when disabled in config."""
        mock_config = Mock()
        mock_config.detection.multi_detector_log_provenance = False
        mock_get_config.return_value = mock_config
        
        result = _get_provenance_logging()
        
        assert result is False
    
    @patch('pii_detector.config.get_config')
    def test_should_return_false_when_config_error(self, mock_get_config):
        """Test fallback to False when config loading fails."""
        mock_get_config.side_effect = ValueError("Config error")
        
        result = _get_provenance_logging()
        
        assert result is False
    
    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.application.config.detection_policy.get_enabled_models')
    def test_should_get_multi_model_ids_from_config(self, mock_get_enabled, mock_load_config):
        """Test getting model IDs from configuration."""
        mock_load_config.return_value = {}
        mock_get_enabled.return_value = [
            {"model_id": "model1", "priority": 1},
            {"model_id": "model2", "priority": 2}
        ]
        
        result = get_multi_model_ids_from_config()
        
        assert result == ["model1", "model2"]
    
    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    def test_should_return_default_when_config_fails(self, mock_load_config):
        """Test fallback to default model when config loading fails."""
        mock_load_config.side_effect = Exception("Config error")
        
        result = get_multi_model_ids_from_config()
        
        assert result == ["iiiorg/piiranha-v1-detect-personal-information"]
    
    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.application.config.detection_policy.get_enabled_models')
    def test_should_use_multi_detector_when_enabled(self, mock_get_enabled, mock_load_config):
        """Test multi-detector check when enabled with multiple models."""
        mock_config = {"detection": {"multi_detector_enabled": True}}
        mock_load_config.return_value = mock_config
        mock_get_enabled.return_value = [
            {"model_id": "model1"},
            {"model_id": "model2"}
        ]
        
        result = should_use_multi_detector()
        
        assert result is True
    
    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.application.config.detection_policy.get_enabled_models')
    def test_should_not_use_multi_detector_when_disabled(self, mock_get_enabled, mock_load_config):
        """Test multi-detector check when disabled."""
        mock_config = {"detection": {"multi_detector_enabled": False}}
        mock_load_config.return_value = mock_config
        
        result = should_use_multi_detector()
        
        assert result is False
    
    @patch('pii_detector.application.config.detection_policy._load_llm_config')
    @patch('pii_detector.application.config.detection_policy.get_enabled_models')
    def test_should_not_use_multi_detector_with_single_model(self, mock_get_enabled, mock_load_config):
        """Test multi-detector check with only one model."""
        mock_config = {"detection": {"multi_detector_enabled": True}}
        mock_load_config.return_value = mock_config
        mock_get_enabled.return_value = [{"model_id": "model1"}]
        
        result = should_use_multi_detector()
        
        assert result is False


class TestMultiModelPIIDetectorInitialization:
    """Test cases for MultiModelPIIDetector initialization."""
    
    def test_should_initialize_with_model_ids(self, mock_logger):
        """Test initialization with model IDs."""
        model_ids = ["model1", "model2"]
        mock_factory = Mock()
        mock_factory.create.side_effect = [Mock(), Mock()]
        
        detector = MultiModelPIIDetector(model_ids=model_ids, factory=mock_factory)
        
        assert detector.model_ids == model_ids
        assert len(detector.detectors) == 2
        assert detector.device is None
    
    def test_should_initialize_with_device(self, mock_logger):
        """Test initialization with specific device."""
        model_ids = ["model1"]
        device = "cuda"
        mock_factory = Mock()
        mock_factory.create.return_value = Mock()
        
        detector = MultiModelPIIDetector(model_ids=model_ids, device=device, factory=mock_factory)
        
        assert detector.device == device
    
    def test_should_create_gliner_detector_for_gliner_model(self, mock_logger):
        """Test creating GLiNERDetector for GLiNER model via factory."""
        model_ids = ["gliner-pii"]
        mock_factory = Mock()
        mock_detector = Mock()
        mock_factory.create.return_value = mock_detector
        
        detector = MultiModelPIIDetector(model_ids=model_ids, factory=mock_factory)
        
        mock_factory.create.assert_called_once()
        assert len(detector.detectors) == 1
    
    def test_should_create_pii_detector_for_non_gliner_model(self, mock_logger):
        """Test creating PIIDetector for non-GLiNER model via factory."""
        model_ids = ["piiranha"]
        mock_factory = Mock()
        mock_detector = Mock()
        mock_factory.create.return_value = mock_detector
        
        detector = MultiModelPIIDetector(model_ids=model_ids, factory=mock_factory)
        
        mock_factory.create.assert_called_once()
        assert len(detector.detectors) == 1
    
    def test_should_have_model_id_property(self, mock_logger):
        """Test model_id property returns first model."""
        mock_detector1 = Mock()
        mock_detector1.model_id = "model1"
        mock_detector2 = Mock()
        mock_detector2.model_id = "model2"
        
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_detector1, mock_detector2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        
        assert detector.model_id == "model1"


class TestLifecycleOperations:
    """Test cases for lifecycle operations."""
    
    def test_should_download_all_models(self, mock_logger):
        """Test downloading all models."""
        mock_det1 = Mock()
        mock_det2 = Mock()
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        detector.download_model()
        
        mock_det1.download_model.assert_called_once()
        mock_det2.download_model.assert_called_once()
    
    def test_should_continue_on_download_failure(self, mock_logger):
        """Test continuing when download fails for one model."""
        mock_det1 = Mock()
        mock_det1.download_model.side_effect = Exception("Download error")
        mock_det2 = Mock()
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        detector.download_model()
        
        # Should not raise, should continue to model2
        mock_det2.download_model.assert_called_once()
    
    def test_should_load_all_models(self, mock_logger):
        """Test loading all models."""
        mock_det1 = Mock()
        mock_det2 = Mock()
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        detector.load_model()
        
        mock_det1.load_model.assert_called_once()
        mock_det2.load_model.assert_called_once()
    
    def test_should_continue_on_load_failure(self, mock_logger):
        """Test continuing when load fails for one model."""
        mock_det1 = Mock()
        mock_det1.load_model.side_effect = Exception("Load error")
        mock_det2 = Mock()
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        detector.load_model()
        
        # Should not raise, should continue to model2
        mock_det2.load_model.assert_called_once()


class TestPIIDetection:
    """Test cases for PII detection."""
    
    def test_should_detect_pii_from_multiple_models(self, mock_logger):
        """Test detecting PII from multiple models."""
        entity1 = PIIEntity(text="test1", pii_type="EMAIL", type_label="EMAIL", start=0, end=5, score=0.9)
        entity2 = PIIEntity(text="test2", pii_type="PHONE", type_label="PHONE", start=10, end=15, score=0.8)
        
        mock_det1 = Mock()
        mock_det1.model_id = "model1"
        mock_det1.detect_pii.return_value = [entity1]
        
        mock_det2 = Mock()
        mock_det2.model_id = "model2"
        mock_det2.detect_pii.return_value = [entity2]
        
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        result = detector.detect_pii("test text")
        
        assert len(result) == 2
        assert entity1 in result
        assert entity2 in result
    
    def test_should_deduplicate_identical_entities(self, mock_logger):
        """Test deduplicating identical entities from different models."""
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        entity2 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.8)
        
        mock_det1 = Mock()
        mock_det1.model_id = "model1"
        mock_det1.detect_pii.return_value = [entity1]
        
        mock_det2 = Mock()
        mock_det2.model_id = "model2"
        mock_det2.detect_pii.return_value = [entity2]
        
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        result = detector.detect_pii("test")
        
        # Should keep only one with higher score
        assert len(result) == 1
        assert result[0].score == 0.9
    
    def test_should_handle_detection_failure(self, mock_logger):
        """Test handling detection failure gracefully."""
        entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        mock_det1 = Mock()
        mock_det1.model_id = "model1"
        mock_det1.detect_pii.side_effect = Exception("Detection error")
        
        mock_det2 = Mock()
        mock_det2.model_id = "model2"
        mock_det2.detect_pii.return_value = [entity]
        
        mock_factory = Mock()
        mock_factory.create.side_effect = [mock_det1, mock_det2]
        
        detector = MultiModelPIIDetector(model_ids=["model1", "model2"], factory=mock_factory)
        result = detector.detect_pii("test")
        
        # Should return results from successful detector
        assert len(result) == 1
        assert result[0] == entity
    
    @patch('pii_detector.application.orchestration.multi_detector.PROVENANCE_LOG_PROVENANCE', True)
    def test_should_log_provenance_when_enabled(self, mock_logger):
        """Test provenance logging when enabled."""
        entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        mock_det = Mock()
        mock_det.model_id = "model1"
        mock_det.detect_pii.return_value = [entity]
        
        mock_factory = Mock()
        mock_factory.create.return_value = mock_det
        
        detector = MultiModelPIIDetector(model_ids=["model1"], factory=mock_factory)
        with patch.object(detector.logger, 'info') as mock_log:
            result = detector.detect_pii("test")
        
        # Should log provenance
        assert mock_log.called


class TestPIIMasking:
    """Test cases for PII masking."""
    
    def test_should_mask_detected_entities(self, mock_logger):
        """Test masking detected PII entities."""
        entity = PIIEntity(text="test@example.com", pii_type="EMAIL", type_label="EMAIL", start=8, end=24, score=0.9)
        
        mock_det = Mock()
        mock_det.model_id = "model1"
        mock_det.detect_pii.return_value = [entity]
        
        mock_factory = Mock()
        mock_factory.create.return_value = mock_det
        
        detector = MultiModelPIIDetector(model_ids=["model1"], factory=mock_factory)
        masked_text, entities = detector.mask_pii("Contact test@example.com")
        
        assert masked_text == "Contact [EMAIL]"
        assert len(entities) == 1


class TestOverlapResolution:
    """Test cases for overlap resolution.
    
    Note: Overlap resolution logic has been extracted to DetectionMerger.
    These tests verify that MultiModelPIIDetector correctly delegates to DetectionMerger.
    Detailed overlap resolution tests are now in test_detection_merger.py.
    """
    
    def test_should_delegate_overlap_resolution_to_merger(self, mock_logger):
        """Test that MultiModelPIIDetector delegates overlap resolution to DetectionMerger."""
        # Short entity
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        # Longer entity containing the short one
        entity2 = PIIEntity(text="test@example.com", pii_type="EMAIL", type_label="EMAIL", start=0, end=16, score=0.8)
        
        mock_det = Mock()
        mock_det.model_id = "model1"
        mock_det.detect_pii.return_value = [entity1, entity2]
        
        mock_factory = Mock()
        mock_factory.create.return_value = mock_det
        
        detector = MultiModelPIIDetector(model_ids=["model1"], factory=mock_factory)
        result = detector.detect_pii("test")
        
        # Should keep longer entity through merger
        assert len(result) == 1
        assert result[0].text == "test@example.com"
