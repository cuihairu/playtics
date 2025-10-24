# SDK 设计规范（跨端一致）

初始化
- `apiKey`, `endpoint`, `projectId`，自动生成 `deviceId`（可覆盖）。
- 批量发送：默认 5s 或 50 条触发；NDJSON+gzip；指数退避重试。
- 离线容错：断网持久化（Web IndexedDB/LocalStorage；Android Room；iOS 文件）。

事件接口
- `track(eventName, props?)` 返回 `event_id`
- `setUserId(userId|null)`、`setUserProps(props)`
- `expose(exp, variant)` 标准化实验曝光事件
- `revenue(amount, currency, props?)`
- `flush()` 手动刷新

会话
- 前后台切换与闲置间隔（默认 30 分钟）自动生成 `session_id`。

治理
- 本地 PII 白名单与掩码；`props` 深度≤3，整体≤64KB。
- 内部诊断指标与丢弃原因统计，debug 开关。

安全
- HMAC 可选，`x-signature` 按时间 + 体组成签名。

示例（Web TS）
```ts
Playtics.init({ apiKey, endpoint, projectId });
Playtics.track('level_start', { level: 3 });
Playtics.setUserId('u123');
Playtics.expose('paywall', 'B');
Playtics.revenue(9.99, 'USD', { sku: 'noads' });
await Playtics.flush();
```
