"""
Unit tests for CompositePIIDetector runtime detector override bug.

This test reproduces the bug where enabling regex detection at runtime
(via database configuration) fails when regex detector was disabled at startup
(via TOML configuration).

Bug scenario:
- TOML: regex_detection_enabled = false → RegexDetector not created
- DB: regex_enabled = true → Tries to use regex detector
- Result: No detection occurs because self.regex_detector is None

Business rule violated: "Configuration is fetched at scan start to ensure 
consistency throughout the entire scan. Detector flags are applied dynamically 
without service restart."
"""

from unittest.mock import Mock, patch

import pytest

from pii_detector.application.orchestration.composite_detector import (
    CompositePIIDetector,
    create_composite_detector,
    _create_regex_detector_if_enabled
)
from pii_detector.domain.entity.pii_entity import PIIEntity


class TestRuntimeRegexActivation:
    """
    Test case reproducing the bug where runtime regex activation fails
    when TOML configuration has regex disabled at startup.
    
    This violates the business rule: "Detector flags are applied dynamically 
    without service restart."
    """
    
    @pytest.fixture
    def sample_text_with_email(self) -> str:
        """Sample text containing PII that regex detector should find."""
        return "Contact me at john.doe@example.com for more information."
    
    @pytest.fixture
    def mock_regex_detector(self):
        """Mock RegexDetector that returns a sample entity."""
        detector = Mock()
        detector.model_id = "regex-detector"
        
        # Create a sample PIIEntity that regex would detect
        email_entity = PIIEntity(
            text="john.doe@example.com",
            pii_type="EMAIL",
            type_label="EMAIL",
            start=14,
            end=36,
            score=1.0
        )
        detector.detect_pii.return_value = [email_entity]
        
        return detector
    
    def test_regex_detector_created_at_startup_regardless_of_toml(
        self, 
        sample_text_with_email: str,
        mock_regex_detector
    ):
        """
        FIXED BEHAVIOR: RegexDetector is now ALWAYS created at startup,
        regardless of TOML configuration, enabling runtime activation.
        
        Steps:
        1. Simulate TOML config with regex_detection_enabled = false
        2. Create CompositePIIDetector via factory
        3. Verify regex_detector is NOT None (fix implemented)
        4. Verify enable_regex is False by default (respects TOML)
        5. Call detect_pii with enable_regex=True (runtime override works)
        6. Should detect PII successfully
        """
        # ARRANGE: Simulate TOML configuration with regex disabled
        with patch('pii_detector.application.orchestration.composite_detector._load_detection_config') as mock_load_config:
            mock_load_config.return_value = (False, False)  # regex=False, presidio=False
            
            # Create composite detector via factory (as done at startup)
            composite = create_composite_detector(ml_detector=None)
            
            # VERIFY FIXED STATE: regex_detector is NOW CREATED (fix implemented)
            assert composite.regex_detector is not None, (
                "FIXED: regex_detector should be created even when TOML has regex_detection_enabled=false"
            )
            assert composite.enable_regex is False, (
                "enable_regex should be False by default (respects TOML default)"
            )
        
        # Replace with mock detector for controlled testing
        composite.regex_detector = mock_regex_detector
        
        # Mock the merger to return regex results
        with patch.object(composite, '_merger') as mock_merger:
            mock_merger.merge.return_value = mock_regex_detector.detect_pii.return_value
            
            # ACT: Activate regex at runtime via DB override (enable_regex=True)
            entities = composite.detect_pii(
                sample_text_with_email,
                threshold=0.5,
                enable_ml=False,
                enable_regex=True,  # Database override: use regex!
                enable_presidio=False
            )
        
        # ASSERT: FIXED BEHAVIOR - Detections work with runtime override
        assert len(entities) == 1, (
            "FIXED: Entities detected successfully because regex_detector was created at startup"
        )
        assert entities[0].pii_type == "EMAIL"
    
    def test_expected_behavior_after_fix(
        self,
        sample_text_with_email: str,
        mock_regex_detector
    ):
        """
        EXPECTED BEHAVIOR (after fix): RegexDetector should always be created
        at startup (regardless of TOML), and runtime activation should work.
        
        This test will FAIL now but should PASS after implementing the fix.
        
        Steps:
        1. Create CompositePIIDetector with regex_detector instance
        2. Initially disable regex (enable_regex=False)
        3. Call detect_pii with enable_regex=True (runtime override)
        4. Should detect PII successfully
        """
        # ARRANGE: Create composite with regex detector available but initially disabled
        composite = CompositePIIDetector(
            ml_detector=None,
            regex_detector=mock_regex_detector,
            presidio_detector=None,
            merger=None,
            enable_regex=False  # Initially disabled (as per TOML)
        )
        
        # Inject a simple merger that just returns regex results
        with patch.object(composite, '_merger') as mock_merger:
            mock_merger.merge.return_value = mock_regex_detector.detect_pii.return_value
            
            # ACT: Enable regex at runtime via parameter override
            entities = composite.detect_pii(
                sample_text_with_email,
                threshold=0.5,
                enable_ml=False,
                enable_regex=True,  # Runtime override: activate regex!
                enable_presidio=False
            )
        
        # ASSERT: Should detect the email
        assert len(entities) == 1, (
            "EXPECTED BEHAVIOR: Should detect PII when enable_regex=True at runtime"
        )
        assert entities[0].pii_type == "EMAIL"
        assert entities[0].text == "john.doe@example.com"
        
        # Verify that regex detector was actually called with correct threshold
        mock_regex_detector.detect_pii.assert_called_once_with(
            sample_text_with_email,
            0.5  # threshold is passed through from detect_pii call
        )
    
    def test_helper_function_always_creates_detector(self):
        """
        FIXED: _create_regex_detector_if_enabled now ALWAYS creates RegexDetector,
        regardless of the regex_enabled parameter (which is now only for logging).
        
        This fixes the root cause where disabled detectors couldn't be activated at runtime.
        """
        # ACT: Call with regex_enabled=False
        result_disabled = _create_regex_detector_if_enabled(regex_enabled=False)
        
        # ASSERT: Should still create detector (fix implemented)
        assert result_disabled is not None, (
            "FIXED: Helper function should create RegexDetector even when regex_enabled=False"
        )
        assert hasattr(result_disabled, 'detect_pii'), (
            "Created detector should be a valid RegexDetector"
        )
        
        # ACT: Call with regex_enabled=True
        result_enabled = _create_regex_detector_if_enabled(regex_enabled=True)
        
        # ASSERT: Should also create detector
        assert result_enabled is not None, (
            "Helper function should create RegexDetector when regex_enabled=True"
        )
    
    def test_factory_creates_detector_instance_even_when_toml_disabled(self):
        """
        FIXED: create_composite_detector now creates regex_detector instance
        even when TOML has regex_detection_enabled=false.
        
        This enables runtime activation without service restart.
        """
        # ARRANGE: Mock TOML config with regex disabled
        with patch('pii_detector.application.orchestration.composite_detector._load_detection_config') as mock_load_config:
            mock_load_config.return_value = (False, False)  # regex=False, presidio=False
            
            # ACT
            composite = create_composite_detector(ml_detector=None)
            
            # ASSERT: FIXED - Detector is created even with TOML disabled
            assert composite.regex_detector is not None, (
                "FIXED: Factory creates regex_detector instance even when TOML has regex_detection_enabled=false"
            )
            assert composite.enable_regex is False, (
                "enable_regex should respect TOML default (False), but detector instance exists"
            )


class TestNoDetectorsWarning:
    """Test that appropriate warning is logged when no detectors are available."""
    
    def test_logs_warning_when_all_detectors_none_and_disabled(self):
        """
        Verify that "No detectors available" warning is logged when
        attempting detection with no available detectors.
        
        This is the symptom of the bug that appears in the logs.
        """
        # ARRANGE: Create composite with no detectors
        composite = CompositePIIDetector(
            ml_detector=None,
            regex_detector=None,
            presidio_detector=None,
            enable_regex=False,
            enable_presidio=False
        )
        
        with patch.object(composite, 'logger') as mock_logger:
            # ACT
            entities = composite.detect_pii(
                "Some text with PII",
                enable_ml=False,
                enable_regex=True,  # Try to enable but detector is None
                enable_presidio=False
            )
            
            # ASSERT
            assert len(entities) == 0
            mock_logger.warning.assert_called_once_with("No detectors available")
