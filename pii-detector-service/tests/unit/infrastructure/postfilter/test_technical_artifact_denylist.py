"""Unit tests for the cross-label technical-artifact denylist.

Covers, for every denylist motif, at least one positive (reject) and one
negative (keep) case, plus every carve-out mandated by the post-filter brief:

- credential labels never dropped on ambiguous shapes (UUID, hex digests),
- JWTs and known secret prefixes always kept,
- IPv4 addresses and plausible dotted dates never eaten by the version motif,
- ``0x``-prefixed hex (Ethereum-style addresses) never treated as a digest,
- the label+shape pass only fires on intrinsically-technical labels,
- absolute fail-open on non-``str`` / empty / composite values.
"""

from __future__ import annotations

import pytest

from pii_detector.infrastructure.postfilter import technical_artifact_denylist


def _evaluate(value, pii_type="UNKNOWN"):
    return technical_artifact_denylist.evaluate(value, pii_type)


# ---------------------------------------------------------------------------
# UUID motif
# ---------------------------------------------------------------------------


class TestUuidMotif:
    @pytest.mark.parametrize(
        "value,pii_type",
        [
            ("3f2504e0-4f89-41d3-9a0c-0305e82c3301", "GUID"),
            ("9f1c2e7a-4b6d-4e2a-9c1f-3d8a7b6e5f40", "SESSION_ID"),
            ("Korrelations-GUID 3f2504e0-4f89-41d3-9a0c-0305e82c3301", "GUID"),
            ("CorrelationId 9f1c2e7a-4b6d-4e2a-9c1f-3d8a7b6e5f40", "GUID"),
        ],
    )
    def test_should_reject_uuid_shaped_artifact(self, value, pii_type) -> None:
        verdict = _evaluate(value, pii_type)
        assert verdict.keep is False
        assert "uuid" in verdict.reason

    @pytest.mark.parametrize(
        "pii_type",
        ["API_KEY", "PASSWORD", "SECRET", "ACCESS_TOKEN", "HTTP_COOKIE"],
    )
    def test_should_keep_uuid_when_label_is_credential(self, pii_type) -> None:
        # A UUID can be a real token / key: the credential carve-out wins.
        value = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        assert _evaluate(value, pii_type).keep is True

    @pytest.mark.parametrize(
        "value",
        [
            "a3f9-bb12-77ce",  # short hex groups, NOT a full UUID
            "8f3k-29dz-71qm",  # gold password with the same 4-4-4 shape
            "XKQR-5T8A-Z9PM-3RNF",  # licence key, not hex
        ],
    )
    def test_should_keep_non_uuid_dashed_groups(self, value) -> None:
        assert _evaluate(value, "SESSION_ID").keep is True


# ---------------------------------------------------------------------------
# Hex digest motifs (24 / 32 / 40 / 64)
# ---------------------------------------------------------------------------


class TestHexDigestMotifs:
    @pytest.mark.parametrize(
        "value,pii_type",
        [
            ("507f1f77bcf86cd799439011", "OBJECT_ID"),  # Mongo ObjectId (24)
            ("MongoDB ObjectId 507f1f77bcf86cd799439011", "OBJECT_ID"),
            ("Mongo document _id 507f1f77bcf86cd799439011", "OBJECT_ID"),
            ("9e107d9d372bb6826bd81d3542a419d6", "HASH"),  # MD5 (32)
            ("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", "COMMIT"),  # SHA-1 (40)
            (
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "DIGEST",
            ),  # SHA-256 (64)
        ],
    )
    def test_should_reject_full_hex_artifact_lengths(
        self, value, pii_type
    ) -> None:
        verdict = _evaluate(value, pii_type)
        assert verdict.keep is False
        assert "hex" in verdict.reason

    @pytest.mark.parametrize(
        "pii_type", ["API_KEY", "SECRET", "ACCESS_TOKEN", "PASSWORD"]
    )
    def test_should_keep_hex_when_label_is_credential(self, pii_type) -> None:
        # Uniform credential carve-out on every hex length: many real secrets
        # are pure hex, so the label wins over the shape.
        value = "507f1f77bcf86cd799439011"
        assert _evaluate(value, pii_type).keep is True

    def test_should_keep_hex16_out_of_artifact_lengths(self) -> None:
        # 16-hex is a common secret shape (gold api_key) -> never a motif.
        assert _evaluate("8a9f2c1d4b6e7081", "SECRET").keep is True
        assert _evaluate("8a9f2c1d4b6e7081", "UNKNOWN").keep is True

    def test_should_keep_0x_prefixed_hex40(self) -> None:
        # 0x + 40 hex is an Ethereum-style wallet address (financial PII).
        value = "0xa94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
        assert _evaluate(value, "WALLET_ADDRESS").keep is True

    def test_should_keep_hex_artifact_with_name_like_prefix(self) -> None:
        # The context prefix carries no technical keyword -> refuse to judge
        # the composite span (it may contain real PII such as a person name).
        value = "David 507f1f77bcf86cd799439011"
        assert _evaluate(value, "OBJECT_ID").keep is True


# ---------------------------------------------------------------------------
# W3C traceparent motif
# ---------------------------------------------------------------------------


class TestTraceparentMotif:
    @pytest.mark.parametrize(
        "value",
        [
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "Trace id 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        ],
    )
    def test_should_reject_traceparent(self, value) -> None:
        verdict = _evaluate(value, "TRACE_ID")
        assert verdict.keep is False
        assert "traceparent" in verdict.reason

    def test_Should_PreserveTraceparentShape_When_LabelIsCredential(self) -> None:
        value = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        assert _evaluate(value, "ACCESS_TOKEN").keep is True


# ---------------------------------------------------------------------------
# Version motif (SemVer / CalVer / firmware)
# ---------------------------------------------------------------------------


class TestVersionMotif:
    @pytest.mark.parametrize(
        "value",
        ["v2023.4.471", "5.8.0-rnf3", "2024.8.820", "1.2.3", "v10.0.0.beta1"],
    )
    def test_should_reject_version_string(self, value) -> None:
        verdict = _evaluate(value, "RELEASE_VERSION")
        assert verdict.keep is False
        assert "version" in verdict.reason

    def test_should_reject_version_string_cross_label(self) -> None:
        assert _evaluate("v2023.4.471", "UNKNOWN").keep is False

    @pytest.mark.parametrize(
        "value", ["10.0.4.27", "192.168.1.1", "172.22.0.5"]
    )
    def test_should_keep_valid_ipv4_despite_version_shape(self, value) -> None:
        # An IPv4 fullmatches the naive version regex: the parse guard wins.
        assert _evaluate(value, "IP_ADDRESS").keep is True
        assert _evaluate(value, "RELEASE_VERSION").keep is True

    @pytest.mark.parametrize(
        "value", ["18.02.1985", "1.2.25", "31.12.99", "2024.05.16", "1985.2.18"]
    )
    def test_should_keep_plausible_dotted_date(self, value) -> None:
        # Dotted dates (d.m.y, m.d.y and year-first y.m.d) are common
        # birth-date shapes: fail-open.
        assert _evaluate(value, "DATE").keep is True
        assert _evaluate(value, "UNKNOWN").keep is True

    @pytest.mark.parametrize("value", ["2024.8.820", "2023.4.471"])
    def test_should_still_reject_calver_with_out_of_range_day(
        self, value
    ) -> None:
        # Year-first CalVer stays rejected: the last component exceeds 31.
        assert _evaluate(value, "RELEASE_VERSION").keep is False

    def test_should_keep_two_component_number(self) -> None:
        # "6.6323" (a GPS coordinate fragment) has only two components.
        assert _evaluate("6.6323", "COORDINATE").keep is True

    def test_should_keep_ascii_avs_shape(self) -> None:
        assert _evaluate("756.1234.5678.97", "AVS_NUMBER").keep is True

    def test_should_not_match_unicode_digits_as_ascii_avs(self) -> None:
        value = "756.１２３４.５６７８.９７"
        assert technical_artifact_denylist._DOTTED_AVS_RE.fullmatch(value) is None
        assert _evaluate(value, "AVS_NUMBER").keep is True


# ---------------------------------------------------------------------------
# Base64-encoded image motif
# ---------------------------------------------------------------------------


class TestBase64ImageMotif:
    @pytest.mark.parametrize(
        "value",
        [
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk",
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJ",
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAE",
        ],
    )
    def test_should_reject_base64_image_blob(self, value) -> None:
        verdict = _evaluate(value, "BASE64")
        assert verdict.keep is False
        assert "base64" in verdict.reason

    def test_should_keep_bare_signature_below_blob_length(self) -> None:
        # The signature alone is too short to be an actual encoded image.
        assert _evaluate("iVBORw0KGgo", "BASE64").keep is True

    def test_should_keep_generic_long_base64_without_image_signature(
        self,
    ) -> None:
        # A generic base64 blob may be a credential (basic-auth, raw key):
        # only unambiguous image signatures are dropped.
        value = "QWxhZGRpbjpvcGVuIHNlc2FtZUFsYWRkaW46b3BlbiBzZXNhbWU="
        assert _evaluate(value, "SECRET").keep is True
        assert _evaluate(value, "UNKNOWN").keep is True


# ---------------------------------------------------------------------------
# Credential carve-outs (JWT + secret prefixes)
# ---------------------------------------------------------------------------


class TestCredentialCarveOuts:
    @pytest.mark.parametrize(
        "value",
        [
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0In0.dQw4w9WgXcQ",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",  # lone JWT segment
            "sk_live_4eC39HqLyjWDarjtT1zdp7dc",
            "sk-ant-api03-EXAMPLE",
            "ghp_xxxxxxxxxxxxxxxxxxxx",
            "github_pat_11EXAMPLE",
            "glpat-EXAMPLE-token",
            "AKIAIOSFODNN7EXAMPLE",
            "AIzaSyD-EXAMPLE-key",
            "Bearer ya29.a0AfH-EXAMPLE-token",
            "xoxb-1234-EXAMPLE",
            "-----BEGIN PRIVATE KEY-----",
        ],
    )
    def test_should_keep_known_credential_shapes(self, value) -> None:
        assert _evaluate(value, "UNKNOWN").keep is True


# ---------------------------------------------------------------------------
# Label+shape pass (weak shapes gated by intrinsically-technical labels)
# ---------------------------------------------------------------------------


class TestLabelShapePass:
    @pytest.mark.parametrize(
        "value,pii_type",
        [
            ("e28e530", "HASH"),  # short git sha, label says hash
            ("BUILD-SHA-9f1c2e7", "BUILD_SHA"),
            ("Build number 2024-8820", "BUILD_NUMBER"),
            ("BUILD-20240412", "BUILD_NUMBER"),
            ("202603115", "BUILD_NUMBER"),
            ("RELEASE-18.2.0", "RELEASE_VERSION"),
            ("APP-VER-3.4.1", "APPLICATION_VERSION"),
        ],
    )
    def test_should_reject_weak_shape_with_technical_label(
        self, value, pii_type
    ) -> None:
        verdict = _evaluate(value, pii_type)
        assert verdict.keep is False
        assert "label+shape" in verdict.reason

    @pytest.mark.parametrize(
        "value,pii_type",
        [
            ("e28e530", "CUSTOMER_ID"),  # same shape, non-technical label
            ("202603115", "PHONE_NUMBER"),
            ("2024-8820", "INVOICE_NUMBER"),
            ("737", "BUILD_NUMBER"),  # below the 4-digit floor
            ("$2b$10$N9qo8uLOickgx2ZMRZoMye", "HASH"),  # bcrypt, not hex
            ("Employee badge number 021000021", "BADGE"),
        ],
    )
    def test_should_keep_weak_shape_without_technical_label(
        self, value, pii_type
    ) -> None:
        assert _evaluate(value, pii_type).keep is True

    def test_should_keep_digit_prefixed_composite_span(self) -> None:
        # A multi-token span whose context contains digits is never judged.
        assert _evaluate("8801 2233 4455 6677", "BUILD_NUMBER").keep is True


# ---------------------------------------------------------------------------
# Absolute fail-open
# ---------------------------------------------------------------------------


class TestFailOpen:
    @pytest.mark.parametrize("value", [None, 42, "", "   "])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert _evaluate(value, "OBJECT_ID").keep is True

    def test_should_keep_plain_text(self) -> None:
        assert _evaluate("jdupont", "USERNAME").keep is True
        assert _evaluate("RSSMRA85T10A562S", "CODICE_FISCALE").keep is True
