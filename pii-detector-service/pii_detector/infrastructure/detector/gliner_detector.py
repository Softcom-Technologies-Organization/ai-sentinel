"""
GLiNER-based PII detector with PIIDetector-compatible interface.

This module provides GLiNERDetector class that implements the same interface
as PIIDetector but uses GLiNER model for entity detection.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, List, Optional, Tuple, Any

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import ModelNotLoadedError, PIIDetectionError
from pii_detector.infrastructure.model_management.gliner_model_manager import \
  GLiNERModelManager
# FIXME: from service.detector.models import
from pii_detector.infrastructure.text_processing.semantic_chunker import \
  create_chunker


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
            
            # Initialize text chunker with GLiNER's tokenizer
            # GLiNER (nvidia/gliner-pii) has internal 378-token limit
            # Character-based chunking with overlap handles long texts
            self._initialize_text_chunker()
                
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
        Verify that chunker is properly initialized.

        Logs chunker configuration for debugging.
        """
        chunk_info = self.semantic_chunker.get_chunk_info()
        self.logger.info(f"Chunker initialized: {chunk_info}")

    def _initialize_text_chunker(self) -> None:
        """
        Initialize text chunker for GLiNER processing.

        Uses character-based chunking with overlap to handle GLiNER's 378 token limit.
        """
        try:
            tokenizer = self._get_tokenizer_from_model()

            self.semantic_chunker = create_chunker(
                tokenizer=tokenizer,
                chunk_size=378,  # GLiNER's internal token limit (nvidia/gliner-pii)
                overlap=100,     # ~300 char overlap to catch entities at boundaries
                use_semantic=False,  # Character-based chunking supports overlap
                logger=self.logger
            )

            self._verify_semantic_chunker()
            self.logger.info("Text chunker initialized successfully")

        except Exception as e:
            self.logger.error(f"CRITICAL: Failed to initialize text chunker: {e}")
            raise RuntimeError(f"Text chunker initialization failed: {str(e)}") from e

    def detect_pii(self, text: str, threshold: Optional[float] = None, pii_type_configs: Optional[Dict] = None) -> List[PIIEntity]:
        """
        Detect PII in text content using GLiNER with token-based chunking.
        
        Always uses fresh configs from database - no caching to avoid stale configuration.
        
        Args:
            text: Text to analyze for PII
            threshold: Confidence threshold for detection
            pii_type_configs: Optional fresh PII type configs from database (if already fetched)
            
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
            # Fetch fresh configs if not provided
            if pii_type_configs is None:
                self.logger.info(f"[{detection_id}] Fetching fresh configuration from database")
                pii_type_configs = self._load_pii_type_configs_from_database()
            else:
                self.logger.info(f"[{detection_id}] Using provided fresh configuration")
            
            # ALWAYS use chunking for GLiNER to prevent internal truncation warnings
            # GLiNER truncates individual sentences at 768 tokens, regardless of total text size
            # Even a 6000 char text can have long sentences (code, lists, tables) that get truncated
            # Chunking ensures all content is analyzed without loss
            entities = self._detect_pii_with_chunking(text, threshold, detection_id, pii_type_configs)

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

    def _load_pii_type_configs_from_database(self) -> Optional[Dict]:
        """
        Load PII type configurations from database for GLiNER detector.
        
        Fetches enabled GLINER PII type configurations from database including:
        - enabled/disabled state
        - confidence thresholds
        - detector_label (GLiNER labels like "email", "credit card number")
        
        Returns:
            Dictionary mapping PII types to their configurations.
            Returns None if database is unavailable or no configs found.
        """
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import get_database_config_adapter
            
            adapter = get_database_config_adapter()
            pii_type_configs = adapter.fetch_pii_type_configs(detector='GLINER')
            
            if not pii_type_configs:
                self.logger.warning("No PII type configs found in database for GLINER")
                return None
            
            self.logger.info(f"Loaded {len(pii_type_configs)} PII type configs from database for GLINER")
            return pii_type_configs
            
        except Exception as e:
            self.logger.warning(f"Failed to load PII type configs from database: {e}")
            return None

    def _get_default_mapping(self) -> Dict[str, str]:
        """
        Get default PII type mapping as fallback.

        This fallback mapping is used when database is unavailable.
        In production, mappings should be managed via database (pii_type_config table).
        Labels match data.sql detector_label values for GLiNER.

        Returns:
            Default mapping from detector labels to PII types (35 types)
        """
        return {
            # CONTACT_CHANNEL
            "email address": "EMAIL",
            "phone number": "PHONE",
            # PERSON_IDENTITY
            "person name": "PERSON_NAME",
            "username": "USERNAME",
            # PERSON_DEMOGRAPHICS
            "date of birth": "DATE_OF_BIRTH",
            "age": "AGE",
            "gender": "GENDER",
            # FINANCIAL_IDENTIFIER
            "credit card number": "CREDIT_CARD",
            "bank account number": "BANK_ACCOUNT",
            "iban": "IBAN",
            "routing number": "ROUTING_NUMBER",
            "tax identification number": "TAX_ID",
            "cryptocurrency wallet address": "CRYPTO_WALLET",
            # GOVERNMENT_IDENTIFIER
            "social security number": "SSN",
            "passport number": "PASSPORT",
            "driver license number": "DRIVER_LICENSE",
            "national id number": "NATIONAL_ID",
            # GEO_LOCATION
            "street address": "ADDRESS",
            "city": "CITY",
            "state": "STATE",
            "country": "COUNTRY",
            "postal code": "ZIP_CODE",
            # CREDENTIAL_SECRET
            "password": "PASSWORD",
            "api key": "API_KEY",
            "access token": "ACCESS_TOKEN",
            "secret key": "SECRET_KEY",
            "database connection string": "CONNECTION_STRING",
            # STRUCTURED_TECH_IDENTIFIER
            "ip address": "IP_ADDRESS",
            "avs number": "AVS_NUMBER",
            "mac address": "MAC_ADDRESS",
            "url": "URL",
            # HEALTHCARE
            "medical record number": "MEDICAL_RECORD",
            "health insurance number": "HEALTH_INSURANCE",
            "medical condition": "MEDICAL_CONDITION",
            "medication": "MEDICATION",
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

    def _build_pii_type_mapping_from_configs(self, pii_type_configs: Dict) -> Dict[str, str]:
        """
        Build PII type mapping from database configurations (detector_label → pii_type).

        Args:
            pii_type_configs: Database PII type configurations

        Returns:
            Dictionary mapping detector labels to PII types
        """
        mapping = {}
        for pii_type, config in pii_type_configs.items():
            detector_label = config.get('detector_label')
            # Only include GLINER configs - skip PRESIDIO and REGEX labels
            if detector_label and config.get('enabled', False) and config.get('detector') == 'GLINER':
                mapping[detector_label] = pii_type

        if not mapping:
            self.logger.warning("No enabled PII types with detector labels, using defaults")
            return self._get_default_mapping()

        return mapping
    
    def _build_scoring_overrides_from_configs(self, pii_type_configs: Dict) -> Dict[str, float]:
        """
        Build per-entity-type scoring thresholds from database configurations.

        Args:
            pii_type_configs: Database PII type configurations

        Returns:
            Dictionary mapping PII types to minimum confidence thresholds
        """
        scoring = {}
        for pii_type, config in pii_type_configs.items():
            # Only include GLINER configs - skip PRESIDIO and REGEX thresholds
            if config.get('enabled', False) and config.get('detector') == 'GLINER':
                scoring[pii_type] = config['threshold']

        return scoring

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

    def _get_gliner_labels(self, pii_type_mapping: Dict[str, str]) -> List[str]:
        """
        Get GLiNER labels from PII type mapping.
        
        Args:
            pii_type_mapping: Mapping from detector labels to PII types
            
        Returns:
            List of GLiNER labels (natural language format)
        """
        return list(pii_type_mapping.keys())

    def _convert_to_pii_entities(self, raw_entities: List[Dict], chunk_text: str, pii_type_mapping: Dict[str, str]) -> List[PIIEntity]:
        """
        Convert GLiNER entities to PIIEntity format.
        
        Args:
            raw_entities: Raw entities from GLiNER
            chunk_text: The chunk text to extract actual PII substrings from
            pii_type_mapping: Mapping from detector labels to PII types
            
        Returns:
            List of PIIEntity objects with correctly extracted PII text
        """
        entities = []
        
        for entity in raw_entities:
            gliner_label = entity.get("label", "")
            pii_type = pii_type_mapping.get(gliner_label, gliner_label.upper())
            
            # Extract actual PII text using start/end positions
            start = entity.get("start", 0)
            end = entity.get("end", 0)
            actual_pii_text = chunk_text[start:end] if 0 <= start < end <= len(chunk_text) else ""
            
            pii_entity = PIIEntity(
                text=actual_pii_text,
                pii_type=pii_type,
                type_label=pii_type,
                start=start,
                end=end,
                score=entity.get("score", 0.0)
            )
            # Tag provenance for downstream logging (e.g. gRPC async PII logs)
            pii_entity.source = "GLINER"
            entities.append(pii_entity)
        
        return entities

    def _apply_entity_scoring_filter(self, entities: List[PIIEntity], scoring_overrides: Dict[str, float]) -> List[PIIEntity]:
        """
        Apply per-entity-type threshold filtering (post-filter).
        
        Similar to Presidio's _convert_and_filter_results, this method applies
        entity-specific thresholds from the scoring configuration.
        Entities below their type-specific threshold are discarded.
        
        Args:
            entities: List of detected entities
            scoring_overrides: Mapping from PII types to minimum confidence thresholds
            
        Returns:
            Filtered list of entities that pass their type-specific thresholds
        """
        if not scoring_overrides:
            return entities
        
        filtered_entities = []
        filtered_count = 0
        
        for entity in entities:
            # Get configured threshold for this entity type
            entity_threshold = scoring_overrides.get(entity.pii_type)

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
        detection_id: str,
        pii_type_mapping: Dict[str, str]
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
            pii_type_mapping: Mapping from detector labels to PII types
            
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

        # Convert raw entities to PIIEntity objects with chunk text for extraction
        chunk_entities = self._convert_to_pii_entities(raw_entities, chunk_result.text, pii_type_mapping)

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
        detection_id: str,
        pii_type_mapping: Dict[str, str]
    ) -> List[PIIEntity]:
        """
        Process chunks in parallel using ThreadPoolExecutor.
        
        Args:
            chunk_results: List of chunk results to process
            labels: GLiNER labels for detection
            threshold: Detection confidence threshold
            detection_id: Detection ID for logging
            pii_type_mapping: Mapping from detector labels to PII types
            
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
                    detection_id,
                    pii_type_mapping
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
        detection_id: str,
        pii_type_mapping: Dict[str, str]
    ) -> List[PIIEntity]:
        """
        Process chunks sequentially in a for loop.
        
        Args:
            chunk_results: List of chunk results to process
            labels: GLiNER labels for detection
            threshold: Detection confidence threshold
            detection_id: Detection ID for logging
            pii_type_mapping: Mapping from detector labels to PII types
            
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
            
            # Convert raw entities to PIIEntity objects with chunk text for extraction
            chunk_entities = self._convert_to_pii_entities(raw_entities, chunk_result.text, pii_type_mapping)
            
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

    def _detect_pii_with_chunking(self, text: str, threshold: float, detection_id: str, pii_type_configs: Optional[Dict]) -> List[PIIEntity]:
        """
        Detect PII using semantic chunking with parallel or sequential processing.
        
        Uses semantic chunking to prevent GLiNER's 768-token sentence truncation.
        Processes multiple chunks in parallel using ThreadPoolExecutor for improved performance
        on large documents.
        
        Always uses fresh configs from database - no caching to avoid stale configuration.
        
        Args:
            text: Text to analyze
            threshold: Confidence threshold
            detection_id: Detection ID for logging
            pii_type_configs: Fresh PII type configs from database
            
        Returns:
            List of detected PII entities with duplicates removed
        """
        start_time = time.time()
        
        if not self.semantic_chunker:
            raise RuntimeError("Semantic chunker not initialized. Call load_model() first.")
        
        # Build fresh pii_type_mapping and scoring_overrides from configs
        if pii_type_configs:
            pii_type_mapping = self._build_pii_type_mapping_from_configs(pii_type_configs)
            scoring_overrides = self._build_scoring_overrides_from_configs(pii_type_configs)
            self.logger.info(f"[{detection_id}] Built mapping with {len(pii_type_mapping)} labels and {len(scoring_overrides)} thresholds from fresh configs")
        else:
            # Fallback to defaults if no configs provided
            self.logger.warning(f"[{detection_id}] No configs provided, using default mapping")
            pii_type_mapping = self._get_default_mapping()
            scoring_overrides = {}
        
        # Get semantic chunks
        chunk_results = self.semantic_chunker.chunk_text(text)

        self.logger.debug(
            f"[{detection_id}] Semantic chunking: {len(text)} chars → {len(chunk_results)} chunks"
        )

        # Pre-compute labels once for all chunks
        labels = self._get_gliner_labels(pii_type_mapping)
        
        # Choose processing strategy based on configuration
        if self.parallel_enabled and len(chunk_results) > 1:
            all_entities = self._process_chunks_parallel(chunk_results, labels, threshold, detection_id, pii_type_mapping)
            processing_mode = "parallel"
        else:
            all_entities = self._process_chunks_sequential(chunk_results, labels, threshold, detection_id, pii_type_mapping)
            processing_mode = "sequential"
        
        detection_time = time.time() - start_time

        # Apply per-entity-type threshold filtering (post-filter)
        filtered_entities = self._apply_entity_scoring_filter(all_entities, scoring_overrides)
        
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
