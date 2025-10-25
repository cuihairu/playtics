# 网关性能调参建议（Playtics Gateway）

目标：在高吞吐（10k–50k evts/s）下保持稳定低延迟与低错误率。以下建议按 JVM、Reactor Netty、Kafka Producer、操作系统与部署层面给出。

JVM/GC
- 建议 JDK 21，默认 G1GC（或尝试 ZGC 在高内存机器上降低暂停）
- 容器内使用：`-XX:MaxRAMPercentage=75.0`（Dockerfile 已设）
- 观察 GC：Prometheus/Grafana 导入 JVM/GC Dashboard；P95 GC Pause < 50ms 为宜

Reactor Netty（服务端）
- EventLoop 线程数：默认≈CPU 核数 x 2；一般无需修改
- 响应压缩：已开启 `server.compression.enabled=true`（对较大错误/诊断响应有益）
- 大请求：限制 `playtics.request.maxBytes`（默认 1MB），避免单请求过大带来延迟尖峰
- JSON 解析：优先 NDJSON；客户端批量 50–100，减少系统调用与序列化开销

Kafka Producer（Avro）
- 调参：
  - `playtics.kafka.producer.lingerMs`：5–20ms（批量更大、吞吐更高，但延迟上升）
  - `playtics.kafka.producer.batchSize`：64–256KB（视事件大小而定）
  - 其他：`acks=all`（已设），`compression=zstd` 可在 Broker 允许时考虑（需同时在 Producer 上开启）
- 分区：确保 `events_raw` 分区数与消费并行度对齐，否则下游会形成热点

操作系统与部署
- 网卡：千兆以上；容器 cgroup 限制合理设置
- HPA：按 CPU 或自定义指标（QPS/429 率）扩缩容；values.yaml 已示例
- 亲和性：跨可用区/节点分散；拓扑分布约束（values.yaml）

压测实践
- 选择固定到达率模型（k6 constant-arrival-rate）更接近真实流量
- 组合维度：RATE（200/400/800 it/s） x BATCH（20/50/100）
- 观察 P95/P99、429 率、Kafka 发送速率/错误、Flink Lag（若联动下游）
- 通过 `scripts/perf-matrix.sh` 与 `scripts/perf-report.py` 采集与生成 CSV 报表

ClickHouse 查询侧（建议）
- 导入基础 Schema：`schema/sql/clickhouse/schema.sql`
- 导入实验 MVs：`schema/sql/clickhouse/schema_experiments_mv.sql`
- 导入视图：`schema/sql/clickhouse/queries_experiment.sql`
- 使用 `clickhouse-benchmark` 对 `v_exp_conversion_24h(_dim)`/`v_exp_conversion_7d(_dim)` 与对应基于 MV 的 Join 查询进行评估，视数据规模选择是否切换到基于 `exp_exposure_users` 与 `exp_first_level_complete` 的 Join

日级预聚合（推荐）
- 导入 `schema/sql/clickhouse/schema_experiments_daily_mv.sql`，将曝光与 24h/7d 转化按日/实验/variant 预聚合，显著降低大范围 Join 成本。
- 快视图优先：在 Superset 中使用 `*_fast` 或日级表对应的数据集/图表（MV-backed）支撑大数据量仪表盘。
