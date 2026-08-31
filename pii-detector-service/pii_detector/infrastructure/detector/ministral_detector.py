"""
Ministral-PII generative PII detector.

Unlike the deterministic Presidio and Regex detectors, Ministral-PII is a
**specialised LLM extractor** served behind an OpenAI-compatible
``/chat/completions`` endpoint (LM Studio). We prompt the model to return a
strict JSON array of ``{text, label}`` objects, then locate each returned text
span back in the source to recover character offsets.

Why HTTP/1.1 + no proxy: LM Studio hangs on the h2c upgrade and local/LAN
endpoints must never be routed through a corporate proxy. We therefore build the
``httpx.Client`` with ``http2=False`` and ``trust_env=False`` (mirrors the Java
``LlmExtractorClient`` ``HTTP_1_1`` / ``NO_PROXY`` reference).

Chunking: long documents are split into windows measured in **real tokens** by
:class:`MinistralTokenChunker` (Ministral's HF tokenizer), degrading to the
char-ratio :class:`FallbackChunker` only when the tokenizer cannot be loaded
(offline / no HF cache). Either way each chunk carries exact character offsets,
so per-chunk entity offsets are rebased to **global** coordinates via
``chunk.start``. A per-chunk failure (timeout / HTTP error) is logged and skipped
(fail-open partial) so one bad chunk never sinks the whole detection — but when
**every** chunk fails the endpoint is down, not the content, so
:class:`DetectorUnavailableError` is raised rather than reporting an empty result
that would read like "no PII here".

Per-type thresholds and the Ministral label -> canonical ``pii_type`` mapping are
resolved from the ``pii_type_config`` DB rows (detector='MINISTRAL'). Because the
model emits an **open, free-cased vocabulary**, label resolution is layered
(:class:`_LabelResolver`): exact ``detector_label`` match, then a case/format-
insensitive match, then an open-vocabulary **passthrough** that surfaces any
still-unmapped label as its normalized ``UPPER_SNAKE`` form. Nothing is dropped
here: the downstream gRPC type-config gate keeps entities with no config row and
drops the ones an operator disabled, so passthrough never resurrects a disabled
type.

Ministral's entities flow through the same deterministic format post-filter
(:mod:`format_postfilter_validator`) as every other detector's; there is no
per-detector exemption.
"""

from __future__ import annotations

import json
import logging
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import httpx

from pii_detector.application.config.detection_policy import DetectionConfig
from pii_detector.domain.entity.detector_failure import (
    DetectorFailure,
    DetectorFailureCode,
)
from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.domain.exception.exceptions import (
    DetectorUnavailableError,
    PIIDetectionError,
)
from pii_detector.infrastructure.text_processing.semantic_chunker import (
    FallbackChunker,
    MinistralTokenChunker,
)

DETECTOR_NAMESPACE = "MINISTRAL"
MINISTRAL_DEFAULT_MODEL_ID = "ministral-3b-pii-preview@q8_0"
# Plain HTTP by default: LM Studio offers no TLS listener, so the nominal
# loopback deployment has nothing to negotiate. It is only a default — a
# deployment that reaches the model server across a network sets
# LLM_MINISTRAL_SCHEME=https rather than sending scanned content in clear.
DEFAULT_SCHEME = "http"
DEFAULT_BASE_URL = "http://localhost:1234/v1"
# Permissive global threshold at this layer (the detector's own default when no
# request threshold is supplied).
DEFAULT_GLOBAL_THRESHOLD = 0.5
# Fixed confidence assigned to every Ministral extraction. The generative model
# emits binary decisions (a span either IS or ISN'T PII) with no probabilistic
# score, so it asserts maximum confidence. Deriving the score from the request
# threshold (as before) was a bug: with a request threshold of 0.30 below the
# per-type thresholds (0.50 in the seed), every well-mapped detection was
# silently dropped by _apply_per_type_thresholds and by the gRPC type-config
# gate. A fixed 1.0 keeps them; per-type thresholds thus never suppress a
# Ministral finding — disable the type instead.
MINISTRAL_CONFIDENCE = 1.0

# Default chunking parameters (real tokens). Mirrors the DB defaults in
# init-script 013 (ministral_chunk_size / ministral_overlap). 2048 sits below
# the recall cliff the chunking benchmark observed between 2048 and 8192 tokens;
# the overlap is ~20% of the window so a boundary-straddling entity is recovered
# whole by the adjacent chunk.
DEFAULT_CHUNK_SIZE_TOKENS = 2048
DEFAULT_CHUNK_OVERLAP_TOKENS = 410
# Conservative chars/token used to translate token limits into char windows
# for the LLM context, ONLY on the degraded FallbackChunker path (multilingual-
# safe upper bound). The primary path measures windows in real tokens.
CHARS_PER_TOKEN = 4

# HF tokenizer used for token-faithful chunking — the same repo the chunking
# benchmark uses, so the production token budget reproduces the bench. Loaded
# lazily via the lightweight ``tokenizers`` library; any load failure (offline /
# no HF cache) degrades to the char-ratio FallbackChunker. Override with
# LLM_MINISTRAL_TOKENIZER (HF repo id, tokenizer.json path, or a directory).
MINISTRAL_TOKENIZER_REPO = "OpenMed/Ministral-3B-PII-Preview"

# Connect timeout (seconds) for a chat/completions call: fail fast only when the
# LM Studio endpoint is unreachable. Override via env if ever needed.
CONNECT_TIMEOUT_SECONDS = float(os.getenv("LLM_MINISTRAL_CONNECT_TIMEOUT", "30"))
# Read timeout (seconds) for the inference itself. Generous on purpose: a single
# dense chunk can legitimately take minutes on a slow or contended host, and
# cutting it off would lose that chunk's findings.
#
# It is nonetheless bounded, because an endpoint that dies mid-request does NOT
# reliably break the TCP connection: closing LM Studio while a chunk is in flight
# leaves the socket open with no answer coming, so an unbounded read makes the
# scan hang for good with nothing reported. A bounded read turns that into a
# failed chunk, which pauses the scan and names the cause.
#
# The default is ~40x the slowest chunk measured on the reference host (chunks run
# between 0.1s and 1.5s there), which keeps a genuinely slow inference safe while
# surfacing a dead endpoint within a minute. Raise it for hosts where inference is
# known to be much slower.
READ_TIMEOUT_SECONDS = float(os.getenv("LLM_MINISTRAL_READ_TIMEOUT", "60"))
# Total budget for the liveness probe (GET /models). Short on purpose: the probe
# gates a scan start, so an operator must get the verdict in seconds, not after
# the generous inference connect timeout above.
PROBE_TIMEOUT_SECONDS = float(os.getenv("LLM_MINISTRAL_PROBE_TIMEOUT", "5"))
# Generation budget large enough to absorb a JSON array over a full chunk.
MAX_TOKENS = 2048

# Strict JSON Schema forcing an array of {text, label} objects (mirrors the
# Java LlmExtractorClient entityArraySchema()).
_ENTITY_ARRAY_SCHEMA: Dict[str, Any] = {
    "type": "array",
    "items": {
        "type": "object",
        "properties": {
            "text": {"type": "string"},
            "label": {"type": "string"},
        },
        "required": ["text", "label"],
    },
}

_SYSTEM_PROMPT = (
    "You are a strict PII extraction engine. Extract every personally "
    "identifiable information span that appears verbatim in the user text. "
    "Return ONLY a JSON array of objects, each with exactly two fields: "
    '"text" (the exact substring as it appears in the input) and "label" '
    "(the PII category). Do not paraphrase, translate or normalise the text. "
    "Do not add explanations. If there is no PII, return an empty array []."
)


# camelCase / PascalCase boundary (lower|digit -> Upper), split so
# "TrackingNumber" -> "tracking_number". A digit->letter boundary is NOT split,
# so trained labels like ``ipv4`` / ``ipv6`` survive intact.
_CAMEL_CASE_BOUNDARY = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")
_NON_ALNUM_RUN = re.compile(r"[^a-z0-9]+")


def _normalize_label(label: str) -> str:
    """Normalise a free-form model label to a canonical ``snake_case`` key.

    Splits camelCase/PascalCase boundaries, lower-cases, and collapses any run of
    non-alphanumerics to a single ``_`` — e.g. ``"Ip Address" -> "ip_address"``,
    ``"TrackingNumber" -> "tracking_number"``, ``"ZIP+4" -> "zip_4"``.
    """
    spaced = _CAMEL_CASE_BOUNDARY.sub("_", label.strip())
    return _NON_ALNUM_RUN.sub("_", spaced.lower()).strip("_")


# Bridge the model's inconsistent open vocabulary to the configured
# ``detector_label`` names. Ministral-3B-PII is generative: for the same concept
# it emits either the documented label or a qualified/synonym variant depending
# on context (e.g. ``driver_license`` vs ``driver_license_number``,
# ``stripe_api_key`` vs ``api_key``, ``routing_number`` vs
# ``bank_routing_number``). The model card itself warns it "can occasionally emit
# a label outside the documented set". Keys and values are NORMALIZED
# (:func:`_normalize_label`) forms; a variant is rewritten to its canonical
# ``detector_label`` before lookup, so it inherits that type's enabled state and
# threshold. Only map variants that are unambiguously the same PII type. Captured
# empirically from the 69-label probe (my-files/ministral-69-labels-probe*).
_MODEL_LABEL_ALIASES: Dict[str, str] = {
    "driver_license": "driver_license_number",
    "routing_number": "bank_routing_number",
    "coordinates": "coordinate",
    "stripe_api_key": "api_key",
    "temporary_password": "password",
    "credit_card": "credit_debit_card",
    "atm_pin": "pin",
    "employer_tax_id": "tax_id",
    "mobile_phone_number": "phone_number",
    "fax": "fax_number",
    "uk_postcode": "postcode",
    "race_and_ethnicity": "race_ethnicity",
    "preferred_spoken_language": "language",
    "highest_education_level": "education_level",
    "vendor": "company_name",
    "ipv4_address": "ipv4",
    "gateway_ipv4": "ipv4",
    "ipv6_address": "ipv6",
    "username": "user_name",
    "imei": "device_identifier",
}


@dataclass(frozen=True)
class _ChunkOutcome:
    """One chunk's extraction result, carrying its failure reason when it failed.

    Keeping the reason alongside the (empty) entity list is what lets
    :meth:`MinistralDetector._merge_chunk_outcomes` tell "this chunk held no PII"
    apart from "this chunk never reached the endpoint".
    """

    entities: List[PIIEntity]
    error: Optional[str] = None


@dataclass(frozen=True)
class _LabelResolver:
    """Resolve a model label to a canonical ``pii_type`` without ever dropping.

    Layered resolution over the DB ``detector_label -> pii_type`` mapping:

    1. **exact** — the label matches a configured ``detector_label`` verbatim;
    2. **normalized** — case/format-insensitive match (``"Ip Address"`` resolves
       to the same ``pii_type`` as the configured ``ip_address``);
    3. **alias** — a known model variant (:data:`_MODEL_LABEL_ALIASES`) is
       rewritten to its canonical ``detector_label`` (e.g. ``driver_license`` ->
       ``driver_license_number``) so the generative model's inconsistent output
       still resolves to the configured type;
    4. **passthrough** — any still-unmapped label surfaces as its normalized
       ``UPPER_SNAKE`` form so open-vocabulary detections are never silently
       dropped. The gRPC type-config gate keeps no-config types and drops
       disabled ones, so this never resurrects a type an operator turned off.
    """

    exact: Dict[str, str]
    normalized: Dict[str, str]

    @classmethod
    def from_mapping(cls, label_mapping: Dict[str, str]) -> _LabelResolver:
        normalized: Dict[str, str] = {}
        for detector_label, pii_type in label_mapping.items():
            # First configured label wins on a normalized collision.
            normalized.setdefault(_normalize_label(detector_label), pii_type)
        return cls(exact=dict(label_mapping), normalized=normalized)

    def resolve(self, label: str) -> Optional[str]:
        """Return the canonical ``pii_type``, or ``None`` only for an empty label."""
        if label in self.exact:
            return self.exact[label]
        norm = _normalize_label(label)
        if not norm:
            return None
        if norm in self.normalized:
            return self.normalized[norm]
        alias = _MODEL_LABEL_ALIASES.get(norm)
        if alias and alias in self.normalized:
            return self.normalized[alias]
        return norm.upper()


class MinistralDetector:
    """PII detector backed by the Ministral-PII LLM (OpenAI-compatible endpoint).

    Implements ``PIIDetectorProtocol``: ``model_id``, ``detect_pii``,
    ``mask_pii``, ``download_model``, ``load_model``.
    """

    def __init__(self, config: Optional[DetectionConfig] = None):
        self.config = config or DetectionConfig(model_id=MINISTRAL_DEFAULT_MODEL_ID)
        # Remote endpoint + model id are env-driven (LM Studio host), with the
        # spec defaults as fallback. The generic config is accepted for parity
        # with the other detectors but the model id is forced to the env value.
        self.base_url = os.environ.get("LLM_MINISTRAL_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
        self._scheme = os.environ.get("LLM_MINISTRAL_SCHEME", DEFAULT_SCHEME)
        self._model_id = os.environ.get("LLM_MINISTRAL_MODEL", MINISTRAL_DEFAULT_MODEL_ID)
        self.threshold = (
            self.config.threshold
            if self.config.threshold is not None
            else DEFAULT_GLOBAL_THRESHOLD
        )

        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")
        self._client: Optional[httpx.Client] = None

        # Lazily-loaded HF tokenizer for token-faithful chunking (cached on the
        # instance; the service holds one detector, so loaded once per process).
        self._tokenizer_repo = os.environ.get(
            "LLM_MINISTRAL_TOKENIZER", MINISTRAL_TOKENIZER_REPO
        )
        self._tokenizer: Optional[Any] = None
        self._tokenizer_loaded = False

    # ------------------------------------------------------------------
    # PIIDetectorProtocol
    # ------------------------------------------------------------------

    @property
    def model_id(self) -> str:
        return self._model_id

    def download_model(self) -> None:
        """No-op: the model lives behind a remote OpenAI-compatible endpoint."""
        return None

    def load_model(self) -> None:
        """No-op: nothing to load locally (remote inference endpoint)."""
        return None

    def check_health(
        self,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
    ) -> Tuple[str, Optional[DetectorFailure]]:
        """Probe the LM Studio endpoint; return ``(endpoint, failure)``.

        ``failure`` is ``None`` when the endpoint answered. Probing with ``GET
        /models`` rather than a real completion keeps the check cheap enough to run
        before every scan while still exercising the same host/port, HTTP/1.1 and
        no-proxy client the detection path uses.
        """
        base_url = self._resolve_base_url(lm_studio_host, lm_studio_port)
        try:
            response = self._get_client().get(
                f"{base_url}/models", timeout=PROBE_TIMEOUT_SECONDS
            )
            response.raise_for_status()
        except (httpx.HTTPError, httpx.TimeoutException) as exc:
            self.logger.warning(
                "MINISTRAL_ENDPOINT_UNREACHABLE base_url=%s: %s", base_url, exc
            )
            return base_url, DetectorFailure(
                code=DetectorFailureCode.ENDPOINT_UNREACHABLE,
                params={"cause": f"{type(exc).__name__}: {exc}"},
                message=f"{type(exc).__name__}: {exc}",
            )

        return base_url, self._model_not_served_reason(base_url)

    def _model_not_served_reason(self, base_url: str) -> Optional[DetectorFailure]:
        """Why the configured model cannot answer, or ``None`` when it is ready.

        A reachable endpoint is not a working detector: LM Studio answers
        ``GET /models`` with every model it has on disk, loaded or not, so a server
        left running with the model unloaded passes a reachability check and then
        fails every request. The model state lives on LM Studio's own
        ``/api/v0/models``; a server that does not expose it (any other
        OpenAI-compatible backend) is left alone rather than refused on a check it
        never claimed to support.
        """
        root = base_url[: -len("/v1")] if base_url.endswith("/v1") else base_url
        try:
            response = self._get_client().get(
                f"{root}/api/v0/models", timeout=PROBE_TIMEOUT_SECONDS
            )
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, httpx.TimeoutException, ValueError):
            return None

        # A verdict is only given on a payload actually understood. An unexpected
        # shape means this backend does not answer the question asked, which is the
        # same situation as not exposing the endpoint at all — staying silent beats
        # refusing a scan over a response misread. An empty list, on the other hand,
        # is understood and conclusive: the server serves no model at all.
        if not isinstance(payload, dict):
            return None
        listed = payload.get("data")
        if not isinstance(listed, list):
            return None
        models = [m for m in listed if isinstance(m, dict)]
        if listed and not models:
            return None

        entry = next((m for m in models if m.get("id") == self._model_id), None)
        if entry is None:
            loaded = [m.get("id") for m in models if m.get("state") == "loaded"]
            return DetectorFailure(
                code=DetectorFailureCode.MODEL_NOT_AVAILABLE,
                params={
                    "model": str(self._model_id),
                    "loadedModels": ", ".join(str(model) for model in loaded),
                },
                message=(
                    f"model {self._model_id} is not available on the endpoint "
                    f"(loaded models: {loaded or 'none'})"
                ),
            )
        state = entry.get("state")
        if state != "loaded":
            return DetectorFailure(
                code=DetectorFailureCode.MODEL_NOT_LOADED,
                params={"model": str(self._model_id), "state": str(state)},
                message=(
                    f"model {self._model_id} is present but not loaded (state: {state})"
                ),
            )
        return None

    def detect_pii(
        self,
        text: str,
        threshold: Optional[float] = None,
        pii_type_configs: Optional[Dict] = None,
        chunk_size: Optional[int] = None,
        overlap: Optional[int] = None,
        lm_studio_host: Optional[str] = None,
        lm_studio_port: Optional[int] = None,
        concurrency: Optional[int] = None,
    ) -> List[PIIEntity]:
        if not text:
            return []

        effective_threshold = threshold if threshold is not None else self.threshold
        # Per-request LM Studio endpoint override (DB columns lm_studio_host /
        # lm_studio_port, re-read on every scan/resume). Threaded as a local so
        # the singleton detector's ``self.base_url`` (env default) is never
        # mutated under concurrent gRPC threads.
        base_url = self._resolve_base_url(lm_studio_host, lm_studio_port)
        detection_id = f"ministral_{int(time.time() * 1000) % 10000}"

        try:
            label_mapping, scoring_overrides, type_labels = self._resolve_runtime_config(
                pii_type_configs
            )
            self.logger.debug(
                "[%s] Ministral runtime config: %d labels mapped, %d thresholds",
                detection_id, len(label_mapping), len(scoring_overrides),
            )
            if not label_mapping:
                return []

            resolver = _LabelResolver.from_mapping(label_mapping)
            entities = self._extract_over_chunks(
                text, resolver, type_labels, chunk_size, overlap, base_url,
                concurrency,
            )
            entities = self._apply_per_type_thresholds(entities, scoring_overrides)
            # Global confidence floor, for parity with the other detectors. The
            # generative extractor emits a fixed max confidence, so this only
            # drops in the degenerate case of a request threshold above 1.0.
            entities = [e for e in entities if e.score >= effective_threshold]

            self.logger.info(
                "[%s] Ministral detection complete: %d kept",
                detection_id, len(entities),
            )
            return entities
        except PIIDetectionError:
            raise
        except Exception as exc:  # pragma: no cover - defensive logging
            self.logger.error(
                "[%s] Ministral detection failed: %s", detection_id, exc, exc_info=True
            )
            raise PIIDetectionError(f"Ministral PII detection failed: {exc}") from exc

    def mask_pii(
        self, text: str, threshold: Optional[float] = None
    ) -> Tuple[str, List[PIIEntity]]:
        entities = self.detect_pii(text, threshold)
        masked = self._apply_masks(text, entities)
        return masked, entities

    # ------------------------------------------------------------------
    # Extraction over chunks (global offset rebasing)
    # ------------------------------------------------------------------

    def _resolve_base_url(
        self, lm_studio_host: Optional[str], lm_studio_port: Optional[int]
    ) -> str:
        """Return the effective LM Studio base URL for this request.

        When the operator-configured host/port are supplied (DB columns
        lm_studio_host / lm_studio_port), build ``<scheme>://host:port/v1``
        using LLM_MINISTRAL_SCHEME; otherwise fall back to the env-driven
        ``self.base_url``.
        """
        if lm_studio_host and lm_studio_port:
            return f"{self._scheme}://{lm_studio_host}:{lm_studio_port}/v1"
        return self.base_url

    def _extract_over_chunks(
        self,
        text: str,
        resolver: _LabelResolver,
        type_labels: Dict[str, str],
        chunk_size: Optional[int],
        overlap: Optional[int],
        base_url: str,
        concurrency: Optional[int] = None,
    ) -> List[PIIEntity]:
        """Chunk the text, extract per chunk, rebase offsets to global coords.

        Each chunk is extracted independently via the remote LLM. The returned
        text spans are located inside the chunk (chunk-local offsets) and then
        shifted by ``chunk.start`` so the final ``PIIEntity`` carries GLOBAL
        character offsets. A per-chunk HTTP/timeout failure is logged and skipped
        (fail-open partial); an all-chunks failure raises
        :class:`DetectorUnavailableError` (see :meth:`_merge_chunk_outcomes`).

        ``concurrency`` controls how many chunk prompts are in flight against the
        LM Studio endpoint at once. ``<= 1`` keeps the historical sequential loop
        (zero overhead). ``> 1`` dispatches chunks across a bounded thread pool
        over the shared thread-safe ``httpx.Client``; because each chunk carries
        its own ``chunk.start``, completion order is irrelevant and
        ``DetectionMerger`` collapses the overlap regions downstream.
        """
        chunker = self._build_chunker(
            chunk_size if chunk_size is not None else DEFAULT_CHUNK_SIZE_TOKENS,
            overlap if overlap is not None else DEFAULT_CHUNK_OVERLAP_TOKENS,
        )
        chunks = chunker.chunk_text(text)
        workers = max(1, int(concurrency)) if concurrency else 1
        if workers <= 1 or len(chunks) <= 1:
            outcomes = [
                self._extract_one_chunk(chunk, resolver, type_labels, base_url)
                for chunk in chunks
            ]
        else:
            outcomes = self._extract_chunks_concurrently(
                chunks, resolver, type_labels, base_url, workers
            )
        return self._merge_chunk_outcomes(outcomes, base_url)

    def _merge_chunk_outcomes(
        self, outcomes: List[_ChunkOutcome], base_url: str
    ) -> List[PIIEntity]:
        """Flatten per-chunk outcomes, escalating a total failure to unavailable.

        Every chunk failing means the endpoint is down, not that the document is
        clean, so returning ``[]`` here would silently degrade the scan. A partial
        failure keeps the fail-open contract but is logged with its ratio so the
        loss is traceable.
        """
        failures = [outcome.error for outcome in outcomes if outcome.error]
        if failures and len(failures) == len(outcomes):
            raise DetectorUnavailableError(
                f"Ministral endpoint unreachable at {base_url} "
                f"({len(failures)}/{len(outcomes)} chunks failed): {failures[0]}"
            )
        if failures:
            self.logger.warning(
                "MINISTRAL_PARTIAL_FAILURE failed_chunks=%d/%d base_url=%s: %s",
                len(failures), len(outcomes), base_url, failures[0],
            )
        return [entity for outcome in outcomes for entity in outcome.entities]

    def _extract_one_chunk(
        self,
        chunk: Any,
        resolver: _LabelResolver,
        type_labels: Dict[str, str],
        base_url: str,
    ) -> _ChunkOutcome:
        """Extract one chunk's entities (global offsets); fail-open on HTTP/timeout.

        A per-chunk HTTP/timeout error is logged and reported as a failed outcome
        so one bad chunk never sinks the whole detection, while still letting the
        caller see how many chunks were lost. Any other exception propagates (it
        is a real bug, surfaced by ``detect_pii`` as ``PIIDetectionError``).
        """
        try:
            raw_pairs = self._extract_chunk(chunk.text, base_url)
        except (httpx.HTTPError, httpx.TimeoutException, DetectorUnavailableError) as exc:
            self.logger.warning(
                "MINISTRAL_CHUNK_FAILED start=%d len=%d: %s",
                chunk.start, len(chunk.text), exc,
            )
            return _ChunkOutcome(entities=[], error=f"{type(exc).__name__}: {exc}")
        return _ChunkOutcome(entities=self._pairs_to_entities(
            raw_pairs, chunk.text, chunk.start, resolver, type_labels,
        ))

    def _extract_chunks_concurrently(
        self,
        chunks: List[Any],
        resolver: _LabelResolver,
        type_labels: Dict[str, str],
        base_url: str,
        workers: int,
    ) -> List[_ChunkOutcome]:
        """Extract chunks across a bounded thread pool over the shared client.

        The pool is capped at ``min(workers, len(chunks))`` — never more threads
        than there is work. The ``httpx.Client`` is warmed up once here so its
        lazy creation does not race across worker threads. Results are gathered in
        submission order; each future re-raises any non-fail-open exception,
        preserving the sequential path's error semantics.
        """
        from concurrent.futures import ThreadPoolExecutor

        # Warm up the lazy shared client before the pool starts (avoid a first-use
        # creation race between worker threads).
        self._get_client()
        max_workers = min(workers, len(chunks))
        with ThreadPoolExecutor(
            max_workers=max_workers, thread_name_prefix="ministral-chunk"
        ) as pool:
            futures = [
                pool.submit(
                    self._extract_one_chunk, chunk, resolver, type_labels, base_url
                )
                for chunk in chunks
            ]
            return [future.result() for future in futures]

    # ------------------------------------------------------------------
    # Chunker selection + lazy tokenizer
    # ------------------------------------------------------------------

    def _build_chunker(self, chunk_size: int, overlap: int) -> Any:
        """Token-window chunker when the HF tokenizer loads, else char-ratio.

        The token chunker measures the window in the unit the 3B model is bounded
        by (real tokens) and returns exact char offsets; the FallbackChunker is
        the offline/degraded char×N approximation of the same budget.
        """
        tokenizer = self._get_tokenizer()
        if tokenizer is not None:
            return MinistralTokenChunker(
                tokenizer=tokenizer,
                chunk_size=chunk_size,
                overlap=overlap,
                logger=self.logger,
            )
        return FallbackChunker(
            chunk_size=chunk_size,
            overlap=overlap,
            chars_per_token=CHARS_PER_TOKEN,
            logger=self.logger,
        )

    def _get_tokenizer(self) -> Optional[Any]:
        """Lazily load + cache the Ministral HF tokenizer (per instance).

        Returns ``None`` (and logs once) when the tokenizer cannot be loaded, so
        the detector degrades to the char-ratio FallbackChunker instead of
        failing. Failures are not memoised, so a transient load error is retried
        on the next request.
        """
        if self._tokenizer_loaded:
            return self._tokenizer
        if self._tokenizer_repo:
            try:
                self._tokenizer = self._load_tokenizer(self._tokenizer_repo)
            except Exception as exc:
                self.logger.warning(
                    "MINISTRAL_TOKENIZER_LOAD_FAILED repo=%s: %s — "
                    "using char-ratio FallbackChunker",
                    self._tokenizer_repo, exc,
                )
                return None
        self._tokenizer_loaded = True
        return self._tokenizer

    @staticmethod
    def _load_tokenizer(repo: str) -> Any:
        """Load a ``tokenizers.Tokenizer`` from an HF repo id, a tokenizer.json
        file, or a directory containing one (mirrors the chunking benchmark)."""
        from tokenizers import Tokenizer

        path = Path(repo)
        if path.is_file():
            return Tokenizer.from_file(str(path))
        if path.is_dir():
            return Tokenizer.from_file(str(path / "tokenizer.json"))
        return Tokenizer.from_pretrained(repo)

    def _pairs_to_entities(
        self,
        raw_pairs: List[Dict[str, Any]],
        chunk_text: str,
        chunk_start: int,
        resolver: _LabelResolver,
        type_labels: Dict[str, str],
    ) -> List[PIIEntity]:
        """Map ``{text, label}`` pairs to ``PIIEntity`` with global offsets."""
        results: List[PIIEntity] = []
        for pair in raw_pairs:
            entity = self._pair_to_entity(
                pair, chunk_text, chunk_start, resolver, type_labels
            )
            if entity is not None:
                results.append(entity)
        return results

    def _pair_to_entity(
        self,
        pair: Dict[str, Any],
        chunk_text: str,
        chunk_start: int,
        resolver: _LabelResolver,
        type_labels: Dict[str, str],
    ) -> Optional[PIIEntity]:
        """Build a single ``PIIEntity`` from one ``{text, label}`` pair.

        The label is resolved to a canonical ``pii_type`` by :class:`_LabelResolver`
        (exact -> normalized -> open-vocabulary passthrough), so an unrecognised
        label is surfaced rather than dropped. Returns ``None`` only when the pair
        is malformed or the text cannot be located in the chunk (case-sensitive
        find; skip on miss).
        """
        if not isinstance(pair, dict):
            return None
        span_text = pair.get("text")
        label = pair.get("label")
        if not span_text or not label:
            return None
        pii_type = resolver.resolve(str(label))
        if not pii_type:
            return None
        local_start = chunk_text.find(str(span_text))
        if local_start < 0:
            return None
        global_start = chunk_start + local_start
        global_end = global_start + len(str(span_text))
        return PIIEntity(
            text=str(span_text),
            pii_type=pii_type,
            type_label=type_labels.get(pii_type, pii_type),
            start=global_start,
            end=global_end,
            score=MINISTRAL_CONFIDENCE,
            source=DetectorSource.MINISTRAL,
        )

    # ------------------------------------------------------------------
    # Remote LLM call (OpenAI-compatible chat/completions, HTTP/1.1, no proxy)
    # ------------------------------------------------------------------

    def _get_client(self) -> httpx.Client:
        """Lazily build the httpx client: HTTP/1.1 forced, env/proxy ignored."""
        if self._client is None:
            self._client = httpx.Client(
                http2=False,
                trust_env=False,
                # Bounded read so a dead endpoint is reported instead of hanging the
                # scan; write/pool stay unbounded (see READ_TIMEOUT_SECONDS).
                timeout=httpx.Timeout(
                    None, connect=CONNECT_TIMEOUT_SECONDS, read=READ_TIMEOUT_SECONDS
                ),
            )
        return self._client

    def _extract_chunk(
        self, chunk_text: str, base_url: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """POST one chunk to ``/chat/completions`` and parse the entity array."""
        client = self._get_client()
        url = f"{base_url or self.base_url}/chat/completions"
        payload = self._build_payload(chunk_text)
        resp = client.post(url, json=payload)
        resp.raise_for_status()
        content = self._extract_content(resp.json())
        return self._parse_entity_array(content)

    def _build_payload(self, chunk_text: str) -> Dict[str, Any]:
        """Build the OpenAI chat/completions body (temperature 0, json_schema)."""
        return {
            "model": self._model_id,
            "messages": [
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user", "content": chunk_text},
            ],
            "temperature": 0,
            "stream": False,
            "max_tokens": MAX_TOKENS,
            "response_format": {
                "type": "json_schema",
                "json_schema": {
                    "name": "pii_entities",
                    "strict": True,
                    "schema": _ENTITY_ARRAY_SCHEMA,
                },
            },
        }

    @staticmethod
    def _extract_content(response_json: Dict[str, Any]) -> str:
        """Read ``choices[0].message.content`` (fallback ``reasoning_content``).

        A body without ``choices`` is NOT an empty result: an OpenAI-compatible
        server that answers HTTP 200 with an error payload (wrong endpoint, model
        not served, an intercepting proxy) would otherwise be read as "this text
        holds no personal data" — an error silently turned into a clean report.
        Raising here makes it a failed chunk, which pauses the scan and names the
        cause.
        """
        if not isinstance(response_json, dict):
            raise DetectorUnavailableError(
                f"endpoint returned a non-object body: {type(response_json).__name__}"
            )
        choices = response_json.get("choices") or []
        if not choices:
            reported = response_json.get("error") or response_json.get("detail")
            raise DetectorUnavailableError(
                "endpoint answered without a completion"
                + (f": {reported}" if reported else f" (keys: {sorted(response_json)})")
            )
        message = choices[0].get("message") or {}
        return str(message.get("content") or message.get("reasoning_content") or "")

    @staticmethod
    def _parse_entity_array(content: str) -> List[Dict[str, Any]]:
        """Tolerant extraction of the first balanced top-level JSON array.

        Handles markdown fences and surrounding prose by scanning for the first
        ``[`` and its matching ``]`` before parsing. Returns an empty list when
        no array is found or the payload is not a list.
        """
        if not content:
            return []
        snippet = MinistralDetector._first_json_array(content)
        if snippet is None:
            return []
        try:
            parsed = json.loads(snippet)
        except (ValueError, TypeError):
            return []
        return parsed if isinstance(parsed, list) else []

    @staticmethod
    def _first_json_array(content: str) -> Optional[str]:
        """Return the first balanced ``[...]`` substring, or ``None``."""
        cleaned = re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL)
        start = cleaned.find("[")
        if start < 0:
            return None
        depth = 0
        for idx in range(start, len(cleaned)):
            char = cleaned[idx]
            if char == "[":
                depth += 1
            elif char == "]":
                depth -= 1
                if depth == 0:
                    return cleaned[start: idx + 1]
        return None

    # ------------------------------------------------------------------
    # Filtering
    # ------------------------------------------------------------------

    def _apply_per_type_thresholds(
        self, entities: List[PIIEntity], scoring_overrides: Dict[str, float]
    ) -> List[PIIEntity]:
        if not scoring_overrides:
            return entities
        kept: List[PIIEntity] = []
        for entity in entities:
            per_type = scoring_overrides.get(entity.pii_type)
            if per_type is not None and entity.score < per_type:
                continue
            kept.append(entity)
        return kept

    # ------------------------------------------------------------------
    # Configuration resolution
    # ------------------------------------------------------------------

    def _resolve_runtime_config(
        self, pii_type_configs: Optional[Dict]
    ) -> Tuple[Dict[str, str], Dict[str, float], Dict[str, str]]:
        configs = pii_type_configs or self._fetch_configs_from_db()
        if not configs:
            self.logger.warning(
                "No MINISTRAL pii_type configs available — detector will emit nothing"
            )
            return {}, {}, {}

        label_mapping: Dict[str, str] = {}
        scoring_overrides: Dict[str, float] = {}
        type_labels: Dict[str, str] = {}

        for key, cfg in configs.items():
            pii_type = self._resolve_pii_type_for_entry(key, cfg)
            if not pii_type or not cfg.get("enabled", False):
                continue
            self._fill_runtime_dicts(
                pii_type, cfg, label_mapping, scoring_overrides, type_labels
            )

        return label_mapping, scoring_overrides, type_labels

    @staticmethod
    def _resolve_pii_type_for_entry(key: Any, cfg: Any) -> Optional[str]:
        """Resolve the canonical ``pii_type`` for one config entry.

        Accepts both the ``MINISTRAL:<type>`` keyed shape and the flat shape
        where the row carries ``detector == "MINISTRAL"``. Returns ``None`` when
        the entry is not a MINISTRAL config (caller skips it).
        """
        if isinstance(key, str) and key.startswith(f"{DETECTOR_NAMESPACE}:"):
            return key.split(":", 1)[1]
        detector_value = cfg.get("detector") if isinstance(cfg, dict) else None
        if detector_value == DETECTOR_NAMESPACE:
            return cfg.get("pii_type") or (
                key if isinstance(key, str) and ":" not in key else None
            )
        return None

    @staticmethod
    def _fill_runtime_dicts(
        pii_type: str,
        cfg: Dict[str, Any],
        label_mapping: Dict[str, str],
        scoring_overrides: Dict[str, float],
        type_labels: Dict[str, str],
    ) -> None:
        """Populate the label / threshold / type_label maps for one entry."""
        detector_label = cfg.get("detector_label")
        if detector_label:
            label_mapping[detector_label] = pii_type
        threshold = cfg.get("threshold")
        if threshold is not None:
            scoring_overrides[pii_type] = float(threshold)
        # Optional human-readable label surfaced in the gRPC response
        # (PIIEntity.type_label). When absent, _pair_to_entity falls back to
        # the pii_type.
        type_label = cfg.get("type_label")
        if type_label:
            type_labels[pii_type] = str(type_label)

    def _fetch_configs_from_db(self) -> Optional[Dict]:
        try:
            from pii_detector.infrastructure.adapter.out.database_config_adapter import (
                get_database_config_adapter,
            )

            adapter = get_database_config_adapter()
            return adapter.fetch_pii_type_configs(detector=DETECTOR_NAMESPACE)
        except Exception as exc:
            self.logger.warning(
                "Failed to fetch MINISTRAL pii_type configs from DB: %s", exc
            )
            return None

    # ------------------------------------------------------------------
    # Masking
    # ------------------------------------------------------------------

    @staticmethod
    def _apply_masks(text: str, entities: List[PIIEntity]) -> str:
        if not entities:
            return text
        sorted_entities = sorted(entities, key=lambda e: e.start)
        parts: List[str] = []
        last_pos = 0
        for entity in sorted_entities:
            if entity.start < last_pos:
                continue
            parts.append(text[last_pos: entity.start])
            parts.append(f"[{entity.pii_type}]")
            last_pos = entity.end
        parts.append(text[last_pos:])
        return "".join(parts)
