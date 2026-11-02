"""Precision thresholds for complete values, not universal validity requirements."""

from string import hexdigits

from pii_detector.infrastructure.postfilter.postfilter_strategy import PostfilterVerdict

MINIMUM_LENGTHS = {
    "CRYPTO": 25,
    "CRYPTO_WALLET": 25,
    "NRP": 2,
    "ACCOUNT_NUMBER": 5,
    "BANK_ROUTING_NUMBER": 5,
    "NATIONAL_ID": 5,
    "NATIONAL_ID_NUMBER": 5,
    "TAX_ID": 5,
    "TAX_NUMBER": 5,
    "DRIVER_LICENSE_NUMBER": 5,
    "CERTIFICATE_LICENSE_NUMBER": 5,
    "LICENSE_PLATE": 5,
    "IBAN": 15,
    "IBAN_CODE": 15,
    "SWIFT_BIC": 8,
    "BIC": 8,
    "SWIFT_CODE": 8,
    "SWIFT": 8,
    "CARD_NUMBER": 12,
    "CREDIT_CARD": 12,
    "CREDIT_DEBIT_CARD": 12,
    "PAYMENT_CARD": 12,
    "AVS_NUMBER": 11,
    "MAC_ADDRESS": 12,
}

_DIGIT_TYPES = {"CARD_NUMBER", "CREDIT_CARD", "CREDIT_DEBIT_CARD", "PAYMENT_CARD", "AVS_NUMBER"}


def evaluate(value: str, pii_type: str) -> PostfilterVerdict | None:
    minimum = MINIMUM_LENGTHS.get(pii_type)
    if minimum is None or not isinstance(value, str):
        return None
    if pii_type in _DIGIT_TYPES:
        length = sum(character.isdecimal() for character in value)
    elif pii_type == "MAC_ADDRESS":
        length = sum(character in hexdigits for character in value)
    else:
        length = sum(character.isalnum() for character in value)
    if length < minimum:
        return PostfilterVerdict(
            False, f"minimum length: {pii_type} measured {length}, required {minimum}"
        )
    return None
