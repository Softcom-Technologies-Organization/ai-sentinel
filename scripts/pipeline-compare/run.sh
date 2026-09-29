#!/usr/bin/env bash
set -euo pipefail

config_dir="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$config_dir/../.." && pwd)"
variant="${1:-}"
service="${2:-}"

case "$variant" in
  baseline)
    source_root="$repo/.worktrees/detection-baseline"
    api_port=8181
    internal_port=8191
    grpc_port=50052
    ui_port=4201
    ;;
  current)
    source_root="$repo"
    api_port=8080
    internal_port=8190
    grpc_port=50051
    ui_port=4200
    ;;
  *) echo "Usage: $0 {baseline|current} {db|api|detector|ui}" >&2; exit 2 ;;
esac

set -a
source "$repo/.worktrees/pipeline-compare/$variant.env"
set +a

case "$service" in
  db)
    if [[ "$variant" == baseline ]]; then
      exec docker compose -f "$config_dir/compose.yml" up -d --wait
    fi
    exec docker start ai-sentinel-db
    ;;
  api)
    mkdir -p "$source_root/personally-identifiable-information-scan-results"
    cd "$source_root/pii-reporting-api"
    export PII_REPORTING_API_EXPORT_DIR="$source_root/personally-identifiable-information-scan-results"
    export LOG_PATH="$source_root/pii-reporting-api/logs"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
    export SPRING_DATASOURCE_USERNAME="$DB_USER"
    export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
    export PII_SCAN_AUTO_RESUME_ENABLED=false
    exec mvn -Dmaven.test.skip=true spring-boot:run \
      "-Dspring-boot.run.jvmArguments=${API_JVM_ARGS:-}" \
      "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=$api_port --spring.devtools.livereload.enabled=false --spring.datasource.url=$SPRING_DATASOURCE_URL --pii-detector.host=127.0.0.1 --pii-detector.port=$grpc_port --armeria.internal-services.port=$internal_port"
    ;;
  detector)
    cd "$source_root/pii-detector-service"
    export PYTHONPATH="$PWD"
    export PII_AUTOTUNE_ENABLED=false
    exec .venv/bin/python pii_detector/server.py --port "$grpc_port"
    ;;
  ui)
    cd "$source_root/pii-reporting-ui"
    export PIPELINE_API_PORT="$api_port"
    exec pnpm exec ng serve --host 127.0.0.1 --port "$ui_port" --proxy-config "$config_dir/proxy.conf.cjs"
    ;;
  *) echo "Unknown service: $service (expected db, api, detector or ui)" >&2; exit 2 ;;
esac
