"""
Multi-Pass GLiNER Detector with Conflict Resolution.

This module provides MultiPassGlinerDetector that runs GLiNER detection in parallel
across multiple themed label categories, then merges results with deterministic
conflict resolution.

Architecture:
    1. Load PII type configurations from database (grouped by category)
    2. Run GLiNER passes in parallel (one per category)
    3. Aggregate entities by span (offset-based)
    4. Resolve conflicts using pattern-based rules (ConflictResolver)
    5. Return exactly 1 label per span

Why Multi-Pass?
    GLiNER performance degrades with too many labels. By splitting into themed
    categories (IDENTITY, FINANCIAL, MEDICAL, etc.), each pass maintains high
    accuracy. Parallel execution minimizes latency impact.

Why Load from Database?
    - Single source of truth for PII type configuration
    - Dynamic updates without code changes
    - Consistent with gliner_detector.py approach
    - Categories are defined in pii_type_config.category column
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import ModelNotLoadedError, PIIDetectionError
from pii_detector.infrastructure.detector.conflict_resolver import (
    ConflictResolver,
)
from pii_detector.infrastructure.detector.gliner_detector import GLiNERDetector


@dataclass(frozen=True, slots=True)
class SpanKey:
    """Key for grouping entities by span position."""
    start: int
    end: int


@dataclass
class AggregatedSpan:
    """Represents a span with all detected labels from different passes."""
    start: int
    end: int
    text: str
    labels: List[Tuple[str, float]]  # List of (pii_type, score) tuples

    def has_conflict(self) -> bool:
        """Returns True if multiple different labels were detected for this span."""
        unique_types = set(label for label, _ in self.labels)
        return len(unique_types) > 1


class MultiPassGlinerDetector:
    """
    Multi-Pass GLiNER detector with parallel category detection and conflict resolution.

    This detector addresses GLiNER's label limit by running multiple focused passes
    in parallel, each with a themed set of labels. Results are then merged using
    deterministic conflict resolution rules.

    Architecture:
        ┌─────────────────────────────────────────────────┐
        │              detect_pii(text)                   │
        │                     │                           │
        │        Load categories from database            │
        │                     │                           │
        │  ┌──────────────────┼──────────────────┐        │
        │  │   ThreadPoolExecutor (parallel)     │        │
        │  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐   │        │
        │  │  │IDENT│ │FINAN│ │MEDIC│ │ IT  │...│        │
        │  │  └──┬──┘ └──┬──┘ └──┬──┘ └──┬──┘   │        │
        │  └─────┼───────┼───────┼───────┼──────┘        │
        │        └───────┴───────┴───────┘               │
        │                     │                           │
        │        Aggregate by span (offset-based)         │
        │                     │                           │
        │        Resolve conflicts (ConflictResolver)     │
        │                     │                           │
        │        Return: 1 label per span                 │
        └─────────────────────────────────────────────────┘

    Key Design Decisions:
        1. Categories loaded from DB: Uses pii_type_config.category column
        2. Parallel passes: Each pass is independent, maximizing throughput
        3. ConflictResolver: Pattern-based rules with type-specific validation
        4. Reuses GLiNERDetector: Leverages existing model management
    """

    def __init__(self, config: Optional[DetectionConfig] = None):
        """
        Initialize the Multi-Pass GLiNER detector.

        Args:
            config: Detection configuration. Uses default if None.
        """
        self.config = config or DetectionConfig()
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

        # Create the underlying GLiNER detector (reuses existing implementation)
        self._gliner_detector = GLiNERDetector(config=self.config)

        # Category mappings - loaded from database on first detection
        self._pass_categories: Optional[Dict[str, Dict[str, str]]] = None
        self._pii_type_to_category: Dict[str, str] = {}

        # Conflict resolver - initialized after loading categories
        self._conflict_resolver: Optional[ConflictResolver] = None

        # Load parallel processing config
        self._load_parallel_config()

        # Initialize persistent executor
        self.executor = None
        if self.parallel_enabled:
            self.executor = ThreadPoolExecutor(max_workers=self.max_workers)

        self.logger.info(
            f"MultiPassGlinerDetector initialized with {self.max_workers} workers "
            f"(categories loaded on first detection)"
        )

    def _load_parallel_config(self) -> None:
        """Load parallel processing configuration from settings."""
        from pii_detector.application.config.detection_policy import _load_llm_config

        try:
            config = _load_llm_config()
            parallel_config = config.get("parallel_processing", {})
            self.parallel_enabled = parallel_config.get("enabled", True)
            self.max_workers = parallel_config.get("max_workers", 10)
        except Exception as e:
            self.logger.debug(f"Failed to load parallel config: {e}, using defaults")
            self.parallel_enabled = True
            self.max_workers = 10

    def _load_categories_from_database(self) -> None:
        """
        Load PII type configurations from database and group by category.
        
        This method also fetches the 'nb_of_label_by_pass' configuration to
        optimize execution passes by merging small categories.
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import (
                get_database_config_adapter
            )

            adapter = get_database_config_adapter()
            
            # Fetch global config for limit
            global_config = adapter.fetch_config()
            limit = global_config.get('nb_of_label_by_pass', 35) if global_config else 35
            
            pii_type_configs = adapter.fetch_pii_type_configs(detector='GLINER')

            if not pii_type_configs:
                self.logger.warning(
                    "No PII type configs found in database for GLINER, using fallback"
                )
                self._use_fallback_categories()
                return

            # Build pii_type_to_category mapping (needed for ConflictResolver logic)
            # This respects the BUSINESS categories from DB
            pii_type_to_category: Dict[str, str] = {}
            for pii_type, config in pii_type_configs.items():
                if config.get('enabled', False):
                    category = config.get('category', 'UNKNOWN')
                    pii_type_to_category[pii_type] = category
            
            self._pii_type_to_category = pii_type_to_category

            # Build optimized execution passes (chunks of labels)
            # This ignores business categories for execution efficiency
            self._pass_categories = self._optimize_passes(pii_type_configs, limit)

            # Initialize conflict resolver with category mapping
            self._conflict_resolver = ConflictResolver(pii_type_to_category)

            # Log summary
            total_types = sum(len(labels) for labels in self._pass_categories.values())
            self.logger.info(
                f"Loaded {total_types} PII types from database, optimized into {len(self._pass_categories)} passes (limit={limit})"
            )
            for pass_name, labels in sorted(self._pass_categories.items()):
                self.logger.info(f"  {pass_name}: {len(labels)} labels")

        except Exception as e:
            self.logger.error(f"Failed to load categories from database: {e}")
            self._use_fallback_categories()

    def _optimize_passes(self, pii_type_configs: dict, limit: int) -> Dict[str, Dict[str, str]]:
        """
        Chunk enabled PII types into optimized passes based on limit.
        
        Args:
            pii_type_configs: Configuration for all PII types
            limit: Maximum number of labels per pass
            
        Returns:
            Dictionary of {pass_name: {label: pii_type}}
        """
        # Collect all enabled (label, pii_type) pairs
        all_labels: List[Tuple[str, str]] = []
        for pii_type, config in pii_type_configs.items():
            if not config.get('enabled', False):
                continue
            
            # Skip if not GLINER or ALL
            config_detector = config.get('detector', 'ALL')
            if config_detector != 'ALL' and config_detector != 'GLINER':
                continue

            detector_label = config.get('detector_label')
            if detector_label:
                all_labels.append((detector_label, pii_type))
        
        # Sort for deterministic batches
        all_labels.sort(key=lambda x: x[1])  # Sort by pii_type
        
        batches: Dict[str, Dict[str, str]] = {}
        
        # Chunk into batches
        for i in range(0, len(all_labels), limit):
            chunk = all_labels[i:i+limit]
            batch_name = f"BATCH_{i//limit + 1}"
            batches[batch_name] = {label: pii_type for label, pii_type in chunk}
            
        if not batches:
             # Fallback if nothing enabled
             return {}
             
        return batches

    def _build_categories_from_config(self, pii_type_configs: dict, limit: Optional[int] = None) -> Dict[str, Dict[str, str]]:
        """
        Build categories mapping dynamically from passed configuration.
        Also applies optimization/chunking using default limit (35).

        Args:
            pii_type_configs: Dictionary of PII type configurations
            limit: Maximum number of labels per pass (chunk size)

        Returns:
            Optimized execution passes
        """
        # We use a default limit here because we might not have DB access context
        # when this is called via direct config injection, but usually
        # pii_type_configs implies we want to override behavior.
        # We'll use 35 as a safe default if not provided.
        return self._optimize_passes(pii_type_configs, limit=limit or 35)

    def _use_fallback_categories(self) -> None:
        """
        Use hardcoded fallback categories if database is unavailable.

        This is a minimal fallback to ensure the detector can still function.
        In production, categories should always come from the database.
        """
        self.logger.warning("Using fallback hardcoded categories (database unavailable)")

        self._pass_categories = {
            "IDENTITY": {
                "person name": "PERSON_NAME",
                "social security number": "SSN",
                "passport number": "PASSPORT_NUMBER",
                "driver license number": "DRIVER_LICENSE_NUMBER",
            },
            "CONTACT": {
                "email address": "EMAIL",
                "phone number": "PHONE_NUMBER",
                "street address": "ADDRESS",
            },
            "FINANCIAL": {
                "credit card number": "CREDIT_CARD_NUMBER",
                "bank account number": "BANK_ACCOUNT_NUMBER",
                "iban": "IBAN",
            },
            "MEDICAL": {
                "avs number": "AVS_NUMBER",
                "patient id": "PATIENT_ID",
                "medical diagnosis": "DIAGNOSIS",
            },
            "IT": {
                "ip address": "IP_ADDRESS",
                "api key": "API_KEY",
                "password": "PASSWORD",
            },
        }

        # Build reverse mapping
        self._pii_type_to_category = {}
        for category, labels in self._pass_categories.items():
            for pii_type in labels.values():
                self._pii_type_to_category[pii_type] = category

        self._conflict_resolver = ConflictResolver(self._pii_type_to_category)

    @property
    def model_id(self) -> str:
        """Get model ID for backward compatibility."""
        return f"multi-pass-{self._gliner_detector.model_id}"

    def download_model(self) -> None:
        """Download the GLiNER model files."""
        self._gliner_detector.download_model()

    def load_model(self) -> None:
        """Load the GLiNER model."""
        self._gliner_detector.load_model()
        self.logger.info("MultiPassGlinerDetector model loaded successfully")

    def detect_pii(
        self,
        text: str,
        threshold: Optional[float] = None,
        categories: Optional[List[str]] = None,
        pii_type_configs: Optional[dict] = None,
        chunk_size: Optional[int] = None
    ) -> List[PIIEntity]:
        """
        Detect PII using multi-pass parallel detection with conflict resolution.

        Args:
            text: Text to analyze
            threshold: Confidence threshold (default: 0.3)
            categories: Optional list of categories to run (default: all)
            pii_type_configs: Optional PII type configs for dynamic settings
            chunk_size: Optional limit for labels per pass (from DB config)

        Returns:
            List of PIIEntity with exactly 1 label per span

        Raises:
            ModelNotLoadedError: If model is not loaded9
            PIIDetectionError: If detection fails
        """
        if not self._gliner_detector.model:
            raise ModelNotLoadedError("Model must be loaded before detection")

        # Use passed pii_type_configs to build dynamic categories if provided
        # Otherwise fallback to loaded categories
        pass_categories = self._pass_categories

        if pii_type_configs:
            pass_categories = self._build_categories_from_config(pii_type_configs, limit=chunk_size)

            # Build pii_type -> category mapping for conflict resolver
            pii_type_to_category: Dict[str, str] = {}
            for category, labels in pass_categories.items():
                for pii_type in labels.values():
                    pii_type_to_category[pii_type] = category
            
            # Initialize conflict resolver with dynamic config
            self._pii_type_to_category = pii_type_to_category
            self._conflict_resolver = ConflictResolver(pii_type_to_category)
        elif self._pass_categories is None:
            # Load categories from database on first call if no config passed
            self._load_categories_from_database()
            pass_categories = self._pass_categories

        threshold = threshold or self.config.threshold
        detection_id = self._generate_detection_id()
        categories_to_run = categories or list(pass_categories.keys())

        self.logger.info(
            f"[{detection_id}] Starting multi-pass detection on {len(text)} chars "
            f"with {len(categories_to_run)} categories, threshold={threshold}"
        )

        start_time = time.time()

        try:
            # Step 1: Run parallel passes
            all_entities = self._run_parallel_passes(
                text, threshold, detection_id, categories_to_run, pass_categories
            )

            # Step 2: Aggregate by span
            aggregated_spans = self._aggregate_by_span(all_entities)

            # Step 3: Resolve conflicts
            resolved_entities = self._resolve_conflicts(aggregated_spans, detection_id)

            # Step 4: Handle overlapping spans (wider span wins)
            final_entities = self._resolve_overlapping_spans(resolved_entities)

            elapsed = time.time() - start_time

            # Log comprehensive summary
            self._log_detection_summary(
                detection_id, elapsed, len(text),
                len(categories_to_run), len(all_entities),
                len(aggregated_spans), len(resolved_entities), len(final_entities)
            )

            return final_entities

        except Exception as e:
            self.logger.error(f"[{detection_id}] Multi-pass detection failed: {e}")
            raise PIIDetectionError(f"Multi-pass detection failed: {e}") from e

    def mask_pii(
        self,
        text: str,
        threshold: Optional[float] = None
    ) -> Tuple[str, List[PIIEntity]]:
        """
        Mask PII in text content.

        Args:
            text: Text to mask
            threshold: Confidence threshold

        Returns:
            Tuple of (masked_text, detected_entities)
        """
        entities = self.detect_pii(text, threshold)

        # Sort by position (reverse) to mask from end to start
        entities_sorted = sorted(entities, key=lambda e: e.start, reverse=True)
        masked_text = text

        for entity in entities_sorted:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[:entity.start] + mask + masked_text[entity.end:]

        self.logger.info(f"Masked {len(entities)} PII entities")
        return masked_text, entities

    def _run_parallel_passes(
        self,
        text: str,
        threshold: float,
        detection_id: str,
        categories: List[str],
        pass_categories: Dict[str, Dict[str, str]]
    ) -> List[PIIEntity]:
        """
        Run detection passes in parallel using ThreadPoolExecutor.

        Each pass uses a different label set (category) to avoid GLiNER's
        label limit degradation.

        Args:
            text: Text to analyze
            threshold: Detection threshold
            detection_id: Logging ID
            categories: Categories to run
            pass_categories: Categories configuration to use

        Returns:
            All entities from all passes (may have duplicates/conflicts)
        """
        all_entities: List[PIIEntity] = []

        if self.parallel_enabled and self.executor and len(categories) > 1:
            self.logger.info(
                f"[{detection_id}] Running {len(categories)} passes in parallel "
                f"with {self.max_workers} workers"
            )

            future_to_category = {
                self.executor.submit(
                    self._run_single_pass,
                    text, threshold, detection_id, category, pass_categories
                ): category
                for category in categories
            }

            for future in as_completed(future_to_category):
                category = future_to_category[future]
                try:
                    entities = future.result()
                    all_entities.extend(entities)
                    self.logger.debug(
                        "[%s] Pass %s: %d entities",
                        detection_id, category, len(entities)
                    )
                except Exception as e:
                    self.logger.error(
                        f"[{detection_id}] Pass {category} failed: {e}"
                    )
                    raise
        else:
            # Sequential fallback
            self.logger.info(f"[{detection_id}] Running {len(categories)} passes sequentially")
            for category in categories:
                entities = self._run_single_pass(text, threshold, detection_id, category, pass_categories)
                all_entities.extend(entities)

        self.logger.info(
            f"[{detection_id}] All passes complete: {len(all_entities)} total entities"
        )
        return all_entities

    def _run_single_pass(
        self,
        text: str,
        threshold: float,
        detection_id: str,
        category: str,
        pass_categories: Dict[str, Dict[str, str]]
    ) -> List[PIIEntity]:
        """
        Run a single detection pass for one category.

        Args:
            text: Text to analyze
            threshold: Detection threshold
            detection_id: Logging ID
            category: Category to detect
            pass_categories: Categories configuration to use

        Returns:
            Entities detected in this pass
        """
        if category not in pass_categories:
            self.logger.warning(f"[{detection_id}] Unknown category: {category}")
            return []

        label_mapping = pass_categories[category]
        labels = list(label_mapping.keys())

        pass_start = time.time()

        # Use GLiNER's model directly to predict with specific labels
        raw_entities = self._gliner_detector.model.predict_entities(
            text,
            labels,
            threshold=threshold
        )

        # Convert to PIIEntity format
        entities = []
        for entity in raw_entities:
            gliner_label = entity.get("label", "")
            pii_type = label_mapping.get(gliner_label, gliner_label.upper())

            start = entity.get("start", 0)
            end = entity.get("end", 0)
            actual_text = text[start:end] if 0 <= start < end <= len(text) else ""

            pii_entity = PIIEntity(
                text=actual_text,
                pii_type=pii_type,
                type_label=pii_type,
                start=start,
                end=end,
                score=entity.get("score", 0.0)
            )
            pii_entity.source = DetectorSource.GLINER
            entities.append(pii_entity)

        pass_time = time.time() - pass_start

        # Log pass results
        if entities:
            entity_types = [e.pii_type for e in entities]
            self.logger.info(
                f"[{detection_id}] Pass {category}: {len(entities)} entities in {pass_time:.2f}s - "
                f"types: {set(entity_types)}"
            )
        else:
            self.logger.debug(
                "[%s] Pass %s: 0 entities in %.2fs",
                detection_id, category, pass_time
            )

        return entities

    def _aggregate_by_span(
        self,
        entities: List[PIIEntity]
    ) -> List[AggregatedSpan]:
        """
        Group entities by their span (start, end position).

        This allows us to see all labels that were detected for the same
        text span across different passes.

        Args:
            entities: All entities from all passes

        Returns:
            Aggregated spans with all labels
        """
        span_map: Dict[SpanKey, AggregatedSpan] = {}

        for entity in entities:
            key = SpanKey(entity.start, entity.end)
            span = span_map.get(key)

            if span is None:
                span = AggregatedSpan(
                    start=entity.start,
                    end=entity.end,
                    text=entity.text,
                    labels=[]
                )
                span_map[key] = span

            span.labels.append((entity.pii_type, entity.score))

        return list(span_map.values())

    def _resolve_conflicts(
        self,
        spans: List[AggregatedSpan],
        detection_id: str
    ) -> List[PIIEntity]:
        """
        Resolve conflicts for each span using ConflictResolver.

        Args:
            spans: Aggregated spans with all labels
            detection_id: Logging ID

        Returns:
            Resolved entities (exactly 1 per span)
        """
        resolved: List[PIIEntity] = []
        single_label_count = 0
        conflict_count = 0

        for span in spans:
            if not span.has_conflict():
                # Single label - accept the highest score
                single_label_count += 1
                best_label, best_score = max(span.labels, key=lambda x: x[1])
                entity = PIIEntity(
                    text=span.text,
                    pii_type=best_label,
                    type_label=best_label,
                    start=span.start,
                    end=span.end,
                    score=best_score
                )
                entity.source = DetectorSource.GLINER
                resolved.append(entity)

                # Log single-label detections at debug level
                if self.logger.isEnabledFor(logging.DEBUG):
                    text_preview = span.text[:40] + "..." if len(span.text) > 40 else span.text
                    self.logger.debug(
                        "[%s] DETECTED | '%s' -> %s (%.2f)",
                        detection_id, text_preview, best_label, best_score
                    )
            else:
                # Multiple labels - use ConflictResolver (which logs conflicts)
                conflict_count += 1
                result = self._conflict_resolver.resolve(
                    span.text,
                    span.labels,
                    detection_id
                )
                if result:
                    winner_type, winner_score = result
                    entity = self._conflict_resolver.build_pii_entity(
                        text=span.text,
                        pii_type=winner_type,
                        score=winner_score,
                        start=span.start,
                        end=span.end,
                        source=DetectorSource.GLINER
                    )
                    resolved.append(entity)

        self.logger.debug(
            "[%s] Conflict resolution: %d single-label, %d conflicts",
            detection_id, single_label_count, conflict_count
        )

        return resolved

    def _resolve_overlapping_spans(
        self,
        entities: List[PIIEntity]
    ) -> List[PIIEntity]:
        """
        Resolve overlapping spans by keeping the wider span.

        When spans partially overlap, the wider (more complete) span wins.
        This is applied after conflict resolution.

        Args:
            entities: Entities after conflict resolution

        Returns:
            Entities with overlaps removed
        """
        if not entities:
            return []

        # Sort by start position, then by span length (longest first)
        sorted_entities = sorted(
            entities,
            key=lambda e: (e.start, -(e.end - e.start))
        )

        kept: List[PIIEntity] = []
        max_end = -1

        for entity in sorted_entities:
            # If current entity starts after (or at) the end of the last kept entity,
            # it means no overlap with any previously kept entity (due to sort order).
            if entity.start >= max_end:
                kept.append(entity)
                max_end = max(max_end, entity.end)
            # Else: It overlaps with a previously kept entity.
            # Since we prioritized "longest first" for same start, and "earlier start" generally,
            # the existing kept entity is preferred. We discard the current one.

        return kept

    def _generate_detection_id(self) -> str:
        """Generate unique detection ID for logging."""
        return f"multipass_{int(time.time() * 1000) % 10000}"

    def _log_detection_summary(
        self,
        detection_id: str,
        elapsed: float,
        text_length: int,
        num_categories: int,
        raw_count: int,
        span_count: int,
        resolved_count: int,
        final_count: int
    ) -> None:
        """
        Log comprehensive detection summary with statistics.

        Args:
            detection_id: Unique ID for this detection run
            elapsed: Total time taken in seconds
            text_length: Length of input text
            num_categories: Number of category passes run
            raw_count: Raw entity count from all passes
            span_count: Unique spans after aggregation
            resolved_count: Entities after conflict resolution
            final_count: Final entities after overlap removal
        """
        # Get conflict stats if available
        conflict_stats = {}
        if self._conflict_resolver:
            conflict_stats = self._conflict_resolver.get_conflict_stats()

        # Calculate derived metrics
        conflicts_resolved = conflict_stats.get("total_conflicts", 0)
        overlaps_removed = resolved_count - final_count

        self.logger.info(
            f"\n{'='*70}\n"
            f"[{detection_id}] MULTI-PASS DETECTION SUMMARY\n"
            f"{'='*70}\n"
            f"  Input:      {text_length:,} chars | {num_categories} categories\n"
            f"  Pipeline:   {raw_count} raw -> {span_count} spans -> {resolved_count} resolved -> {final_count} final\n"
            f"  Conflicts:  {conflicts_resolved} resolved "
            f"(pattern: {conflict_stats.get('resolved_by_pattern', 0)}, "
            f"fallback: {conflict_stats.get('resolved_by_fallback', 0)}, "
            f"category: {conflict_stats.get('resolved_by_category', 0)})\n"
            f"  Overlaps:   {overlaps_removed} removed\n"
            f"  Time:       {elapsed:.3f}s ({elapsed/num_categories*1000:.1f}ms per category)\n"
            f"{'='*70}"
        )

        # Reset conflict stats for next detection
        if self._conflict_resolver:
            self._conflict_resolver.reset_conflict_stats()

    def _apply_masks(
        self,
        content: str,
        entities: List[Any]
    ) -> str:
        """
        Apply masks to content for detected PII entities.

        This method is called by pii_service.py to generate masked content.
        It handles both PIIEntity objects and dictionary entities.

        Args:
            content: Original text content
            entities: List of detected entities (dict or PIIEntity)

        Returns:
            Masked content with PII replaced by type labels
        """
        if not entities:
            return content

        # Convert to sortable list with start, end, and type
        mask_data = []
        for entity in entities:
            if hasattr(entity, 'start'):
                # PIIEntity object
                mask_data.append({
                    'start': entity.start,
                    'end': entity.end,
                    'type': getattr(entity, 'pii_type', getattr(entity, 'type', 'PII'))
                })
            elif isinstance(entity, dict):
                # Dictionary entity
                mask_data.append({
                    'start': entity.get('start', 0),
                    'end': entity.get('end', 0),
                    'type': entity.get('type', entity.get('pii_type', 'PII'))
                })

        # Sort by position (reverse) to mask from end to start
        mask_data.sort(key=lambda e: e['start'], reverse=True)

        masked_text = content
        for item in mask_data:
            start = item['start']
            end = item['end']
            pii_type = item['type']

            if 0 <= start < end <= len(masked_text):
                mask = f"[{pii_type}]"
                masked_text = masked_text[:start] + mask + masked_text[end:]

        return masked_text

    def __del__(self):
        """Cleanup when detector is destroyed."""
        try:
            if hasattr(self, 'executor') and self.executor:
                self.executor.shutdown(wait=True)
                self.executor = None

            if hasattr(self, '_gliner_detector'):
                del self._gliner_detector
        except Exception as e:
            # During interpreter shutdown, logging might fail
            try:
                if hasattr(self, 'logger'):
                    self.logger.error(f"Cleanup error: {e}")
            except Exception:
                pass