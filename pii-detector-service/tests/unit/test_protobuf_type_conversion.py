"""
Unit tests for Protobuf type conversion in pii_service.

This module tests that entity values are correctly converted to native Python types
to ensure Protobuf compatibility, especially for NumPy types from Presidio detector.
"""

import pytest
from unittest.mock import Mock, MagicMock
import importlib

# Import from module with reserved keyword 'in'
pii_service = importlib.import_module('pii_detector.infrastructure.adapter.in.grpc.pii_service')
PIIDetectionServicer = pii_service.PIIDetectionServicer

from pii_detector.proto.generated import pii_detection_pb2


class TestProtobufTypeConversion:
    """Test suite for Protobuf type conversion."""
    
    @pytest.fixture
    def servicer(self):
        """Create PIIDetectionServicer instance for testing."""
        # Mock the detector to avoid loading models
        mock_detector = Mock()
        mock_detector.config = Mock()
        mock_detector.config.chunk_size = 1000
        mock_detector.config.chunk_overlap = 100
        
        servicer = PIIDetectionServicer(
            max_text_size=1_000_000,
            enable_memory_monitoring=False
        )
        servicer.detector = mock_detector
        
        return servicer
    
    def test_should_convert_numpy_int64_to_int(self, servicer):
        """
        Test that numpy.int64 values are converted to native Python int.
        
        Given: Entities with numpy.int64 values for start/end positions
        When: Adding entities to Protobuf response
        Then: Values should be converted to native Python int without error
        """
        import numpy as np
        
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': 'test@example.com',
                'type': 'EMAIL',
                'type_label': 'Email',
                'start': np.int64(0),  # NumPy type from Presidio
                'end': np.int64(16),   # NumPy type from Presidio
                'score': 0.95
            }
        ]
        
        # Should not raise any exception
        servicer._add_entities_to_response(response, entities, 'test_req')
        
        assert len(response.entities) == 1
        assert response.entities[0].start == 0
        assert response.entities[0].end == 16
        assert isinstance(response.entities[0].start, int)
        assert isinstance(response.entities[0].end, int)
    
    def test_should_convert_numpy_float64_to_float(self, servicer):
        """
        Test that numpy.float64 values are converted to native Python float.
        
        Given: Entities with numpy.float64 values for score
        When: Adding entities to Protobuf response
        Then: Values should be converted to native Python float without error
        """
        import numpy as np
        
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': 'John Doe',
                'type': 'PERSON_NAME',
                'type_label': 'Nom de personne',
                'start': 10,
                'end': 18,
                'score': np.float64(0.92)  # NumPy type from Presidio
            }
        ]
        
        # Should not raise any exception
        servicer._add_entities_to_response(response, entities, 'test_req')
        
        assert len(response.entities) == 1
        assert response.entities[0].score == pytest.approx(0.92)
        assert isinstance(response.entities[0].score, float)
    
    def test_should_convert_all_numpy_types_together(self, servicer):
        """
        Test that all NumPy types are converted together.
        
        Given: Entities with multiple NumPy types
        When: Adding entities to Protobuf response
        Then: All values should be converted to native Python types
        """
        import numpy as np
        
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': '+33612345678',
                'type': 'PHONE',
                'type_label': 'Téléphone',
                'start': np.int64(5),
                'end': np.int64(17),
                'score': np.float64(0.88)
            },
            {
                'text': 'FR7630001007941234567890185',
                'type': 'IBAN',
                'type_label': 'IBAN',
                'start': np.int64(20),
                'end': np.int64(47),
                'score': np.float64(0.99)
            }
        ]
        
        # Should not raise any exception
        servicer._add_entities_to_response(response, entities, 'test_req')
        
        assert len(response.entities) == 2
        
        # Verify first entity
        assert isinstance(response.entities[0].start, int)
        assert isinstance(response.entities[0].end, int)
        assert isinstance(response.entities[0].score, float)
        
        # Verify second entity
        assert isinstance(response.entities[1].start, int)
        assert isinstance(response.entities[1].end, int)
        assert isinstance(response.entities[1].score, float)
    
    def test_should_handle_regular_python_types(self, servicer):
        """
        Test that regular Python types still work correctly.
        
        Given: Entities with native Python types
        When: Adding entities to Protobuf response
        Then: Values should be preserved without error
        """
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': 'test@domain.com',
                'type': 'EMAIL',
                'type_label': 'Email',
                'start': 0,
                'end': 15,
                'score': 0.95
            }
        ]
        
        # Should not raise any exception
        servicer._add_entities_to_response(response, entities, 'test_req')
        
        assert len(response.entities) == 1
        assert response.entities[0].start == 0
        assert response.entities[0].end == 15
        assert response.entities[0].score == pytest.approx(0.95)
    
    def test_should_raise_error_on_invalid_type_conversion(self, servicer):
        """
        Test that conversion error is properly raised and logged.
        
        Given: Entity with non-convertible value
        When: Adding entities to Protobuf response
        Then: ValueError or TypeError should be raised with proper logging
        """
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': 'test',
                'type': 'TEST',
                'type_label': 'Test',
                'start': 'invalid',  # Invalid type that cannot be converted to int
                'end': 10,
                'score': 0.5
            }
        ]
        
        with pytest.raises((ValueError, TypeError)):
            servicer._add_entities_to_response(response, entities, 'test_req')
    
    def test_should_convert_streaming_chunk_update_entities(self, servicer):
        """
        Test that streaming chunk update entities are also converted correctly.
        
        Given: Entities with NumPy types in streaming context
        When: Creating chunk update
        Then: Values should be converted to native Python types
        """
        import numpy as np
        from pii_detector.domain.entity.pii_entity import PIIEntity
        
        entities = [
            PIIEntity(
                text='test@example.com',
                pii_type='EMAIL',
                type_label='Email',
                start=np.int64(0),
                end=np.int64(16),
                score=np.float64(0.95)
            )
        ]
        
        # Should not raise any exception
        update = servicer._create_chunk_update(entities, 0, 1)
        
        assert len(update.entities) == 1
        assert isinstance(update.entities[0].start, int)
        assert isinstance(update.entities[0].end, int)
        assert isinstance(update.entities[0].score, float)
    
    def test_should_handle_multiple_entities_with_mixed_types(self, servicer):
        """
        Test handling of multiple entities with mixed Python and NumPy types.
        
        Given: Mix of entities with Python types and NumPy types
        When: Adding entities to Protobuf response
        Then: All should be converted correctly without error
        """
        import numpy as np
        
        response = pii_detection_pb2.PIIDetectionResponse()
        entities = [
            {
                'text': 'Python types',
                'type': 'TEST1',
                'type_label': 'Test 1',
                'start': 0,
                'end': 12,
                'score': 0.9
            },
            {
                'text': 'NumPy types',
                'type': 'TEST2',
                'type_label': 'Test 2',
                'start': np.int64(15),
                'end': np.int64(26),
                'score': np.float64(0.85)
            },
            {
                'text': 'Mixed types',
                'type': 'TEST3',
                'type_label': 'Test 3',
                'start': np.int64(30),
                'end': 41,
                'score': np.float64(0.88)
            }
        ]
        
        # Should not raise any exception
        servicer._add_entities_to_response(response, entities, 'test_req')
        
        assert len(response.entities) == 3
        
        # Verify all entities have correct native types
        for entity in response.entities:
            assert isinstance(entity.start, int)
            assert isinstance(entity.end, int)
            assert isinstance(entity.score, float)
