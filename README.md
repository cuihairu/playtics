# Playtics

Lean game analytics stack: Gateway (Spring Boot) → Kafka (Avro+Registry) → Flink (enrich/session/retention/funnel) → ClickHouse → Superset.

Quick start
- End-to-end: `bash scripts/e2e.sh`
- Import Superset bundle: `bash scripts/superset-import.sh`

Docs
- Chinese: `docs/architecture.zh.md`, `docs/e2e.zh.md`, `docs/ops.zh.md`, `docs/deploy.k8s.zh.md`

Deployment
- Dockerfiles: `services/*/Dockerfile`
- Helm chart: `infra/helm/playtics`
