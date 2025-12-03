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
        """Support dictionary-style access for backward compatibility.
        
        This method supports both standard fields and dynamically attached
        attributes (e.g., 'source' set by specific detectors).
        """
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
        elif hasattr(self, key):
            # Support dynamically attached attributes
            return getattr(self, key)
        else:
            raise KeyError(f"'{key}' not found in PIIEntity")
    
    def __contains__(self, key):
        """Support 'in' operator for backward compatibility.
        
        This method checks both standard fields and dynamically attached
        attributes (e.g., 'source' set by specific detectors).
        """
        standard_keys = ['text', 'type', 'type_label', 'type_fr', 'start', 'end', 'score']
        return key in standard_keys or hasattr(self, key)

    def get(self, key, default=None):
        """Support dictionary-style get method for backward compatibility.

        This method first looks for a dynamically attached attribute with the
        requested key name (for example ``source`` set by specific detectors),
        then falls back to the standard mapping implemented in ``__getitem__``.
        """
        # Allow detectors to attach additional attributes (e.g. ``source``)
        # while keeping dict-style access consistent for existing callers.
        if hasattr(self, key):
            return getattr(self, key)

        try:
            return self[key]
        except KeyError:
            return default

    def keys(self):
        """Support dictionary-style keys method.
        
        Returns both standard fields and dynamically attached attributes
        (e.g., 'source' set by specific detectors).
        """
        standard_keys = ['text', 'type', 'type_label', 'type_fr', 'start', 'end', 'score']
        
        # Get dynamically attached attributes (not dataclass fields or private)
        dataclass_fields = {'text', 'pii_type', 'type_label', 'start', 'end', 'score'}
        dynamic_keys = [
            key for key in self.__dict__.keys()
            if key not in dataclass_fields and not key.startswith('_')
        ]
        
        return standard_keys + dynamic_keys

    def values(self):
        """Support dictionary-style values method.
        
        Returns values for both standard fields and dynamically attached
        attributes (e.g., 'source' set by specific detectors).
        """
        return [self[key] for key in self.keys()]

    def items(self):
        """Support dictionary-style items method.
        
        Returns items for both standard fields and dynamically attached
        attributes (e.g., 'source' set by specific detectors).
        """
        return [(key, self[key]) for key in self.keys()]
