"""
Composite multi-model PII detector.

Goal: Provide an opt-in, clean way to run several local HF models in parallel and
merge the results into the existing PIIEntity structure without duplicates.

Design principles:
- Keep current single-model behavior untouched by default (opt-in via env).
- Reuse existing PIIDetector for each backend model to avoid duplicating logic.
- Simple parallelization per model using ThreadPoolExecutor.
- Deterministic deduplication by (start, end, pii_type, text) keeping max score.
- Minimal public API parity: download_model, load_model, detect_pii, mask_pii.

Environment variables:
- MULTI_DETECTOR_ENABLED: "true" (case-insensitive) to activate composite.
- MULTI_DETECTOR_MODELS: semicolon-separated list of HF repo IDs.
  Defaults to [primary_model, "Ar86Bat/multilang-pii-ner"].
- MULTI_DETECTOR_LOG_PROVENANCE: "true" to log source model for each entity and merge decisions.
"""
from __future__ import annotations

import logging
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Iterable, List, Optional, Tuple, Dict

from .pii_detector import PIIDetector, DetectionConfig, PIIEntity


logger = logging.getLogger(__name__)


def _get_provenance_logging() -> bool:
    """Get provenance logging setting from centralized configuration."""
    from config import get_config
    
    try:
        config = get_config()
        return config.detection.multi_detector_log_provenance
    except (ValueError, AttributeError):
        return False


# Toggle detailed provenance logging of model source for each entity
PROVENANCE_LOG_PROVENANCE = _get_provenance_logging()


def get_multi_model_ids(primary_model: str) -> List[str]:
    """Resolve multi-model list from centralized configuration with sensible defaults.

    The default includes the primary model and a multilingual PII NER model to
    enable cross-analysis out-of-the-box.
    """
    from config import get_config
    
    try:
        config = get_config()
        models = config.detection.multi_detector_models
        if not models:
            return [primary_model, "Ar86Bat/multilang-pii-ner"]
        
        # Parse models (comma-separated in config)
        model_list = [m.strip() for m in models if m.strip()]
    except (ValueError, AttributeError):
        # Config not available, use defaults
        return [primary_model, "Ar86Bat/multilang-pii-ner"]
    
    # Ensure the primary model is present at least once
    if primary_model not in model_list:
        model_list.insert(0, primary_model)
    
    # De-duplicate preserving order
    seen = set()
    ordered: List[str] = []
    for m in model_list:
        if m not in seen:
            seen.add(m)
            ordered.append(m)
    return ordered


class MultiModelPIIDetector:
    """Composite detector that orchestrates multiple PIIDetector backends.

    This class mirrors the subset of PIIDetector's API used by the gRPC service
    to minimize integration changes.
    """

    def __init__(self, model_ids: Iterable[str], device: Optional[str] = None):
        self.model_ids = list(model_ids)
        self.device = device
        self.detectors: List[PIIDetector] = [
            PIIDetector(config=DetectionConfig(model_id=m, device=device)) for m in self.model_ids
        ]
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self.logger.info(f"Initialized MultiModelPIIDetector with models: {self.model_ids}")

    # Lifecycle operations -------------------------------------------------
    def download_model(self) -> None:
        for det in self.detectors:
            try:
                det.download_model()
            except Exception as e:
                # Do not fail whole composition; log and continue
                self.logger.warning(f"Download failed for {det.model_id}: {e}")

    def load_model(self) -> None:
        for det in self.detectors:
            try:
                det.load_model()
            except Exception as e:
                self.logger.warning(f"Load failed for {det.model_id}: {e}")

    # Inference operations -------------------------------------------------
    def detect_pii(self, text: str, threshold: Optional[float] = None) -> List[PIIEntity]:
        """Run detection in parallel across models and merge results without duplicates."""
        # Keep association between each detector and its entities for provenance logging
        results_per_detector: List[Tuple[PIIDetector, List[PIIEntity]]] = []

        def _run(det: PIIDetector) -> List[PIIEntity]:
            try:
                return det.detect_pii(text, threshold)
            except Exception as e:  # pragma: no cover - defensive path
                self.logger.warning(f"Detection failed for {det.model_id}: {e}")
                return []

        # Parallel across detectors (one thread per model)
        with ThreadPoolExecutor(max_workers=max(1, len(self.detectors))) as ex:
            futures = {ex.submit(_run, det): det for det in self.detectors}
            for fut in as_completed(futures):
                det = futures[fut]
                ents = fut.result()
                results_per_detector.append((det, ents))
                # Per-entity provenance logging (opt-in to avoid noisy logs)
                if PROVENANCE_LOG_PROVENANCE:
                    for e in ents:
                        try:
                            self.logger.info(
                                "[PII-PROVENANCE] model=%s type=%s text=%s start=%s end=%s score=%.4f",
                                det.model_id,
                                getattr(e, 'pii_type', getattr(e, 'type', '')),
                                getattr(e, 'text', ''),
                                getattr(e, 'start', -1),
                                getattr(e, 'end', -1),
                                float(getattr(e, 'score', 0.0)),
                            )
                        except Exception:
                            # Never break detection due to logging
                            pass

        # Merge and deduplicate
        merged: Dict[Tuple[int, int, str, str], PIIEntity] = {}
        source_by_key: Dict[Tuple[int, int, str, str], str] = {}
        for det, model_entities in results_per_detector:
            for e in model_entities:
                key = (e.start, e.end, e.pii_type, e.text)
                prev = merged.get(key)
                if prev is None:
                    merged[key] = e
                    if PROVENANCE_LOG_PROVENANCE:
                        source_by_key[key] = det.model_id
                elif e.score > prev.score:
                    if PROVENANCE_LOG_PROVENANCE:
                        prev_src = source_by_key.get(key, "?")
                        try:
                            self.logger.info(
                                "[PII-MERGE] key=%s replaced old_model=%s old_score=%.4f new_model=%s new_score=%.4f",
                                str(key), prev_src, float(prev.score), det.model_id, float(e.score)
                            )
                        except Exception:
                            pass
                        source_by_key[key] = det.model_id
                    merged[key] = e

        # Resolve overlapping entities (prefer longer spans for complete information)
        deduplicated = list(merged.values())
        resolved = self._resolve_overlapping_entities(deduplicated)
        
        if PROVENANCE_LOG_PROVENANCE:
            removed_count = len(deduplicated) - len(resolved)
            if removed_count > 0:
                self.logger.info(f"[PII-OVERLAP-RESOLUTION] Removed {removed_count} overlapping fragments")
        
        return resolved

    def mask_pii(self, text: str, threshold: Optional[float] = None) -> Tuple[str, List[PIIEntity]]:
        entities = self.detect_pii(text, threshold)
        # Apply masking in descending order of start index to preserve spans
        entities_sorted = sorted(entities, key=lambda x: x.start, reverse=True)
        masked_text = text
        for entity in entities_sorted:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[: entity.start] + mask + masked_text[entity.end :]
        return masked_text, entities

    # Overlap resolution methods for multi-model entity fusion -----------
    def _resolve_overlapping_entities(self, entities: List[PIIEntity]) -> List[PIIEntity]:
        """Resolve overlapping entities by preferring longer spans.
        
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
        """Resolve overlaps for a single entity type using sweep-line algorithm.
        
        Args:
            entities: List of entities of the same type
            
        Returns:
            List of non-overlapping entities with best spans kept
        """
        if len(entities) <= 1:
            return entities
        
        # Sort by start position, then by span length (longest first), then by score (highest first)
        sorted_entities = sorted(
            entities,
            key=lambda e: (e.start, -(e.end - e.start), -e.score)
        )
        
        kept: List[PIIEntity] = []
        for current in sorted_entities:
            should_keep = True
            remove_indices = []
            
            for i, kept_entity in enumerate(kept):
                overlap_type = self._check_overlap(kept_entity, current)
                
                if overlap_type == 'none':
                    continue
                elif overlap_type == 'current_contains_kept':
                    # Current entity is larger and contains a kept entity - replace it
                    remove_indices.append(i)
                elif overlap_type in ('kept_contains_current', 'partial'):
                    # Kept entity is larger or they partially overlap - skip current
                    should_keep = False
                    break
            
            # Remove kept entities that are contained in the current larger entity
            for idx in reversed(remove_indices):
                kept.pop(idx)
            
            if should_keep:
                kept.append(current)
        
        return kept

    def _check_overlap(self, e1: PIIEntity, e2: PIIEntity) -> str:
        """Check overlap relationship between two entities.
        
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

    # Optional helpers used by tests or service (keep API parity if needed)
    @property
    def model_id(self) -> str:
        # Return primary model id for compatibility (first in list)
        return self.detectors[0].model_id if self.detectors else ""
