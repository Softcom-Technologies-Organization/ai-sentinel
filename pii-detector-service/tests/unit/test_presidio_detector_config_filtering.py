"""
Unit tests for Presidio detector's database configuration filtering.

Business Rule: Presidio detector should only process PII type configs where
detector field is 'PRESIDIO' or 'ALL', ignoring configs for GLINER and REGEX.

This ensures that Presidio's whitelist and scoring thresholds are built only
from relevant configurations, preventing incorrect detection behavior.
"""

import pytest
from unittest.mock import Mock, patch
from pathlib import Path
from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector


class TestPresidioDetectorConfigFiltering:
    """Test detector-specific config filtering in Presidio detector."""
    
    def setup_method(self):
        """Setup test fixtures with mocked database adapter."""
        # Mock database adapter to prevent real database connection
        with patch('pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter') as mock_adapter:
            mock_adapter.return_value = None  # Disable database for unit tests
            self.detector = PresidioDetector()
    
    def test_should_build_allowed_entities_only_from_presidio_and_all_configs(self):
        """
        Given: Database configs with mixed detector values (GLINER, PRESIDIO, REGEX, ALL)
        When: _build_allowed_entities_from_database is called
        Then: Only configs with detector=PRESIDIO or detector=ALL should be included
        
        Business Rule: Presidio should not use configs intended for other detectors.
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,
                'detector': 'PRESIDIO',  # Should be included
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email Address',
                'category': 'contact'
            },
            'PHONE_NUMBER': {
                'enabled': True,
                'threshold': 0.5,
                'detector': 'GLINER',  # Should be EXCLUDED
                'detector_label': 'phone',
                'display_name': 'Phone Number',
                'category': 'contact'
            },
            'CREDIT_CARD': {
                'enabled': True,
                'threshold': 0.7,
                'detector': 'ALL',  # Should be included
                'detector_label': 'CREDIT_CARD',
                'display_name': 'Credit Card',
                'category': 'financial'
            },
            'IP_ADDRESS': {
                'enabled': True,
                'threshold': 0.6,
                'detector': 'REGEX',  # Should be EXCLUDED
                'detector_label': r'\d+\.\d+\.\d+\.\d+',
                'display_name': 'IP Address',
                'category': 'network'
            },
            'PERSON_NAME': {
                'enabled': True,
                'threshold': 0.4,
                'detector': 'PRESIDIO',  # Should be included
                'detector_label': 'PERSON',
                'display_name': 'Person Name',
                'category': 'personal'
            }
        }
        
        # Act
        allowed_entities = self.detector._build_allowed_entities_from_database(db_configs)
        
        # Assert
        assert len(allowed_entities) == 3, (
            f"Expected 3 entities (2 PRESIDIO + 1 ALL), got {len(allowed_entities)}"
        )
        assert 'EMAIL_ADDRESS' in allowed_entities, "PRESIDIO EMAIL should be included"
        assert 'CREDIT_CARD' in allowed_entities, "ALL CREDIT_CARD should be included"
        assert 'PERSON' in allowed_entities, "PRESIDIO PERSON should be included"
        
        # Should NOT include GLINER or REGEX configs
        assert 'phone' not in allowed_entities, "GLINER phone should be excluded"
        assert r'\d+\.\d+\.\d+\.\d+' not in allowed_entities, "REGEX IP should be excluded"
    
    def test_should_exclude_disabled_configs_regardless_of_detector(self):
        """
        Given: Configs with enabled=False for various detectors
        When: _build_allowed_entities_from_database is called
        Then: Disabled configs should be excluded even if detector matches
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': False,  # Disabled
                'threshold': 0.3,
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email'
            },
            'PHONE_NUMBER': {
                'enabled': False,  # Disabled
                'threshold': 0.5,
                'detector': 'ALL',
                'detector_label': 'PHONE_NUMBER',
                'display_name': 'Phone'
            },
            'CREDIT_CARD': {
                'enabled': True,  # Enabled
                'threshold': 0.7,
                'detector': 'PRESIDIO',
                'detector_label': 'CREDIT_CARD',
                'display_name': 'Credit Card'
            }
        }
        
        # Act
        allowed_entities = self.detector._build_allowed_entities_from_database(db_configs)
        
        # Assert
        assert len(allowed_entities) == 1, f"Expected 1 entity (only enabled PRESIDIO), got {len(allowed_entities)}"
        assert 'CREDIT_CARD' in allowed_entities
        assert 'EMAIL_ADDRESS' not in allowed_entities, "Disabled EMAIL should be excluded"
        assert 'PHONE_NUMBER' not in allowed_entities, "Disabled PHONE should be excluded"
    
    def test_should_handle_missing_detector_field_as_all(self):
        """
        Given: Config without detector field
        When: _build_allowed_entities_from_database is called
        Then: Config should be treated as detector=ALL and included
        
        Business Rule: Missing detector field defaults to ALL for backward compatibility.
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,
                # detector field missing - should default to 'ALL'
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email'
            }
        }
        
        # Act
        allowed_entities = self.detector._build_allowed_entities_from_database(db_configs)
        
        # Assert
        assert len(allowed_entities) == 1
        assert 'EMAIL_ADDRESS' in allowed_entities, "Config without detector field should be included (default ALL)"
    
    def test_should_skip_configs_without_detector_label(self):
        """
        Given: Enabled config without detector_label
        When: _build_allowed_entities_from_database is called
        Then: Config should be skipped with warning log
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,
                'detector': 'PRESIDIO',
                # detector_label missing
                'display_name': 'Email'
            }
        }
        
        # Act
        allowed_entities = self.detector._build_allowed_entities_from_database(db_configs)
        
        # Assert
        assert len(allowed_entities) == 0, "Config without detector_label should be skipped"
    
    def test_should_build_scoring_overrides_only_from_presidio_and_all_configs(self):
        """
        Given: Database configs with mixed detector values
        When: _build_scoring_overrides_from_database is called
        Then: Only configs with detector=PRESIDIO or detector=ALL should be included
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.35,
                'detector': 'PRESIDIO',  # Should be included
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email'
            },
            'PHONE_NUMBER': {
                'enabled': True,
                'threshold': 0.55,
                'detector': 'GLINER',  # Should be EXCLUDED
                'detector_label': 'phone',
                'display_name': 'Phone'
            },
            'CREDIT_CARD': {
                'enabled': True,
                'threshold': 0.75,
                'detector': 'ALL',  # Should be included
                'detector_label': 'CREDIT_CARD',
                'display_name': 'Credit Card'
            },
            'IP_ADDRESS': {
                'enabled': True,
                'threshold': 0.65,
                'detector': 'REGEX',  # Should be EXCLUDED
                'detector_label': 'IP_ADDRESS',
                'display_name': 'IP'
            }
        }
        
        # Act
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert
        assert len(scoring_overrides) == 2, (
            f"Expected 2 thresholds (1 PRESIDIO + 1 ALL), got {len(scoring_overrides)}"
        )
        assert 'EMAIL_ADDRESS' in scoring_overrides
        assert scoring_overrides['EMAIL_ADDRESS'] == 0.35
        assert 'CREDIT_CARD' in scoring_overrides
        assert scoring_overrides['CREDIT_CARD'] == 0.75
        
        # Should NOT include GLINER or REGEX
        assert 'phone' not in scoring_overrides, "GLINER config should be excluded"
        assert 'IP_ADDRESS' not in scoring_overrides, "REGEX config should be excluded"
    
    def test_should_exclude_disabled_configs_from_scoring_overrides(self):
        """
        Given: Disabled configs for PRESIDIO and ALL
        When: _build_scoring_overrides_from_database is called
        Then: Disabled configs should not create scoring overrides
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': False,  # Disabled
                'threshold': 0.35,
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email'
            },
            'CREDIT_CARD': {
                'enabled': True,  # Enabled
                'threshold': 0.75,
                'detector': 'PRESIDIO',
                'detector_label': 'CREDIT_CARD',
                'display_name': 'Credit Card'
            }
        }
        
        # Act
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert
        assert len(scoring_overrides) == 1
        assert 'CREDIT_CARD' in scoring_overrides
        assert 'EMAIL_ADDRESS' not in scoring_overrides, "Disabled config should not create override"
    
    def test_should_skip_scoring_for_configs_without_threshold(self):
        """
        Given: Config without threshold value
        When: _build_scoring_overrides_from_database is called
        Then: Config should be skipped (no scoring override created)
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS',
                # threshold missing
                'display_name': 'Email'
            }
        }
        
        # Act
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert
        assert len(scoring_overrides) == 0, "Config without threshold should not create override"
    
    def test_should_skip_scoring_for_configs_without_detector_label(self):
        """
        Given: Config without detector_label but with threshold
        When: _build_scoring_overrides_from_database is called
        Then: Config should be skipped (cannot map threshold without label)
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.35,
                'detector': 'PRESIDIO',
                # detector_label missing
                'display_name': 'Email'
            }
        }
        
        # Act
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert
        assert len(scoring_overrides) == 0, "Config without detector_label should be skipped"
    
    def test_should_convert_threshold_to_float(self):
        """
        Given: Config with threshold as string or int
        When: _build_scoring_overrides_from_database is called
        Then: Threshold should be converted to float
        """
        # Arrange
        db_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': '0.35',  # String
                'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS',
                'display_name': 'Email'
            },
            'PHONE_NUMBER': {
                'enabled': True,
                'threshold': 1,  # Integer
                'detector': 'ALL',
                'detector_label': 'PHONE_NUMBER',
                'display_name': 'Phone'
            }
        }
        
        # Act
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert
        assert isinstance(scoring_overrides['EMAIL_ADDRESS'], float)
        assert scoring_overrides['EMAIL_ADDRESS'] == 0.35
        assert isinstance(scoring_overrides['PHONE_NUMBER'], float)
        assert scoring_overrides['PHONE_NUMBER'] == 1.0
    
    def test_integration_scenario_mixed_detectors(self):
        """
        Integration test with realistic mixed detector configuration.
        
        Simulates a database with:
        - 5 GLINER configs (should be excluded)
        - 3 PRESIDIO configs (should be included)
        - 2 REGEX configs (should be excluded)
        - 4 ALL configs (should be included)
        """
        # Arrange
        db_configs = {
            # GLINER configs - should be excluded
            'EMAIL_GLINER': {
                'enabled': True, 'threshold': 0.3, 'detector': 'GLINER',
                'detector_label': 'email', 'display_name': 'Email (GLiNER)'
            },
            'PHONE_GLINER': {
                'enabled': True, 'threshold': 0.5, 'detector': 'GLINER',
                'detector_label': 'phone', 'display_name': 'Phone (GLiNER)'
            },
            'PERSON_GLINER': {
                'enabled': True, 'threshold': 0.4, 'detector': 'GLINER',
                'detector_label': 'person', 'display_name': 'Person (GLiNER)'
            },
            
            # PRESIDIO configs - should be included
            'EMAIL_PRESIDIO': {
                'enabled': True, 'threshold': 0.35, 'detector': 'PRESIDIO',
                'detector_label': 'EMAIL_ADDRESS', 'display_name': 'Email (Presidio)'
            },
            'CREDIT_CARD_PRESIDIO': {
                'enabled': True, 'threshold': 0.75, 'detector': 'PRESIDIO',
                'detector_label': 'CREDIT_CARD', 'display_name': 'Credit Card (Presidio)'
            },
            'US_SSN_PRESIDIO': {
                'enabled': True, 'threshold': 0.85, 'detector': 'PRESIDIO',
                'detector_label': 'US_SSN', 'display_name': 'US SSN (Presidio)'
            },
            
            # REGEX configs - should be excluded
            'IP_REGEX': {
                'enabled': True, 'threshold': 0.6, 'detector': 'REGEX',
                'detector_label': r'\d+\.\d+\.\d+\.\d+', 'display_name': 'IP (Regex)'
            },
            'MAC_REGEX': {
                'enabled': True, 'threshold': 0.7, 'detector': 'REGEX',
                'detector_label': r'([0-9A-Fa-f]{2}[:-]){5}', 'display_name': 'MAC (Regex)'
            },
            
            # ALL configs - should be included
            'LOCATION_ALL': {
                'enabled': True, 'threshold': 0.45, 'detector': 'ALL',
                'detector_label': 'LOCATION', 'display_name': 'Location (All)'
            },
            'DATE_ALL': {
                'enabled': True, 'threshold': 0.5, 'detector': 'ALL',
                'detector_label': 'DATE_TIME', 'display_name': 'Date (All)'
            },
            'AGE_ALL': {
                'enabled': False, 'threshold': 0.4, 'detector': 'ALL',  # Disabled
                'detector_label': 'AGE', 'display_name': 'Age (All)'
            },
            'URL_ALL': {
                'enabled': True, 'threshold': 0.3, 'detector': 'ALL',
                'detector_label': 'URL', 'display_name': 'URL (All)'
            }
        }
        
        # Act
        allowed_entities = self.detector._build_allowed_entities_from_database(db_configs)
        scoring_overrides = self.detector._build_scoring_overrides_from_database(db_configs)
        
        # Assert allowed_entities
        # Should have: 3 PRESIDIO + 3 enabled ALL = 6 total (AGE_ALL is disabled)
        assert len(allowed_entities) == 6, (
            f"Expected 6 entities (3 PRESIDIO + 3 ALL enabled), got {len(allowed_entities)}"
        )
        
        # Check PRESIDIO entities present
        assert 'EMAIL_ADDRESS' in allowed_entities
        assert 'CREDIT_CARD' in allowed_entities
        assert 'US_SSN' in allowed_entities
        
        # Check ALL entities present (except disabled AGE)
        assert 'LOCATION' in allowed_entities
        assert 'DATE_TIME' in allowed_entities
        assert 'URL' in allowed_entities
        assert 'AGE' not in allowed_entities, "Disabled AGE should be excluded"
        
        # Check GLINER entities excluded
        assert 'email' not in allowed_entities  # GLiNER label
        assert 'phone' not in allowed_entities
        assert 'person' not in allowed_entities
        
        # Check REGEX entities excluded
        assert r'\d+\.\d+\.\d+\.\d+' not in allowed_entities
        
        # Assert scoring_overrides (same 6 entities)
        assert len(scoring_overrides) == 6
        assert scoring_overrides['EMAIL_ADDRESS'] == 0.35
        assert scoring_overrides['CREDIT_CARD'] == 0.75
        assert scoring_overrides['US_SSN'] == 0.85
        assert scoring_overrides['LOCATION'] == 0.45
        assert scoring_overrides['DATE_TIME'] == 0.5
        assert scoring_overrides['URL'] == 0.3
        
        # Check GLINER and REGEX not in scoring
        assert 'email' not in scoring_overrides
        assert 'phone' not in scoring_overrides
        assert r'\d+\.\d+\.\d+\.\d+' not in scoring_overrides


class TestPresidioDetectorIntegration:
    """Integration tests for Presidio detector with database configuration."""
    
    def test_should_use_filtered_config_in_detect_pii(self):
        """
        End-to-end test: Verify that detect_pii uses filtered database config.
        
        This test requires database and Presidio analyzer, so it's more of an integration test.
        We'll mock the analyzer to verify it's called with correct entity whitelist.
        """
        # This is a placeholder for a future integration test
        # that would verify the full flow with a real database
        pass
