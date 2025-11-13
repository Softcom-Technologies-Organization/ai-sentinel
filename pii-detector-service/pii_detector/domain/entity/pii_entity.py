"""
Data class representing a detected PII entity.

This module provides the PIIEntity dataclass that represents a single
detected piece of Personally Identifiable Information, including its
position in the text, type, and confidence score.

The class provides dictionary-style access methods for backward compatibility
with code that treats entities as dictionaries.
"""

from dataclasses import dataclass


@dataclass
class PIIEntity:
    """Data class representing a detected PII entity."""

    text: str
    pii_type: str
    type_label: str
    start: int
    end: int
    score: float

    def __getitem__(self, key):
        """Support dictionary-style access for backward compatibility."""
        if key == 'text':
            return self.text
        elif key == 'type':
            return self.pii_type
        elif key == 'type_label':
            return self.type_label
        elif key == 'type_fr':
            return self.type_label  # French label is the same as type_label
        elif key == 'start':
            return self.start
        elif key == 'end':
            return self.end
        elif key == 'score':
            return self.score
        else:
            raise KeyError(f"'{key}' not found in PIIEntity")
    
    def __contains__(self, key):
        """Support 'in' operator for backward compatibility."""
        return key in ['text', 'type', 'type_label', 'type_fr', 'start', 'end', 'score']

    def get(self, key, default=None):
        """Support dictionary-style get method for backward compatibility."""
        try:
            return self[key]
        except KeyError:
            return default

    def keys(self):
        """Support dictionary-style keys method."""
        return ['text', 'type', 'type_label', 'type_fr', 'start', 'end', 'score']

    def values(self):
        """Support dictionary-style values method."""
        return [self.text, self.pii_type, self.type_label, self.type_label, self.start, self.end, self.score]

    def items(self):
        """Support dictionary-style items method."""
        return [(key, self[key]) for key in self.keys()]
