"""Cross-label denylist of technical artefacts (precision post-filter pass).

Rejects an entity whose TEXT is a recognisable machine-generated artefact
(UUID, Mongo ObjectId, MD5/SHA digests, W3C traceparent, version strings,
base64-encoded images), whatever open-vocabulary label the Ministral
detector attached to it -- those passthrough labels (``OBJECT_ID``, ``GUID``,
``TRACE_ID``...) are never registered in :mod:`registry`, so the per-type
strategies cannot see them.

Two passes, both pure and deterministic:

1. **Text patterns** -- unambiguous artefact shapes matched on the whole
   span (after conservatively stripping a keyword-bearing context prefix
   such as ``"MongoDB ObjectId "``).
2. **Label+shape** -- shapes too weak to reject on text alone (short hex
   digests, build numbers) are rejected only when the detector itself
   labelled the span with an intrinsically-technical label (``HASH``,
   ``BUILD_NUMBER``, ``RELEASE_VERSION``...).

Golden rule (fail-open absolute): ``keep=False`` only on a positive,
complete match with no carve-out. Carve-outs that always win:

- credential labels (a UUID or hex blob can be a real token / key),
- JWTs and known secret prefixes (``sk_live_``, ``ghp_``, ``AKIA``...),
- values parsing as IPv4 or a plausible dotted date (version motif),
- ``0x``-prefixed hex (Ethereum-style wallet addresses are financial PII),
- any composite span whose context prefix carries digits or no technical
  keyword (it may embed real PII we refuse to judge).
"""

from __future__ import annotations

import ipaddress
import re

from pii_detector.infrastructure.postfilter.postfilter_strategy import (
    PASS,
    PostfilterVerdict,
)

# ---------------------------------------------------------------------------
# Carve-outs
# ---------------------------------------------------------------------------

# A credential value can have any shape; only the context-aware judge can reject it.
CREDENTIAL_LABELS = frozenset(
    {
        "API_KEY",
        "CLIENT_SECRET",
        "PASSWORD",
        "PASSPHRASE",
        "SECRET",
        "SECRET_KEY",
        "ACCESS_TOKEN",
        "HTTP_COOKIE",
        "COOKIE",
        "TOKEN",
        "JWT_TOKEN",
        "AUTH_TOKEN",
        "REFRESH_TOKEN",
        "SESSION_TOKEN",
        "CREDENTIAL",
        "CREDENTIALS",
        "PRIVATE_KEY",
        "AWS_ACCESS_KEY",
        "AWS_KEY",
        "CONNECTION_STRING",
        "GOOGLE_API_KEY",
        "GITHUB_TOKEN",
    }
)

# Substring markers of well-known secret formats: their presence anywhere in
# the span keeps the entity, whatever pattern also matched.
_SECRET_MARKERS = (
    "sk_live_",
    "sk_test_",
    "rk_live_",
    "rk_test_",
    "sk-",
    "ghp_",
    "gho_",
    "ghu_",
    "ghs_",
    "ghr_",
    "github_pat_",
    "glpat-",
    "AKIA",
    "ASIA",
    "AIza",
    "ya29.",
    "npm_",
    "dckr_pat_",
    "SG.",
    "AGE-SECRET-KEY-",
    "-----BEGIN",
)

_SLACK_TOKEN_RE = re.compile(r"xox[a-z]-")
_JWT_RE = re.compile(r"eyJ[A-Za-z0-9_-]+")

_IP_LABELS = frozenset(
    {"IP", "IP_ADDRESS", "IPV4", "IPV4_ADDRESS", "IPV6", "IPV6_ADDRESS"}
)

# ---------------------------------------------------------------------------
# Context-prefix stripping
# ---------------------------------------------------------------------------

# A multi-token span is only judged when everything before the last token is
# digit-free AND names the artefact (e.g. "MongoDB ObjectId <hex>"). A prefix
# such as "David " carries no technical keyword -> the span is kept as-is.
_TECH_KEYWORDS = frozenset(
    {
        "id",
        "guid",
        "uuid",
        "oid",
        "objectid",
        "object",
        "trace",
        "traceid",
        "traceparent",
        "span",
        "correlation",
        "correlationid",
        "korrelation",
        "korrelations",
        "hash",
        "sha",
        "sha1",
        "sha256",
        "md5",
        "digest",
        "checksum",
        "commit",
        "build",
        "mongo",
        "mongodb",
        "document",
        "doc",
        "session",
        "sessionid",
        "request",
        "requestid",
        "version",
        "release",
    }
)

_TOKEN_SPLIT_RE = re.compile(r"[^0-9A-Za-z]+")

# ---------------------------------------------------------------------------
# Text patterns (pass 1)
# ---------------------------------------------------------------------------

_UUID_RE = re.compile(
    r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
    r"-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)
_TRACEPARENT_RE = re.compile(
    r"[0-9a-fA-F]{2}-[0-9a-fA-F]{32}-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}"
)
_PURE_HEX_RE = re.compile(r"[0-9a-fA-F]+")
_HEX_ARTIFACT_NAMES = {
    24: "mongodb-objectid",
    32: "md5-digest",
    40: "sha1-digest",
    64: "sha256-digest",
}
_VERSION_RE = re.compile(r"v?\d+\.\d+\.\d+(?:[.-][0-9A-Za-z]+)?")
_DOTTED_AVS_RE = re.compile(r"756\.\d{4}\.\d{4}\.\d{2}", re.ASCII)
_DOTTED_DATE_RE = re.compile(r"(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})")
_YEAR_FIRST_DOTTED_DATE_RE = re.compile(r"\d{4}\.(\d{1,2})\.(\d{1,2})")
_BASE64_IMAGE_SIGNATURES = ("iVBORw0KGgo", "/9j/", "R0lGOD")  # PNG/JPEG/GIF
_DATA_IMAGE_URI_RE = re.compile(r"data:image/[0-9A-Za-z.+-]+;base64,")
# Below this length a signature match is just the signature itself, not an
# actual encoded blob.
_MIN_BASE64_BLOB_LEN = 24

# ---------------------------------------------------------------------------
# Label+shape rules (pass 2)
# ---------------------------------------------------------------------------

_HASH_LABELS = frozenset(
    {
        "HASH",
        "BUILD_SHA",
        "COMMIT",
        "COMMIT_SHA",
        "COMMIT_HASH",
        "GIT_SHA",
        "GIT_COMMIT",
        "SHA",
        "SHA1",
        "SHA256",
        "MD5",
        "CHECKSUM",
        "DIGEST",
    }
)
_BUILD_LABELS = frozenset({"BUILD_NUMBER", "BUILD_ID"})
_VERSION_LABELS = frozenset(
    {
        "VERSION",
        "RELEASE",
        "RELEASE_VERSION",
        "APPLICATION_VERSION",
        "APP_VERSION",
        "SOFTWARE_VERSION",
        "FIRMWARE_VERSION",
        "BUILD_VERSION",
        "SEMVER",
    }
)

_HEX_DIGEST_SHAPE_RE = re.compile(r"(?:[A-Za-z]+[-_])*[0-9a-fA-F]{6,64}")
_BUILD_NUMBER_SHAPE_RE = re.compile(r"(?:[A-Za-z]+[-_])*\d{4,}(?:[-._]\d+)*")
_VERSION_SHAPE_RE = re.compile(
    r"(?:[A-Za-z]+[-_])*v?\d+(?:\.\d+){1,3}(?:[-.][0-9A-Za-z]+)?"
)


def _normalize_pii_type(pii_type) -> str:
    if pii_type is None or pii_type == "":
        return "UNKNOWN"
    return str(pii_type).upper().replace(" ", "_").replace("-", "_")


def has_credential_marker(span: str) -> bool:
    if any(marker in span for marker in _SECRET_MARKERS):
        return True
    # JWT-like values start with the base64 of '{"' -- anchored so a random
    # "eyJ" inside an image blob does not neutralise the base64 motif.
    return bool(_SLACK_TOKEN_RE.search(span) or _JWT_RE.match(span))


def _split_context_prefix(span: str):
    """Return the judgeable core of ``span``, or ``None`` to refuse.

    Single-token spans are judged whole. For multi-token spans the last
    token is the candidate core, accepted only when the leading context is
    digit-free and contains a technical keyword -- otherwise the composite
    span may embed real PII and is refused (fail-open).
    """
    parts = span.split()
    if len(parts) == 1:
        return span
    context = " ".join(parts[:-1])
    if any(ch.isdigit() for ch in context):
        return None
    tokens = {t for t in _TOKEN_SPLIT_RE.split(context.lower()) if t}
    if not tokens & _TECH_KEYWORDS:
        return None
    return parts[-1]


def _is_ipv4(value: str) -> bool:
    try:
        ipaddress.IPv4Address(value)
        return True
    except ValueError:
        return False


def _is_plausible_dotted_date(value: str) -> bool:
    match = _DOTTED_DATE_RE.fullmatch(value)
    if match:
        first, second = int(match.group(1)), int(match.group(2))
        # Accept both d.m.y and m.d.y readings -- generosity is fail-open.
        return 1 <= first <= 31 and 1 <= second <= 31
    match = _YEAR_FIRST_DOTTED_DATE_RE.fullmatch(value)
    if match:
        # Year-first (y.m.d) reading: 2024.05.16 is a date, 2024.8.820 is a
        # CalVer (last component out of day range).
        month, day = int(match.group(1)), int(match.group(2))
        return 1 <= month <= 12 and 1 <= day <= 31
    return False


def _evaluate_text_patterns(core: str, pii_type: str) -> PostfilterVerdict:
    if _UUID_RE.fullmatch(core):
        return PostfilterVerdict(False, "technical artifact: uuid")
    if _TRACEPARENT_RE.fullmatch(core):
        return PostfilterVerdict(False, "technical artifact: w3c traceparent")
    if _PURE_HEX_RE.fullmatch(core) and len(core) in _HEX_ARTIFACT_NAMES:
        name = _HEX_ARTIFACT_NAMES[len(core)]
        return PostfilterVerdict(
            False, f"technical artifact: {name} (hex-{len(core)})"
        )
    if _VERSION_RE.fullmatch(core):
        if (
            pii_type in _IP_LABELS
            or _is_ipv4(core)
            or _is_plausible_dotted_date(core)
            or _DOTTED_AVS_RE.fullmatch(core)
        ):
            return PASS
        return PostfilterVerdict(False, "technical artifact: version string")
    if _DATA_IMAGE_URI_RE.match(core) or (
        len(core) >= _MIN_BASE64_BLOB_LEN
        and core.startswith(_BASE64_IMAGE_SIGNATURES)
    ):
        return PostfilterVerdict(
            False, "technical artifact: base64-encoded image"
        )
    return PASS


def _evaluate_label_shape(core: str, pii_type: str) -> PostfilterVerdict:
    if pii_type in _HASH_LABELS and _HEX_DIGEST_SHAPE_RE.fullmatch(core):
        return PostfilterVerdict(False, "label+shape: hex digest")
    if pii_type in _BUILD_LABELS and _BUILD_NUMBER_SHAPE_RE.fullmatch(core):
        return PostfilterVerdict(False, "label+shape: build number")
    if (
        pii_type in _VERSION_LABELS
        and _VERSION_SHAPE_RE.fullmatch(core)
        and not _is_ipv4(core)
        and not _is_plausible_dotted_date(core)
    ):
        return PostfilterVerdict(False, "label+shape: version string")
    return PASS


def evaluate(value, pii_type) -> PostfilterVerdict:
    """Return the deterministic keep/reject verdict for one entity.

    Args:
        value: The entity text (any non-``str`` fails open).
        pii_type: The entity label; normalised to UPPER_SNAKE internally.
    """
    if not isinstance(value, str):
        return PASS
    span = value.strip()
    if not span:
        return PASS
    normalized_type = _normalize_pii_type(pii_type)
    if normalized_type in CREDENTIAL_LABELS or has_credential_marker(span):
        return PASS
    core = _split_context_prefix(span)
    if core is None:
        return PASS
    verdict = _evaluate_text_patterns(core, normalized_type)
    if not verdict.keep:
        return verdict
    return _evaluate_label_shape(core, normalized_type)


__all__ = ["CREDENTIAL_LABELS", "evaluate", "has_credential_marker"]
