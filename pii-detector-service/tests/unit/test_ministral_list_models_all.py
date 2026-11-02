"""``MinistralDetector.list_models``: the family filter and its ``all_models`` bypass."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector

ON_DISK = [
    {"id": "ministral-3b-pii-preview@q8_0", "type": "llm", "state": "loaded"},
    {"id": "openai/gpt-oss-120b", "type": "llm", "state": "loaded"},
    {"id": "text-embedding-nomic-embed-text-v1.5", "type": "embeddings", "state": "not-loaded"},
]


class TestListModelsFamilyFilter:
    def test_Should_KeepMinistralFamilyOnly_When_ListingForDetection(self):
        detector = MinistralDetector()
        with patch.object(detector, "_fetch_lm_studio_models", return_value=ON_DISK):
            listing = detector.list_models("lmstudio", 1234)

        assert [m["id"] for m in listing["models"]] == ["ministral-3b-pii-preview@q8_0"]

    def test_Should_ListEveryChatModel_When_AllModelsRequested(self):
        detector = MinistralDetector()
        with patch.object(detector, "_fetch_lm_studio_models", return_value=ON_DISK):
            listing = detector.list_models("lmstudio", 1234, all_models=True)

        assert [m["id"] for m in listing["models"]] == [
            "ministral-3b-pii-preview@q8_0", "openai/gpt-oss-120b",
        ]

    def test_Should_ListStrataJudge_When_LmStudioEndpointUnavailable(self, monkeypatch):
        monkeypatch.setenv("LLM_JUDGE_API_KEY", "synthetic-test-key")
        detector = MinistralDetector()
        detector._client = MagicMock()
        detector._client.get.return_value.json.return_value = {
            "data": [{"id": "qwen3.8-flash-next-iq3_s", "object": "model", "status": {"value": "loaded"}}],
        }
        with patch.object(detector, "_fetch_lm_studio_models", return_value=None):
            listing = detector.list_models("strata", 8081, all_models=True)

        assert [model["id"] for model in listing["models"]] == ["qwen3.8-flash-next-iq3_s"]
        assert listing["models"][0]["state"] == "loaded"
        assert detector._client.get.call_args.args[0] == "http://strata:8081/v1/models"
        assert detector._client.get.call_args.kwargs["headers"] == {
            "Authorization": "Bearer synthetic-test-key",
        }

    def test_Should_NotUseJudgeCredentials_When_ListingDetectionModels(self, monkeypatch):
        monkeypatch.setenv("LLM_JUDGE_API_KEY", "synthetic-test-key")
        detector = MinistralDetector()
        detector._client = MagicMock()
        with patch.object(detector, "_fetch_lm_studio_models", return_value=None):
            listing = detector.list_models("lmstudio", 1234)

        assert listing["models"] == []
        detector._client.get.assert_not_called()
