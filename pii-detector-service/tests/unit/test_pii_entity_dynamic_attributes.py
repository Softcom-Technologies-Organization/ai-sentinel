"""
Test dynamic attribute access in PIIEntity.

This test verifies that dynamically attached attributes (like 'source')
are properly accessible through all dict-style access methods.

Bug context: Presidio detector attaches entity.source = "PRESIDIO" but
the post-filtering logic couldn't access it reliably.
"""

import pytest
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.entity.pii_type import PIIType


class TestPIIEntityDynamicAttributes:
    """Test that PIIEntity properly exposes dynamically attached attributes."""
    
    def test_should_access_dynamic_source_via_get(self):
        """Should access dynamically attached 'source' attribute via get() method."""
        # Given: a PIIEntity with dynamically attached source attribute
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        
        # When: accessing source via get()
        source = entity.get('source', 'UNKNOWN')
        
        # Then: should return the attached value
        assert source == "PRESIDIO", f"Expected 'PRESIDIO' but got '{source}'"
    
    def test_should_access_dynamic_source_via_dict_access(self):
        """Should access dynamically attached 'source' attribute via dict-style access."""
        # Given: a PIIEntity with dynamically attached source attribute
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        
        # When: accessing source via bracket notation
        # Then: should either work or raise KeyError (current behavior)
        # After fix, this should work
        try:
            source = entity['source']
            assert source == "PRESIDIO"
        except KeyError:
            pytest.fail("PIIEntity should expose dynamic attributes via __getitem__")
    
    def test_should_check_dynamic_source_via_contains(self):
        """Should check dynamically attached 'source' attribute via 'in' operator."""
        # Given: a PIIEntity with dynamically attached source attribute
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        
        # When: checking if 'source' in entity
        has_source = 'source' in entity
        
        # Then: should return True (after fix)
        assert has_source, "PIIEntity should include dynamic attributes in __contains__"
    
    def test_should_include_dynamic_source_in_keys(self):
        """Should include dynamically attached 'source' in keys() method."""
        # Given: a PIIEntity with dynamically attached source attribute
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        
        # When: getting keys
        keys = list(entity.keys())
        
        # Then: should include 'source' (after fix)
        assert 'source' in keys, f"PIIEntity keys should include 'source', but got: {keys}"
    
    def test_should_include_dynamic_source_in_items(self):
        """Should include dynamically attached 'source' in items() method."""
        # Given: a PIIEntity with dynamically attached source attribute
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        
        # When: getting items
        items = dict(entity.items())
        
        # Then: should include 'source' (after fix)
        assert 'source' in items, f"PIIEntity items should include 'source', but got: {items.keys()}"
        assert items['source'] == "PRESIDIO"
    
    def test_should_access_standard_fields_unchanged(self):
        """Should still access standard fields correctly after fix."""
        # Given: a PIIEntity with standard fields
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        
        # When/Then: all standard fields should work
        assert entity.get('text') == "test@example.com"
        assert entity['text'] == "test@example.com"
        assert 'text' in entity
        assert 'text' in entity.keys()
        
        assert entity.get('type') == PIIType.EMAIL
        assert entity['type'] == PIIType.EMAIL
        assert 'type' in entity
        
        assert entity.get('score') == 0.95
        assert entity['score'] == 0.95
    
    def test_should_handle_multiple_dynamic_attributes(self):
        """Should handle multiple dynamically attached attributes."""
        # Given: a PIIEntity with multiple dynamic attributes
        entity = PIIEntity(
            text="test@example.com",
            pii_type=PIIType.EMAIL,
            type_label="EMAIL",
            start=0,
            end=17,
            score=0.95
        )
        entity.source = "PRESIDIO"
        entity.confidence_level = "HIGH"
        entity.extra_metadata = {"key": "value"}
        
        # When: accessing all dynamic attributes
        source = entity.get('source')
        confidence = entity.get('confidence_level')
        metadata = entity.get('extra_metadata')
        
        # Then: all should be accessible
        assert source == "PRESIDIO"
        assert confidence == "HIGH"
        assert metadata == {"key": "value"}
        
        # And: should be in keys (after fix)
        keys = list(entity.keys())
        assert 'source' in keys
        assert 'confidence_level' in keys
        assert 'extra_metadata' in keys
