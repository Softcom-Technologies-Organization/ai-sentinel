import json
from collections import Counter
from importlib.metadata import version
from pathlib import Path

from presidio_analyzer.predefined_recognizers import (
    CreditCardRecognizer,
    IbanRecognizer,
    UsSsnRecognizer,
)
from stdnum import iban, luhn
from stdnum.us import itin, ssn

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.infrastructure.detector.regex_detector import RegexDetector
from pii_detector.infrastructure.postfilter.format_postfilter_validator import (
    FormatPostfilterValidator,
)
from pii_detector.infrastructure.postfilter.registry import STRATEGIES


def entity(label, value):
    return PIIEntity(value, label, label, 0, len(value), 0.99, DetectorSource.MINISTRAL)


def main():
    validator = FormatPostfilterValidator()
    cases = [
        ("card_valid_spaces", "CREDIT_CARD", "4242 4242 4242 4242"),
        ("card_valid_hyphens", "CREDIT_CARD", "4242-4242-4242-4242"),
        ("card_invalid_checksum", "CREDIT_CARD", "4242 4242 4242 4243"),
        ("card_invalid_with_letters", "CREDIT_CARD", "AB4242424242424243"),
        ("card_12_digits", "CREDIT_CARD", "412345678901"),
        ("card_20_digits", "CREDIT_CARD", "41234567890123456789"),
        ("iban_valid_compact", "IBAN", "CH9300762011623852957"),
        ("iban_valid_spaces", "IBAN", "CH93 0076 2011 6238 5295 7"),
        ("iban_invalid_compact", "IBAN", "CH9300762011623852958"),
        ("iban_invalid_spaces", "IBAN", "CH93 0076 2011 6238 5295 8"),
        ("iban_invalid_hyphens", "IBAN", "CH93-0076-2011-6238-5295-8"),
        ("iban_invalid_nbsp", "IBAN", "CH93\u00a00076\u00a02011\u00a06238\u00a05295\u00a08"),
        ("iban_non_iban_words", "IBAN", "customer reference pending"),
        ("ssn_invalid_us_typed", "US_SSN", "000-12-3456"),
        ("ssn_invalid_generic_typed", "SOCIAL_SECURITY_NUMBER", "000-12-3456"),
        ("ssn_non_identifier", "SOCIAL_SECURITY_NUMBER", "customer reference pending"),
        ("avs_invalid_typed", "AVS_NUMBER", "756.9217.0769.84"),
        ("avs_invalid_generic_typed", "SOCIAL_SECURITY_NUMBER", "756.9217.0769.84"),
        ("tax_uid_valid", "TAX_ID", "CHE-116.281.710"),
        ("tax_uid_invalid", "TAX_ID", "CHE-116.281.711"),
        ("tax_unknown_format", "TAX_ID", "TX940039"),
        ("tax_non_identifier", "TAX_ID", "customer reference pending"),
    ]
    observations = []
    for name, label, value in cases:
        kept, rejected = validator.filter_with_verdicts(value, [entity(label, value)])
        observations.append({
            "case": name, "label": label, "value": value, "kept": bool(kept),
            "strategy": type(STRATEGIES[label]).__name__ if label in STRATEGIES else None,
            "reason": rejected[0][1].reason if rejected else None,
        })
    recognizers = [
        (CreditCardRecognizer(), "CREDIT_CARD", "4242 4242 4242 4242"),
        (CreditCardRecognizer(), "CREDIT_CARD", "2223 0031 2200 3222"),
        (CreditCardRecognizer(), "CREDIT_CARD", "4242\u00a04242\u00a04242\u00a04242"),
        (CreditCardRecognizer(), "CREDIT_CARD", "4000000000000000006"),
        (IbanRecognizer(), "IBAN_CODE", "CH93-0076-2011-6238-5295-7"),
        (IbanRecognizer(), "IBAN_CODE", "CH93\u00a00076\u00a02011\u00a06238\u00a05295\u00a07"),
        (IbanRecognizer(), "IBAN_CODE", "DE813704004405320130000"),
        (UsSsnRecognizer(), "US_SSN", "900-12-3456"),
    ]
    extraction = []
    for recognizer, label, value in recognizers:
        matches = recognizer.analyze(value, [label])
        extraction.append({
            "recognizer": type(recognizer).__name__, "value": value,
            "matches": [{"span": value[m.start:m.end], "score": m.score} for m in matches],
        })
    regex_detector = RegexDetector()
    avs = ["756.9217.0769.85", "7569217076985", "756 9217 0769 85", "756-9217-0769-85"]
    regex_extraction = [{"value": value, "count": len(regex_detector.detect_pii(value))} for value in avs]
    resources = Path(__file__).resolve().parents[3] / "pii-detector-service/tests/resources/ministral-postfilter"
    corpus_counts = {}
    for filename in ("gold-values.json", "raw-findings.json"):
        pairs = json.loads((resources / filename).read_text())
        kept, rejected = validator.filter_with_verdicts("", [entity(p["label"], p["text"]) for p in pairs])
        corpus_counts[filename] = {
            "total": len(pairs), "kept": len(kept), "rejected": len(rejected),
            "rejected_by_label": dict(Counter(e.pii_type for e, _ in rejected)),
        }
    current = {o["case"]: o["kept"] for o in observations}
    assert current["iban_invalid_hyphens"] and not current["iban_invalid_compact"]
    assert current["avs_invalid_generic_typed"] and not current["avs_invalid_typed"]
    assert not current["card_invalid_checksum"] and current["card_invalid_with_letters"]
    assert luhn.is_valid("2223003122003222") and not extraction[1]["matches"]
    assert not ssn.is_valid("900123456") and extraction[-1]["matches"]
    assert iban.is_valid("CH9300762011623852957")
    assert not iban.is_valid("DE813704004405320130000")
    assert luhn.is_valid("4000000000000000006")
    print(json.dumps({
        "versions": {p: version(p) for p in ("python-stdnum", "presidio-analyzer")},
        "scope": "Synthetic/public examples; direct components, not live configuration or end-to-end recall",
        "postfilter": observations, "presidio_extraction": extraction,
        "regex_avs_extraction": regex_extraction,
        "itin_group_50": {"value": "912-50-3456", "stdnum_accepts": itin.is_valid("912-50-3456")},
        "ssn_blacklist_separator_probe": {
            value: {"raw": ssn.is_valid(value), "compact": ssn.is_valid(ssn.compact(value))}
            for value in ("078-051120", "07805-1120", "078-05-1120")
        },
        "reference_corpus": corpus_counts,
    }, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
