#!/usr/bin/env bash
# Records, or checks against, the exact fingerprint of the three test suites.
#
#   qa/sonar/baseline.sh record [api|detector|ui]    # writes the reference
#   qa/sonar/baseline.sh check  [api|detector|ui]    # compares, non-zero if a NEW test fails
#
# The suites are not green on this branch (the UI has known pre-existing failures), so "all
# green" cannot be the safety criterion. The criterion is "the same failures as before":
# check only fails when a test that used to pass starts failing.
set -uo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
out_dir="$repo_root/qa/sonar/baseline"
mode=${1:-}
only=${2:-all}
[[ $mode == record || $mode == check ]] || { echo "Usage: $(basename "$0") <record|check> [api|detector|ui]" >&2; exit 2; }
mkdir -p "$out_dir"

# Sorted list of failing test identifiers, one per line — the only thing compared.
failures_api() {
  python3 - "$repo_root/pii-reporting-api/target/surefire-reports" <<'PY'
import sys, glob, xml.etree.ElementTree as ET
names = []
for f in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    for case in ET.parse(f).getroot().iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            names.append(f"{case.get('classname')}.{case.get('name')}")
# No trailing blank line when nothing failed: an empty file must stay empty, otherwise the
# comparison sees a phantom failure named "".
sys.stdout.write("".join(n + "\n" for n in sorted(names)))
PY
}

failures_detector() { grep -oE '^FAILED [^ ]+' "$1" | sed 's/^FAILED //' | sort -u; }

# Read the JSON reporter rather than the console output: the pretty printer only lists failing
# test names, and its layout is not a stable contract.
failures_ui() {
  python3 - "$1" <<'PY'
import sys, json
report = json.load(open(sys.argv[1]))
names = [a["fullName"]
         for suite in report["testResults"]
         for a in suite["assertionResults"]
         if a["status"] == "failed"]
sys.stdout.write("".join(n + "\n" for n in sorted(names)))
PY
}

run_suite() {
  local name=$1
  local log="$out_dir/$name.log"
  echo ">>> $name"
  case $name in
    api)
      (cd "$repo_root/pii-reporting-api" && MAVEN_OPTS=-Xmx1024m mvn clean test -Pci-build) >"$log" 2>&1
      failures_api >"$out_dir/$name.$suffix" ;;
    detector)
      (cd "$repo_root/pii-detector-service" && PYTHONPATH="$repo_root/pii-detector-service" \
        .venv/bin/python -m pytest tests/unit/ -q --tb=no -rf) >"$log" 2>&1
      failures_detector "$log" >"$out_dir/$name.$suffix" ;;
    ui)
      (cd "$repo_root/pii-reporting-ui" && pnpm exec ng test --watch=false --coverage \
        --coverage-reporters=lcovonly --reporters=json --output-file="$out_dir/ui-report.json") >"$log" 2>&1
      failures_ui "$out_dir/ui-report.json" >"$out_dir/$name.$suffix" ;;
  esac
  echo "    $(wc -l <"$out_dir/$name.$suffix" | tr -d ' ') test(s) en echec — log: qa/sonar/baseline/$name.log"
}

suffix=$([[ $mode == record ]] && echo failures || echo current)
suites=$([[ $only == all ]] && echo "api detector ui" || echo "$only")
status=0

for suite in $suites; do
  run_suite "$suite"
  [[ $mode == record ]] && continue
  ref="$out_dir/$suite.failures"
  if [[ ! -f $ref ]]; then
    echo "    ⛔ pas de reference pour $suite — lancer 'baseline.sh record $suite' d'abord" >&2
    status=1
    continue
  fi
  # Only newly-failing tests matter. A test that was failing and now passes is an improvement.
  new_failures=$(comm -13 "$ref" "$out_dir/$suite.current")
  if [[ -n $new_failures ]]; then
    echo "    ⛔ REGRESSION — tests qui passaient et qui echouent maintenant :" >&2
    echo "$new_failures" | sed 's/^/       /' >&2
    status=1
  else
    echo "    ✅ aucune regression"
  fi
done

exit $status
