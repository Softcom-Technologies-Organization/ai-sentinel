"""
Branch-coverage unit tests for :class:`RegexDetector`.

The happy paths are already covered by ``test_regex_detector.py``; this file
pins the remaining error/edge branches that need a controlled patterns TOML or
a forced failure: missing config file, malformed config, disabled patterns,
the ``enable_luhn=False`` and ``remove_overlaps=False`` switches, the unknown /
short-number validation branches and the detect-time exception wrapper.

The patterns are loaded from a tmp_path TOML so the tests stay independent of
the shipped ``config/models/regex-patterns.toml``.
"""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pytest

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import PIIDetectionError
from pii_detector.infrastructure.detector.regex_detector import RegexDetector


def _config() -> DetectionConfig:
    """A regex-only DetectionConfig (no ML model needed)."""
    return DetectionConfig(model_id="regex-detector")


def _write_patterns_toml(tmp_path: Path, body: str) -> Path:
    path = tmp_path / "regex-patterns.toml"
    path.write_text(body, encoding="utf-8")
    return path


class TestLoadingErrors:
    def test_Should_RaiseFileNotFound_When_ConfigPathMissing(self, tmp_path: Path) -> None:
        missing = tmp_path / "does-not-exist.toml"
        config = _config()
        with pytest.raises(FileNotFoundError, match="Regex patterns config not found"):
            RegexDetector(config=config, config_path=missing)

    def test_Should_RaiseValueError_When_PatternsConfigMalformed(
        self, tmp_path: Path
    ) -> None:
        # A pattern entry missing the mandatory 'type' key triggers a KeyError
        # inside _load_patterns, wrapped as ValueError.
        path = _write_patterns_toml(
            tmp_path,
            """
            [patterns.broken]
            pattern = "\\\\d+"
            score = 0.9
            priority = "high"
            """,
        )
        config = _config()
        with pytest.raises(ValueError, match="Failed to load regex patterns"):
            RegexDetector(config=config, config_path=path)

    def test_Should_SkipDisabledPatterns_When_EnabledFalse(self, tmp_path: Path) -> None:
        path = _write_patterns_toml(
            tmp_path,
            """
            [patterns.active]
            type = "IP_ADDRESS"
            pattern = "\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+"
            score = 0.9
            priority = "high"

            [patterns.disabled]
            type = "EMAIL"
            pattern = "\\\\w+@\\\\w+"
            score = 0.9
            priority = "high"
            enabled = false
            """,
        )
        detector = RegexDetector(config=_config(), config_path=path)

        # Only the enabled pattern survives the load.
        loaded_types = {p.pii_type for p in detector.patterns}
        assert loaded_types == {"IP_ADDRESS"}

    def test_Should_FallbackToEmptySettings_When_ValidationLoadFails(
        self, tmp_path: Path
    ) -> None:
        path = _write_patterns_toml(
            tmp_path,
            """
            [patterns.ip]
            type = "IP_ADDRESS"
            pattern = "\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+"
            score = 0.9
            priority = "high"
            """,
        )
        detector = RegexDetector(config=_config(), config_path=path)

        # Force the *second* toml.load (inside _load_validation_settings) to
        # blow up by patching toml.load to raise on the next call.
        with patch(
            "pii_detector.infrastructure.detector.regex_detector.toml.load",
            side_effect=ValueError("corrupt"),
        ):
            settings = detector._load_validation_settings(path)

        assert settings == {}


class TestDetectionBranches:
    def _detector(self, tmp_path: Path, validation_block: str = "") -> RegexDetector:
        body = f"""
        [patterns.ip]
        type = "IP_ADDRESS"
        pattern = "\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+"
        score = 0.9
        priority = "high"
        {validation_block}
        """
        return RegexDetector(config=_config(), config_path=_write_patterns_toml(tmp_path, body))

    def test_Should_KeepOverlaps_When_RemoveOverlapsDisabled(self, tmp_path: Path) -> None:
        # Two adjacent IPs do not overlap; this exercises the
        # remove_overlaps=False branch where _resolve_overlaps is skipped.
        detector = self._detector(
            tmp_path,
            validation_block="\n        [validation]\n        remove_overlaps = false\n",
        )
        entities = detector.detect_pii("10.0.0.1 and 10.0.0.2")

        assert len(entities) == 2

    def test_Should_WrapException_When_DetectionRaises(self, tmp_path: Path) -> None:
        detector = self._detector(tmp_path)
        with patch.object(
            detector, "_detect_all_patterns", side_effect=RuntimeError("boom")
        ):
            with pytest.raises(PIIDetectionError, match="Regex detection failed"):
                detector.detect_pii("10.0.0.1")


class TestValidationBranches:
    def _build_detector(self, tmp_path: Path) -> RegexDetector:
        body = """
        [patterns.ip]
        type = "IP_ADDRESS"
        pattern = "\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+"
        score = 0.9
        priority = "high"
        """
        return RegexDetector(config=_config(), config_path=_write_patterns_toml(tmp_path, body))

    def test_Should_ReturnMatchesUnchanged_When_LuhnDisabled(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        detector.validation_settings = {"enable_luhn": False}

        match = _entity_requiring_luhn("0000")
        result = detector._validate_matches([match])

        # enable_luhn=False short-circuits: the match is returned as-is.
        assert result == [match]

    def test_Should_KeepMatch_When_NoValidationRequired(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        detector.validation_settings = {"enable_luhn": True}

        match = _entity(_requires_validation=False)
        assert detector._validate_matches([match]) == [match]

    def test_Should_KeepMatch_When_ValidationMethodUnknown(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        detector.validation_settings = {"enable_luhn": True}

        match = _entity(_requires_validation=True, _validation_method="unknown")
        assert detector._validate_matches([match]) == [match]

    def test_Should_DropMatch_When_LuhnInvalid(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        detector.validation_settings = {"enable_luhn": True}

        match = _entity_requiring_luhn("4532015112830367")  # Luhn KO
        assert detector._validate_matches([match]) == []

    def test_Should_KeepMatch_When_LuhnValid(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        detector.validation_settings = {"enable_luhn": True}

        match = _entity_requiring_luhn("4532015112830366")  # Luhn OK
        assert detector._validate_matches([match]) == [match]

    def test_Should_RejectLuhn_When_TooFewDigits(self, tmp_path: Path) -> None:
        detector = self._build_detector(tmp_path)
        # Below the 13-digit minimum -> rejected by the length guard.
        assert detector._validate_luhn("12345") is False


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _entity(**attrs) -> PIIEntity:
    entity = PIIEntity(
        text="0000",
        pii_type="CREDIT_CARD_NUMBER",
        type_label="CREDIT_CARD_NUMBER",
        start=0,
        end=4,
        score=0.9,
    )
    for name, value in attrs.items():
        setattr(entity, name, value)
    return entity


def _entity_requiring_luhn(text: str) -> PIIEntity:
    return _entity(
        text=text,
        _requires_validation=True,
        _validation_method="luhn",
    )
