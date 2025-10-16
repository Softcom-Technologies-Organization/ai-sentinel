"""
Test suite for EntityProcessor.

This module contains comprehensive tests for the EntityProcessor class,
which handles entity processing and formatting operations.
"""

import pytest
from unittest.mock import Mock, patch

from pii_detector.service.detector.entity_processor import EntityProcessor
from pii_detector.service.detector.models.pii_entity import PIIEntity
from pii_detector.service.detector.models.pii_type import PIIType


class TestEntityProcessorInit:
    """Test cases for EntityProcessor initialization."""
    
    def test_init_creates_label_mapping(self):
        """Test that __init__ creates proper label mapping from PIIType enum."""
        processor = EntityProcessor()
        
        # Verify label_mapping is created
        assert hasattr(processor, 'label_mapping')
        assert isinstance(processor.label_mapping, dict)
        
        # Verify all PIIType values are in the mapping
        for pii_type in PIIType:
            assert pii_type.name in processor.label_mapping
            assert processor.label_mapping[pii_type.name] == pii_type.value
    
    def test_init_creates_logger(self):
        """Test that __init__ creates a logger instance."""
        processor = EntityProcessor()
        
        # Verify logger is created
        assert hasattr(processor, 'logger')
        assert processor.logger is not None
        
        # Verify logger name includes class name
        assert 'EntityProcessor' in processor.logger.name


class TestProcessEntities:
    """Test cases for process_entities method."""
    
    @pytest.fixture
    def processor(self):
        """Create an EntityProcessor instance for testing."""
        return EntityProcessor()
    
    @pytest.fixture
    def sample_raw_entities(self):
        """Create sample raw entities from model output."""
        return [
            {
                'word': 'john.doe@example.com',
                'entity_group': 'EMAIL',
                'start': 0,
                'end': 20,
                'score': 0.95
            },
            {
                'word': 'John Doe',
                'entity_group': 'GIVENNAME',
                'start': 25,
                'end': 33,
                'score': 0.88
            },
            {
                'word': '123-45-6789',
                'entity_group': 'SSN',
                'start': 40,
                'end': 51,
                'score': 0.45  # Below common threshold
            }
        ]
    
    def test_process_entities_with_default_threshold(self, processor, sample_raw_entities):
        """Test process_entities filters entities by threshold."""
        threshold = 0.5
        
        result = processor.process_entities(sample_raw_entities, threshold)
        
        # Only entities with score >= 0.5 should be returned
        assert len(result) == 2
        assert all(isinstance(entity, PIIEntity) for entity in result)
        assert all(entity.score >= threshold for entity in result)
    
    def test_process_entities_with_high_threshold(self, processor, sample_raw_entities):
        """Test process_entities with high threshold filters more entities."""
        threshold = 0.9
        
        result = processor.process_entities(sample_raw_entities, threshold)
        
        # Only one entity has score >= 0.9
        assert len(result) == 1
        assert result[0].score >= threshold
        assert result[0].text == 'john.doe@example.com'
    
    def test_process_entities_with_low_threshold(self, processor, sample_raw_entities):
        """Test process_entities with low threshold includes all entities."""
        threshold = 0.4
        
        result = processor.process_entities(sample_raw_entities, threshold)
        
        # All entities should be included
        assert len(result) == 3
        assert all(entity.score >= threshold for entity in result)
    
    def test_process_entities_with_empty_input(self, processor):
        """Test process_entities handles empty input."""
        result = processor.process_entities([], 0.5)
        
        assert result == []
        assert isinstance(result, list)
    
    def test_process_entities_preserves_entity_data(self, processor, sample_raw_entities):
        """Test that process_entities preserves all entity data correctly."""
        threshold = 0.5
        
        result = processor.process_entities(sample_raw_entities, threshold)
        
        # Check first entity
        assert result[0].text == 'john.doe@example.com'
        assert result[0].pii_type == 'EMAIL'
        assert result[0].start == 0
        assert result[0].end == 20
        assert result[0].score == 0.95
        
        # Check second entity
        assert result[1].text == 'John Doe'
        assert result[1].pii_type == 'GIVENNAME'
        assert result[1].start == 25
        assert result[1].end == 33
        assert result[1].score == 0.88
    
    def test_process_entities_handles_whitespace_in_text(self, processor):
        """Test that process_entities strips whitespace from entity text."""
        raw_entities = [
            {
                'word': '  test@example.com  ',
                'entity_group': 'EMAIL',
                'start': 0,
                'end': 20,
                'score': 0.9
            }
        ]
        
        result = processor.process_entities(raw_entities, 0.5)
        
        # Whitespace should be stripped
        assert result[0].text == 'test@example.com'


class TestDetectEmailsWithRegex:
    """Test cases for detect_emails_with_regex method."""
    
    @pytest.fixture
    def processor(self):
        """Create an EntityProcessor instance for testing."""
        return EntityProcessor()
    
    def test_detect_emails_with_regex_returns_empty_list(self, processor):
        """Test that detect_emails_with_regex always returns empty list (business rule)."""
        result = processor.detect_emails_with_regex()
        
        # Should always return empty list (no-op by policy)
        assert result == []
        assert isinstance(result, list)


class TestCreatePIIEntity:
    """Test cases for _create_pii_entity method."""
    
    @pytest.fixture
    def processor(self):
        """Create an EntityProcessor instance for testing."""
        return EntityProcessor()
    
    def test_create_pii_entity_with_known_type(self, processor):
        """Test _create_pii_entity with known PIIType."""
        raw_entity = {
            'word': 'john@example.com',
            'entity_group': 'EMAIL',
            'start': 0,
            'end': 16,
            'score': 0.95
        }
        
        result = processor._create_pii_entity(raw_entity)
        
        assert isinstance(result, PIIEntity)
        assert result.text == 'john@example.com'
        assert result.pii_type == 'EMAIL'
        assert result.type_label == PIIType.EMAIL.value  # Should use label mapping
        assert result.start == 0
        assert result.end == 16
        assert result.score == 0.95
    
    def test_create_pii_entity_with_unknown_type(self, processor):
        """Test _create_pii_entity with unknown entity type."""
        raw_entity = {
            'word': 'some_custom_entity',
            'entity_group': 'UNKNOWN_TYPE',
            'start': 0,
            'end': 18,
            'score': 0.8
        }
        
        result = processor._create_pii_entity(raw_entity)
        
        assert isinstance(result, PIIEntity)
        assert result.text == 'some_custom_entity'
        assert result.pii_type == 'UNKNOWN_TYPE'
        # Should use entity_group as fallback when not in label_mapping
        assert result.type_label == 'UNKNOWN_TYPE'
        assert result.start == 0
        assert result.end == 18
        assert result.score == 0.8
    
    def test_create_pii_entity_strips_whitespace(self, processor):
        """Test that _create_pii_entity strips whitespace from text."""
        raw_entity = {
            'word': '  John Doe  ',
            'entity_group': 'GIVENNAME',
            'start': 0,
            'end': 12,
            'score': 0.9
        }
        
        result = processor._create_pii_entity(raw_entity)
        
        # Text should be stripped
        assert result.text == 'John Doe'
    
    def test_create_pii_entity_with_all_pii_types(self, processor):
        """Test _create_pii_entity with all known PIIType values."""
        for pii_type in PIIType:
            raw_entity = {
                'word': 'test_value',
                'entity_group': pii_type.name,
                'start': 0,
                'end': 10,
                'score': 0.9
            }
            
            result = processor._create_pii_entity(raw_entity)
            
            assert result.pii_type == pii_type.name
            assert result.type_label == pii_type.value


class TestLabelMapping:
    """Test cases for label_mapping functionality."""
    
    def test_label_mapping_contains_all_pii_types(self):
        """Test that label_mapping contains all PIIType enum values."""
        processor = EntityProcessor()
        
        # Get all PIIType names
        expected_types = {pii_type.name for pii_type in PIIType}
        actual_types = set(processor.label_mapping.keys())
        
        assert expected_types == actual_types
    
    def test_label_mapping_values_are_human_readable(self):
        """Test that label_mapping values are human-readable labels."""
        processor = EntityProcessor()
        
        # Verify some common mappings
        assert processor.label_mapping['EMAIL'] == PIIType.EMAIL.value
        assert processor.label_mapping['GIVENNAME'] == PIIType.GIVENNAME.value
        assert processor.label_mapping['TELEPHONENUM'] == PIIType.TELEPHONENUM.value


class TestIntegration:
    """Integration tests for EntityProcessor."""
    
    def test_end_to_end_entity_processing(self):
        """Test complete entity processing workflow."""
        processor = EntityProcessor()
        
        # Simulate raw entities from model
        raw_entities = [
            {
                'word': '  alice@example.com  ',
                'entity_group': 'EMAIL',
                'start': 0,
                'end': 20,
                'score': 0.95
            },
            {
                'word': 'Alice Smith',
                'entity_group': 'GIVENNAME',
                'start': 25,
                'end': 36,
                'score': 0.88
            },
            {
                'word': '+1-555-1234',
                'entity_group': 'TELEPHONENUM',
                'start': 45,
                'end': 56,
                'score': 0.92
            },
            {
                'word': 'low_score',
                'entity_group': 'GIVENNAME',
                'start': 60,
                'end': 69,
                'score': 0.3  # Below threshold
            }
        ]
        
        threshold = 0.5
        result = processor.process_entities(raw_entities, threshold)
        
        # Should have 3 entities (one filtered by threshold)
        assert len(result) == 3
        
        # Verify each entity
        assert result[0].text == 'alice@example.com'  # Whitespace stripped
        assert result[0].type_label == PIIType.EMAIL.value
        assert result[0].score == 0.95
        
        assert result[1].text == 'Alice Smith'
        assert result[1].type_label == PIIType.GIVENNAME.value
        assert result[1].score == 0.88
        
        assert result[2].text == '+1-555-1234'
        assert result[2].type_label == PIIType.TELEPHONENUM.value
        assert result[2].score == 0.92
