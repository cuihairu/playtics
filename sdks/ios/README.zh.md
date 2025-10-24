# Playtics iOS SDK（Swift Package）

集成
- 在 Xcode 中：File -> Add Packages -> 选择本地文件夹 `sdks/ios`（或将其作为 SPM 依赖）

用法
```swift
import Playtics

var opts = PlayticsOptions(apiKey: "pk_test_example",
                           endpoint: URL(string: "http://localhost:8080")!,
                           projectId: "p1")
opts.debug = true
Playtics.shared.initSDK(opts)

Playtics.shared.setUserId("u1")
_ = Playtics.shared.track("level_start", props: ["level": 3])
_ = Playtics.shared.expose("paywall", variant: "B")
_ = Playtics.shared.revenue(amount: 9.99, currency: "USD", props: ["sku": "noads"]) 
Playtics.shared.flush()
```

特性
- 批量发送（默认 5s 或 50 条），NDJSON；可选 gzip（Compression）
- 离线容错：队列持久化到 Caches 目录（NDJSON 文件）
- 会话：默认 30 分钟闲置切换 session_id
- 可选 HMAC（CryptoKit），不建议在客户端放 secret

注意
- 需要 iOS 13+（CryptoKit/Compression）
- 生产项目建议将队列持久化到 Application Support 并配合后台传输策略
- 网关默认接受 `ts_client` 为毫秒整数或 ISO 字符串（已放宽 JSON Schema）
