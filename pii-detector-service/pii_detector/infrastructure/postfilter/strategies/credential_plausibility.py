"""Credential plausibility strategy for PASSWORD / API_KEY / SECRET labels.

The Ministral detector routinely tags credential *mentions* as credential
*values*: the literal keyword (``password``, ``mdp``, ``mot de passe``), a
configuration property key (``spring.datasource.password``), a placeholder
(``your_password_here``, ``ZZZZ``) or a whole natural-language sentence
about passwords. This strategy rejects those four false-positive classes
while keeping every plausible real secret (recall-first, measured on the
2026-07 Confluence scan corpus: 134 PASSWORD findings, ~129 mentions vs ~5
real secrets).

Rule ladder -- keep-guards first, then reject rules on positive lexical
evidence only, fail-open otherwise:

- K0: known secret-format markers (``sk_live_``, ``AKIA``, JWT...) via
  :func:`technical_artifact_denylist.has_credential_marker` -> keep.
- K1: single token with Shannon entropy >= 3.7 over the base64 charset
  (detect-secrets scorer) -> keep. Calibrated on the scan corpus: the real
  secrets score 3.75+ while the worst single-token mention scores 3.52.
- R1: the value IS a credential keyword (fr/en/de literals).
- R2: dotted/hyphenated configuration property key whose leaf segment names
  a credential (``password``, ``salt``...). ``key``/``token`` leaves are
  deliberately NOT rejected (a Google API key ends in ``-key``-like tails).
- R3: placeholders -- ``${...}``/``{{...}}``/``<...>`` templates
  (detect-secrets ``is_templated_secret``), a single repeated character
  (``ZZZZ``), an assignment whose right-hand side is masked or templated
  (``webservice.password=*****``, ``security.user.password=<PASSWORD>``),
  or a purely-alphabetic span embedding a credential word
  (``mySafePassword``). One digit or symbol makes the span a keeper.
- K2: assignment whose right-hand side is a single digit-bearing token
  (``webservice.password = sU59z7Uq``, ``MDP = Password1234``) -> keep:
  the span embeds a plausible real value, R4 must not judge it.
- R4: multi-token span mentioning a password word (``mot de passe``,
  ``keepass``...). ``token``/``key``/``secret`` are excluded here so
  ``Bearer ya29....`` survives.
- R5: sentence net -- >= 4 whitespace tokens and >= 2 distinct fr/en
  stopwords. A spaced passphrase (``correct horse battery staple``) has no
  stopword and survives.
- R6: single purely-alphabetic dictionary word, zxcvbn score <= 1. The
  alphabetic-only constraint keeps weak-but-real corporate passwords
  (``Geneva2024!``, ``IN_Eureka53030``) out of reach.

Accepted trade-offs (documented, deliberate): a real passphrase written as a
stopword-bearing sentence is rejected by R5; a real password equal to the
literal ``secret`` is rejected by R1; ``monMotDePasse`` is kept (R3 only
knows atomic credential words, conservative).
"""

from __future__ import annotations

import re

from detect_secrets.filters.heuristic import is_templated_secret
from detect_secrets.plugins.high_entropy_strings import Base64HighEntropyString
from zxcvbn import zxcvbn

from pii_detector.infrastructure.postfilter.postfilter_strategy import (
    PASS,
    PostfilterVerdict,
)
from pii_detector.infrastructure.postfilter.technical_artifact_denylist import (
    CREDENTIAL_LABELS,
    has_credential_marker,
)

# Registry keys served by this strategy: every label the denylist carves out
# as "credential" plus PASSPHRASE (same semantics, not in the carve-out set).
CREDENTIAL_PII_TYPES = frozenset(CREDENTIAL_LABELS | {"PASSPHRASE"})

_HIGH_ENTROPY_KEEP_THRESHOLD = 3.7
_ENTROPY_SCORER = Base64HighEntropyString(limit=_HIGH_ENTROPY_KEEP_THRESHOLD)

# R6 guardrail: zxcvbn is O(n^2)-ish, and no real-world password is a pure
# alphabetic run this long anyway.
_MAX_ZXCVBN_LEN = 72

_KEYWORD_LITERALS = frozenset(
    {
        "password",
        "passwords",
        "passwort",
        "passwd",
        "pwd",
        "pw",
        "mdp",
        "mot de passe",
        "mot de passes",
        "mots de passe",
        "mots de passes",
        "passphrase",
        "secret",
        "secrets",
        "key",
        "keys",
        "token",
        "tokens",
        "api key",
        "apikey",
        "credential",
        "credentials",
        "otp",
        "pw change",
        "tbd",
        "tba",
        "todo",
    }
)

_CONFIG_KEY_SHAPE_RE = re.compile(
    r"^[A-Za-z][A-Za-z_]*(?:[.\-][A-Za-z][A-Za-z_]*)+$"
)
_CONFIG_KEY_LEAVES = frozenset(
    {
        "password",
        "passwd",
        "pwd",
        "passwort",
        "passphrase",
        "secret",
        "salt",
        "credentials",
    }
)

_MASKED_ASSIGNMENT_RE = re.compile(r"[=:]\s*(?:\*{3,}|<[^<>]+>)\s*$")
_PLAUSIBLE_ASSIGNMENT_RE = re.compile(r"^\S{1,64}\s*[=:]\s*(\S{1,64})$")

_ALPHA_SPAN_RE = re.compile(r"^[A-Za-z]+(?:[_\-][A-Za-z]+)*$")
_CAMEL_TOKEN_RE = re.compile(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])")
_PLACEHOLDER_WORDS = frozenset(
    {"password", "passwort", "passphrase", "mdp", "secret"}
)

# R4 deliberately omits token/key/secret: "Bearer ya29..." must survive.
_PASSWORD_MENTION_RE = re.compile(
    r"\b(?:mots?(?: de)? passes?|passwords?|passwort|passphrase|mdp"
    r"|keepass|keypass)\b"
)

_STOPWORDS = frozenset(
    {
        # French
        "le", "la", "les", "un", "une", "des", "de", "du", "d", "l",
        "et", "ou", "au", "aux", "pour", "dans", "sur", "avec", "est",
        "sont", "ce", "cette", "ces", "que", "qui", "pas", "plus", "en",
        "a", "à", "se", "son", "sa", "ses", "il", "elle", "on", "nous",
        # English
        "the", "of", "to", "in", "for", "and", "or", "is", "are",
        "with", "at", "this", "that", "be", "it", "as", "by",
        "from",
    }
)

_SEPARATOR_RE = re.compile(r"[-_\s]+")
_WORD_SPLIT_RE = re.compile(r"[^0-9A-Za-zÀ-ÿ]+")
_BOUNDARY_PUNCT = "\"'«»“”:;=.,!?()[]{}"


def _normalize(span: str) -> str:
    """Lowercase and collapse ``-``/``_``/whitespace runs to single spaces."""
    return _SEPARATOR_RE.sub(" ", span.lower()).strip()


def _is_keyword_literal(span: str) -> bool:
    return _normalize(span).strip(_BOUNDARY_PUNCT + " ") in _KEYWORD_LITERALS


def _is_config_property_key(span: str) -> bool:
    if not _CONFIG_KEY_SHAPE_RE.match(span):
        return False
    leaf = re.split(r"[.\-]", span)[-1].lower()
    return leaf in _CONFIG_KEY_LEAVES


def _is_placeholder(span: str) -> PostfilterVerdict | None:
    try:
        if is_templated_secret(span):
            return PostfilterVerdict(False, "placeholder: templated secret")
    except Exception:  # defensive fail-open on the external filter
        pass
    if len(span) >= 3 and span == span[0] * len(span):
        return PostfilterVerdict(False, "placeholder: repeated character")
    if _ALPHA_SPAN_RE.match(span):
        tokens = {
            t.lower()
            for part in re.split(r"[_\-]", span)
            for t in _CAMEL_TOKEN_RE.findall(part)
        }
        if tokens & _PLACEHOLDER_WORDS:
            return PostfilterVerdict(
                False, "placeholder: credential word without digit or symbol"
            )
    return None


def _embeds_plausible_assignment_value(span: str) -> bool:
    match = _PLAUSIBLE_ASSIGNMENT_RE.match(span)
    return bool(match) and any(ch.isdigit() for ch in match.group(1))


def _is_natural_language_mention(span: str) -> bool:
    return " " in span.strip() and bool(
        _PASSWORD_MENTION_RE.search(_normalize(span))
    )


def _is_stopword_sentence(span: str) -> bool:
    if len(span.split()) < 4:
        return False
    words = {w.lower() for w in _WORD_SPLIT_RE.split(span) if w}
    return len(words & _STOPWORDS) >= 2


def _is_high_entropy_single_token(span: str) -> bool:
    if " " in span:
        return False
    try:
        return (
            _ENTROPY_SCORER.calculate_shannon_entropy(span)
            >= _HIGH_ENTROPY_KEEP_THRESHOLD
        )
    except Exception:  # defensive fail-open on the external scorer
        return False


def _is_dictionary_word(span: str) -> bool:
    if not span.isalpha() or len(span) > _MAX_ZXCVBN_LEN:
        return False
    try:
        return zxcvbn(span)["score"] <= 1
    except Exception:  # defensive fail-open on the external scorer
        return False


class CredentialPlausibilityStrategy:
    """Reject credential mentions, keep plausible credential values."""

    pii_type = "PASSWORD"

    def evaluate(self, value: str) -> PostfilterVerdict:
        if not isinstance(value, str):
            return PASS
        span = value.strip()
        if not span:
            return PASS
        if has_credential_marker(span):
            return PASS
        # A masked/templated right-hand side is unambiguous (no secret is
        # exposed), so it outranks the entropy keep-guard: long dotted keys
        # such as ``security.user.password=<PASSWORD>`` clear 3.7 bits.
        if _MASKED_ASSIGNMENT_RE.search(span):
            return PostfilterVerdict(
                False, "placeholder: masked assignment value"
            )
        if _is_high_entropy_single_token(span):
            return PASS
        if _is_keyword_literal(span):
            return PostfilterVerdict(False, "credential keyword literal")
        if _is_config_property_key(span):
            return PostfilterVerdict(False, "configuration property key")
        placeholder_verdict = _is_placeholder(span)
        if placeholder_verdict is not None:
            return placeholder_verdict
        if _embeds_plausible_assignment_value(span):
            return PASS
        if _is_natural_language_mention(span):
            return PostfilterVerdict(
                False, "natural-language credential mention"
            )
        if _is_stopword_sentence(span):
            return PostfilterVerdict(False, "natural-language sentence")
        if _is_dictionary_word(span):
            return PostfilterVerdict(False, "dictionary word (zxcvbn)")
        return PASS
