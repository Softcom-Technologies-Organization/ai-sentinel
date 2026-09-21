"""Run Ettin PII detection and benchmark it on a folder of UTF-8 text files.

Usage::

    python scripts/benchmark_ettin_pii.py ./corpus
    python scripts/benchmark_ettin_pii.py ./corpus --output ./outputs/ettin.json
"""

from __future__ import annotations

import argparse
import json
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Sequence, Tuple

MODEL_ID = "kalyan-ks/ettin-68m-nemotron-pii"
STRIDE = 256


def find_text_files(folder: Path) -> List[Path]:
    if not folder.is_dir():
        raise ValueError(f"Not a directory: {folder}")
    files = sorted(path for path in folder.rglob("*.txt") if path.is_file())
    if not files:
        raise ValueError(f"No .txt files found in: {folder}")
    return files


def load_detector() -> Tuple[Any, str]:
    import torch
    from transformers import (
        AutoModelForTokenClassification,
        PreTrainedTokenizerFast,
        pipeline,
    )

    if torch.cuda.is_available():
        device = "cuda"
    elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        device = "mps"
    else:
        device = "cpu"

    tokenizer = PreTrainedTokenizerFast.from_pretrained(MODEL_ID)
    model = AutoModelForTokenClassification.from_pretrained(MODEL_ID)
    detector = pipeline(
        "ner",
        model=model,
        tokenizer=tokenizer,
        aggregation_strategy="simple",
        device=torch.device(device),
    )
    return detector, device


def normalize_entities(
    entities: Sequence[Dict[str, Any]], text: str, include_text: bool
) -> List[Dict[str, Any]]:
    normalized = []
    for entity in entities:
        start = int(entity["start"])
        end = int(entity["end"])
        item = {
            "label": str(entity["entity_group"]),
            "score": round(float(entity["score"]), 6),
            "start": start,
            "end": end,
        }
        if include_text:
            item["text"] = text[start:end]
        normalized.append(item)
    return sorted(normalized, key=lambda item: (item["start"], item["end"]))


def benchmark_files(
    detector: Any, files: Sequence[Path], folder: Path, include_text: bool
) -> Dict[str, Any]:
    results = []
    label_counts: Counter[str] = Counter()
    total_chars = 0
    inference_seconds = 0.0

    for path in files:
        text = path.read_text(encoding="utf-8")
        started = time.perf_counter()
        raw_entities = detector(text, stride=STRIDE) if text else []
        elapsed = time.perf_counter() - started
        entities = normalize_entities(raw_entities, text, include_text)

        total_chars += len(text)
        inference_seconds += elapsed
        label_counts.update(entity["label"] for entity in entities)
        results.append(
            {
                "file": path.relative_to(folder).as_posix(),
                "characters": len(text),
                "seconds": round(elapsed, 6),
                "characters_per_second": round(len(text) / elapsed, 2) if elapsed else None,
                "entity_count": len(entities),
                "entities": entities,
            }
        )

    return {
        "file_count": len(files),
        "character_count": total_chars,
        "entity_count": sum(label_counts.values()),
        "inference_seconds": round(inference_seconds, 6),
        "characters_per_second": (
            round(total_chars / inference_seconds, 2) if inference_seconds else None
        ),
        "files_per_second": (
            round(len(files) / inference_seconds, 2) if inference_seconds else None
        ),
        "entities_by_label": dict(sorted(label_counts.items())),
        "files": results,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Test Ettin 68M PII detection and benchmark all .txt files recursively."
    )
    parser.add_argument("folder", type=Path, help="Folder containing UTF-8 .txt files")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("ettin-pii-benchmark.json"),
        help="JSON report path (default: ettin-pii-benchmark.json)",
    )
    parser.add_argument(
        "--include-text",
        action="store_true",
        help="Include raw detected PII values in the JSON report",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    folder = args.folder.resolve()
    try:
        files = find_text_files(folder)
    except ValueError as error:
        raise SystemExit(str(error)) from error

    print(f"Loading {MODEL_ID}...")
    started = time.perf_counter()
    detector, device = load_detector()
    model_load_seconds = time.perf_counter() - started

    warmup_started = time.perf_counter()
    detector("Contact alice@example.com.", stride=STRIDE)
    warmup_seconds = time.perf_counter() - warmup_started

    print(f"Benchmarking {len(files)} file(s) on {device}...")
    report = benchmark_files(detector, files, folder, args.include_text)
    report.update(
        {
            "model": MODEL_ID,
            "device": device,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "model_load_seconds": round(model_load_seconds, 6),
            "warmup_seconds": round(warmup_seconds, 6),
            "raw_text_included": args.include_text,
        }
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Entities: {report['entity_count']}")
    print(f"Inference: {report['inference_seconds']:.3f}s")
    print(f"Throughput: {report['characters_per_second'] or 0:.2f} chars/s")
    print(f"Report: {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
