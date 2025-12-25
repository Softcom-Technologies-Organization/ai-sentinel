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
# =============================================================================

CONFLICT_GROUPS: List[ConflictGroup] = [
    # -------------------------------------------------------------------------
    # GROUP 1: NUMERIC_DOTTED - Pattern: \d+(\.\d+)+
    # Examples: 192.168.1.1, 756.1234.5678.90, 01.02.2024
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="NUMERIC_DOTTED",
        group_pattern=r"^\d+(\.\d+)+$",
        type_patterns={
            # IP: exactly 4 octets, each 0-255
            "IP_ADDRESS": r"^((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)$",
            # Swiss AVS: starts with 756, format 756.XXXX.XXXX.XX (13 digits total)
            "AVS_NUMBER": r"^756\.\d{4}\.\d{4}\.\d{2}$",
            # Date: DD.MM.YYYY or similar
            "DATE": r"^(0?[1-9]|[12]\d|3[01])\.(0?[1-9]|1[0-2])\.(\d{2}|\d{4})$",
            # Medical record: generic dotted number (fallback)
            "MEDICAL_RECORD_NUMBER": r"^\d{1,3}(\.\d{1,4}){2,}$",
        },
        fallback_priority=["IP_ADDRESS", "AVS_NUMBER", "DATE", "MEDICAL_RECORD_NUMBER"]
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
            # Serial number: manufacturer patterns
            "SERIAL_NUMBER": r"^[A-Z]{2,4}\d{6,12}$",
        },
        fallback_priority=["API_KEY", "IBAN", "PATIENT_ID", "ACCESS_TOKEN", "SERIAL_NUMBER"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 5: EMAIL_LIKE - Pattern: .+@.+
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="EMAIL_LIKE",
        group_pattern=r"^.+@.+\..+$",
        type_patterns={
            # Standard email
            "EMAIL": r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$",
            # Work email: corporate domains
            "WORK_EMAIL": r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.(com|org|net|edu|gov|corp)$",
            # Username with @ (like Twitter handles in email context)
            "USERNAME": r"^@?[a-zA-Z][a-zA-Z0-9_]{2,30}$",
            # Login: could be email-like
            "LOGIN": r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+$",
        },
        fallback_priority=["EMAIL", "WORK_EMAIL", "USERNAME", "LOGIN"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 6: URL_LIKE - Pattern: http(s)?://
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="URL_LIKE",
        group_pattern=r"^https?://",
        type_patterns={
            # API endpoint: /api/ or /v1/ in path
            "API_ENDPOINT": r"^https?://[^/]+/(api|v\d)/",
            # Social media URLs
            "SOCIAL_MEDIA_URL": r"^https?://(www\.)?(facebook|twitter|linkedin|instagram|x)\.com/",
            # Profile URL: /user/ or /profile/ or /@
            "PROFILE_URL": r"^https?://[^/]+/(user|profile|u|@)[/\?]",
            # Personal website: personal domain patterns
            "PERSONAL_WEBSITE": r"^https?://[^/]+\.(me|io|dev|blog|personal)",
            # Generic URL
            "URL": r"^https?://[^\s]+$",
            # IP in URL
            "IP_ADDRESS": r"^https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}",
            # Hostname in URL
            "HOSTNAME": r"^https?://[a-zA-Z][a-zA-Z0-9-]*(\.[a-zA-Z0-9-]+)*",
        },
        fallback_priority=["API_ENDPOINT", "SOCIAL_MEDIA_URL", "PROFILE_URL", "PERSONAL_WEBSITE", "URL", "IP_ADDRESS", "HOSTNAME"]
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
            # Mobile: typically starts with mobile prefixes
            "MOBILE_PHONE": r"^(\+\d{1,3}[\s-]?)?(07|06|09|\(0\)7|\(0\)6)\d{1,2}[\s.-]?\d{3}[\s.-]?\d{2,4}$",
            # Fax: often has fax-like patterns or extension
            "FAX_NUMBER": r"^(\+\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s.-]?\d{2,4}[\s.-]?\d{2,4}([\s-]?(ext|x|fax)[\s.-]?\d+)?$",
            # Work phone: extension patterns
            "WORK_PHONE": r"^(\+\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s.-]?\d{2,4}[\s.-]?\d{2,4}([\s-]?(ext|x)[\s.-]?\d+)?$",
        },
        fallback_priority=["PHONE_NUMBER", "MOBILE_PHONE", "FAX_NUMBER", "WORK_PHONE"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 8: PERSON_LIKE - Capitalized tokens
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="PERSON_LIKE",
        group_pattern=r"^[A-Z][a-z]+(\s+[A-Z][a-z]+)*$",
        type_patterns={
            # Full name: 2-4 capitalized words
            "FULL_NAME": r"^[A-Z][a-z]+(\s+[A-Z][a-z]+){1,3}$",
            # Person name: 1-3 words
            "PERSON_NAME": r"^[A-Z][a-z]+(\s+[A-Z][a-z]+){0,2}$",
            # Employee name: often with title
            "EMPLOYEE_NAME": r"^(Mr|Mrs|Ms|Dr|Prof)?\.?\s?[A-Z][a-z]+(\s+[A-Z][a-z]+){0,2}$",
            # Doctor name: with Dr. prefix
            "DOCTOR_NAME": r"^(Dr|Doctor)\.?\s+[A-Z][a-z]+(\s+[A-Z][a-z]+)?$",
            # First name: single word
            "FIRST_NAME": r"^[A-Z][a-z]{1,20}$",
            # Last name: single word, may have prefix
            "LAST_NAME": r"^(van|von|de|la|le)?\s?[A-Z][a-z]{1,20}$",
            # Username: might be capitalized
            "USERNAME": r"^[A-Za-z][A-Za-z0-9_]{2,20}$",
        },
        fallback_priority=["FULL_NAME", "PERSON_NAME", "EMPLOYEE_NAME", "DOCTOR_NAME", "FIRST_NAME", "LAST_NAME", "USERNAME"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 9: ADDRESS_LIKE - Number + Street keyword
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="ADDRESS_LIKE",
        group_pattern=r"^\d+\s+.*(street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|way|place|pl|court|ct)",
        type_patterns={
            # Home address: includes apartment/unit
            "HOME_ADDRESS": r"^\d+\s+[A-Za-z\s]+,?\s*(apt|unit|suite|#)?\s*\d*",
            # Mailing address: with city/state/zip
            "MAILING_ADDRESS": r"^\d+\s+[A-Za-z\s]+,\s*[A-Za-z\s]+,\s*[A-Z]{2}\s*\d{5}",
            # Generic street address
            "ADDRESS": r"^\d+\s+[A-Za-z\s]+(street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|way|place|pl|court|ct)",
            # Home location: general
            "HOME_LOCATION": r"^\d+\s+[A-Za-z\s]+",
            # Work location
            "WORK_LOCATION": r"^\d+\s+[A-Za-z\s]+(building|bldg|floor|suite|office)",
        },
        fallback_priority=["HOME_ADDRESS", "MAILING_ADDRESS", "ADDRESS", "HOME_LOCATION", "WORK_LOCATION"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 10: DATE_LIKE - YYYY-MM-DD, DD/MM/YYYY, etc.
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="DATE_LIKE",
        group_pattern=r"^(\d{1,4}[-/\.]\d{1,2}[-/\.]\d{1,4}|\d{1,2}[-/\.]\d{1,2}[-/\.]\d{2,4})$",
        type_patterns={
            # Birth date: typically historical (not future)
            "BIRTH_DATE": r"^(0?[1-9]|[12]\d|3[01])[-/\.](0?[1-9]|1[0-2])[-/\.](19|20)\d{2}$",
            # Date: any valid date
            "DATE": r"^(\d{1,4}[-/\.]\d{1,2}[-/\.]\d{1,4}|\d{1,2}[-/\.]\d{1,2}[-/\.]\d{2,4})$",
            # Timestamp: ISO format
            "TIMESTAMP": r"^\d{4}[-/\.]\d{2}[-/\.]\d{2}(T|\s)\d{2}:\d{2}",
        },
        fallback_priority=["BIRTH_DATE", "DATE", "TIMESTAMP"]
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
            # Employee ID: 5-10 digits
            "EMPLOYEE_ID": r"^\d{5,10}$",
            # Student ID: 6-12 digits
            "STUDENT_ID": r"^\d{6,12}$",
            # Customer ID: 6-15 digits
            "CUSTOMER_ID": r"^\d{6,15}$",
            # User ID: 4-12 digits
            "USER_ID": r"^\d{4,12}$",
            # Account ID: 6-15 digits
            "ACCOUNT_ID": r"^\d{6,15}$",
        },
        fallback_priority=["BANK_ACCOUNT_NUMBER", "PATIENT_ID", "EMPLOYEE_ID", "STUDENT_ID", "CUSTOMER_ID", "USER_ID", "ACCOUNT_ID"]
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
            # Access token: bearer-like
            "ACCESS_TOKEN": r"^(eyJ|Bearer\s?)[A-Za-z0-9_.-]+$",
            # Secret key: secret_ prefix or hex-like
            "SECRET_KEY": r"^(secret_|sec_)?[A-Fa-f0-9]{32,}$",
            # OAuth token
            "OAUTH_TOKEN": r"^(ya29\.|1\/\/)[A-Za-z0-9_-]+$",
            # Private key: PEM-like content
            "PRIVATE_KEY": r"^(-----BEGIN|MII)[A-Za-z0-9+/=\s]+$",
            # SSH key
            "SSH_KEY": r"^(ssh-rsa|ssh-ed25519|ecdsa-sha2)\s+[A-Za-z0-9+/=]+",
            # Refresh token
            "REFRESH_TOKEN": r"^[A-Za-z0-9_-]{32,}$",
            # Password: anything else long
            "PASSWORD": r"^.{8,}$",
            # Password hash: hex or base64
            "PASSWORD_HASH": r"^(\$2[aby]?\$|sha256\$|pbkdf2:)?[A-Za-z0-9./=+$]{32,}$",
        },
        fallback_priority=["API_KEY", "ACCESS_TOKEN", "SECRET_KEY", "OAUTH_TOKEN", "PRIVATE_KEY", "SSH_KEY", "REFRESH_TOKEN", "PASSWORD", "PASSWORD_HASH"]
    ),

    # -------------------------------------------------------------------------
    # GROUP 13: LOCATION_CODE - [A-Z]{2}\d{4} or similar
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
    # GROUP 14: MEDICAL_IDENTIFIER - Domain-specific
    # -------------------------------------------------------------------------
    ConflictGroup(
        name="MEDICAL_IDENTIFIER",
        group_pattern=r"^(756\.\d{4}\.\d{4}\.\d{2}|\d{3}\.\d{4}\.\d{4}\.\d{2})$",
        type_patterns={
            # Swiss AVS: exactly 756.XXXX.XXXX.XX
            "AVS_NUMBER": r"^756\.\d{4}\.\d{4}\.\d{2}$",
            # Health insurance: similar format but not 756
            "HEALTH_INSURANCE_NUMBER": r"^(?!756)\d{3}\.\d{4}\.\d{4}\.\d{2}$",
            # Patient ID: alphanumeric
            "PATIENT_ID": r"^(P|PAT)\d{6,10}$",
            # Medical record number
            "MEDICAL_RECORD_NUMBER": r"^(MRN|MR)?\d{6,12}$",
        },
        fallback_priority=["AVS_NUMBER", "HEALTH_INSURANCE_NUMBER", "PATIENT_ID", "MEDICAL_RECORD_NUMBER"]
    ),
]


# =============================================================================
# Category Priority for Fallback Resolution
# =============================================================================
# Higher priority categories win when no specific pattern matches

CATEGORY_PRIORITY: Dict[str, int] = {
    "FINANCIAL": 100,      # Highest - financial data is most sensitive
    "MEDICAL": 95,         # HIPAA/GDPR Art. 9 protected
    "IT": 90,              # Credentials and secrets
    "IDENTITY": 85,        # Core PII
    "CONTACT": 80,
    "DIGITAL_IDENTITY": 75,
    "PROFESSIONAL": 70,
    "LOCATION": 65,
    "LEGAL": 60,
    "ASSET": 55,
    "BIOMETRIC": 50,
    "TEMPORAL": 45,
    "RESOURCE": 40,
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

        self.logger.info(
            f"ConflictResolver initialized with {len(CONFLICT_GROUPS)} conflict groups"
        )

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
                text_preview = text[:30] + "..." if len(text) > 30 else text
                self.logger.info(
                    f"[{detection_id}] Conflict for '{text_preview}' resolved by pattern: {winner} "
                    f"(group: {group.name})"
                )
                return (winner, scores.get(winner, 0.0))

            # Multiple or no matches -> use fallback priority
            for pii_type in group.fallback_priority:
                if pii_type in detected_types:
                    text_preview = text[:30] + "..." if len(text) > 30 else text
                    self.logger.info(
                        f"[{detection_id}] Conflict for '{text_preview}' resolved by fallback priority: {pii_type} "
                        f"(group: {group.name})"
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
            type_priorities.append((pii_type, priority, score))

        # Sort by priority (desc), then by score (desc)
        type_priorities.sort(key=lambda x: (x[1], x[2]), reverse=True)

        winner_type, winner_priority, winner_score = type_priorities[0]

        text_preview = text[:30] + "..." if len(text) > 30 else text
        self.logger.info(
            f"[{detection_id}] Conflict for '{text_preview}' resolved by category priority: {winner_type} "
            f"(priority: {winner_priority})"
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
            text: Entity text
            pii_type: Resolved PII type
            score: Confidence score
            start: Start position in original text
            end: End position in original text
            source: Detection source for provenance

        Returns:
            PIIEntity object
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
