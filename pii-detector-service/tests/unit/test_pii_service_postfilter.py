
from __future__ import annotations

import importlib
import sys
from typing import List

import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity


pii_service = importlib.import_module(
    "pii_detector.infrastructure.adapter.in.grpc.pii_service"
)
PIIDetectionServicer = pii_service.PIIDetectionServicer


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _ip_entity(text: str) -> PIIEntity:
    return PIIEntity(
        text=text,
        pii_type="IP_ADDRESS",
        type_label="IP_ADDRESS",
        start=0,
        end=len(text),
        score=0.9,
        source=DetectorSource.MINISTRAL,
    )


# ---------------------------------------------------------------------------
# _is_postfilter_enabled
# ---------------------------------------------------------------------------


class TestIsPostfilterEnabled:
    @pytest.mark.parametrize(
        "flags,expected",
        [
            (None, False),
            ({}, False),
            ({"postfilter_enabled": False}, False),
            ({"postfilter_enabled": True}, True),
            ({"ministral_enabled": True}, False),  # unrelated flags ignored
        ],
    )
    def test_should_default_to_false_when_flag_missing(
        self, flags, expected
    ) -> None:
        assert PIIDetectionServicer._is_postfilter_enabled(flags) is expected


# ---------------------------------------------------------------------------
# _apply_format_postfilter
# ---------------------------------------------------------------------------


class _FakeServicer:
    """Minimal stand-in exposing only the method under test.

    We bind the real ``_apply_format_postfilter`` to this instance so we can
    exercise its branches without booting the servicer.
    """

    _apply_format_postfilter = PIIDetectionServicer._apply_format_postfilter


class TestApplyFormatPostfilter:
    @pytest.mark.parametrize(
        "pii_type",
        ["SOCIAL_SECURITY_NUMBER", "social security number", "SSN", "US_SSN", "SOCIALNUM"],
    )
    def test_Should_KeepShortSocialSecurityValues_When_PostfilterRuns(self, pii_type):
        kept_values = [
            "1234", "123456",
            "123456789", "123-45-6789", "756.1234.5678.97", "7561234567897",
            "1 84 12 75 116 001 42", "1841275116001", "1 84 12 2A 116 001 42",
            "850730-033-28",
        ]
        entities = [
            PIIEntity(
                text=value, pii_type=pii_type, type_label=pii_type,
                start=0, end=len(value), score=1.0, source=DetectorSource.MINISTRAL,
            )
            for value in kept_values
        ]
        kept, rejections = _FakeServicer()._apply_format_postfilter(
            entities, content="", request_id="req-ssn"
        )
        assert kept == entities
        assert rejections == []

    def test_should_discard_fake_ip_and_keep_real_ip(self) -> None:
        servicer = _FakeServicer()
        real_ip = _ip_entity("10.217.4.11")
        fake_ip = _ip_entity("0.244.999.7")
        kept, rejections = servicer._apply_format_postfilter(
            [real_ip, fake_ip], content="trace", request_id="req-1"
        )
        assert kept == [real_ip]
        assert len(rejections) == 1
        rejected_entity, verdict = rejections[0]
        assert rejected_entity is fake_ip
        assert verdict.verdict == "FALSE_POSITIVE"

    def test_should_passthrough_when_entities_empty(self) -> None:
        servicer = _FakeServicer()
        assert servicer._apply_format_postfilter([], "text", "req-2") == ([], [])

    def test_should_fail_open_when_validator_blows_up(self) -> None:
        servicer = _FakeServicer()
        entities: List[PIIEntity] = [_ip_entity("0.244.999.7")]
        from unittest.mock import MagicMock, patch

        validator = MagicMock()
        validator.filter_with_verdicts.side_effect = RuntimeError(
            "validator misconfigured"
        )
        with patch(
            "pii_detector.infrastructure.postfilter.format_postfilter_validator.get_instance",
            return_value=validator,
        ):
            result = servicer._apply_format_postfilter(entities, "text", "req-3")
        # Defense-in-depth: original entities returned untouched, no rejection.
        assert result == (entities, [])

    def test_should_emit_structured_log_with_counts(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        servicer = _FakeServicer()
        real_ip = _ip_entity("10.217.4.11")
        fake_ip = _ip_entity("0.244.999.7")
        with caplog.at_level("INFO", logger=pii_service.logger.name):
            servicer._apply_format_postfilter(
                [real_ip, fake_ip], "text", "req-4"
            )
        message = " ".join(r.message for r in caplog.records)
        assert "[PREFILTER] post-filter" in message
        assert "rejected=1" in message
        assert "2->1 entities" in message


# ---------------------------------------------------------------------------
# Zero-overhead guarantee when flag is disabled
# ---------------------------------------------------------------------------


class TestZeroOverheadWhenDisabled:
    def test_should_never_import_validator_when_flag_off(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """When ``postfilter_enabled=False`` the pipeline must not touch the
        validator module (zero-overhead criterion)."""
        for mod in list(sys.modules):
            if mod.startswith(
                "pii_detector.infrastructure.postfilter.format_postfilter_validator"
            ):
                monkeypatch.delitem(sys.modules, mod, raising=False)

        # The gate must return False -> no get_instance() ever called.
        assert PIIDetectionServicer._is_postfilter_enabled(
            {"postfilter_enabled": False}
        ) is False
        assert PIIDetectionServicer._is_postfilter_enabled(None) is False
        assert PIIDetectionServicer._is_postfilter_enabled({}) is False

        # The module must remain unloaded (the gate alone never imports it).
        assert (
            "pii_detector.infrastructure.postfilter.format_postfilter_validator"
            not in sys.modules
        ), "prefilter module imported even though flag is OFF"
