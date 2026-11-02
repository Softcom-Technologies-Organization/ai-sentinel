"""Compare completed calibration campaigns using the existing held-out scorer."""
import argparse
from collections import defaultdict
import csv
import hashlib
import json
from pathlib import Path
import random

import numpy as np

import calibrate_gpt_oss as campaign
import calibrate_openjev as shared
import config


def paired_interval(records, arms, groups):
    buckets = defaultdict(list)
    for record in records:
        buckets[groups[record["finding_id"]]].append(record)
    counts = []
    for answers, threshold in arms:
        measured = [shared.metrics(bucket, answers, threshold) for bucket in buckets.values()]
        counts.append([[m["tp"], m["fp"], m["fn"]] for m in measured])
    counts = np.array(counts)
    rng = random.Random(shared.SEED)
    differences = []
    for _ in range(1000):
        indices = rng.choices(range(len(buckets)), k=len(buckets))
        totals = counts[:, indices, :].sum(axis=1)
        denominator = 2 * totals[:, 0] + totals[:, 1] + totals[:, 2]
        f1 = np.divide(2 * totals[:, 0], denominator, out=np.zeros(2), where=denominator != 0)
        differences.append(float(f1[0] - f1[1]))
    return np.quantile(differences, [.025, .975]).tolist()


def compare(first, second, output):
    folders = (first, second)
    protocols = [shared.read_json(folder / "protocol.json") for folder in folders]
    for key in ("dataset_sha256", "partitions", "groups", "thresholds", "seed", "strategies",
                "prompts_sha256", "prompt_builder_sha256", "lmstudio_driver_sha256", "runner_sha256"):
        if shared.digest(protocols[0][key]) != shared.digest(protocols[1][key]):
            raise ValueError(f"Incomparable campaigns: {key}")
    dataset = config.DATASETS_DIR / "large.jsonl"
    if hashlib.sha256(dataset.read_bytes()).hexdigest() != protocols[0]["dataset_sha256"]:
        raise ValueError("Dataset differs from the measured corpus")
    records = shared.load_records(dataset)
    test = [r for r in records if protocols[0]["partitions"][r["finding_id"]] == "test"]
    selections = [shared.read_json(folder / "selection.json") for folder in folders]
    result = {"dataset_sha256": protocols[0]["dataset_sha256"], "test_n": len(test),
              "models": [p["model"] for p in protocols], "objectives": {}}
    export = []
    lines = ["# Qwen3.8-Flash-Next / Strata contre GPT-OSS-120B", "",
             "Même benchmark existant : 3 600 candidats, 18 types ; six stratégies sur 1 240 cas de calibrage, "
             "puis 2 360 cas de test. Le meilleur F1 est choisi uniquement sur le calibrage.", "",
             "Les scores ci-dessous concernent le jugement de candidats PII et leur conservation après filtrage.", ""]
    for objective in ("max_f1", "general_0.8", "recall_priority"):
        settings = [({"strategy": "general", "threshold": .8} if objective == "general_0.8"
                     else {k: selection[objective][k] for k in ("strategy", "threshold")})
                    for selection in selections]
        arms = [(campaign.load_answers(folder / f"{setting['strategy']}__test.jsonl", test), setting["threshold"])
                for folder, setting in zip(folders, settings)]
        global_metrics = [shared.metrics(test, *arm) for arm in arms]
        interval = paired_interval(test, arms, protocols[0]["groups"])
        result["objectives"][objective] = {"settings": settings, "metrics": global_metrics,
                                            "paired_f1_delta_ci95": interval, "by_type": {}}
        lines += [f"## {objective}", "", "| Modèle | Prompt | Seuil | P | R | F1 | Erreurs HTTP |",
                  "|---|---|---:|---:|---:|---:|---:|"]
        for label, setting, metrics in zip(("Qwen / Strata", "GPT-OSS"), settings, global_metrics):
            lines.append(f"| {label} | {setting['strategy']} | {setting['threshold']:g} | "
                         f"{metrics['precision']:.2%} | {metrics['recall']:.2%} | {metrics['f1']:.2%} | {metrics['errors']} |")
        lines += ["", f"IC 95 % de l'écart F1 Qwen − GPT-OSS : [{100*interval[0]:+.2f}, {100*interval[1]:+.2f}] points. "
                  "1 000 rééchantillonnages appariés par groupe de documents, graine identique au benchmark.", "",
                  "| PII | n | P Qwen | R Qwen | F1 Qwen | P GPT | R GPT | F1 GPT | Δ F1 (points) |",
                  "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
        for pii_type in sorted({r["pii_type"] for r in test}):
            subset = [r for r in test if r["pii_type"] == pii_type]
            values = [shared.metrics(subset, *arm) for arm in arms]
            result["objectives"][objective]["by_type"][pii_type] = values
            cells = [f"{value[metric]:.2%}" for value in values for metric in ("precision", "recall", "f1")]
            lines.append(f"| {pii_type} | {len(subset)} | " + " | ".join(cells)
                         + f" | {100*(values[0]['f1']-values[1]['f1']):+.2f} |")
            for model, value in zip(result["models"], values):
                export.append({"objective": objective, "model": model["model"], "pii_type": pii_type, **value})
    lines += ["", "## Limites", "",
              "Corpus et faux positifs synthétiques. Les incertitudes par type ne sont pas testées séparément. "
              "Quantifications et moteurs différents : IQ3_S / Strata et MXFP4 / LM Studio. "
              "Qwen utilise un contexte de 32 768 tokens, GPT-OSS 8 192 ; même budget de sortie de 2 048 tokens, "
              "mêmes prompts et quatre requêtes simultanées. Les erreurs et abstentions suivent la règle de conservation du benchmark.", "",
              "[Résultats JSON](comparison.json) · [Détail CSV](per-type.csv)"]
    output.mkdir(parents=True, exist_ok=True)
    shared.write_json(output / "comparison.json", result)
    (output / "RAPPORT.md").write_text("\n".join(lines) + "\n")
    with (output / "per-type.csv").open("w") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(export[0]))
        writer.writeheader()
        writer.writerows(export)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("qwen", type=Path)
    parser.add_argument("gpt", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    compare(args.qwen, args.gpt, args.output)
