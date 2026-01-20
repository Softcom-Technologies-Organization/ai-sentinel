"""
Unit tests for detector-specific PII type filtering in gRPC service.

Business Rule: PII type configs with a specific detector (GLINER, PRESIDIO, REGEX)
should only apply to entities detected by that detector, not to entities from other sources.
"""

import importlib

# Import workaround: "in" is a Python reserved keyword in the module path
# pii_detector.infrastructure.adapter.in.grpc.pii_service
pii_service_module = importlib.import_module('pii_detector.infrastructure.adapter.in.grpc.pii_service')
PIIDetectionServicer = pii_service_module.PIIDetectionServicer


class TestDetectorSpecificFiltering:
    """Test detector-specific filtering logic in _filter_entities_by_type_config."""
    
    def setup_method(self):
        """Setup test fixtures."""
        self.servicer = PIIDetectionServicer()
        self.request_id = "test_req_001"
    
    def test_should_keep_presidio_email_when_gliner_email_disabled(self):
        """
        BUG REPRODUCTION TEST
        
        Given: EMAIL config with enabled=False, detector=GLINER
        When: An EMAIL is detected by PRESIDIO with score=1.0
        Then: The EMAIL should be KEPT (config doesn't apply to PRESIDIO)
        
        Business Rule: Detector-specific configs only apply to their target detector.
        """
        # Arrange
        entities = [
            {
                'text': 'marie.dupont@example.fr',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 0,
                'end': 24,
                'score': 1.0,
                'source': 'PRESIDIO'  # Detected by PRESIDIO
            }
        ]
        
        pii_type_configs = {
            'EMAIL': {
                'enabled': False,  # Disabled
                'threshold': 0.3,
                'detector': 'GLINER',  # But only for GLINER
                'detector_label': 'email',
                'display_name': 'Email Address',
                'category': 'contact',
                'country_code': None,
                'description': 'Email address'
            }
        }
        
        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )
        
        # Assert
        assert len(filtered) == 1, (
            "EMAIL detected by PRESIDIO should be KEPT when only GLINER detector is disabled. "
            f"Expected 1 entity, got {len(filtered)}"
        )
        assert filtered[0]['text'] == 'marie.dupont@example.fr'
        assert filtered[0]['source'] == 'PRESIDIO'
    
    def test_should_filter_gliner_email_when_gliner_email_disabled(self):
        """
        Given: EMAIL config with enabled=False, detector=GLINER
        When: An EMAIL is detected by GLINER
        Then: The EMAIL should be FILTERED OUT (config applies to GLINER)
        """
        # Arrange
        entities = [
            {
                'text': 'john.doe@test.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 0,
                'end': 18,
                'score': 0.95,
                'source': 'GLINER'  # Detected by GLINER
            }
        ]
        
        pii_type_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.3,
                'detector': 'GLINER',
                'detector_label': 'email',
                'display_name': 'Email Address',
                'category': 'contact',
                'country_code': None,
                'description': 'Email address'
            }
        }
        
        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )
        
        # Assert
        assert len(filtered) == 0, (
            "EMAIL detected by GLINER should be FILTERED when GLINER detector is disabled. "
            f"Expected 0 entities, got {len(filtered)}"
        )
    
    def test_should_filter_all_detectors_when_config_detector_is_all(self):
        """
        Given: EMAIL config with enabled=False, detector=ALL
        When: EMAILS are detected by multiple detectors (GLINER, PRESIDIO, REGEX)
        Then: ALL EMAILS should be FILTERED OUT
        """
        # Arrange
        entities = [
            {
                'text': 'gliner@example.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 0,
                'end': 18,
                'score': 0.9,
                'source': 'GLINER'
            },
            {
                'text': 'presidio@example.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 20,
                'end': 40,
                'score': 1.0,
                'source': 'PRESIDIO'
            },
            {
                'text': 'regex@example.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 42,
                'end': 59,
                'score': 0.8,
                'source': 'REGEX'
            }
        ]
        
        pii_type_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.3,
                'detector': 'ALL',  # Applies to ALL detectors
                'detector_label': 'email',
                'display_name': 'Email Address',
                'category': 'contact',
                'country_code': None,
                'description': 'Email address'
            }
        }
        
        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )
        
        # Assert
        assert len(filtered) == 0, (
            "All EMAILS should be FILTERED when detector=ALL and enabled=False. "
            f"Expected 0 entities, got {len(filtered)}"
        )
    
    def test_should_keep_presidio_phone_when_gliner_phone_disabled(self):
        """
        Given: PHONE_NUMBER config with enabled=False, detector=GLINER
        When: A PHONE_NUMBER is detected by PRESIDIO
        Then: The PHONE_NUMBER should be KEPT
        """
        # Arrange
        entities = [
            {
                'text': '+33 6 12 34 56 78',
                'type': 'PHONE_NUMBER',
                'type_label': 'PHONE_NUMBER',
                'start': 0,
                'end': 18,
                'score': 1.0,
                'source': 'PRESIDIO'
            }
        ]
        
        pii_type_configs = {
            'PHONE_NUMBER': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'GLINER',
                'detector_label': 'phone',
                'display_name': 'Phone Number',
                'category': 'contact',
                'country_code': None,
                'description': 'Phone number'
            }
        }
        
        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )
        
        # Assert
        assert len(filtered) == 1
        assert filtered[0]['source'] == 'PRESIDIO'
    
    def test_should_handle_mixed_detectors_correctly(self):
        """
        Complex scenario with multiple PII types and mixed detector configs.
        
        Given:
        - EMAIL disabled for GLINER only
        - PHONE disabled for ALL
        - CREDIT_CARD enabled for ALL
        
        When: Mixed entities are detected
        Then: Apply correct filtering per detector
        """
        # Arrange
        entities = [
            {
                'text': 'email1@test.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 0,
                'end': 15,
                'score': 0.9,
                'source': 'GLINER'  # Should be filtered
            },
            {
                'text': 'email2@test.com',
                'type': 'EMAIL',
                'type_label': 'EMAIL',
                'start': 20,
                'end': 35,
                'score': 1.0,
                'source': 'PRESIDIO'  # Should be kept
            },
            {
                'text': '+33612345678',
                'type': 'PHONE_NUMBER',
                'type_label': 'PHONE_NUMBER',
                'start': 40,
                'end': 52,
                'score': 1.0,
                'source': 'PRESIDIO'  # Should be filtered (ALL)
            },
            {
                'text': '4111111111111111',
                'type': 'CREDIT_CARD',
                'type_label': 'CREDIT_CARD',
                'start': 60,
                'end': 76,
                'score': 1.0,
                'source': 'PRESIDIO'  # Should be kept (enabled)
            }
        ]
        
        pii_type_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.3,
                'detector': 'GLINER',  # Only GLINER
                'detector_label': 'email',
                'display_name': 'Email',
                'category': 'contact',
                'country_code': None,
                'description': 'Email'
            },
            'PHONE_NUMBER': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'ALL',  # ALL detectors
                'detector_label': 'phone',
                'display_name': 'Phone',
                'category': 'contact',
                'country_code': None,
                'description': 'Phone'
            },
            'CREDIT_CARD': {
                'enabled': True,
                'threshold': 0.7,
                'detector': 'ALL',
                'detector_label': 'credit_card',
                'display_name': 'Credit Card',
                'category': 'financial',
                'country_code': None,
                'description': 'Credit Card'
            }
        }
        
        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )
        
        # Assert
        assert len(filtered) == 2, f"Expected 2 entities (Presidio EMAIL + CREDIT_CARD), got {len(filtered)}"
        
        types = [e['type_label'] for e in filtered]
        assert 'EMAIL' in types, "Presidio EMAIL should be kept"
        assert 'CREDIT_CARD' in types, "CREDIT_CARD should be kept"
        assert 'PHONE_NUMBER' not in types, "PHONE should be filtered (ALL)"
        
        email_entity = next(e for e in filtered if e['type_label'] == 'EMAIL')
        assert email_entity['source'] == 'PRESIDIO', "Only Presidio EMAIL should remain"
