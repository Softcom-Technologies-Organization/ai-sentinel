import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BENCH = ROOT / 'benchmarks/pii-judge-dataset-eval'
sys.path.insert(0, str(ROOT / 'pii-detector-service'))

def load(path):
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    indexed = {row['finding_id']: row for row in rows}
    assert len(rows) == len(indexed), path
    return indexed

def rejected(rows, threshold):
    return {key for key, row in rows.items() if row['verdict'] == 'FALSE_POSITIVE' and row['confidence'] >= threshold}

def metrics(ids, drops):
    positive = {key for key in ids if gold[key]['ground_truth'] == 'TP'}
    negative = set(ids) - positive
    tp, fn = len(positive - drops), len(positive & drops)
    fp, tn = len(negative - drops), len(negative & drops)
    return dict(tp=tp, fp=fp, tn=tn, fn=fn, precision=tp/(tp+fp), recall=tp/(tp+fn), f1=2*tp/(2*tp+fp+fn))

gold = load(BENCH / 'datasets/large.jsonl')
op = BENCH / 'results/openjev-calibration'
gp = BENCH / 'results/gpt-oss-calibration'
protocol = json.loads((op / 'protocol.json').read_text())
gprotocol = json.loads((gp / 'protocol.json').read_text())
assert protocol['partitions'] == gprotocol['partitions']
assert protocol['dataset_sha256'] == gprotocol['dataset_sha256'] == hashlib.sha256((BENCH/'datasets/large.jsonl').read_bytes()).hexdigest()
oj = load(op / 'per_type__test.jsonl')
ids = set(oj)
assert ids == {key for key, split in protocol['partitions'].items() if split == 'test'}
assert len(ids) == 2360
odrop = rejected(oj, 0)
assert [metrics(ids, odrop)[key] for key in ('tp','fp','tn','fn')] == [1193,809,354,4]
otime = sum(row['latency_s'] for row in oj.values())
selection = json.loads((gp / 'selection.json').read_text())
output = dict(n=len(ids), baseline=metrics(ids,set()), openjev=metrics(ids,odrop), openjev_seconds=otime, calls_avoided=len(odrop), call_reduction=len(odrop)/len(ids), arms={})
arms = [('general', .8)] + [(selection[key]['strategy'], selection[key]['threshold']) for key in ('recall_priority','max_f1')]
for strategy, threshold in arms:
    judge = load(gp / (strategy + '__test.jsonl'))
    assert ids == set(judge)
    errors = sum(row['verdict'] in ('ERROR','UNPARSEABLE') for row in judge.values())
    drops = rejected(judge, threshold)
    all_seconds = sum(row['latency_s'] for row in judge.values())
    saved = sum(judge[key]['latency_s'] for key in odrop)
    summary = dict(threshold=threshold, judge=metrics(ids,drops), cascade=metrics(ids,odrop|drops), confirmation_only=metrics(ids,odrop&drops), extra_fp_removed=len([key for key in odrop-drops if gold[key]['ground_truth']=='FP']), extra_tp_lost=len([key for key in odrop-drops if gold[key]['ground_truth']=='TP']), judge_latency_sum_s=all_seconds, avoided_latency_sum_s=saved, serial_proxy_s=otime+all_seconds-saved, four_workers_proxy_s=otime+(all_seconds-saved)/4, judge_four_workers_proxy_s=all_seconds/4)
    summary['by_type'] = {}
    summary['errors_kept'] = errors
    for pii_type in sorted({gold[key]['pii_type'] for key in ids}):
        subset = {key for key in ids if gold[key]['pii_type']==pii_type}
        summary['by_type'][pii_type] = dict(n=len(subset), openjev=metrics(subset,odrop), cascade=metrics(subset,odrop|drops), calls_avoided=len(subset&odrop), openjev_s=sum(oj[key]['latency_s'] for key in subset), avoided_judge_s=sum(judge[key]['latency_s'] for key in subset&odrop))
    output['arms'][strategy] = summary

calibration_oj = load(op / 'per_type__calibration.jsonl')
calibration_gpt = load(gp / 'checklist__calibration.jsonl')
calibration_ids = set(calibration_oj)
assert calibration_ids == set(calibration_gpt)
assert calibration_ids == {key for key, split in protocol['partitions'].items() if split == 'calibration'}
assert not calibration_ids & ids
calibration_drops = rejected(calibration_gpt, .975)
margin = lambda row: row['option_logits'][1] - row['option_logits'][0]
utilities = {}
for threshold in (step / 2 for step in range(-16, 5)):
    drops = {key for key in calibration_drops if margin(calibration_oj[key]) > threshold}
    scores = metrics(calibration_ids, drops)
    utilities[threshold] = (scores['tn'] - 10 * scores['fn']) / (scores['tn'] + scores['fp'])
margin_threshold = max(utilities, key=lambda threshold: (utilities[threshold], threshold))
judge = load(gp / 'checklist__test.jsonl')
routed = {key for key in ids if margin(oj[key]) > margin_threshold}
output['exploratory_keep_routing'] = dict(
    calibration_margin_threshold=margin_threshold,
    calls=len(routed),
    calls_avoided=len(ids-routed),
    metrics=metrics(ids, routed & rejected(judge, .975)),
)
from pii_detector.domain.entity.pii_entity import PIIEntity
from pii_detector.infrastructure.postfilter.format_postfilter_validator import FormatPostfilterValidator

validator = FormatPostfilterValidator()
format_drops = set()
for key in ids:
    row = gold[key]
    entity = PIIEntity(row['value'], row['pii_type'], row['type_label'], row['start'], row['end'], 1.0)
    _, rejections = validator.filter_with_verdicts(row['document_text'], [entity])
    if rejections:
        format_drops.add(key)
survivors = ids - format_drops
output['format_filter'] = dict(
    alone=metrics(ids, format_drops),
    overlap_with_openjev=len(format_drops & odrop),
    remaining_n=len(survivors),
    remaining_baseline=metrics(survivors, set()),
    after_openjev=metrics(survivors, odrop),
    additional_calls_avoided=len(odrop - format_drops),
    additional_call_reduction=len(odrop - format_drops)/len(survivors),
)
print(json.dumps(output, indent=2))
