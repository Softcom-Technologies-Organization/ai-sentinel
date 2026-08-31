"""Unit tests for the detector worker pool.

This module parallelises DetectPII across N inference processes, so its
guarantees are operational rather than functional — and none of them were
covered:

- ``pool_size_from_env`` gates the whole feature: 1 worker means "no pool", and
  an unparsable value must not take the service down at startup;
- ``_worker_detect_with_stats`` falls back to ``detect_pii`` when the detector
  predates per-detector stats, which is the documented backward-compatible path;
- ``_worker_init`` must pin the thread counts and warm the worker up, and a
  failing warmup must not leave the worker unusable;
- the pool must start with ``fork`` when the platform offers it, because the
  copy-on-write inheritance of the preloaded weights is the reason the pool is
  affordable at all;
- ``warm_up`` must submit exactly one task per worker.

No real process is ever started: ``multiprocessing`` is faked so the tests stay
fast and deterministic.
"""
from __future__ import annotations

import importlib
import os
from unittest.mock import MagicMock, Mock

import pytest

from pii_detector.infrastructure.model_management import detector_worker_pool as wp

# The grpc adapter lives under a package named ``in``, a Python keyword, so it
# can only be reached through importlib — same as the production code does.
pii_service = importlib.import_module(
    'pii_detector.infrastructure.adapter.in.grpc.pii_service')


@pytest.fixture(autouse=True)
def isolate_worker_state(monkeypatch):
    """Keeps the per-process detector and the thread env vars from leaking.

    ``_worker_init`` writes both, so they are registered with monkeypatch here
    and restored after each test whoever changed them in between.
    """
    monkeypatch.setattr(wp, '_WORKER_DETECTOR', None)
    monkeypatch.setenv('OMP_NUM_THREADS', '1')
    monkeypatch.setenv('MKL_NUM_THREADS', '1')


class TestPoolSizeFromEnv:
    def test_Should_DisablePool_When_VariableAbsent(self, monkeypatch):
        monkeypatch.delenv('PII_WORKER_PROCESSES', raising=False)
        assert wp.pool_size_from_env() == 0

    @pytest.mark.parametrize('value', ['0', '1', '-2'])
    def test_Should_DisablePool_When_ValueIsOneOrLess(self, monkeypatch, value):
        # A single worker buys nothing but pickling overhead, so it means "off".
        monkeypatch.setenv('PII_WORKER_PROCESSES', value)
        assert wp.pool_size_from_env() == 0

    def test_Should_ReturnRequestedSize_When_ValueIsAboveOne(self, monkeypatch):
        monkeypatch.setenv('PII_WORKER_PROCESSES', '3')
        assert wp.pool_size_from_env() == 3

    def test_Should_DisablePool_When_ValueIsNotAnInteger(self, monkeypatch):
        # A typo in the deployment must not stop the service from booting.
        monkeypatch.setenv('PII_WORKER_PROCESSES', 'four')
        assert wp.pool_size_from_env() == 0


class TestWorkerDetect:
    def test_Should_ForwardThresholdAndKwargs_When_Detecting(self, monkeypatch):
        detector = Mock()
        detector.detect_pii.return_value = ['entity']
        monkeypatch.setattr(wp, '_WORKER_DETECTOR', detector)

        result = wp._worker_detect(('some text', 0.7, {'pii_type_configs': {'IBAN': 1}}))

        assert result == ['entity']
        detector.detect_pii.assert_called_once_with(
            'some text', 0.7, pii_type_configs={'IBAN': 1})

    def test_Should_CallWithoutExtraKwargs_When_PayloadCarriesNone(self, monkeypatch):
        detector = Mock()
        monkeypatch.setattr(wp, '_WORKER_DETECTOR', detector)

        wp._worker_detect(('some text', 0.5, None))

        detector.detect_pii.assert_called_once_with('some text', 0.5)


class TestWorkerDetectWithStats:
    def test_Should_UseStatsAwareCall_When_DetectorSupportsIt(self, monkeypatch):
        detector = Mock()
        detector.detect_pii_with_stats.return_value = (['entity'], [{'source': 'REGEX'}])
        monkeypatch.setattr(wp, '_WORKER_DETECTOR', detector)

        entities, stats = wp._worker_detect_with_stats(('text', 0.4, {'flag': True}))

        assert entities == ['entity']
        assert stats == [{'source': 'REGEX'}]
        detector.detect_pii_with_stats.assert_called_once_with('text', 0.4, flag=True)
        detector.detect_pii.assert_not_called()

    def test_Should_FallBackToPlainDetect_When_DetectorHasNoStats(self, monkeypatch):
        # Backward-compatible path: empty stats leave the proto field empty
        # instead of failing the request.
        detector = Mock(spec=['detect_pii'])
        detector.detect_pii.return_value = ['entity']
        monkeypatch.setattr(wp, '_WORKER_DETECTOR', detector)

        entities, stats = wp._worker_detect_with_stats(('text', 0.4, None))

        assert entities == ['entity']
        assert stats == []


class TestWorkerInit:
    @staticmethod
    def _patch_detector(monkeypatch, detector):
        monkeypatch.setattr(pii_service, 'get_detector_instance', lambda: detector)

    @staticmethod
    def _patch_torch(monkeypatch):
        import torch
        set_num_threads = Mock()
        monkeypatch.setattr(torch, 'set_num_threads', set_num_threads, raising=False)
        return set_num_threads

    def test_Should_PinThreadsAndWarmUp_When_WorkerStarts(self, monkeypatch):
        detector = Mock()
        self._patch_detector(monkeypatch, detector)
        set_num_threads = self._patch_torch(monkeypatch)

        wp._worker_init(2)

        assert os.environ['OMP_NUM_THREADS'] == '2'
        assert os.environ['MKL_NUM_THREADS'] == '2'
        set_num_threads.assert_called_once_with(2)
        assert wp._WORKER_DETECTOR is detector
        # The first forward must happen in the worker, never in the parent.
        detector.detect_pii.assert_called_once()

    def test_Should_StayUsable_When_WarmupRaises(self, monkeypatch):
        detector = Mock()
        detector.detect_pii.side_effect = RuntimeError('model not loaded yet')
        self._patch_detector(monkeypatch, detector)
        self._patch_torch(monkeypatch)

        wp._worker_init(1)

        # A failed warmup is logged and swallowed: the worker still serves.
        assert wp._WORKER_DETECTOR is detector

    def test_Should_SkipWarmup_When_DetectorIsUnavailable(self, monkeypatch):
        self._patch_detector(monkeypatch, None)
        self._patch_torch(monkeypatch)

        wp._worker_init(1)

        assert wp._WORKER_DETECTOR is None


class TestDetectorWorkerPool:
    @staticmethod
    def _fake_context(monkeypatch, start_methods=('fork', 'spawn')):
        pool = MagicMock()
        ctx = MagicMock()
        ctx.Pool.return_value = pool
        monkeypatch.setattr(wp.mp, 'get_all_start_methods', lambda: list(start_methods))
        monkeypatch.setattr(wp.mp, 'get_context', Mock(return_value=ctx))
        return ctx, pool

    def test_Should_StartWithFork_When_PlatformSupportsIt(self, monkeypatch):
        # fork is what makes the preloaded weights shared copy-on-write.
        ctx, _ = self._fake_context(monkeypatch, start_methods=('fork', 'spawn'))

        wp.DetectorWorkerPool(processes=3, torch_threads=2)

        wp.mp.get_context.assert_called_once_with('fork')
        ctx.Pool.assert_called_once_with(
            processes=3, initializer=wp._worker_init, initargs=(2,))

    def test_Should_FallBackToSpawn_When_ForkIsUnavailable(self, monkeypatch):
        self._fake_context(monkeypatch, start_methods=('spawn',))

        wp.DetectorWorkerPool(processes=2, torch_threads=1)

        wp.mp.get_context.assert_called_once_with('spawn')

    def test_Should_DelegateToPool_When_Detecting(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        pool.apply.return_value = ['entity']
        subject = wp.DetectorWorkerPool(processes=2, torch_threads=1)

        assert subject.detect('text', 0.6, {'flag': True}) == ['entity']
        pool.apply.assert_called_once_with(
            wp._worker_detect, (('text', 0.6, {'flag': True}),))

    def test_Should_DelegateToStatsAwareTask_When_DetectingWithStats(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        pool.apply.return_value = (['entity'], [])
        subject = wp.DetectorWorkerPool(processes=2, torch_threads=1)

        assert subject.detect_with_stats('text', 0.6) == (['entity'], [])
        pool.apply.assert_called_once_with(
            wp._worker_detect_with_stats, (('text', 0.6, None),))

    def test_Should_SubmitOneTaskPerWorker_When_WarmingUp(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        subject = wp.DetectorWorkerPool(processes=3, torch_threads=1)

        subject.warm_up()

        # One task per worker, otherwise a worker could still be initialising
        # when the server announces itself ready.
        assert pool.apply_async.call_count == 3

    def test_Should_CompleteWarmUp_When_OneTaskFails(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        failing = MagicMock()
        failing.get.side_effect = RuntimeError('worker died')
        pool.apply_async.return_value = failing
        subject = wp.DetectorWorkerPool(processes=2, torch_threads=1)

        subject.warm_up()

    def test_Should_TerminateAndJoin_When_ShuttingDown(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        subject = wp.DetectorWorkerPool(processes=2, torch_threads=1)

        subject.shutdown()

        pool.terminate.assert_called_once()
        pool.join.assert_called_once()

    def test_Should_NotRaise_When_ShutdownFails(self, monkeypatch):
        _, pool = self._fake_context(monkeypatch)
        pool.terminate.side_effect = OSError('already gone')
        subject = wp.DetectorWorkerPool(processes=2, torch_threads=1)

        subject.shutdown()
