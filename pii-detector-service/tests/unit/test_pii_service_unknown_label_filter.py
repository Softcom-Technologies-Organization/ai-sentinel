"""
Unit tests for the MINISTRAL unknown-label filter in the gRPC service.

Business Rule: Ministral is an open-vocabulary model. A label it emits that has
no ``pii_type_config`` row is dropped from findings. Every other source keeps
the historical allow-by-default behaviour.
"""

import importlib

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity

# Import workaround: "in" is a Python reserved keyword, so the module path
# cannot be reached with a plain import statement.
pii_service_module = importlib.import_module('pii_detector.infrastructure.adapter.in.grpc.pii_service')
PIIDetectionServicer = pii_service_module.PIIDetectionServicer


def _ministral_entity(pii_type: str, score: float = 0.9) -> PIIEntity:
    return PIIEntity(
        text='value',
        pii_type=pii_type,
        type_label=pii_type,
        start=0,
        end=5,
        score=score,
        source=DetectorSource.MINISTRAL,
    )


class TestUnknownMinistralLabelFilter:
    """Test dropping of unconfigured MINISTRAL labels in _filter_entities_by_type_config."""

    def setup_method(self):
        """Setup test fixtures."""
        self.servicer = PIIDetectionServicer()
        self.request_id = "test_req_unknown_label"
        # Non-empty configs without the fabricated labels: the method returns
        # early when configs are empty, so an unrelated entry must be present.
        self.unrelated_configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,
                'detector': 'ALL',
                'detector_label': 'email',
            }
        }

    def test_Should_Drop_When_MinistralLabelUnconfigured(self):
        entities = [_ministral_entity('FAVOURITE_COLOR')]

        filtered = self.servicer._filter_entities_by_type_config(
            entities, self.unrelated_configs, self.request_id,
        )

        assert len(filtered) == 0, "Unconfigured MINISTRAL label must be dropped"

    def test_Should_Keep_When_MinistralLabelConfigured(self):
        entities = [_ministral_entity('EMAIL', score=0.9)]
        configs = {
            'EMAIL': {
                'enabled': True,
                'threshold': 0.3,
                'detector': 'MINISTRAL',
                'detector_label': 'email',
            }
        }

        filtered = self.servicer._filter_entities_by_type_config(
            entities, configs, self.request_id,
        )

        assert len(filtered) == 1, "Configured, enabled MINISTRAL label must be kept"

    def test_Should_KeepNonMinistral_When_NoConfig(self):
        entities = [
            PIIEntity(
                text='10.217.4.11',
                pii_type='IP_ADDRESS',
                type_label='IP_ADDRESS',
                start=0,
                end=11,
                score=0.9,
                source=DetectorSource.PRESIDIO,
            )
        ]

        filtered = self.servicer._filter_entities_by_type_config(
            entities, self.unrelated_configs, self.request_id,
        )

        assert len(filtered) == 1, "Non-MINISTRAL entity keeps allow-by-default"
        assert filtered[0]['source'] == DetectorSource.PRESIDIO

    def test_Should_DropEveryOccurrence_When_SameUnknownMinistralLabelRepeats(self):
        entities = [
            _ministral_entity('FAVOURITE_COLOR'),
            _ministral_entity('FAVOURITE_COLOR'),
        ]

        filtered = self.servicer._filter_entities_by_type_config(
            entities, self.unrelated_configs, self.request_id,
        )

        assert len(filtered) == 0
