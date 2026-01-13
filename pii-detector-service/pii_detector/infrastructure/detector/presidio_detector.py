"""
Presidio-based PII detector using Microsoft Presidio.

This module provides PresidioDetector that leverages Microsoft's Presidio
for production-ready, rule-based PII detection with extensive format support.

Business value:
- Production-tested patterns from Microsoft
- Multi-language support (EN, FR, DE, etc.)
- Extensive entity coverage (30+ types)
- Active maintenance and community support
- Configurable recognizers and scoring
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Dict, List, Optional

import toml
from presidio_analyzer import AnalyzerEngine
from presidio_analyzer.nlp_engine import NlpEngineProvider, NerModelConfiguration

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.entity.pii_type import PIIType

logger = logging.getLogger(__name__)


# Mapping from Presidio entity types to our PIIType
PRESIDIO_TO_PII_TYPE_MAP: Dict[str, PIIType] = {
    # Contact Information
    "EMAIL_ADDRESS": PIIType.EMAIL,
    "PHONE_NUMBER": PIIType.PHONE,
    "URL": PIIType.URL,
    
    # Financial
    "CREDIT_CARD": PIIType.CREDIT_CARD,
    "IBAN_CODE": PIIType.IBAN,
    "CRYPTO": PIIType.CRYPTO_WALLET,
    
    # Network
    "IP_ADDRESS": PIIType.IP_ADDRESS,
    "MAC_ADDRESS": PIIType.MAC_ADDRESS,
    
    # Personal Data
    "PERSON": PIIType.PERSON_NAME,
    "LOCATION": PIIType.LOCATION,
    "DATE_TIME": PIIType.DATE,
    "AGE": PIIType.AGE,
    "NRP": PIIType.NRP,
    
    # Medical
    "MEDICAL_LICENSE": PIIType.MEDICAL_LICENSE,
    
    # USA
    "US_SSN": PIIType.US_SSN,
    "US_BANK_NUMBER": PIIType.US_BANK_NUMBER,
    "US_DRIVER_LICENSE": PIIType.US_DRIVER_LICENSE,
    "US_ITIN": PIIType.US_ITIN,
    "US_PASSPORT": PIIType.US_PASSPORT,
    
    # UK
    "UK_NHS": PIIType.UK_NHS,
    "UK_NINO": PIIType.UK_NINO,
    
    # Spain
    "ES_NIF": PIIType.ES_NIF,
    "ES_NIE": PIIType.ES_NIE,
    
    # Italy
    "IT_FISCAL_CODE": PIIType.IT_FISCAL_CODE,
    "IT_DRIVER_LICENSE": PIIType.IT_DRIVER_LICENSE,
    "IT_VAT_CODE": PIIType.IT_VAT_CODE,
    "IT_PASSPORT": PIIType.IT_PASSPORT,
    "IT_IDENTITY_CARD": PIIType.IT_IDENTITY_CARD,
    
    # Poland
    "PL_PESEL": PIIType.PL_PESEL,
    
    # Singapore
    "SG_NRIC_FIN": PIIType.SG_NRIC_FIN,
    "SG_UEN": PIIType.SG_UEN,
    
    # Australia
    "AU_ABN": PIIType.AU_ABN,
    "AU_ACN": PIIType.AU_ACN,
    "AU_TFN": PIIType.AU_TFN,
    "AU_MEDICARE": PIIType.AU_MEDICARE,
    
    # India
    "IN_PAN": PIIType.IN_PAN,
    "IN_AADHAAR": PIIType.IN_AADHAAR,
    "IN_VEHICLE_REGISTRATION": PIIType.IN_VEHICLE_REGISTRATION,
    "IN_VOTER": PIIType.IN_VOTER,
    "IN_PASSPORT": PIIType.IN_PASSPORT,
    
    # Finland
    "FI_PERSONAL_IDENTITY_CODE": PIIType.FI_PERSONAL_IDENTITY_CODE,
    
    # Korea
    "KR_RRN": PIIType.KR_RRN,
    
    # Thailand
    "TH_TNIN": PIIType.TH_TNIN,
}


class PresidioDetector:
    """
    PII detector using Microsoft Presidio.
    
    This detector wraps Presidio's AnalyzerEngine to provide:
    - Production-ready PII detection
    - Multi-language support
    - Extensive entity type coverage
    - Configurable recognizers
    - Custom scoring per entity type
    
    Business rules:
    - Loads configuration from presidio-detector.toml
    - Maps Presidio entities to internal PIIType
    - Applies custom scoring overrides
    - Filters by confidence threshold
    - Supports allow/deny lists for custom policies
    
    Architecture:
    - Implements PIIDetectorProtocol for integration
    - Uses Presidio's AnalyzerEngine internally
    - Configurable via TOML file
    - Lazy initialization of analyzer
    """
    
    def __init__(self, config_path: Optional[Path] = None):
        """
        Initialize Presidio detector.
        
        Args:
            config_path: Optional path to configuration file
        """
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        
        # Initialize database adapter (with graceful fallback)
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import get_database_config_adapter
            self._db_adapter = get_database_config_adapter()
            self.logger.info("Database adapter initialized for Presidio configuration")
        except Exception as e:
            self.logger.warning(f"Failed to initialize database adapter: {e}, will use TOML fallback")
            self._db_adapter = None
        
        # Load configuration
        self.config = self._load_config(config_path)
        self._analyzer: Optional[AnalyzerEngine] = None
        
        # Extract configuration
        model_config = self.config.get("model", {})
        self._model_id = model_config.get("model_id", "presidio-detector")
        self._enabled = model_config.get("enabled", True)
        self._priority = model_config.get("priority", 2)
        
        detection_config = self.config.get("detection", {})
        self._default_threshold = detection_config.get("default_threshold", 0.5)
        self._languages = detection_config.get("languages", ["en"])
        print("\n Languages", self._languages)
        self._labels_to_ignore = detection_config.get("labels_to_ignore", [
            "CARDINAL", "MONEY", "WORK_OF_ART", "PRODUCT", "ORDINAL",
            "QUANTITY", "PERCENT", "LANGUAGE", "EVENT", "LAW", "FAC"
        ])
        
        # Recognizer configuration (database-first, TOML fallback)
        self._recognizers_config = self.config.get("recognizers", {})
        self._scoring_overrides = self.config.get("scoring", {})
        
        # Advanced features
        advanced_config = self.config.get("advanced", {})
        self._use_context = advanced_config.get("use_context", True)
        self._allow_list = advanced_config.get("allow_list", [])
        self._deny_list = advanced_config.get("deny_list", [])
        
        # Validate configuration
        self._validate_configuration()
        
        self.logger.info(
            f"PresidioDetector initialized: enabled={self._enabled}, "
            f"languages={self._languages}, threshold={self._default_threshold}"
        )
    
    @property
    def model_id(self) -> str:
        """Get model identifier."""
        return self._model_id
    
    def _validate_configuration(self) -> None:
        """
        Validate configuration and log warnings for common issues.
        
        This method checks for:
        - Empty configuration (file not loaded)
        - No recognizers enabled
        - Common configuration errors
        """
        if not self.config:
            self.logger.error(
                "Presidio configuration is empty. This usually means the config file "
                "could not be loaded. Please check that "
                "pii-detector-service/config/models/presidio-detector.toml exists."
            )
            return
        
        if not self._recognizers_config:
            self.logger.warning(
                "No recognizers section found in Presidio configuration. "
                "All entity types will be disabled by default. "
                "Please ensure [recognizers] section exists in presidio-detector.toml"
            )
            return
        
        # Count enabled recognizers
        enabled_count = sum(1 for enabled in self._recognizers_config.values() if enabled)
        
        if enabled_count == 0:
            self.logger.warning(
                "No recognizers are enabled in Presidio configuration. "
                "PII detection will return no results. "
                "Please enable at least one recognizer in [recognizers] section of presidio-detector.toml"
            )
        else:
            self.logger.debug(f"Configuration valid: {enabled_count} recognizers enabled")
    
    def _load_config(self, config_path: Optional[Path] = None) -> Dict:
        """
        Load configuration from TOML file.
        
        Args:
            config_path: Optional path to config file
            
        Returns:
            Configuration dictionary
        """
        if config_path is None:
            # From pii-detector-service/pii_detector/service/detector/presidio_detector.py
            # Go up 4 levels to reach pii-detector-service/
            config_dir = Path(__file__).parent.parent.parent.parent / "config"
            config_path = config_dir / "models" / "presidio-detector.toml"
        
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                config = toml.load(f)
            self.logger.info(f"Loaded configuration from {config_path}")
            return config
        except Exception as e:
            self.logger.warning(f"Failed to load config from {config_path}: {e}")
            return {}
    
    def _load_pii_type_configs_from_database(self) -> Optional[Dict]:
        """
        Load PII type configurations from database for Presidio detector.
        
        Fetches enabled PRESIDIO PII type configurations from database including:
        - enabled/disabled state
        - confidence thresholds
        - detector_label (Presidio entity types like "EMAIL_ADDRESS")
        
        Returns:
            Dictionary mapping PII types to their configurations.
            Returns None if database is unavailable or no configs found.
        """
        if not self._db_adapter:
            self.logger.debug("Database adapter not available, will use TOML configuration")
            return None
        
        try:
            pii_type_configs = self._db_adapter.fetch_pii_type_configs(detector='PRESIDIO')
            
            if not pii_type_configs:
                self.logger.warning("No PII type configs found in database for PRESIDIO, using TOML")
                return None
            
            self.logger.info(f"Loaded {len(pii_type_configs)} PII type configs from database for PRESIDIO")
            return pii_type_configs
            
        except Exception as e:
            self.logger.warning(f"Failed to load PII type configs from database: {e}, using TOML")
            return None
    
    def _build_allowed_entities_from_database(self, db_configs: Dict) -> List[str]:
        """
        Build whitelist of allowed Presidio entity types from database configurations.
        
        Uses detector_label field which contains Presidio entity types (e.g., "EMAIL_ADDRESS").
        Only processes configs where detector is 'PRESIDIO' or 'ALL'.
        
        Args:
            db_configs: Database PII type configurations
            
        Returns:
            List of allowed Presidio entity type strings from detector_label field
        """
        allowed_entities = []
        
        for pii_type, config in db_configs.items():
            # Filter by detector: only process PRESIDIO or ALL configs
            config_detector = config.get('detector', 'ALL')
            if config_detector not in ('PRESIDIO', 'ALL'):
                continue  # Skip configs for other detectors (GLINER, REGEX)
            
            if config.get('enabled', False):
                detector_label = config.get('detector_label')
                if detector_label:
                    allowed_entities.append(detector_label)
                else:
                    self.logger.warning(
                        f"PII type {pii_type} enabled but has no detector_label, skipping"
                    )
        
        self.logger.info(
            f"Built whitelist from database with {len(allowed_entities)} entity types"
        )
        return allowed_entities
    
    def _build_scoring_overrides_from_database(self, db_configs: Dict) -> Dict[str, float]:
        """
        Build per-entity-type scoring thresholds from database configurations.
        
        Maps detector_label (Presidio entity type) to threshold value.
        Only processes configs where detector is 'PRESIDIO' or 'ALL'.
        
        Args:
            db_configs: Database PII type configurations
            
        Returns:
            Dictionary mapping Presidio entity types to minimum confidence thresholds
        """
        scoring = {}
        
        for pii_type, config in db_configs.items():
            # Filter by detector: only process PRESIDIO or ALL configs
            config_detector = config.get('detector', 'ALL')
            if config_detector not in ('PRESIDIO', 'ALL'):
                continue  # Skip configs for other detectors (GLINER, REGEX)
            
            if config.get('enabled', False):
                detector_label = config.get('detector_label')
                threshold = config.get('threshold')
                if detector_label and threshold is not None:
                    scoring[detector_label] = float(threshold)
        
        self.logger.info(
            f"Built scoring overrides from database with {len(scoring)} thresholds"
        )
        return scoring
    
    def download_model(self) -> None:
        """
        Download model (no-op for Presidio).
        
        Presidio uses pre-built recognizers, no download needed.
        """
        self.logger.info("Presidio uses built-in recognizers, no download needed")
    
    def load_model(self) -> None:
        """
        Load Presidio analyzer with configuration.
        
        Initializes the AnalyzerEngine with:
        - Selected recognizers based on configuration
        - Custom scoring overrides
        - Language support
        """
        if self._analyzer is not None:
            self.logger.info("Presidio analyzer already loaded")
            return
        
        try:
            # Build NLP engine without triggering spaCy downloads.
            # If the config lists concrete model packages, we will let NlpEngineProvider handle it.
            # If it lists placeholders like "blank:xx" or has no models, we inject spaCy.blank pipelines directly.
            nlp_section = self.config.get("nlp", {}) if isinstance(self.config, dict) else {}
            models_cfg = []
            if isinstance(nlp_section, dict):
                models_cfg = nlp_section.get("models", []) or []

            use_provider = False
            if models_cfg:
                # Decide whether the models are real packages or placeholders
                # Any model_name starting with "blank:" should NOT be sent to provider
                has_placeholder = any(
                    isinstance(m, dict) and str(m.get("model_name", "")).startswith("blank:")
                    for m in models_cfg
                )
                use_provider = not has_placeholder

            if use_provider:
                nlp_configuration = {**nlp_section}
                
                # Inject labels_to_ignore into ner_model_configuration
                # This is the modern way to configure ignored labels for NlpEngine
                if "ner_model_configuration" not in nlp_configuration:
                    nlp_configuration["ner_model_configuration"] = {}
                
                ner_config = nlp_configuration["ner_model_configuration"]
                if "labels_to_ignore" not in ner_config:
                    ner_config["labels_to_ignore"] = self._labels_to_ignore

                # Also inject into models for backward compatibility or specific engine versions
                if "models" in nlp_configuration and isinstance(nlp_configuration["models"], list):
                    for model in nlp_configuration["models"]:
                        if isinstance(model, dict):
                            # Add labels_to_ignore if not present
                            if "labels_to_ignore" not in model:
                                model["labels_to_ignore"] = self._labels_to_ignore
                
                provider = NlpEngineProvider(nlp_configuration=nlp_configuration)
                nlp_engine = provider.create_engine()
                supported_langs = self._languages
            else:
                # Inject blank pipelines for requested languages
                try:
                    from presidio_analyzer.nlp_engine import SpacyNlpEngine  # type: ignore
                    import spacy  # type: ignore
                except Exception as imp_err:
                    raise RuntimeError(f"spaCy not available for blank injection: {imp_err}")

                # Configure NER to ignore labels even in fallback/blank mode
                ner_config = NerModelConfiguration(labels_to_ignore=self._labels_to_ignore)
                nlp_engine = SpacyNlpEngine(ner_model_configuration=ner_config)
                # Ensure the engine has an 'nlp' mapping
                if not hasattr(nlp_engine, "nlp") or getattr(nlp_engine, "nlp") is None:
                    setattr(nlp_engine, "nlp", {})
                loaded_langs: List[str] = []
                for lang in self._languages:
                    try:
                        nlp_engine.nlp[lang] = spacy.blank(lang)
                        loaded_langs.append(lang)
                    except Exception as lang_err:
                        self.logger.warning(f"Failed to initialize spaCy blank('{lang}'): {lang_err}")
                supported_langs = loaded_langs if loaded_langs else ["en"]
                if not loaded_langs and "en" in supported_langs:
                    # Try at least English
                    try:
                        nlp_engine.nlp["en"] = spacy.blank("en")
                    except Exception as en_err:
                        self.logger.warning(f"Also failed to initialize spaCy blank('en'): {en_err}")

            # Create analyzer with prepared NLP engine and supported languages
            self._analyzer = AnalyzerEngine(
                nlp_engine=nlp_engine,
                supported_languages=supported_langs,
            )

            self.logger.info(
                "Presidio analyzer loaded without spaCy package downloads (blank pipelines or preinstalled models)."
            )

        except Exception as e:
            self.logger.error(f"Failed to load Presidio analyzer with configured NLP engine: {e}")
            # Last resort fallback without NLP
            try:
                self._analyzer = AnalyzerEngine()
                self.logger.warning("Using basic Presidio analyzer without NLP support")
            except Exception as inner_e:
                self.logger.error(f"AnalyzerEngine fallback failed: {inner_e}")
                raise
    
    def _build_allowed_entities(self, fresh_configs: Optional[Dict] = None) -> List[str]:
        """
        Build whitelist of allowed Presidio entity types.
        
        Always uses fresh configs from database - no caching to avoid stale configuration.
        
        Args:
            fresh_configs: Optional fresh PII type configs from database (if already fetched)
        
        Returns:
            List of allowed Presidio entity type strings
        """
        # If fresh configs provided, use them
        if fresh_configs is not None:
            self.logger.info("Using fresh request-time configuration for allowed entities")
            return self._build_allowed_entities_from_database(fresh_configs)
        
        # Otherwise, fetch fresh from database (no cache)
        if not self._db_adapter:
            raise RuntimeError("Database adapter not available, cannot load PII type configs from database")
        
        self.logger.info("Fetching fresh configuration from database for allowed entities")
        fresh_db_configs = self._load_pii_type_configs_from_database()
        
        if fresh_db_configs:
            return self._build_allowed_entities_from_database(fresh_db_configs)
        else:
            self.logger.warning("No configs found in database, returning empty whitelist")
            return []

    def _build_allowed_entities_from_toml(self) -> List[str]:
        """
        Build whitelist of allowed Presidio entity types from TOML config.
        
        Converts TOML keys to official Presidio entity names.
        Example: email -> EMAIL_ADDRESS, person_name -> PERSON
        
        Returns:
            List of allowed Presidio entity type strings
        """
        allowed_entities = []
        
        # Mapping from config keys to Presidio entity types
        config_to_presidio = {
            # Contact Information
            "email": "EMAIL_ADDRESS",
            "phone": "PHONE_NUMBER",
            "url": "URL",
            
            # Financial
            "credit_card": "CREDIT_CARD",
            "iban": "IBAN_CODE",
            "crypto": "CRYPTO",
            
            # Network
            "ip_address": "IP_ADDRESS",
            "mac_address": "MAC_ADDRESS",
            
            # Personal Data
            "person_name": "PERSON",
            "location": "LOCATION",
            "date": "DATE_TIME",
            "age": "AGE",
            "gender": "GENDER",
            "nrp": "NRP",
            
            # Medical
            "medical_license": "MEDICAL_LICENSE",
            
            # USA
            "us_ssn": "US_SSN",
            "us_bank_number": "US_BANK_NUMBER",
            "us_driver_license": "US_DRIVER_LICENSE",
            "us_itin": "US_ITIN",
            "us_passport": "US_PASSPORT",
            
            # UK
            "uk_nhs": "UK_NHS",
            "uk_nino": "UK_NINO",
            
            # Spain
            "es_nif": "ES_NIF",
            "es_nie": "ES_NIE",
            
            # Italy
            "it_fiscal_code": "IT_FISCAL_CODE",
            "it_driver_license": "IT_DRIVER_LICENSE",
            "it_vat_code": "IT_VAT_CODE",
            "it_passport": "IT_PASSPORT",
            "it_identity_card": "IT_IDENTITY_CARD",
            
            # Poland
            "pl_pesel": "PL_PESEL",
            
            # Singapore
            "sg_nric_fin": "SG_NRIC_FIN",
            "sg_uen": "SG_UEN",
            
            # Australia
            "au_abn": "AU_ABN",
            "au_acn": "AU_ACN",
            "au_tfn": "AU_TFN",
            "au_medicare": "AU_MEDICARE",
            
            # India
            "in_pan": "IN_PAN",
            "in_aadhaar": "IN_AADHAAR",
            "in_vehicle_registration": "IN_VEHICLE_REGISTRATION",
            "in_voter": "IN_VOTER",
            "in_passport": "IN_PASSPORT",
            
            # Finland
            "fi_personal_identity_code": "FI_PERSONAL_IDENTITY_CODE",
            
            # Korea
            "kr_rrn": "KR_RRN",
            
            # Thailand
            "th_tnin": "TH_TNIN",
        }
        
        # Build allowed list from enabled recognizers
        for config_key, enabled in self._recognizers_config.items():
            if enabled:
                presidio_entity = config_to_presidio.get(config_key)
                if presidio_entity:
                    allowed_entities.append(presidio_entity)
                else:
                    self.logger.warning(
                        f"Unknown recognizer key '{config_key}' in config, skipping"
                    )
        
        self.logger.info(f"Built whitelist from TOML with {len(allowed_entities)} entity types")
        return allowed_entities
    
    def detect_pii(
        self, text: str, threshold: Optional[float] = None, pii_type_configs: Optional[Dict] = None
    ) -> List[PIIEntity]:
        """
        Detect PII using Presidio analyzer.
        
        Follows Presidio best practices:
        1. Uses entities whitelist to restrict detection scope
        2. Applies score_threshold at analysis time
        3. Post-filters results based on per-entity thresholds from [scoring]
        
        Args:
            text: Text to analyze
            threshold: Optional confidence threshold (uses default if None)
            pii_type_configs: Optional fresh PII type configs from database (overrides cached configs)
            
        Returns:
            List of detected PII entities
        """
        if not text:
            return []
        
        if not self._enabled:
            self.logger.debug("Presidio detector is disabled")
            return []
        
        # Ensure analyzer is loaded
        if self._analyzer is None:
            self.load_model()
        
        # Use provided threshold or default
        score_threshold = threshold if threshold is not None else self._default_threshold
        
        # Build whitelist of allowed entities (use fresh configs if provided)
        allowed_entities = self._build_allowed_entities(pii_type_configs)
        
        if not allowed_entities:
            self.logger.warning("No entities enabled in configuration, skipping detection")
            return []
        
        try:
            # Log detection parameters for debugging
            self.logger.info(
                f"Starting Presidio analysis with {len(allowed_entities)} allowed entities, "
                f"threshold={score_threshold}, language={self._languages[0]}"
            )
            self.logger.info(f"Allowed entities: {allowed_entities}")
            self.logger.info(f"Text length: {len(text)} characters")
            self.logger.debug(f"Text preview (first 200 chars): {text[:200]}")
            
            # Analyze with Presidio - pass entities parameter to lock scope
            results = self._analyzer.analyze(
                text=text,
                language=self._languages[0],  # Primary language
                entities=allowed_entities,     # Whitelist restricts detection scope
                score_threshold=score_threshold,
                return_decision_process=False
            )
            
            self.logger.info(
                f"Presidio analyze() returned {len(results)} raw results "
                f"(before post-filtering)"
                f"Presidio analyze() returned this result {results}"
            )

            # Log raw entity types for debugging unexpected entities
            if results:
                entity_types = {r.entity_type for r in results}
                self.logger.info(f"Raw entity types detected: {entity_types}")
                self.logger.debug("Raw results details:")
                for r in results[:10]:  # Log first 10 results
                    self.logger.debug(
                        f"  - {r.entity_type} at [{r.start}:{r.end}] "
                        f"text='{text[r.start:r.end]}' score={r.score:.3f}"
                    )
            else:
                self.logger.warning(
                    "Presidio returned 0 results! This may indicate:"
                    "\n  1. No PII in text"
                    "\n  2. Recognizers not matching (check language support)"
                    "\n  3. Score threshold too high"
                    "\n  4. Recognizers were removed incorrectly"
                )
            
            # Convert to PIIEntity with post-filtering by per-entity thresholds
            entities = self._convert_and_filter_results(text, results, pii_type_configs)
            
            self.logger.debug(
                f"Presidio final output: {len(entities)} entities after post-filtering"
            )
            
            return entities
            
        except Exception as e:
            self.logger.error(f"Presidio detection failed: {e}", exc_info=True)
            return []
    
    def _convert_and_filter_results(
        self, text: str, results: List, fresh_configs: Optional[Dict] = None
    ) -> List[PIIEntity]:
        """
        Convert Presidio results to PIIEntity and apply per-entity threshold filtering.
        
        Always uses fresh configs from database - no caching to avoid stale configuration.
        Post-filters results based on scoring configuration:
        - If a score override exists, it's used as the minimum threshold
        - Results below the entity-specific threshold are discarded
        
        Args:
            text: Original text
            results: Presidio RecognizerResult list
            fresh_configs: Optional fresh PII type configs from database (if already fetched)
            
        Returns:
            List of PIIEntity objects that pass threshold filtering
        """
        # If fresh configs provided, use them
        if fresh_configs is not None:
            self.logger.info("Using fresh request-time configuration for post-filtering")
            scoring_overrides = self._build_scoring_overrides_from_database(fresh_configs)
        else:
            # Otherwise, fetch fresh from database (no cache)
            if self._db_adapter:
                self.logger.info("Fetching fresh configuration from database for post-filtering")
                fresh_db_configs = self._load_pii_type_configs_from_database()
                
                if fresh_db_configs:
                    scoring_overrides = self._build_scoring_overrides_from_database(fresh_db_configs)
                else:
                    self.logger.warning("No configs found in database, using TOML thresholds for post-filtering")
                    scoring_overrides = self._scoring_overrides
            else:
                self.logger.warning("Database adapter not available, using TOML thresholds for post-filtering")
                scoring_overrides = self._scoring_overrides
        
        entities = []
        filtered_count = 0
        
        for result in results:
            # Map Presidio entity type to our PIIType
            pii_type = PRESIDIO_TO_PII_TYPE_MAP.get(
                result.entity_type,
                PIIType.UNKNOWN
            )
            
            # Log unknown entity types for debugging
            if pii_type == PIIType.UNKNOWN:
                self.logger.warning(
                    f"Unknown Presidio entity_type '{result.entity_type}' detected at "
                    f"position {result.start}-{result.end}. Consider adding to "
                    f"PRESIDIO_TO_PII_TYPE_MAP or filtering in configuration."
                )
            
            # Get configured threshold for this entity type
            entity_threshold = scoring_overrides.get(result.entity_type)
            
            # Post-filter: discard if below entity-specific threshold
            if entity_threshold is not None and result.score < entity_threshold:
                filtered_count += 1
                self.logger.debug(
                    f"Filtered out {result.entity_type} (score={result.score:.3f} < "
                    f"threshold={entity_threshold:.3f}) at position {result.start}-{result.end}"
                )
                continue
            
            # Extract text
            entity_text = text[result.start:result.end]
            
            # Use original Presidio score (no override)
            # The scoring values are used as minimum thresholds, not score replacements
            score = result.score
            
            # Create PIIEntity with type_label
            entity = PIIEntity(
                text=entity_text,
                pii_type=pii_type,
                type_label=pii_type.value,  # Use PIIType enum value as label
                start=result.start,
                end=result.end,
                score=score,
                source=DetectorSource.PRESIDIO
            )
            
            entities.append(entity)
        
        if filtered_count > 0:
            self.logger.info(
                f"Post-filtered {filtered_count} results based on per-entity thresholds"
            )
        
        return entities


def should_use_presidio_detector() -> bool:
    """
    Determine if Presidio detector should be used.
    
    Returns True if:
    - Presidio detection is enabled in global configuration
    - Presidio detector model is enabled in config
    
    Returns:
        True if Presidio detector should be used
    """
    try:
        from pii_detector.application.config.detection_policy import _load_llm_config
        
        config = _load_llm_config()
        
        # Check global presidio detection switch
        detection_config = config.get("detection", {})
        presidio_enabled = detection_config.get("presidio_detection_enabled", True)
        
        if not presidio_enabled:
            logger.info("Presidio detection disabled in detection-settings.toml")
            return False
        
        # Check if presidio-detector model is enabled
        presidio_config = config.get("models", {}).get("presidio-detector", {})
        model_enabled = presidio_config.get("enabled", True)
        
        if not model_enabled:
            logger.info("Presidio detector model disabled in presidio-detector.toml")
            return False
        
        logger.info("Presidio detection enabled")
        return True
        
    except Exception as e:
        logger.warning(f"Failed to determine Presidio detector status: {e}")
        return False