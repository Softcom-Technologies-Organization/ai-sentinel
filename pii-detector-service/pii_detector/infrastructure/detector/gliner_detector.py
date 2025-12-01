"""
GLiNER-based PII detector with PIIDetector-compatible interface.

This module provides GLiNERDetector class that implements the same interface
as PIIDetector but uses GLiNER model for entity detection.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import ModelNotLoadedError
from pii_detector.infrastructure.model_management.gliner_model_manager import \
  GLiNERModelManager
# FIXME: from service.detector.models import
from pii_detector.infrastructure.text_processing.semantic_chunker import \
  create_chunker
from typing import Dict, List, Optional, Tuple, Any


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
        
        # Initialize logger FIRST (needed by other init methods)
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        
        self.model_manager = GLiNERModelManager(self.config)
        self.model: Optional[Any] = None
        self.pii_type_mapping = self._load_pii_type_mapping()
        self.scoring_overrides = self._load_scoring_overrides()
        self.semantic_chunker: Optional[Any] = None  # Initialized after model load
        
        # Load throughput logging flag from config
        self.log_throughput = self._load_log_throughput_config()
        
        # Load parallel processing configuration
        self.parallel_enabled, self.max_workers = self._load_parallel_config()
        
        self.logger.info(f"GLiNER Detector initialized with device: {self.device}")
        if self.parallel_enabled:
            self.logger.info(f"Parallel chunk processing enabled with {self.max_workers} workers")

    @property
    def model_id(self) -> str:
        """Get model ID for backward compatibility."""
        return self.config.model_id

    def download_model(self) -> None:
        """Download the GLiNER model files from Hugging Face."""
        self.model_manager.download_model()

    def load_model(self) -> None:
        """Load the GLiNER model and initialize semantic chunker."""
        try:
            self.model = self.model_manager.load_model()
            self.logger.info("GLiNER model loaded successfully")
            
            # Initialize semantic chunker with GLiNER's tokenizer
            # GLiNER has internal 768-token sentence limit, so we chunk at 768 tokens
            # CRITICAL: semantic chunking is REQUIRED to prevent truncation warnings
            self._initialize_semantic_chunker()
                
        except Exception as e:
            self.logger.error(f"Failed to load GLiNER model: {str(e)}")
            raise

    def _get_tokenizer_from_model(self) -> Any:
        """
        Extract tokenizer from GLiNER model with AutoTokenizer fallback.
        
        Returns:
            Tokenizer object (either from model or AutoTokenizer)
        """
        tokenizer = getattr(self.model.data_processor.config, 'tokenizer', None)
        if tokenizer is None:
            # Fallback: try to get from model name
            from transformers import AutoTokenizer
            model_name = getattr(self.model.config, 'model_name', 'bert-base-cased')
            tokenizer = AutoTokenizer.from_pretrained(model_name)
        return tokenizer

    def _verify_semantic_chunker(self) -> None:
        """
        Verify that semantic chunker uses semchunk library.
        
        Raises:
            RuntimeError: If chunker is not using semchunk library
        """
        chunk_info = self.semantic_chunker.get_chunk_info()
        if chunk_info.get("library") != "semchunk":
            raise RuntimeError(
                "Semantic chunking REQUIRED but fallback chunker was created. "
                "Install semchunk: pip install semchunk"
            )

    def _initialize_semantic_chunker(self) -> None:
        """
        Initialize semantic chunker with GLiNER's tokenizer.
        
        Raises:
            RuntimeError: If semantic chunker initialization fails
        """
        try:
            tokenizer = self._get_tokenizer_from_model()
            
            self.semantic_chunker = create_chunker(
                tokenizer=tokenizer,
                chunk_size=768,  # GLiNER's hard limit per sentence
                overlap=100,     # Overlap to catch entities at boundaries
                use_semantic=True,
                logger=self.logger
            )
            
            self._verify_semantic_chunker()
            self.logger.info("Semantic chunker initialized successfully with semchunk")
            
        except Exception as e:
            self.logger.error(f"CRITICAL: Failed to initialize semantic chunker: {e}")
            raise RuntimeError(
                "Semantic chunking is REQUIRED for GLiNER to prevent truncation. "
                f"Error: {str(e)}"
            ) from e

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
        Load PII type mapping from database (detector_label → pii_type).
        
        Fetches enabled GLINER PII type configurations from database and builds
        a reverse mapping from detector labels to normalized PII types.
        
        Returns:
            Dictionary mapping detector labels to PII types.
            Falls back to hardcoded defaults if database is unavailable.
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import get_database_config_adapter
            
            adapter = get_database_config_adapter()
            pii_type_configs = adapter.fetch_pii_type_configs(detector='GLINER')
            
            if not pii_type_configs:
                self.logger.warning("No PII type configs found in database for GLINER, using defaults")
                return self._get_default_mapping()
            
            # Build reverse mapping: detector_label → pii_type
            mapping = {}
            for pii_type, config in pii_type_configs.items():
                detector_label = config.get('detector_label')
                if detector_label and config.get('enabled', False):
                    mapping[detector_label] = pii_type
            
            if not mapping:
                self.logger.warning("No enabled PII types with detector labels in database, using defaults")
                return self._get_default_mapping()
            
            return mapping
            
        except Exception as e:
            self.logger.warning(f"Failed to load mapping from database: {e}, using defaults")
            return self._get_default_mapping()

    def _get_default_mapping(self) -> Dict[str, str]:
        """
        Get default PII type mapping as fallback.
        
        This fallback mapping is used when database is unavailable.
        In production, mappings should be managed via database (pii_type_config table).
        
        Returns:
            Default mapping from detector labels to PII types (27 types)
        """
        return {
            # Original 17 types
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
            "zip code": "ZIPCODE",
            # Additional 10 types
            "person name": "PERSONNAME",
            "full name": "FULLNAME",
            "URL": "URL",
            "IBAN": "IBAN",
            "IP address": "IPADDRESS",
            "MAC address": "MACADDRESS",
            "crypto wallet": "CRYPTOWALLET",
            "date": "DATE",
            "vehicle registration": "VEHICLEREG",
            "voter ID": "VOTERID"
        }

    def _load_log_throughput_config(self) -> bool:
        """
        Load log_throughput flag from configuration.
        
        Returns:
            True if throughput logging is enabled, False otherwise
        """
        from pii_detector.application.config.detection_policy import _load_llm_config
        
        try:
            config = _load_llm_config()
            detection_config = config.get("detection", {})
            return detection_config.get("log_throughput", True)  # Default: enabled
        except Exception as e:
            self.logger.debug(f"Failed to load log_throughput config: {e}, defaulting to True")
            return True

    def _load_scoring_overrides(self) -> Dict[str, float]:
        """
        Load per-entity-type scoring thresholds from database.
        
        Scoring thresholds are now managed in the database (pii_type_config table)
        for runtime configurability. This method fetches enabled PII type configs
        for the GLINER detector and builds a threshold map.
        
        Returns:
            Dictionary mapping PII types to minimum confidence thresholds.
            Empty dict if database is unavailable or no configs found.
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import get_database_config_adapter
            
            adapter = get_database_config_adapter()
            pii_type_configs = adapter.fetch_pii_type_configs(detector='GLINER')
            
            if not pii_type_configs:
                self.logger.info("No PII type configs found in database for GLINER")
                return {}
            
            # Transform DB format {pii_type: {enabled, threshold, ...}} 
            # to scoring_overrides format {pii_type: threshold}
            scoring = {}
            for pii_type, config in pii_type_configs.items():
                if config.get('enabled', False):
                    scoring[pii_type] = config['threshold']
            
            if scoring:
                self.logger.info(f"Loaded {len(scoring)} scoring overrides from database")
            else:
                self.logger.info("No enabled PII types found in database for GLINER")
            
            return scoring
            
        except Exception as e:
            self.logger.warning(f"Failed to load scoring overrides from database: {e}")
            return {}

    def _load_parallel_config(self) -> Tuple[bool, int]:
        """
        Load parallel processing configuration from detection settings.
        
        Returns:
            Tuple of (parallel_enabled, max_workers)
        """
        from pii_detector.application.config.detection_policy import _load_llm_config
        
        try:
            config = _load_llm_config()
            parallel_config = config.get("parallel_processing", {})
            
            enabled = parallel_config.get("enabled", True)  # Default: enabled
            max_workers = parallel_config.get("max_workers", 10)  # Default: 10 workers
            
            return enabled, max_workers
            
        except Exception as e:
            self.logger.debug(f"Failed to load parallel config: {e}, using defaults (enabled=True, workers=10)")
            return True, 10

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
            # Tag provenance for downstream logging (e.g. gRPC async PII logs)
            pii_entity.source = "GLINER"
            entities.append(pii_entity)
        
        return entities

    def _apply_entity_scoring_filter(self, entities: List[PIIEntity]) -> List[PIIEntity]:
        """
        Apply per-entity-type threshold filtering (post-filter).
        
        Similar to Presidio's _convert_and_filter_results, this method applies
        entity-specific thresholds from the [scoring] configuration section.
        Entities below their type-specific threshold are discarded.
        
        Args:
            entities: List of detected entities
            
        Returns:
            Filtered list of entities that pass their type-specific thresholds
        """
        if not self.scoring_overrides:
            return entities
        
        filtered_entities = []
        filtered_count = 0
        
        for entity in entities:
            # Get configured threshold for this entity type
            entity_threshold = self.scoring_overrides.get(entity.pii_type)

            # Post-filter: discard if below entity-specific threshold
            if entity_threshold is not None and entity.score < entity_threshold:
                filtered_count += 1
                self.logger.debug(
                    "Filtered out %s (score=%.3f < threshold=%.3f) text='%s' at position %s-%s",
                    entity.pii_type,
                    entity.score,
                    entity_threshold,
                    entity.text,
                    entity.start,
                    entity.end,
                )
                continue

            filtered_entities.append(entity)

        if filtered_count > 0:
            # Aggregate log kept at DEBUG level to avoid per-request noise in
            # production while still being available for diagnostics.
            self.logger.debug(
                "Post-filtered %s entities based on per-type thresholds (%s/%s remaining)",
                filtered_count,
                len(filtered_entities),
                len(entities),
            )
        
        return filtered_entities

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

    def _process_single_chunk(
        self, 
        chunk_idx: int, 
        chunk_result: Any, 
        labels: List[str], 
        threshold: float,
        detection_id: str
    ) -> Tuple[int, List[PIIEntity]]:
        """
        Process a single chunk of text for PII detection.
        
        This method is designed to be called by ThreadPoolExecutor for parallel processing.
        
        Args:
            chunk_idx: Index of the chunk being processed
            chunk_result: ChunkResult object containing chunk text and position
            labels: List of GLiNER labels to detect
            threshold: Detection confidence threshold
            detection_id: Detection ID for logging
            
        Returns:
            Tuple of (chunk_index, list of detected PIIEntity objects with adjusted positions)
        """
        self.logger.debug(
            f"[{detection_id}] Processing chunk {chunk_idx + 1} in parallel: "
            f"{len(chunk_result.text)} chars"
        )
        
        # Process single chunk with GLiNER
        raw_entities = self.model.predict_entities(
            chunk_result.text,
            labels,
            threshold=threshold
        )
        
        # Convert raw entities to PIIEntity objects
        chunk_entities = self._convert_to_pii_entities(raw_entities)
        
        # Adjust entity positions relative to original text
        adjusted_entities = []
        for entity in chunk_entities:
            adjusted = PIIEntity(
                text=entity.text,
                pii_type=entity.pii_type,
                type_label=entity.type_label,
                start=entity.start + chunk_result.start,
                end=entity.end + chunk_result.start,
                score=entity.score
            )
            adjusted_entities.append(adjusted)
        
        return chunk_idx, adjusted_entities

    def _process_chunks_parallel(
        self,
        chunk_results: List[Any],
        labels: List[str],
        threshold: float,
        detection_id: str
    ) -> List[PIIEntity]:
        """
        Process chunks in parallel using ThreadPoolExecutor.
        
        Args:
            chunk_results: List of chunk results to process
            labels: GLiNER labels for detection
            threshold: Detection confidence threshold
            detection_id: Detection ID for logging
            
        Returns:
            List of detected PIIEntity objects with duplicates removed
        """
        self.logger.info(
            f"[{detection_id}] Using parallel processing with {self.max_workers} workers"
        )
        
        seen_entities: set = set()
        all_entities: List[PIIEntity] = []
        
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            # Submit all chunks for parallel processing
            future_to_chunk = {
                executor.submit(
                    self._process_single_chunk,
                    chunk_idx,
                    chunk_result,
                    labels,
                    threshold,
                    detection_id
                ): (chunk_idx, chunk_result)
                for chunk_idx, chunk_result in enumerate(chunk_results)
            }
            
            # Collect results as they complete
            for future in as_completed(future_to_chunk):
                try:
                    chunk_idx, chunk_entities = future.result()
                    
                    # Add entities to all_entities, avoiding duplicates
                    for entity in chunk_entities:
                        entity_key = (entity.start, entity.end, entity.pii_type)
                        
                        if entity_key not in seen_entities:
                            seen_entities.add(entity_key)
                            all_entities.append(entity)
                    
                except Exception as e:
                    chunk_idx, _ = future_to_chunk[future]
                    self.logger.error(
                        f"[{detection_id}] Error processing chunk {chunk_idx + 1}: {str(e)}"
                    )
                    raise
        
        return all_entities

    def _process_chunks_sequential(
        self,
        chunk_results: List[Any],
        labels: List[str],
        threshold: float,
        detection_id: str
    ) -> List[PIIEntity]:
        """
        Process chunks sequentially in a for loop.
        
        Args:
            chunk_results: List of chunk results to process
            labels: GLiNER labels for detection
            threshold: Detection confidence threshold
            detection_id: Detection ID for logging
            
        Returns:
            List of detected PIIEntity objects with duplicates removed
        """
        if not self.parallel_enabled:
            self.logger.info(f"[{detection_id}] Parallel processing disabled, using sequential mode")
        else:
            self.logger.info(f"[{detection_id}] Single chunk detected, using sequential mode")
        
        seen_entities: set = set()
        all_entities: List[PIIEntity] = []
        
        for chunk_idx, chunk_result in enumerate(chunk_results):
            self.logger.debug(
                f"[{detection_id}] Processing chunk {chunk_idx + 1}/{len(chunk_results)}: "
                f"{len(chunk_result.text)} chars"
            )
            
            # Process single chunk with GLiNER
            raw_entities = self.model.predict_entities(
                chunk_result.text,
                labels,
                threshold=threshold
            )
            
            # Convert raw entities to PIIEntity objects
            chunk_entities = self._convert_to_pii_entities(raw_entities)
            
            # Adjust entity positions relative to original text and avoid duplicates
            for entity in chunk_entities:
                adjusted_start = entity.start + chunk_result.start
                adjusted_end = entity.end + chunk_result.start
                
                entity_key = (adjusted_start, adjusted_end, entity.pii_type)
                
                if entity_key not in seen_entities:
                    seen_entities.add(entity_key)
                    adjusted = PIIEntity(
                        text=entity.text,
                        pii_type=entity.pii_type,
                        type_label=entity.type_label,
                        start=adjusted_start,
                        end=adjusted_end,
                        score=entity.score
                    )
                    all_entities.append(adjusted)
        
        return all_entities

    def _log_detection_results(
        self,
        detection_id: str,
        processing_mode: str,
        detection_time: float,
        chunk_count: int,
        entity_count: int,
        text_length: int
    ) -> None:
        """
        Log detection results with optional throughput calculation.
        
        Args:
            detection_id: Detection ID for logging
            processing_mode: Processing mode used ("parallel" or "sequential")
            detection_time: Time taken for detection
            chunk_count: Number of chunks processed
            entity_count: Number of entities found (after filtering)
            text_length: Length of analyzed text in characters
        """
        if self.log_throughput:
            throughput = text_length / detection_time if detection_time > 0 else 0
            # Throughput logging is useful for benchmarking but expensive in
            # production at high volume. Downgrade to DEBUG so it is only
            # enabled when explicitly requested.
            self.logger.debug(
                "[%s] %s processing completed in %.3fs, processed %s chunks, "
                "found %s entities (after post-filter), throughput: %.0f chars/s",
                detection_id,
                processing_mode.capitalize(),
                detection_time,
                chunk_count,
                entity_count,
                throughput,
            )
        else:
            # Keep a compact INFO log without throughput details disabled by
            # default to reduce noise.
            self.logger.info(
                "[%s] %s processing completed in %.3fs, processed %s chunks, found %s entities (after post-filter)",
                detection_id,
                processing_mode.capitalize(),
                detection_time,
                chunk_count,
                entity_count,
            )

    def _detect_pii_with_chunking(self, text: str, threshold: float, detection_id: str) -> List[PIIEntity]:
        """
        Detect PII using semantic chunking with parallel or sequential processing.
        
        Uses semantic chunking to prevent GLiNER's 768-token sentence truncation.
        Processes multiple chunks in parallel using ThreadPoolExecutor for improved performance
        on large documents.
        
        Args:
            text: Text to analyze
            threshold: Confidence threshold
            detection_id: Detection ID for logging
            
        Returns:
            List of detected PII entities with duplicates removed
        """
        start_time = time.time()
        
        if not self.semantic_chunker:
            raise RuntimeError("Semantic chunker not initialized. Call load_model() first.")
        
        # Get semantic chunks
        chunk_results = self.semantic_chunker.chunk_text(text)
        
        self.logger.info(
            f"[{detection_id}] Semantic chunking: {len(text)} chars → {len(chunk_results)} chunks"
        )
        
        # Pre-compute labels once for all chunks
        labels = self._get_gliner_labels()
        
        # Choose processing strategy based on configuration
        if self.parallel_enabled and len(chunk_results) > 1:
            all_entities = self._process_chunks_parallel(chunk_results, labels, threshold, detection_id)
            processing_mode = "parallel"
        else:
            all_entities = self._process_chunks_sequential(chunk_results, labels, threshold, detection_id)
            processing_mode = "sequential"
        
        detection_time = time.time() - start_time
        
        # Apply per-entity-type threshold filtering (post-filter)
        filtered_entities = self._apply_entity_scoring_filter(all_entities)
        
        # Log detection results
        self._log_detection_results(
            detection_id=detection_id,
            processing_mode=processing_mode,
            detection_time=detection_time,
            chunk_count=len(chunk_results),
            entity_count=len(filtered_entities),
            text_length=len(text)
        )
        
        return filtered_entities

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
