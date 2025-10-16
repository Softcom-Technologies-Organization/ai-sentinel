"""
Entity processing for PII detection.

This module provides the EntityProcessor class that handles entity
processing and formatting operations, including filtering by threshold,
creating PIIEntity objects, and managing label mappings.
"""

import logging
from typing import Dict, List

from .models.pii_entity import PIIEntity
from .models.pii_type import PIIType


class EntityProcessor:
    """Handles entity processing and formatting operations."""

    def __init__(self):
        self.label_mapping = {pii_type.name: pii_type.value for pii_type in PIIType}
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

    def process_entities(self, raw_entities: List[Dict], threshold: float) -> List[PIIEntity]:
        """Process and filter raw entities from the model."""
        processed_entities = []

        for entity in raw_entities:
            if entity['score'] >= threshold:
                pii_entity = self._create_pii_entity(entity)
                processed_entities.append(pii_entity)

        return processed_entities

    def detect_emails_with_regex(self) -> List[PIIEntity]:
        """Regex-based detections are disabled by policy; returns no additional entities."""
        # Business rule: No regex-based detection is allowed. This is intentionally a no-op.
        return []

    def _create_pii_entity(self, entity: Dict) -> PIIEntity:
        """Create a PIIEntity from raw model output."""
        return PIIEntity(
            text=entity['word'].strip(),
            pii_type=entity['entity_group'],
            type_label=self.label_mapping.get(entity['entity_group'], entity['entity_group']),
            start=entity['start'],
            end=entity['end'],
            score=entity['score']
        )
