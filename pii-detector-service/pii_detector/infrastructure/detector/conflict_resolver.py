"""
Conflict Resolver for Multi-Pass GLiNER Detection.

This module provides deterministic conflict resolution when multiple PII types
are detected for the same text span across different detection passes.

Resolution Strategy:
    1. Match conflict group by group pattern
    2. Test type-specific patterns to identify exact type
    3. If exactly one pattern matches -> use that type
    4. If multiple/none match -> use fallback priority
    5. Final fallback: category risk priority

Design Principles:
    - Regex-only validation (no checksums like Luhn/IBAN)
    - Deterministic resolution (reproducible results)
    - Type-specific patterns for accurate differentiation

CONSOLIDATED VERSION: 44 PII types across 7 categories
"""

import logging
import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple

from pii_detector.domain.entity.pii_entity import PIIEntity


logger = logging.getLogger(__name__)


# =============================================================================
# Conflict Group Definition
# =============================================================================

@dataclass
class ConflictGroup:
    """
    Defines a conflict group with type-specific resolution patterns.

    Attributes:
        name: Group identifier (e.g., "NUMERIC_DOTTED")
        group_pattern: Regex to identify if text belongs to this group
        type_patterns: Dict mapping PII type to its specific validation regex
        fallback_priority: Ordered list for when patterns don't resolve conflict
    """
    name: str
    group_pattern: str
    type_patterns: Dict[str, str]  # pii_type -> specific regex pattern
    fallback_priority: List[str]


# =============================================================================
# Conflict Groups with Type-Specific Patterns
# CONSOLIDATED: Only includes the 44 active PII types
# =============================================================================

CONFLICT_GROUPS: List[ConflictGroup] = [
    # -------------------------------------------------------------------------
    # GROUP 1: NUMERIC_DOTTED - Pattern: \d+(\.\d+)+
    # Examples: 192.168.1.1, 756.1234.5678.90
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="NUMERIC_DOTTED",
        group_pattern=r"^\d+(\.\d+)+$",
        type_patterns={
            # IP: exactly 4 octets, each 0-255
            "IP_ADDRESS": r"^((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)$",
            # Swiss AVS: starts with 756, format 756.XXXX.XXXX.XX (13 digits total)
            "AVS_NUMBER": r"^756\.\d{4}\.\d{4}\.\d{2}$",
            # Medical record: generic dotted number
            "MEDICAL_RECORD_NUMBER": r"^\d{1,3}(\.\d{1,4}){2,}$",
        },
        fallback_priority=["IP_ADDRESS", "AVS_NUMBER", "MEDICAL_RECORD_NUMBER"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 2: NUMERIC_DASHED - Pattern: \d+(-\d+)+
    # Examples: 123-45-6789, 41-79-123-4567
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="NUMERIC_DASHED",
        group_pattern=r"^\d+(-\d+)+$",
        type_patterns={
            # US SSN: exactly XXX-XX-XXXX
            "SSN": r"^\d{3}-\d{2}-\d{4}$",
            # National ID: various formats with dashes
            "NATIONAL_ID": r"^\d{2,3}-\d{2,4}-\d{2,6}$",
            # Phone: international or local with dashes
            "PHONE_NUMBER": r"^(\+?\d{1,3}-)?\d{2,4}(-\d{2,4}){1,3}$",
            # Bank account: longer sequences
            "BANK_ACCOUNT_NUMBER": r"^\d{4}(-\d{4}){2,4}$",
        },
        fallback_priority=["SSN", "NATIONAL_ID", "PHONE_NUMBER", "BANK_ACCOUNT_NUMBER"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 3: NUMERIC_SPACED - Pattern: \d{2,}(\s\d{2,})+
    # Examples: 4532 1234 5678 9012, CH93 0076 2011 6238 5295 7
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="NUMERIC_SPACED",
        group_pattern=r"^\d{2,}(\s\d{2,})+$",
        type_patterns={
            # Credit card: 4 groups of 4 digits (16 total, spaces removed)
            "CREDIT_CARD_NUMBER": r"^(\d{4}\s){3}\d{4}$",
            # IBAN: 2 letters + 2 digits + alphanumeric (spaces allowed)
            "IBAN": r"^[A-Z]{2}\d{2}(\s?[A-Z0-9]{4}){2,7}\s?[A-Z0-9]{1,4}$",
            # Phone with spaces
            "PHONE_NUMBER": r"^(\+?\d{1,3}\s)?\d{2,4}(\s\d{2,4}){1,3}$",
            # Bank account with spaces
            "BANK_ACCOUNT_NUMBER": r"^\d{2,6}(\s\d{2,6}){1,4}$",
        },
        fallback_priority=["CREDIT_CARD_NUMBER", "IBAN", "PHONE_NUMBER", "BANK_ACCOUNT_NUMBER"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 4: LONG_ALPHANUMERIC - Pattern: [A-Z0-9]{10,}
    # Examples: CH9300762011623852957, sk_live_abc123def456
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="LONG_ALPHANUMERIC",
        group_pattern=r"^[A-Za-z0-9]{10,}$",
        type_patterns={
            # API Key: common prefixes or patterns
            "API_KEY": r"^(sk_|pk_|api_|key_|token_)[A-Za-z0-9]{16,}$",
            # IBAN without spaces: 2 letters + 2 digits + alphanumeric
            "IBAN": r"^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$",
            # Patient ID: hospital prefix + numbers
            "PATIENT_ID": r"^(PAT|PT|P)\d{6,12}$",
            # Access token: JWT-like or long random
            "ACCESS_TOKEN": r"^(eyJ|Bearer\s)?[A-Za-z0-9_-]{20,}$",
        },
        fallback_priority=["API_KEY", "IBAN", "PATIENT_ID", "ACCESS_TOKEN"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 5: EMAIL_LIKE - Pattern: .+@.+
    # Handles all email formats including internal domains
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="EMAIL_LIKE",
        group_pattern=r"^.+@.+$",
        type_patterns={
            # Email: broad pattern including internal domains (.local, .internal)
            "EMAIL": r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$",
            # Username with @ (like Twitter handles)
            "USERNAME": r"^@?[a-zA-Z][a-zA-Z0-9_]{2,30}$",
        },
        fallback_priority=["EMAIL", "USERNAME"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 6: URL_LIKE - Pattern: http(s)?://
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="URL_LIKE",
        group_pattern=r"^https?://",
        type_patterns={
            # Generic URL
            "URL": r"^https?://[^\s]+$",
            # IP in URL
            "IP_ADDRESS": r"^https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}",
            # Hostname in URL
            "HOSTNAME": r"^https?://[a-zA-Z][a-zA-Z0-9-]*(\.[a-zA-Z0-9-]+)*",
        },
        fallback_priority=["URL", "IP_ADDRESS", "HOSTNAME"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 7: PHONE_LIKE - Pattern: \+?\d[\d\s\-().]{6,}
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="PHONE_LIKE",
        group_pattern=r"^\+?\d[\d\s\-().]{6,}$",
        type_patterns={
            # Phone: starts with + or digit, common formats
            "PHONE_NUMBER": r"^(\+\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s.-]?\d{2,4}[\s.-]?\d{2,4}$",
        },
        fallback_priority=["PHONE_NUMBER"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 8: PERSON_LIKE - Capitalized tokens
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="PERSON_LIKE",
        group_pattern=r"^[A-Z][a-z]+(\s+[A-Z][a-z]+)*$",
        type_patterns={
            # Person name: 1-3 capitalized words
            "PERSON_NAME": r"^[A-Z][a-z]+(\s+[A-Z][a-z]+){0,2}$",
            # Username: might be capitalized
            "USERNAME": r"^[A-Za-z][A-Za-z0-9_]{2,20}$",
        },
        fallback_priority=["PERSON_NAME", "USERNAME"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 9: ADDRESS_LIKE - Number + Street keyword
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="ADDRESS_LIKE",
        group_pattern=r"^\d+\s+.*(street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|way|place|pl|court|ct|rue|chemin|route)",
        type_patterns={
            # Generic address
            "ADDRESS": r"^\d+\s+[A-Za-z\s]+(street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|way|place|pl|court|ct|rue|chemin|route)",
        },
        fallback_priority=["ADDRESS"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 10: DATE_LIKE - YYYY-MM-DD, DD/MM/YYYY, etc.
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="DATE_LIKE",
        group_pattern=r"^(\d{1,4}[-/\.]\d{1,2}[-/\.]\d{1,4}|\d{1,2}[-/\.]\d{1,2}[-/\.]\d{2,4})$",
        type_patterns={
            # Date of birth: typically historical
            "DATE_OF_BIRTH": r"^(0?[1-9]|[12]\d|3[01])[-/\.](0?[1-9]|1[0-2])[-/\.](19|20)\d{2}$",
        },
        fallback_priority=["DATE_OF_BIRTH"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 11: ACCOUNT_LIKE - Digits + fixed length
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="ACCOUNT_LIKE",
        group_pattern=r"^\d{6,20}$",
        type_patterns={
            # Bank account: typically 8-20 digits
            "BANK_ACCOUNT_NUMBER": r"^\d{8,20}$",
            # Patient ID: 6-12 digits
            "PATIENT_ID": r"^\d{6,12}$",
            # Account ID: 6-15 digits
            "ACCOUNT_ID": r"^\d{6,15}$",
        },
        fallback_priority=["BANK_ACCOUNT_NUMBER", "PATIENT_ID", "ACCOUNT_ID"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 12: CREDENTIAL_LIKE - Random-looking strings
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="CREDENTIAL_LIKE",
        group_pattern=r"^[A-Za-z0-9+/=_\-]{16,}$",
        type_patterns={
            # API Key: common prefixes
            "API_KEY": r"^(sk_|pk_|api_|key_|AKIA)[A-Za-z0-9_-]{16,}$",
            # Access token: bearer-like or JWT
            "ACCESS_TOKEN": r"^(eyJ|Bearer\s?)[A-Za-z0-9_.-]+$",
            # Secret key: secret_ prefix or hex-like
            "SECRET_KEY": r"^(secret_|sec_)?[A-Fa-f0-9]{32,}$",
            # Password: anything else long
            "PASSWORD": r"^.{8,}$",
        },
        fallback_priority=["API_KEY", "ACCESS_TOKEN", "SECRET_KEY", "PASSWORD"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 13: LOCATION_CODE - Postal codes, license plates
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="LOCATION_CODE",
        group_pattern=r"^[A-Z]{1,3}[\s-]?\d{2,6}$",
        type_patterns={
            # Postal code: various formats
            "POSTAL_CODE": r"^([A-Z]{1,2}\d{1,2}\s?\d[A-Z]{2}|\d{5}(-\d{4})?|[A-Z]\d[A-Z]\s?\d[A-Z]\d)$",
            # License plate: country-specific
            "LICENSE_PLATE": r"^[A-Z]{1,3}[\s-]?\d{1,4}[\s-]?[A-Z]{0,3}$",
        },
        fallback_priority=["POSTAL_CODE", "LICENSE_PLATE"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 14: MEDICAL_IDENTIFIER - Swiss AVS, health IDs
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="MEDICAL_IDENTIFIER",
        group_pattern=r"^(756\.\d{4}\.\d{4}\.\d{2}|\d{3}\.\d{4}\.\d{4}\.\d{2})$",
        type_patterns={
            # Swiss AVS: exactly 756.XXXX.XXXX.XX
            "AVS_NUMBER": r"^756\.\d{4}\.\d{4}\.\d{2}$",
            # Health insurance: similar format but not 756
            "HEALTH_INSURANCE_NUMBER": r"^(?!756)\d{3}\.\d{4}\.\d{4}\.\d{2}$",
            # Medical record number
            "MEDICAL_RECORD_NUMBER": r"^(MRN|MR)?\d{6,12}$",
        },
        fallback_priority=["AVS_NUMBER", "HEALTH_INSURANCE_NUMBER", "MEDICAL_RECORD_NUMBER"]
    ),
]


# =============================================================================
# Category Priority for Fallback Resolution
# CONSOLIDATED: 7 categories (down from 13)
# =============================================================================

CATEGORY_PRIORITY: Dict[str, int] = {
    "FINANCIAL": 100,       # Highest - financial data is most sensitive
    "MEDICAL": 95,          # HIPAA/GDPR Art. 9 protected
    "IT_CREDENTIALS": 90,   # Credentials and secrets
    "IDENTITY": 85,         # Core PII
    "CONTACT": 80,          # Contact information
    "DIGITAL": 75,          # Online identifiers
    "LEGAL_ASSET": 70,      # Legal + property
}


# =============================================================================
# Conflict Resolver Class
# =============================================================================

class ConflictResolver:
    """
    Resolves conflicts when multiple PII types are detected for the same span.

    Resolution Strategy:
        1. Find applicable conflict group (by group_pattern)
        2. Test type-specific patterns against the text
        3. If exactly one type pattern matches -> winner
        4. If multiple match -> use fallback_priority
        5. If none match -> use fallback_priority
        6. Final fallback -> category priority

    Usage:
        resolver = ConflictResolver(pii_type_to_category_mapping)
        winner = resolver.resolve(text, detected_types_with_scores)
    """

    def __init__(self, pii_type_to_category: Optional[Dict[str, str]] = None):
        """
        Initialize the conflict resolver.

        Args:
            pii_type_to_category: Mapping from PII type to its category.
                                  Loaded from database if not provided.
        """
        self.pii_type_to_category = pii_type_to_category or {}
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

        # Pre-compile all patterns for efficiency
        self._compiled_group_patterns: Dict[str, re.Pattern] = {}
        self._compiled_type_patterns: Dict[str, Dict[str, re.Pattern]] = {}

        for group in CONFLICT_GROUPS:
            self._compiled_group_patterns[group.name] = re.compile(
                group.group_pattern, re.IGNORECASE
            )
            self._compiled_type_patterns[group.name] = {
                pii_type: re.compile(pattern, re.IGNORECASE)
                for pii_type, pattern in group.type_patterns.items()
            }

        # Conflict statistics for monitoring
        self._conflict_stats: Dict[str, int] = {
            "total_conflicts": 0,
            "resolved_by_pattern": 0,
            "resolved_by_fallback": 0,
            "resolved_by_category": 0,
        }

        self.logger.info(
            f"ConflictResolver initialized with {len(CONFLICT_GROUPS)} conflict groups"
        )

    def _log_conflict_resolution(
        self,
        detection_id: str,
        text: str,
        detected_labels: List[Tuple[str, float]],
        winner: str,
        losers: List[str],
        resolution_method: str,
        group_name: Optional[str] = None
    ) -> None:
        """
        Log detailed conflict resolution information.

        Args:
            detection_id: Unique ID for this detection run
            text: The conflicting text span
            detected_labels: All detected (type, score) pairs
            winner: The winning PII type
            losers: List of discarded PII types
            resolution_method: How the conflict was resolved
            group_name: Name of conflict group if applicable
        """
        text_preview = text[:40] + "..." if len(text) > 40 else text

        # Build labels summary with scores
        labels_summary = ", ".join(
            f"{t}({s:.2f})" for t, s in sorted(detected_labels, key=lambda x: -x[1])
        )

        # Log the resolution
        self.logger.info(
            f"[{detection_id}] CONFLICT RESOLVED | "
            f"text='{text_preview}' | "
            f"candidates=[{labels_summary}] | "
            f"winner={winner} | "
            f"discarded=[{', '.join(losers)}] | "
            f"method={resolution_method}" +
            (f" | group={group_name}" if group_name else "")
        )

        # Update stats
        self._conflict_stats["total_conflicts"] += 1
        if resolution_method == "pattern_match":
            self._conflict_stats["resolved_by_pattern"] += 1
        elif resolution_method == "fallback_priority":
            self._conflict_stats["resolved_by_fallback"] += 1
        elif resolution_method == "category_priority":
            self._conflict_stats["resolved_by_category"] += 1

    def get_conflict_stats(self) -> Dict[str, int]:
        """Return current conflict resolution statistics."""
        return self._conflict_stats.copy()

    def reset_conflict_stats(self) -> None:
        """Reset conflict statistics."""
        for key in self._conflict_stats:
            self._conflict_stats[key] = 0

    def resolve(
        self,
        text: str,
        detected_labels: List[Tuple[str, float]],
        detection_id: str = ""
    ) -> Optional[Tuple[str, float]]:
        """
        Resolve conflict for a span with multiple detected labels.

        Args:
            text: The text content of the span
            detected_labels: List of (pii_type, score) tuples
            detection_id: Logging ID for traceability

        Returns:
            Tuple of (winning_pii_type, score) or None if no resolution
        """
        if not detected_labels:
            return None

        if len(detected_labels) == 1:
            return detected_labels[0]

        detected_types = {label for label, _ in detected_labels}
        scores = {label: score for label, score in detected_labels}

        # Try pattern-based resolution
        for group in CONFLICT_GROUPS:
            group_pattern = self._compiled_group_patterns[group.name]

            # Check if text matches this group's pattern
            if not group_pattern.match(text):
                continue

            # Check if any detected types belong to this group
            relevant_types = detected_types & set(group.type_patterns.keys())
            if not relevant_types:
                continue

            self.logger.debug(
                f"[{detection_id}] Matched conflict group: {group.name}"
            )

            # Test each type-specific pattern
            matching_types = []
            for pii_type in relevant_types:
                if pii_type in self._compiled_type_patterns[group.name]:
                    type_pattern = self._compiled_type_patterns[group.name][pii_type]
                    if type_pattern.match(text):
                        matching_types.append(pii_type)
                        self.logger.debug(
                            f"[{detection_id}] Type pattern matched: {pii_type}"
                        )

            # Exactly one match -> winner
            if len(matching_types) == 1:
                winner = matching_types[0]
                losers = [t for t in detected_types if t != winner]
                self._log_conflict_resolution(
                    detection_id, text, detected_labels,
                    winner, losers, "pattern_match", group.name
                )
                return (winner, scores.get(winner, 0.0))

            # Multiple or no matches -> use fallback priority
            for pii_type in group.fallback_priority:
                if pii_type in detected_types:
                    losers = [t for t in detected_types if t != pii_type]
                    self._log_conflict_resolution(
                        detection_id, text, detected_labels,
                        pii_type, losers, "fallback_priority", group.name
                    )
                    return (pii_type, scores.get(pii_type, 0.0))

        # No conflict group matched -> use category priority
        return self._resolve_by_category_priority(text, detected_labels, detection_id)

    def _resolve_by_category_priority(
        self,
        text: str,
        detected_labels: List[Tuple[str, float]],
        detection_id: str
    ) -> Optional[Tuple[str, float]]:
        """
        Fallback resolution using category risk priority.

        Higher-risk categories (FINANCIAL, MEDICAL) win over lower ones.

        Args:
            text: The text content of the span
            detected_labels: List of (pii_type, score) tuples
            detection_id: Logging ID

        Returns:
            Tuple of (winning_pii_type, score)
        """
        # Score each type by category priority
        type_priorities = []
        for pii_type, score in detected_labels:
            category = self.pii_type_to_category.get(pii_type, "")
            priority = CATEGORY_PRIORITY.get(category, 0)
            type_priorities.append((pii_type, priority, score, category))

        # Sort by priority (desc), then by score (desc)
        type_priorities.sort(key=lambda x: (x[1], x[2]), reverse=True)

        winner_type, winner_priority, winner_score, winner_category = type_priorities[0]
        losers = [t for t, _, _, _ in type_priorities[1:]]

        self._log_conflict_resolution(
            detection_id, text, detected_labels,
            winner_type, losers, "category_priority"
        )

        # Also log the category reasoning
        self.logger.debug(
            f"[{detection_id}] Category priority details: "
            f"{winner_type} ({winner_category}={winner_priority}) beat "
            f"{[(t, c, p) for t, p, _, c in type_priorities[1:]]}"
        )

        return (winner_type, winner_score)

    def build_pii_entity(
        self,
        text: str,
        pii_type: str,
        score: float,
        start: int,
        end: int,
        source: str = "GLINER_MULTIPASS"
    ) -> PIIEntity:
        """
        Build a PIIEntity from resolved conflict.

        Args:
            text: The PII text content
            pii_type: Resolved PII type
            score: Confidence score
            start: Start offset in original text
            end: End offset in original text
            source: Detection source identifier

        Returns:
            PIIEntity with all fields populated
        """
        entity = PIIEntity(
            text=text,
            pii_type=pii_type,
            type_label=pii_type,
            start=start,
            end=end,
            score=score
        )
        entity.source = source
        return entity
