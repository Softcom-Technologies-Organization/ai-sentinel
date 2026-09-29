from unittest.mock import patch

import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_type import PIIType
from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector


@pytest.fixture(scope="module")
def detector():
    config = {"detection": {"languages": ["en", "fr", "de"]}}
    with (
        patch.object(PresidioDetector, "_load_config", return_value=config),
        patch(
            "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter",
            return_value=None,
        ),
    ):
        instance = PresidioDetector()
        instance.load_model()
    return instance


@pytest.mark.parametrize(
    "value",
    [
        "4242424242424242",
        "4242-4242-4242-4242",
        "4242\u00a04242\u00a04242\u00a04242",
        "4242\u202f4242\u202f4242\u202f4242",
        "2223 0031 2200 3222",
        "2221000000000009",
        "2720000000000005",
        "4000000000000000006",
        "3782 822463 10005",
        "4222222222222",
    ],
)
def test_Should_DetectCompleteCardSpan_When_SupportedFormat(detector, value):
    text = f"Payment card: ({value})."
    config = {
        "CREDIT_CARD": {
            "detector": "PRESIDIO",
            "detector_label": "CREDIT_CARD",
            "enabled": True,
            "threshold": 0.9,
        }
    }

    results = detector.detect_pii(text, pii_type_configs=config)

    assert [(r.text, r.pii_type, r.start, r.end, r.source) for r in results] == [
        (value, PIIType.CREDIT_CARD, 15, 15 + len(value), DetectorSource.PRESIDIO)
    ]


@pytest.mark.parametrize(
    "value",
    [
        "4242 4242 4242 4243",
        "4242\u00a04242\u00a04242\u00a04243",
        "0000000000000000",
        "1000000000009",
        "1000 0000 0000 9",
        "2220000000000000",
        "2721000000000004",
        "A4242424242424242B",
        "942424242424242429999",
        "9999 4242 4242 4242 4242",
        "4242 4242 4242 4242 9",
        "4242\n4242\n4242\n4242",
    ],
)
def test_Should_RejectCardCandidate_When_InvalidOrEmbedded(detector, value):
    assert (
        detector._analyzer.analyze(
            f"Credit card: {value}",
            language="en",
            entities=["CREDIT_CARD"],
            score_threshold=0.01,
        )
        == []
    )


@pytest.mark.parametrize(
    "value",
    [
        "CH9300762011623852957",
        "CH93 0076 2011 6238 5295 7",
        "CH93-0076-2011-6238-5295-7",
        "CH93\u00a00076\u00a02011\u00a06238\u00a05295\u00a07",
        "CH93\u202f0076\u202f2011\u202f6238\u202f5295\u202f7",
        "ch93 0076 2011 6238 5295 7",
        "DE89 3704 0044 0532 0130 00",
        "GB82 WEST 1234 5698 7654 32",
        "FR14 2004 1010 0505 0001 3M02 606",
        "NO93 8601 1117 947",
    ],
)
def test_Should_DetectCompleteIbanSpan_When_SupportedFormat(detector, value):
    text = f"IBAN: ({value}) payable tomorrow."
    config = {
        "IBAN_CODE": {
            "detector": "PRESIDIO",
            "detector_label": "IBAN_CODE",
            "enabled": True,
            "threshold": 0.9,
        }
    }

    results = detector.detect_pii(text, pii_type_configs=config)

    assert [(r.text, r.pii_type, r.start, r.end) for r in results] == [
        (value, PIIType.IBAN, 7, 7 + len(value))
    ]


@pytest.mark.parametrize(
    "value",
    [
        "CH9300762011623852958",
        "CH93-0076-2011-6238-5295-8",
        "CH93\u00a00076\u00a02011\u00a06238\u00a05295\u00a08",
        "DE813704004405320130000",
        "ZZ22370400440532013000",
        "GB53 1234 WEST 5698 7654 32",
        "ACH9300762011623852957",
        "CH9300762011623852957X",
        "CH9300762011623852957-9999",
        "CH93 0076\n2011 6238 5295 7",
        "CH93 **** **** **** 5295 7",
        "BE41539007547035",
    ],
)
def test_Should_RejectIbanCandidate_When_InvalidOrEmbedded(detector, value):
    assert (
        detector._analyzer.analyze(
            f"IBAN: {value}",
            language="en",
            entities=["IBAN_CODE"],
            score_threshold=0.01,
        )
        == []
    )


@pytest.mark.parametrize("language", ["en", "fr", "de"])
def test_Should_UseCustomizedRecognizers_When_LanguageChanges(detector, language):
    text = "2223 0031 2200 3222; ch93 0076 2011 6238 5295 7"
    results = detector._analyzer.analyze(
        text,
        language=language,
        entities=["CREDIT_CARD", "IBAN_CODE"],
        score_threshold=0.9,
    )
    assert sorted((r.entity_type, text[r.start : r.end]) for r in results) == [
        ("CREDIT_CARD", "2223 0031 2200 3222"),
        ("IBAN_CODE", "ch93 0076 2011 6238 5295 7"),
    ]


def test_Should_RespectDisabledType_When_CustomRecognizerIsRegistered(detector):
    config = {
        "CREDIT_CARD": {"detector": "PRESIDIO", "detector_label": "CREDIT_CARD", "enabled": False},
        "IBAN_CODE": {"detector": "PRESIDIO", "detector_label": "IBAN_CODE", "enabled": True},
    }
    results = detector.detect_pii(
        "4242 4242 4242 4242; CH9300762011623852957",
        pii_type_configs=config,
    )
    assert [(r.pii_type, r.text) for r in results] == [(PIIType.IBAN, "CH9300762011623852957")]


def test_Should_KeepFinancialValidation_When_NlpInitializationFallsBack(detector):
    with (
        patch.object(PresidioDetector, "_load_config", return_value={}),
        patch(
            "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter",
            return_value=None,
        ),
        patch.object(PresidioDetector, "_create_blank_nlp_engine", side_effect=RuntimeError),
        patch(
            "presidio_analyzer.nlp_engine.NlpEngineProvider.create_engine",
            return_value=detector._analyzer.nlp_engine,
        ),
    ):
        fallback = PresidioDetector()
        fallback.load_model()

    text = "2223 0031 2200 3222; ch93 0076 2011 6238 5295 7; DE813704004405320130000"
    results = fallback._analyzer.analyze(
        text,
        language="en",
        entities=["CREDIT_CARD", "IBAN_CODE"],
        score_threshold=0.9,
    )
    assert sorted(text[r.start : r.end] for r in results) == [
        "2223 0031 2200 3222",
        "ch93 0076 2011 6238 5295 7",
    ]
