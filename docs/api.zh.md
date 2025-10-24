# Playtics 采集 API（中文）

路径：`POST /v1/batch`

Headers
- `x-api-key`: 项目级 API Key（必需）
- `x-signature`: 可选，`HMAC-SHA256` 对请求体签名（推荐）
- `content-type`: `application/json`（数组）或 `application/x-ndjson`
- `content-encoding`: `gzip`（推荐）
限制
- 请求体（解压后）≤ 1MB（可配 `playtics.request.maxBytes`）
- 单事件序列化后 ≤ 64KB（可配 `playtics.event.maxBytes`）

请求体
- JSON 数组或 NDJSON（每行一个 JSON 事件）。
- 单次上限：≤ 500 条 或 ≤ 1MB。

事件字段（简版）
- 必填：`event_id(uuidv7)`, `event_name`, `project_id`, `device_id`, `ts_client`(epoch毫秒)
- 常用：`user_id?`, `session_id?`, `platform`, `app_version`, `country`, `props`
- 可选：`revenue_amount`, `revenue_currency`, `trace_id`, `span_id`

响应体
```json
{
  "accepted": ["01J..."],
  "rejected": [{"event_id":"01J...","reason":"invalid_schema"}],
  "next_hint_ms": 3000
}
```

错误响应（统一格式）
```json
{
  "code": "too_many_requests",
  "message": "rate limited",
  "request_id": "f6d1..."
}
```
说明：`request_id` 同时在响应头 `x-request-id` 返回，可用于排障定位。

幂等与去重
- `event_id` 应全局唯一。网关不做去重（重复上报仍返回 `accepted`）；去重由流式任务（Flink enrich/dedup）按 `event_id` + 状态 TTL 完成，保证下游聚合不重复。

校验与治理
- Schema 版本由控制面管理，网关按 JSON Schema 快速校验（长度、大小、类型）。
- PII 策略：配置白/黑名单键名；不合规字段丢弃或掩码；严重违规进入 DLQ。
- Props 白名单：`playtics.props.allowlist` 控制允许的自定义字段；多余字段被丢弃，嵌套层级最多 3 层，数组最多 50 项。
 - PII 细则：`playtics.pii.email/phone/ip` 可设为 `allow|mask|drop`；默认 email/phone 掩码、IP 做粗化（IPv4 /24, IPv6 /48）。
 - PII 阻断：`playtics.pii.denyKeys` 中的键名出现时事件将被拒绝（`pii_blocked`）并写入 DLQ。

签名规范（可选）
- `x-signature: t=TIMESTAMP, s=hex(hmacSha256(secret, t + '.' + body)))`
- 服务器校验时间窗（默认 5 分钟）与 HMAC 一致性。

错误码（示例）
- `invalid_api_key` `invalid_signature` `too_many_requests` `payload_too_large` `invalid_schema` `internal_error`
