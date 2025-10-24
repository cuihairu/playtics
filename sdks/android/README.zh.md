# Playtics Android SDK（Kotlin）

特性
- 批量发送：默认 5s 或 50 条；`application/x-ndjson`；自动 `gzip`
- 离线容错：失败回退本地队列（SharedPreferences 持久化）
- 会话管理：默认 30 分钟闲置切新 session
- 可选 HMAC：`x-signature: t=ts, s=hmacSha256(secret, t + '.' + body)`（生产不建议在客户端放 secret）

快速开始
```kotlin
val pt = Playtics(
  context,
  Playtics.Options(
    apiKey = "pk_test_example",
    endpoint = "http://10.0.2.2:8080", // 模拟器访问本机网关
    projectId = "p1",
    debug = true
  )
)
pt.track("level_start", mapOf("level" to 1))
pt.setUserId("u1")
pt.expose("paywall", "B")
pt.revenue(9.99, "USD", mapOf("sku" to "noads"))
pt.flush()
```

集成
- 该目录为独立 Android Library 项目：`sdks/android`
- 用 Android Studio 打开 `sdks/android` 作为工程或将模块导入到你的 App 中
- 依赖：Kotlin 1.9.24、Android Gradle Plugin 8.5.1、OkHttp 4.12.0

注意
- HMAC 仅用于服务端/可信环境；客户端放置 secret 存在泄漏风险。
- 队列持久化为 NDJSON 字符串；为简化演示，重启后的反序列化可按需补充（当前只保留原始字符串避免崩溃）。
- 可按需接入 WorkManager/Connectivity 监听以优化离线重试。
