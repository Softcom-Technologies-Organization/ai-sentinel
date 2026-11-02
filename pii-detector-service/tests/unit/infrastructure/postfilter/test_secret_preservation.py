import pytest

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.infrastructure.postfilter.format_postfilter_validator import (
    FormatPostfilterValidator,
)


@pytest.mark.parametrize(
    "pii_type",
    [
        "PASSWORD", "PASSPHRASE", "API_KEY", "CLIENT_SECRET", "SECRET",
        "ACCESS_TOKEN", "HTTP_COOKIE", "COOKIE", "TOKEN", "AUTH_TOKEN",
        "REFRESH_TOKEN", "SESSION_TOKEN", "CREDENTIAL", "CREDENTIALS",
        "PRIVATE_KEY", "AWS_ACCESS_KEY", "GOOGLE_API_KEY", "GITHUB_TOKEN",
        "SECRET_KEY", "JWT_TOKEN", "AWS_KEY", "CONNECTION_STRING",
        "password", "api key", "client-secret",
    ],
)
@pytest.mark.parametrize(
    "value",
    [
        "a",
        "1234",
        "secret",
        "sunshine",
        "the moon is blue",
        "mot de passe",
        "ZZZZ",
        "mySafePassword",
        "spring.datasource.password",
        "${DB_PASSWORD}",
        "1.2.3",
        "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
        "507f1f77bcf86cd799439011",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk",
    ],
)
def test_Should_PreserveSecret_When_ValueIsWeakOrLooksLikeArtifact(pii_type, value):
    content = f'The account credential is "{value}".'
    entity = _entity(value, pii_type, len('The account credential is "'))
    assert FormatPostfilterValidator().filter_with_verdicts(content, [entity]) == ([entity], [])


@pytest.mark.parametrize(
    "pii_type,value",
    [
        ("PASSWORD", "password=ab1"),
        ("PASSWORD", "webservice.password = 'ab1'"),
        ("PASSWORD", "OWNER_PASS= ab1"),
        ("API_KEY", "api_key=sk_x"),
        ("ACCESS_TOKEN", "Bearer abc"),
        ("GITHUB_TOKEN", "GITHUB_TOKEN=ghp_abc"),
        ("HTTP_COOKIE", "JSESSIONID=abc; Path=/; HttpOnly"),
        ("HTTP_COOKIE", 'Cookie: JSESSIONID="abc"; Secure'),
    ],
)
def test_Should_PreserveSecret_When_ShortValueHasAssignmentOrWrapper(pii_type, value):
    entity = _entity(value, pii_type, 0)
    assert FormatPostfilterValidator().filter_with_verdicts(value, [entity]) == ([entity], [])


def _entity(value, pii_type, start):
    return PIIEntity(
        text=value,
        pii_type=pii_type,
        type_label=pii_type,
        start=start,
        end=start + len(value),
        score=1.0,
        source=DetectorSource.MINISTRAL,
    )
