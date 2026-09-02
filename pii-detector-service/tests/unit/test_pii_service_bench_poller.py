"""Unit tests for the on-demand benchmark poller body (``_run_bench_job_once``).

One tick claims at most one job and persists its outcome through the adapter:
DONE with the chosen concurrency, CANCELLED when the operator stopped the run,
FAILED otherwise.
"""
from __future__ import annotations

import importlib
from unittest.mock import MagicMock

from pii_detector.infrastructure.model_management.concurrency_autotuner import BenchOutcome

pii_service = importlib.import_module(
    "pii_detector.infrastructure.adapter.in.grpc.pii_service"
)
PIIDetectionServicer = pii_service.PIIDetectionServicer


def _servicer() -> PIIDetectionServicer:
    servicer = PIIDetectionServicer.__new__(PIIDetectionServicer)
    servicer.detector = MagicMock()
    return servicer


class TestRunBenchJobOnce:
    def test_Should_DoNothing_When_NoJobPending(self):
        adapter = MagicMock()
        adapter.claim_bench_job.return_value = None
        run_autotune = MagicMock()

        ran = _servicer()._run_bench_job_once(adapter, run_autotune)

        assert ran is False
        run_autotune.assert_not_called()
        adapter.complete_bench_job.assert_not_called()

    def test_Should_PersistChosenConcurrency_When_BenchSucceeds(self):
        adapter = MagicMock()
        adapter.claim_bench_job.return_value = 8
        run_autotune = MagicMock(return_value=BenchOutcome(4, "host:1234|model", True, "ok"))
        servicer = _servicer()

        ran = servicer._run_bench_job_once(adapter, run_autotune)

        assert ran is True
        run_autotune.assert_called_once_with(
            servicer.detector,
            on_progress=adapter.update_bench_progress,
            max_concurrency=8,
            should_stop=adapter.is_bench_cancel_requested,
        )
        adapter.complete_bench_job.assert_called_once_with(4, "host:1234|model")
        adapter.cancel_bench_job.assert_not_called()
        adapter.fail_bench_job.assert_not_called()

    def test_Should_MarkCancelled_When_OperatorStoppedTheRun(self):
        adapter = MagicMock()
        adapter.claim_bench_job.return_value = 6
        run_autotune = MagicMock(return_value=BenchOutcome(None, "host:1234|model", False, "cancelled"))

        _servicer()._run_bench_job_once(adapter, run_autotune)

        adapter.cancel_bench_job.assert_called_once()
        adapter.complete_bench_job.assert_not_called()
        adapter.fail_bench_job.assert_not_called()

    def test_Should_MarkFailed_When_BenchCouldNotRun(self):
        adapter = MagicMock()
        adapter.claim_bench_job.return_value = 4
        run_autotune = MagicMock(return_value=BenchOutcome(None, "host:1234|model", False, "endpoint_down"))

        _servicer()._run_bench_job_once(adapter, run_autotune)

        adapter.fail_bench_job.assert_called_once()
        assert "endpoint_down" in adapter.fail_bench_job.call_args.args[0]
        adapter.complete_bench_job.assert_not_called()
