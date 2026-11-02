import hashlib
import json
from pathlib import Path

import pytest

from pii_detector.infrastructure.judge.llm_finding_judge import build_payload


PROMPT_HASHES = json.loads(
    (Path(__file__).parents[1] / "resources/judge-per-type-v1.json").read_text()
)


@pytest.mark.parametrize("pii_type,expected_hash", PROMPT_HASHES.items())
def test_Should_MatchFrozenBenchmarkPrompt_When_BuildingJudgePayload(pii_type, expected_hash):
    payload = build_payload("qwen3.8-flash-next-iq3_s", pii_type, "Type", "value", "value")

    prompt = payload["messages"][0]["content"]
    assert hashlib.sha256(prompt.encode()).hexdigest() == expected_hash
    assert payload["chat_template_kwargs"] == {"enable_thinking": False}
    assert payload["temperature"] == 0
    assert payload["max_tokens"] == 2048
    assert payload["response_format"]["json_schema"]["schema"]["properties"]["verdict"]["enum"] == [
        "TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE",
    ]


def test_Should_UseFrozenDefaultBody_When_TypeHasNoBenchmarkCoverage():
    payload = build_payload("qwen3.8-flash-next-iq3_s", "../PASSWORD", "Type", "value", "value")

    prompt = payload["messages"][0]["content"]
    assert hashlib.sha256(prompt.encode()).hexdigest() == PROMPT_HASHES["_default"]
