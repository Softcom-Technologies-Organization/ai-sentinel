"""Assemble the system prompt a finding is judged with.

A strategy is declared in ``config/prompts.toml`` and its parts are Markdown
files under ``prompts/``. Switching from one generic prompt to one body per
pii_type is ``--strategy per_type``; writing a new body is dropping a file in
``prompts/per-type/``. Nothing here has to change for either.

The trunk is spliced, not templated: the body is inserted immediately before the
paragraph named by ``insert_before``, which keeps
``prompts/general/production-v2.md`` byte-identical to the constant
``pii-detector-service`` ships. ``self_test.py`` asserts that identity, so the
generic arm is provably the production baseline and not a paraphrase of it.
"""
from __future__ import annotations

import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Set

import config

DEFAULT_BODY = "_default.md"


@dataclass(frozen=True)
class Strategy:
    name: str
    label: str
    trunk: str
    bodies: Dict[str, str]
    insert_before: Optional[str]
    restricted_to: Optional[Set[str]]

    def body_for(self, pii_type: str) -> str:
        if self.restricted_to is not None and pii_type not in self.restricted_to:
            return self.bodies.get(DEFAULT_BODY, "")
        return self.bodies.get(pii_type) or self.bodies.get(DEFAULT_BODY, "")

    def system(self, pii_type: str) -> str:
        body = self.body_for(pii_type)
        if not body or not self.insert_before:
            return self.trunk
        anchor = self.trunk.find(self.insert_before)
        if anchor < 0:
            raise ValueError(
                f"strategy {self.name!r}: anchor {self.insert_before!r} not found in its trunk"
            )
        return f"{self.trunk[:anchor]}{body}\n\n{self.trunk[anchor:]}"

    def covered_types(self) -> List[str]:
        return sorted(name for name in self.bodies if name != DEFAULT_BODY)


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip("\n")


def load_strategies() -> Dict[str, Strategy]:
    with open(config.CONFIG_DIR / "prompts.toml", "rb") as handle:
        table = tomllib.load(handle)
    strategies: Dict[str, Strategy] = {}
    for name, section in table["strategies"].items():
        bodies: Dict[str, str] = {}
        if "bodies" in section:
            for path in sorted((config.PROMPTS_DIR / section["bodies"]).glob("*.md")):
                key = DEFAULT_BODY if path.name == DEFAULT_BODY else path.stem
                bodies[key] = _read(path)
        strategies[name] = Strategy(
            name=name,
            label=section["label"],
            trunk=_read(config.PROMPTS_DIR / section["system"]),
            bodies=bodies,
            insert_before=section.get("insert_before"),
            restricted_to=set(section["types"]) if "types" in section else None,
        )
    return strategies


def default_strategy() -> str:
    with open(config.CONFIG_DIR / "prompts.toml", "rb") as handle:
        return tomllib.load(handle)["default"]


def mark_span(text: str, start: int, end: int) -> str:
    """Delimit the judged span with ``>>>``/``<<<``, as production does.

    A value that also appears as its own field label on the same line would be
    ambiguous otherwise. Production locates the span by searching for the value;
    here the offsets are known, which is the same result without the search.
    """
    return f"{text[:start]}>>>{text[start:end]}<<<{text[end:]}"


def user_prompt(record: dict) -> str:
    return (
        f"pii_type   : {record['pii_type']}\n"
        f"type_label : {record['type_label']}\n"
        f"value      : {record['value']}\n"
        f"contexte   : {mark_span(record['document_text'], record['start'], record['end'])}"
    )


if __name__ == "__main__":
    for name, strategy in load_strategies().items():
        print(f"{name:28s} {strategy.label}")
        print(f"  trunk {len(strategy.trunk)} chars, "
              f"{len(strategy.covered_types())} per-type bodies")
