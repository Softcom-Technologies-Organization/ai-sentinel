"""Precision thresholds for complete values, not universal validity requirements."""

import re
from string import hexdigits

from pii_detector.infrastructure.postfilter.postfilter_strategy import PostfilterVerdict

MINIMUM_LENGTHS = {
    "PASSWORD": 6,
    "API_KEY": 8,
    "ACCESS_TOKEN": 8,
    "CLIENT_SECRET": 8,
    "HTTP_COOKIE": 10,
    "COOKIE": 10,
    "GITHUB_TOKEN": 40,
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
_SECRET_TYPES = {"PASSWORD", "API_KEY", "ACCESS_TOKEN", "CLIENT_SECRET", "GITHUB_TOKEN"}
_COOKIE_TYPES = {"HTTP_COOKIE", "COOKIE"}
_SECRET_ASSIGNMENT = re.compile(
    r"^(?:[\w.-]+[._-])?(?:password|passwd|pwd|mdp|passw?|api[_-]?key|"
    r"access[_-]?token|client[_-]?secret|github[_-]?token|token|secret)\s*[:=]\s*(.*)$",
    re.IGNORECASE | re.DOTALL,
)
_COOKIE_NAME = re.compile(r"[\w.-]+")


def _unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]:
        return value[1:-1]
    return value


def _secret_value(value: str, pii_type: str) -> str:
    span = _unquote(value)
    if pii_type in _COOKIE_TYPES:
        span = re.sub(r"^(?:set-cookie|cookie):\s*", "", span, flags=re.IGNORECASE)
        # Bare base64 padding is ambiguous with an empty cookie assignment.
        if re.fullmatch(r"[A-Za-z0-9+/]+={1,2}", span):
            return span
        name, separator, cookie_value = span.partition("=")
        if separator and _COOKIE_NAME.fullmatch(name.strip()):
            span = _unquote(cookie_value.lstrip().partition(";")[0])
    else:
        assignment = _SECRET_ASSIGNMENT.fullmatch(span)
        if assignment:
            span = _unquote(assignment.group(1))
    if pii_type != "PASSWORD":
        span = re.sub(r"^(?:Bearer|Basic)\s+", "", span, flags=re.IGNORECASE)
    return span


def evaluate(value: str, pii_type: str) -> PostfilterVerdict | None:
    minimum = MINIMUM_LENGTHS.get(pii_type)
    if minimum is None or not isinstance(value, str):
        return None
    if pii_type in _SECRET_TYPES | _COOKIE_TYPES:
        length = len(_secret_value(value, pii_type))
    elif pii_type in _DIGIT_TYPES:
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
