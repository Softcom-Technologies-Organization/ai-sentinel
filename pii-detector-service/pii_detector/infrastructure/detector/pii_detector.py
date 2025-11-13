"""
PII Detector Module with Modern Python Best Practices.

This module provides functionality for detecting Personally Identifiable Information (PII)
in text content using the Piiranha model with optimizations for memory usage and code quality.
"""

import logging
import time
from typing import Dict, List, Optional, Tuple

import torch
import unicodedata
from transformers import AutoTokenizer, AutoModelForTokenClassification, \
  pipeline

from pii_detector.domain.service.entity_processor import EntityProcessor
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.exception.exceptions import PIIDetectionError, ModelNotLoadedError
# Import managers
from pii_detector.infrastructure.model_management.memory_manager import MemoryManager
from pii_detector.infrastructure.model_management.model_manager import ModelManager

# Constants
_MODEL_NOT_LOADED_ERROR_MESSAGE = "The model must be loaded before use"
_TRAILING_PUNCTUATION = {".", ",", ";", ":"}
_SEPARATOR_CHARS = {" ", "\t", "\n", ",", ";", ":"}



class PIIDetector:
    """
    Modern PII detector with improved code structure and error handling.


    This class follows SOLID principles and modern Python best practices
    for detecting Personally Identifiable Information in text.
    """

    def __init__(self, config: Optional[DetectionConfig] = None, model_id: Optional[str] = None, 
                 device: Optional[str] = None, max_length: Optional[int] = None):
        """
        Initialize the PII detector.

        Args:
            config: Detection configuration. Uses default if None.
            model_id: Model ID for backward compatibility
            device: Device for backward compatibility  
            max_length: Max length for backward compatibility
        """
        # Handle backward compatibility with old constructor signature
        if config is None:
            config = DetectionConfig()
            if model_id is not None:
                config.model_id = model_id
            if device is not None:
                config.device = device
            if max_length is not None:
                config.max_length = max_length
        
        self.config = config
        self.device = self.config.device or ('cuda' if torch.cuda.is_available() else 'cpu')

        # Initialize components
        self.memory_manager = MemoryManager()
        self.model_manager = ModelManager(self.config)
        self.entity_processor = EntityProcessor()

        # Model components
        self.tokenizer: Optional[AutoTokenizer] = None
        self.model: Optional[AutoModelForTokenClassification] = None
        self.pipeline: Optional[pipeline] = None

        # Setup
        self.memory_manager.setup_memory_optimization()
        self.memory_manager.optimize_for_device(self.device)

        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self.logger.info(f"PII Detector initialized with device: {self.device}")

    # Backward compatibility properties
    @property
    def model_id(self) -> str:
        """Get model ID for backward compatibility."""
        return self.config.model_id

    @property
    def max_length(self) -> int:
        """Get max length for backward compatibility."""
        return self.config.max_length

    @property
    def pipe(self) -> Optional[pipeline]:
        """Get pipeline for backward compatibility."""
        return self.pipeline

    @property
    def label_mapping(self) -> Dict[str, str]:
        """Get label mapping for backward compatibility."""
        return self.entity_processor.label_mapping

    # Backward compatibility methods
    def _detect_emails_regex(self) -> List:
      """Detect emails using regex for backward compatibility."""
      return self.entity_processor.detect_emails_with_regex()

    def _detect_pii_chunked(self, text: str, threshold: float = None) -> List[PIIEntity]:
        """Process chunked text for backward compatibility."""
        if threshold is None:
            threshold = self.config.threshold
        detection_id = self._generate_detection_id()
        return self._detect_pii_chunked_internal(text, threshold, detection_id)

    def _detect_pii_chunked_internal(self, text: str, threshold: float, detection_id: str) -> List[PIIEntity]:
        """Process text using token-based splitting aligned with model context (256 tokens)."""
        self.logger.info(f"[{detection_id}] Using token-based splitting for detection")
        return self._detect_pii_token_splitting(text, threshold)

    def download_model(self) -> None:
        """Download the model files from Hugging Face."""
        self.model_manager.download_model()

    def load_model(self) -> None:
        """Load the model with memory optimizations."""
        try:
            self.tokenizer, self.model = self.model_manager.load_model_components()
            self.pipeline = self._create_pipeline()

            self.logger.info("Model loaded successfully")
            self.memory_manager.clear_cache(self.device)

        except Exception as e:
            self.logger.error(f"Failed to load model: {str(e)}")
            raise

    def detect_pii(self, text: str, threshold: Optional[float] = None) -> List[PIIEntity]:
        """
        Detect PII in text content.

        Args:
            text: Text to analyze for PII
            threshold: Confidence threshold for detection

        Returns:
            List of detected PII entities

        Raises:
            ModelNotLoadedError: If model is not loaded
            PIIDetectionError: If detection fails
        """
        if not self.pipeline:
            raise ModelNotLoadedError(_MODEL_NOT_LOADED_ERROR_MESSAGE)

        threshold = threshold or self.config.threshold
        detection_id = self._generate_detection_id()

        self.logger.info(f"[{detection_id}] Starting PII detection for {len(text)} characters")

        try:
            if len(text) > self.config.long_text_threshold:
                return self._detect_pii_chunked(text, threshold)
            else:
                return self._detect_pii_standard(text, threshold, detection_id)

        except Exception as e:
            self.logger.error(f"[{detection_id}] Detection failed: {str(e)}")
            raise PIIDetectionError(f"PII detection failed: {str(e)}") from e
        finally:
            self.memory_manager.clear_cache(self.device)

    def detect_pii_batch(self, texts: List[str], threshold: Optional[float] = None, batch_size: Optional[int] = None) -> List[List[PIIEntity]]:
        """
        Detect PII in multiple texts using batch processing.

        Args:
            texts: List of texts to analyze
            threshold: Confidence threshold for detection
            batch_size: Batch size for processing (overrides config if provided)

        Returns:
            List of entity lists for each text
        """
        if not self.pipeline:
            raise ModelNotLoadedError(_MODEL_NOT_LOADED_ERROR_MESSAGE)

        threshold = threshold or self.config.threshold
        effective_batch_size = batch_size or self.config.batch_size
        all_results = []

        for i in range(0, len(texts), effective_batch_size):
            batch = texts[i:i + effective_batch_size]
            batch_results = self._process_batch(batch, threshold)
            all_results.extend(batch_results)

            # Periodic cleanup
            if (i + effective_batch_size) % (effective_batch_size * 10) == 0:
                self.memory_manager.clear_cache(self.device)

        return all_results

    def mask_pii(self, text: str, threshold: Optional[float] = None) -> Tuple[str, List[PIIEntity]]:
        """
        Mask PII in text content.

        Args:
            text: Text to mask
            threshold: Confidence threshold for detection

        Returns:
            Tuple of (masked_text, detected_entities)
        """
        entities = self.detect_pii(text, threshold)
        masked_text = self._apply_masks(text, entities)

        self.logger.info(f"Masked {len(entities)} PII entities")
        return masked_text, entities

    def get_summary(self, text: str, threshold: Optional[float] = None) -> Dict[str, int]:
        """
        Get a summary of detected PII types.

        Args:
            text: Text to analyze
            threshold: Confidence threshold for detection

        Returns:
            Dictionary mapping PII type labels to counts
        """
        entities = self.detect_pii(text, threshold)
        summary = {}

        for entity in entities:
            pii_type = entity.type_label
            summary[pii_type] = summary.get(pii_type, 0) + 1

        self.logger.info(f"Generated summary with {len(summary)} PII types")
        return summary

    def clear_cache(self) -> None:
        """Clear memory caches."""
        self.memory_manager.clear_cache(self.device)

    def _create_pipeline(self) -> pipeline:
        """Create the inference pipeline."""
        return pipeline(
            "token-classification",
            model=self.model,
            tokenizer=self.tokenizer,
            aggregation_strategy="simple",
            device=-1 if self.device == 'cpu' else 0,
            batch_size=1
        )

    def _split_text_by_tokens(self, text: str, max_tokens: int) -> List[Tuple[str, int, int]]:
        """Split text into segments by tokenizer tokens with exact character spans.
        
        Returns a list of tuples: (segment_text, start_char, end_char).
        
        Business rule: Use overlap so that entities crossing window boundaries
        appear entirely in at least one segment.
        """
        if not text:
            return []
        if not self.tokenizer:
            raise ModelNotLoadedError(_MODEL_NOT_LOADED_ERROR_MESSAGE)

        # Determine stride (overlap) from config while respecting max_tokens
        stride = max(0, min(getattr(self.config, 'stride_tokens', 0), max_tokens - 1))

        # Use tokenizer overflow to create token windows with offsets and overlap
        encoding = self.tokenizer(
            text,
            return_offsets_mapping=True,
            truncation=True,
            max_length=max_tokens,
            return_overflowing_tokens=True,
            stride=stride,
            padding=False
        )

        # Ensure iterable over segments
        input_ids = encoding["input_ids"]
        offsets_list = encoding["offset_mapping"]

        segments: List[Tuple[str, int, int]] = []
        for i in range(len(input_ids)):
            offsets = offsets_list[i]
            # Filter out special tokens which often have (0, 0)
            valid_offsets = [o for o in offsets if (o[0] != 0 or o[1] != 0)]
            if not valid_offsets:
                continue
            start_char = valid_offsets[0][0]
            end_char = valid_offsets[-1][1]
            if end_char <= start_char:
                continue
            segment_text = text[start_char:end_char]
            segments.append((segment_text, start_char, end_char))

        return segments

    def _detect_pii_token_splitting(self, text: str, threshold: float) -> List[PIIEntity]:
        """Detect PII by splitting the input into 256-token segments and merging results.
        
        Business rule: Normalize text to Unicode NFC form before tokenization to reduce
        splitting issues with diacritics (é, ô, î, etc.). This improves detection quality
        for multilingual models and names with accented characters. Entity positions refer
        to the normalized text.
        """
        # Normalize text to canonical Unicode form (NFC) to reduce tokenizer splitting on diacritics
        normalized_text = unicodedata.normalize('NFC', text)
        
        segments = self._split_text_by_tokens(normalized_text, max_tokens=256)
        if not segments:
            return []

        all_entities: List[PIIEntity] = []
        for segment_text, start_char, _ in segments:
            with torch.no_grad():
                raw = self.pipeline(segment_text)
            entities = self.entity_processor.process_entities(raw, threshold)
            # Adjust positions and avoid duplicates
            for e in entities:
                adjusted = PIIEntity(
                    text=e.text,
                    pii_type=e.pii_type,
                    type_label=e.type_label,
                    start=e.start + start_char,
                    end=e.end + start_char,
                    score=e.score
                )
                if not self._is_duplicate_entity(adjusted, all_entities):
                    all_entities.append(adjusted)

        # Use normalized text for post-processing to ensure position alignment
        all_entities = self._post_process_entities(normalized_text, all_entities)
        return all_entities

    def _detect_pii_standard(self, text: str, threshold: float, detection_id: str) -> List[PIIEntity]:
        """Standard PII detection using 256-token segments for all texts."""
        start_time = time.time()

        entities = self._detect_pii_token_splitting(text, threshold)

        detection_time = time.time() - start_time
        self.logger.info(f"[{detection_id}] Detection completed in {detection_time:.3f}s, found {len(entities)} entities")

        return entities


    def _process_batch(self, batch: List[str], threshold: float) -> List[List[PIIEntity]]:
        """Process a batch of texts."""
        try:
            batch_results = self.pipeline(batch)
            processed_results = []

            results_list = batch_results if isinstance(batch_results[0], list) else [batch_results]

            for results in results_list:
                entities = self.entity_processor.process_entities(results, threshold)
                processed_results.append(entities)

            return processed_results

        except Exception as e:
            self.logger.error(f"Error processing batch: {str(e)}")
            return [[] for _ in batch]

    def _apply_masks(self, text: str, entities: List[PIIEntity]) -> str:
        """Apply masks to detected PII entities."""
        entities_sorted = sorted(entities, key=lambda x: x.start, reverse=True)
        masked_text = text

        for entity in entities_sorted:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[:entity.start] + mask + masked_text[entity.end:]

        return masked_text

    def _is_duplicate_entity(self, entity: PIIEntity, existing_entities: List[PIIEntity]) -> bool:
        """Check if an entity with the same span and type already exists."""
        return any(
            (e.start == entity.start) and (e.end == entity.end) and (e.pii_type == entity.pii_type)
            for e in existing_entities
        )

    def _post_process_entities(self, text: str, entities: List[PIIEntity]) -> List[PIIEntity]:
        """Normalize entities after model inference.
        Business rules:
        - If an EMAIL span lacks a domain but the source text contains '@' immédiatement après,
          étendre le span pour inclure le domaine jusqu'au prochain séparateur.
        - Si un ZIPCODE contient également une ville (ex.: "69007 Lyon" ou "69007, Lyon"),
          le scinder en ZIPCODE (5 chiffres) et CITY (libellé ville) afin de respecter le
          contrat métier FR.
        - Merge adjacent entities of the same type to handle tokenizer splitting issues
          (e.g., "Rod"+"ri"+"gue" -> "Rodrigue", "Ben"+"o"+"ît" -> "Benoît").
        """
        if not entities:
            return []

        fixed = self._expand_email_domain(text, entities)
        fixed = self._split_zipcode_and_city(text, fixed)
        fixed = self._merge_adjacent_entities(text, fixed)

        # De-duplicate by (type, start, end)
        unique: List[PIIEntity] = []
        for e in fixed:
            if not self._is_duplicate_entity(e, unique):
                unique.append(e)
        return unique

    def _expand_email_domain(self, text: str, entities: List[PIIEntity]) -> List[PIIEntity]:
        """Expand EMAIL entities by searching forward for '@' and capturing complete email.
        
        Business intent: When the model detects partial email local parts (e.g., 'jean' from
        'jean.dupont@vd.ch'), search forward within a reasonable window (max 50 chars) to find
        '@' and capture the complete email address including full local part and domain.
        
        Improved forward search handles cases where '@' is not immediately adjacent to the
        detected entity, addressing tokenization splits in email local parts.
        """
        out: List[PIIEntity] = []
        
        for e in entities:
            if self._should_skip_email_expansion(e):
                out.append(e)
                continue

            expanded = self._try_expand_email(text, e)
            out.append(expanded)

        return out

    def _should_skip_email_expansion(self, entity: PIIEntity) -> bool:
        """Check if email expansion should be skipped for this entity."""
        return entity.pii_type != 'EMAIL' or '@' in entity.text

    def _try_expand_email(self, text: str, entity: PIIEntity) -> PIIEntity:
        """Try to expand a partial email entity to include full email address."""
        max_lookahead = 50
        search_end = min(entity.end + max_lookahead, len(text))
        at_pos = text.find('@', entity.end, search_end)
        
        if at_pos == -1:
            return entity
        
        expanded = self._build_expanded_email(text, entity, at_pos)
        return expanded if expanded else entity

    def _build_expanded_email(self, text: str, entity: PIIEntity, at_pos: int) -> Optional[PIIEntity]:
        """Build an expanded email entity by capturing local and domain parts."""
        local_start, _ = self._capture_email_local_part(text, entity, at_pos)
        domain_end = self._capture_email_domain(text, at_pos)
        
        if not self._is_valid_email_structure(text, local_start, at_pos, domain_end):
            return None
        
        complete_email = text[local_start:domain_end]
        return PIIEntity(
            text=complete_email,
            pii_type='EMAIL',
            type_label=self.label_mapping['EMAIL'],
            start=local_start,
            end=domain_end,
            score=entity.score
        )

    def _capture_email_local_part(self, text: str, entity: PIIEntity, at_pos: int) -> Tuple[int, int]:
        """Capture the complete local part of an email (before @)."""
        local_chars = set('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._+-')
        local_start = entity.start
        i = at_pos - 1
        
        while i >= 0 and text[i] in local_chars:
            local_start = i
            i -= 1
        
        return local_start, at_pos

    def _capture_email_domain(self, text: str, at_pos: int) -> int:
        """Capture the complete domain part of an email (after @)."""
        domain_chars = set('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-')
        trailing_to_strip = _TRAILING_PUNCTUATION
        
        j = at_pos + 1
        while j < len(text) and text[j] in domain_chars:
            j += 1
        
        while j > at_pos + 1 and text[j - 1] in trailing_to_strip:
            j -= 1
        
        return j

    def _is_valid_email_structure(self, text: str, local_start: int, at_pos: int, domain_end: int) -> bool:
        """Validate email structure has valid local and domain parts."""
        if domain_end <= at_pos + 1 or at_pos <= local_start:
            return False
        
        complete_email = text[local_start:domain_end]
        domain_part = text[at_pos + 1:domain_end]
        
        return complete_email.count('@') == 1 and '.' in domain_part

    def _split_zipcode_and_city(self, text: str, entities: List[PIIEntity]) -> List[PIIEntity]:
        """Split a ZIPCODE span that also contains a city name into ZIPCODE + CITY.
        International heuristic (no assumption on ZIP length/format):
        - If a comma is present (e.g., "H4A 2K7, Montreal" or "69007, Lyon"), split at the first comma.
        - Otherwise, split at the first transition of separator(s) to a capitalized word
          (e.g., "SW1W 0NY London", "69007 Lyon").
        This handles alphanumeric ZIPs with spaces or dashes (UK, CA, NL, CZ, SK, BR, etc.).
        """
        out: List[PIIEntity] = []

        for e in entities:
            if e.pii_type != 'ZIPCODE':
                out.append(e)
                continue

            split_entities = self._try_split_zipcode(text, e)
            out.extend(split_entities)

        return out

    def _try_split_zipcode(self, text: str, entity: PIIEntity) -> List[PIIEntity]:
        """Try to split a ZIPCODE entity into ZIPCODE and CITY components."""
        span_full = text[entity.start:entity.end]
        span = span_full.strip()
        
        if not span:
            return [entity]

        # Try comma-based split first
        split_result = self._try_comma_split(span_full, span, entity)
        if split_result:
            return split_result

        # Try separator-based split
        split_result = self._try_separator_split(span_full, span, entity)
        if split_result:
            return split_result

        return [entity]

    def _try_comma_split(self, span_full: str, span: str, entity: PIIEntity) -> Optional[List[PIIEntity]]:
        """Try to split ZIPCODE and CITY at comma."""
        comma_idx = span.find(',')
        if comma_idx == -1:
            return None

        left, right = self._extract_comma_parts(span, comma_idx)
        
        if not self._is_valid_zipcode_city_pair(left, right):
            return None

        return self._create_zipcode_city_entities(span_full, left, right, entity)

    def _extract_comma_parts(self, span: str, comma_idx: int) -> Tuple[str, str]:
        """Extract left and right parts around comma."""
        left = span[:comma_idx].strip()
        right = span[comma_idx + 1:].strip().strip(''.join(_TRAILING_PUNCTUATION))
        return left, right

    def _try_separator_split(self, span_full: str, span: str, entity: PIIEntity) -> Optional[List[PIIEntity]]:
        """Try to split ZIPCODE and CITY at separator transition."""
        left, right = self._find_separator_split_point(span)
        
        if not self._is_valid_zipcode_city_pair(left, right):
            return None

        return self._create_zipcode_city_entities(span_full, left, right, entity)

    def _find_separator_split_point(self, span: str) -> Tuple[str, str]:
        """Find the split point between zipcode and city using separators."""
        n = len(span)
        i = 0
        
        # Advance through zipcode part (alnum, space, dash)
        while i < n and (span[i].isalnum() or span[i] in {' ', '-'}):
            i += 1
        
        # Skip separators
        j = i
        while j < n and span[j] in _SEPARATOR_CHARS:
            j += 1
        
        left = span[:i].rstrip(''.join(_SEPARATOR_CHARS))
        right = span[j:].strip().strip(''.join(_TRAILING_PUNCTUATION))
        
        return left, right

    def _is_valid_zipcode_city_pair(self, zipcode: str, city: str) -> bool:
        """Check if zipcode and city parts are valid."""
        if not city:
            return False
        
        return self._count_alnum(zipcode) >= 2 and self._looks_like_city(city)

    def _count_alnum(self, s: str) -> int:
        """Count alphanumeric characters in string."""
        return sum(1 for ch in s if ch.isalnum())

    def _looks_like_city(self, s: str) -> bool:
        """Check if string looks like a city name."""
        s2 = s.strip().strip(".,;:")
        return len(s2) >= 2 and s2[0].isalpha() and s2[0].isupper()

    def _create_zipcode_city_entities(
        self, span_full: str, zipcode: str, city: str, original: PIIEntity
    ) -> List[PIIEntity]:
        """Create separate ZIPCODE and CITY entities from split parts."""
        entities = []
        
        # ZIPCODE entity
        zip_start_in_full = span_full.find(zipcode)
        entities.append(PIIEntity(
            text=zipcode,
            pii_type='ZIPCODE',
            type_label=self.label_mapping['ZIPCODE'],
            start=original.start + zip_start_in_full,
            end=original.start + zip_start_in_full + len(zipcode),
            score=original.score
        ))
        
        # CITY entity
        city_start_in_full = span_full.find(city)
        entities.append(PIIEntity(
            text=city,
            pii_type='CITY',
            type_label=self.label_mapping['CITY'],
            start=original.start + city_start_in_full,
            end=original.start + city_start_in_full + len(city),
            score=original.score
        ))
        
        return entities

    def _merge_adjacent_entities(self, text: str, entities: List[PIIEntity]) -> List[PIIEntity]:
        """Merge adjacent entities of the same type into single entities.
        
        Business rules:
        - Merge two entities if they have the same pii_type and are adjacent
          (prev.end == next.start) or separated by a single allowed separator.
        - Allowed separators: apostrophe ('), right single quotation mark ('), hyphen (-).
        - The merged entity text is extracted from the source text to preserve diacritics.
        - The score is the maximum of the merged entities (prudent confidence measure).
        - This handles tokenizer splitting issues with diacritics and compound names
          (e.g., "Rodrigue", "Benoît", "Jean-Paul", "O'Connor").
        
        Args:
            text: The original text being analyzed
            entities: List of detected entities
            
        Returns:
            List of entities with adjacent same-type entities merged
        """
        if not entities:
            return []

        # Sort by position to ensure linear traversal
        sorted_entities = sorted(entities, key=lambda e: (e.start, e.end))
        merged: List[PIIEntity] = []

        for current in sorted_entities:
            if not merged:
                merged.append(current)
                continue

            last = merged[-1]
            if self._can_merge_entities(text, last, current):
                merged[-1] = self._create_merged_entity(text, last, current)
            else:
                merged.append(current)

        return merged

    def _can_merge_entities(self, text: str, prev: PIIEntity, current: PIIEntity) -> bool:
        """Check if two entities can be merged based on business rules.
        
        Args:
            text: The original text
            prev: The previous entity
            current: The current entity
            
        Returns:
            True if entities should be merged, False otherwise
        """
        if prev.pii_type != current.pii_type:
            return False

        # Strictly adjacent (no gap)
        if prev.end == current.start:
            return True

        # Allow single-character separator
        if current.start - prev.end == 1:
            separator = text[prev.end:current.start]
            return separator in {"'", "-"}

        return False

    def _create_merged_entity(self, text: str, prev: PIIEntity, current: PIIEntity) -> PIIEntity:
        """Create a new entity from merging two adjacent entities.
        
        Args:
            text: The original text
            prev: The previous entity
            current: The current entity
            
        Returns:
            A new PIIEntity representing the merged span
        """
        new_start = prev.start
        new_end = current.end
        new_text = text[new_start:new_end]
        new_score = max(prev.score, current.score)

        return PIIEntity(
            text=new_text,
            pii_type=prev.pii_type,
            type_label=prev.type_label,
            start=new_start,
            end=new_end,
            score=new_score
        )

    def _generate_detection_id(self) -> str:
        """Generate a unique detection ID for logging."""
        return f"det_{int(time.time() * 1000) % 10000}"

    def __del__(self):
        """Cleanup when the detector is destroyed."""
        try:
            if hasattr(self, 'model') and self.model is not None:
                del self.model
            if hasattr(self, 'tokenizer') and self.tokenizer is not None:
                del self.tokenizer
            if hasattr(self, 'pipeline') and self.pipeline is not None:
                del self.pipeline

            if hasattr(self, 'memory_manager') and hasattr(self, 'device'):
                self.memory_manager.clear_cache(self.device)

        except Exception as e:
            if hasattr(self, 'logger'):
                self.logger.error(f"Error during cleanup: {str(e)}")


# Configure logging at module level
def setup_logging(level: int = logging.INFO) -> None:
    """Setup logging configuration for the module."""
    logging.basicConfig(
        level=level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )


# Initialize logging
setup_logging()
