"""
GLiNER-based PII detector with PIIDetector-compatible interface.

This module provides GLiNERDetector class that implements the same interface
as PIIDetector but uses GLiNER model for entity detection.
"""

import logging
import time
from typing import Dict, List, Optional, Tuple, Any

from .models import PIIEntity, DetectionConfig, ModelNotLoadedError, PIIDetectionError
from .gliner_model_manager import GLiNERModelManager


class GLiNERDetector:
    """
    GLiNER-based PII detector compatible with PIIDetector interface.
    
    This detector uses GLiNER (Generalist and Lightweight model for Named Entity Recognition)
    for PII detection with natural language labels.
    """

    def __init__(self, config: Optional[DetectionConfig] = None):
        """
        Initialize the GLiNER PII detector.
        
        Args:
            config: Detection configuration. Uses default if None.
        """
        self.config = config or DetectionConfig()
        self.device = self.config.device or 'cpu'
        
        self.model_manager = GLiNERModelManager(self.config)
        self.model: Optional[Any] = None
        self.pii_type_mapping = self._load_pii_type_mapping()
        
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self.logger.info(f"GLiNER Detector initialized with device: {self.device}")

    @property
    def model_id(self) -> str:
        """Get model ID for backward compatibility."""
        return self.config.model_id

    def download_model(self) -> None:
        """Download the GLiNER model files from Hugging Face."""
        self.model_manager.download_model()

    def load_model(self) -> None:
        """Load the GLiNER model."""
        try:
            self.model = self.model_manager.load_model()
            self.logger.info("GLiNER model loaded successfully")
        except Exception as e:
            self.logger.error(f"Failed to load GLiNER model: {str(e)}")
            raise

    def detect_pii(self, text: str, threshold: Optional[float] = None) -> List[PIIEntity]:
        """
        Detect PII in text content using GLiNER with token-based chunking.
        
        Args:
            text: Text to analyze for PII
            threshold: Confidence threshold for detection
            
        Returns:
            List of detected PII entities
            
        Raises:
            ModelNotLoadedError: If model is not loaded
            PIIDetectionError: If detection fails
        """
        if not self.model:
            raise ModelNotLoadedError("The GLiNER model must be loaded before use")
        
        threshold = threshold or self.config.threshold
        detection_id = self._generate_detection_id()
        
        self.logger.info(f"[{detection_id}] Starting GLiNER PII detection for {len(text)} characters")
        
        try:
            # ALWAYS use chunking for GLiNER to prevent internal truncation warnings
            # GLiNER truncates individual sentences at 768 tokens, regardless of total text size
            # Even a 6000 char text can have long sentences (code, lists, tables) that get truncated
            # Chunking ensures all content is analyzed without loss
            entities = self._detect_pii_with_chunking(text, threshold, detection_id)
            
            return entities
            
        except Exception as e:
            self.logger.error(f"[{detection_id}] Detection failed: {str(e)}")
            raise PIIDetectionError(f"GLiNER PII detection failed: {str(e)}") from e

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

    def _load_pii_type_mapping(self) -> Dict[str, str]:
        """
        Load PII type mapping from configuration.
        
        Returns:
            Dictionary mapping GLiNER labels to PII types
        """
        from .models.detection_config import _load_llm_config
        
        try:
            config = _load_llm_config()
            models_config = config.get("models", {})
            gliner_config = models_config.get("gliner-pii", {})
            mapping = gliner_config.get("pii_type_mapping", {})
            
            if not mapping:
                self.logger.warning("No PII type mapping found in config, using defaults")
                return self._get_default_mapping()
            
            return mapping
            
        except Exception as e:
            self.logger.warning(f"Failed to load mapping from config: {e}, using defaults")
            return self._get_default_mapping()

    def _get_default_mapping(self) -> Dict[str, str]:
        """
        Get default PII type mapping.
        
        Returns:
            Default mapping from GLiNER labels to PII types
        """
        return {
            "account number": "ACCOUNTNUM",
            "building number": "BUILDINGNUM",
            "city": "CITY",
            "credit card number": "CREDITCARDNUMBER",
            "date of birth": "DATEOFBIRTH",
            "driver license number": "DRIVERLICENSENUM",
            "email": "EMAIL",
            "first name": "GIVENNAME",
            "ID card number": "IDCARDNUM",
            "password": "PASSWORD",
            "social security number": "SOCIALNUM",
            "street": "STREET",
            "last name": "SURNAME",
            "tax number": "TAXNUM",
            "phone number": "TELEPHONENUM",
            "username": "USERNAME",
            "zip code": "ZIPCODE"
        }

    def _get_gliner_labels(self) -> List[str]:
        """
        Get GLiNER labels from PII type mapping.
        
        Returns:
            List of GLiNER labels (natural language format)
        """
        return list(self.pii_type_mapping.keys())

    def _convert_to_pii_entities(self, raw_entities: List[Dict]) -> List[PIIEntity]:
        """
        Convert GLiNER entities to PIIEntity format.
        
        Args:
            raw_entities: Raw entities from GLiNER
            
        Returns:
            List of PIIEntity objects
        """
        entities = []
        
        for entity in raw_entities:
            gliner_label = entity.get("label", "")
            pii_type = self.pii_type_mapping.get(gliner_label, gliner_label.upper())
            
            pii_entity = PIIEntity(
                text=entity.get("text", ""),
                pii_type=pii_type,
                type_label=pii_type,
                start=entity.get("start", 0),
                end=entity.get("end", 0),
                score=entity.get("score", 0.0)
            )
            entities.append(pii_entity)
        
        return entities

    def _apply_masks(self, text: str, entities: List[PIIEntity]) -> str:
        """
        Apply masks to detected PII entities.
        
        Args:
            text: Original text
            entities: List of detected entities
            
        Returns:
            Masked text with PII replaced by type labels
        """
        entities_sorted = sorted(entities, key=lambda x: x.start, reverse=True)
        masked_text = text
        
        for entity in entities_sorted:
            mask = f"[{entity.pii_type}]"
            masked_text = masked_text[:entity.start] + mask + masked_text[entity.end:]
        
        return masked_text

    def _detect_pii_with_chunking(self, text: str, threshold: float, detection_id: str) -> List[PIIEntity]:
        """
        Detect PII in large texts using character-based chunking with overlap.
        
        This method splits text into overlapping chunks to avoid GLiNER's internal
        truncation warning (768 tokens). Uses character-based chunking since GLiNER
        works with raw text, not tokenized input.
        
        Business rule: Use overlap (stride_tokens from config) to ensure entities
        at chunk boundaries appear in at least one complete chunk.
        
        Args:
            text: Text to analyze
            threshold: Confidence threshold
            detection_id: Detection ID for logging
            
        Returns:
            List of detected PII entities with duplicates removed
        """
        start_time = time.time()
        
        # Convert token limits to approximate character limits
        # Rough estimate: 1 token ≈ 4 characters for European languages
        chars_per_token = 4
        chunk_chars = self.config.max_length * chars_per_token  # ~2880 chars for 720 tokens
        overlap_chars = self.config.stride_tokens * chars_per_token  # ~400 chars for 100 tokens
        
        labels = self._get_gliner_labels()
        all_entities: List[PIIEntity] = []
        offset = 0
        chunk_count = 0
        
        self.logger.info(
            f"[{detection_id}] Chunking text: {len(text)} chars, "
            f"chunk_size={chunk_chars}, overlap={overlap_chars}"
        )
        
        while offset < len(text):
            # Extract chunk with overlap
            chunk_start = max(0, offset - overlap_chars if offset > 0 else 0)
            chunk_end = min(len(text), offset + chunk_chars)
            chunk = text[chunk_start:chunk_end]
            
            # Detect PII in chunk
            chunk_count += 1
            self.logger.debug(f"[{detection_id}] Processing chunk {chunk_count}: {len(chunk)} chars")
            
            raw_entities = self.model.predict_entities(chunk, labels, threshold=threshold)
            chunk_entities = self._convert_to_pii_entities(raw_entities)
            
            # Adjust entity positions and avoid duplicates
            for entity in chunk_entities:
                # Only include entities that start in the current chunk (not in overlap)
                start_in_chunk = entity.start
                if start_in_chunk >= (overlap_chars if offset > 0 else 0):
                    adjusted = PIIEntity(
                        text=entity.text,
                        pii_type=entity.pii_type,
                        type_label=entity.type_label,
                        start=entity.start + chunk_start,
                        end=entity.end + chunk_start,
                        score=entity.score
                    )
                    
                    if not self._is_duplicate_entity(adjusted, all_entities):
                        all_entities.append(adjusted)
            
            offset += chunk_chars
        
        detection_time = time.time() - start_time
        self.logger.info(
            f"[{detection_id}] Chunked detection completed in {detection_time:.3f}s, "
            f"processed {chunk_count} chunks, found {len(all_entities)} entities"
        )
        
        return all_entities

    def _is_duplicate_entity(self, entity: PIIEntity, existing_entities: List[PIIEntity]) -> bool:
        """
        Check if an entity with the same span and type already exists.
        
        Args:
            entity: Entity to check
            existing_entities: List of existing entities
            
        Returns:
            True if duplicate found, False otherwise
        """
        return any(
            (e.start == entity.start) and (e.end == entity.end) and (e.pii_type == entity.pii_type)
            for e in existing_entities
        )

    def _generate_detection_id(self) -> str:
        """Generate a unique detection ID for logging."""
        return f"gliner_{int(time.time() * 1000) % 10000}"

    def __del__(self):
        """Cleanup when the detector is destroyed."""
        try:
            if hasattr(self, 'model') and self.model is not None:
                del self.model
        except Exception as e:
            if hasattr(self, 'logger'):
                self.logger.error(f"Error during cleanup: {str(e)}")
