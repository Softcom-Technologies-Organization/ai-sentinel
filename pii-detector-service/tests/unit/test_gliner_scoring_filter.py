"""
Unit tests for GLiNER scoring filter functionality.

This module tests the _apply_entity_scoring_filter method
to ensure per-entity-type threshold filtering works correctly.
"""

import pytest
from unittest.mock import Mock, patch

from pii_detector.service.detector.gliner_detector import GLiNERDetector
from pii_detector.service.detector.models import PIIEntity, DetectionConfig


class TestGLiNERScoringFilter:
    """Test cases for GLiNER scoring filter functionality."""

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_filter_entities_below_type_specific_threshold(self, mock_manager_class):
        """Test that entities below their type-specific threshold are filtered out."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Mock scoring overrides
        detector.scoring_overrides = {
            'TELEPHONENUM': 0.95,
            'EMAIL': 0.80,
            'GIVENNAME': 0.75
        }
        
        # Create entities with various scores
        entities = [
            # Should be filtered (score < threshold)
            PIIEntity(text="2010", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=0, end=4, score=0.85),
            PIIEntity(text="john@test", pii_type="EMAIL", type_label="EMAIL", 
                     start=10, end=19, score=0.75),
            PIIEntity(text="Jean", pii_type="GIVENNAME", type_label="GIVENNAME", 
                     start=25, end=29, score=0.70),
            
            # Should pass (score >= threshold)
            PIIEntity(text="+41 79 123 45 67", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=40, end=57, score=0.96),
            PIIEntity(text="valid@email.com", pii_type="EMAIL", type_label="EMAIL", 
                     start=60, end=75, score=0.92),
            PIIEntity(text="Marie", pii_type="GIVENNAME", type_label="GIVENNAME", 
                     start=80, end=85, score=0.88),
        ]
        
        # Act
        filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 3, \
            f"Expected 3 entities after filtering, got {len(filtered_entities)}"
        
        # Verify correct entities passed
        filtered_texts = {e.text for e in filtered_entities}
        assert "+41 79 123 45 67" in filtered_texts
        assert "valid@email.com" in filtered_texts
        assert "Marie" in filtered_texts
        
        # Verify filtered entities are gone
        assert "2010" not in filtered_texts
        assert "john@test" not in filtered_texts
        assert "Jean" not in filtered_texts

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_keep_all_entities_when_no_scoring_overrides(self, mock_manager_class):
        """Test that all entities are kept when no scoring overrides are configured."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # No scoring overrides
        detector.scoring_overrides = {}
        
        entities = [
            PIIEntity(text="test1", pii_type="EMAIL", type_label="EMAIL", 
                     start=0, end=5, score=0.30),
            PIIEntity(text="test2", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=10, end=15, score=0.40),
        ]
        
        # Act
        filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 2, \
            "All entities should be kept when no scoring overrides are configured"

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_keep_entities_without_type_specific_threshold(self, mock_manager_class):
        """Test that entities without a type-specific threshold are kept."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Only TELEPHONENUM has a scoring override
        detector.scoring_overrides = {
            'TELEPHONENUM': 0.95
        }
        
        entities = [
            # Should be filtered (has threshold and score below)
            PIIEntity(text="2010", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=0, end=4, score=0.85),
            
            # Should pass (no threshold configured for EMAIL)
            PIIEntity(text="test@email.com", pii_type="EMAIL", type_label="EMAIL", 
                     start=10, end=24, score=0.40),
            
            # Should pass (no threshold configured for GIVENNAME)
            PIIEntity(text="Jean", pii_type="GIVENNAME", type_label="GIVENNAME", 
                     start=30, end=34, score=0.50),
        ]
        
        # Act
        filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 2, \
            f"Expected 2 entities (EMAIL and GIVENNAME), got {len(filtered_entities)}"
        
        filtered_types = {e.pii_type for e in filtered_entities}
        assert "EMAIL" in filtered_types
        assert "GIVENNAME" in filtered_types
        assert "TELEPHONENUM" not in filtered_types

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_filter_multiple_entities_of_same_type(self, mock_manager_class):
        """Test filtering multiple entities of the same type."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        detector.scoring_overrides = {
            'TELEPHONENUM': 0.95
        }
        
        entities = [
            # False positives (should be filtered)
            PIIEntity(text="2010", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=0, end=4, score=0.85),
            PIIEntity(text="8 1700", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=10, end=16, score=0.85),
            PIIEntity(text="692.20", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=20, end=26, score=0.85),
            
            # Valid phone numbers (should pass)
            PIIEntity(text="+41 79 123 45 67", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=30, end=47, score=0.96),
            PIIEntity(text="022 123 45 67", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=50, end=63, score=0.97),
        ]
        
        # Act
        filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 2, \
            f"Expected 2 valid phone numbers, got {len(filtered_entities)}"
        
        for entity in filtered_entities:
            assert entity.score >= 0.95, \
                f"Entity '{entity.text}' has score {entity.score} < 0.95"

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_handle_edge_case_score_equals_threshold(self, mock_manager_class):
        """Test that entities with score exactly equal to threshold are kept."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        detector.scoring_overrides = {
            'TELEPHONENUM': 0.95
        }
        
        entities = [
            # Score exactly equals threshold (should pass)
            PIIEntity(text="+41 79 123 45 67", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=0, end=17, score=0.95),
            # Score below threshold (should be filtered)
            PIIEntity(text="2010", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=20, end=24, score=0.9499),
        ]
        
        # Act
        filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 1
        assert filtered_entities[0].text == "+41 79 123 45 67"
        assert filtered_entities[0].score == 0.95

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_load_scoring_overrides_from_config(self, mock_manager_class):
        """Test that scoring overrides are loaded from configuration file."""
        # Arrange & Act
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Assert
        assert isinstance(detector.scoring_overrides, dict)
        
        # Verify key thresholds from gliner-pii.toml
        assert 'TELEPHONENUM' in detector.scoring_overrides
        assert detector.scoring_overrides['TELEPHONENUM'] == 0.95
        
        assert 'EMAIL' in detector.scoring_overrides
        assert detector.scoring_overrides['EMAIL'] == 0.80
        
        assert 'GIVENNAME' in detector.scoring_overrides
        assert detector.scoring_overrides['GIVENNAME'] == 0.75

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_log_filtered_entities(self, mock_manager_class, caplog):
        """Test that filtered entities are logged for debugging."""
        # Arrange
        config = DetectionConfig(model_id="gliner-pii", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        detector.scoring_overrides = {
            'TELEPHONENUM': 0.95
        }
        
        entities = [
            PIIEntity(text="2010", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=0, end=4, score=0.85),
            PIIEntity(text="+41 79 123 45 67", pii_type="TELEPHONENUM", type_label="TELEPHONENUM", 
                     start=10, end=27, score=0.96),
        ]
        
        # Act
        with caplog.at_level('INFO'):
            filtered_entities = detector._apply_entity_scoring_filter(entities)
        
        # Assert
        assert len(filtered_entities) == 1
        
        # Verify logging
        log_messages = [record.message for record in caplog.records]
        assert any("Post-filtered 1 entities" in msg for msg in log_messages)

    @patch('pii_detector.service.detector.gliner_detector.GLiNERModelManager')
    def test_should_load_scoring_overrides_for_gliner_model_id(self, mock_manager_class):
        """Test that scoring overrides are loaded correctly even with full HuggingFace model ID."""
        # Arrange & Act - Using the actual HuggingFace model ID from config
        config = DetectionConfig(model_id="knowledgator/gliner-pii-large-v1.0", device="cpu", threshold=0.5)
        detector = GLiNERDetector(config=config)
        
        # Assert
        # The bug: scoring_overrides might be empty if the config file name doesn't match model_id
        assert isinstance(detector.scoring_overrides, dict)
        
        # This test will FAIL if the bug exists (scoring_overrides will be empty)
        # because _load_scoring_overrides() hardcodes "gliner-pii" but model_id is "knowledgator/gliner-pii-large-v1.0"
        assert len(detector.scoring_overrides) > 0, \
            "Bug detected: scoring_overrides are empty because config file lookup uses hardcoded 'gliner-pii' instead of deriving from model_id"
        
        # Verify key thresholds are loaded
        assert 'TELEPHONENUM' in detector.scoring_overrides
        assert detector.scoring_overrides['TELEPHONENUM'] == 0.95
