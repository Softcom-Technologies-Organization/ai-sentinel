"""``MinistralDetector.list_models``: the family filter and its ``all_models`` bypass."""
from __future__ import annotations

from unittest.mock import patch

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
