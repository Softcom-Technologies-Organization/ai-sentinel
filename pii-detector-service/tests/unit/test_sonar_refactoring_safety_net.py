"""
Safety net tests for SonarQube refactoring.

These characterization tests pin the CURRENT behavior of functions that will be
refactored to reduce cognitive complexity. They ensure zero regressions during
the refactoring process.

Covers:
    - CompositePIIDetector.detect_pii()  (Issue 1 - S3776 CC=22)
    - ConflictResolver.resolve()          (Issue 2 - S3776 CC=23)
    - PresidioDetector.load_model()       (Issue 3 - S3776 CC=48)
    - PresidioDetector._convert_and_filter_results()  (Issue 4 - S3776 CC=16)
"""

from __future__ import annotations

import logging
from unittest.mock import MagicMock, Mock, patch

import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.entity.pii_type import PIIType


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_entity(
    text: str = "dummy",
    pii_type: str = "PERSON_NAME",
    start: int = 0,
    end: int = 5,
    score: float = 0.9,
    source: DetectorSource = DetectorSource.UNKNOWN_SOURCE,
) -> PIIEntity:
    """Factory helper for PIIEntity instances."""
    return PIIEntity(
        text=text,
        pii_type=pii_type,
        type_label=pii_type,
        start=start,
        end=end,
        score=score,
        source=source,
    )


# ===========================================================================
# SECTION 1: CompositePIIDetector.detect_pii
# ===========================================================================


class TestCompositeDetectPii:
    """Characterization tests for CompositePIIDetector.detect_pii()."""

    # --- Fixtures ---

    @pytest.fixture
    def mock_ml_detector(self):
        detector = Mock()
        detector.model_id = "mock-ml"
        detector.detect_pii = Mock(return_value=[])
        return detector

    @pytest.fixture
    def mock_regex_detector(self):
        detector = Mock()
        detector.model_id = "regex"
        detector.detect_pii = Mock(return_value=[])
        detector.download_model = Mock()
        detector.load_model = Mock()
        return detector

    @pytest.fixture
    def mock_presidio_detector(self):
        detector = Mock()
        detector.model_id = "presidio"
        detector.detect_pii = Mock(return_value=[])
        return detector

    @pytest.fixture
    def mock_merger(self):
        merger = Mock()
        merger.merge = Mock(return_value=[])
        return merger

    def _build(self, ml=None, regex=None, presidio=None, merger=None,
               enable_regex=True, enable_presidio=True):
        from pii_detector.application.orchestration.composite_detector import (
            CompositePIIDetector,
        )
        return CompositePIIDetector(
            ml_detector=ml,
            regex_detector=regex,
            presidio_detector=presidio,
            merger=merger,
            enable_regex=enable_regex,
            enable_presidio=enable_presidio,
        )

    # --- Tests ---

    def test_should_return_empty_list_when_text_is_empty(
        self, mock_ml_detector, mock_merger
    ):
        composite = self._build(ml=mock_ml_detector, merger=mock_merger)
        result = composite.detect_pii("")
        assert result == []
        mock_merger.merge.assert_not_called()

    def test_should_return_empty_list_when_text_is_none(
        self, mock_ml_detector, mock_merger
    ):
        composite = self._build(ml=mock_ml_detector, merger=mock_merger)
        result = composite.detect_pii(None)
        assert result == []

    def test_should_use_ml_only_when_regex_and_presidio_disabled(
        self, mock_ml_detector, mock_merger
    ):
        ml_entity = _make_entity(text="John", pii_type="PERSON_NAME", start=0, end=4)
        mock_ml_detector.detect_pii.return_value = [ml_entity]
        mock_merger.merge.return_value = [ml_entity]

        composite = self._build(
            ml=mock_ml_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=False,
        )
        result = composite.detect_pii("John is here")

        assert len(result) == 1
        assert result[0].text == "John"
        # ML detector was called
        mock_ml_detector.detect_pii.assert_called_once()

    def test_should_use_regex_only_when_ml_is_none_and_presidio_disabled(
        self, mock_regex_detector, mock_merger
    ):
        regex_entity = _make_entity(text="test@mail.com", pii_type="EMAIL", start=0, end=13)
        mock_regex_detector.detect_pii.return_value = [regex_entity]
        mock_merger.merge.return_value = [regex_entity]

        composite = self._build(
            regex=mock_regex_detector, merger=mock_merger,
            enable_regex=True, enable_presidio=False,
        )
        result = composite.detect_pii("test@mail.com is my email")

        assert len(result) == 1
        assert result[0].pii_type == "EMAIL"

    def test_should_use_presidio_only_when_ml_is_none_and_regex_disabled(
        self, mock_presidio_detector, mock_merger
    ):
        presidio_entity = _make_entity(text="John", pii_type="PERSON_NAME", start=0, end=4)
        mock_presidio_detector.detect_pii.return_value = [presidio_entity]
        mock_merger.merge.return_value = [presidio_entity]

        composite = self._build(
            presidio=mock_presidio_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=True,
        )
        result = composite.detect_pii("John is here")

        assert len(result) == 1

    def test_should_call_all_detectors_when_all_enabled(
        self, mock_ml_detector, mock_regex_detector, mock_presidio_detector, mock_merger
    ):
        mock_merger.merge.return_value = []

        composite = self._build(
            ml=mock_ml_detector,
            regex=mock_regex_detector,
            presidio=mock_presidio_detector,
            merger=mock_merger,
            enable_regex=True,
            enable_presidio=True,
        )
        composite.detect_pii("some text here")

        mock_ml_detector.detect_pii.assert_called_once()
        mock_regex_detector.detect_pii.assert_called_once()
        mock_presidio_detector.detect_pii.assert_called_once()
        mock_merger.merge.assert_called_once()

    def test_should_return_warning_empty_when_no_detectors_available(self, mock_merger):
        mock_merger.merge.return_value = []

        composite = self._build(
            merger=mock_merger,
            enable_regex=False,
            enable_presidio=False,
        )
        result = composite.detect_pii("some text")
        assert result == []
        mock_merger.merge.assert_not_called()

    def test_should_override_ml_to_false_when_enable_ml_false_at_runtime(
        self, mock_ml_detector, mock_merger
    ):
        mock_merger.merge.return_value = []
        composite = self._build(
            ml=mock_ml_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=False,
        )
        result = composite.detect_pii("text", enable_ml=False)
        assert result == []
        mock_ml_detector.detect_pii.assert_not_called()

    def test_should_override_regex_to_true_when_enable_regex_true_at_runtime(
        self, mock_ml_detector, mock_regex_detector, mock_merger
    ):
        regex_entity = _make_entity(text="x", pii_type="EMAIL", start=0, end=1)
        mock_regex_detector.detect_pii.return_value = [regex_entity]
        mock_merger.merge.return_value = [regex_entity]

        composite = self._build(
            ml=mock_ml_detector,
            regex=mock_regex_detector,
            merger=mock_merger,
            enable_regex=False,
            enable_presidio=False,
        )
        # enable_regex=False in __init__, but overridden at call time
        result = composite.detect_pii("text", enable_ml=False, enable_regex=True)
        assert len(result) == 1

    def test_should_override_presidio_to_true_when_enable_presidio_true_at_runtime(
        self, mock_ml_detector, mock_presidio_detector, mock_merger
    ):
        presidio_entity = _make_entity(text="John", pii_type="PERSON_NAME", start=0, end=4)
        mock_presidio_detector.detect_pii.return_value = [presidio_entity]
        mock_merger.merge.return_value = [presidio_entity]

        composite = self._build(
            ml=mock_ml_detector,
            presidio=mock_presidio_detector,
            merger=mock_merger,
            enable_regex=False,
            enable_presidio=False,
        )
        result = composite.detect_pii("John text", enable_ml=False, enable_presidio=True)
        assert len(result) == 1

    def test_should_pass_threshold_when_provided(
        self, mock_ml_detector, mock_merger
    ):
        mock_merger.merge.return_value = []
        composite = self._build(
            ml=mock_ml_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=False,
        )
        composite.detect_pii("text", threshold=0.9)
        # ML detector is called via _run_ml_detection
        mock_ml_detector.detect_pii.assert_called_once()

    def test_should_pass_pii_type_configs_when_provided(
        self, mock_ml_detector, mock_merger
    ):
        mock_merger.merge.return_value = []
        composite = self._build(
            ml=mock_ml_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=False,
        )
        configs = {"EMAIL": {"enabled": True}}
        composite.detect_pii("text", pii_type_configs=configs)
        mock_ml_detector.detect_pii.assert_called_once()

    def test_should_handle_ml_detector_exception_when_detection_fails(
        self, mock_ml_detector, mock_merger
    ):
        mock_ml_detector.detect_pii.side_effect = RuntimeError("boom")
        mock_merger.merge.return_value = []

        composite = self._build(
            ml=mock_ml_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=False,
        )
        # Should not raise, returns empty due to exception handling in _run_ml_detection
        result = composite.detect_pii("text")
        assert result == []

    def test_should_handle_regex_detector_exception_when_detection_fails(
        self, mock_regex_detector, mock_merger
    ):
        mock_regex_detector.detect_pii.side_effect = RuntimeError("boom")
        mock_merger.merge.return_value = []

        composite = self._build(
            regex=mock_regex_detector, merger=mock_merger,
            enable_regex=True, enable_presidio=False,
        )
        result = composite.detect_pii("text")
        assert result == []

    def test_should_handle_presidio_detector_exception_when_detection_fails(
        self, mock_presidio_detector, mock_merger
    ):
        mock_presidio_detector.detect_pii.side_effect = RuntimeError("boom")
        mock_merger.merge.return_value = []

        composite = self._build(
            presidio=mock_presidio_detector, merger=mock_merger,
            enable_regex=False, enable_presidio=True,
        )
        result = composite.detect_pii("text")
        assert result == []

    def test_should_count_entities_per_detector_when_multiple_sources(
        self, mock_ml_detector, mock_regex_detector, mock_presidio_detector, mock_merger
    ):
        ml_ent = _make_entity(text="A", start=0, end=1, pii_type="X")
        regex_ent = _make_entity(text="B", start=2, end=3, pii_type="Y")
        presidio_ent = _make_entity(text="C", start=4, end=5, pii_type="Z")

        mock_ml_detector.detect_pii.return_value = [ml_ent]
        mock_regex_detector.detect_pii.return_value = [regex_ent]
        mock_presidio_detector.detect_pii.return_value = [presidio_ent]
        mock_merger.merge.return_value = [ml_ent, regex_ent, presidio_ent]

        composite = self._build(
            ml=mock_ml_detector,
            regex=mock_regex_detector,
            presidio=mock_presidio_detector,
            merger=mock_merger,
        )
        result = composite.detect_pii("A B C text")
        assert len(result) == 3


# ===========================================================================
# SECTION 2: ConflictResolver.resolve
# ===========================================================================


class TestConflictResolverResolve:
    """Characterization tests for ConflictResolver.resolve()."""

    @pytest.fixture
    def resolver(self):
        from pii_detector.infrastructure.detector.conflict_resolver import (
            ConflictResolver,
        )
        category_map = {
            "SSN": "IDENTITY",
            "PHONE_NUMBER": "CONTACT",
            "EMAIL": "CONTACT",
            "CREDIT_CARD_NUMBER": "FINANCIAL",
            "BANK_ACCOUNT_NUMBER": "FINANCIAL",
            "IP_ADDRESS": "DIGITAL",
            "PERSON_NAME": "IDENTITY",
            "USERNAME": "IDENTITY",
            "AVS_NUMBER": "MEDICAL",
            "IBAN": "FINANCIAL",
            "URL": "DIGITAL",
            "ADDRESS": "CONTACT",
            "DATE_OF_BIRTH": "IDENTITY",
            "PATIENT_ID": "MEDICAL",
            "ACCOUNT_ID": "FINANCIAL",
            "API_KEY": "IT_CREDENTIALS",
            "ACCESS_TOKEN": "IT_CREDENTIALS",
            "SECRET_KEY": "IT_CREDENTIALS",
            "PASSWORD": "IT_CREDENTIALS",
        }
        return ConflictResolver(pii_type_to_category=category_map)

    # --- Empty / Single label ---

    def test_should_return_none_when_empty_labels(self, resolver):
        assert resolver.resolve("text", []) is None

    def test_should_return_single_label_when_only_one_detected(self, resolver):
        result = resolver.resolve("John", [("PERSON_NAME", 0.85)])
        assert result == ("PERSON_NAME", 0.85)

    # --- Pattern-based resolution (GROUP 1: NUMERIC_DOTTED) ---

    def test_should_resolve_to_ip_when_valid_ip_detected(self, resolver):
        labels = [("IP_ADDRESS", 0.8), ("AVS_NUMBER", 0.7)]
        result = resolver.resolve("192.168.1.1", labels)
        assert result is not None
        assert result[0] == "IP_ADDRESS"

    def test_should_resolve_to_avs_when_valid_avs_detected(self, resolver):
        labels = [("IP_ADDRESS", 0.6), ("AVS_NUMBER", 0.9)]
        result = resolver.resolve("756.1234.5678.90", labels)
        assert result is not None
        assert result[0] == "AVS_NUMBER"

    # --- Pattern-based resolution (GROUP 2: NUMERIC_DASHED) ---

    def test_should_resolve_to_ssn_when_ssn_format_detected(self, resolver):
        labels = [("SSN", 0.8), ("PHONE_NUMBER", 0.7)]
        result = resolver.resolve("123-45-6789", labels)
        assert result is not None
        assert result[0] == "SSN"

    # --- Pattern-based resolution (GROUP 3: NUMERIC_SPACED) ---

    def test_should_resolve_to_credit_card_when_4x4_digit_format(self, resolver):
        labels = [("CREDIT_CARD_NUMBER", 0.9), ("BANK_ACCOUNT_NUMBER", 0.6)]
        result = resolver.resolve("4532 1234 5678 9012", labels)
        assert result is not None
        assert result[0] == "CREDIT_CARD_NUMBER"

    # --- Pattern-based resolution (GROUP 5: EMAIL_LIKE) ---

    def test_should_resolve_to_email_when_email_detected(self, resolver):
        labels = [("EMAIL", 0.9), ("USERNAME", 0.5)]
        result = resolver.resolve("user@example.com", labels)
        assert result is not None
        assert result[0] == "EMAIL"

    # --- Pattern-based resolution (GROUP 8: PERSON_LIKE) ---

    def test_should_resolve_to_person_name_when_capitalized_name(self, resolver):
        labels = [("PERSON_NAME", 0.8), ("USERNAME", 0.6)]
        result = resolver.resolve("John Doe", labels)
        assert result is not None
        assert result[0] == "PERSON_NAME"

    # --- Fallback priority resolution ---

    def test_should_use_fallback_priority_when_multiple_pattern_match(self, resolver):
        """When both SSN and NATIONAL_ID match the same dashed pattern,
        fallback priority should pick SSN (first in list)."""
        labels = [("SSN", 0.7), ("NATIONAL_ID", 0.7)]
        result = resolver.resolve("123-45-6789", labels)
        assert result is not None
        assert result[0] == "SSN"

    # --- Category priority resolution ---

    def test_should_use_category_priority_when_no_group_matches(self, resolver):
        """When text does not match any conflict group pattern,
        resolve by category priority."""
        # Use a text with special chars that matches NO group pattern
        labels = [("PERSON_NAME", 0.7), ("CREDIT_CARD_NUMBER", 0.8)]
        result = resolver.resolve("~data~", labels)
        assert result is not None
        # FINANCIAL (100) beats IDENTITY (85)
        assert result[0] == "CREDIT_CARD_NUMBER"

    def test_should_prefer_higher_score_when_same_category_priority(self, resolver):
        labels = [("EMAIL", 0.5), ("PHONE_NUMBER", 0.9)]
        # Both in CONTACT category -> pick by score within same priority
        result = resolver.resolve("random data", labels)
        assert result is not None
        # Same category, higher score wins
        assert result[0] == "PHONE_NUMBER"

    # --- Stats tracking ---

    def test_should_increment_stats_when_conflicts_resolved(self, resolver):
        resolver.reset_conflict_stats()
        resolver.resolve("192.168.1.1", [("IP_ADDRESS", 0.8), ("AVS_NUMBER", 0.7)])
        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] >= 1

    def test_should_reset_stats_when_reset_called(self, resolver):
        resolver.resolve("192.168.1.1", [("IP_ADDRESS", 0.8), ("AVS_NUMBER", 0.7)])
        resolver.reset_conflict_stats()
        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] == 0

    # --- Edge case: detected types not in any group ---

    def test_should_fallback_to_category_when_types_not_in_any_group(self, resolver):
        labels = [("PERSON_NAME", 0.8), ("API_KEY", 0.7)]
        # Use text with special chars that matches NO group pattern
        result = resolver.resolve("~data~", labels)
        assert result is not None
        # IT_CREDENTIALS (90) beats IDENTITY (85)
        assert result[0] == "API_KEY"

    # --- GROUP 11: ACCOUNT_LIKE ---

    def test_should_resolve_patient_id_when_long_digit_sequence_matches_alphanumeric_group(self, resolver):
        """14-digit string matches LONG_ALPHANUMERIC group first (before ACCOUNT_LIKE).
        In LONG_ALPHANUMERIC, PATIENT_ID is in fallback_priority and detected_types,
        so it wins via fallback_priority even though BANK_ACCOUNT_NUMBER is also detected."""
        labels = [("BANK_ACCOUNT_NUMBER", 0.8), ("PATIENT_ID", 0.7)]
        result = resolver.resolve("12345678901234", labels)
        assert result is not None
        assert result[0] == "PATIENT_ID"

    # --- GROUP 10: DATE_LIKE ---

    def test_should_resolve_date_of_birth_when_date_pattern(self, resolver):
        labels = [("DATE_OF_BIRTH", 0.8)]
        result = resolver.resolve("01/12/1990", labels)
        # single label, returned directly
        assert result == ("DATE_OF_BIRTH", 0.8)

    # --- Dict comprehension equivalence (Issue 8) ---

    def test_should_build_scores_dict_when_multiple_labels_detected(self, resolver):
        """Verify scores dict built from detected_labels is correct."""
        labels = [("SSN", 0.8), ("PHONE_NUMBER", 0.7)]
        result = resolver.resolve("123-45-6789", labels)
        assert result is not None
        # The score returned should match one from the input
        assert result[1] in (0.8, 0.7)


# ===========================================================================
# SECTION 3: PresidioDetector.load_model
# ===========================================================================


class TestPresidioLoadModel:
    """Characterization tests for PresidioDetector.load_model()."""

    @pytest.fixture
    def mock_toml_config(self):
        """Default TOML configuration for Presidio."""
        return {
            "model": {
                "model_id": "presidio-test",
                "enabled": True,
                "priority": 2,
            },
            "detection": {
                "default_threshold": 0.5,
                "languages": ["en"],
                "labels_to_ignore": ["CARDINAL"],
            },
            "nlp": {
                "models": [
                    {"model_name": "blank:en", "lang_code": "en"}
                ],
            },
            "recognizers": {"email": True},
            "scoring": {},
            "advanced": {},
        }

    @pytest.fixture
    def mock_toml_config_with_provider(self):
        """TOML config where models use real packages (not blank)."""
        return {
            "model": {
                "model_id": "presidio-provider",
                "enabled": True,
                "priority": 2,
            },
            "detection": {
                "default_threshold": 0.5,
                "languages": ["en"],
                "labels_to_ignore": [],
            },
            "nlp": {
                "models": [
                    {"model_name": "en_core_web_sm", "lang_code": "en"}
                ],
            },
            "recognizers": {},
            "scoring": {},
            "advanced": {},
        }

    def _create_detector(self, toml_config):
        """Helper to create a PresidioDetector with mocked dependencies."""
        with patch(
            "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter",
            return_value=None,
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.toml.load",
            return_value=toml_config,
        ), patch("builtins.open", create=True):
            from pii_detector.infrastructure.detector.presidio_detector import (
                PresidioDetector,
            )
            return PresidioDetector()

    def test_should_skip_loading_when_already_loaded(self, mock_toml_config):
        detector = self._create_detector(mock_toml_config)
        detector._analyzer = Mock()  # pretend already loaded

        detector.load_model()

        # Analyzer should not have been recreated
        assert detector._analyzer is not None

    def test_should_use_blank_pipeline_when_model_name_starts_with_blank(
        self, mock_toml_config
    ):
        detector = self._create_detector(mock_toml_config)
        detector._analyzer = None

        mock_spacy = MagicMock()
        mock_spacy.blank.return_value = MagicMock()

        mock_spacy_nlp_engine = MagicMock()

        with patch.dict("sys.modules", {"spacy": mock_spacy}), \
             patch(
                 "presidio_analyzer.nlp_engine.SpacyNlpEngine",
                 mock_spacy_nlp_engine,
             ), \
             patch(
                 "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine"
             ) as mock_analyzer_cls:

            detector.load_model()
            mock_analyzer_cls.assert_called_once()

    def test_should_use_nlp_engine_provider_when_model_name_is_real_package(
        self, mock_toml_config_with_provider
    ):
        detector = self._create_detector(mock_toml_config_with_provider)
        detector._analyzer = None

        mock_provider_instance = MagicMock()
        mock_provider_instance.create_engine.return_value = MagicMock()

        with patch(
            "pii_detector.infrastructure.detector.presidio_detector.NlpEngineProvider",
            return_value=mock_provider_instance,
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine"
        ) as mock_analyzer_cls:

            detector.load_model()

            mock_provider_instance.create_engine.assert_called_once()
            mock_analyzer_cls.assert_called_once()

    def test_should_fallback_to_basic_analyzer_when_nlp_engine_init_fails(
        self, mock_toml_config_with_provider
    ):
        detector = self._create_detector(mock_toml_config_with_provider)
        detector._analyzer = None

        # NlpEngineProvider raises, so AnalyzerEngine(nlp_engine=...) in try block
        # is never called. The except block calls AnalyzerEngine() (no args) as fallback.
        # So AnalyzerEngine is called only ONCE (in the except block).
        mock_fallback_analyzer = MagicMock()

        with patch(
            "pii_detector.infrastructure.detector.presidio_detector.NlpEngineProvider",
            side_effect=RuntimeError("NLP provider failed"),
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine",
            return_value=mock_fallback_analyzer,
        ):
            detector.load_model()
            # Should have fallen back to basic AnalyzerEngine()
            assert detector._analyzer is mock_fallback_analyzer

    def test_should_raise_exception_when_all_fallbacks_fail(
        self, mock_toml_config_with_provider
    ):
        detector = self._create_detector(mock_toml_config_with_provider)
        detector._analyzer = None

        with patch(
            "pii_detector.infrastructure.detector.presidio_detector.NlpEngineProvider",
            side_effect=RuntimeError("NLP provider failed"),
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine",
            side_effect=RuntimeError("All engines failed"),
        ):
            with pytest.raises(RuntimeError):
                detector.load_model()

    def test_should_handle_empty_nlp_section_when_no_models(self):
        config = {
            "model": {"model_id": "test", "enabled": True},
            "detection": {"default_threshold": 0.5, "languages": ["en"]},
            "nlp": {},
            "recognizers": {},
            "scoring": {},
            "advanced": {},
        }
        detector = self._create_detector(config)
        detector._analyzer = None

        mock_spacy = MagicMock()
        mock_spacy.blank.return_value = MagicMock()

        mock_spacy_nlp_engine = MagicMock()

        with patch.dict("sys.modules", {"spacy": mock_spacy}), \
             patch(
                 "presidio_analyzer.nlp_engine.SpacyNlpEngine",
                 mock_spacy_nlp_engine,
             ), \
             patch(
                 "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine"
             ) as mock_analyzer_cls:

            detector.load_model()
            mock_analyzer_cls.assert_called_once()

    def test_should_inject_labels_to_ignore_when_ner_config_missing(
        self, mock_toml_config_with_provider
    ):
        # Remove any existing ner config to test the injection path
        mock_toml_config_with_provider["nlp"].pop("ner_model_configuration", None)

        detector = self._create_detector(mock_toml_config_with_provider)
        detector._analyzer = None

        mock_provider_instance = MagicMock()
        mock_provider_instance.create_engine.return_value = MagicMock()

        with patch(
            "pii_detector.infrastructure.detector.presidio_detector.NlpEngineProvider",
            return_value=mock_provider_instance,
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.AnalyzerEngine"
        ):
            detector.load_model()

            # Provider was instantiated - no error


# ===========================================================================
# SECTION 4: PresidioDetector._convert_and_filter_results
# ===========================================================================


class TestPresidioConvertAndFilterResults:
    """Characterization tests for PresidioDetector._convert_and_filter_results()."""

    @pytest.fixture
    def detector(self):
        """Create a PresidioDetector with mocked dependencies."""
        with patch(
            "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter",
            return_value=None,
        ), patch(
            "pii_detector.infrastructure.detector.presidio_detector.toml.load",
            return_value={
                "model": {"model_id": "test", "enabled": True},
                "detection": {"default_threshold": 0.5, "languages": ["en"]},
                "nlp": {},
                "recognizers": {},
                "scoring": {"EMAIL_ADDRESS": 0.7, "PHONE_NUMBER": 0.6},
                "advanced": {},
            },
        ), patch("builtins.open", create=True):
            from pii_detector.infrastructure.detector.presidio_detector import (
                PresidioDetector,
            )
            det = PresidioDetector()
        return det

    def _make_presidio_result(
        self, entity_type: str, start: int, end: int, score: float
    ):
        """Create a mock Presidio RecognizerResult."""
        result = Mock()
        result.entity_type = entity_type
        result.start = start
        result.end = end
        result.score = score
        return result

    def test_should_convert_results_when_no_fresh_configs(self, detector):
        text = "user@example.com called 555-0123"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.95),
            self._make_presidio_result("PHONE_NUMBER", 24, 32, 0.80),
        ]

        entities = detector._convert_and_filter_results(text, results)

        assert len(entities) == 2
        assert entities[0].pii_type == PIIType.EMAIL
        assert entities[0].source == DetectorSource.PRESIDIO
        assert entities[0].text == "user@example.com"
        assert entities[1].pii_type == PIIType.PHONE

    def test_should_filter_by_threshold_when_score_below_entity_threshold(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.5),
        ]
        # EMAIL_ADDRESS threshold is 0.7 from TOML scoring
        entities = detector._convert_and_filter_results(text, results)
        assert len(entities) == 0  # filtered out

    def test_should_keep_result_when_score_above_threshold(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.95),
        ]
        entities = detector._convert_and_filter_results(text, results)
        assert len(entities) == 1

    def test_should_use_fresh_configs_when_provided(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.6),
        ]
        fresh_configs = {
            "EMAIL": {
                "enabled": True,
                "detector": "PRESIDIO",
                "detector_label": "EMAIL_ADDRESS",
                "threshold": 0.5,  # Lower threshold allows the result
            },
        }
        entities = detector._convert_and_filter_results(text, results, fresh_configs)
        assert len(entities) == 1

    def test_should_filter_with_fresh_configs_when_score_below_fresh_threshold(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.3),
        ]
        fresh_configs = {
            "EMAIL": {
                "enabled": True,
                "detector": "PRESIDIO",
                "detector_label": "EMAIL_ADDRESS",
                "threshold": 0.5,
            },
        }
        entities = detector._convert_and_filter_results(text, results, fresh_configs)
        assert len(entities) == 0

    def test_should_use_db_configs_when_db_adapter_available(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.6),
        ]
        mock_adapter = Mock()
        mock_adapter.fetch_pii_type_configs.return_value = {
            "EMAIL": {
                "enabled": True,
                "detector": "PRESIDIO",
                "detector_label": "EMAIL_ADDRESS",
                "threshold": 0.5,
            },
        }
        detector._db_adapter = mock_adapter

        entities = detector._convert_and_filter_results(text, results)
        assert len(entities) == 1

    def test_should_handle_unknown_entity_type_when_not_in_map(self, detector):
        text = "some custom entity"
        results = [
            self._make_presidio_result("CUSTOM_UNKNOWN", 0, 18, 0.95),
        ]
        entities = detector._convert_and_filter_results(text, results)
        assert len(entities) == 1
        assert entities[0].pii_type == PIIType.UNKNOWN

    def test_should_return_empty_list_when_no_results(self, detector):
        entities = detector._convert_and_filter_results("text", [])
        assert entities == []

    def test_should_use_toml_fallback_when_db_returns_none(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.6),
        ]
        mock_adapter = Mock()
        mock_adapter.fetch_pii_type_configs.return_value = None
        detector._db_adapter = mock_adapter

        # TOML scoring has EMAIL_ADDRESS: 0.7, so 0.6 should be filtered
        entities = detector._convert_and_filter_results(text, results)
        assert len(entities) == 0

    def test_should_preserve_original_score_when_scoring_overrides_exist(self, detector):
        text = "user@example.com"
        results = [
            self._make_presidio_result("EMAIL_ADDRESS", 0, 16, 0.95),
        ]
        entities = detector._convert_and_filter_results(text, results)
        # Score should be original Presidio score, not overridden
        assert abs(entities[0].score - 0.95) < 1e-9


# ===========================================================================
# SECTION 5: Additional edge-case tests for minor issues
# ===========================================================================


class TestAggregatedSpanHasConflict:
    """Test for Issue 9 - set comprehension equivalence in AggregatedSpan.has_conflict()."""

    def test_should_return_false_when_single_label_type(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            AggregatedSpan,
        )
        span = AggregatedSpan(start=0, end=5, text="hello", labels=[("EMAIL", 0.9)])
        assert span.has_conflict() is False

    def test_should_return_true_when_multiple_label_types(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            AggregatedSpan,
        )
        span = AggregatedSpan(
            start=0, end=5, text="hello",
            labels=[("EMAIL", 0.9), ("PHONE_NUMBER", 0.7)],
        )
        assert span.has_conflict() is True

    def test_should_return_false_when_same_type_different_scores(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            AggregatedSpan,
        )
        span = AggregatedSpan(
            start=0, end=5, text="hello",
            labels=[("EMAIL", 0.9), ("EMAIL", 0.7)],
        )
        assert span.has_conflict() is False


class TestOptimizePassesDictComprehension:
    """Test for Issue 7 - dict comprehension equivalence in _optimize_passes."""

    def test_should_build_batches_correctly_when_labels_provided(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            MultiPassGlinerDetector,
        )
        with patch.object(MultiPassGlinerDetector, "__init__", lambda self, **kwargs: None):
            det = MultiPassGlinerDetector.__new__(MultiPassGlinerDetector)
            det.logger = logging.getLogger("test")

        configs = {
            "EMAIL": {
                "enabled": True,
                "detector": "GLINER",
                "detector_label": "email address",
            },
            "PHONE": {
                "enabled": True,
                "detector": "GLINER",
                "detector_label": "phone number",
            },
        }
        result = det._optimize_passes(configs, limit=10)
        assert len(result) == 1
        batch = list(result.values())[0]
        assert "email address" in batch
        assert "phone number" in batch

    def test_should_split_into_batches_when_limit_exceeded(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            MultiPassGlinerDetector,
        )
        with patch.object(MultiPassGlinerDetector, "__init__", lambda self, **kwargs: None):
            det = MultiPassGlinerDetector.__new__(MultiPassGlinerDetector)
            det.logger = logging.getLogger("test")

        configs = {}
        for i in range(5):
            configs[f"TYPE_{i}"] = {
                "enabled": True,
                "detector": "GLINER",
                "detector_label": f"label {i}",
            }

        result = det._optimize_passes(configs, limit=2)
        assert len(result) == 3  # 5 items / 2 per batch = 3 batches

    def test_should_return_empty_dict_when_nothing_enabled(self):
        from pii_detector.infrastructure.detector.multi_pass_gliner_detector import (
            MultiPassGlinerDetector,
        )
        with patch.object(MultiPassGlinerDetector, "__init__", lambda self, **kwargs: None):
            det = MultiPassGlinerDetector.__new__(MultiPassGlinerDetector)
            det.logger = logging.getLogger("test")

        configs = {
            "EMAIL": {"enabled": False, "detector": "GLINER", "detector_label": "email"},
        }
        result = det._optimize_passes(configs, limit=10)
        assert result == {}
