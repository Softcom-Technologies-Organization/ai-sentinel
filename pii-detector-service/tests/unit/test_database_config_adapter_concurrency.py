"""Tests for the ministral_concurrency* columns in ``DatabaseConfigAdapter``
(migration 015): defensive read normalisation + the auto-tuner write path.
"""
from __future__ import annotations

from contextlib import contextmanager
from typing import Any, Dict, Iterator, List, Optional
from unittest.mock import MagicMock, patch

import psycopg2

from pii_detector.infrastructure.adapter.out.database_config_adapter import (
    DatabaseConfigAdapter,
)


def _row(**overrides: Any) -> Dict[str, Any]:
    row = {
        "presidio_enabled": True,
        "regex_enabled": False,
        "ministral_enabled": False,
        "ministral_chunk_size": 2048,
        "ministral_overlap": 410,
        "default_threshold": 0.5,
        "postfilter_enabled": False,
        "lm_studio_host": "localhost",
        "lm_studio_port": 1234,
        "ministral_concurrency": 4,
        "ministral_concurrency_auto": True,
        "ministral_concurrency_tuned_signature": "localhost:1234|model",
    }
    row.update(overrides)
    return row


@contextmanager
def _patched_adapter(
    fetchone_results: List[Optional[Dict[str, Any]]],
    execute_side_effects: Optional[List[Optional[Exception]]] = None,
    rowcount: int = 1,
) -> Iterator[tuple]:
    cursor = MagicMock()
    fetch_iter = iter(fetchone_results)
    cursor.fetchone.side_effect = lambda: next(fetch_iter)
    cursor.rowcount = rowcount
    if execute_side_effects:
        cursor.execute.side_effect = execute_side_effects

    connection = MagicMock()
    connection.cursor.return_value = cursor

    with patch.object(psycopg2, "connect", return_value=connection):
        yield DatabaseConfigAdapter(), cursor, connection


class TestFetchConfigConcurrency:
    def test_should_surface_concurrency_fields_from_row(self) -> None:
        with _patched_adapter([_row()]) as (adapter, _c, _conn):
            config = adapter.fetch_config()
        assert config["ministral_concurrency"] == 4
        assert config["ministral_concurrency_auto"] is True
        assert config["ministral_concurrency_tuned_signature"] == "localhost:1234|model"

    def test_should_default_concurrency_when_null(self) -> None:
        with _patched_adapter(
            [_row(ministral_concurrency=None, ministral_concurrency_auto=None)]
        ) as (adapter, _c, _conn):
            config = adapter.fetch_config()
        assert config["ministral_concurrency"] == 1
        assert config["ministral_concurrency_auto"] is True

    def test_should_default_when_columns_missing(self) -> None:
        undefined = psycopg2.errors.UndefinedColumn("column does not exist")
        fallback_row = {
            "presidio_enabled": True,
            "regex_enabled": False,
            "default_threshold": 0.5,
        }
        with _patched_adapter(
            fetchone_results=[fallback_row],
            execute_side_effects=[undefined, None],
        ) as (adapter, _c, _conn):
            config = adapter.fetch_config()
        assert config["ministral_concurrency"] == 1
        assert config["ministral_concurrency_auto"] is True
        assert config["ministral_concurrency_tuned_signature"] is None


class TestUpdateMinistralConcurrency:
    def test_should_issue_update_and_commit(self) -> None:
        with _patched_adapter([], rowcount=1) as (adapter, cursor, connection):
            ok = adapter.update_ministral_concurrency(4, "localhost:1234|model")
        assert ok is True
        connection.commit.assert_called_once()
        sql, params = cursor.execute.call_args.args
        assert "UPDATE pii_detection_config" in sql
        assert "ministral_concurrency" in sql
        assert params[0] == 4
        assert params[1] == "localhost:1234|model"

    def test_should_return_false_when_no_row_matched(self) -> None:
        with _patched_adapter([], rowcount=0) as (adapter, _c, _conn):
            ok = adapter.update_ministral_concurrency(2, "sig")
        assert ok is False

    def test_should_return_false_and_rollback_on_db_error(self) -> None:
        err = psycopg2.OperationalError("boom")
        with _patched_adapter([], execute_side_effects=[err]) as (adapter, _c, conn):
            ok = adapter.update_ministral_concurrency(2, "sig")
        assert ok is False
        conn.rollback.assert_called_once()


class TestBenchJobMethods:
    def test_claim_returns_requested_max_and_flips_flag(self) -> None:
        with _patched_adapter([(8,)], rowcount=1) as (adapter, cursor, conn):
            claimed = adapter.claim_bench_job()
        assert claimed == 8
        conn.commit.assert_called_once()
        sql = cursor.execute.call_args.args[0]
        assert "concurrency_bench_requested = false" in sql
        assert "concurrency_bench_requested = true" in sql  # atomic WHERE guard
        assert "RETURNING concurrency_bench_max_concurrency" in sql

    def test_claim_returns_none_when_no_pending_request(self) -> None:
        with _patched_adapter([], rowcount=0) as (adapter, _c, _conn):
            assert adapter.claim_bench_job() is None

    def test_update_progress_writes_percent_and_message_for_running_job_only(self) -> None:
        with _patched_adapter([], rowcount=1) as (adapter, cursor, _conn):
            adapter.update_bench_progress(50, "Testing concurrency 2/4")
        sql, params = cursor.execute.call_args.args
        assert "concurrency_bench_progress" in sql
        # A CANCEL_REQUESTED status written by the API must survive a late tick.
        assert "WHERE id = 1 AND concurrency_bench_status = 'RUNNING'" in sql
        assert params == (50, "Testing concurrency 2/4")

    def test_is_cancel_requested_reads_status(self) -> None:
        with _patched_adapter([("CANCEL_REQUESTED",)], rowcount=1) as (adapter, _c, _conn):
            assert adapter.is_bench_cancel_requested() is True
        with _patched_adapter([("RUNNING",)], rowcount=1) as (adapter, _c, _conn):
            assert adapter.is_bench_cancel_requested() is False

    def test_cancel_marks_job_cancelled_and_clears_request(self) -> None:
        with _patched_adapter([], rowcount=1) as (adapter, cursor, _conn):
            adapter.cancel_bench_job()
        sql = cursor.execute.call_args.args[0]
        assert "'CANCELLED'" in sql
        assert "concurrency_bench_requested = false" in sql

    def test_complete_persists_result_and_marks_done(self) -> None:
        with _patched_adapter([], rowcount=1) as (adapter, cursor, _conn):
            adapter.complete_bench_job(3, "host:1234|model")
        sql, params = cursor.execute.call_args.args
        assert "'DONE'" in sql
        assert "ministral_concurrency = %s" in sql
        assert params[0] == 3
        assert params[1] == "host:1234|model"

    def test_fail_sets_failed_status(self) -> None:
        with _patched_adapter([], rowcount=1) as (adapter, cursor, _conn):
            adapter.fail_bench_job("endpoint down")
        sql, params = cursor.execute.call_args.args
        assert "'FAILED'" in sql
        assert params == ("endpoint down",)

    def test_claim_returns_none_quietly_on_connection_error(self) -> None:
        # A DB outage during the poll loop must not raise and must not crash the
        # poller; claim_bench_job returns None (quiet path).
        err = psycopg2.OperationalError("db down")
        with _patched_adapter([], execute_side_effects=[err]) as (adapter, _c, conn):
            assert adapter.claim_bench_job() is None
        conn.rollback.assert_called_once()
