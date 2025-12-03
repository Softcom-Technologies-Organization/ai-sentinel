"""
Unit tests for Presidio detector stale config fix (Phase 3).

Tests that Presidio detector correctly uses fresh configs passed at request time
instead of relying on stale cached configs from initialization.
"""

import pytest
from unittest.mock import Mock, MagicMock
from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector
from pii_detector.domain.entity.pii_type import PIIType


class TestPresidioStaleConfigFix:
    """Test suite for Phase 3 bug: stale config cache in Presidio detector."""
    
    def test_Should_UseRequestTimeConfigs_When_PassedToDetectPii(self, mocker):
        """
        Test that Presidio detector uses configs passed at request time
        instead of cached initialization configs.
        
        Scenario:
        1. Detector initialized with EMAIL disabled in DB
        2. Request comes with EMAIL enabled in fresh configs
        3. Detector should use fresh configs, not cached ones
        """
        # Arrange: Mock database adapter
        mock_db_adapter = Mock()
        
        # Cached configs at initialization: EMAIL disabled
        cached_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS'
            },
            'PERSON_NAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'PERSON'
            }
        }
        
        # Fresh configs at request time: EMAIL enabled
        fresh_configs = {
            'EMAIL': {
                'enabled': True,  # Now enabled!
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS'
            },
            'PERSON_NAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'PERSON'
            }
        }
        
        mock_db_adapter.fetch_pii_type_configs.return_value = cached_configs
        
        # Mock get_database_config_adapter to return our mock
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector (with EMAIL disabled in cache)
        detector = PresidioDetector()
        detector.load_model()
        
        # Mock analyzer to avoid actual NLP processing
        mock_analyzer = Mock()
        mock_analyzer.analyze.return_value = [
            Mock(
                entity_type='EMAIL_ADDRESS',
                start=0,
                end=15,
                score=0.9
            )
        ]
        detector._analyzer = mock_analyzer
        
        # Act: Call detect_pii with fresh configs (EMAIL now enabled)
        text = "test@email.com"
        entities = detector.detect_pii(text, threshold=0.5, pii_type_configs=fresh_configs)
        
        # Assert: EMAIL_ADDRESS should be in allowed entities (from fresh config)
        # The analyzer should have been called with EMAIL_ADDRESS in the whitelist
        call_args = mock_analyzer.analyze.call_args
        allowed_entities = call_args[1]['entities']
        
        assert 'EMAIL_ADDRESS' in allowed_entities, (
            "EMAIL_ADDRESS should be in allowed entities when fresh config has it enabled"
        )
        
        # Assert: Entity should be detected and returned
        assert len(entities) == 1
        assert entities[0].pii_type == PIIType.EMAIL
        assert entities[0].text == "test@email.com"
    
    def test_Should_FetchFreshConfigs_When_NoConfigsPassedToDetectPii(self, mocker):
        """
        Test that when no configs passed, detector fetches fresh from database.
        
        This ensures no stale cache - always uses fresh DB data.
        """
        # Arrange
        mock_db_adapter = Mock()
        
        db_configs = {
            'PERSON_NAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'PERSON'
            }
        }
        
        # Mock will be called twice: once for _build_allowed_entities, once for _convert_and_filter_results
        mock_db_adapter.fetch_pii_type_configs.return_value = db_configs
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = PresidioDetector()
        detector.load_model()
        
        # Mock analyzer
        mock_analyzer = Mock()
        mock_analyzer.analyze.return_value = [
            Mock(
                entity_type='PERSON',
                start=0,
                end=10,
                score=0.9
            )
        ]
        detector._analyzer = mock_analyzer
        
        # Act: Call detect_pii WITHOUT passing pii_type_configs (None/default)
        text = "John Smith"
        entities = detector.detect_pii(text, threshold=0.5)
        
        # Assert: Should have fetched fresh configs from DB (at least twice: pre-filter + post-filter)
        assert mock_db_adapter.fetch_pii_type_configs.call_count >= 2, (
            "Should fetch fresh configs from database when none passed"
        )
        
        call_args = mock_analyzer.analyze.call_args
        allowed_entities = call_args[1]['entities']
        
        assert 'PERSON' in allowed_entities
        assert len(entities) == 1
    
    def test_Should_HandleEmptyFreshConfigs_When_PassedToDetectPii(self, mocker):
        """
        Test that passing empty configs doesn't break detection.
        
        Business rule: If fresh configs are empty, fall back to cached configs.
        """
        # Arrange
        mock_db_adapter = Mock()
        
        cached_configs = {
            'PERSON_NAME': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'PRESIDIO',
                'detector_label': 'PERSON'
            }
        }
        
        mock_db_adapter.fetch_pii_type_configs.return_value = cached_configs
        
        mocker.patch(
            'pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter',
            return_value=mock_db_adapter
        )
        
        # Initialize detector
        detector = PresidioDetector()
        detector.load_model()
        
        # Mock analyzer
        mock_analyzer = Mock()
        mock_analyzer.analyze.return_value = []
        detector._analyzer = mock_analyzer
        
        # Act: Pass empty dict as fresh configs
        text = "John Smith"
        entities = detector.detect_pii(text, threshold=0.5, pii_type_configs={})
        
        # Assert: Should handle gracefully (fall back to cached or return empty)
        # This ensures we don't break when configs are empty
        assert isinstance(entities, list)
