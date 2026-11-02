"""Unit tests for ``MinistralDetector.judge_finding``: transport reuse and fail-open."""
from __future__ import annotations

import json
from unittest.mock import MagicMock

import httpx

from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector


def _detector(post) -> MinistralDetector:
    detector = MinistralDetector()
    client = MagicMock()
    client.post = MagicMock(side_effect=post)
    detector._client = client
    return detector


def _completion(verdict: dict) -> MagicMock:
    response = MagicMock()
    response.raise_for_status = MagicMock()
    response.json = MagicMock(return_value={
        "choices": [{"message": {"content": json.dumps(verdict)}}],
    })
    return response


class TestJudgeFinding:
    def test_Should_PostToOverriddenEndpointWithModel_When_Given(self):
        calls = []

        def post(url, json, headers):
            calls.append((url, json))
            return _completion({"reason": "ok", "confidence": 0.97, "verdict": "TRUE_POSITIVE"})

        answer = _detector(post).judge_finding(
            "EMAIL", "Email", "a@b.ch", "mail a@b.ch",
            lm_studio_model="judge@q8", lm_studio_host="lmstudio", lm_studio_port=4321,
        )

        url, payload = calls[0]
        assert url == "http://lmstudio:4321/v1/chat/completions"
        assert payload["model"] == "judge@q8"
        assert "contexte   : mail >>>a@b.ch<<<" in payload["messages"][1]["content"]
        assert answer == {"verdict": "TRUE_POSITIVE", "confidence": 0.97, "reason": "ok", "error": ""}

    def test_Should_FallBackToQwenJudge_When_NoModelGiven(self):
        calls = []

        def post(url, json, headers):
            calls.append(json)
            return _completion({"reason": "ok", "confidence": 0.5, "verdict": "UNSURE"})

        detector = _detector(post)
        detector.judge_finding("EMAIL", "Email", "a@b.ch", "ctx")

        assert calls[0]["model"] == "qwen3.8-flash-next-iq3_s"

    def test_Should_ReturnErrorVerdict_When_EndpointUnreachable(self):
        def post(url, json, headers):
            raise httpx.ConnectError("connection refused")

        answer = _detector(post).judge_finding("EMAIL", "Email", "a@b.ch", "ctx")

        assert answer["verdict"] == "ERROR"
        assert answer["error"].startswith("ConnectError")

    def test_Should_ReturnErrorVerdict_When_BodyHasNoChoices(self):
        response = MagicMock()
        response.raise_for_status = MagicMock()
        response.json = MagicMock(return_value={"error": "model not loaded"})

        answer = _detector(lambda url, **kwargs: response).judge_finding("EMAIL", "Email", "a@b.ch", "ctx")

        assert answer["verdict"] == "ERROR"
        assert "DetectorUnavailableError" in answer["error"]

    def test_Should_AuthenticateJudge_When_StrataKeyConfigured(self, monkeypatch):
        monkeypatch.setenv("LLM_JUDGE_API_KEY", "synthetic-test-key")
        response = _completion({"reason": "ok", "confidence": 1.0, "verdict": "TRUE_POSITIVE"})
        detector = _detector(None)
        detector._client.post.return_value = response

        detector.judge_finding("PASSWORD", "Password", "secret", "Password: secret")

        assert detector._client.post.call_args.kwargs["headers"] == {
            "Authorization": "Bearer synthetic-test-key",
        }
