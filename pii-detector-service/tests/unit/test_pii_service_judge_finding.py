"""Unit tests for the ``JudgeFinding`` RPC.

The RPC relays one finding to the LLM judge of the detector and never fails the
call: a detector without the capability or a crashing judge is reported as an
``ERROR`` verdict with the technical reason in ``error``.
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


def _request(**overrides):
    fields = {
        "pii_type": "IBAN", "type_label": "IBAN", "value": "CH93 0076",
        "context": "Compte : CH93 0076", "lm_studio_model": "judge@q8",
        "lm_studio_host": "lmstudio", "lm_studio_port": 1234,
    }
    fields.update(overrides)
    return pii_detection_pb2.JudgeFindingRequest(**fields)


class TestJudgeFinding:
    def test_Should_RelayVerdict_When_DetectorAnswers(self):
        detector = MagicMock()
        detector.judge_finding.return_value = {
            "verdict": "FALSE_POSITIVE", "confidence": 0.91, "reason": "motif (b)", "error": "",
        }
        servicer = _servicer(detector)

        response = servicer.JudgeFinding(_request(), MagicMock())

        detector.judge_finding.assert_called_once_with(
            pii_type="IBAN", type_label="IBAN", value="CH93 0076",
            context="Compte : CH93 0076", lm_studio_model="judge@q8",
            lm_studio_host="lmstudio", lm_studio_port=1234,
        )
        assert response.verdict == "FALSE_POSITIVE"
        assert abs(response.confidence - 0.91) < 1e-6
        assert response.reason == "motif (b)"
        assert response.error == ""

    def test_Should_PassNoneModel_When_RequestModelEmpty(self):
        detector = MagicMock()
        detector.judge_finding.return_value = {"verdict": "UNSURE", "confidence": 0.3, "reason": "?"}
        servicer = _servicer(detector)

        servicer.JudgeFinding(_request(lm_studio_model=""), MagicMock())

        assert detector.judge_finding.call_args.kwargs["lm_studio_model"] is None

    def test_Should_ReportError_When_DetectorCannotJudge(self):
        detector = MagicMock(spec=[])
        servicer = _servicer(detector)

        response = servicer.JudgeFinding(_request(), MagicMock())

        assert response.verdict == "ERROR"
        assert response.error == "detector does not support judging"

    def test_Should_ReportError_When_JudgeRaises(self):
        detector = MagicMock()
        detector.judge_finding.side_effect = RuntimeError("boom")
        servicer = _servicer(detector)

        response = servicer.JudgeFinding(_request(), MagicMock())

        assert response.verdict == "ERROR"
        assert response.error == "RuntimeError: boom"
