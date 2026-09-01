"""End-to-end tests for the on-demand (operator-triggered) concurrency benchmark.

These exercise ``run_ondemand_autotune`` plus the gRPC poller body it is wired
into, against a REAL OpenAI-compatible HTTP endpoint over REAL threads — the
transport is never mocked. A tiny server runs in a background thread and records
the peak number of simultaneously in-flight POSTs, which is what proves the
concurrency actually fired (not merely that a code path was taken). Only the DB
adapter is faked (an in-memory stand-in): its write path is unit-tested already;
the point here is the LM Studio transport, the thread pool, and the error-aware
decision.

Three scenarios:

1. Force-run + progress + persistence: even with ``ministral_concurrency_auto``
   false and a stale tuned signature, the on-demand path benches anyway, reports
   progress, and the simulated poller persists the chosen concurrency via
   ``complete_bench_job`` while the server observes overlapping requests.
2. Error-aware backoff (the key regression guard): the fake endpoint returns HTTP
   400 once more than two POSTs are in flight (a shared-KV context overflow). The
   decision must exclude the erroring levels (C=3, C=4) and cap the result at 2 —
   proving 4 parallel full chunks that overflow the context are no longer chosen.
3. Endpoint down: the config points at a closed port; the run reports
   ``ran=False`` / ``endpoint_down`` and the simulated poller fails the job
   without completing it.
"""

from __future__ import annotations

import json
import re
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector
from pii_detector.infrastructure.model_management.concurrency_autotuner import (
    run_ondemand_autotune,
)

# The autotuner imports the adapter factory lazily inside the function, so the
# patch must target its SOURCE module, not the autotuner module.
_GET_ADAPTER_TARGET = (
    "pii_detector.infrastructure.adapter.out.database_config_adapter"
    ".get_database_config_adapter"
)

# Complete email matcher: the fake "model" only echoes spans present verbatim in
# the posted chunk, so recovered offsets are always locatable.
_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

# Per-request server delay: long enough that a pool of workers overlaps in the
# server, short enough to keep the suite fast.
_REQUEST_DELAY_S = 0.05


class _FakeLmStudioServer(ThreadingHTTPServer):
    """Threaded OpenAI-compatible server recording peak request concurrency.

    ``overflow_threshold`` models a shared-KV context limit: when set, any request
    that finds strictly more than ``overflow_threshold`` POSTs in flight is
    rejected with HTTP 400 (as an overloaded LM Studio slot pool would).
    """

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, server_address, handler, overflow_threshold=None):
        super().__init__(server_address, handler)
        self._lock = threading.Lock()
        self.inflight = 0
        self.max_inflight = 0
        self.post_count = 0
        self.overflow_threshold = overflow_threshold

    def reset(self) -> None:
        with self._lock:
            self.inflight = 0
            self.max_inflight = 0
            self.post_count = 0

    def enter_request(self) -> int:
        with self._lock:
            self.inflight += 1
            self.post_count += 1
            if self.inflight > self.max_inflight:
                self.max_inflight = self.inflight
            return self.inflight

    def leave_request(self) -> None:
        with self._lock:
            self.inflight -= 1

    @property
    def port(self) -> int:
        return self.server_address[1]


class _Handler(BaseHTTPRequestHandler):
    """GET /v1/models (health) + POST /v1/chat/completions (extraction)."""

    def log_message(self, *args) -> None:  # noqa: D401 - silence access log
        return

    def do_GET(self) -> None:
        if self.path.endswith("/models"):
            self._send_json(200, {"object": "list", "data": [{"id": "fake-model"}]})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        if not self.path.endswith("/chat/completions"):
            self._send_json(404, {"error": "not found"})
            return

        chunk_text = self._read_user_content()

        # Hold the in-flight count up across the delay so overlapping requests
        # register as real concurrency (the sleep is outside the counter lock).
        current = self.server.enter_request()
        try:
            time.sleep(_REQUEST_DELAY_S)
            threshold = self.server.overflow_threshold
            if threshold is not None and current > threshold:
                # Too many concurrent full chunks for the shared KV context.
                self._send_json(400, {"error": "context overflow"})
                return
            pairs = [
                {"text": match.group(0), "label": "email"}
                for match in _EMAIL_RE.finditer(chunk_text)
            ]
            response = {
                "choices": [{"message": {"content": json.dumps(pairs)}}],
                "usage": {"completion_tokens": len(pairs) * 4 + 1},
            }
            self._send_json(200, response)
        finally:
            self.server.leave_request()

    def _read_user_content(self) -> str:
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8")) if raw else {}
        except (ValueError, UnicodeDecodeError):
            return ""
        for message in reversed(body.get("messages") or []):
            if message.get("role") == "user":
                return str(message.get("content") or "")
        return ""

    def _send_json(self, status: int, payload: dict) -> None:
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class _FakeOndemandAdapter:
    """In-memory DB adapter mirroring the on-demand bench job surface."""

    def __init__(self, config: dict):
        self._config = config
        self.claim_calls = 0
        self.progress_calls: list = []
        self.completed: list = []
        self.failed: list = []
        self.cancelled = 0
        self.max_concurrency = 4

    def fetch_config(self) -> dict:
        return dict(self._config)

    def claim_bench_job(self):
        self.claim_calls += 1
        return self.max_concurrency if self.claim_calls == 1 else None

    def is_bench_cancel_requested(self) -> bool:
        return False

    def cancel_bench_job(self) -> None:
        self.cancelled += 1

    def update_bench_progress(self, progress: int, message: str) -> None:
        self.progress_calls.append((progress, message))

    def complete_bench_job(self, concurrency: int, signature: str) -> None:
        self.completed.append((concurrency, signature))

    def fail_bench_job(self, message: str) -> None:
        self.failed.append(message)


@pytest.fixture
def server_factory():
    servers: list = []

    def _make(overflow_threshold=None) -> _FakeLmStudioServer:
        server = _FakeLmStudioServer(
            ("127.0.0.1", 0), _Handler, overflow_threshold
        )
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        servers.append((server, thread))
        return server

    yield _make

    for server, thread in servers:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def _make_detector() -> MinistralDetector:
    detector = MinistralDetector()
    # Force the deterministic char-ratio FallbackChunker (no HF tokenizer): the
    # offline/degraded path keeps the whole test hermetic and reproducible.
    detector._get_tokenizer = lambda: None
    return detector


def _base_config(port: int) -> dict:
    return {
        "ministral_enabled": True,
        "ministral_concurrency_auto": True,
        "ministral_concurrency_tuned_signature": None,
        "ministral_concurrency": 1,
        "lm_studio_host": "127.0.0.1",
        "lm_studio_port": port,
        # Small chunking so the synthetic sample yields many chunks quickly.
        "ministral_chunk_size": 16,
        "ministral_overlap": 4,
    }


def _find_closed_port() -> int:
    """Bind then release an ephemeral port so it is (very likely) closed."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return port


def _run_poller_once(detector, adapter):
    """Simulate the gRPC servicer's bench-job poller body for one tick."""
    max_concurrency = adapter.claim_bench_job()
    if max_concurrency is None:
        return None
    outcome = run_ondemand_autotune(
        detector,
        on_progress=adapter.update_bench_progress,
        max_concurrency=max_concurrency,
        should_stop=adapter.is_bench_cancel_requested,
    )
    if outcome.ran and outcome.chosen is not None:
        adapter.complete_bench_job(outcome.chosen, outcome.signature)
    elif outcome.reason == "cancelled":
        adapter.cancel_bench_job()
    else:
        adapter.fail_bench_job(outcome.reason)
    return outcome


@pytest.mark.integration
def test_ondemand_forces_bench_reports_progress_and_persists(
    server_factory, monkeypatch
):
    server = server_factory()
    detector = _make_detector()
    config = _base_config(server.port)
    # Operator pinned + already tuned for a different signature: the on-demand
    # path must ignore both and bench anyway.
    config["ministral_concurrency_auto"] = False
    config["ministral_concurrency_tuned_signature"] = "stale-host:0|old-model"
    adapter = _FakeOndemandAdapter(config)
    monkeypatch.setattr(_GET_ADAPTER_TARGET, lambda: adapter)
    monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "4")
    # Low gate: the real speedup on the fake server clears it easily.
    monkeypatch.setenv("PII_AUTOTUNE_MIN_GAIN", "1.05")

    server.reset()
    outcome = _run_poller_once(detector, adapter)

    assert outcome is not None, "on-demand must force a bench"
    assert outcome.ran, "on-demand must force a bench"
    assert adapter.claim_calls == 1

    # Progress reported multiple times, non-decreasing percent, "<c>/4" messages.
    assert len(adapter.progress_calls) >= 2
    percents = [p for p, _ in adapter.progress_calls]
    assert percents == sorted(percents), f"progress went backwards: {percents}"
    for _, message in adapter.progress_calls:
        assert re.search(r"\d+/4", message), f"missing 'k/4' marker: {message!r}"

    # Persisted exactly once via complete_bench_job with the FRESH signature.
    assert adapter.failed == []
    assert len(adapter.completed) == 1
    chosen, signature = adapter.completed[0]
    assert isinstance(chosen, int)
    assert chosen >= 1
    assert chosen == outcome.chosen
    assert signature == f"127.0.0.1:{server.port}|{detector._model_id}"

    # The forced bench genuinely overlapped requests against the real endpoint.
    assert server.max_inflight > 1, (
        f"bench never overlapped (max_inflight={server.max_inflight})"
    )


@pytest.mark.integration
def test_ondemand_backoff_excludes_erroring_levels(server_factory, monkeypatch):
    # Endpoint rejects (HTTP 400) whenever more than two POSTs are in flight,
    # so C=3 and C=4 error and must be excluded from the decision.
    server = server_factory(overflow_threshold=2)
    detector = _make_detector()
    adapter = _FakeOndemandAdapter(_base_config(server.port))
    monkeypatch.setattr(_GET_ADAPTER_TARGET, lambda: adapter)
    monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "4")
    monkeypatch.setenv("PII_AUTOTUNE_MIN_GAIN", "1.05")

    server.reset()
    outcome = _run_poller_once(detector, adapter)

    assert outcome.ran, f"bench did not run: {outcome.reason}"
    assert outcome.chosen is not None
    assert outcome.chosen <= 2, (
        "error-aware decision must cap at 2 (C=3/C=4 overflow the context), "
        f"got {outcome.chosen}"
    )
    assert len(adapter.completed) == 1
    assert adapter.completed[0][0] == outcome.chosen
    # The endpoint really was driven past two concurrent requests.
    assert server.max_inflight >= 3, (
        f"never pushed past 2 in flight (max_inflight={server.max_inflight})"
    )


@pytest.mark.integration
def test_ondemand_endpoint_down_fails_job(monkeypatch):
    closed_port = _find_closed_port()
    detector = _make_detector()
    adapter = _FakeOndemandAdapter(_base_config(closed_port))
    monkeypatch.setattr(_GET_ADAPTER_TARGET, lambda: adapter)
    monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "4")

    outcome = _run_poller_once(detector, adapter)

    assert outcome.ran is False
    assert outcome.reason == "endpoint_down"
    assert adapter.failed == ["endpoint_down"], "poller must fail the job"
    assert adapter.completed == [], "a down endpoint must not complete the job"
