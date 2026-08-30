"""
Unit tests for :mod:`scripts.parse_throughput_logs`.

The script is intentionally dependency-free so the tests load it directly
from its file path -- the parent package (``pii-detector-service/scripts``)
is not a regular Python package and is not on ``sys.path`` in production
(the script is invoked standalone in post-CI environments).
"""

from __future__ import annotations

import importlib.util
import io
import sys
from pathlib import Path
from typing import Any, List

import pytest


# ---------------------------------------------------------------------------
# Dynamic module loading
# ---------------------------------------------------------------------------

_SCRIPT_PATH = (
    Path(__file__).resolve().parents[3] / "scripts" / "parse_throughput_logs.py"
)


def _load_module():
    spec = importlib.util.spec_from_file_location(
        "scripts_parse_throughput_logs", _SCRIPT_PATH
    )
    assert spec is not None, f"Cannot locate script at {_SCRIPT_PATH}"
    assert spec.loader is not None, f"Cannot locate script at {_SCRIPT_PATH}"
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


ptl = _load_module()


# ---------------------------------------------------------------------------
# Sample log lines (aligned on the real Python / Java emitters)
# ---------------------------------------------------------------------------

PYTHON_DETECTION_LINE = (
    "[THROUGHPUT] phase=detection request_id=req_42 chars=15234 "
    "duration_s=3.421 chars_per_s=4453.4 entities_in=42"
)

PYTHON_TOTAL_LINE = (
    "[THROUGHPUT] phase=total request_id=req_42 chars=15234 "
    "duration_s=3.500 chars_per_s=4352.6 entities_final=24"
)

JAVA_GRPC_LINE = (
    "[THROUGHPUT] phase=grpc.client request_id=550e8400-e29b-41d4-a716-446655440000 "
    "chars=15234 duration_ms=3421 chars_per_s=4453.40"
)

PYTHON_LOGGER_PREFIX_LINE = (
    "2026-05-25 12:34:56,789 INFO pii_detector.throughput "
    + PYTHON_DETECTION_LINE
)

JAVA_LOGBACK_PREFIX_LINE = (
    "2026-05-25 12:34:56.789 INFO 1 --- [reactor-http-nio-2] "
    "p.s.a.GrpcPiiDetectorArmeriaClientAdapter : " + JAVA_GRPC_LINE
)


# ---------------------------------------------------------------------------
# TestParseLogLines
# ---------------------------------------------------------------------------


class TestParseLogLines:
    def test_parses_python_detection_line(self) -> None:
        [entry] = ptl.parse_log_lines([PYTHON_DETECTION_LINE])
        assert entry.phase == "detection"
        assert entry.request_id == "req_42"
        assert entry.chars == 15234
        assert entry.duration_s == pytest.approx(3.421)
        assert entry.chars_per_s == pytest.approx(4453.4)
        assert entry.extra == {"entities_in": 42}

    def test_parses_python_total_line(self) -> None:
        [entry] = ptl.parse_log_lines([PYTHON_TOTAL_LINE])
        assert entry.phase == "total"
        assert entry.extra["entities_final"] == 24

    def test_parses_java_grpc_client_line_with_duration_ms(self) -> None:
        [entry] = ptl.parse_log_lines([JAVA_GRPC_LINE])
        assert entry.phase == "grpc.client"
        assert entry.chars == 15234
        # 3421 ms -> 3.421 s -- conversion is the contract.
        assert entry.duration_s == pytest.approx(3.421)
        # duration_ms / duration_s must not leak into ``extra``.
        assert "duration_ms" not in entry.extra
        assert "duration_s" not in entry.extra

    def test_skips_lines_without_throughput_tag(self) -> None:
        lines = [
            "INFO some unrelated log line",
            "2026-05-25 12:00:00 server starting",
            "",
        ]
        assert ptl.parse_log_lines(lines) == []

    def test_skips_malformed_lines_silently(self) -> None:
        # Missing ``chars`` => entry rejected, no exception raised.
        bad_line = "[THROUGHPUT] phase=detection request_id=req duration_s=1.0"
        good_line = PYTHON_DETECTION_LINE
        entries = ptl.parse_log_lines([bad_line, good_line])
        assert len(entries) == 1
        assert entries[0].request_id == "req_42"

    def test_handles_logback_prefix(self) -> None:
        [entry] = ptl.parse_log_lines([JAVA_LOGBACK_PREFIX_LINE])
        assert entry.phase == "grpc.client"
        assert entry.chars == 15234

    def test_handles_python_logger_prefix(self) -> None:
        [entry] = ptl.parse_log_lines([PYTHON_LOGGER_PREFIX_LINE])
        assert entry.phase == "detection"
        assert entry.request_id == "req_42"

    def test_handles_extra_fields_into_extra_dict(self) -> None:
        line = (
            "[THROUGHPUT] phase=detection request_id=req_99 chars=10 "
            "duration_s=1.0 chars_per_s=10.0 custom_field=hello another=42"
        )
        [entry] = ptl.parse_log_lines([line])
        assert entry.extra["custom_field"] == "hello"
        # Unknown integer-looking key stays as string -- we do not guess.
        assert entry.extra["another"] == "42"


# ---------------------------------------------------------------------------
# TestAggregate
# ---------------------------------------------------------------------------


def _make_entries(chars_per_s_values: List[float], phase: str = "detection") -> List[Any]:
    entries = []
    for idx, cps in enumerate(chars_per_s_values):
        entries.append(
            ptl.ThroughputEntry(
                phase=phase,
                request_id=f"req_{idx}",
                chars=int(cps),
                duration_s=1.0,
                chars_per_s=cps,
                extra={},
            )
        )
    return entries


class TestAggregate:
    def test_computes_p50_p95_p99_per_phase(self) -> None:
        values = [float(i * 100) for i in range(1, 101)]  # 100 .. 10_000
        entries = _make_entries(values, phase="detection")
        report = ptl.aggregate(entries)
        stats = report.phase_stats["detection"]
        assert stats.count == 100
        # With 100 points evenly spaced from 100 to 10_000, p50 ~= 5050.
        assert stats.p50 == pytest.approx(5050.0, rel=0.05)
        assert stats.p95 >= stats.p50
        assert stats.p99 >= stats.p95
        assert stats.mean_chars_per_s == pytest.approx(5050.0)

    def test_handles_empty_input_gracefully(self) -> None:
        report = ptl.aggregate([])
        assert report.entry_count == 0
        assert report.phase_stats == {}
        assert report.slowest_requests == []

    def test_groups_by_request_id_when_breakdown_requested(self) -> None:
        # Slowest entry must come first in the breakdown.
        fast = ptl.ThroughputEntry(
            phase="total",
            request_id="req_fast",
            chars=10_000,
            duration_s=1.0,
            chars_per_s=10_000.0,
            extra={},
        )
        slow = ptl.ThroughputEntry(
            phase="total",
            request_id="req_slow",
            chars=10,
            duration_s=1.0,
            chars_per_s=10.0,
            extra={},
        )
        report = ptl.aggregate([fast, slow])
        assert report.slowest_requests[0].request_id == "req_slow"
        assert report.slowest_requests[-1].request_id == "req_fast"


# ---------------------------------------------------------------------------
# TestWriteReport
# ---------------------------------------------------------------------------


class TestWriteReport:
    def test_writes_markdown_with_all_sections(self, tmp_path: Path) -> None:
        entries = ptl.parse_log_lines(
            [PYTHON_DETECTION_LINE, PYTHON_TOTAL_LINE]
        )
        report = ptl.aggregate(entries)
        out = tmp_path / "report.md"
        ptl.write_report(report, out)
        content = out.read_text(encoding="utf-8")
        assert "# Throughput report" in content
        assert "## Throughput per phase" in content
        assert "## Breakdown" in content
        # No baseline => no delta section.
        assert "## Delta vs baseline" not in content

    def test_includes_delta_section_when_baseline_provided(
        self, tmp_path: Path
    ) -> None:
        baseline = ptl.aggregate(_make_entries([1000.0, 1100.0, 900.0]))
        improved = ptl.aggregate(_make_entries([500.0, 600.0, 700.0]))
        out = tmp_path / "report.md"
        ptl.write_report(improved, out, baseline_report=baseline)
        content = out.read_text(encoding="utf-8")
        assert "## Delta vs baseline" in content
        # Improved mean is lower => delta is negative.
        assert "-" in content

    def test_omits_delta_section_when_no_baseline(self, tmp_path: Path) -> None:
        report = ptl.aggregate(_make_entries([100.0, 200.0]))
        out = tmp_path / "report.md"
        ptl.write_report(report, out, baseline_report=None)
        content = out.read_text(encoding="utf-8")
        assert "## Delta vs baseline" not in content

    def test_raises_when_output_parent_missing(self, tmp_path: Path) -> None:
        report = ptl.aggregate(_make_entries([100.0]))
        out = tmp_path / "missing-dir" / "report.md"
        with pytest.raises(FileNotFoundError):
            ptl.write_report(report, out)


# ---------------------------------------------------------------------------
# TestCli
# ---------------------------------------------------------------------------


class TestCli:
    def test_cli_writes_to_output_path(
        self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        log_file = tmp_path / "input.log"
        log_file.write_text(
            PYTHON_DETECTION_LINE + "\n" + PYTHON_TOTAL_LINE + "\n",
            encoding="utf-8",
        )
        out = tmp_path / "report.md"
        exit_code = ptl.main(
            [
                "--input",
                str(log_file),
                "--output",
                str(out),
            ]
        )
        assert exit_code == 0
        assert out.exists()
        assert "Throughput per phase" in out.read_text(encoding="utf-8")

    def test_cli_supports_baseline_flag(self, tmp_path: Path) -> None:
        improved_log = tmp_path / "improved.log"
        baseline_log = tmp_path / "baseline.log"
        improved_log.write_text(
            PYTHON_DETECTION_LINE + "\n", encoding="utf-8"
        )
        baseline_log.write_text(
            "[THROUGHPUT] phase=detection request_id=req_b chars=10 "
            "duration_s=1.0 chars_per_s=10.0\n",
            encoding="utf-8",
        )
        out = tmp_path / "report.md"
        exit_code = ptl.main(
            [
                "--input",
                str(improved_log),
                "--baseline",
                str(baseline_log),
                "--output",
                str(out),
            ]
        )
        assert exit_code == 0
        content = out.read_text(encoding="utf-8")
        assert "## Delta vs baseline" in content

    def test_cli_reads_stdin_when_no_input(
        self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        fake_stdin = io.StringIO(PYTHON_DETECTION_LINE + "\n")
        monkeypatch.setattr(sys, "stdin", fake_stdin)
        out = tmp_path / "report.md"
        exit_code = ptl.main(["--output", str(out)])
        assert exit_code == 0
        assert "Throughput per phase" in out.read_text(encoding="utf-8")

    def test_cli_returns_error_when_output_dir_missing(
        self, tmp_path: Path
    ) -> None:
        log_file = tmp_path / "input.log"
        log_file.write_text(PYTHON_DETECTION_LINE + "\n", encoding="utf-8")
        bad_out = tmp_path / "does-not-exist" / "report.md"
        exit_code = ptl.main(
            [
                "--input",
                str(log_file),
                "--output",
                str(bad_out),
            ]
        )
        assert exit_code == 1
