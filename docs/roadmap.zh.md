# Playtics 路线图（Roadmap）

版本规划（目标：v0.2.0）

一、实验平台（A/B）与漏斗（CEP）
- 实验（A/B）
  - 实体：Experiment、Variant、Traffic Split、Holdout、Targeting Rules（属性/国家/版本/平台）
  - SDK：标准化曝光事件 `expose(exp, variant)`（已有），补充 assignment hooks（按用户/设备一致性哈希）
  - 控制面：创建/发布/停用/分流/受众；导出配置到 CDN/JSON；上线灰度
  - 流处理：曝光→效果指标（转化/留存/收入）归因；Uplift 计算
  - ClickHouse：experiments、exposures、exp_metrics_by_day 聚合表
- 多步漏斗（CEP）
  - Flink CEP 作业：支持可配置 steps（最多 5）、窗口（如 24h）、乱序处理
  - 输出：daily funnel per project/step，支持分组（平台/国家/版本）

二、RBAC 与多租户
- 控制面迁移到 PostgreSQL（H2→Postgres）
- 模型：User/Role（admin/editor/viewer）、Project、Membership、API Key（作用域到 Project）
- 登录：OIDC（Keycloak/企业 IdP）
- UI：登录/注销、项目切换、基于角色的权限控制
- 网关：按 Project 限速与配额；按 API Key 作用域校验

三、数据治理与审计
- PII 策略增强：regex 规则、字段级审计（命中次数、采样值）
- Schema 治理：Schema Registry 兼容性策略（backward/forward/full）管控；Schema 演进记录
- 审计表：ingest_audit（项目、Key、错误类型、时间、样例）

四、Flink 与 ClickHouse 的一致性与回放
- 写入安全：优选 Kafka → CH Kafka 引擎 + 物化视图 或分区临时表 + 原子交换，减少自研 2PC 复杂性
- 回放与重算：按时间窗回放 offsets；临时表对比一致性 → 原子替换
- Checkpoint 策略：外部化存储（S3/HDFS），监控与报警

五、可观测与告警
- 应用级指标完善：HTTP 请求/错误、GC、线程、Kafka 生产/消费、Flink Backpressure
- Tracing：关键路径埋点（入口请求→Kafka 发→Flink→CH）；采样与追踪 ID 贯穿
- Grafana：导入 JVM/Spring/Flink 常用 Dashboard，提供预置 JSON

六、部署与运维
- Helm：完善 secrets 管理、TLS（Ingress/ServiceMesh）、节点亲和/污点容忍、PDB、HPA 配置样例
- 日志：结构化 JSON；集中收集（Loki/ELK）
- 安全：API Key 轮换、IP Allowlist/Blocklist、WAF 规则建议

里程碑与交付（建议）
- M1（2-3周）：实验平台 MVP（控制面 + SDK + 曝光入库 + 基本报表）、CEP 漏斗 PoC
- M2（2-3周）：RBAC/OIDC、多租户模型、Postgres 迁移；Grafana 应用面板完善
- M3（2-3周）：回放/重算流程、审计与治理增强；Helm/运维完善；压测报告与容量指南更新

风险与依赖
- CEP 复杂度与一致性：需谨慎处理乱序与窗口；拉更多样本验证
- OIDC 接入：依赖企业 IdP 与权限模型设计
- 数据治理与合规：PII 识别策略需与业务/法务确认

受理标准（验收）
- A/B：可创建/发布实验，SDK 正确曝光，CH 可查询 uplift 指标，Superset 有基础图表
- 漏斗：多步漏斗可配置，按日输出聚合，Superset 有漏斗图
- RBAC/OIDC：按不同角色权限正确；多租户项目隔离
- 回放/重算：给定时间窗可重算并对比一致性；操作手册完善
- 监控：Grafana 面板完备，告警规则样例
