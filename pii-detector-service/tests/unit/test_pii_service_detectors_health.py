"""Unit tests for the ``CheckDetectorsHealth`` RPC.

The RPC answers the pre-flight question "will the detectors the operator enabled
really contribute?". It must report an unreachable detector rather than let a scan
run and produce a report that looks clean while a detector never ran.
"""

from __future__ import annotations

import importlib
from unittest.mock import MagicMock

from pii_detector.domain.entity.detector_failure import DetectorFailureCode
from pii_detector.domain.entity.detector_source import DetectorSource

pii_service = importlib.import_module(
    "pii_detector.infrastructure.adapter.in.grpc.pii_service"
)
PIIDetectionServicer = pii_service.PIIDetectionServicer
pii_detection_pb2 = pii_service.pii_detection_pb2


def _servicer(detector: MagicMock) -> PIIDetectionServicer:
    """Build a servicer around a stub detector, bypassing __init__ side effects.

    ``__init__`` builds the singleton detector, forks the worker pool and starts
    background threads; none of that is relevant to the health RPC.
    """
    servicer = PIIDetectionServicer.__new__(PIIDetectionServicer)
    servicer.detector = detector
    servicer.request_counter = 0
    return servicer


class TestCheckDetectorsHealth:
    def test_Should_ReportUnreachableDetector_When_ProbeFails(self):
        detector = MagicMock()
        detector.check_detectors_health.return_value = [{
            "source": DetectorSource.MINISTRAL,
            "reachable": False,
            "endpoint": "http://lmstudio:1234/v1",
            "error": "ConnectError: connection refused",
            "error_code": DetectorFailureCode.ENDPOINT_UNREACHABLE.value,
            "error_params": {"cause": "ConnectError: connection refused"},
        }]
        servicer = _servicer(detector)
        servicer._fetch_detector_flags = lambda _request_id: {
            "regex_enabled": False,
            "presidio_enabled": False,
            "ministral_enabled": True,
            "lm_studio_host": "lmstudio",
            "lm_studio_port": 1234,
        }

        response = servicer.CheckDetectorsHealth(
            pii_detection_pb2.DetectorsHealthRequest(), MagicMock()
        )

        assert len(response.detectors) == 1
        entry = response.detectors[0]
        assert entry.source == pii_detection_pb2.DetectorSource.MINISTRAL
        assert entry.reachable is False
        assert entry.endpoint == "http://lmstudio:1234/v1"
        assert "connection refused" in entry.error
        # The code and its values are what the dashboard translates; the English
        # sentence above never reaches the operator.
        assert entry.error_code == DetectorFailureCode.ENDPOINT_UNREACHABLE.value
        assert entry.error_params["cause"] == "ConnectError: connection refused"

    def test_Should_ForwardConfiguredFlagsAndEndpoint_When_Probing(self):
        detector = MagicMock()
        detector.check_detectors_health.return_value = []
        servicer = _servicer(detector)
        servicer._fetch_detector_flags = lambda _request_id: {
            "regex_enabled": True,
            "presidio_enabled": False,
            "ministral_enabled": True,
            "lm_studio_host": "host.docker.internal",
            "lm_studio_port": 4321,
        }

        servicer.CheckDetectorsHealth(
            pii_detection_pb2.DetectorsHealthRequest(), MagicMock()
        )

        detector.check_detectors_health.assert_called_once_with(
            enable_regex=True,
            enable_presidio=False,
            enable_ministral=True,
            lm_studio_host="host.docker.internal",
            lm_studio_port=4321,
        )

    def test_Should_ReportNothing_When_ConfigurationUnreadable(self):
        """Neither "all healthy" (a lie) nor "all down" (blocks scans over a DB blip)."""
        detector = MagicMock()
        servicer = _servicer(detector)
        servicer._fetch_detector_flags = lambda _request_id: None

        response = servicer.CheckDetectorsHealth(
            pii_detection_pb2.DetectorsHealthRequest(), MagicMock()
        )

        assert list(response.detectors) == []
        detector.check_detectors_health.assert_not_called()
