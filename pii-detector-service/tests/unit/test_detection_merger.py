"""
Test suite for DetectionMerger class.

This module contains comprehensive tests for the DetectionMerger class,
covering deduplication, overlap resolution, and provenance logging.
"""

from unittest.mock import Mock, patch

from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.service.detection_merger import DetectionMerger


class TestDetectionMergerInitialization:
    """Test cases for DetectionMerger initialization."""
    
    def test_should_initialize_with_default_settings(self):
        """Test initialization with default settings."""
        merger = DetectionMerger()
        
        assert merger.log_provenance is False
        assert merger.logger is not None
    
    def test_should_initialize_with_provenance_logging_enabled(self):
        """Test initialization with provenance logging enabled."""
        merger = DetectionMerger(log_provenance=True)
        
        assert merger.log_provenance is True


class TestMergeEntities:
    """Test cases for entity merging."""
    
    def test_should_merge_entities_from_multiple_detectors(self):
        """Test merging entities from multiple detectors."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test1", pii_type="EMAIL", type_label="EMAIL", start=0, end=5, score=0.9)
        entity2 = PIIEntity(text="test2", pii_type="PHONE", type_label="PHONE", start=10, end=15, score=0.8)
        
        detector1 = Mock()
        detector1.model_id = "model1"
        detector2 = Mock()
        detector2.model_id = "model2"
        
        results = [(detector1, [entity1]), (detector2, [entity2])]
        
        result = merger.merge(results)
        
        assert len(result) == 2
        assert entity1 in result
        assert entity2 in result
    
    def test_should_deduplicate_identical_entities(self):
        """Test deduplicating identical entities keeping highest score."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        entity2 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.8)
        
        detector1 = Mock()
        detector1.model_id = "model1"
        detector2 = Mock()
        detector2.model_id = "model2"
        
        results = [(detector1, [entity1]), (detector2, [entity2])]
        
        result = merger.merge(results)
        
        assert len(result) == 1
        assert result[0].score == 0.9
    
    def test_should_resolve_overlapping_entities(self):
        """Test resolving overlapping entities preferring longer spans."""
        merger = DetectionMerger()
        
        # Short entity
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        # Longer entity containing the short one
        entity2 = PIIEntity(text="test@example.com", pii_type="EMAIL", type_label="EMAIL", start=0, end=16, score=0.8)
        
        detector = Mock()
        detector.model_id = "model1"
        
        results = [(detector, [entity1, entity2])]
        
        result = merger.merge(results)
        
        # Should keep longer entity
        assert len(result) == 1
        assert result[0].text == "test@example.com"
    
    def test_should_handle_empty_results(self):
        """Test handling empty results."""
        merger = DetectionMerger()
        
        results = []
        
        result = merger.merge(results)
        
        assert result == []


class TestDeduplication:
    """Test cases for deduplication logic."""
    
    def test_should_create_entity_key(self):
        """Test entity key creation."""
        merger = DetectionMerger()
        
        entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        key = merger._create_entity_key(entity)
        
        assert key == (0, 4, "EMAIL", "test")
    
    def test_should_keep_first_entity_when_no_duplicate(self):
        """Test keeping first entity when no duplicate exists."""
        merger = DetectionMerger()
        
        entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        detector = Mock()
        detector.model_id = "model1"
        
        results = [(detector, [entity])]
        
        merged, source_by_key = merger._merge_and_deduplicate_entities(results)
        
        assert len(merged) == 1
        assert merged[0] == entity
    
    def test_should_replace_with_higher_score(self):
        """Test replacing entity with higher confidence score."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.8)
        entity2 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        detector1 = Mock()
        detector1.model_id = "model1"
        detector2 = Mock()
        detector2.model_id = "model2"
        
        results = [(detector1, [entity1]), (detector2, [entity2])]
        
        merged, source_by_key = merger._merge_and_deduplicate_entities(results)
        
        assert len(merged) == 1
        assert merged[0].score == 0.9


class TestOverlapResolution:
    """Test cases for overlap resolution."""
    
    def test_should_keep_longer_span_when_overlapping(self):
        """Test keeping longer span when entities overlap."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        entity2 = PIIEntity(text="test@example.com", pii_type="EMAIL", type_label="EMAIL", start=0, end=16, score=0.8)
        
        result = merger._resolve_overlapping_entities([entity1, entity2])
        
        assert len(result) == 1
        assert result[0].text == "test@example.com"
    
    def test_should_keep_non_overlapping_entities(self):
        """Test keeping non-overlapping entities."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test1", pii_type="EMAIL", type_label="EMAIL", start=0, end=5, score=0.9)
        entity2 = PIIEntity(text="test2", pii_type="EMAIL", type_label="EMAIL", start=10, end=15, score=0.8)
        
        result = merger._resolve_overlapping_entities([entity1, entity2])
        
        assert len(result) == 2
    
    def test_should_resolve_partial_overlap(self):
        """Test resolving partial overlaps."""
        merger = DetectionMerger()
        
        entity1 = PIIEntity(text="test1", pii_type="EMAIL", type_label="EMAIL", start=0, end=8, score=0.9)
        entity2 = PIIEntity(text="test2", pii_type="EMAIL", type_label="EMAIL", start=5, end=13, score=0.8)
        
        result = merger._resolve_overlapping_entities([entity1, entity2])
        
        # Should prefer the first one (started earlier)
        assert len(result) == 1
        assert result[0].start == 0
    
    def test_should_handle_empty_list(self):
        """Test handling empty entity list."""
        merger = DetectionMerger()
        
        result = merger._resolve_overlapping_entities([])
        
        assert result == []
    
    def test_should_handle_single_entity(self):
        """Test handling single entity."""
        merger = DetectionMerger()
        
        entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        
        result = merger._resolve_overlapping_entities([entity])
        
        assert len(result) == 1
        assert result[0] == entity
    
    def test_should_resolve_by_type_independently(self):
        """Test resolving overlaps independently per type."""
        merger = DetectionMerger()
        
        email1 = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        email2 = PIIEntity(text="test@example.com", pii_type="EMAIL", type_label="EMAIL", start=0, end=16, score=0.8)
        phone = PIIEntity(text="555-1234", pii_type="PHONE", type_label="PHONE", start=0, end=8, score=0.7)
        
        result = merger._resolve_overlapping_entities([email1, email2, phone])
        
        # Should have one email (longer) and one phone
        assert len(result) == 2
        types = [e.pii_type for e in result]
        assert "EMAIL" in types
        assert "PHONE" in types
    
    def test_should_check_overlap_types(self):
        """Test overlap type detection."""
        merger = DetectionMerger()
        
        # Test no overlap
        e1 = PIIEntity(text="a", pii_type="EMAIL", type_label="EMAIL", start=0, end=5, score=0.9)
        e2 = PIIEntity(text="b", pii_type="EMAIL", type_label="EMAIL", start=10, end=15, score=0.8)
        assert merger._check_overlap(e1, e2) == 'none'
        
        # Test e1 contains e2
        e1 = PIIEntity(text="a", pii_type="EMAIL", type_label="EMAIL", start=0, end=20, score=0.9)
        e2 = PIIEntity(text="b", pii_type="EMAIL", type_label="EMAIL", start=5, end=15, score=0.8)
        assert merger._check_overlap(e1, e2) == 'kept_contains_current'
        
        # Test e2 contains e1
        e1 = PIIEntity(text="a", pii_type="EMAIL", type_label="EMAIL", start=5, end=15, score=0.9)
        e2 = PIIEntity(text="b", pii_type="EMAIL", type_label="EMAIL", start=0, end=20, score=0.8)
        assert merger._check_overlap(e1, e2) == 'current_contains_kept'
        
        # Test partial overlap
        e1 = PIIEntity(text="a", pii_type="EMAIL", type_label="EMAIL", start=0, end=10, score=0.9)
        e2 = PIIEntity(text="b", pii_type="EMAIL", type_label="EMAIL", start=5, end=15, score=0.8)
        assert merger._check_overlap(e1, e2) == 'partial'


class TestProvenanceLogging:
    """Test cases for provenance logging."""
    
    def test_should_log_entity_replacement_when_enabled(self):
        """Test logging entity replacement when provenance logging is enabled."""
        merger = DetectionMerger(log_provenance=True)
        
        old_entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.8)
        new_entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        key = (0, 4, "EMAIL", "test")
        source_by_key = {key: "model1"}
        
        with patch.object(merger.logger, 'info') as mock_log:
            merger._log_entity_replacement(key, old_entity, new_entity, "model2", source_by_key)
            assert mock_log.called
    
    def test_should_not_log_when_disabled(self):
        """Test not logging when provenance logging is disabled."""
        merger = DetectionMerger(log_provenance=False)
        
        old_entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.8)
        new_entity = PIIEntity(text="test", pii_type="EMAIL", type_label="EMAIL", start=0, end=4, score=0.9)
        key = (0, 4, "EMAIL", "test")
        source_by_key = {key: "model1"}
        
        with patch.object(merger.logger, 'info') as mock_log:
            merger._log_entity_replacement(key, old_entity, new_entity, "model2", source_by_key)
            assert not mock_log.called
    
    def test_should_log_overlap_resolution_when_enabled(self):
        """Test logging overlap resolution when enabled."""
        merger = DetectionMerger(log_provenance=True)
        
        with patch.object(merger.logger, 'info') as mock_log:
            merger._log_overlap_resolution(10, 8)
            assert mock_log.called
    
    def test_should_not_log_overlap_when_no_removal(self):
        """Test not logging overlap resolution when no entities removed."""
        merger = DetectionMerger(log_provenance=True)
        
        with patch.object(merger.logger, 'info') as mock_log:
            merger._log_overlap_resolution(10, 10)
            assert not mock_log.called
