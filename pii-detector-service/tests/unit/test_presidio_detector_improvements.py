"""
Tests for Presidio detector improvements.

This module validates the enhancements made to PresidioDetector:
- Whitelist construction from config
- Entity filtering via analyze() entities parameter
- Post-filtering based on scoring thresholds
- SpacyRecognizer removal when person_name is disabled
- Unknown entity logging
"""

import pytest
from pathlib import Path
from unittest.mock import Mock, patch, MagicMock
from presidio_analyzer import RecognizerResult

from pii_detector.service.detector.presidio_detector import PresidioDetector
from pii_detector.service.detector.models import PIIType


class TestPresidioDetectorImprovements:
    """Test suite for Presidio detector improvements."""
    
    @pytest.fixture
    def mock_config(self):
        """Provide a mock configuration for testing."""
        return {
            "model": {
                "model_id": "presidio-detector",
                "enabled": True,
                "priority": 2
            },
            "detection": {
                "default_threshold": 0.5,
                "languages": ["en"],
                "labels_to_ignore": []
            },
            "recognizers": {
                "email": True,
                "phone": True,
                "person_name": False,  # Disabled to test SpacyRecognizer removal
                "ip_address": True,
                "credit_card": True,
                "location": False
            },
            "scoring": {
                "EMAIL_ADDRESS": 0.95,
                "PHONE_NUMBER": 0.85,
                "IP_ADDRESS": 0.98,
                "CREDIT_CARD": 0.90
            },
            "advanced": {
                "use_context": True,
                "allow_list": [],
                "deny_list": []
            }
        }
    
    @pytest.fixture
    def detector_with_mock_config(self, mock_config):
        """Create a PresidioDetector with mocked configuration."""
        with patch.object(PresidioDetector, '_load_config', return_value=mock_config):
            detector = PresidioDetector()
            return detector
    
    def test_build_allowed_entities_should_map_config_keys_to_presidio_entities(
        self, detector_with_mock_config
    ):
        """
        Should_CreateWhitelistWithCorrectPresidioEntityNames_When_BuildingFromConfig.
        
        Validates that config keys are correctly mapped to official Presidio entity types.
        """
        # When
        allowed_entities = detector_with_mock_config._build_allowed_entities()
        
        # Then
        assert "EMAIL_ADDRESS" in allowed_entities  # email -> EMAIL_ADDRESS
        assert "PHONE_NUMBER" in allowed_entities   # phone -> PHONE_NUMBER
        assert "IP_ADDRESS" in allowed_entities     # ip_address -> IP_ADDRESS
        assert "CREDIT_CARD" in allowed_entities    # credit_card -> CREDIT_CARD
        
        # Disabled entities should not be in whitelist
        assert "PERSON" not in allowed_entities     # person_name = false
        assert "LOCATION" not in allowed_entities   # location = false
    
    def test_build_allowed_entities_should_log_warning_for_unknown_config_keys(
        self, detector_with_mock_config, caplog
    ):
        """
        Should_LogWarning_When_ConfigContainsUnknownRecognizerKey.
        
        Validates that unknown recognizer keys are logged for debugging.
        """
        # Given: Add an unknown recognizer key
        detector_with_mock_config._recognizers_config["unknown_entity"] = True
        
        # When
        with caplog.at_level("WARNING"):
            allowed_entities = detector_with_mock_config._build_allowed_entities()
        
        # Then
        assert "Unknown recognizer key 'unknown_entity'" in caplog.text
    
    def test_filter_recognizers_should_remove_spacy_recognizer_when_person_name_disabled(
        self, detector_with_mock_config
    ):
        """
        Should_RemoveSpacyRecognizer_When_PersonNameIsDisabled.
        
        Validates that SpacyRecognizer is explicitly removed when person_name=false
        to reduce false positives.
        """
        # Given: Mock analyzer with SpacyRecognizer and iterable recognizers list
        mock_analyzer = Mock()
        mock_registry = Mock()
        mock_recognizer = Mock()
        mock_recognizer.supported_entities = ["EMAIL_ADDRESS"]
        mock_registry.recognizers = [mock_recognizer]  # Make it iterable
        mock_analyzer.registry = mock_registry
        detector_with_mock_config._analyzer = mock_analyzer
        
        # When
        detector_with_mock_config._filter_recognizers()
        
        # Then
        mock_registry.remove_recognizer.assert_called_once_with("SpacyRecognizer")
    
    def test_detect_pii_should_pass_entities_whitelist_to_analyzer(
        self, detector_with_mock_config
    ):
        """
        Should_PassEntitiesWhitelist_When_CallingAnalyze.
        
        Validates that the entities parameter is used in analyze() to lock detection scope.
        """
        # Given
        mock_analyzer = Mock()
        mock_analyzer.analyze.return_value = []
        detector_with_mock_config._analyzer = mock_analyzer
        
        text = "Test text with email@test.com"
        
        # When
        detector_with_mock_config.detect_pii(text)
        
        # Then
        call_args = mock_analyzer.analyze.call_args
        assert "entities" in call_args.kwargs
        entities_param = call_args.kwargs["entities"]
        
        # Verify whitelist contains expected entities
        assert "EMAIL_ADDRESS" in entities_param
        assert "PHONE_NUMBER" in entities_param
        assert "IP_ADDRESS" in entities_param
        
        # Verify disabled entities are not in whitelist
        assert "PERSON" not in entities_param
        assert "LOCATION" not in entities_param
    
    def test_convert_and_filter_results_should_apply_entity_specific_thresholds(
        self, detector_with_mock_config
    ):
        """
        Should_FilterOutLowScoreResults_When_BelowEntitySpecificThreshold.
        
        Validates post-filtering based on per-entity thresholds from [scoring].
        """
        # Given
        text = "email@test.com and phone 555-1234"
        
        # Mock results with different scores
        mock_results = [
            # Email with high score - should pass (threshold: 0.95)
            Mock(
                entity_type="EMAIL_ADDRESS",
                start=0,
                end=14,
                score=0.96
            ),
            # Email with low score - should be filtered (threshold: 0.95)
            Mock(
                entity_type="EMAIL_ADDRESS",
                start=0,
                end=14,
                score=0.85
            ),
            # Phone with acceptable score - should pass (threshold: 0.85)
            Mock(
                entity_type="PHONE_NUMBER",
                start=25,
                end=33,
                score=0.87
            ),
            # Phone with low score - should be filtered (threshold: 0.85)
            Mock(
                entity_type="PHONE_NUMBER",
                start=25,
                end=33,
                score=0.70
            )
        ]
        
        # When
        entities = detector_with_mock_config._convert_and_filter_results(text, mock_results)
        
        # Then
        assert len(entities) == 2  # Only 2 out of 4 should pass
        
        # Verify the passing entities
        assert entities[0].pii_type == PIIType.EMAIL
        assert entities[0].score == 0.96
        
        assert entities[1].pii_type == PIIType.PHONE
        assert entities[1].score == 0.87
    
    def test_convert_and_filter_results_should_log_unknown_entity_types(
        self, detector_with_mock_config, caplog
    ):
        """
        Should_LogWarning_When_UnknownPresidioEntityTypeDetected.
        
        Validates that unknown Presidio entity types are logged for debugging.
        """
        # Given
        text = "some text"
        mock_results = [
            Mock(
                entity_type="UNKNOWN_TYPE",
                start=0,
                end=9,
                score=0.90
            )
        ]
        
        # When
        with caplog.at_level("WARNING"):
            entities = detector_with_mock_config._convert_and_filter_results(
                text, mock_results
            )
        
        # Then
        assert "Unknown Presidio entity_type 'UNKNOWN_TYPE'" in caplog.text
        assert "Consider adding to PRESIDIO_TO_PII_TYPE_MAP" in caplog.text
        
        # Entity should still be created with UNKNOWN type
        assert len(entities) == 1
        assert entities[0].pii_type == PIIType.UNKNOWN
    
    def test_convert_and_filter_results_should_preserve_original_scores(
        self, detector_with_mock_config
    ):
        """
        Should_PreserveOriginalPresidioScores_When_ConvertingResults.
        
        Validates that original Presidio scores are not overridden,
        and [scoring] values are used only as thresholds.
        """
        # Given
        text = "email@test.com"
        original_score = 0.97
        
        mock_results = [
            Mock(
                entity_type="EMAIL_ADDRESS",
                start=0,
                end=14,
                score=original_score
            )
        ]
        
        # When
        entities = detector_with_mock_config._convert_and_filter_results(
            text, mock_results
        )
        
        # Then
        assert len(entities) == 1
        assert entities[0].score == original_score  # Score should be preserved
    
    def test_convert_and_filter_results_should_log_filtered_count(
        self, detector_with_mock_config, caplog
    ):
        """
        Should_LogFilteredCount_When_PostFilteringApplied.
        
        Validates that the number of filtered results is logged for monitoring.
        """
        # Given
        text = "test"
        mock_results = [
            # Below threshold - will be filtered
            Mock(entity_type="EMAIL_ADDRESS", start=0, end=4, score=0.70),
            Mock(entity_type="EMAIL_ADDRESS", start=0, end=4, score=0.80),
            # Above threshold - will pass
            Mock(entity_type="EMAIL_ADDRESS", start=0, end=4, score=0.96)
        ]
        
        # When
        with caplog.at_level("INFO"):
            entities = detector_with_mock_config._convert_and_filter_results(
                text, mock_results
            )
        
        # Then
        assert "Post-filtered 2 results based on per-entity thresholds" in caplog.text
        assert len(entities) == 1
    
    def test_detect_pii_should_return_empty_list_when_no_entities_enabled(
        self, detector_with_mock_config, caplog
    ):
        """
        Should_ReturnEmptyListAndLogWarning_When_NoEntitiesEnabled.
        
        Validates graceful handling when all recognizers are disabled.
        """
        # Given: Disable all recognizers
        detector_with_mock_config._recognizers_config = {}
        
        # When
        with caplog.at_level("WARNING"):
            entities = detector_with_mock_config.detect_pii("test text")
        
        # Then
        assert len(entities) == 0
        assert "No entities enabled in configuration" in caplog.text
    
    def test_detect_pii_should_log_raw_entity_types_for_debugging(
        self, detector_with_mock_config, caplog
    ):
        """
        Should_LogRawEntityTypes_When_ResultsReturned.
        
        Validates that raw Presidio entity types are logged for debugging.
        """
        # Given
        mock_analyzer = Mock()
        mock_results = [
            Mock(entity_type="EMAIL_ADDRESS", start=0, end=10, score=0.96),
            Mock(entity_type="PHONE_NUMBER", start=11, end=20, score=0.90)
        ]
        mock_analyzer.analyze.return_value = mock_results
        detector_with_mock_config._analyzer = mock_analyzer
        
        # When
        with caplog.at_level("DEBUG"):
            detector_with_mock_config.detect_pii("test text")
        
        # Then
        assert "Raw entity types detected:" in caplog.text
        assert "EMAIL_ADDRESS" in caplog.text or "PHONE_NUMBER" in caplog.text


class TestPresidioDetectorConfigurationScenarios:
    """Test various configuration scenarios for Presidio detector."""
    
    def test_should_handle_high_threshold_configuration(self):
        """
        Should_FilterMoreAggressively_When_HighThresholdsConfigured.
        
        Validates that high scoring thresholds reduce false positives.
        """
        # Given: Very high thresholds
        config = {
            "model": {"model_id": "test", "enabled": True, "priority": 1},
            "detection": {"default_threshold": 0.9, "languages": ["en"], "labels_to_ignore": []},
            "recognizers": {"email": True, "phone": True},
            "scoring": {
                "EMAIL_ADDRESS": 0.99,  # Very high threshold
                "PHONE_NUMBER": 0.99
            },
            "advanced": {"use_context": True, "allow_list": [], "deny_list": []}
        }
        
        with patch.object(PresidioDetector, '_load_config', return_value=config):
            detector = PresidioDetector()
            
            # Mock results with good but not excellent scores
            mock_results = [
                Mock(entity_type="EMAIL_ADDRESS", start=0, end=10, score=0.95),  # Below 0.99
                Mock(entity_type="PHONE_NUMBER", start=11, end=20, score=0.96)   # Below 0.99
            ]
            
            # When
            entities = detector._convert_and_filter_results("test", mock_results)
            
            # Then
            assert len(entities) == 0  # All filtered due to high thresholds
    
    def test_should_allow_all_entities_when_no_scoring_overrides(self):
        """
        Should_PassAllResults_When_NoScoringOverridesConfigured.
        
        Validates that entities pass through when no specific thresholds are set.
        """
        # Given: No scoring overrides
        config = {
            "model": {"model_id": "test", "enabled": True, "priority": 1},
            "detection": {"default_threshold": 0.5, "languages": ["en"], "labels_to_ignore": []},
            "recognizers": {"email": True},
            "scoring": {},  # Empty scoring section
            "advanced": {"use_context": True, "allow_list": [], "deny_list": []}
        }
        
        with patch.object(PresidioDetector, '_load_config', return_value=config):
            detector = PresidioDetector()
            
            mock_results = [
                Mock(entity_type="EMAIL_ADDRESS", start=0, end=10, score=0.60)
            ]
            
            # When
            entities = detector._convert_and_filter_results("test", mock_results)
            
            # Then
            assert len(entities) == 1  # Should pass without specific threshold
