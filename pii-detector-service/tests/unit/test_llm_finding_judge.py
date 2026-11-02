"""Unit tests for the pure LLM-judge module: prompt marking and verdict parsing."""
from __future__ import annotations

import json

from pii_detector.infrastructure.judge.llm_finding_judge import (
    JUDGE_MAX_TOKENS,
    system_prompt,
    build_payload,
    mark_span,
    parse_verdict,
    user_prompt,
)


class TestMarkSpan:
    def test_Should_MarkFirstOccurrence_When_ValueRepeated(self):
        assert mark_span("token: abc, again abc", "abc") == "token: >>>abc<<<, again abc"

    def test_Should_LeaveContextUntouched_When_ValueAbsent(self):
        assert mark_span("nothing here", "abc") == "nothing here"

    def test_Should_LeaveContextUntouched_When_ValueEmpty(self):
        assert mark_span("nothing here", "") == "nothing here"


class TestUserPrompt:
    def test_Should_ListTypeLabelValueAndMarkedContext(self):
        prompt = user_prompt("IBAN", "IBAN", "CH93 0076", "Compte : CH93 0076.")

        assert prompt.splitlines() == [
            "pii_type   : IBAN",
            "type_label : IBAN",
            "value      : CH93 0076",
            "contexte   : Compte : >>>CH93 0076<<<.",
        ]


class TestBuildPayload:
    def test_Should_RequestStrictJsonVerdict_When_Built(self):
        payload = build_payload("judge@q8", "EMAIL", "Email", "a@b.ch", "mail a@b.ch")

        assert payload["model"] == "judge@q8"
        assert payload["temperature"] == 0
        assert payload["max_tokens"] == JUDGE_MAX_TOKENS
        assert payload["messages"][0] == {"role": "system", "content": system_prompt("EMAIL")}
        assert payload["response_format"]["json_schema"]["strict"] is True
        assert payload["response_format"]["json_schema"]["schema"]["required"] == [
            "reason", "confidence", "verdict",
        ]


class TestParseVerdict:
    def test_Should_ReturnVerdict_When_ContentIsStrictJson(self):
        content = json.dumps({"reason": "motif (c)", "confidence": 0.93, "verdict": "false_positive"})

        assert parse_verdict(content) == {
            "verdict": "FALSE_POSITIVE", "confidence": 0.93, "reason": "motif (c)",
        }

    def test_Should_ExtractObject_When_JsonWrappedInProse(self):
        content = 'Voici : {"reason": "ok", "confidence": 1.4, "verdict": "TRUE_POSITIVE"} fin'

        parsed = parse_verdict(content)

        assert parsed["verdict"] == "TRUE_POSITIVE"
        assert parsed["confidence"] == 1.0

    def test_Should_BeUnparseable_When_ContentEmpty(self):
        assert parse_verdict("") == {
            "verdict": "UNPARSEABLE", "confidence": 0.0, "reason": "empty content channel",
        }

    def test_Should_BeUnparseable_When_VerdictUnknown(self):
        content = json.dumps({"reason": "?", "confidence": 0.5, "verdict": "MAYBE"})

        assert parse_verdict(content)["verdict"] == "UNPARSEABLE"

    def test_Should_BeUnparseable_When_NotJson(self):
        parsed = parse_verdict("je ne sais pas")

        assert parsed["verdict"] == "UNPARSEABLE"
        assert parsed["reason"] == "je ne sais pas"
