"""
Regex-based PII detector for deterministic pattern matching.

This detector uses regular expressions for fast, deterministic PII detection,
complementing ML-based detectors with rule-based patterns.
"""

import logging
import re
from importlib import import_module
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import toml

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import PIIDetectionError


class RegexPattern:
    """
    Encapsulates a regex pattern with metadata for PII detection.
    
    Business value:
    - Associates regex patterns with PII types and confidence scores
    - Supports validation logic (e.g., Luhn algorithm for credit cards)
    - Enables priority-based pattern selection
    """
    
    def __init__(
        self,
        name: str,
        pii_type: str,
        pattern: str,
        score: float,
        priority: str,
        description: str = "",
        requires_validation: bool = False,
        validation_method: Optional[str] = None,
        country: Optional[str] = None,
        enabled: bool = True
    ):
        """
        Initialize regex pattern with metadata.
        
        Args:
            name: Pattern identifier
            pii_type: PII type this pattern detects
            pattern: Regular expression pattern
            score: Confidence score (0.0-1.0)
            priority: Priority level (high, medium, low)
            description: Human-readable description
            requires_validation: Whether additional validation is needed
            validation_method: Validation method name (e.g., 'luhn')
            country: Country code if pattern is country-specific
            enabled: Whether pattern is active
        """
        self.name = name
        self.pii_type = pii_type
        self.pattern = pattern
        self.score = score
        self.priority = priority
        self.description = description
        self.requires_validation = requires_validation
        self.validation_method = validation_method
        self.country = country
        self.enabled = enabled
        
        # Compile regex for performance
        try:
            self.compiled = re.compile(pattern)
        except re.error as e:
            raise ValueError(f"Invalid regex pattern '{name}': {e}")


class RegexDetector:
    """
    Regex-based PII detector implementing PIIDetectorProtocol.
    
    This detector provides fast, deterministic PII detection using
    configurable regular expression patterns. It complements ML-based
    detectors by:
    - Providing high precision for structured formats (emails, IPs, etc.)
    - Offering consistent, reproducible results
    - Requiring no model loading or GPU resources
    
    Business rules:
    - Patterns are loaded from configuration files
    - Each pattern has a priority and confidence score
    - Optional validation (e.g., Luhn) for specific types
    - Overlapping matches are resolved by priority
    """
    
    def __init__(
        self,
        config: Optional[DetectionConfig] = None,
        config_path: Optional[Path] = None
    ):
        """
        Initialize the regex detector.
        
        Args:
            config: Detection configuration (optional)
            config_path: Path to regex patterns config file
        """
        self.config = config or DetectionConfig(model_id="regex-detector")
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        
        # Load patterns from configuration
        if config_path is None:
            config_path = self._get_default_config_path()
        
        self.patterns = self._load_patterns(config_path)
        self._national_validators = {}
        if config_path.resolve() == self._get_default_config_path().resolve():
            self.patterns.extend(self._load_national_patterns(config_path.with_name("national-identifiers.toml")))
        self.validation_settings = self._load_validation_settings(config_path)
        
        self.logger.info(
            f"RegexDetector initialized with {len(self.patterns)} patterns"
        )
    
    @property
    def model_id(self) -> str:
        """Get model ID for compatibility."""
        return self.config.model_id
    
    def download_model(self) -> None:
        """
        No-op for regex detector (no model to download).
        
        Implements PIIDetectorProtocol interface for compatibility.
        """
        self.logger.debug("RegexDetector requires no model download")
    
    def load_model(self) -> None:
        """
        No-op for regex detector (no model to load).
        
        Implements PIIDetectorProtocol interface for compatibility.
        """
        self.logger.debug("RegexDetector requires no model loading")
    
    def detect_pii(
        self, text: str, threshold: Optional[float] = None, pii_type_configs: Optional[dict] = None
    ) -> List[PIIEntity]:
        """
        Detect PII using regex patterns.
        
        Business process:
        1. Apply all enabled regex patterns to text
        2. Validate matches requiring validation (e.g., Luhn for credit cards)
        3. Filter by confidence threshold
        4. Resolve overlapping matches by priority
        5. Return sorted entities
        
        Args:
            text: Text to analyze
            threshold: Confidence threshold (filters low-confidence matches)
            
        Returns:
            List of detected PII entities sorted by position
            
        Raises:
            PIIDetectionError: If detection fails
        """
        if not text:
            return []
        
        threshold = threshold or self.config.threshold
        
        try:
            # Detect matches for all patterns
            all_matches = self._detect_all_patterns(text, pii_type_configs)
            
            # Validate matches requiring validation
            validated_matches = self._validate_matches(all_matches)
            
            # Filter by threshold
            filtered_matches = [
                m for m in validated_matches if m.score >= threshold
            ]
            
            # Resolve overlaps if configured
            if self.validation_settings.get("remove_overlaps", True):
                final_matches = self._resolve_overlaps(filtered_matches)
            else:
                final_matches = filtered_matches
            
            # Sort by position
            final_matches.sort(key=lambda x: x.start)
            
            self.logger.debug(
                f"RegexDetector found {len(final_matches)} entities "
                f"(from {len(all_matches)} initial matches)"
            )
            
            return final_matches
            
        except Exception as e:
            self.logger.error(f"Regex detection failed: {e}")
            raise PIIDetectionError(f"Regex detection failed: {e}") from e
    
    def mask_pii(
        self, text: str, threshold: Optional[float] = None
    ) -> Tuple[str, List[PIIEntity]]:
        """
        Mask PII in text using regex detection.
        
        Args:
            text: Text to mask
            threshold: Confidence threshold
            
        Returns:
            Tuple of (masked_text, detected_entities)
        """
        entities = self.detect_pii(text, threshold)
        masked_text = self._apply_masks(text, entities)
        
        self.logger.debug(f"Masked {len(entities)} entities")
        return masked_text, entities
    
    def _get_default_config_path(self) -> Path:
        """Get default path to regex patterns configuration."""
        # Navigate up from current file to config directory
        current_file = Path(__file__)
        project_root = current_file.parent.parent.parent.parent
        config_path = project_root / "config" / "models" / "regex-patterns.toml"
        
        return config_path
    
    def _load_patterns(self, config_path: Path) -> List[RegexPattern]:
        """
        Load regex patterns from TOML configuration.
        
        Args:
            config_path: Path to configuration file
            
        Returns:
            List of compiled regex patterns
            
        Raises:
            FileNotFoundError: If config file doesn't exist
            ValueError: If configuration is invalid
        """
        if not config_path.exists():
            raise FileNotFoundError(f"Regex patterns config not found: {config_path}")
        
        try:
            config = toml.load(config_path)
            patterns = []
            
            patterns_config = config.get("patterns", {})
            for pattern_name, pattern_data in patterns_config.items():
                # Check if pattern is enabled (default: True)
                if not pattern_data.get("enabled", True):
                    self.logger.debug(f"Pattern '{pattern_name}' is disabled")
                    continue
                
                pattern = RegexPattern(
                    name=pattern_name,
                    pii_type=pattern_data["type"],
                    pattern=pattern_data["pattern"],
                    score=pattern_data["score"],
                    priority=pattern_data["priority"],
                    description=pattern_data.get("description", ""),
                    requires_validation=pattern_data.get("requires_validation", False),
                    validation_method=pattern_data.get("validation_method"),
                    country=pattern_data.get("country"),
                    enabled=pattern_data.get("enabled", True)
                )
                patterns.append(pattern)
                self.logger.debug(f"Loaded pattern: {pattern_name} -> {pattern.pii_type}")
            
            return patterns
            
        except Exception as e:
            raise ValueError(f"Failed to load regex patterns: {e}") from e
    
    def _load_validation_settings(self, config_path: Path) -> Dict:
        """Load validation settings from configuration."""
        try:
            config = toml.load(config_path)
            return config.get("validation", {})
        except Exception as e:
            self.logger.warning(f"Failed to load validation settings: {e}")
            return {}
    
    def _load_national_patterns(self, path: Path) -> List[RegexPattern]:
        patterns = []
        for rule in toml.load(path)["rules"]:
            method = rule["validator"]
            if method != "format":
                self._national_validators[method] = import_module(f"stdnum.{method}").is_valid
            pattern = RegexPattern(
                name=rule["key"], pii_type=rule["entity_type"],
                pattern="(?i)" + rule["pattern"], score=rule["score"], priority="medium",
                description=rule["label"], country=rule["country"],
                requires_validation=method != "format", validation_method=method,
            )
            pattern.config_key = rule["key"]
            patterns.append(pattern)
        return patterns

    @staticmethod
    def _pattern_is_enabled(pattern: RegexPattern, configs: Optional[dict]) -> bool:
        key = getattr(pattern, "config_key", None)
        if configs is None:
            return pattern.enabled and key is None
        key = key or pattern.pii_type
        config = configs.get(f"REGEX:{key}", configs.get(key, {}))
        return (config.get("detector", "ALL") in ("REGEX", "ALL")
                and config.get("enabled", False)
                and pattern.score >= float(config.get("threshold", 0.0)))

    def _detect_all_patterns(self, text: str, pii_type_configs: Optional[dict] = None) -> List[PIIEntity]:
        """
        Apply all enabled regex patterns to text.
        
        Args:
            text: Text to analyze
            
        Returns:
            List of all matches from all patterns
        """
        all_matches = []
        
        for pattern in self.patterns:
            if not self._pattern_is_enabled(pattern, pii_type_configs):
                continue
            
            matches = pattern.compiled.finditer(text)
            for match in matches:
                entity = PIIEntity(
                    text=match.group(),
                    pii_type=pattern.pii_type,
                    type_label=pattern.pii_type,
                    start=match.start(),
                    end=match.end(),
                    score=pattern.score,
                    source=DetectorSource.REGEX
                )
                # Tag provenance for downstream logging (e.g. gRPC async PII logs)
                entity.source = DetectorSource.REGEX
                # Store pattern metadata for validation
                entity._pattern_name = pattern.name
                entity._requires_validation = pattern.requires_validation
                entity._validation_method = pattern.validation_method
                entity._priority = pattern.priority
                entity._config_pii_type = getattr(pattern, "config_key", pattern.pii_type)
                
                all_matches.append(entity)
        
        return all_matches
    
    def _validate_matches(self, matches: List[PIIEntity]) -> List[PIIEntity]:
        """
        Validate matches that require additional validation.
        
        Business rule: Some patterns (e.g., credit cards) need algorithmic
        validation beyond regex matching to reduce false positives.
        
        Args:
            matches: List of matches to validate
            
        Returns:
            List of validated matches (invalid matches removed)
        """
        luhn_enabled = self.validation_settings.get("enable_luhn", True)
        validators = {
            "luhn": self._validate_luhn,
            "insee_key": self._validate_insee_key,
            "avs_ean13": self._validate_avs_ean13,
            "be_nrn": self._validate_be_nrn,
            **self._national_validators,
        }

        validated = []

        for match in matches:
            if not getattr(match, '_requires_validation', False):
                validated.append(match)
                continue

            validation_method = getattr(match, '_validation_method', None)

            # Luhn stays behind its historical opt-out flag; other checksum
            # validators always run since they are the sole precision guard
            # for the national identifier patterns.
            if validation_method == "luhn" and not luhn_enabled:
                validated.append(match)
                continue

            validator = validators.get(validation_method)
            if validator is None:
                # Unknown validation method, keep the match
                validated.append(match)
            elif validator(match.text.replace("\u00a0", " ").replace("\u202f", " ")) and (
                validation_method != "be.ssn" or self._validate_be_nrn(match.text)
            ):
                validated.append(match)
            else:
                self.logger.debug(
                    f"{validation_method} validation failed for: {match.text[:4]}..."
                )

        return validated
    
    def _validate_luhn(self, card_number: str) -> bool:
        """
        Validate credit card number using Luhn algorithm.
        
        Args:
            card_number: Card number to validate
            
        Returns:
            True if valid, False otherwise
        """
        # Remove non-digit characters
        digits = [int(d) for d in card_number if d.isdigit()]
        
        if len(digits) < 13:  # Minimum credit card length
            return False
        
        checksum = 0
        odd = True
        
        for digit in reversed(digits):
            if odd:
                checksum += digit
            else:
                doubled = digit * 2
                checksum += doubled if doubled < 10 else doubled - 9
            odd = not odd
        
        return checksum % 10 == 0

    def _validate_insee_key(self, nir: str) -> bool:
        """
        Validate a French social security number (NIR) control key.

        The 15-digit NIR is a 13-digit body plus a 2-digit control key equal to
        97 - (body mod 97). Corsica departments are encoded 2A/2B and mapped to
        19/18 before the computation.

        Args:
            nir: Matched NIR text (separators and Corsica letters allowed)

        Returns:
            True if the control key matches, False otherwise
        """
        normalized = nir.upper().replace(" ", "").replace(".", "")
        normalized = normalized.replace("2A", "19").replace("2B", "18")

        if len(normalized) != 15 or not normalized.isdigit():
            return False

        body = int(normalized[:13])
        key = int(normalized[13:])
        return 97 - (body % 97) == key

    def _validate_avs_ean13(self, avs: str) -> bool:
        """
        Validate a Swiss AVS/AHV number using its EAN-13 check digit.

        Args:
            avs: Matched AVS text (e.g. "756.xxxx.xxxx.xx")

        Returns:
            True if the check digit is valid, False otherwise
        """
        digits = [int(c) for c in avs if c.isdigit()]

        if len(digits) != 13:
            return False

        weighted = sum(
            digit * (1 if index % 2 == 0 else 3)
            for index, digit in enumerate(digits[:12])
        )
        check_digit = (10 - (weighted % 10)) % 10
        return check_digit == digits[12]

    def _validate_be_nrn(self, nrn: str) -> bool:
        """
        Validate a Belgian National Register Number (mod-97 control number).

        The control number is 97 - (body mod 97). For people born from 2000
        onwards a leading "2" is prepended to the 9-digit body before the
        modulo, so both variants are accepted.

        Args:
            nrn: Matched NRN text (e.g. "YYMMDD-SSS-CC")

        Returns:
            True if the control number matches either variant, False otherwise
        """
        digits = "".join(c for c in nrn if c.isdigit())

        if len(digits) != 11:
            return False

        body, control = digits[:9], int(digits[9:])
        pre_2000 = 97 - (int(body) % 97)
        post_2000 = 97 - (int("2" + body) % 97)
        return control in (pre_2000, post_2000)

    def _resolve_overlaps(self, matches: List[PIIEntity]) -> List[PIIEntity]:
        """
        Resolve overlapping matches by priority.
        
        Business rule: When multiple patterns match overlapping text,
        keep the match with highest priority. This prevents duplicate
        detections and improves precision.
        
        Args:
            matches: List of potentially overlapping matches
            
        Returns:
            List with overlaps resolved
        """
        if len(matches) <= 1:
            return matches
        
        # Sort by priority (high > medium > low) then by score
        priority_order = {"high": 3, "medium": 2, "low": 1}
        
        sorted_matches = sorted(
            matches,
            key=lambda x: (
                priority_order.get(getattr(x, '_priority', 'medium'), 2),
                x.score
            ),
            reverse=True
        )
        
        final_matches = []
        
        for match in sorted_matches:
            # Check if this match overlaps with any accepted match
            overlaps = False
            for accepted in final_matches:
                if self._is_overlap(match, accepted):
                    overlaps = True
                    break
            
            if not overlaps:
                final_matches.append(match)
        
        return final_matches
    
    def _is_overlap(self, match1: PIIEntity, match2: PIIEntity) -> bool:
        """
        Check if two matches overlap.
        
        Args:
            match1: First match
            match2: Second match
            
        Returns:
            True if matches overlap, False otherwise
        """
        return not (match1.end <= match2.start or match2.end <= match1.start)
    
    def _apply_masks(self, text: str, entities: List[PIIEntity]) -> str:
        """
        Apply masks to detected entities.
        
        Args:
            text: Original text
            entities: Detected entities
            
        Returns:
            Masked text
        """
        # Sort by position (reverse order for replacement)
        sorted_entities = sorted(entities, key=lambda x: x.start, reverse=True)
        
        masked_text = text
        for entity in sorted_entities:
            mask = f"[{entity.pii_type}]"
            masked_text = (
                masked_text[:entity.start] + mask + masked_text[entity.end:]
            )
        
        return masked_text
