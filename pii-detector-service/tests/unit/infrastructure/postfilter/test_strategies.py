"""Unit tests for the GO pre-filter strategies and the registry.

Covers the verdicts pinned in ``PLAN.md`` T2 for IP_ADDRESS, MAC_ADDRESS and
IBAN, plus the fail-open invariants shared by every strategy (non-``str``
values, empty strings, exotic types always PASS). The MAC rule under test is
the "mixed separator" rule, never a "6 hex octets" rule.
"""

from __future__ import annotations

import pytest

from pii_detector.infrastructure.postfilter.postfilter_strategy import (
    PostfilterStrategy,
)
from pii_detector.infrastructure.postfilter.registry import STRATEGIES
from pii_detector.infrastructure.postfilter.strategies.avs_number import (
    AvsNumberStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.card_number import (
    CardNumberStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.iban import IbanStrategy
from pii_detector.infrastructure.postfilter.strategies.ip_address import (
    IpAddressStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.mac_address import (
    MacAddressStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.swift_bic import (
    SwiftBicStrategy,
)
from pii_detector.infrastructure.postfilter.strategies.swiss_uid import (
    SwissUidStrategy,
)


# ---------------------------------------------------------------------------
# IP_ADDRESS
# ---------------------------------------------------------------------------


class TestIpAddressStrategy:
    @pytest.mark.parametrize(
        "value",
        [
            "0.244.999.7",  # octet out of range -> parse failure
            "756.7017.9436.43",  # mis-typed AVS, 4 dotted groups, fails parse
            "vd.ch. 3600",  # fragment with a dot, fails parse (reject)
        ],
    )
    def test_should_reject_when_value_looks_like_ip_but_fails_parse(
        self, value: str
    ) -> None:
        verdict = IpAddressStrategy().evaluate(value)
        assert verdict.keep is False
        assert "parse failed" in verdict.reason

    def test_should_keep_real_ipv4(self) -> None:
        assert IpAddressStrategy().evaluate("10.217.4.11").keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        # None / int / empty string never look like an IP -> always PASS.
        assert IpAddressStrategy().evaluate(value).keep is True

    def test_should_keep_pure_digits_without_separator(self) -> None:
        # Avoids the implicit ip_address(int) path -> kept.
        assert IpAddressStrategy().evaluate("3600").keep is True


# ---------------------------------------------------------------------------
# MAC_ADDRESS
# ---------------------------------------------------------------------------


class TestMacAddressStrategy:
    def test_should_reject_time_range_with_mixed_separators(self) -> None:
        verdict = MacAddressStrategy().evaluate("13:56:49-13:56:52")
        assert verdict.keep is False
        assert "separator" in verdict.reason

    @pytest.mark.parametrize(
        "value",
        [
            "98:e7:43:a6:3f:0a",  # canonical colon MAC
            "98-e7-43-a6-3f-0a",  # canonical hyphen MAC
        ],
    )
    def test_should_keep_canonical_mac_with_single_separator(
        self, value: str
    ) -> None:
        assert MacAddressStrategy().evaluate(value).keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert MacAddressStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# IBAN
# ---------------------------------------------------------------------------


class TestIbanStrategy:
    def test_should_keep_valid_iban(self) -> None:
        assert IbanStrategy().evaluate("CH9300762011623852957").keep is True

    def test_should_reject_canonical_iban_failing_mod97(self) -> None:
        verdict = IbanStrategy().evaluate("CH9300762011623852958")
        assert verdict.keep is False
        assert "mod-97" in verdict.reason

    def test_should_fail_open_on_truncated_fragment(self) -> None:
        # Non-canonical shape -> keep (fail-open), never a checksum verdict.
        assert IbanStrategy().evaluate("I241015_00045").keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert IbanStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# CARD_NUMBER (v2)
# ---------------------------------------------------------------------------


class TestCardNumberStrategy:
    def test_should_keep_test_card_passing_luhn(self) -> None:
        # Format-only filter: a valid-Luhn test card PASSes (no test/prod call).
        assert CardNumberStrategy().evaluate("4242424242424242").keep is True

    @pytest.mark.parametrize(
        "value",
        [
            "4242 4242 4242 4241",  # 16 digits, Luhn KO
            "12345678901234",  # 14 digits, Luhn KO
            "33333333333331",  # 14 digits, Luhn KO (log/session number)
        ],
    )
    def test_should_reject_in_range_value_failing_luhn(
        self, value: str
    ) -> None:
        verdict = CardNumberStrategy().evaluate(value)
        assert verdict.keep is False
        assert "luhn" in verdict.reason

    @pytest.mark.parametrize(
        "value",
        [
            "ABCD1234EFGH5678",  # letters after strip -> not numeric -> keep
            "I241015_00045",  # truncated fragment -> keep
            "42424242",  # 8 digits, below PAN range -> keep
        ],
    )
    def test_should_fail_open_on_non_numeric_or_out_of_range(
        self, value: str
    ) -> None:
        assert CardNumberStrategy().evaluate(value).keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert CardNumberStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# AVS_NUMBER (v2)
# ---------------------------------------------------------------------------


class TestAvsNumberStrategy:
    def test_should_keep_valid_avs(self) -> None:
        # Ground truth (research §1): valid EAN-13 key -> PASS.
        assert AvsNumberStrategy().evaluate("756.9217.0769.85").keep is True

    @pytest.mark.parametrize(
        "value",
        [
            "756.9217.0769.84",  # valid 756-13 shape, checksum KO
            "756.1234.1234.12",  # synthetic test AVS, wrong key
        ],
    )
    def test_should_reject_756_thirteen_digits_failing_checksum(
        self, value: str
    ) -> None:
        verdict = AvsNumberStrategy().evaluate(value)
        assert verdict.keep is False
        assert "checksum" in verdict.reason

    def test_should_fail_open_on_legacy_eleven_digits(self) -> None:
        # 11-digit legacy AVS has no EAN-13 checksum -> InvalidLength -> keep.
        assert AvsNumberStrategy().evaluate("12345678901").keep is True

    def test_should_fail_open_on_non_756_prefix(self) -> None:
        # 13 digits but prefix != 756 -> InvalidComponent -> keep (NOT reject).
        assert AvsNumberStrategy().evaluate("123.4567.8910.19").keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert AvsNumberStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# TAX_ID / TAX_NUMBER -- Swiss UID (v2b)
# ---------------------------------------------------------------------------


class TestSwissUidStrategy:
    @pytest.mark.parametrize(
        "value",
        [
            "CHE-116.281.710",  # UID of the Confederation, valid mod-11
            "CHE 116.281.710",  # space separator -> same compact form
            "CHE116281710",  # already compact
            "CHE-116.281.710 TVA",  # tolerated VAT suffix -> stripped
            "CHE-116.281.710 MWST",
            "CHE-116.281.710 IVA",
        ],
    )
    def test_should_keep_valid_swiss_uid(self, value: str) -> None:
        # Ground truth: valid mod-11 check digit -> PASS (any tolerated form).
        assert SwissUidStrategy().evaluate(value).keep is True

    def test_should_reject_strict_form_failing_mod11(self) -> None:
        verdict = SwissUidStrategy().evaluate("CHE-116.281.711")
        assert verdict.keep is False
        assert "mod-11" in verdict.reason

    @pytest.mark.parametrize(
        "value",
        [
            "CHE-123",  # truncated -> not the strict CHE+9 form -> keep
            "TX940039",  # foreign tax id out of template -> keep
        ],
    )
    def test_should_fail_open_when_not_strict_uid_form(
        self, value: str
    ) -> None:
        assert SwissUidStrategy().evaluate(value).keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert SwissUidStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# SWIFT_BIC (Tier C)
# ---------------------------------------------------------------------------


class TestSwiftBicStrategy:
    @pytest.mark.parametrize(
        "value",
        [
            "DEUTDEFF",  # valid 8-char BIC
            "UBSWCHZH80A",  # valid 11-char BIC
            "POFICHBEXXX",  # valid 11-char BIC with XXX branch
            "ubswchzh80a",  # lowercase input -> normalised before check
        ],
    )
    def test_should_keep_valid_bic(self, value: str) -> None:
        assert SwiftBicStrategy().evaluate(value).keep is True

    @pytest.mark.parametrize(
        "value",
        [
            "12345678",  # 8 chars but digits in the bank code
            "DEUT12FF",  # digits in the country code
            "DEUTZZFF",  # unknown ISO country
        ],
    )
    def test_should_reject_full_length_value_failing_structure(
        self, value: str
    ) -> None:
        verdict = SwiftBicStrategy().evaluate(value)
        assert verdict.keep is False
        assert "structure" in verdict.reason

    @pytest.mark.parametrize(
        "value",
        [
            "DEUTDEFF5",  # 9 chars: neither 8 nor 11 -> truncated -> keep
            "DEUTDE",  # 6 chars fragment -> keep
            "DEUT DEFF",  # embedded space -> not a canonical claim -> keep
        ],
    )
    def test_should_fail_open_when_not_a_full_bic_claim(
        self, value: str
    ) -> None:
        assert SwiftBicStrategy().evaluate(value).keep is True

    @pytest.mark.parametrize("value", [None, 42, ""])
    def test_should_fail_open_on_non_str_or_empty(self, value) -> None:
        assert SwiftBicStrategy().evaluate(value).keep is True


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------


class TestRegistry:
    def test_should_key_strategies_on_normalised_pii_type(self) -> None:
        assert set(STRATEGIES) == {
            "IP_ADDRESS",
            "IPV4",
            "IPV6",
            "MAC_ADDRESS",
            "IBAN",
            "IBAN_CODE",
            "CARD_NUMBER",
            "PAYMENT_CARD",
            "CREDIT_CARD",
            "CREDIT_DEBIT_CARD",
            "AVS_NUMBER",
            "NATIONAL_ID_NUMBER",
            "TAX_ID",
            "TAX_NUMBER",
            "SWIFT_BIC",
            "BIC",
            "SWIFT_CODE",
            "SWIFT",
        }

    def test_should_register_strategies_satisfying_the_protocol(self) -> None:
        for strategy in STRATEGIES.values():
            assert isinstance(strategy, PostfilterStrategy)

    def test_should_alias_payment_card_to_card_number_instance(self) -> None:
        # PAYMENT_CARD is a pure alias: the SAME instance, not a copy.
        assert STRATEGIES["PAYMENT_CARD"] is STRATEGIES["CARD_NUMBER"]

    def test_should_alias_national_id_to_avs_number_instance(self) -> None:
        assert STRATEGIES["NATIONAL_ID_NUMBER"] is STRATEGIES["AVS_NUMBER"]

    def test_should_share_one_swiss_uid_instance_across_tax_keys(self) -> None:
        assert STRATEGIES["TAX_ID"] is STRATEGIES["TAX_NUMBER"]

    def test_should_reject_via_payment_card_alias_key(self) -> None:
        # evaluate() routed through the alias key behaves like CARD_NUMBER.
        verdict = STRATEGIES["PAYMENT_CARD"].evaluate("4242424242424241")
        assert verdict.keep is False
        assert "luhn" in verdict.reason

    def test_should_alias_ipv4_to_ip_address_instance(self) -> None:
        assert STRATEGIES["IPV4"] is STRATEGIES["IP_ADDRESS"]

    def test_should_alias_credit_card_to_card_number_instance(self) -> None:
        assert STRATEGIES["CREDIT_CARD"] is STRATEGIES["CARD_NUMBER"]

    def test_should_reject_unparsable_ip_via_ipv4_alias_key(self) -> None:
        # Real finding from the 2026-07 scan: IPV4-labelled non-address.
        verdict = STRATEGIES["IPV4"].evaluate("56.1234.1234.12")
        assert verdict.keep is False
        assert "parse failed" in verdict.reason

    def test_should_pass_valid_avs_via_national_id_alias_key(self) -> None:
        # Corpus value with a valid EAN-13 key -> PASS through the alias.
        assert (
            STRATEGIES["NATIONAL_ID_NUMBER"].evaluate("756.3865.9392.03").keep
            is True
        )

    def test_should_reject_invalid_avs_via_national_id_alias_key(self) -> None:
        verdict = STRATEGIES["NATIONAL_ID_NUMBER"].evaluate("756.9217.0769.84")
        assert verdict.keep is False
        assert "checksum" in verdict.reason
