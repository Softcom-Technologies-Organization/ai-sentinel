#!/usr/bin/env bash

set -euo pipefail

readonly MODEL_REPO="perplexity-ai/pplx-pii-masking-GGUF"
readonly MODEL_FILE="pplx-pii-masking-backbone-f16.gguf"
readonly SERVER_URL="http://127.0.0.1:8080"
readonly DEFAULT_TEXT="Hi, I'm Daniel Whitfield, you can reach me at daniels@meridiancap.com or 415-555-0123."

for command_name in hf llama-server python3 curl; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing command: $command_name" >&2
    echo "Install llama.cpp with 'brew install llama.cpp' and Python dependencies with 'python3 -m pip install huggingface_hub torch safetensors transformers'." >&2
    exit 1
  fi
done

text="${*:-$DEFAULT_TEXT}"
model_dir="$(hf download "$MODEL_REPO")"
server_log="$(mktemp -t pplx-pii-masking.XXXXXX)"
server_pid=""

cleanup() {
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid"
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -f "$server_log"
}
trap cleanup EXIT INT TERM

llama-server \
  -m "$model_dir/$MODEL_FILE" \
  --host 127.0.0.1 \
  --port 8080 \
  --embeddings \
  --pooling none \
  -c 4096 \
  -b 4096 \
  -ub 4096 \
  >"$server_log" 2>&1 &
server_pid=$!

server_ready=false
for ((attempt = 0; attempt < 120; attempt++)); do
  if curl --fail --silent --max-time 1 "$SERVER_URL/health" >/dev/null; then
    server_ready=true
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "llama-server stopped before becoming ready:" >&2
    cat "$server_log" >&2
    exit 1
  fi
  sleep 1
done

if [[ "$server_ready" != true ]]; then
  echo "llama-server did not become ready within 120 seconds:" >&2
  cat "$server_log" >&2
  exit 1
fi

(
  cd "$model_dir"
  python3 example_client.py "$text"
)
