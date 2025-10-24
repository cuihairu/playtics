# 可观测性（Metrics/Traces/Logs）

本地监控栈
- OTel Collector：接收 OTLP（:4317/:4318），Prometheus 导出（:9464）
- Prometheus：抓取 OTel Collector 暴露指标（otelcol_*）
- Grafana：已预置 Prom 数据源与 `Playtics Overview` 面板（OTLP 接收量/uptime）
- 启动：`cd infra && docker compose up -d otel-collector prometheus grafana`

采集应用指标（建议）
- Spring Boot（网关/控制面）：
  - 方式一：Java Agent（自动）：下载 OTel Java Agent，启动时添加 `-javaagent:/path/opentelemetry-javaagent.jar` 并配置环境：
    ```
    OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
    OTEL_METRICS_EXPORTER=otlp
    OTEL_TRACES_EXPORTER=otlp
    OTEL_LOGS_EXPORTER=none
    OTEL_SERVICE_NAME=playtics-gateway
    ```
  - 方式二：Micrometer OTel Registry（代码集成）：引入 `micrometer-registry-otlp` 并配置 MeterRegistry 指向 Collector
- Flink 作业：可启用 OTel Metrics Reporter，将指标上报至 Collector

Grafana 面板
- 已预置的 `Playtics Overview` 显示 OTel Collector 接收的 Spans/Metrics/Logs 速率与 uptime
- 如需应用级面板（HTTP 请求、JVM、GC 等），请在应用开启 OTel 上报后导入 JVM/Netty/Spring 相关 Dashboard（Grafana 官方/社区模板）
