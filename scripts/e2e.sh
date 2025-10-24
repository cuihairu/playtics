#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

# Helpers
gradle_bin() {
  if [ -x ./gradlew ]; then echo ./gradlew; elif command -v gradle >/dev/null 2>&1; then echo gradle; else echo ""; fi
}
wait_http() {
  local url="$1"; local tries=${2:-30}; local interval=${3:-2}
  for ((i=0;i<tries;i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then return 0; fi
    sleep "$interval"
  done
  return 1
}

# 1) Start infra
echo "[1/6] Starting infra (Kafka, ClickHouse, Apicurio, OTel, Superset)"
(cd infra && docker compose up -d zookeeper kafka clickhouse apicurio otel-collector superset)

# 2) Start control-service & gateway
GBIN=$(gradle_bin)
if [ -z "$GBIN" ]; then echo "Gradle not found. Install Gradle or add gradlew wrapper."; exit 1; fi

echo "[2/6] Starting control-service"
$GBIN :services:control-service:bootRun &
CTRL_PID=$!
wait_http http://localhost:8085/ 60 2 || { echo "control-service not ready"; exit 1; }

echo "[3/6] Starting gateway-service"
$GBIN :services:gateway-service:bootRun &
GW_PID=$!
wait_http http://localhost:8080/actuator/health 60 2 || { echo "gateway-service not ready"; kill $CTRL_PID || true; exit 1; }

# 3) Create project and key via control-service
echo "[4/6] Create project and API key"
ADM=admin
curl -fsS -X POST 'http://localhost:8085/api/projects' -H 'content-type: application/json' -H "x-admin-token: $ADM" -d '{"id":"p1","name":"E2E"}' >/dev/null || true
KEY_JSON=$(curl -fsS -X POST 'http://localhost:8085/api/keys' -H 'content-type: application/json' -H "x-admin-token: $ADM" -d '{"projectId":"p1","name":"e2e"}')
API_KEY=$(echo "$KEY_JSON" | sed -n 's/.*"apiKey"\s*:\s*"\([^"]*\)".*/\1/p')
if [ -z "$API_KEY" ]; then echo "Failed to create api key: $KEY_JSON"; kill $GW_PID $CTRL_PID || true; exit 1; fi

echo "API_KEY=$API_KEY"

# 4) Send sample batch to gateway
TS=$(date +%s000)
NDJSON=$(cat <<EOL
{"event_id":"01JE2E0001","event_name":"level_start","project_id":"p1","device_id":"d1","ts_client":$TS,"props":{"level":1}}
{"event_id":"01JE2E0002","event_name":"level_complete","project_id":"p1","device_id":"d1","ts_client":$((TS+1000)),"props":{"level":1,"stars":3}}
EOL
)
RES=$(curl -fsS 'http://localhost:8080/v1/batch' -H "x-api-key: $API_KEY" -H 'content-type: application/x-ndjson' --data-binary "$NDJSON") || {
  echo "batch request failed"; echo "$RES"; kill $GW_PID $CTRL_PID || true; exit 1; }
echo "Gateway response: $RES"

# 5) Start Flink jobs if available
if command -v flink >/dev/null 2>&1; then
  echo "[5/6] Starting Flink jobs (enrich, sessions, retention, funnels)"
  CLICKHOUSE_URL=${CLICKHOUSE_URL:-jdbc:clickhouse://localhost:8123/default} \
  KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-localhost:9092} \
  REGISTRY_URL=${REGISTRY_URL:-http://localhost:8081/apis/registry/v2} \
  KAFKA_TOPIC=${KAFKA_TOPIC:-playtics.events_raw} \
  bash scripts/run_flink.sh || true
  echo "Wait 10s for sinks to flush"
  sleep 10
  # Verify ClickHouse events count
  CNT=$(curl -fsS "http://localhost:8123/?query=SELECT%20count()%20FROM%20events%20WHERE%20project_id%3D'p1'") || CNT=0
  echo "ClickHouse events count: $CNT"
  if [ "$CNT" -ge 2 ]; then echo "[PASS] end-to-end to ClickHouse"; else echo "[WARN] Flink not inserted or not available"; fi
else
  echo "[5/6] Flink CLI not found; skipping CH verification"
fi

echo "[6/6] Done. PIDs: control=$CTRL_PID gateway=$GW_PID (CTRL-C to stop)"
wait $CTRL_PID $GW_PID || true
