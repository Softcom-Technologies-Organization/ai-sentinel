from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import pytest

_SCRIPT_PATH = (
    Path(__file__).resolve().parents[3] / "scripts" / "openjev_entity_judge.py"
)


def _load_module():
    spec = importlib.util.spec_from_file_location("openjev_entity_judge", _SCRIPT_PATH)
    assert spec is not None
    assert spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


judge = _load_module()


def test_builds_openjev_decision_from_entity_value_and_context() -> None:
    row = judge.build_decision_row(
        entity_type="EMAIL",
        value="alice@example.com",
        context="Contact: alice@example.com",
    )

    assert row["state"] == {
        "expected_entity_type": "EMAIL",
        "candidate_value": "alice@example.com",
        "context": "Contact: >>>alice@example.com<<<",
    }
    assert row["question"] == (
        "Which verdict best describes the candidate value in its exact context?"
    )
    assert [option["id"] for option in row["options"]] == [
        "TRUE_POSITIVE",
        "FALSE_POSITIVE",
        "UNSURE",
    ]


def test_preserves_exact_span_marker_from_benchmark() -> None:
    row = judge.build_decision_row(
        entity_type="EMAIL",
        value="alice@example.com",
        context="Label alice@example.com, value >>>alice@example.com<<<",
    )

    assert row["state"]["context"] == (
        "Label alice@example.com, value >>>alice@example.com<<<"
    )


def test_interprets_highest_openjev_option_as_confirmation() -> None:
    result = judge.interpret_score(
        {
            "id": "entity-judge",
            "option_ids": ["TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"],
            "probabilities": [0.8, 0.1, 0.1],
            "model": {"source": "local-model", "revision": "test"},
            "input_tokens": 42,
            "total_seconds": 0.25,
            "probability_status": "conditional option score",
        }
    )

    assert result == {
        "verdict": "TRUE_POSITIVE",
        "confirmed": True,
        "selected_score": 0.8,
        "scores": {
            "TRUE_POSITIVE": 0.8,
            "FALSE_POSITIVE": 0.1,
            "UNSURE": 0.1,
        },
        "model": {"source": "local-model", "revision": "test"},
        "input_tokens": 42,
        "seconds": 0.25,
        "score_status": "conditional option score",
    }


def test_interprets_unsure_without_forcing_a_boolean() -> None:
    result = judge.interpret_score(
        {
            "option_ids": ["TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"],
            "probabilities": [0.2, 0.3, 0.5],
            "model": {},
            "input_tokens": 10,
            "total_seconds": 0.1,
            "probability_status": "uncalibrated",
        }
    )

    assert result["verdict"] == "UNSURE"
    assert result["confirmed"] is None


def test_judges_entity_with_openjev_direct_scores() -> None:
    captured = {}

    def scorer(model, tokenizer, row, metadata, max_tokens):
        captured.update(
            model=model,
            tokenizer=tokenizer,
            row=row,
            metadata=metadata,
            max_tokens=max_tokens,
        )
        return {
            "option_ids": ["TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"],
            "probabilities": [0.05, 0.9, 0.05],
            "model": metadata,
            "input_tokens": 54,
            "total_seconds": 0.4,
            "probability_status": "uncalibrated",
        }

    result = judge.judge_entity(
        model="model",
        tokenizer="tokenizer",
        metadata={"source": "test"},
        entity_type="IP_ADDRESS",
        value="8080",
        context="Service listens on port 8080.",
        max_tokens=1024,
        scorer=scorer,
    )

    assert captured["row"]["state"]["context"] == (
        "Service listens on port >>>8080<<<."
    )
    assert captured["max_tokens"] == 1024
    assert result["verdict"] == "FALSE_POSITIVE"
    assert result["confirmed"] is False


def test_cli_prints_machine_readable_verdict(monkeypatch, capsys) -> None:
    monkeypatch.setattr(
        judge,
        "load_model",
        lambda model_id, revision, device: (
            "model",
            "tokenizer",
            {"source": model_id, "revision": revision, "device": device},
        ),
    )
    monkeypatch.setattr(
        judge,
        "judge_entity",
        lambda **kwargs: {
            "verdict": "TRUE_POSITIVE",
            "confirmed": True,
            "selected_score": 0.9,
        },
    )

    exit_code = judge.main(
        [
            "--entity-type",
            "EMAIL",
            "--value",
            "alice@example.com",
            "--context",
            "Contact alice@example.com",
            "--model",
            "local-model",
            "--revision",
            "revision-id",
            "--device",
            "cpu",
        ],
        scorer=lambda *args: None,
    )

    assert exit_code == 0
    assert json.loads(capsys.readouterr().out) == {
        "verdict": "TRUE_POSITIVE",
        "confirmed": True,
        "selected_score": 0.9,
    }


@pytest.mark.parametrize("field", ["entity_type", "value", "context"])
def test_rejects_empty_required_input(field: str) -> None:
    inputs = {
        "entity_type": "EMAIL",
        "value": "alice@example.com",
        "context": "Contact: alice@example.com",
    }
    inputs[field] = ""

    with pytest.raises(ValueError, match=field):
        judge.build_decision_row(**inputs)
