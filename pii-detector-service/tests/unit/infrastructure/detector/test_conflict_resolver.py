"""
Test suite for ConflictResolver class.

This module contains comprehensive tests for the ConflictResolver class,
covering pattern-based resolution, category priority fallback, and conflict statistics.

CONSOLIDATED: Tests the 44 PII types across 7 categories.
"""

import logging

import pytest

from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.infrastructure.detector.conflict_resolver import (
    ConflictResolver,
    CONFLICT_GROUPS,
)


class TestConflictResolverInitialization:
    """Test cases for ConflictResolver initialization."""

    def test_should_initialize_with_empty_category_mapping(self):
        """Test initialization with no category mapping."""
        resolver = ConflictResolver()

        assert resolver.pii_type_to_category == {}
        assert len(resolver._compiled_group_patterns) == len(CONFLICT_GROUPS)
        assert len(resolver._compiled_type_patterns) == len(CONFLICT_GROUPS)

    def test_should_initialize_with_category_mapping(self):
        """Test initialization with category mapping provided."""
        mapping = {
            "IP_ADDRESS": "IT_CREDENTIALS",
            "AVS_NUMBER": "MEDICAL",
            "EMAIL": "CONTACT",
        }
        resolver = ConflictResolver(pii_type_to_category=mapping)

        assert resolver.pii_type_to_category == mapping
        assert resolver.pii_type_to_category["IP_ADDRESS"] == "IT_CREDENTIALS"

    def test_should_precompile_all_patterns(self):
        """Test that all regex patterns are pre-compiled."""
        resolver = ConflictResolver()

        # All group patterns should be compiled
        for group in CONFLICT_GROUPS:
            assert group.name in resolver._compiled_group_patterns
            # Verify it's a compiled pattern (has match method)
            assert hasattr(resolver._compiled_group_patterns[group.name], 'match')

            # All type patterns within group should be compiled
            assert group.name in resolver._compiled_type_patterns
            for pii_type in group.type_patterns:
                assert pii_type in resolver._compiled_type_patterns[group.name]
                assert hasattr(
                    resolver._compiled_type_patterns[group.name][pii_type], 'match'
                )

    def test_should_initialize_conflict_stats(self):
        """Test that conflict statistics are initialized to zero."""
        resolver = ConflictResolver()

        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] == 0
        assert stats["resolved_by_pattern"] == 0
        assert stats["resolved_by_fallback"] == 0
        assert stats["resolved_by_category"] == 0


class TestResolveSingleLabel:
    """Test cases for single-label and empty-label resolution."""

    def test_should_return_single_label_immediately(self):
        """Test that single label is returned without resolution logic."""
        resolver = ConflictResolver()
        detected_labels = [("EMAIL", 0.95)]

        result = resolver.resolve("john@example.com", detected_labels)

        assert result == ("EMAIL", 0.95)
        # No conflict should be tracked
        assert resolver.get_conflict_stats()["total_conflicts"] == 0

    def test_should_return_none_for_empty_labels(self):
        """Test that empty labels returns None."""
        resolver = ConflictResolver()

        result = resolver.resolve("some text", [])

        assert result is None


class TestResolveNumericDottedGroup:
    """Test cases for NUMERIC_DOTTED conflict group (IP vs AVS)."""

    @pytest.fixture
    def resolver_with_categories(self) -> ConflictResolver:
        """Create resolver with category mapping."""
        return ConflictResolver(pii_type_to_category={
            "IP_ADDRESS": "IT_CREDENTIALS",
            "AVS_NUMBER": "MEDICAL",
            "MEDICAL_RECORD_NUMBER": "MEDICAL",
        })

    def test_should_resolve_ip_address_by_pattern(self, resolver_with_categories):
        """Test IP address is correctly identified by pattern."""
        text = "192.168.1.1"
        detected_labels = [("IP_ADDRESS", 0.85), ("AVS_NUMBER", 0.80)]

        result = resolver_with_categories.resolve(text, detected_labels, "test-001")

        assert result is not None
        assert result[0] == "IP_ADDRESS"
        stats = resolver_with_categories.get_conflict_stats()
        assert stats["total_conflicts"] == 1
        assert stats["resolved_by_pattern"] == 1

    def test_should_resolve_avs_number_by_pattern(self, resolver_with_categories):
        """Test Swiss AVS number is correctly identified by pattern."""
        text = "756.1234.5678.90"
        detected_labels = [("IP_ADDRESS", 0.75), ("AVS_NUMBER", 0.90)]

        result = resolver_with_categories.resolve(text, detected_labels, "test-002")

        assert result is not None
        assert result[0] == "AVS_NUMBER"
        stats = resolver_with_categories.get_conflict_stats()
        assert stats["resolved_by_pattern"] == 1

    def test_should_use_fallback_priority_when_no_pattern_matches(
        self, resolver_with_categories
    ):
        """Test fallback priority when patterns don't match."""
        # This format doesn't match IP or AVS exactly
        text = "123.456.789.012"
        detected_labels = [("IP_ADDRESS", 0.70), ("AVS_NUMBER", 0.65)]

        result = resolver_with_categories.resolve(text, detected_labels, "test-003")

        # Should use fallback priority (IP_ADDRESS is first in fallback)
        assert result is not None
        assert result[0] == "IP_ADDRESS"

    def test_should_resolve_valid_ip_range(self, resolver_with_categories):
        """Test various valid IP addresses."""
        valid_ips = ["10.0.0.1", "255.255.255.255", "1.1.1.1", "172.16.0.254"]

        for ip in valid_ips:
            detected_labels = [("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)]
            result = resolver_with_categories.resolve(ip, detected_labels)

            assert result is not None
            assert result[0] == "IP_ADDRESS", f"Failed for IP: {ip}"


class TestResolveNumericDashedGroup:
    """Test cases for NUMERIC_DASHED conflict group (SSN, Phone, etc.)."""

    @pytest.fixture
    def resolver_with_categories(self) -> ConflictResolver:
        """Create resolver with category mapping."""
        return ConflictResolver(pii_type_to_category={
            "SSN": "IDENTITY",
            "NATIONAL_ID": "IDENTITY",
            "PHONE_NUMBER": "CONTACT",
            "BANK_ACCOUNT_NUMBER": "FINANCIAL",
        })

    def test_should_resolve_ssn_by_pattern(self, resolver_with_categories):
        """Test US SSN format is correctly identified."""
        text = "123-45-6789"
        detected_labels = [("SSN", 0.90), ("NATIONAL_ID", 0.75)]

        result = resolver_with_categories.resolve(text, detected_labels, "test-ssn")

        assert result is not None
        assert result[0] == "SSN"

    def test_should_resolve_national_id_with_different_format(
        self, resolver_with_categories
    ):
        """Test national ID with non-SSN format."""
        text = "41-79-1234"
        detected_labels = [("SSN", 0.65), ("NATIONAL_ID", 0.85)]

        result = resolver_with_categories.resolve(text, detected_labels, "test-nid")

        # Pattern match should identify this as NATIONAL_ID
        assert result is not None


class TestResolveEmailLikeGroup:
    """Test cases for EMAIL_LIKE conflict group."""

    @pytest.fixture
    def resolver(self) -> ConflictResolver:
        """Create resolver with category mapping."""
        return ConflictResolver(pii_type_to_category={
            "EMAIL": "CONTACT",
            "USERNAME": "DIGITAL",
        })

    def test_should_resolve_email_by_pattern(self, resolver):
        """Test email address is correctly identified."""
        text = "john.doe@example.com"
        detected_labels = [("EMAIL", 0.95), ("USERNAME", 0.70)]

        result = resolver.resolve(text, detected_labels, "test-email")

        assert result is not None
        assert result[0] == "EMAIL"

    def test_should_handle_internal_domain_email(self, resolver):
        """Test email with internal domain."""
        text = "admin@internal.local"
        detected_labels = [("EMAIL", 0.88), ("USERNAME", 0.65)]

        result = resolver.resolve(text, detected_labels, "test-internal")

        assert result is not None
        assert result[0] == "EMAIL"


class TestResolveURLLikeGroup:
    """Test cases for URL_LIKE conflict group."""

    @pytest.fixture
    def resolver(self) -> ConflictResolver:
        """Create resolver with category mapping."""
        return ConflictResolver(pii_type_to_category={
            "URL": "DIGITAL",
            "IP_ADDRESS": "IT_CREDENTIALS",
            "HOSTNAME": "IT_CREDENTIALS",
        })

    def test_should_resolve_url_by_pattern(self, resolver):
        """Test URL is correctly identified."""
        text = "https://example.com/path"
        detected_labels = [("URL", 0.95), ("HOSTNAME", 0.80)]

        result = resolver.resolve(text, detected_labels, "test-url")

        assert result is not None
        assert result[0] == "URL"

    def test_should_resolve_url_with_ip(self, resolver):
        """Test URL containing IP address."""
        text = "http://192.168.1.1/admin"
        detected_labels = [("URL", 0.90), ("IP_ADDRESS", 0.85)]

        result = resolver.resolve(text, detected_labels, "test-url-ip")

        assert result is not None
        # URL should win as it's the container pattern
        assert result[0] == "URL"


class TestResolveByCategoryPriority:
    """Test cases for category priority fallback resolution."""

    @pytest.fixture
    def resolver_with_full_mapping(self) -> ConflictResolver:
        """Create resolver with full category mapping."""
        return ConflictResolver(pii_type_to_category={
            "CREDIT_CARD_NUMBER": "FINANCIAL",
            "BANK_ACCOUNT_NUMBER": "FINANCIAL",
            "AVS_NUMBER": "MEDICAL",
            "PATIENT_ID": "MEDICAL",
            "API_KEY": "IT_CREDENTIALS",
            "PERSON_NAME": "IDENTITY",
            "EMAIL": "CONTACT",
            "USERNAME": "DIGITAL",
            "LICENSE_PLATE": "LEGAL_ASSET",
        })

    def test_should_prefer_financial_over_contact(self, resolver_with_full_mapping):
        """Test FINANCIAL (100) beats CONTACT (80)."""
        # Use text that doesn't match any conflict group pattern
        text = "ABC123XYZ789"
        detected_labels = [("CREDIT_CARD_NUMBER", 0.85), ("EMAIL", 0.90)]

        result = resolver_with_full_mapping.resolve(text, detected_labels, "test-fin")

        assert result is not None
        # Financial has higher priority despite lower score
        assert result[0] == "CREDIT_CARD_NUMBER"
        stats = resolver_with_full_mapping.get_conflict_stats()
        assert stats["resolved_by_category"] == 1

    def test_should_prefer_medical_over_identity(self, resolver_with_full_mapping):
        """Test MEDICAL (95) beats IDENTITY (85)."""
        text = "XYZ999ABC"
        detected_labels = [("PATIENT_ID", 0.80), ("PERSON_NAME", 0.90)]

        result = resolver_with_full_mapping.resolve(text, detected_labels, "test-med")

        assert result is not None
        assert result[0] == "PATIENT_ID"

    def test_should_use_score_as_tiebreaker(self, resolver_with_full_mapping):
        """Test score is used when categories have same priority."""
        text = "XYZ123"
        # Both are FINANCIAL, same category priority
        detected_labels = [
            ("CREDIT_CARD_NUMBER", 0.85),
            ("BANK_ACCOUNT_NUMBER", 0.92),
        ]

        result = resolver_with_full_mapping.resolve(text, detected_labels, "test-tie")

        assert result is not None
        # Higher score should win when same priority
        assert result[0] == "BANK_ACCOUNT_NUMBER"

    def test_should_handle_unknown_category(self, resolver_with_full_mapping):
        """Test handling of PII type not in category mapping."""
        text = "UNKNOWN123"
        detected_labels = [
            ("UNKNOWN_TYPE", 0.95),  # Not in mapping
            ("EMAIL", 0.80),  # CONTACT priority 80
        ]

        result = resolver_with_full_mapping.resolve(text, detected_labels, "test-unk")

        assert result is not None
        # EMAIL should win because UNKNOWN_TYPE has priority 0
        assert result[0] == "EMAIL"


class TestConflictStatistics:
    """Test cases for conflict statistics tracking."""

    def test_should_track_pattern_resolution_count(self):
        """Test pattern resolution is tracked."""
        resolver = ConflictResolver()

        # IP address - resolved by pattern
        resolver.resolve("192.168.1.1", [("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)])

        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] == 1
        assert stats["resolved_by_pattern"] == 1
        assert stats["resolved_by_fallback"] == 0
        assert stats["resolved_by_category"] == 0

    def test_should_track_fallback_resolution_count(self):
        """Test fallback resolution is tracked."""
        resolver = ConflictResolver()

        # This format matches group but not specific type patterns
        resolver.resolve("123.456.789.012", [("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)])

        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] == 1
        assert stats["resolved_by_fallback"] == 1

    def test_should_track_category_resolution_count(self):
        """Test category resolution is tracked."""
        resolver = ConflictResolver(pii_type_to_category={
            "CUSTOM_TYPE_A": "IDENTITY",
            "CUSTOM_TYPE_B": "CONTACT",
        })

        # Text that doesn't match any conflict group pattern
        # Use types that are NOT in any conflict group's type_patterns
        resolver.resolve("some random text here", [("CUSTOM_TYPE_A", 0.90), ("CUSTOM_TYPE_B", 0.85)])

        stats = resolver.get_conflict_stats()
        assert stats["total_conflicts"] == 1
        assert stats["resolved_by_category"] == 1

    def test_should_reset_stats(self):
        """Test statistics reset."""
        resolver = ConflictResolver()

        # Generate some conflicts
        resolver.resolve("192.168.1.1", [("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)])
        resolver.resolve("756.1234.5678.90", [("IP_ADDRESS", 0.80), ("AVS_NUMBER", 0.90)])

        stats_before = resolver.get_conflict_stats()
        assert stats_before["total_conflicts"] == 2

        resolver.reset_conflict_stats()

        stats_after = resolver.get_conflict_stats()
        assert stats_after["total_conflicts"] == 0
        assert stats_after["resolved_by_pattern"] == 0

    def test_get_conflict_stats_returns_copy(self):
        """Test that get_conflict_stats returns a copy, not the original."""
        resolver = ConflictResolver()

        stats = resolver.get_conflict_stats()
        stats["total_conflicts"] = 9999

        # Original should be unchanged
        assert resolver.get_conflict_stats()["total_conflicts"] == 0


class TestBuildPIIEntity:
    """Test cases for PIIEntity construction."""

    def test_should_build_valid_pii_entity(self):
        """Test building a valid PIIEntity."""
        resolver = ConflictResolver()

        entity = resolver.build_pii_entity(
            text="john@example.com",
            pii_type="EMAIL",
            score=0.95,
            start=10,
            end=26,
        )

        assert isinstance(entity, PIIEntity)
        assert entity.text == "john@example.com"
        assert entity.pii_type == "EMAIL"
        assert entity.type_label == "EMAIL"
        assert entity.score == 0.95
        assert entity.start == 10
        assert entity.end == 26


class TestConflictLogging:
    """Test cases for conflict resolution logging."""

    def test_should_log_conflict_resolution(self, caplog):
        """Test that conflict resolution is logged."""
        resolver = ConflictResolver(pii_type_to_category={
            "IP_ADDRESS": "IT_CREDENTIALS",
            "AVS_NUMBER": "MEDICAL",
        })

        with caplog.at_level(logging.INFO):
            resolver.resolve(
                "192.168.1.1",
                [("IP_ADDRESS", 0.90), ("AVS_NUMBER", 0.85)],
                detection_id="test-log-001"
            )

        # Check log contains key information
        log_text = caplog.text
        assert "test-log-001" in log_text
        assert "CONFLICT RESOLVED" in log_text
        assert "IP_ADDRESS" in log_text


class TestEdgeCases:
    """Test cases for edge cases and boundary conditions."""

    def test_should_handle_very_long_text(self):
        """Test handling of very long text content."""
        resolver = ConflictResolver()
        long_text = "a" * 1000

        result = resolver.resolve(long_text, [("PERSON_NAME", 0.5)])

        assert result == ("PERSON_NAME", 0.5)

    def test_should_handle_special_characters_in_text(self):
        """Test handling of special characters."""
        resolver = ConflictResolver()
        text = "john+doe@example.com"

        result = resolver.resolve(text, [("EMAIL", 0.9), ("USERNAME", 0.7)])

        assert result is not None

    def test_should_handle_unicode_text(self):
        """Test handling of unicode characters."""
        resolver = ConflictResolver()
        text = "müller@example.com"

        result = resolver.resolve(text, [("EMAIL", 0.85)])

        assert result == ("EMAIL", 0.85)

    def test_should_handle_many_conflicting_labels(self):
        """Test handling of many conflicting labels."""
        resolver = ConflictResolver(pii_type_to_category={
            "TYPE_A": "FINANCIAL",
            "TYPE_B": "MEDICAL",
            "TYPE_C": "IDENTITY",
            "TYPE_D": "CONTACT",
            "TYPE_E": "DIGITAL",
        })

        labels = [
            ("TYPE_A", 0.90),
            ("TYPE_B", 0.85),
            ("TYPE_C", 0.80),
            ("TYPE_D", 0.75),
            ("TYPE_E", 0.70),
        ]

        result = resolver.resolve("random_text", labels, "test-many")

        assert result is not None
        # FINANCIAL has highest priority
        assert result[0] == "TYPE_A"
