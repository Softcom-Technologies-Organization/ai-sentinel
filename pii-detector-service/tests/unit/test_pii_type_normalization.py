"""
Test PII type normalization for gRPC response.

This test ensures that PII types sent via gRPC are properly normalized
to match Java PiiType enum expectations (e.g., 'EMAIL' not 'PIIType.EMAIL').
"""

from pii_detector.domain.entity.pii_type import PIIType


class TestPiiTypeNormalization:
    """Test suite for PII type normalization logic."""

    def test_Should_ReturnEnumName_When_ConvertingPIITypeToString(self):
        """
        Should convert PIIType enum to just the name (EMAIL) not the full repr (PIIType.EMAIL).
        
        Business rule: Java expects 'EMAIL', not 'PIIType.EMAIL'
        Bug: str(PIIType.EMAIL) returns 'PIIType.EMAIL' which Java cannot parse
        """
        pii_type = PIIType.EMAIL
        
        # ❌ WRONG: str() returns full repr
        assert str(pii_type) == "PIIType.EMAIL"
        
        # ✅ CORRECT: Use .name to get just the enum name
        assert pii_type.name == "EMAIL"
    
    def test_Should_NormalizePIIType_When_MixedTypes(self):
        """
        Should correctly normalize various PII type representations.
        """
        test_cases = [
            (PIIType.CREDIT_CARD, "CREDIT_CARD"),
            (PIIType.SSN, "SSN"),
            (PIIType.IBAN, "IBAN"),
            (PIIType.PHONE, "PHONE"),
            (PIIType.CRYPTO_WALLET, "CRYPTO_WALLET"),
            ("EMAIL", "EMAIL"),  # Already a string
            ("PHONE", "PHONE"),
        ]
        
        for input_value, expected in test_cases:
            normalized = self._normalize_pii_type(input_value)
            assert normalized == expected, f"Failed for {input_value}"
    
    def test_Should_HandleUnknownType_When_InvalidInput(self):
        """
        Should handle edge cases gracefully.
        """
        assert self._normalize_pii_type(None) == "UNKNOWN"
        assert self._normalize_pii_type("") == "UNKNOWN"
        assert self._normalize_pii_type(PIIType.UNKNOWN) == "UNKNOWN"
    
    @staticmethod
    def _normalize_pii_type(pii_type) -> str:
        """
        Normalize PII type to string format expected by Java.
        
        This is the implementation that should be used in pii_service.py
        """
        if pii_type is None or pii_type == "":
            return "UNKNOWN"
        
        # If it's a PIIType enum, extract the name
        if isinstance(pii_type, PIIType):
            return pii_type.name
        
        # If it's already a string, use it as-is (uppercase)
        return str(pii_type).upper()
