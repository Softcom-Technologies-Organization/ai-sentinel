"""Unit tests for the ``ListLmStudioModels`` RPC.

The RPC feeds the dashboard's model picker with the Ministral-PII quantizations LM
Studio has on disk, as seen from this service. It never fails the call: an
unreachable endpoint or a detector without the capability is reported in
``error`` with an empty list.
"""
from __future__ import annotations

import importlib
from unittest.mock import MagicMock

pii_service = importlib.import_module(
    "pii_detector.infrastructure.adapter.in.grpc.pii_service"
)
PIIDetectionServicer = pii_service.PIIDetectionServicer
pii_detection_pb2 = pii_service.pii_detection_pb2


def _servicer(detector) -> PIIDetectionServicer:
    servicer = PIIDetectionServicer.__new__(PIIDetectionServicer)
    servicer.detector = detector
    servicer.request_counter = 0
    return servicer


def _listing(**overrides):
    listing = {
        "endpoint": "http://lmstudio:1234/v1",
        "family": "ministral-3b-pii-preview",
        "models": [
            {"id": "ministral-3b-pii-preview@q8_0", "quantization": "Q8_0",
             "publisher": "mradermacher", "state": "loaded"},
            {"id": "ministral-3b-pii-preview@q4_k_m", "quantization": "Q4_K_M",
             "publisher": "mradermacher", "state": "not-loaded"},
        ],
        "error": "",
    }
    listing.update(overrides)
    return listing


class TestListLmStudioModels:
    def test_Should_ListModelsOfRequestedEndpoint_When_HostAndPortGiven(self):
        detector = MagicMock()
        detector.list_lm_studio_models.return_value = _listing()
        servicer = _servicer(detector)

        response = servicer.ListLmStudioModels(
            pii_detection_pb2.LmStudioModelsRequest(lm_studio_host="lmstudio", lm_studio_port=1234),
            MagicMock(),
        )

        detector.list_lm_studio_models.assert_called_once_with(
            lm_studio_host="lmstudio", lm_studio_port=1234
        )
        assert response.endpoint == "http://lmstudio:1234/v1"
        assert response.family == "ministral-3b-pii-preview"
        assert response.error == ""
        assert [m.id for m in response.models] == [
            "ministral-3b-pii-preview@q8_0", "ministral-3b-pii-preview@q4_k_m",
        ]
        assert response.models[0].quantization == "Q8_0"
        assert response.models[0].publisher == "mradermacher"
        assert response.models[1].state == "not-loaded"

    def test_Should_FallBackToConfiguredEndpoint_When_RequestIsEmpty(self):
        detector = MagicMock()
        detector.list_lm_studio_models.return_value = _listing(models=[])
        servicer = _servicer(detector)
        servicer._fetch_detector_flags = lambda _request_id: {
            "lm_studio_host": "host.docker.internal",
            "lm_studio_port": 4321,
        }

        servicer.ListLmStudioModels(pii_detection_pb2.LmStudioModelsRequest(), MagicMock())

        detector.list_lm_studio_models.assert_called_once_with(
            lm_studio_host="host.docker.internal", lm_studio_port=4321
        )

    def test_Should_ReportError_When_DetectorCannotListModels(self):
        servicer = _servicer(object())

        response = servicer.ListLmStudioModels(
            pii_detection_pb2.LmStudioModelsRequest(lm_studio_host="lmstudio", lm_studio_port=1234),
            MagicMock(),
        )

        assert len(response.models) == 0
        assert "does not support" in response.error

    def test_Should_ReportError_When_ListingRaises(self):
        detector = MagicMock()
        detector.list_lm_studio_models.side_effect = RuntimeError("boom")
        servicer = _servicer(detector)

        response = servicer.ListLmStudioModels(
            pii_detection_pb2.LmStudioModelsRequest(lm_studio_host="lmstudio", lm_studio_port=1234),
            MagicMock(),
        )

        assert len(response.models) == 0
        assert response.error == "RuntimeError: boom"

    def test_Should_ForwardListingError_When_EndpointUnreachable(self):
        detector = MagicMock()
        detector.list_lm_studio_models.return_value = _listing(
            models=[], error="LM Studio model list unavailable at http://lmstudio:1234/v1"
        )
        servicer = _servicer(detector)

        response = servicer.ListLmStudioModels(
            pii_detection_pb2.LmStudioModelsRequest(lm_studio_host="lmstudio", lm_studio_port=1234),
            MagicMock(),
        )

        assert len(response.models) == 0
        assert "unavailable" in response.error
