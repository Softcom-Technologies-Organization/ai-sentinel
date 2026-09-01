"""Unit tests for the startup concurrency auto-tuner.

Coverage:
- ``_decide`` picks the best speedup, gated by min_gain, preferring the smaller
  concurrency on a near-tie;
- every skip path (disabled by env, Ministral off, operator-pinned, already tuned,
  endpoint down) returns None and never persists;
- the happy path persists the decided concurrency and its signature.

The bench itself (`_run_level`) is patched in the happy path so decisions are
deterministic and no real timing/HTTP is involved.
"""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from pii_detector.infrastructure.model_management import concurrency_autotuner as ct
from pii_detector.infrastructure.detector.ministral_detector import MinistralDetector

_ADAPTER_FACTORY = (
    "pii_detector.infrastructure.adapter.out.database_config_adapter"
    ".get_database_config_adapter"
)


def _level(concurrency: int, wall_s: float, tokens: int = 100, errors: int = 0) -> ct.BenchLevel:
    lvl = ct.BenchLevel(concurrency)
    lvl.wall_s = wall_s
    lvl.completion_tokens = tokens
    lvl.errors = errors
    return lvl


class TestDecide:
    def test_Should_ReturnOne_When_NoLevelClearsMinGain(self):
        levels = {1: _level(1, 1.0), 2: _level(2, 0.95), 3: _level(3, 0.92)}
        assert ct._decide(levels, min_gain=1.3, eps=0.10) == 1

    def test_Should_PickFasterLevel_When_SpeedupClearsMinGain(self):
        levels = {1: _level(1, 1.0), 2: _level(2, 0.5), 3: _level(3, 0.48)}
        # C=2 gives 2x; C=3 gives ~2.08x -> within 10% of best -> prefer smaller (2).
        assert ct._decide(levels, min_gain=1.3, eps=0.10) == 2

    def test_Should_PickLargest_When_GainKeepsGrowingBeyondEps(self):
        levels = {1: _level(1, 1.0), 2: _level(2, 0.7), 3: _level(3, 0.34)}
        # C=3 ~2.94x is far beyond C=2 (1.43x) -> not a tie -> pick 3.
        assert ct._decide(levels, min_gain=1.3, eps=0.10) == 3

    def test_Should_ReturnOne_When_BaselineMissingOrZero(self):
        assert ct._decide({2: _level(2, 0.5)}, min_gain=1.3, eps=0.10) == 1
        assert ct._decide({1: _level(1, 0.0)}, min_gain=1.3, eps=0.10) == 1


def _config(**overrides):
    base = {
        "ministral_enabled": True,
        "ministral_concurrency_auto": True,
        "ministral_concurrency_tuned_signature": None,
        "ministral_concurrency": 1,
        "lm_studio_host": "localhost",
        "lm_studio_port": 1234,
        "ministral_chunk_size": 16,
        "ministral_overlap": 4,
    }
    base.update(overrides)
    return base


def _ministral_with_probe(*, probe_ok: bool = True) -> MinistralDetector:
    detector = MinistralDetector()
    detector._get_tokenizer = lambda: None  # FallbackChunker
    client = MagicMock()
    if probe_ok:
        client.get.return_value.raise_for_status = MagicMock()
    else:
        import httpx

        client.get.side_effect = httpx.ConnectError("down")
    detector._client = client
    return detector


def _mock_adapter(config):
    adapter = MagicMock()
    adapter.fetch_config.return_value = config
    adapter.update_ministral_concurrency = MagicMock(return_value=True)
    return adapter


class TestSkipPaths:
    def test_Should_Skip_When_DisabledByEnv(self, monkeypatch):
        monkeypatch.setenv("PII_AUTOTUNE_ENABLED", "false")
        assert ct.run_startup_autotune(_ministral_with_probe()) is None

    def test_Should_Skip_When_MinistralDisabled(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        adapter = _mock_adapter(_config(ministral_enabled=False))
        with patch(_ADAPTER_FACTORY, return_value=adapter):
            assert ct.run_startup_autotune(_ministral_with_probe()) is None
        adapter.update_ministral_concurrency.assert_not_called()

    def test_Should_Skip_When_OperatorPinned(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        adapter = _mock_adapter(_config(ministral_concurrency_auto=False))
        with patch(_ADAPTER_FACTORY, return_value=adapter):
            assert ct.run_startup_autotune(_ministral_with_probe()) is None
        adapter.update_ministral_concurrency.assert_not_called()

    def test_Should_Skip_When_AlreadyTunedForSignature(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        detector = _ministral_with_probe()
        signature = f"localhost:1234|{detector._model_id}"
        adapter = _mock_adapter(
            _config(ministral_concurrency_tuned_signature=signature)
        )
        with patch(_ADAPTER_FACTORY, return_value=adapter):
            assert ct.run_startup_autotune(detector) is None
        adapter.update_ministral_concurrency.assert_not_called()

    def test_Should_Skip_When_EndpointDown(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        adapter = _mock_adapter(_config())
        with patch(_ADAPTER_FACTORY, return_value=adapter):
            result = ct.run_startup_autotune(_ministral_with_probe(probe_ok=False))
        assert result is None
        adapter.update_ministral_concurrency.assert_not_called()


class TestHappyPath:
    def test_Should_BenchAndPersist_When_TunableAndReachable(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "2")
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())

        # Deterministic bench: C=2 is 2x faster than C=1 -> decision must be 2.
        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            return _level(concurrency, 1.0 if concurrency == 1 else 0.5)

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            chosen = ct.run_startup_autotune(detector)

        assert chosen == 2
        signature = f"localhost:1234|{detector._model_id}"
        adapter.update_ministral_concurrency.assert_called_once_with(2, signature)

    def test_Should_PersistOne_When_NoScalingObserved(self, monkeypatch):
        monkeypatch.delenv("PII_AUTOTUNE_ENABLED", raising=False)
        monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "2")
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            return _level(concurrency, 1.0)  # flat: no gain

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            chosen = ct.run_startup_autotune(detector)

        assert chosen == 1
        signature = f"localhost:1234|{detector._model_id}"
        adapter.update_ministral_concurrency.assert_called_once_with(1, signature)


class TestDecideErrorAware:
    def test_Should_ExcludeLevelWithErrors_EvenIfFaster(self):
        # C=4 looks fastest only because its requests errored fast (context
        # overflow). It must be excluded; C=2 (error-free, 1.67x) wins.
        levels = {
            1: _level(1, 1.0),
            2: _level(2, 0.6),
            4: _level(4, 0.1, errors=4),
        }
        assert ct._decide(levels, min_gain=1.3, eps=0.10) == 2

    def test_Should_ReturnOne_When_BaselineErrors(self):
        levels = {1: _level(1, 0.5, errors=1), 2: _level(2, 0.2)}
        assert ct._decide(levels, min_gain=1.3, eps=0.10) == 1


class TestOnDemand:
    def test_Should_ForceRun_IgnoringAutoAndSignature(self, monkeypatch):
        monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "2")
        detector = _ministral_with_probe()
        signature = f"localhost:1234|{detector._model_id}"
        # auto=false AND already tuned: the startup path would skip; on-demand forces.
        cfg = _config(
            ministral_concurrency_auto=False,
            ministral_concurrency_tuned_signature=signature,
        )
        adapter = _mock_adapter(cfg)

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            return _level(concurrency, 1.0 if concurrency == 1 else 0.5)

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            outcome = ct.run_ondemand_autotune(detector)

        assert outcome.ran is True
        assert outcome.chosen == 2
        assert outcome.signature == signature
        # On-demand does NOT persist itself (the poller owns persistence).
        adapter.update_ministral_concurrency.assert_not_called()

    def test_Should_ReportEndpointDown_When_ProbeFails(self):
        adapter = _mock_adapter(_config())
        with patch(_ADAPTER_FACTORY, return_value=adapter):
            outcome = ct.run_ondemand_autotune(_ministral_with_probe(probe_ok=False))
        assert outcome.ran is False
        assert outcome.reason == "endpoint_down"

    def test_Should_ReportNoMinistral_When_DetectorLacksMinistral(self):
        outcome = ct.run_ondemand_autotune(object())
        assert outcome.ran is False
        assert outcome.reason == "no_ministral"

    def test_Should_InvokeProgressCallback_PerLevel(self, monkeypatch):
        monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "3")
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())
        seen = []

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            return _level(concurrency, 1.0)

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            ct.run_ondemand_autotune(
                detector, on_progress=lambda p, m: seen.append((p, m))
            )

        assert len(seen) == 3  # one call before each level 1..3
        assert seen[0][0] == 0
        assert "1/3" in seen[0][1]


class TestOnDemandBounds:
    def test_Should_UseRequestedMaxConcurrency_OverEnvDefault(self, monkeypatch):
        monkeypatch.setenv("PII_AUTOTUNE_MAX_C", "2")
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())
        seen = []

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            return _level(concurrency, 1.0)

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            ct.run_ondemand_autotune(
                detector, on_progress=lambda p, m: seen.append(m), max_concurrency=3
            )

        assert len(seen) == 3
        assert "3/3" in seen[-1]

    def test_Should_ClampRequestedMaxConcurrency_ToBounds(self):
        assert ct._resolve_max_concurrency(50) == ct.MAX_BENCH_CONCURRENCY
        assert ct._resolve_max_concurrency(1) == 2
        assert ct._resolve_max_concurrency(8) == 8


class TestOnDemandCancellation:
    def test_Should_ReportCancelled_When_StopRequestedBetweenLevels(self):
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())
        checks = {"n": 0}

        def should_stop():
            checks["n"] += 1
            return checks["n"] > 1  # the first level runs, the second is refused

        measured = []

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            measured.append(concurrency)
            return _level(concurrency, 1.0)

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            outcome = ct.run_ondemand_autotune(
                detector, max_concurrency=4, should_stop=should_stop
            )

        assert outcome.ran is False
        assert outcome.reason == "cancelled"
        assert outcome.chosen is None
        assert measured == [1]
        adapter.update_ministral_concurrency.assert_not_called()

    def test_Should_ReportCancelled_When_LevelWasInterrupted(self):
        detector = _ministral_with_probe()
        adapter = _mock_adapter(_config())

        def fake_run_level(ministral, chunks, url, concurrency, should_stop=None):
            level = _level(concurrency, 1.0)
            level.cancelled = concurrency == 2
            return level

        with patch(_ADAPTER_FACTORY, return_value=adapter), \
                patch.object(ct, "_run_level", side_effect=fake_run_level):
            outcome = ct.run_ondemand_autotune(detector, max_concurrency=4)

        assert outcome.ran is False
        assert outcome.reason == "cancelled"

    def test_Should_StopSendingChunks_When_StopRequestedMidLevel(self):
        detector = _ministral_with_probe()
        chunks = [MagicMock(text=f"chunk {i}") for i in range(5)]
        sent = []
        verdicts = iter([False, False, True, True, True])

        def fake_post(client, url, payload):
            sent.append(payload)
            return 1

        with patch.object(ct, "_post_completion_tokens", side_effect=fake_post):
            level = ct._run_level(
                detector, chunks, "http://localhost:1234/v1/chat/completions", 1,
                should_stop=lambda: next(verdicts),
            )

        assert level.cancelled is True
        assert len(sent) == 2


class TestSampleBuilder:
    def test_Should_ProduceEnoughChunks_When_SizedForConcurrency(self):
        sample = ct._build_sample(chunk_size_tokens=16, max_concurrency=4)
        # Sample must be large enough that the char chunker yields > max_concurrency
        # windows so every slot has work.
        from pii_detector.infrastructure.text_processing.semantic_chunker import (
            FallbackChunker,
        )

        chunks = FallbackChunker(chunk_size=16, overlap=4, chars_per_token=4).chunk_text(sample)
        assert len(chunks) > 4
