"""Startup auto-tuner for the Ministral chunk-prompt concurrency.

The Ministral detector can send its document chunks to the LM Studio endpoint
concurrently (see ``MinistralDetector._extract_over_chunks``). The optimal number
of concurrent prompts is host-dependent (iGPU vs CPU, memory bandwidth, LM Studio
slot count), so a single hardcoded value cannot fit the heterogeneous client
machines the product is deployed on.

This module runs a one-shot micro-benchmark **once at service startup** against the
configured LM Studio endpoint, picks the concurrency that maximises aggregate
throughput, and persists it to the ``pii_detection_config`` DB row. It is invoked
from ``PIIDetectionServicer.__init__`` (the parent process, before the worker pool
forks) so it runs exactly once and never contends with a real scan.

Design contract (see docs/superpowers/specs/2026-07-15-ministral-concurrency-autotune-design.md):

- Skip when the operator pinned the value (``ministral_concurrency_auto = false``)
  or when it was already tuned for the current ``host:port|model`` signature.
- Skip (WARNING, no persist) when LM Studio is unreachable — fail-open.
- Skip when the Ministral detector is disabled (nothing would use the value) or
  absent.
- Fully guarded: any failure leaves the stored value untouched and never aborts
  startup. "Blocking" means synchronous-before-serving, not "aborts boot".

The bench mirrors production by reusing the detector's own chunker, HTTP client and
request payload, at the configured chunk size — so the per-prompt cost measured is
the real prefill-heavy cost the model pays in production.
"""
from __future__ import annotations

import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

import httpx

logger = logging.getLogger(__name__)

# Bench tunables (env-overridable for a specific client host).
DEFAULT_MAX_CONCURRENCY = 4
# Hard cap on the levels an operator may request from the UI: every level replays
# the whole sample, so the run time grows quadratically with this bound.
MAX_BENCH_CONCURRENCY = 20
DEFAULT_MIN_GAIN = 1.3
# On a near-tie, prefer the smaller concurrency within this fraction of the best
# speedup (avoid claiming +1 slot for a marginal gain).
DEFAULT_TIE_EPS = 0.10
# Health-probe timeout (seconds): a quick liveness check, not an inference call.
PROBE_TIMEOUT_SECONDS = 5.0
# Rough chars/token used only to size the synthetic sample so it yields enough
# chunks to fill the concurrency slots. The real chunker still measures in tokens.
BENCH_CHARS_PER_TOKEN = 4

# One representative FR paragraph with varied PII. Repeated (with a per-block
# marker to defeat prompt-prefix caching) until the sample is large enough to
# produce more chunks than the max concurrency under test.
_SAMPLE_BLOCK = (
    "Note de dossier n°{n} — Le collaborateur Jean-Marc Dubois (n° AVS "
    "756.1234.5678.97) a transmis son RIB IBAN CH93 0076 2011 6238 5295 7 pour "
    "le versement du salaire. Contact : jean-marc.dubois@example.ch, "
    "téléphone +41 21 555 34 21, domicilié 14 Rue du Lac, 1003 Lausanne. "
    "Le dossier fiscal référence le numéro de contribuable 12.345.678 et la "
    "pièce d'identité C1234567. Carte de crédit de test 4111 1111 1111 1111, "
    "expiration 04/28. Adresse IP du poste : 10.42.7.19. Le mandataire Sophie "
    "Müller (sophie.mueller@example.ch) valide la demande le 12 mars.\n"
)


class BenchLevel:
    """Aggregate measurement for one concurrency level."""

    __slots__ = ("concurrency", "wall_s", "completion_tokens", "errors", "cancelled")

    def __init__(self, concurrency: int) -> None:
        self.concurrency = concurrency
        self.wall_s = 0.0
        self.completion_tokens = 0
        self.errors = 0
        self.cancelled = False


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


def _bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _resolve_ministral(detector: Any) -> Optional[Any]:
    """Return the MinistralDetector instance from a composite or a bare detector."""
    ministral = getattr(detector, "ministral_detector", None)
    if ministral is not None:
        return ministral
    # A bare MinistralDetector exposes the private bench primitives we reuse.
    if hasattr(detector, "_build_payload") and hasattr(detector, "_build_chunker"):
        return detector
    return None


def _signature(host: str, port: Any, model: str) -> str:
    """Stable identity of the endpoint+model the concurrency is tuned for."""
    return f"{host}:{port}|{model}"


def _load_bench_sample() -> str:
    """Load the representative benchmark sample shipped next to this module.

    ``bench_sample.txt`` is a dense, multilingual real-world PII document so the
    bench measures the true per-request cost (long completions) instead of the
    short completions a synthetic sample would produce — which is what made the
    auto-tuner over-pick concurrency. Falls back to the synthetic block if the
    file is missing (minimal/packaged deployment).
    """
    path = Path(__file__).parent / "bench_sample.txt"
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        text = ""
    return text or _SAMPLE_BLOCK.format(n=0)


def _build_sample(chunk_size_tokens: int, max_concurrency: int) -> str:
    """Return a representative sample sized to yield > ``max_concurrency`` chunks.

    Uses the real-world dense sample, truncated (or repeated for a very large
    ``max_concurrency``) to roughly ``max_concurrency + 1`` chunks so the bench
    stays representative yet bounded in duration.
    """
    target_chars = (max_concurrency + 1) * chunk_size_tokens * BENCH_CHARS_PER_TOKEN
    base = _load_bench_sample()
    if len(base) >= target_chars:
        return base[:target_chars]
    parts: List[str] = []
    total = 0
    index = 0
    while total < target_chars:
        block = f"[bench:{index}]\n{base}"
        parts.append(block)
        total += len(block)
        index += 1
    return "".join(parts)


def _post_completion_tokens(
    client: httpx.Client, url: str, payload: Dict[str, Any]
) -> int:
    """POST one chunk, return the completion token count (0 on missing usage)."""
    resp = client.post(url, json=payload)
    resp.raise_for_status()
    usage = (resp.json() or {}).get("usage") or {}
    try:
        return int(usage.get("completion_tokens") or 0)
    except (TypeError, ValueError):
        return 0


def _run_level(
    ministral: Any,
    chunks: List[Any],
    url: str,
    concurrency: int,
    should_stop: Optional[Callable[[], bool]] = None,
) -> BenchLevel:
    """Send every chunk once through a pool of ``concurrency`` workers; time it.

    ``should_stop`` is consulted before each request so an operator cancellation
    takes effect within one chunk; the level is then flagged ``cancelled`` and
    its timing is meaningless.
    """
    level = BenchLevel(concurrency)
    client = ministral._get_client()
    payloads = [ministral._build_payload(chunk.text) for chunk in chunks]
    stop = should_stop or (lambda: False)

    def _task(payload: Dict[str, Any]) -> int:
        if level.cancelled or stop():
            level.cancelled = True
            return 0
        return _post_completion_tokens(client, url, payload)

    started = time.perf_counter()
    if concurrency <= 1:
        for payload in payloads:
            if level.cancelled:
                break
            level.completion_tokens += _safe_task(_task, payload, level)
    else:
        max_workers = min(concurrency, len(payloads))
        with ThreadPoolExecutor(
            max_workers=max_workers, thread_name_prefix="autotune"
        ) as pool:
            futures = [pool.submit(_task, p) for p in payloads]
            for future in futures:
                level.completion_tokens += _collect_future(future, level)
    level.wall_s = time.perf_counter() - started
    return level


def _safe_task(task, payload, level: BenchLevel) -> int:
    try:
        return task(payload)
    except (httpx.HTTPError, httpx.TimeoutException) as exc:
        level.errors += 1
        logger.warning("[AUTOTUNE] bench request failed: %s", exc)
        return 0


def _collect_future(future, level: BenchLevel) -> int:
    try:
        return future.result()
    except (httpx.HTTPError, httpx.TimeoutException) as exc:
        level.errors += 1
        logger.warning("[AUTOTUNE] bench request failed: %s", exc)
        return 0


def _decide(levels: Dict[int, BenchLevel], min_gain: float, eps: float) -> int:
    """Pick the concurrency: best speedup vs C=1, gated by min_gain, prefer smaller.

    All levels send the same total work, so wall-clock is directly comparable and
    ``speedup(C) = wall(1) / wall(C)``. Returns 1 when no level clears ``min_gain``.

    A level that produced ANY error is excluded from the choice: on a shared-KV
    LM Studio, too many concurrent full-size chunks overflow the context and the
    requests fail fast — which would otherwise masquerade as a great wall-clock
    and get picked. Excluding error levels caps the result at the highest
    concurrency the endpoint actually sustains for this chunk size. If C=1 itself
    errors (endpoint/config broken), fall back to 1.
    """
    base = levels.get(1)
    if base is None or base.wall_s <= 0 or base.errors > 0:
        return 1
    clean = {
        c: lvl for c, lvl in levels.items()
        if lvl.errors == 0 and lvl.wall_s > 0
    }
    speedups = {c: base.wall_s / lvl.wall_s for c, lvl in clean.items()}
    best_c = max(speedups, key=lambda c: speedups[c])
    best_speedup = speedups[best_c]
    if best_speedup < min_gain:
        return 1
    threshold = best_speedup * (1.0 - eps)
    for c in sorted(clean):
        if speedups[c] >= threshold:
            return c
    return best_c


def _probe_endpoint(client: httpx.Client, base_url: str) -> bool:
    """Quick liveness check of the LM Studio endpoint (GET /models)."""
    try:
        resp = client.get(f"{base_url}/models", timeout=PROBE_TIMEOUT_SECONDS)
        resp.raise_for_status()
        return True
    except (httpx.HTTPError, httpx.TimeoutException) as exc:
        logger.warning(
            "[AUTOTUNE] LM Studio endpoint unreachable at %s (%s) — "
            "skipping concurrency auto-tune, keeping stored value.",
            base_url, exc,
        )
        return False


def run_startup_autotune(detector: Any) -> Optional[int]:
    """Auto-tune and persist the Ministral concurrency once, at startup.

    Returns the chosen concurrency when a bench ran and persisted, else ``None``
    (any skip/failure path). Never raises.
    """
    try:
        return _run_startup_autotune_inner(detector)
    except Exception:  # pragma: no cover - defensive: never break startup
        logger.warning(
            "[AUTOTUNE] concurrency auto-tune aborted (unexpected error); "
            "keeping stored value.", exc_info=True,
        )
        return None


class BenchOutcome:
    """Result of a bench+decide run (shared by the startup and on-demand paths)."""

    __slots__ = ("chosen", "signature", "ran", "reason")

    def __init__(self, chosen: Optional[int], signature: str, ran: bool, reason: str):
        self.chosen = chosen        # chosen concurrency, or None when not measured
        self.signature = signature  # "host:port|model" the run targeted
        self.ran = ran              # True iff levels were actually measured
        self.reason = reason        # "ok" | "endpoint_down" | "insufficient_chunks" | ...


def _resolve_max_concurrency(requested: Optional[int]) -> int:
    """Highest level to measure: the operator's request when given, else the
    env default; always within 2..MAX_BENCH_CONCURRENCY."""
    base = requested if requested else _int_env("PII_AUTOTUNE_MAX_C", DEFAULT_MAX_CONCURRENCY)
    return max(2, min(MAX_BENCH_CONCURRENCY, int(base)))


def _cancelled(signature: str, level: int) -> BenchOutcome:
    logger.info("[AUTOTUNE] benchmark cancelled by operator at C=%d", level)
    return BenchOutcome(None, signature, False, "cancelled")


def _bench_and_decide(
    ministral: Any,
    config: dict,
    on_progress=None,
    max_concurrency: Optional[int] = None,
    should_stop: Optional[Callable[[], bool]] = None,
) -> BenchOutcome:
    """Health-probe, bench concurrency 1..MAX_C, decide. No DB persistence here.

    ``on_progress(percent:int, message:str)`` is invoked before each level when
    provided (drives the UI progress bar). ``max_concurrency`` overrides the env
    default (clamped to 2..MAX_BENCH_CONCURRENCY). ``should_stop()`` returning
    True aborts the run with reason ``cancelled``. Returns a
    :class:`BenchOutcome`; ``ran`` is False (with a reason) when the endpoint is
    unreachable, the sample yields too few chunks, or the run was cancelled.
    """
    host = config.get("lm_studio_host", "localhost")
    port = config.get("lm_studio_port", 1234)
    model = getattr(ministral, "_model_id", "unknown")
    signature = _signature(host, port, model)

    base_url = ministral._resolve_base_url(host, port)
    client = ministral._get_client()
    if not _probe_endpoint(client, base_url):
        return BenchOutcome(None, signature, False, "endpoint_down")

    max_c = _resolve_max_concurrency(max_concurrency)
    min_gain = _float_env("PII_AUTOTUNE_MIN_GAIN", DEFAULT_MIN_GAIN)
    eps = _float_env("PII_AUTOTUNE_TIE_EPS", DEFAULT_TIE_EPS)
    chunk_size = int(config.get("ministral_chunk_size") or 2048)
    overlap = int(config.get("ministral_overlap") or 410)

    sample = _build_sample(chunk_size, max_c)
    chunker = ministral._build_chunker(chunk_size, overlap)
    chunks = chunker.chunk_text(sample)
    if len(chunks) < 2:
        logger.warning(
            "[AUTOTUNE] sample produced %d chunk(s); cannot measure concurrency.",
            len(chunks),
        )
        return BenchOutcome(None, signature, False, "insufficient_chunks")

    url = f"{base_url}/chat/completions"
    logger.info(
        "[AUTOTUNE] benching concurrency 1..%d on %d chunks (chunk_size=%d) at %s",
        max_c, len(chunks), chunk_size, base_url,
    )

    levels: Dict[int, BenchLevel] = {}
    for c in range(1, max_c + 1):
        if should_stop is not None and should_stop():
            return _cancelled(signature, c)
        if on_progress is not None:
            on_progress(int((c - 1) / max_c * 95), f"Testing concurrency {c}/{max_c}")
        level = _run_level(ministral, chunks, url, c, should_stop)
        if level.cancelled:
            return _cancelled(signature, c)
        levels[c] = level
        logger.info(
            "[AUTOTUNE] C=%d wall=%.2fs compl_tok=%d errors=%d",
            c, level.wall_s, level.completion_tokens, level.errors,
        )

    chosen = _decide(levels, min_gain, eps)
    base_wall = levels[1].wall_s
    speedup = base_wall / levels[chosen].wall_s if levels[chosen].wall_s > 0 else 1.0
    logger.info(
        "[AUTOTUNE] chosen concurrency=%d (speedup=%.2fx vs sequential, "
        "min_gain=%.2f) for %s", chosen, speedup, min_gain, signature,
    )
    return BenchOutcome(chosen, signature, True, "ok")


def _run_startup_autotune_inner(detector: Any) -> Optional[int]:
    if not _bool_env("PII_AUTOTUNE_ENABLED", True):
        logger.info("[AUTOTUNE] disabled via PII_AUTOTUNE_ENABLED; skipping.")
        return None

    ministral = _resolve_ministral(detector)
    if ministral is None:
        logger.info("[AUTOTUNE] no Ministral detector present; skipping.")
        return None

    from pii_detector.infrastructure.adapter.out.database_config_adapter import (
        get_database_config_adapter,
    )

    adapter = get_database_config_adapter()
    config = adapter.fetch_config()
    if not config:
        logger.warning("[AUTOTUNE] no DB config available; skipping.")
        return None

    if not config.get("ministral_enabled", False):
        logger.info(
            "[AUTOTUNE] Ministral detector disabled in config; skipping "
            "(will tune on a later startup once enabled)."
        )
        return None
    if not config.get("ministral_concurrency_auto", True):
        logger.info("[AUTOTUNE] operator-pinned (auto=false); skipping.")
        return None

    host = config.get("lm_studio_host", "localhost")
    port = config.get("lm_studio_port", 1234)
    model = getattr(ministral, "_model_id", "unknown")
    signature = _signature(host, port, model)
    if config.get("ministral_concurrency_tuned_signature") == signature:
        logger.info(
            "[AUTOTUNE] already tuned for %s (concurrency=%s); skipping.",
            signature, config.get("ministral_concurrency"),
        )
        return None

    outcome = _bench_and_decide(ministral, config)
    if outcome.ran and outcome.chosen is not None:
        adapter.update_ministral_concurrency(outcome.chosen, outcome.signature)
        return outcome.chosen
    return None


def run_ondemand_autotune(
    detector: Any,
    on_progress=None,
    max_concurrency: Optional[int] = None,
    should_stop: Optional[Callable[[], bool]] = None,
) -> BenchOutcome:
    """Run the benchmark on demand (operator-triggered), returning the outcome.

    Unlike the startup path this FORCES a run: it ignores the auto flag and the
    already-tuned-signature skip (the operator explicitly clicked the button). It
    still health-probes and is error-aware. ``max_concurrency`` is the operator's
    upper bound and ``should_stop`` their cancellation switch. Persistence of the
    result is left to the caller (the poller) so it can also transition the job
    status. Never raises — a failure is reported as a non-``ran`` outcome.
    """
    try:
        ministral = _resolve_ministral(detector)
        if ministral is None:
            return BenchOutcome(None, "", False, "no_ministral")
        from pii_detector.infrastructure.adapter.out.database_config_adapter import (
            get_database_config_adapter,
        )

        config = get_database_config_adapter().fetch_config()
        if not config:
            return BenchOutcome(None, "", False, "no_config")
        return _bench_and_decide(
            ministral, config, on_progress, max_concurrency, should_stop
        )
    except Exception:  # pragma: no cover - defensive
        logger.warning(
            "[AUTOTUNE] on-demand run aborted (unexpected error)", exc_info=True
        )
        return BenchOutcome(None, "", False, "error")
