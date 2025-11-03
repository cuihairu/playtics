# Pit（游戏数据分析栈）

- 入口：Spring Boot 网关（限流/治理/PII/Schema）→ Kafka（Avro+Registry）→ Flink（富化/会话/留存/漏斗）→ ClickHouse → Superset
- SDK：Web/Android/Unity/iOS（批量/NDJSON/gzip/离线/会话）

快速体验
```bash
bash scripts/e2e.sh
bash scripts/superset-import.sh   # 导入仪表盘（可选）
```

文档
- 架构：docs/architecture.zh.md
- E2E：docs/e2e.zh.md
- 运维：docs/ops.zh.md
- 部署：docs/deploy.k8s.zh.md
- 发布：docs/release.zh.md
 - 实验：docs/experiments.zh.md
 - 路线图：docs/roadmap.zh.md
 - 贡献指南：docs/contributing.zh.md
