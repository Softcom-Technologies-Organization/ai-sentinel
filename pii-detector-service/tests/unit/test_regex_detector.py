"""
Unit tests for RegexDetector.

Tests regex-based PII detection including pattern matching,
validation logic, and overlap resolution.
"""

import pytest
from pathlib import Path

from pii_detector.infrastructure.detector.regex_detector import RegexDetector, RegexPattern
from pii_detector.domain.entity.pii_entity import PIIEntity


class TestRegexPattern:
    """Test RegexPattern class."""
    
    def test_Should_CompilePattern_When_ValidRegex(self):
        """Should compile regex pattern successfully."""
        pattern = RegexPattern(
            name="test_email",
            pii_type="EMAIL",
            pattern=r"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b",
            score=0.95,
            priority="high"
        )
        
        assert pattern.compiled is not None
        assert pattern.name == "test_email"
        assert pattern.pii_type == "EMAIL"
        assert pattern.score == 0.95
        assert pattern.priority == "high"
    
    def test_Should_RaiseError_When_InvalidRegex(self):
        """Should raise ValueError for invalid regex pattern."""
        with pytest.raises(ValueError, match="Invalid regex pattern"):
            RegexPattern(
                name="invalid",
                pii_type="TEST",
                pattern=r"[invalid(regex",
                score=0.5,
                priority="low"
            )


class TestRegexDetector:
    """Test RegexDetector class."""
    
    @pytest.fixture
    def detector(self):
        """Create RegexDetector instance for testing."""
        return RegexDetector()
    
    def test_Should_InitializeDetector_When_DefaultConfig(self, detector):
        """Should initialize detector with default configuration."""
        assert detector is not None
        assert detector.model_id == "regex-detector"
        assert len(detector.patterns) > 0
    
    def test_Should_DetectEmail_When_ValidEmailInText(self, detector):
        """Should detect valid email addresses."""
        text = "Contact me at john.doe@example.com for more info."
        
        entities = detector.detect_pii(text)
        
        assert len(entities) >= 1
        email_entities = [e for e in entities if e.pii_type == "EMAIL"]
        assert len(email_entities) == 1
        assert email_entities[0].text == "john.doe@example.com"
        assert email_entities[0].score >= 0.9
    
    def test_Should_DetectMultipleEmails_When_MultipleInText(self, detector):
        """Should detect multiple email addresses."""
        text = "Send to alice@test.com or bob@example.org"
        
        entities = detector.detect_pii(text)
        
        email_entities = [e for e in entities if e.pii_type == "EMAIL"]
        assert len(email_entities) == 2
        emails = {e.text for e in email_entities}
        assert "alice@test.com" in emails
        assert "bob@example.org" in emails
    
    def test_Should_DetectIPv4_When_ValidIPInText(self, detector):
        """Should detect valid IPv4 addresses."""
        text = "Server IP is 192.168.1.1"
        
        entities = detector.detect_pii(text)
        
        ip_entities = [e for e in entities if e.pii_type == "IP_ADDRESS"]
        assert len(ip_entities) >= 1
        assert any(e.text == "192.168.1.1" for e in ip_entities)
    
    def test_Should_DetectMACAddress_When_ValidMACInText(self, detector):
        """Should detect valid MAC addresses."""
        text = "Device MAC: 00:1B:44:11:3A:B7"
        
        entities = detector.detect_pii(text)
        
        mac_entities = [e for e in entities if e.pii_type == "MAC_ADDRESS"]
        assert len(mac_entities) == 1
        assert mac_entities[0].text == "00:1B:44:11:3A:B7"
    
    def test_Should_DetectGitHubToken_When_ValidTokenInText(self, detector):
        """Should detect GitHub tokens."""
        text = "Token: ghp_1234567890abcdefghijklmnopqrstuvwxyz"
        
        entities = detector.detect_pii(text)
        
        token_entities = [e for e in entities if e.pii_type == "API_KEY"]
        assert len(token_entities) >= 1
        assert any("ghp_" in e.text for e in token_entities)
    
    def test_Should_DetectAWSKey_When_ValidKeyInText(self, detector):
        """Should detect AWS access keys."""
        text = "AWS Key: AKIAIOSFODNN7EXAMPLE"
        
        entities = detector.detect_pii(text)
        
        key_entities = [e for e in entities if e.pii_type == "API_KEY"]
        assert len(key_entities) >= 1
        assert any("AKIA" in e.text for e in key_entities)
    
    def test_Should_DetectSwissSSN_When_ValidSSNInText(self, detector):
        """Should detect Swiss social security numbers."""
        text = "Swiss SSN: 756.1234.5678.97"
        
        entities = detector.detect_pii(text)
        
        ssn_entities = [e for e in entities if e.pii_type == "SOCIALNUM"]
        assert len(ssn_entities) >= 1
        assert any("756." in e.text for e in ssn_entities)
    
    def test_Should_DetectFrenchPhone_When_ValidPhoneInText(self, detector):
        """Should detect French phone numbers."""
        text = "Call me at 01 23 45 67 89"
        
        entities = detector.detect_pii(text)
        
        phone_entities = [e for e in entities if e.pii_type == "TELEPHONENUM"]
        assert len(phone_entities) >= 1
    
    def test_Should_ValidateCreditCard_When_ValidLuhn(self, detector):
        """Should validate credit card with Luhn algorithm."""
        # Valid Visa test number
        valid_card = "4532015112830366"
        assert detector._validate_luhn(valid_card) is True
    
    def test_Should_RejectCreditCard_When_InvalidLuhn(self, detector):
        """Should reject credit card with invalid Luhn checksum."""
        # Invalid checksum
        invalid_card = "4532015112830367"
        assert detector._validate_luhn(invalid_card) is False
    
    def test_Should_FilterByThreshold_When_LowConfidence(self, detector):
        """Should filter entities below confidence threshold."""
        text = "Email: test@example.com"
        
        # High threshold should filter out lower-scored patterns
        entities = detector.detect_pii(text, threshold=0.99)
        
        # Email pattern has score 0.95, should be filtered
        email_entities = [e for e in entities if e.pii_type == "EMAIL"]
        assert len(email_entities) == 0
    
    def test_Should_ResolveOverlaps_When_MultiplePatternMatch(self, detector):
        """Should resolve overlapping matches by priority."""
        # Create overlapping entities
        entity1 = PIIEntity(
            text="test", pii_type="TYPE1", type_label="TYPE1",
            start=0, end=10, score=0.9
        )
        entity1._priority = "high"
        
        entity2 = PIIEntity(
            text="test2", pii_type="TYPE2", type_label="TYPE2",
            start=5, end=15, score=0.8
        )
        entity2._priority = "low"
        
        matches = [entity1, entity2]
        resolved = detector._resolve_overlaps(matches)
        
        # Should keep high priority entity
        assert len(resolved) == 1
        assert resolved[0].pii_type == "TYPE1"
    
    def test_Should_ReturnEmpty_When_NoMatches(self, detector):
        """Should return empty list when no PII detected."""
        text = "This is a clean text with no PII."
        
        entities = detector.detect_pii(text)
        
        assert entities == []
    
    def test_Should_ReturnEmpty_When_EmptyText(self, detector):
        """Should return empty list for empty text."""
        entities = detector.detect_pii("")
        
        assert entities == []
    
    def test_Should_SortByPosition_When_MultipleEntities(self, detector):
        """Should return entities sorted by start position."""
        text = "Email: alice@test.com and bob@example.org, IP: 192.168.1.1"
        
        entities = detector.detect_pii(text)
        
        # Check entities are sorted by start position
        for i in range(len(entities) - 1):
            assert entities[i].start <= entities[i + 1].start
    
    def test_Should_MaskPII_When_DetectedEntities(self, detector):
        """Should mask detected PII in text."""
        text = "Contact: john@example.com"
        
        masked_text, entities = detector.mask_pii(text)
        
        assert "[EMAIL]" in masked_text
        assert "john@example.com" not in masked_text
        assert len(entities) >= 1
    
    def test_Should_NoOpDownload_When_CalledSafely(self, detector):
        """Should safely no-op on download_model."""
        # Should not raise exception
        detector.download_model()
    
    def test_Should_NoOpLoad_When_CalledSafely(self, detector):
        """Should safely no-op on load_model."""
        # Should not raise exception
        detector.load_model()
    
    def test_Should_DetectJWT_When_ValidTokenInText(self, detector):
        """Should detect JWT tokens."""
        text = "JWT: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        
        entities = detector.detect_pii(text)
        
        jwt_entities = [e for e in entities if e.pii_type == "API_KEY"]
        assert len(jwt_entities) >= 1
        assert any("eyJ" in e.text for e in jwt_entities)


class TestRegexDetectorIntegration:
    """Integration tests for RegexDetector."""
    
    @pytest.fixture
    def detector(self):
        """Create detector for integration tests."""
        return RegexDetector()
    
    def test_Should_DetectMixedPII_When_RealWorldText(self, detector):
        """Should detect multiple PII types in realistic text."""
        text = """
        Please contact our support team:
        Email: support@company.com
        Phone: 01 23 45 67 89
        Server IP: 192.168.1.100
        AWS Key: AKIAIOSFODNN7EXAMPLE
        """
        
        entities = detector.detect_pii(text)
        
        # Should detect multiple types
        pii_types = {e.pii_type for e in entities}
        assert "EMAIL" in pii_types
        assert "IP_ADDRESS" in pii_types
        assert len(entities) >= 3
    
    def test_Should_HandleLargeText_When_MultipleOccurrences(self, detector):
        """Should handle large texts with many PII occurrences."""
        # Generate text with many emails
        emails = [f"user{i}@example.com" for i in range(100)]
        text = " ".join(emails)
        
        entities = detector.detect_pii(text)
        
        email_entities = [e for e in entities if e.pii_type == "EMAIL"]
        assert len(email_entities) == 100
    
    def test_Should_PreservePositions_When_Masking(self, detector):
        """Should preserve correct positions when masking."""
        text = "Email: test@example.com and IP: 192.168.1.1"
        
        masked_text, entities = detector.mask_pii(text)
        
        # Original structure should be preserved
        assert "Email:" in masked_text
        assert "and" in masked_text
        assert "IP:" in masked_text
        assert "[EMAIL]" in masked_text
        assert "[IP_ADDRESS]" in masked_text
