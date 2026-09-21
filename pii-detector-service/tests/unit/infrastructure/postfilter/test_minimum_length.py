import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.infrastructure.postfilter.format_postfilter_validator import (
    FormatPostfilterValidator,
)


@pytest.mark.parametrize(
    "pii_type,short_value,accepted_value",
    [
        ("PASSWORD", "Ab1!x", "Ab1!xy"),
        ("API_KEY", "aB3!xY8", "aB3!xY8?"),
        ("ACCESS_TOKEN", "aB3!xY8", "aB3!xY8?"),
        ("CLIENT_SECRET", "aB3!xY8", "aB3!xY8?"),
        ("HTTP_COOKIE", "aB3!xY8?z", "aB3!xY8?z9"),
        ("GITHUB_TOKEN", "ghp_" + "a" * 35, "ghp_" + "a" * 36),
        ("CRYPTO", "r" + "p" * 23, "r" + "p" * 24),
        ("CRYPTO_WALLET", "r" + "p" * 23, "r" + "p" * 24),
        ("NRP", "Y", "Yi"),
        ("IBAN", "NO938601117947", "NO9386011117947"),
        ("IBAN_CODE", "NO938601117947", "NO9386011117947"),
        ("SWIFT_BIC", "DEUTDEF", "DEUTDEFF"),
        ("CREDIT_CARD", "12345678901", "123456789012"),
        ("CREDIT_DEBIT_CARD", "12345678901", "123456789012"),
        ("AVS_NUMBER", "1234567890", "12345678901"),
        ("MAC_ADDRESS", "01:23:45:67:89:a", "01:23:45:67:89:ab"),
        *[
            (kind, "1234", "12345")
            for kind in (
                "ACCOUNT_NUMBER",
                "BANK_ROUTING_NUMBER",
                "NATIONAL_ID",
                "TAX_ID",
                "DRIVER_LICENSE_NUMBER",
                "CERTIFICATE_LICENSE_NUMBER",
                "LICENSE_PLATE",
            )
        ],
    ],
)
def test_Should_EnforceMinimum_When_ValueCrossesBoundary(pii_type, short_value, accepted_value):
    entities = [_entity(value, pii_type) for value in (short_value, accepted_value)]
    kept, rejected = FormatPostfilterValidator().filter_with_verdicts("", entities)
    assert kept == entities[1:]
    assert [entity for entity, _ in rejected] == entities[:1]
    assert "minimum length" in rejected[0][1].reason


@pytest.mark.parametrize(
    "pii_type,value",
    [
        ("password", "  Ab1!x  "),
        ("PASSWORD", '"Ab1!x"'),
        ("PASSWORD", "password=ab1"),
        ("PASSWORD", "webservice.password = 'ab1'"),
        ("PASSWORD", "OWNER_PASS= ab1"),
        ("API_KEY", "api_key=sk_x"),
        ("ACCESS_TOKEN", "Bearer abc"),
        ("GITHUB_TOKEN", "GITHUB_TOKEN=ghp_abc"),
        ("HTTP_COOKIE", "JSESSIONID=abc; Path=/; HttpOnly"),
        ("HTTP_COOKIE", 'Cookie: JSESSIONID="abc"; Secure'),
        ("ACCOUNT_NUMBER", "1 - 2 . 3 / 4"),
        ("credit debit card", "1234 5678 901"),
        ("AVS_NUMBER", "123.456.789.0"),
        ("MAC_ADDRESS", "01:23:45:67:89:zz"),
        ("NRP", " Y. "),
    ],
)
def test_Should_IgnoreWrappers_When_CountingValue(pii_type, value):
    entity = _entity(value, pii_type)
    kept, rejected = FormatPostfilterValidator().filter_with_verdicts("", [entity])
    assert kept == []
    assert rejected[0][0] is entity
    assert "minimum length" in rejected[0][1].reason
    assert value not in rejected[0][1].reason


@pytest.mark.parametrize(
    "pii_type,value",
    [
        ("PASSWORD", "Abc123=="),
        ("PASSWORD", "Abc123="),
        ("PASSWORD", "xy z7!"),
        ("PASSWORD", "password='Ab1!xy'"),
        ("CLIENT_SECRET", "3f2504e0-4f89-41d3-9a0c-0305e82c3301"),
        ("HTTP_COOKIE", "YWJjZGVmZ2hp=="),
        ("HTTP_COOKIE", "JSESSIONID=aB3!xY8?z9; Path=/; HttpOnly"),
        ("GITHUB_TOKEN", "abcdef0123456789" * 2 + "abcd1234"),
        ("IPV6", "::1"),
        ("EMAIL", "a@b.c"),
        *[
            (kind, value)
            for kind in (
                "SOCIAL_SECURITY_NUMBER",
                "SSN",
                "US_SSN",
                "SOCIALNUM",
            )
            for value in ("1234", "123456")
        ],
    ],
)
def test_Should_PreserveValues_When_MinimumDoesNotReject(pii_type, value):
    entity = _entity(value, pii_type)
    assert FormatPostfilterValidator().filter_with_verdicts("", [entity]) == ([entity], [])


def _entity(value, pii_type):
    return PIIEntity(
        text=value,
        pii_type=pii_type,
        type_label=pii_type,
        start=0,
        end=len(value),
        score=1.0,
        source=DetectorSource.MINISTRAL,
    )
