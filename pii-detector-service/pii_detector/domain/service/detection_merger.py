"""
Merger for PII detection results from multiple models.

This module provides DetectionMerger class that handles deduplication,
overlap resolution, and result fusion for multi-model PII detection.
"""

import logging
from typing import Dict, List, Tuple

from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.port.pii_detector_protocol import PIIDetectorProtocol


class DetectionMerger:
    """
    Handles merging and deduplication of PII entities from multiple detectors.
    
    Business responsibilities:
    - Deduplicate identical entities keeping highest confidence
    - Resolve overlapping entities preferring longer spans
    - Track entity provenance for debugging (optional)
    
    This class implements the core fusion logic for the Composite pattern
    in MultiModelPIIDetector, separating concerns and reducing complexity.
    """

    def __init__(self, log_provenance: bool = False):
        """
        Initialize the detection merger.
        
        Args:
            log_provenance: Enable detailed provenance logging for debugging
        """
        self.log_provenance = log_provenance
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

    def merge(
        self,
        results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]]
    ) -> List[PIIEntity]:
        """
        Merge detection results from multiple detectors.
        
        Business process:
        1. Deduplicate identical entities keeping highest confidence
        2. Resolve overlapping entities preferring longer spans
        3. Return final unified entity list
        
        Args:
            results_per_detector: List of (detector, entities) tuples
            
        Returns:
            Unified list of PII entities with duplicates removed and overlaps resolved
        """
        merged_entities, _ = self._merge_and_deduplicate_entities(results_per_detector)
        resolved = self._resolve_overlapping_entities(merged_entities)
        self._log_overlap_resolution(len(merged_entities), len(resolved))
        return resolved

    def _merge_and_deduplicate_entities(
        self, results_per_detector: List[Tuple[PIIDetectorProtocol, List[PIIEntity]]]
    ) -> Tuple[List[PIIEntity], Dict[Tuple[int, int, str, str], str]]:
        """
        Merge entities from all detectors, keeping highest confidence for duplicates.
        
        Business rule: For identical entities (same position, type, text), 
        keep the one with highest confidence score.
        
        Args:
            results_per_detector: Detection results from all models
            
        Returns:
            Tuple of (deduplicated entities list, source tracking dict)
        """
        merged: Dict[Tuple[int, int, str, str], PIIEntity] = {}
        source_by_key: Dict[Tuple[int, int, str, str], str] = {}
        
        for detector, model_entities in results_per_detector:
            for entity in model_entities:
                entity_key = self._create_entity_key(entity)
                self._merge_entity(entity_key, entity, detector.model_id, merged, source_by_key)
        
        return list(merged.values()), source_by_key

    def _create_entity_key(self, entity: PIIEntity) -> Tuple[int, int, str, str]:
        """
        Create unique key for entity deduplication.
        
        Args:
            entity: Entity to create key for
            
        Returns:
            Tuple of (start, end, pii_type, text)
        """
        return (entity.start, entity.end, entity.pii_type, entity.text)

    def _merge_entity(
        self,
        key: Tuple[int, int, str, str],
        entity: PIIEntity,
        model_id: str,
        merged: Dict[Tuple[int, int, str, str], PIIEntity],
        source_by_key: Dict[Tuple[int, int, str, str], str]
    ) -> None:
        """
        Merge a single entity into the deduplicated collection.
        
        Args:
            key: Entity unique key
            entity: Entity to merge
            model_id: Source model identifier
            merged: Dictionary of merged entities
            source_by_key: Dictionary tracking entity sources
        """
        existing_entity = merged.get(key)
        
        if existing_entity is None:
            merged[key] = entity
            if self.log_provenance:
                source_by_key[key] = model_id
        elif entity.score > existing_entity.score:
            self._log_entity_replacement(key, existing_entity, entity, model_id, source_by_key)
            merged[key] = entity
            if self.log_provenance:
                source_by_key[key] = model_id

    def _log_entity_replacement(
        self,
        key: Tuple[int, int, str, str],
        old_entity: PIIEntity,
        new_entity: PIIEntity,
        new_model_id: str,
        source_by_key: Dict[Tuple[int, int, str, str], str]
    ) -> None:
        """
        Log when an entity is replaced by a higher confidence one.
        
        Args:
            key: Entity key
            old_entity: Entity being replaced
            new_entity: Replacement entity
            new_model_id: Source model of new entity
            source_by_key: Source tracking dictionary
        """
        if not self.log_provenance:
            return
        
        old_model_id = source_by_key.get(key, "?")
        try:
            self.logger.info(
                "[PII-MERGE] key=%s replaced old_model=%s old_score=%.4f new_model=%s new_score=%.4f",
                str(key), old_model_id, float(old_entity.score), new_model_id, float(new_entity.score)
            )
        except Exception:
            # Never break detection due to logging errors
            pass

    def _resolve_overlapping_entities(self, entities: List[PIIEntity]) -> List[PIIEntity]:
        """
        Resolve overlapping entities by preferring longer spans.
        
        Business rules for multi-model entity fusion:
        - When two entities of the same type overlap, keep the one with the longer span
          (more complete information, e.g., full email vs partial).
        - If spans have equal length, keep the one with higher confidence score.
        - Non-overlapping entities are always kept.
        
        Algorithm: For each entity type, sort by position and apply a sweep-line
        approach to identify and resolve overlaps. Complexity: O(n log n).
        
        Args:
            entities: List of entities from all models after deduplication
            
        Returns:
            List of entities with overlaps resolved
        """
        if not entities:
            return []
        
        # Group entities by type for independent resolution
        by_type: Dict[str, List[PIIEntity]] = {}
        for e in entities:
            by_type.setdefault(e.pii_type, []).append(e)
        
        resolved: List[PIIEntity] = []
        for pii_type, type_entities in by_type.items():
            resolved.extend(self._resolve_overlaps_for_type(type_entities))
        
        return resolved

    def _resolve_overlaps_for_type(self, entities: List[PIIEntity]) -> List[PIIEntity]:
        """
        Resolve overlaps for a single entity type using a true sweep-line scan.

        Algorithm (O(n log n) total — sort + linear scan):

        1. Sort by ``(start ASC, -length DESC, -score DESC)``.
        2. Keep only the entity that ends the latest among those started so far.

        Why a single comparison against the last kept entity is sufficient:

        - After sorting by ``start`` ascending, every entity processed has
          ``start >= last_kept.start``. An "earlier" kept entity can only
          conflict with the current one if it overlaps it, but if such an
          earlier entity were "active" we would have used it as a comparison
          point — instead the sort guarantees the most recently kept entity
          covers the right-most accepted region.
        - The ``-length`` tiebreaker means that for entities sharing a start,
          the longest comes first. The ``current_contains_kept`` branch in
          the previous implementation was therefore unreachable: a later
          entity at the same start cannot be longer.

        Result: behaviour is preserved (validated by existing
        ``test_detection_merger.py`` cases) but the inner O(n) scan over
        ``kept`` is replaced by an O(1) check, dropping the worst case from
        O(n²) to O(n log n) — matching the docstring that has always claimed
        sweep-line complexity.

        Args:
            entities: List of entities of the same type

        Returns:
            List of non-overlapping entities with best spans kept
        """
        if len(entities) <= 1:
            return entities

        sorted_entities = sorted(
            entities,
            key=lambda e: (e.start, -(e.end - e.start), -e.score)
        )

        kept: List[PIIEntity] = []
        for current in sorted_entities:
            if not kept:
                kept.append(current)
                continue

            last = kept[-1]
            # Sort guarantees `current.start >= last.start`. The two entities
            # are disjoint iff the previous one ends before this one starts.
            if current.start >= last.end:
                kept.append(current)
            # Otherwise the current entity is either contained in `last` (sort
            # key forbids it from extending further right when starts tie) or
            # partially overlaps; in both cases the earlier-kept entity wins.

        return kept

    def _check_overlap(self, e1: PIIEntity, e2: PIIEntity) -> str:
        """
        Check overlap relationship between two entities.
        
        Args:
            e1: First entity
            e2: Second entity
            
        Returns:
            - 'none': No overlap
            - 'kept_contains_current': e1 fully contains e2
            - 'current_contains_kept': e2 fully contains e1
            - 'partial': Partial overlap
        """
        # No overlap if they don't intersect
        if e1.end <= e2.start or e2.end <= e1.start:
            return 'none'
        
        # Check containment
        if e1.start <= e2.start and e1.end >= e2.end:
            # e1 contains e2 (or they're equal)
            return 'kept_contains_current'
        elif e2.start <= e1.start and e2.end >= e1.end:
            return 'current_contains_kept'
        else:
            # Partial overlap - prefer the one that started first (already in kept)
            return 'partial'

    def _log_overlap_resolution(self, before_count: int, after_count: int) -> None:
        """
        Log overlap resolution statistics if enabled.
        
        Args:
            before_count: Number of entities before resolution
            after_count: Number of entities after resolution
        """
        if not self.log_provenance:
            return
        
        removed_count = before_count - after_count
        if removed_count > 0:
            self.logger.info(
                f"[PII-OVERLAP-RESOLUTION] Removed {removed_count} overlapping fragments"
            )
