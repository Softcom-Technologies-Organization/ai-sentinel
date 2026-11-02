"""Calibrate GPT-OSS-120B prompts and compare them with calibrated OpenJev."""
from __future__ import annotations

import argparse
from dataclasses import asdict, replace
from datetime import datetime, timezone
import hashlib
import inspect
import json
from pathlib import Path
import shlex
import statistics

import calibrate_openjev as shared
import config
import prompts
import run_judges

MODEL_KEY = "gpt-oss-120b"
OUT = config.RESULTS_DIR / "gpt-oss-calibration"
OPENJEV_METRICS = config.RESULTS_DIR / "openjev-calibration" / "metrics.json"


def choose(calibration, predictions):
    sweep = []
    for strategy, answers in predictions.items():
        for threshold in shared.THRESHOLDS:
            sweep.append({"strategy": strategy, "threshold": threshold,
                          **shared.metrics(calibration, answers, threshold)})
    safe = max((result for result in sweep if result["recall"] >= .99),
               key=lambda result: (result["utility"], result["recall"], result["threshold"]))
    max_f1 = max(sweep, key=lambda result: (result["f1"], result["recall"], result["threshold"]))
    return {"recall_priority": safe, "max_f1": max_f1, "sweep": sweep}


def comparison_rows(gpt_metrics, openjev_metrics, judge_label="GPT-OSS-120B"):
    rows = []
    for objective in ("recall_priority", "max_f1"):
        for judge, metrics in ((judge_label, gpt_metrics), ("OpenJev", openjev_metrics)):
            rows.append({"judge": judge, "objective": objective, **metrics[objective]})
    return rows


def load_answers(path, records, require_complete=True):
    answers = shared.predictions_from(path)
    expected = {record["finding_id"] for record in records}
    extra = set(answers) - expected
    missing = expected - set(answers)
    if extra:
        raise ValueError(f"Unexpected predictions in {path}: {len(extra)}")
    if require_complete and missing:
        raise ValueError(f"Missing predictions in {path}: {len(missing)}")
    return answers


def score_records(spec, records, strategy, output):
    existing = load_answers(output, records, require_complete=False)
    run_judges.run_lmstudio(spec, records, strategy, output, set(existing), keep_loaded=True)
    return load_answers(output, records)


def protocol_for(dataset, records, partitions, groups, strategies, spec):
    pii_types = sorted({record["pii_type"] for record in records})
    rendered_prompts = {
        name: {pii_type: strategy.system(pii_type) for pii_type in pii_types}
        for name, strategy in strategies.items()
    }
    return {
        "dataset_sha256": hashlib.sha256(dataset.read_bytes()).hexdigest(),
        "model": asdict(spec),
        "partitions": partitions,
        "groups": groups,
        "thresholds": shared.THRESHOLDS,
        "seed": shared.SEED,
        "strategies": list(strategies),
        "prompts_sha256": shared.digest(rendered_prompts),
        "prompt_builder_sha256": shared.digest(inspect.getsource(prompts)),
        "lmstudio_driver_sha256": hashlib.sha256(Path(run_judges.__file__).read_bytes()).hexdigest(),
        "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }


def result_metrics(records, answers, threshold, groups):
    return {**shared.metrics(records, answers, threshold),
            "ci95": shared.bootstrap(records, answers, threshold, groups)}


def pct(value):
    return f"{100 * value:.2f} %"


def metric_row(name, result):
    return (f"| {name} | {result['threshold']:.6g} | {pct(result['precision'])} | "
            f"{pct(result['recall'])} | {pct(result['f1'])} | "
            f"{pct(result['fp_elimination'])} | {result['fn']} | {result['utility']:.3f} |")


def report(records, partitions, groups, selection, calibration_predictions,
           test_predictions, output, protocol):
    judge_label = protocol["model"]["label"]
    calibration = [record for record in records if partitions[record["finding_id"]] == "calibration"]
    test = [record for record in records if partitions[record["finding_id"]] == "test"]
    settings = {
        "general_0.8": {"strategy": "general", "threshold": .8},
        "recall_priority": {key: selection["recall_priority"][key]
                            for key in ("strategy", "threshold")},
        "max_f1": {key: selection["max_f1"][key] for key in ("strategy", "threshold")},
    }
    results = {}
    for name, setting in settings.items():
        results[name] = {**setting,
                         **result_metrics(test, test_predictions[setting["strategy"]],
                                          setting["threshold"], groups)}
    shared.write_json(output / "metrics.json", results)

    openjev = shared.read_json(OPENJEV_METRICS) if OPENJEV_METRICS.exists() else None
    comparable_gpt = {name: results[name] for name in ("recall_priority", "max_f1")}
    rows = comparison_rows(comparable_gpt, openjev, judge_label) if openjev else []
    shared.write_json(output / "comparison.json", {
        "openjev_metrics": str(OPENJEV_METRICS.resolve()),
        "openjev_available": openjev is not None,
        "rows": rows,
    })

    lines = [f"# {judge_label} : calibration comme juge PII", "",
             f"Rapport généré le {datetime.now(timezone.utc).isoformat()}.", "",
             "## Résultats sur les documents réservés", "",
             "Les stratégies et seuils sont choisis exclusivement sur le lot de calibrage, "
             "puis figés avant le lot de test.", "",
             "| Réglage | Seuil FP | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |",
             "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for name in ("general_0.8", "recall_priority", "max_f1"):
        lines.append(metric_row(name, results[name]))
    lines += ["", "La classe positive est une vraie entité conservée. `UNSURE`, "
              "`UNPARSEABLE`, `ERROR` et un rejet sous le seuil restent conservés.", "",
              "| Réglage | TP | FP | TN | FN | IC 95 % précision | IC 95 % rappel | IC 95 % F1 |",
              "|---|---:|---:|---:|---:|---|---|---|"]
    for name in ("general_0.8", "recall_priority", "max_f1"):
        result = results[name]
        intervals = [" – ".join(pct(value) for value in result["ci95"][metric])
                     for metric in ("precision", "recall", "f1")]
        lines.append(f"| {name} | {result['tp']} | {result['fp']} | {result['tn']} | "
                     f"{result['fn']} | " + " | ".join(intervals) + " |")

    lines += ["", "## Comparaison avec OpenJev", ""]
    if not rows:
        lines.append("Les métriques OpenJev sont absentes. Exécuter d'abord `calibrate_openjev.py`.")
    else:
        lines += ["| Juge | Objectif | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité λ=10 |",
                  "|---|---|---:|---:|---:|---:|---:|---:|"]
        for row in rows:
            lines.append(f"| {row['judge']} | {row['objective']} | {pct(row['precision'])} | "
                         f"{pct(row['recall'])} | {pct(row['f1'])} | "
                         f"{pct(row['fp_elimination'])} | {row['fn']} | {row['utility']:.3f} |")

    lines += ["", "## Calibration des stratégies", "",
              "| Stratégie | Réglage | Seuil | Précision | Rappel | F1 | FP éliminés | TP perdus | Utilité |",
              "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for strategy in calibration_predictions:
        candidates = [item for item in selection["sweep"] if item["strategy"] == strategy]
        safe = max((item for item in candidates if item["recall"] >= .99),
                   key=lambda item: (item["utility"], item["recall"], item["threshold"]))
        max_f1 = max(candidates, key=lambda item: (item["f1"], item["recall"], item["threshold"]))
        for label, item in (("rappel prioritaire", safe), ("F1 maximal", max_f1)):
            lines.append(f"| {strategy} | {label} | {item['threshold']:.6g} | "
                         f"{pct(item['precision'])} | {pct(item['recall'])} | {pct(item['f1'])} | "
                         f"{pct(item['fp_elimination'])} | {item['fn']} | {item['utility']:.3f} |")

    by_type = {}
    for objective, setting in settings.items():
        lines += ["", f"## Résultats par type : {objective}", "",
                  f"Stratégie `{setting['strategy']}`, seuil `{setting['threshold']}`.", "",
                  "| Type | n | Précision | Rappel | F1 | FP éliminés | TP perdus |",
                  "|---|---:|---:|---:|---:|---:|---:|"]
        answers = test_predictions[setting["strategy"]]
        by_type[objective] = {}
        for pii_type in sorted({record["pii_type"] for record in test}):
            subset = [record for record in test if record["pii_type"] == pii_type]
            measured = shared.metrics(subset, answers, setting["threshold"])
            by_type[objective][pii_type] = measured
            lines.append(f"| {pii_type} | {measured['n']} | {pct(measured['precision'])} | "
                         f"{pct(measured['recall'])} | {pct(measured['f1'])} | "
                         f"{pct(measured['fp_elimination'])} | {measured['fn']} |")
    shared.write_json(output / "per-type.json", by_type)

    lines += ["", "## Temps de calcul", "",
              "| Stratégie | Prédictions | Latence médiane | Latence p95 | Temps cumulé |",
              "|---|---:|---:|---:|---:|"]
    for strategy, calibration_answers in calibration_predictions.items():
        answers = {**calibration_answers, **test_predictions.get(strategy, {})}
        latencies = sorted(float(answer.get("latency_s") or 0) for answer in answers.values())
        lines.append(f"| {strategy} | {len(answers)} | {statistics.median(latencies):.2f} s | "
                     f"{latencies[int(.95 * (len(latencies) - 1))]:.2f} s | "
                     f"{sum(latencies) / 60:.1f} min |")

    lines += ["", "## Protocole et limites", "",
              f"- Modèle : `{protocol['model']['model']}`, paramètres `{protocol['model']['extra_body']}`, "
              f"contexte `{protocol['model']['context_length']}`.",
              f"- Dataset : `{protocol['dataset_sha256']}` ; {len(calibration)} candidats de "
              f"calibrage et {len(test)} de test, avec exactement les partitions OpenJev.",
              "- Même grille de seuils et même fonction d'utilité que la campagne OpenJev.",
              "- Le modèle génère un verdict JSON et une confiance déclarée. Cette confiance n'est "
              "pas directement comparable au score conditionnel entre options d'OpenJev ; chaque "
              "juge est donc calibré séparément.",
              "- Les textes des corpus sont synthétiques et les faux positifs construits. Les "
              "résultats ne mesurent pas la fréquence réelle des erreurs en production.",
              "- Une seule exécution par candidat ; `temperature: 0` ne garantit pas une parfaite "
              "reproductibilité du serveur LM Studio.", "",
              "## Reproduire", "", "Depuis la racine du dépôt :", "", "```bash",
              protocol.get("command", "caffeinate -i pii-detector-service/.venv/bin/python benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py"),
              "```", "", "Les résultats sont repris automatiquement. `--report-only` régénère "
              "le rapport sans charger le modèle. `--keep-loaded` utilise le modèle déjà servi.", ""]
    (output / "RAPPORT.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(results, indent=2), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=config.DATASETS_DIR / "large.jsonl")
    parser.add_argument("--output", type=Path, default=OUT)
    parser.add_argument("--keep-loaded", action="store_true")
    parser.add_argument("--report-only", action="store_true")
    parser.add_argument("--judge", choices=("gpt-oss-120b", "qwen3.8-flash-next"), default=MODEL_KEY)
    args = parser.parse_args()

    spec = run_judges.JUDGES_BY_KEY[MODEL_KEY]
    if args.judge == "qwen3.8-flash-next":
        if not args.keep_loaded and not args.report_only:
            parser.error("Strata must already serve Qwen; use --keep-loaded")
        spec = replace(spec, key=args.judge, label="Qwen3.8-Flash-Next IQ3_S (Strata)",
                       engine="strata", model="qwen3.8-flash-next-iq3_s", size_gb=83.6,
                       context_length=32768, extra_body={"chat_template_kwargs": {"enable_thinking": False}})
        run_judges.BASE_URL = "http://127.0.0.1:8080/v1"
        if args.output == OUT:
            args.output = config.RESULTS_DIR / "qwen38-strata-calibration"
    records = shared.load_records(args.dataset)
    partitions, groups = shared.split_documents(records)
    all_strategies = prompts.load_strategies()
    strategies = {name: strategy for name, strategy in all_strategies.items()}
    protocol = protocol_for(args.dataset, records, partitions, groups, strategies, spec)
    protocol["base_url"] = run_judges.BASE_URL
    protocol["rendered_prompts"] = {name: {pii_type: strategy.system(pii_type)
                                          for pii_type in sorted({r["pii_type"] for r in records})}
                                    for name, strategy in strategies.items()}
    protocol["command"] = shlex.join(["caffeinate", "-i", "pii-detector-service/.venv/bin/python",
                                      "benchmarks/pii-judge-dataset-eval/calibrate_gpt_oss.py",
                                      "--judge", args.judge, "--output", str(args.output)]
                                     + (["--keep-loaded"] if spec.engine == "strata" else []))
    args.output.mkdir(parents=True, exist_ok=True)
    protocol_path = args.output / "protocol.json"
    if protocol_path.exists() and shared.digest(shared.read_json(protocol_path)) != shared.digest(protocol):
        raise ValueError("This output directory belongs to a different experiment")
    shared.write_json(protocol_path, protocol)

    calibration = [record for record in records if partitions[record["finding_id"]] == "calibration"]
    test = [record for record in records if partitions[record["finding_id"]] == "test"]
    print(f"Split: {len(calibration)} calibration / {len(test)} test; "
          f"{len(set(groups.values()))} groups", flush=True)

    calibration_predictions = {}
    test_predictions = {}
    if not args.report_only:
        if not args.keep_loaded:
            run_judges.prepare_model(spec)
        for name, strategy in strategies.items():
            calibration_predictions[name] = score_records(
                spec, calibration, strategy, args.output / f"{name}__calibration.jsonl")
        selection = choose(calibration, calibration_predictions)
        selection_path = args.output / "selection.json"
        if selection_path.exists() and shared.read_json(selection_path) != selection:
            raise ValueError("Frozen selection changed")
        shared.write_json(selection_path, selection)
        print("Frozen selection:", {
            objective: (selection[objective]["strategy"], selection[objective]["threshold"])
            for objective in ("recall_priority", "max_f1")
        }, flush=True)
        selected = {"general", selection["recall_priority"]["strategy"],
                    selection["max_f1"]["strategy"]}
        for name in selected:
            test_predictions[name] = score_records(
                spec, test, strategies[name], args.output / f"{name}__test.jsonl")
    else:
        selection = shared.read_json(args.output / "selection.json")
        for name in strategies:
            calibration_predictions[name] = load_answers(
                args.output / f"{name}__calibration.jsonl", calibration)
        selected = {"general", selection["recall_priority"]["strategy"],
                    selection["max_f1"]["strategy"]}
        for name in selected:
            test_predictions[name] = load_answers(args.output / f"{name}__test.jsonl", test)

    report(records, partitions, groups, selection, calibration_predictions,
           test_predictions, args.output, protocol)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
