"""Judge one named-entity candidate with OpenJev's direct option scoring.

Install the renamed OpenJev package from its pinned source revision first::

    pip install --no-deps \
      git+https://github.com/TheoLeeCJ/openjev.git@b9cb32537e78be65f19abfcb1de8fc504b627d84
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

VERDICTS = ("TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE")
DEFAULT_MODEL = "Qwen/Qwen3.5-4B"
DEFAULT_REVISION = "851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a"

_VERDICT_DESCRIPTIONS = {
    "TRUE_POSITIVE": (
        "The candidate value is genuinely an instance of the expected entity type in this "
        "exact context. Its format, meaning, and role are compatible."
    ),
    "FALSE_POSITIVE": (
        "The candidate value is not an instance of the expected entity type because its format "
        "is incompatible, the context explicitly gives it another role, or it is not a value."
    ),
    "UNSURE": (
        "The available evidence is insufficient or the entity specification is ambiguous. "
        "Do not guess."
    ),
}


def build_decision_row(entity_type: str, value: str, context: str) -> dict:
    inputs = {"entity_type": entity_type, "value": value, "context": context}
    for name, content in inputs.items():
        if not isinstance(content, str) or not content.strip():
            raise ValueError(f"{name} must be a nonempty string")
    marker = f">>>{value}<<<"
    marked_context = context if marker in context else context.replace(value, marker, 1)
    return {
        "id": "entity-judge",
        "state": {
            "expected_entity_type": entity_type,
            "candidate_value": value,
            "context": marked_context,
        },
        "question": "Which verdict best describes the candidate value in its exact context?",
        "options": [
            {"id": verdict, "description": _VERDICT_DESCRIPTIONS[verdict]}
            for verdict in VERDICTS
        ],
    }


def interpret_score(raw_result: dict) -> dict:
    scores = dict(zip(raw_result["option_ids"], raw_result["probabilities"]))
    verdict = max(scores, key=scores.get)
    confirmed = {
        "TRUE_POSITIVE": True,
        "FALSE_POSITIVE": False,
        "UNSURE": None,
    }[verdict]
    return {
        "verdict": verdict,
        "confirmed": confirmed,
        "selected_score": scores[verdict],
        "scores": scores,
        "model": raw_result["model"],
        "input_tokens": raw_result["input_tokens"],
        "seconds": raw_result["total_seconds"],
        "score_status": raw_result["probability_status"],
    }


def judge_entity(
    model,
    tokenizer,
    metadata: dict,
    entity_type: str,
    value: str,
    context: str,
    max_tokens: int = 4096,
    scorer=None,
) -> dict:
    if scorer is None:
        from semif_phase1.direct import score as scorer

    raw_result = scorer(
        model,
        tokenizer,
        build_decision_row(entity_type, value, context),
        metadata,
        max_tokens,
    )
    return interpret_score(raw_result)


def load_model(model_id: str, revision: str, device: str):
    import torch
    import transformers

    local_model = Path(model_id).exists()
    if not local_model and not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Remote models require a pinned 40-character commit revision")

    if device == "auto":
        if torch.cuda.is_available():
            device = "cuda"
        elif torch.backends.mps.is_available():
            device = "mps"
        else:
            device = "cpu"
    if device == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA was requested but is not available")
    if device == "mps" and not torch.backends.mps.is_available():
        raise ValueError("MPS was requested but is not available")

    dtype = {
        "cuda": torch.bfloat16,
        "mps": torch.float16,
        "cpu": torch.float32,
    }[device]
    common = {
        "revision": None if local_model else revision,
        "local_files_only": local_model,
        "trust_remote_code": False,
    }
    config = transformers.AutoConfig.from_pretrained(model_id, **common)
    tokenizer = transformers.AutoTokenizer.from_pretrained(model_id, **common)
    model_class = transformers.AutoModelForCausalLM
    if config.model_type in {"qwen3_5", "qwen3_5_text"}:
        model_class = getattr(transformers, "Qwen3_5ForCausalLM", None)
        if model_class is None:
            raise RuntimeError("Installed transformers lacks native Qwen3.5 support")
        config = config.get_text_config()
    model, loading = model_class.from_pretrained(
        model_id,
        config=config,
        dtype=dtype,
        low_cpu_mem_usage=True,
        output_loading_info=True,
        **common,
    )
    if any(
        loading.get(key) for key in ("missing_keys", "mismatched_keys", "error_msgs")
    ):
        raise RuntimeError(f"Checkpoint did not load completely: {loading}")
    model.to(device)
    model.eval()
    return (
        model,
        tokenizer,
        {
            "source": model_id,
            "revision": revision,
            "device": device,
            "dtype": str(dtype).removeprefix("torch."),
            "torch_version": torch.__version__,
            "transformers_version": transformers.__version__,
        },
    )


def main(argv=None, scorer=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--entity-type", required=True)
    parser.add_argument("--value", required=True)
    parser.add_argument("--context", required=True)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--revision", default=DEFAULT_REVISION)
    parser.add_argument(
        "--device", choices=("auto", "cuda", "mps", "cpu"), default="auto"
    )
    parser.add_argument("--max-tokens", type=int, default=4096)
    args = parser.parse_args(argv)
    if args.max_tokens < 1:
        parser.error("--max-tokens must be positive")
    if scorer is None:
        try:
            from semif_phase1.direct import score as scorer
        except ImportError as error:
            parser.error(
                "OpenJev/SemIf is missing; install the pinned package shown in this script's usage"
            )
            raise AssertionError from error

    model, tokenizer, metadata = load_model(args.model, args.revision, args.device)
    result = judge_entity(
        model=model,
        tokenizer=tokenizer,
        metadata=metadata,
        entity_type=args.entity_type,
        value=args.value,
        context=args.context,
        max_tokens=args.max_tokens,
        scorer=scorer,
    )
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
