"""Put one judge and one prompt strategy through a tier of the dataset.

Three engines sit behind the same result format. Three of the five judges are
generative models served by LM Studio over the OpenAI chat API, and the driver
is serial on purpose: a 96 GB MoE cannot share a 128 GB box with anything, so
eviction is explicit (``lms unload --all``) rather than left to LM Studio's JIT
heuristics, which would make load times — and therefore any latency figure —
irreproducible. The fourth, OpenJev, is not a chat model at all: it scores the
three verdict options directly, takes no system prompt, and therefore runs under
``--strategy none``, once, whatever prompt work happens around it. The fifth,
PII-Tracer, is a token tagger: it re-tags the context and the verdict comes from
what it predicts on the span (see ``tagger_verdict``), also under ``none``.

Every judgement is appended as it lands, so a kill keeps what was measured and
``--resume`` (the default) picks up exactly where it stopped. A model that
cannot produce a parseable verdict inside its budget is recorded as
``UNPARSEABLE`` with the raw head of its answer, never dropped: production keeps
such a finding (fail-open) and the scorer has to count it the same way.

Usage::

    python run_judges.py --judge ministral --size light
    python run_judges.py --judge gpt-oss-120b --size normal --strategy per_type
    python run_judges.py --all --size light
    python run_judges.py --judge openjev --size light      # service venv only
    python run_judges.py --judge pii-tracer --size large   # service venv only
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock
from typing import Dict, List, Optional, Tuple

import config
import prompts as prompt_lib

BASE_URL = "http://localhost:1234/v1"
LMS = str(Path.home() / ".lmstudio" / "bin" / "lms")
VERDICTS = ("TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE")

VERDICT_SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "confidence": {"type": "number"},
        "verdict": {"type": "string", "enum": list(VERDICTS)},
    },
    "required": ["reason", "confidence", "verdict"],
}


@dataclass(frozen=True)
class JudgeSpec:
    key: str
    label: str
    engine: str
    model: str
    size_gb: float
    max_tokens: int = 700
    parallel: int = 4
    context_length: int = 4096
    extra_body: Dict = field(default_factory=dict)
    note: str = ""


JUDGES: List[JudgeSpec] = [
    JudgeSpec(
        "ministral", "Ministral-3B-PII (modèle du détecteur)", "lmstudio",
        "ministral-3b-pii-preview@q8_0", 3.65, max_tokens=500, parallel=6,
        note="The quantisation data.sql seeds as lm_studio_model.",
    ),
    JudgeSpec(
        "gpt-oss-120b", "GPT-OSS-120B (MoE, reasoning low)", "lmstudio",
        "openai/gpt-oss-120b", 63.39, max_tokens=2048, parallel=4,
        context_length=8192, extra_body={"reasoning_effort": "low"},
    ),
    JudgeSpec(
        "deepseek-v4-flash", "DeepSeek-V4-Flash 256x8.4B q2_k_xl", "lmstudio",
        "deepseek-v4-flash-0731@q2_k_xl", 96.83, max_tokens=1536, parallel=4,
        context_length=16384,
        note="Loading 96.8 GB needs `sudo sysctl iogpu.wired_limit_mb=118784` "
             "first; the macOS default caps the GPU near 96 GB and the load fails. "
             "This is the judge that produced the PCYBER verdicts of 2026-09-18.",
    ),
    JudgeSpec(
        "openjev", "OpenJev/SemIf — Qwen3.5-4B", "openjev",
        "Qwen/Qwen3.5-4B", 8.7, max_tokens=4096, parallel=1,
        note="Scores the three verdict options directly instead of generating "
             "JSON, so it takes no system prompt and is always run with "
             "--strategy none. Needs pii-detector-service/.venv (semif_phase1, "
             "transformers, torch).",
    ),
    JudgeSpec(
        "pii-tracer", "PII-Tracer 0.6B (encodeur, Perplexity)", "tagger",
        "perplexity-ai/PII-Tracer", 1.19, parallel=1,
        note="Token tagger, not a chat model: LM Studio cannot serve it, it runs "
             "through transformers>=5.2 in pii-detector-service/.venv. IP types "
             "are outside its vocabulary and come back UNSURE.",
    ),
]

JUDGES_BY_KEY = {spec.key: spec for spec in JUDGES}

# trust_remote_code runs the repo's modeling file: pin the reviewed revision.
PII_TRACER_REVISION = "d25c16f2e57e321f6d2527715c01df9112f956f5"

# pii_type -> PII-Tracer labels that confirm it, per the category definitions of
# the PII-TRACE paper (arXiv 2609.22200): account_number covers national ID, SSN,
# IBAN, card and sort code; secret covers password, API key, CVV and PIN. Arguable
# labels are accepted so the model is never under-rated by a strict reading.
# None: no category covers the type, the tagger abstains instead of rejecting.
_ACCOUNT = ("account_number",)
_SECRET = ("secret",)
_GOV_ID = ("account_number", "other_pii")
PII_TRACER_LABELS: Dict[str, Optional[Tuple[str, ...]]] = {
    "PASSWORD": _SECRET, "API_KEY": _SECRET,
    "PIN": _SECRET + _ACCOUNT, "CVV": _SECRET + _ACCOUNT,
    "ACCOUNT_NUMBER": _ACCOUNT, "BANK_ROUTING_NUMBER": _ACCOUNT,
    "IBAN": _ACCOUNT, "IBAN_CODE": _ACCOUNT, "SWIFT_BIC": _ACCOUNT,
    "CREDIT_CARD": _ACCOUNT, "CREDIT_DEBIT_CARD": _ACCOUNT,
    "SOCIAL_SECURITY_NUMBER": _GOV_ID, "SOCIALNUM": _GOV_ID,
    "NATIONAL_ID": _GOV_ID, "DRIVER_LICENSE_NUMBER": _GOV_ID,
    "IP_ADDRESS": None, "IPV4": None, "IPV6": None,
}


def tagger_verdict(spans: List[Tuple[int, int, str]], accepted: Optional[Tuple[str, ...]],
                   start: int, end: int, compatible_prob: float) -> Dict:
    """Turn a tagger's predictions on the judged span into a judge answer.

    The verdict follows the decoded spans; the confidence follows the highest
    probability, over the span's tokens, of any accepted tag. A rejection is
    therefore only confident when the model gave the claimed type no chance at
    all, which lets the production threshold act on a tagger as on an LLM.
    """
    if accepted is None:
        return {"verdict": "UNSURE", "reason": "type outside the tagger vocabulary",
                "confidence": 0.0}
    labels = sorted({label for span_start, span_end, label in spans
                     if span_start < end and start < span_end})
    if any(label in accepted for label in labels):
        return {"verdict": "TRUE_POSITIVE", "reason": f"predicted {labels}",
                "confidence": compatible_prob}
    reason = f"rival label predicted: {labels}" if labels else "no entity predicted on the span"
    return {"verdict": "FALSE_POSITIVE", "reason": reason, "confidence": 1.0 - compatible_prob}


def load_records(size: str, types: Optional[List[str]], limit: Optional[int]) -> List[dict]:
    path = config.DATASETS_DIR / f"{size}.jsonl"
    if not path.exists():
        sys.exit(f"{path} is missing — run build_dataset.py first")
    records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if types:
        records = [r for r in records if r["pii_type"] in types]
    if limit:
        step = max(1, len(records) // limit)
        records = records[::step][:limit]
    return records


def result_path(spec: JudgeSpec, strategy: str, size: str, tag: str) -> Path:
    stem = f"{spec.key}__{strategy}__{size}"
    return config.RESULTS_DIR / f"{stem}{f'__{tag}' if tag else ''}.jsonl"


def parse_verdict(content: str) -> Dict:
    """Read the verdict out of the content channel, or report why it could not.

    Structured output is requested, but a reasoning model that burns its budget
    mid-chain answers with an empty or truncated channel and some servers wrap
    the JSON in prose. The JSON decoder handles braces inside quoted strings
    while recovering the first object from such wrappers.
    """
    text = (content or "").strip()
    if not text:
        return {"verdict": "UNPARSEABLE", "reason": "empty content channel", "confidence": 0.0}
    start = text.find("{")
    if start < 0:
        return {"verdict": "UNPARSEABLE", "reason": text[:200], "confidence": 0.0}
    try:
        parsed, _ = json.JSONDecoder().raw_decode(text, start)
    except ValueError:
        return {"verdict": "UNPARSEABLE", "reason": text[:200], "confidence": 0.0}
    verdict = str(parsed.get("verdict", "")).upper()
    if verdict not in VERDICTS:
        return {"verdict": "UNPARSEABLE", "reason": f"unknown verdict {verdict!r}",
                "confidence": 0.0}
    try:
        confidence = min(1.0, max(0.0, float(parsed.get("confidence") or 0.0)))
    except (TypeError, ValueError):
        confidence = 0.0
    return {"verdict": verdict, "reason": str(parsed.get("reason", ""))[:400],
            "confidence": confidence}


def _run_lms(args: List[str], timeout: int = 1200) -> None:
    result = subprocess.run([LMS, *args], capture_output=True, text=True, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError(f"lms {' '.join(args)} failed ({result.returncode}): "
                           f"{result.stderr.strip()[:400]}")


def prepare_model(spec: JudgeSpec) -> float:
    print("  unloading all models...", flush=True)
    try:
        _run_lms(["unload", "--all"], timeout=300)
    except Exception as exc:
        print(f"  (unload: {exc})", flush=True)
    print(f"  loading {spec.model} ({spec.size_gb:.1f} GB)...", flush=True)
    started = time.time()
    _run_lms(["load", spec.model, "-y",
              "--context-length", str(spec.context_length),
              "--parallel", str(spec.parallel), "--ttl", "7200"])
    elapsed = time.time() - started
    print(f"  loaded in {elapsed:.0f}s", flush=True)
    return elapsed


def judge_one_lmstudio(client, spec: JudgeSpec, record: dict,
                       strategy: prompt_lib.Strategy) -> Dict:
    payload = {
        "model": spec.model,
        "messages": [
            {"role": "system", "content": strategy.system(record["pii_type"])},
            {"role": "user", "content": prompt_lib.user_prompt(record)},
        ],
        "temperature": 0,
        "stream": False,
        "max_tokens": spec.max_tokens,
        "response_format": {
            "type": "json_schema",
            "json_schema": {"name": "pii_verdict", "strict": True, "schema": VERDICT_SCHEMA},
        },
        **spec.extra_body,
    }
    started = time.time()
    try:
        response = client.post(f"{BASE_URL}/chat/completions", json=payload)
        response.raise_for_status()
        body = response.json()
    except Exception as exc:
        return {"verdict": "ERROR", "reason": f"{type(exc).__name__}: {exc}"[:300],
                "confidence": 0.0, "latency_s": round(time.time() - started, 2),
                "prompt_tokens": 0, "completion_tokens": 0, "reasoning_tokens": 0,
                "finish_reason": "error"}
    latency = time.time() - started
    choice = (body.get("choices") or [{}])[0]
    usage = body.get("usage") or {}
    details = usage.get("completion_tokens_details") or {}
    parsed = parse_verdict((choice.get("message") or {}).get("content") or "")
    return {**parsed,
            "latency_s": round(latency, 2),
            "prompt_tokens": usage.get("prompt_tokens", 0),
            "completion_tokens": usage.get("completion_tokens", 0),
            "reasoning_tokens": details.get("reasoning_tokens", 0),
            "finish_reason": choice.get("finish_reason", "")}


def run_lmstudio(spec: JudgeSpec, records: List[dict], strategy: prompt_lib.Strategy,
                 output: Path, done: set, keep_loaded: bool) -> float:
    import httpx

    todo = [r for r in records if r["finding_id"] not in done]
    load_seconds = 0.0 if keep_loaded else prepare_model(spec)
    # trust_env=False and HTTP/1.1: LM Studio hangs on the h2c upgrade, and a
    # localhost endpoint must never be routed through a corporate proxy. Same
    # constraint MinistralDetector carries in production.
    client = httpx.Client(http2=False, trust_env=False,
                          timeout=httpx.Timeout(600.0, connect=30.0))
    write_lock = Lock()
    counter = {"n": 0}
    started = time.time()
    with output.open("a", encoding="utf-8") as handle:
        def task(record: dict) -> None:
            result = judge_one_lmstudio(client, spec, record, strategy)
            with write_lock:
                handle.write(json.dumps({"finding_id": record["finding_id"],
                                         "judge": spec.key, "model": spec.model,
                                         **result}, ensure_ascii=False) + "\n")
                handle.flush()
                counter["n"] += 1
                if counter["n"] % 20 == 0 or counter["n"] == len(todo):
                    rate = counter["n"] / (time.time() - started)
                    left = (len(todo) - counter["n"]) / rate if rate else 0
                    print(f"    {counter['n']}/{len(todo)} ({rate:.2f}/s, "
                          f"~{left / 60:.1f} min left)", flush=True)

        with ThreadPoolExecutor(max_workers=spec.parallel) as pool:
            list(pool.map(task, todo))
    client.close()
    return load_seconds


def run_openjev(spec: JudgeSpec, records: List[dict], output: Path, done: set,
                device: str) -> float:
    scripts = config.HERE.parents[1] / "pii-detector-service" / "scripts"
    sys.path.insert(0, str(scripts))
    from openjev_entity_judge import DEFAULT_REVISION, judge_entity, load_model

    todo = [r for r in records if r["finding_id"] not in done]
    started = time.time()
    model, tokenizer, metadata = load_model(spec.model, DEFAULT_REVISION, device)
    load_seconds = time.time() - started
    with output.open("a", encoding="utf-8") as handle:
        for index, record in enumerate(todo, 1):
            context = prompt_lib.mark_span(record["document_text"], record["start"], record["end"])
            result = judge_entity(model=model, tokenizer=tokenizer, metadata=metadata,
                                  entity_type=record["pii_type"], value=record["value"],
                                  context=context, max_tokens=spec.max_tokens)
            handle.write(json.dumps({
                "finding_id": record["finding_id"], "judge": spec.key, "model": spec.model,
                "verdict": result["verdict"],
                "reason": f"OpenJev selected {result['verdict']}",
                "confidence": result["selected_score"],
                "latency_s": round(result["seconds"], 3),
                "prompt_tokens": result["input_tokens"], "completion_tokens": 0,
                "reasoning_tokens": 0, "finish_reason": "stop",
                "option_scores": result["scores"], "score_status": result["score_status"],
            }, ensure_ascii=False) + "\n")
            handle.flush()
            if index % 20 == 0 or index == len(todo):
                print(f"    {index}/{len(todo)}", flush=True)
    return load_seconds


def run_tagger(spec: JudgeSpec, records: List[dict], output: Path, done: set,
               device: str) -> float:
    import torch
    from transformers import AutoModel

    if device == "auto":
        device = "mps" if torch.backends.mps.is_available() else "cpu"
    todo = [r for r in records if r["finding_id"] not in done]
    started = time.time()
    model = AutoModel.from_pretrained(spec.model, revision=PII_TRACER_REVISION,
                                      trust_remote_code=True).to(device).eval()
    tag_types = [model.config.id2label[i].partition("-")[2]
                 for i in range(model.config.num_token_labels)]
    load_seconds = time.time() - started
    with output.open("a", encoding="utf-8") as handle:
        for index, record in enumerate(todo, 1):
            item_started = time.time()
            text, start, end = record["document_text"], record["start"], record["end"]
            encoded = model.tokenizer(text, return_offsets_mapping=True, return_tensors="pt",
                                      truncation=True, max_length=model.config.max_seq_len)
            offsets = [tuple(o) for o in encoded.pop("offset_mapping")[0].tolist()]
            with torch.no_grad():
                logits = model(**{k: v.to(device) for k, v in encoded.items()}).logits[0].float().cpu()
            spans = [(s.start, s.end, s.label) for s in model.viterbi.decode(logits, offsets, text=text)]
            accepted = PII_TRACER_LABELS[record["pii_type"]]
            on_span = [i for i, (a, b) in enumerate(offsets) if a < end and start < b]
            compatible = [i for i, t in enumerate(tag_types) if accepted and t in accepted]
            compatible_prob = (float(logits.softmax(-1)[on_span][:, compatible].sum(-1).max())
                               if on_span and compatible else 0.0)
            result = tagger_verdict(spans, accepted, start, end, compatible_prob)
            handle.write(json.dumps({
                "finding_id": record["finding_id"], "judge": spec.key, "model": spec.model,
                **result, "latency_s": round(time.time() - item_started, 4),
                "prompt_tokens": len(offsets), "completion_tokens": 0,
                "reasoning_tokens": 0, "finish_reason": "stop",
                "in_vocabulary": accepted is not None,
            }, ensure_ascii=False) + "\n")
            handle.flush()
            if index % 200 == 0 or index == len(todo):
                print(f"    {index}/{len(todo)}", flush=True)
    return load_seconds


def run_judge(spec: JudgeSpec, records: List[dict], strategy_name: str, size: str,
              tag: str, resume: bool, keep_loaded: bool, device: str) -> Path:
    strategies = prompt_lib.load_strategies()
    if spec.engine in ("openjev", "tagger"):
        strategy_name = "none"
        strategy = None
    else:
        strategy = strategies[strategy_name]

    output = result_path(spec, strategy_name, size, tag)
    output.parent.mkdir(parents=True, exist_ok=True)
    done: set = set()
    if resume and output.exists():
        done = {json.loads(line)["finding_id"]
                for line in output.read_text(encoding="utf-8").splitlines() if line.strip()}

    print(f"\n=== {spec.label} [{strategy_name}, {size}] ===", flush=True)
    todo = [r for r in records if r["finding_id"] not in done]
    if not todo:
        print(f"  already complete ({len(done)} judgements)", flush=True)
        return output
    print(f"  judging {len(todo)} findings ({len(done)} cached)", flush=True)

    started = time.time()
    if spec.engine == "openjev":
        load_seconds = run_openjev(spec, records, output, done, device)
    elif spec.engine == "tagger":
        load_seconds = run_tagger(spec, records, output, done, device)
    else:
        load_seconds = run_lmstudio(spec, records, strategy, output, done, keep_loaded)
    wall = time.time() - started

    (config.RESULTS_DIR / "meta.jsonl").open("a", encoding="utf-8").write(json.dumps({
        "judge": spec.key, "label": spec.label, "model": spec.model,
        "size_gb": spec.size_gb, "engine": spec.engine,
        "strategy": strategy_name, "dataset": size, "tag": tag,
        "result_file": output.name, "judged": len(todo),
        "wall_seconds": round(wall, 1), "load_seconds": round(load_seconds, 1),
        "parallel": spec.parallel, "max_tokens": spec.max_tokens,
        "context_length": spec.context_length,
        "bodies_used": (len(strategy.covered_types()) if strategy else 0),
    }, ensure_ascii=False) + "\n")
    print(f"  done in {wall / 60:.1f} min -> {output.name}", flush=True)
    return output


def main(argv: Optional[List[str]] = None) -> int:
    strategies = prompt_lib.load_strategies()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--judge", action="append", default=[],
                        help="judge key (repeatable); default: --all")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--size", default="light", choices=sorted(config.load_sizes()))
    parser.add_argument("--strategy", default=prompt_lib.default_strategy(),
                        choices=sorted(strategies))
    parser.add_argument("--type", action="append", dest="types", default=[],
                        help="restrict to a pii_type (repeatable)")
    parser.add_argument("--limit", type=int, default=None, help="stride-sample N findings")
    parser.add_argument("--tag", default="", help="suffix on the result file, for repeat runs")
    parser.add_argument("--keep-loaded", action="store_true",
                        help="judge with whatever LM Studio already serves")
    parser.add_argument("--device", choices=("auto", "cuda", "mps", "cpu"), default="auto",
                        help="OpenJev and PII-Tracer only")
    parser.add_argument("--no-resume", action="store_true")
    args = parser.parse_args(argv)

    if args.all or not args.judge:
        specs = JUDGES
    else:
        unknown = [k for k in args.judge if k not in JUDGES_BY_KEY]
        if unknown:
            sys.exit(f"unknown judge(s): {unknown}\nknown: {list(JUDGES_BY_KEY)}")
        specs = [JUDGES_BY_KEY[k] for k in args.judge]

    records = load_records(args.size, args.types, args.limit)
    print(f"dataset {args.size}: {len(records)} findings, strategy {args.strategy}, "
          f"{len(specs)} judge(s)")

    for spec in specs:
        if spec.note:
            print(f"  note [{spec.key}]: {spec.note}")
        try:
            run_judge(spec, records, args.strategy, args.size, args.tag,
                      not args.no_resume, args.keep_loaded, args.device)
        except Exception as exc:
            print(f"  !! {spec.key} aborted: {type(exc).__name__}: {exc}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
