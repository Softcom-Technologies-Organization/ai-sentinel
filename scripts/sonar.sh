#!/usr/bin/env bash
# Runs the SonarQube analysis of a single module against the local server (http://localhost:9000).
#
#   scripts/sonar.sh <api|ui|detector> [--skip-tests]
#
# With --skip-tests the coverage report is not regenerated: the last one still present in the
# module is reused, so the analysis keeps reporting the coverage of the previous full run.
# SONAR_TOKEN is read from the environment by sonar-scanner and sonar-maven-plugin.
set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") <api|ui|detector> [--skip-tests]" >&2
  exit 2
}

# Analysing without a coverage report resets the coverage of the project on the server, so a report
# that the tests were supposed to produce is a hard error.
require_coverage_report() {
  local report=$1
  [[ -f $report ]] && return 0
  if $run_tests; then
    echo "ERROR: the tests did not produce $report. Fix them before analysing, otherwise SonarQube would record 0% coverage." >&2
    exit 1
  fi
  echo "WARNING: $report is missing, the analysis will report no coverage." >&2
}

module=${1:-}
option=${2:-}
[[ -n $module ]] || usage
[[ -z $option || $option == --skip-tests ]] || usage

run_tests=true
[[ $option == --skip-tests ]] && run_tests=false

: "${SONAR_TOKEN:?SONAR_TOKEN is not set. Export it in ~/.zshrc, then restart the IDE so it picks up the new environment.}"

# Without an explicit URL, SonarScanner CLI 8+ targets SonarQube Cloud instead of the local server.
export SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case $module in
  api)
    cd "$repo_root/pii-reporting-api"
    if $run_tests; then
      mvn clean verify
    else
      mvn test-compile -DskipTests
    fi
    require_coverage_report target/site/jacoco/jacoco.xml
    mvn sonar:sonar
    ;;

  detector)
    cd "$repo_root/pii-detector-service"
    if $run_tests; then
      .venv/bin/pytest tests/unit --cov=pii_detector --cov-report=xml --cov-report=term-missing
    fi
    require_coverage_report coverage.xml
    sonar-scanner
    ;;

  ui)
    cd "$repo_root/pii-reporting-ui"
    if $run_tests; then
      # The Angular unit-test builder overrides the reporter list of vitest.config.ts, so lcov has
      # to be requested here. Vitest also exits non-zero when a coverage threshold is not met, but
      # the report is already written at that point, so the analysis is still worth running.
      pnpm exec ng test --coverage --coverage-reporters=lcovonly --watch=false || echo "WARNING: tests failed, continuing with the report they produced." >&2
    fi
    require_coverage_report coverage/vitest/lcov.info
    sonar-scanner
    ;;

  *) usage ;;
esac
