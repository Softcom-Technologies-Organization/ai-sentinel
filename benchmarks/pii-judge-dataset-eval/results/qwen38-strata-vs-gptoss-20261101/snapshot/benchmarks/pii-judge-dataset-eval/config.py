"""Stdlib-only readers for ``config/*.toml``.

Kept free of ``huggingface_hub`` and ``pyarrow`` so ``self_test.py`` can check
the tables offline, and so ``score.py`` can label a report without the download
dependencies installed.
"""
from __future__ import annotations

import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

HERE = Path(__file__).resolve().parent
CONFIG_DIR = HERE / "config"
PROMPTS_DIR = HERE / "prompts"
DATASETS_DIR = HERE / "datasets"
RESULTS_DIR = HERE / "results"


def _load(name: str) -> Dict[str, Any]:
    with open(CONFIG_DIR / name, "rb") as handle:
        return tomllib.load(handle)


@dataclass(frozen=True)
class SourceFile:
    path: str
    lang: str


@dataclass(frozen=True)
class Source:
    name: str
    repo: str
    revision: str
    format: str
    text_field: str
    spans_field: str
    spans_format: str
    files: List[SourceFile]


@dataclass(frozen=True)
class PiiType:
    """One judged ``pii_type``, as production emits it."""

    name: str
    label: str
    detectors: List[str]
    category: str
    gold: List[str]
    gold_filter: Optional[str]
    competing: List[str]
    violations: List[str]
    mentions: Dict[str, List[str]]

    def all_mentions(self) -> List[str]:
        """Every surface form, all languages, longest first.

        Longest first so "numéro de carte de crédit" is matched before "carte de
        crédit" and the harvested span is the whole noun phrase.
        """
        forms = {form for forms in self.mentions.values() for form in forms}
        return sorted(forms, key=lambda form: (-len(form), form))

    def sampling_signature(self) -> str:
        """What decides the draw, deliberately excluding the name and the label.

        Two rows of ``pii_type_config`` for the same concept — IBAN and
        IBAN_CODE, CREDIT_CARD and CREDIT_DEBIT_CARD — share this signature and
        therefore draw the SAME spans. Any gap between their scores is then
        attributable to the ``pii_type`` and ``type_label`` strings alone, which
        is the only reason to benchmark both.
        """
        parts = [
            ",".join(sorted(self.gold)),
            self.gold_filter or "",
            ",".join(sorted(self.competing)),
            ",".join(sorted(self.violations)),
            ",".join(self.all_mentions()),
        ]
        return "|".join(parts)


def load_sources() -> Dict[str, Source]:
    table = _load("datasets.toml")["sources"]
    return {
        name: Source(
            name=name,
            repo=section["repo"],
            revision=section["revision"],
            format=section["format"],
            text_field=section["text_field"],
            spans_field=section["spans_field"],
            spans_format=section["spans_format"],
            files=[SourceFile(f["path"], f["lang"]) for f in section["files"]],
        )
        for name, section in table.items()
    }


def load_sizes() -> Dict[str, int]:
    return {name: section["n"] for name, section in _load("datasets.toml")["sizes"].items()}


def load_side_chars() -> int:
    return _load("datasets.toml")["context"]["side_chars"]


def load_fp_families() -> Dict[str, float]:
    table = _load("datasets.toml")["fp_families"]
    return {name: float(section["share"]) for name, section in table.items()}


def load_types() -> Dict[str, PiiType]:
    table = _load("pii_types.toml")["types"]
    return {
        name: PiiType(
            name=name,
            label=section["label"],
            detectors=list(section["detectors"]),
            category=section["category"],
            gold=list(section["gold"]),
            gold_filter=section.get("gold_filter"),
            competing=list(section.get("competing", [])),
            violations=list(section.get("violations", [])),
            mentions={lang: list(forms) for lang, forms in section.get("mentions", {}).items()},
        )
        for name, section in table.items()
    }


def load_shapes() -> Dict[str, str]:
    return dict(_load("pii_types.toml")["shapes"])


def load_rival_mentions() -> Dict[str, List[str]]:
    """``{"<dataset>:<label>": [surface form, ...]}`` for labels no type owns."""
    table = _load("pii_types.toml").get("rival_mentions", {})
    return {spec: list(forms) for spec, forms in table.items()}


def rival_forms_by_label(types: Dict[str, PiiType]) -> Dict[str, List[str]]:
    """Every way the text can name a dataset label, longest form first.

    A label some judged type owns as gold is named by that type's own mentions;
    the rest are declared in ``[rival_mentions]``. This is what qualifies a
    competing-type candidate: without a naming phrase nearby, the production
    prompt forbids the rejection the benchmark would be asking for.
    """
    forms: Dict[str, set] = {}
    for pii_type in types.values():
        for spec in pii_type.gold:
            forms.setdefault(spec, set()).update(pii_type.all_mentions())
    for spec, declared in load_rival_mentions().items():
        forms.setdefault(spec, set()).update(declared)
    return {
        spec: sorted(values, key=lambda form: (-len(form), form))
        for spec, values in forms.items()
    }


def load_placeholder_patterns() -> List[str]:
    return list(_load("pii_types.toml")["placeholders"]["patterns"])
