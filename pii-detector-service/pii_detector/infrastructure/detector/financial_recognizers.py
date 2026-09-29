import re

from presidio_analyzer import Pattern, PatternRecognizer, RecognizerRegistry
from presidio_analyzer.predefined_recognizers import CreditCardRecognizer, IbanRecognizer
from stdnum import iban, numdb

_SEPARATORS = " \u00a0\u202f-"
_SEPARATOR_PATTERN = r"[ \u00a0\u202f-]"
_REMOVE_SEPARATORS = str.maketrans("", "", _SEPARATORS)
_CARD_PREFIX = (
    r"(?:[1346][0-9]{3}|5[0-5][0-9]{2}|222[1-9]|22[3-9][0-9]|2[3-6][0-9]{2}|27[01][0-9]|2720)"
)
_CARD_PATTERN = Pattern(
    "Payment card with bounded separators",
    rf"(?<!\w)(?<![0-9]{_SEPARATOR_PATTERN})"
    rf"(?!1(?:{_SEPARATOR_PATTERN}?[0-9]){{12}}(?!{_SEPARATOR_PATTERN}?[0-9]))"
    rf"{_CARD_PREFIX}"
    rf"(?:{_SEPARATOR_PATTERN}?[0-9]){{9,15}}(?!\w|{_SEPARATOR_PATTERN}?[0-9])",
    0.3,
)


def _iban_pattern() -> Pattern:
    countries = []
    for _, country, _, properties, _ in numdb.get("iban").prefixes:
        length = sum(int(size) for size in re.findall(r"([0-9]+)!", properties["bban"]))
        blocks, remainder = divmod(length, 4)
        pattern = rf"{country}[0-9]{{2}}(?:{_SEPARATOR_PATTERN}?[A-Z0-9]{{4}}){{{blocks}}}"
        if remainder:
            pattern += rf"{_SEPARATOR_PATTERN}?[A-Z0-9]{{{remainder}}}"
        countries.append(pattern)
    return Pattern(
        "IBAN with registered country length",
        r"(?<!\w)(?:" + "|".join(countries) + r")(?!\w|-[A-Z0-9])",
        0.5,
    )


class CountryIbanRecognizer(PatternRecognizer):
    def __init__(self, language: str):
        super().__init__(
            supported_entity="IBAN_CODE",
            supported_language=language,
            name="IbanRecognizer",
            patterns=[_iban_pattern()],
            context=IbanRecognizer.CONTEXT,
        )

    def validate_result(self, pattern_text: str) -> bool:
        canonical = pattern_text.translate(_REMOVE_SEPARATORS).upper()
        return iban.is_valid(canonical)


def configure_financial_recognizers(registry: RecognizerRegistry, languages: list[str]) -> None:
    registry.remove_recognizer("CreditCardRecognizer")
    registry.remove_recognizer("IbanRecognizer")
    for language in languages:
        registry.add_recognizer(
            CreditCardRecognizer(
                supported_language=language,
                patterns=[_CARD_PATTERN],
                replacement_pairs=[(separator, "") for separator in _SEPARATORS],
            )
        )
        registry.add_recognizer(CountryIbanRecognizer(language))
