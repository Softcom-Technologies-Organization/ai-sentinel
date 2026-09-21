from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

_SCRIPT_PATH = (
    Path(__file__).resolve().parents[3] / "scripts" / "benchmark_ettin_pii.py"
)


def _load_module():
    spec = importlib.util.spec_from_file_location("benchmark_ettin_pii", _SCRIPT_PATH)
    assert spec is not None
    assert spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


benchmark = _load_module()


def test_benchmarks_txt_files_recursively_without_exposing_raw_pii(tmp_path: Path) -> None:
    nested = tmp_path / "nested"
    nested.mkdir()
    (tmp_path / "first.txt").write_text("Alice", encoding="utf-8")
    (nested / "second.txt").write_text("Bob", encoding="utf-8")
    (tmp_path / "ignored.md").write_text("Carol", encoding="utf-8")

    def detector(text: str, stride: int):
        assert stride == benchmark.STRIDE
        return [
            {
                "entity_group": "first_name",
                "score": 0.9876543,
                "start": 0,
                "end": len(text),
            }
        ]

    files = benchmark.find_text_files(tmp_path)
    report = benchmark.benchmark_files(detector, files, tmp_path, include_text=False)

    assert [item["file"] for item in report["files"]] == [
        "first.txt",
        "nested/second.txt",
    ]
    assert report["character_count"] == 8
    assert report["entity_count"] == 2
    assert report["entities_by_label"] == {"first_name": 2}
    assert "text" not in report["files"][0]["entities"][0]


def test_fails_when_folder_has_no_txt_files(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match=r"No \.txt files found"):
        benchmark.find_text_files(tmp_path)
