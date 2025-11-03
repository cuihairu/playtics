#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

ENDPOINT=${ENDPOINT:-http://localhost:8080}
API_KEY=${API_KEY:-pk_test_example}
PROJECT_ID=${PROJECT_ID:-p1}
DURATION=${DURATION:-30s}
RATES=${RATES:-"200 400 800"}
BATCHES=${BATCHES:-"20 50 100"}
OUT_DIR=${OUT_DIR:-var/perf}
mkdir -p "$OUT_DIR"

echo "Endpoint=$ENDPOINT API_KEY=$API_KEY PROJECT_ID=$PROJECT_ID Duration=$DURATION" >&2
for r in $RATES; do
  for b in $BATCHES; do
    FN="$OUT_DIR/summary_r${r}_b${b}.json"
    echo "Running RATE=$r BATCH=$b ..." >&2
    docker run --rm -i \
      -e ENDPOINT="$ENDPOINT" -e API_KEY="$API_KEY" -e PROJECT_ID="$PROJECT_ID" \
      -e RATE="$r" -e DURATION="$DURATION" -e BATCH="$b" \
      -v "$PWD":/work -w /work \
      grafana/k6 run --summary-export "$FN" - < scripts/loadtest/k6-pit.js >/dev/null || true
    echo "Saved $FN" >&2
  done
done

echo "Done. Use scripts/perf-report.py $OUT_DIR to generate CSV."
