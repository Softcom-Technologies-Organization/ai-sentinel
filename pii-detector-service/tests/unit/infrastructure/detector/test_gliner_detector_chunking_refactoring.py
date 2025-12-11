"""
Tests for GLiNER detector chunking refactoring.

These tests validate the behavior of _detect_pii_with_chunking method
before and after refactoring to ensure no regression.
"""

import pytest
from typing import List
from unittest.mock import Mock, MagicMock, patch

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector


class TestGLiNERDetectorChunkingRefactoring:
    """Test suite for chunking method refactoring."""

    @pytest.fixture
    def mock_config(self):
        """Create a mock configuration."""
        config = Mock(spec=DetectionConfig)
        config.model_id = "test-model"
        config.device = "cpu"
        config.threshold = 0.5
        return config

    @pytest.fixture
    def detector_with_mocks(self, mock_config):
        """Create detector with mocked dependencies."""
        with patch('pii_detector.infrastructure.detector.gliner_detector.GLiNERModelManager'):
            detector = GLiNERDetector(config=mock_config)
            
            # Mock model
            detector.model = Mock()
            
            # Mock semantic chunker
            detector.semantic_chunker = Mock()
            
            return detector

    def test_should_DetectPII_When_ParallelProcessingEnabled(self, detector_with_mocks):
        """
        Test PII detection with parallel processing enabled.
        
        Validates that:
        - Multiple chunks are processed in parallel
        - Entities are correctly collected and deduplicated
        - Positions are adjusted correctly
        """
        # Arrange
        detector = detector_with_mocks
        detector.parallel_enabled = True
        detector.max_workers = 2
        
        # Mock chunk results (2 chunks)
        chunk1 = Mock()
        chunk1.text = "John Doe lives here"
        chunk1.start = 0
        
        chunk2 = Mock()
        chunk2.text = "Email: john@example.com"
        chunk2.start = 20
        
        detector.semantic_chunker.chunk_text.return_value = [chunk1, chunk2]
        
        # Mock GLiNER predictions for each chunk
        # Chunk 1: detects "John Doe"
        detector.model.predict_entities.side_effect = [
            [{"text": "John Doe", "label": "first name", "start": 0, "end": 8, "score": 0.9}],
            [{"text": "john@example.com", "label": "email", "start": 7, "end": 23, "score": 0.95}]
        ]
        
        text = "John Doe lives here Email: john@example.com"
        threshold = 0.5
        detection_id = "test-001"
        
        # Mock pii_type_configs for fresh config fetch
        pii_type_configs = {
            'GIVENNAME': {'enabled': True, 'detector_label': 'first name', 'threshold': 0.5},
            'EMAIL': {'enabled': True, 'detector_label': 'email', 'threshold': 0.5}
        }
        
        # Act
        entities = detector._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)
        
        # Assert
        assert len(entities) == 2
        
        # Verify first entity (from chunk 1)
        assert entities[0].text == "John Doe"
        assert entities[0].pii_type == "GIVENNAME"
        assert entities[0].start == 0  # Position relative to original text
        assert entities[0].end == 8
        assert entities[0].score == 0.9
        
        # Verify second entity (from chunk 2)
        assert entities[1].text == "john@example.com"
        assert entities[1].pii_type == "EMAIL"
        assert entities[1].start == 27  # 7 + 20 (chunk offset)
        assert entities[1].end == 43    # 23 + 20
        assert entities[1].score == 0.95

    def test_should_DetectPII_When_SequentialProcessingEnabled(self, detector_with_mocks):
        """
        Test PII detection with sequential processing (parallel disabled).
        
        Validates that:
        - Chunks are processed sequentially
        - Results are identical to parallel processing
        """
        # Arrange
        detector = detector_with_mocks
        detector.parallel_enabled = False
        
        # Mock chunk results (2 chunks)
        chunk1 = Mock()
        chunk1.text = "John Doe"
        chunk1.start = 0
        
        chunk2 = Mock()
        chunk2.text = "jane@example.com"
        chunk2.start = 10
        
        detector.semantic_chunker.chunk_text.return_value = [chunk1, chunk2]
        
        # Mock GLiNER predictions
        detector.model.predict_entities.side_effect = [
            [{"text": "John Doe", "label": "first name", "start": 0, "end": 8, "score": 0.9}],
            [{"text": "jane@example.com", "label": "email", "start": 0, "end": 16, "score": 0.95}]
        ]
        
        text = "John Doe jane@example.com"
        threshold = 0.5
        detection_id = "test-002"
        
        # Mock pii_type_configs
        pii_type_configs = {
            'GIVENNAME': {'enabled': True, 'detector_label': 'first name', 'threshold': 0.5},
            'EMAIL': {'enabled': True, 'detector_label': 'email', 'threshold': 0.5}
        }
        
        # Act
        entities = detector._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)
        
        # Assert
        assert len(entities) == 2
        assert entities[0].start == 0
        assert entities[0].end == 8
        assert entities[1].start == 10  # Adjusted position
        assert entities[1].end == 26    # 16 + 10

    def test_should_RemoveDuplicates_When_SameEntityInMultipleChunks(self, detector_with_mocks):
        """
        Test deduplication when same entity appears in overlapping chunks.
        """
        # Arrange
        detector = detector_with_mocks
        detector.parallel_enabled = False
        
        # Mock overlapping chunks with duplicate entity
        chunk1 = Mock()
        chunk1.text = "Email: john@example.com"
        chunk1.start = 0
        
        chunk2 = Mock()  # Overlapping chunk
        chunk2.text = "john@example.com is"
        chunk2.start = 7
        
        detector.semantic_chunker.chunk_text.return_value = [chunk1, chunk2]
        
        # Both chunks detect the same email at same position
        detector.model.predict_entities.side_effect = [
            [{"text": "john@example.com", "label": "email", "start": 7, "end": 23, "score": 0.95}],
            [{"text": "john@example.com", "label": "email", "start": 0, "end": 16, "score": 0.95}]
        ]
        
        text = "Email: john@example.com is valid"
        threshold = 0.5
        detection_id = "test-003"
        
        # Mock pii_type_configs
        pii_type_configs = {
            'EMAIL': {'enabled': True, 'detector_label': 'email', 'threshold': 0.5}
        }
        
        # Act
        entities = detector._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)
        
        # Assert - should only have one entity (duplicate removed)
        assert len(entities) == 1
        assert entities[0].text == "john@example.com"
        assert entities[0].start == 7

    def test_should_RaiseError_When_SemanticChunkerNotInitialized(self, detector_with_mocks):
        """
        Test error handling when semantic chunker is not initialized.
        """
        # Arrange
        detector = detector_with_mocks
        detector.semantic_chunker = None
        
        # Act & Assert
        with pytest.raises(RuntimeError, match="Semantic chunker not initialized"):
            detector._detect_pii_with_chunking("test text", 0.5, "test-004", None)

    def test_should_ApplyScoringFilter_When_ConfiguredThresholdsExist(self, detector_with_mocks):
        """
        Test that entity-specific scoring thresholds are applied.
        """
        # Arrange
        detector = detector_with_mocks
        detector.parallel_enabled = False
        
        # Mock pii_type_configs with high threshold for EMAIL
        pii_type_configs = {
            'EMAIL': {'enabled': True, 'detector_label': 'email', 'threshold': 0.9}  # High threshold
        }
        
        chunk = Mock()
        chunk.text = "Contact: john@example.com"
        chunk.start = 0
        
        detector.semantic_chunker.chunk_text.return_value = [chunk]
        
        # Mock detection with score below threshold
        detector.model.predict_entities.return_value = [
            {"text": "john@example.com", "label": "email", "start": 9, "end": 25, "score": 0.7}  # Below 0.9
        ]
        
        text = "Contact: john@example.com"
        threshold = 0.5
        detection_id = "test-005"
        
        # Act
        entities = detector._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)
        
        # Assert - entity should be filtered out due to scoring override
        assert len(entities) == 0

    def test_should_UseSingleChunkMode_When_OnlyOneChunk(self, detector_with_mocks):
        """
        Test that sequential mode is used when there's only one chunk,
        even if parallel is enabled.
        """
        # Arrange
        detector = detector_with_mocks
        detector.parallel_enabled = True
        detector.max_workers = 10
        
        # Single chunk
        chunk = Mock()
        chunk.text = "Short text"
        chunk.start = 0
        
        detector.semantic_chunker.chunk_text.return_value = [chunk]
        
        detector.model.predict_entities.return_value = []
        
        text = "Short text"
        threshold = 0.5
        detection_id = "test-006"
        
        # Mock pii_type_configs
        pii_type_configs = {}
        
        # Act
        entities = detector._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)
        
        # Assert - should complete without error
        assert entities == []
        
        # Verify model was called once (sequential mode)
        assert detector.model.predict_entities.call_count == 1
