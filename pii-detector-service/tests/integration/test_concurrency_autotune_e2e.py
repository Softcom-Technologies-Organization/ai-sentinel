"""End-to-end tests for the Ministral chunk-prompt concurrency feature.

These exercise the *runtime* behaviour against a REAL HTTP endpoint over REAL
threads — the transport is never mocked. A tiny OpenAI-compatible server runs in
a background thread and records the maximum number of simultaneously in-flight
POSTs, which is what proves the concurrency actually fired (not just that a code
path was taken). Only the DB adapter is faked (an in-memory stand-in): its write
path is already covered by the unit tests, and the point here is the LM Studio
transport + thread pool, not persistence.

Three scenarios:

1. ``run_startup_autotune`` micro-benchmarks the fake endpoint, decides a
   concurrency >= 2 (real measured speedup clears the gate), and persists it with
   the ``host:port|model`` signature — while the server observes overlapping
   requests during the bench.
2. ``MinistralDetector.detect_pii(..., concurrency=4)`` dispatches chunk prompts
   concurrently, returns entities with correct GLOBAL offsets, and the server
   again observes overlapping requests.
3. With ``ministral_concurrency_auto = False`` the auto-tuner skips: it returns
   ``None``, never benches (no POST hits the server), and never persists.
"""

from __future__ import annotations

import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector
from pii_detector.infrastructure.model_management.concurrency_autotuner import (
    run_startup_autotune,
)

_GET_ADAPTER_TARGET = (
    "pii_detector.infrastructure.adapter.out.database_config_adapter"
    ".get_database_config_adapter"
)

# Complete email matcher: the fake "model" only returns spans that appear
# verbatim in the posted chunk, so recovered offsets are always locatable.
_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

# Per-request server delay: long enough that a pool of workers overlaps in the
# server, short enough to keep the suite fast.
_REQUEST_DELAY_S = 0.05


class _FakeLmStudioServer(ThreadingHTTPServer):
    """Threaded OpenAI-compatible server that records peak request concurrency."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._lock = threading.Lock()
        self.inflight = 0
        self.max_inflight = 0
        self.post_count = 0

    def reset(self) -> None:
        with self._lock:
            self.inflight = 0
            self.max_inflight = 0
            self.post_count = 0

    def enter_request(self) -> None:
        with self._lock:
            self.inflight += 1
            self.post_count += 1
            if self.inflight > self.max_inflight:
                self.max_inflight = self.inflight

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
        self.server.enter_request()
        try:
            time.sleep(_REQUEST_DELAY_S)
            pairs = [
                {"text": match.group(0), "label": "email"}
                for match in _EMAIL_RE.finditer(chunk_text)
            ]
        finally:
            self.server.leave_request()

        response = {
            "choices": [{"message": {"content": json.dumps(pairs)}}],
            "usage": {"completion_tokens": len(pairs) * 4 + 1},
        }
        self._send_json(200, response)

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


class _FakeDbAdapter:
    """In-memory DB adapter: serves a config dict, records concurrency writes."""

    def __init__(self, config: dict):
        self._config = config
        self.update_calls: list = []

    def fetch_config(self) -> dict:
        return dict(self._config)

    def update_ministral_concurrency(self, concurrency: int, tuned_signature: str) -> bool:
        self.update_calls.append((concurrency, tuned_signature))
        return True


@pytest.fixture()
def fake_server():
    server = _FakeLmStudioServer(("127.0.0.1", 0), _Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def _make_detector() -> MinistralDetector:
    detector = MinistralDetector()
    # Force the deterministic char-ratio FallbackChunker (no HF tokenizer): the
    # offline/degraded path keeps the whole test hermetic and reproducible.
    detector._get_tokenizer = lambda: None
    return detector


def _base_config(port: int, *, auto: bool) -> dict:
    return {
        "ministral_enabled": True,
        "ministral_concurrency_auto": auto,
        "ministral_concurrency_tuned_signature": None,
        "ministral_concurrency": 1,
        "lm_studio_host": "127.0.0.1",
        "lm_studio_port": port,
        # Small chunking so the synthetic sample yields many chunks quickly.
        "ministral_chunk_size": 16,
        "ministral_overlap": 4,
    }


@pytest.mark.integration
def test_autotuner_benches_and_persists_concurrency(fake_server, monkeypatch):
    detector = _make_detector()
    adapter = _FakeDbAdapter(_base_config(fake_server.port, auto=True))
    monkeypatch.setattr(_GET_ADAPTER_TARGET, lambda: adapter)
    monkeypatch.setenv("PII_AUTOTUNE_ENABLED", "true")
    monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "4")
    # Low gate: the real speedup on the fake server clears it easily.
    monkeypatch.setenv("PII_AUTOTUNE_MIN_GAIN", "1.05")

    fake_server.reset()
    chosen = run_startup_autotune(detector)

    assert chosen is not None, "expected a concurrency to be chosen"
    assert chosen >= 2, f"expected concurrency >= 2, got {chosen}"
    assert len(adapter.update_calls) == 1, "concurrency must be persisted exactly once"

    persisted_concurrency, persisted_signature = adapter.update_calls[0]
    assert persisted_concurrency == chosen
    assert persisted_concurrency >= 2
    assert persisted_signature == f"127.0.0.1:{fake_server.port}|{detector._model_id}"

    # The bench genuinely ran overlapping requests against the real endpoint.
    assert fake_server.max_inflight > 1, (
        f"bench never overlapped (max_inflight={fake_server.max_inflight})"
    )


@pytest.mark.integration
def test_parallel_detection_fires_concurrent_prompts(fake_server, monkeypatch):
    detector = _make_detector()

    # 10 fixed-width 40-char records, one unique email each. With chunk_size=20
    # tokens -> 80 chars (FallbackChunker, chars/token=4) and overlap=0, chunk
    # boundaries fall on multiples of 80 == 2*40, aligned with record starts, so
    # every email sits fully inside exactly one chunk (never straddles a cut).
    record_len = 40
    text = "".join(
        f"user{i:02d}@mail.test note. ".ljust(record_len, ".")[:record_len]
        for i in range(10)
    )

    pii_type_configs = {
        "MINISTRAL:EMAIL": {
            "enabled": True,
            "threshold": 0.5,
            "detector": "MINISTRAL",
            "detector_label": "email",
            "type_label": "Email address",
        }
    }

    fake_server.reset()
    entities = detector.detect_pii(
        text,
        pii_type_configs=pii_type_configs,
        chunk_size=20,  # tokens -> 80 chars, 5 chunks over the 400-char text
        overlap=0,
        lm_studio_host="127.0.0.1",
        lm_studio_port=fake_server.port,
        concurrency=4,
    )

    assert entities, "expected Ministral entities from the parallel detection"
    for entity in entities:
        # Offsets are GLOBAL: the recovered span must match the source text.
        assert text[entity.start:entity.end] == entity.text
        assert entity.pii_type == "EMAIL"

    assert fake_server.max_inflight >= 2, (
        f"parallel loop never overlapped (max_inflight={fake_server.max_inflight})"
    )


@pytest.mark.integration
def test_autotuner_skips_when_operator_pinned(fake_server, monkeypatch):
    detector = _make_detector()
    adapter = _FakeDbAdapter(_base_config(fake_server.port, auto=False))
    monkeypatch.setattr(_GET_ADAPTER_TARGET, lambda: adapter)
    monkeypatch.setenv("PII_AUTOTUNE_ENABLED", "true")

    fake_server.reset()
    result = run_startup_autotune(detector)

    assert result is None, "operator-pinned config must skip auto-tuning"
    assert adapter.update_calls == [], "no persist on the skip path"
    assert fake_server.post_count == 0, "no bench requests must hit the endpoint"
