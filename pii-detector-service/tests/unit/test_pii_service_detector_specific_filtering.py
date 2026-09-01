"""
Unit tests for detector-specific PII type filtering in gRPC service.

Business Rule: PII type configs with a specific detector (MINISTRAL, PRESIDIO, REGEX)
should only apply to entities detected by that detector, not to entities from other sources.
"""

import importlib

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity

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

    def test_should_keep_presidio_email_when_ministral_email_disabled(self):
        """
        BUG REPRODUCTION TEST

        Given: EMAIL config with enabled=False, detector=MINISTRAL
        When: An EMAIL is detected by PRESIDIO with score=1.0
        Then: The EMAIL should be KEPT (config doesn't apply to PRESIDIO)

        Business Rule: Detector-specific configs only apply to their target detector.
        """
        # Arrange
        entities = [
            PIIEntity(
                text='marie.dupont@example.fr',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=0,
                end=24,
                score=1.0,
                source=DetectorSource.PRESIDIO
            )
        ]

        pii_type_configs = {
            'EMAIL': {
                'enabled': False,  # Disabled
                'threshold': 0.3,
                'detector': 'MINISTRAL',  # But only for MINISTRAL
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
            "EMAIL detected by PRESIDIO should be KEPT when only MINISTRAL detector is disabled. "
            f"Expected 1 entity, got {len(filtered)}"
        )
        assert filtered[0]['text'] == 'marie.dupont@example.fr'
        assert filtered[0]['source'] == DetectorSource.PRESIDIO

    def test_should_filter_ministral_email_when_ministral_email_disabled(self):
        """
        Given: EMAIL config with enabled=False, detector=MINISTRAL
        When: An EMAIL is detected by MINISTRAL
        Then: The EMAIL should be FILTERED OUT (config applies to MINISTRAL)
        """
        # Arrange
        entities = [
            PIIEntity(
                text='john.doe@test.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=0,
                end=18,
                score=0.95,
                source=DetectorSource.MINISTRAL
            )
        ]

        pii_type_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.3,
                'detector': 'MINISTRAL',
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
            "EMAIL detected by MINISTRAL should be FILTERED when MINISTRAL detector is disabled. "
            f"Expected 0 entities, got {len(filtered)}"
        )

    def test_should_filter_all_detectors_when_config_detector_is_all(self):
        """
        Given: EMAIL config with enabled=False, detector=ALL
        When: EMAILS are detected by multiple detectors (MINISTRAL, PRESIDIO, REGEX)
        Then: ALL EMAILS should be FILTERED OUT
        """
        # Arrange
        entities = [
            PIIEntity(
                text='ministral@example.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=0,
                end=18,
                score=0.9,
                source=DetectorSource.MINISTRAL
            ),
            PIIEntity(
                text='presidio@example.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=20,
                end=40,
                score=1.0,
                source=DetectorSource.PRESIDIO
            ),
            PIIEntity(
                text='regex@example.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=42,
                end=59,
                score=0.8,
                source=DetectorSource.REGEX
            )
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

    def test_should_keep_presidio_phone_when_ministral_phone_disabled(self):
        """
        Given: PHONE_NUMBER config with enabled=False, detector=MINISTRAL
        When: A PHONE_NUMBER is detected by PRESIDIO
        Then: The PHONE_NUMBER should be KEPT
        """
        # Arrange
        entities = [
            PIIEntity(
                text='+33 6 12 34 56 78',
                pii_type='PHONE_NUMBER',
                type_label='PHONE_NUMBER',
                start=0,
                end=18,
                score=1.0,
                source=DetectorSource.PRESIDIO
            )
        ]

        pii_type_configs = {
            'PHONE_NUMBER': {
                'enabled': False,
                'threshold': 0.5,
                'detector': 'MINISTRAL',
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
        assert filtered[0]['source'] == DetectorSource.PRESIDIO

    def test_should_handle_mixed_detectors_correctly(self):
        """
        Complex scenario with multiple PII types and mixed detector configs.

        Given:
        - EMAIL disabled for MINISTRAL only
        - PHONE disabled for ALL
        - CREDIT_CARD enabled for ALL

        When: Mixed entities are detected
        Then: Apply correct filtering per detector
        """
        # Arrange
        entities = [
            PIIEntity(
                text='email1@test.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=0,
                end=15,
                score=0.9,
                source=DetectorSource.MINISTRAL  # Should be filtered
            ),
            PIIEntity(
                text='email2@test.com',
                pii_type='EMAIL',
                type_label='EMAIL',
                start=20,
                end=35,
                score=1.0,
                source=DetectorSource.PRESIDIO  # Should be kept
            ),
            PIIEntity(
                text='+33612345678',
                pii_type='PHONE_NUMBER',
                type_label='PHONE_NUMBER',
                start=40,
                end=52,
                score=1.0,
                source=DetectorSource.PRESIDIO  # Should be filtered (ALL)
            ),
            PIIEntity(
                text='4111111111111111',
                pii_type='CREDIT_CARD',
                type_label='CREDIT_CARD',
                start=60,
                end=76,
                score=1.0,
                source=DetectorSource.PRESIDIO  # Should be kept (enabled)
            )
        ]

        pii_type_configs = {
            'EMAIL': {
                'enabled': False,
                'threshold': 0.3,
                'detector': 'MINISTRAL',  # Only MINISTRAL
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
        assert email_entity['source'] == DetectorSource.PRESIDIO, "Only Presidio EMAIL should remain"

    def test_should_filter_by_threshold_when_detector_matches(self):
        """
        REGRESSION TEST for the original bug (NATIONAL_ID at 45% with threshold 80%).

        Given: NATIONAL_ID config with threshold=0.80, detector=MINISTRAL
        When: A NATIONAL_ID is detected by MINISTRAL with score=0.45
        Then: The NATIONAL_ID should be FILTERED OUT (score < threshold)
        """
        # Arrange
        entities = [
            PIIEntity(
                text='12345678901',
                pii_type='NATIONAL_ID',
                type_label='NATIONAL_ID',
                start=0,
                end=11,
                score=0.45,
                source=DetectorSource.MINISTRAL
            )
        ]

        pii_type_configs = {
            'NATIONAL_ID': {
                'enabled': True,
                'threshold': 0.80,
                'detector': 'MINISTRAL',
                'detector_label': 'national identity number',
                'display_name': 'National ID',
                'category': 'identity',
                'country_code': None,
                'description': 'National identity number'
            }
        }

        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )

        # Assert
        assert len(filtered) == 0, (
            "NATIONAL_ID with score 0.45 should be FILTERED when threshold is 0.80. "
            f"Expected 0 entities, got {len(filtered)}"
        )

    def test_should_keep_entity_when_score_above_threshold(self):
        """
        Given: NATIONAL_ID config with threshold=0.80, detector=MINISTRAL
        When: A NATIONAL_ID is detected by MINISTRAL with score=0.92
        Then: The NATIONAL_ID should be KEPT (score >= threshold)
        """
        # Arrange
        entities = [
            PIIEntity(
                text='12345678901',
                pii_type='NATIONAL_ID',
                type_label='NATIONAL_ID',
                start=0,
                end=11,
                score=0.92,
                source=DetectorSource.MINISTRAL
            )
        ]

        pii_type_configs = {
            'NATIONAL_ID': {
                'enabled': True,
                'threshold': 0.80,
                'detector': 'MINISTRAL',
                'detector_label': 'national identity number',
                'display_name': 'National ID',
                'category': 'identity',
                'country_code': None,
                'description': 'National identity number'
            }
        }

        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id
        )

        # Assert
        assert len(filtered) == 1, (
            "NATIONAL_ID with score 0.92 should be KEPT when threshold is 0.80. "
            f"Expected 1 entity, got {len(filtered)}"
        )
        assert filtered[0]['source'] == DetectorSource.MINISTRAL

    def test_Should_DropMinistralLabel_When_TypeOnlyConfiguredDisabledForOtherDetector(self):
        """
        BUG REPRODUCTION (live): a disabled type leaks in via Ministral passthrough.

        Given: LOCATION is configured only for PRESIDIO with enabled=False (the
               production seed shape, with the composite ``PRESIDIO:LOCATION`` key
               the ALL-detector fetch adds alongside the plain key).
        When:  Ministral emits LOCATION via open-vocabulary passthrough.
        Then:  The LOCATION must be DROPPED — not kept via the detector-mismatch
               allow-by-default path.
        """
        # Arrange — mirror the DatabaseConfigAdapter ALL-detector dict shape:
        # both a plain key and the composite per-detector key point to the row.
        presidio_location = {
            'enabled': False,
            'threshold': 0.75,
            'detector': 'PRESIDIO',
            'detector_label': 'LOCATION',
            'category': 'Location',
            'country_code': None,
        }
        pii_type_configs = {
            'LOCATION': presidio_location,
            'PRESIDIO:LOCATION': presidio_location,
        }
        entities = [
            PIIEntity(
                text='46.5197, 6.6323',
                pii_type='LOCATION',
                type_label='LOCATION',
                start=0,
                end=15,
                score=1.0,
                source=DetectorSource.MINISTRAL,
            )
        ]

        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id,
        )

        # Assert
        assert len(filtered) == 0, (
            "MINISTRAL LOCATION must be dropped when LOCATION is only configured "
            f"(disabled) for PRESIDIO. Expected 0 entities, got {len(filtered)}"
        )

    def test_Should_KeepPresidioType_When_OnlyConfiguredDisabledForMinistral(self):
        """Symmetric guard: a non-Ministral source keeps allow-by-default when the
        type is only configured for another (Ministral) detector."""
        # Arrange
        ministral_only = {
            'enabled': False,
            'threshold': 0.5,
            'detector': 'MINISTRAL',
            'detector_label': 'city',
            'category': 'Location',
            'country_code': None,
        }
        pii_type_configs = {
            'CITY': ministral_only,
            'MINISTRAL:CITY': ministral_only,
        }
        entities = [
            PIIEntity(
                text='Lausanne',
                pii_type='CITY',
                type_label='CITY',
                start=0,
                end=8,
                score=1.0,
                source=DetectorSource.PRESIDIO,
            )
        ]

        # Act
        filtered = self.servicer._filter_entities_by_type_config(
            entities, pii_type_configs, self.request_id,
        )

        # Assert
        assert len(filtered) == 1
        assert filtered[0]['source'] == DetectorSource.PRESIDIO
