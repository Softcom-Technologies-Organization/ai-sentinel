"""Frozen per-type judge prompt from the Qwen/Strata benchmark of 2026-11-01."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict

VERDICTS = frozenset({"TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"})

# Reasoning models spend most of the budget before the JSON; a plain model stops
# at the closing brace long before.
JUDGE_MAX_TOKENS = 2048

VERDICT_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "confidence": {"type": "number"},
        "verdict": {
            "type": "string",
            "enum": ["TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"],
        },
    },
    "required": ["reason", "confidence", "verdict"],
}

_PROMPT_DIRECTORY = Path(__file__).resolve().parents[3] / "config/prompts/judge/per-type-v1"
SYSTEM_PROMPT = (_PROMPT_DIRECTORY / "system.md").read_text(encoding="utf-8").strip("\n")
_TYPE_BODIES = {
    path.stem: path.read_text(encoding="utf-8").strip("\n")
    for path in (_PROMPT_DIRECTORY / "per-type").glob("*.md")
}


def system_prompt(pii_type: str) -> str:
    body = _TYPE_BODIES.get(pii_type, _TYPE_BODIES["_default"])
    anchor = SYSTEM_PROMPT.index("GARDE-FOUS SUR LES MOTIFS")
    return f"{SYSTEM_PROMPT[:anchor]}{body}\n\n{SYSTEM_PROMPT[anchor:]}"


def mark_span(context: str, value: str) -> str:
    """Delimit the first occurrence of ``value`` in ``context`` with ``>>>``/``<<<``.

    Without the markers a repeated value (a label and its value on the same line)
    would be ambiguous about which occurrence is on trial. A value absent from the
    context (masked or normalised window) leaves the context untouched: the value
    is already stated on its own line of the prompt.
    """
    if not value:
        return context
    start = context.find(value)
    if start < 0:
        return context
    end = start + len(value)
    return f"{context[:start]}>>>{context[start:end]}<<<{context[end:]}"


def user_prompt(pii_type: str, type_label: str, value: str, context: str) -> str:
    return (
        f"pii_type   : {pii_type}\n"
        f"type_label : {type_label}\n"
        f"value      : {value}\n"
        f"contexte   : {mark_span(context, value)}"
    )


def build_payload(
    model_id: str, pii_type: str, type_label: str, value: str, context: str
) -> Dict[str, Any]:
    """OpenAI ``chat/completions`` body: temperature 0, strict JSON schema output."""
    return {
        "model": model_id,
        "messages": [
            {"role": "system", "content": system_prompt(pii_type)},
            {"role": "user", "content": user_prompt(pii_type, type_label, value, context)},
        ],
        "temperature": 0,
        "stream": False,
        "max_tokens": JUDGE_MAX_TOKENS,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "pii_verdict",
                "strict": True,
                "schema": VERDICT_SCHEMA,
            },
        },
    }


def parse_verdict(content: str) -> Dict[str, Any]:
    """Read the verdict object out of the model's content channel.

    Structured output is requested, but a reasoning model that exhausts its token
    budget answers with an empty or truncated content channel, and some servers
    wrap the JSON in prose. Anything that is not a well-formed verdict comes back as
    ``UNPARSEABLE`` with the raw head of the content as reason, never as an
    exception: the caller keeps the finding (fail-open) and records why.
    """
    text = (content or "").strip()
    if not text:
        return _unparseable("empty content channel")
    parsed = _first_json_object(text)
    if parsed is None:
        return _unparseable(text[:200])
    verdict = str(parsed.get("verdict", "")).upper()
    if verdict not in VERDICTS:
        return _unparseable(f"unknown verdict {verdict!r}")
    return {
        "verdict": verdict,
        "confidence": _confidence(parsed.get("confidence")),
        "reason": str(parsed.get("reason", ""))[:400],
    }


def _first_json_object(text: str) -> Dict[str, Any] | None:
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        parsed = json.loads(text[start:end + 1])
    except ValueError:
        return None
    return parsed if isinstance(parsed, dict) else None


def _confidence(raw: Any) -> float:
    try:
        return min(1.0, max(0.0, float(raw)))
    except (TypeError, ValueError):
        return 0.0


def _unparseable(reason: str) -> Dict[str, Any]:
    return {"verdict": "UNPARSEABLE", "confidence": 0.0, "reason": reason}
