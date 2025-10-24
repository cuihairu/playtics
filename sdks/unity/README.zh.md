# Playtics Unity SDK（C#）

用法
1) 将 `sdks/unity/Runtime/Playtics.cs` 拷贝到你的 Unity 工程（建议 `Assets/Playtics/Runtime/`）
2) 启动时初始化：
```csharp
using Playtics;

void Start() {
  Playtics.Playtics.Init(new Options {
    apiKey = "pk_test_example",
    endpoint = "http://localhost:8080",
    projectId = "p1",
    debug = true,
  });
}
```
3) 上报事件：
```csharp
Playtics.Playtics.Track("level_start", new Dictionary<string, object>{{"level",1}});
Playtics.Playtics.SetUserId("u1");
Playtics.Playtics.Expose("paywall","B");
Playtics.Playtics.Revenue(9.99, "USD", new Dictionary<string, object>{{"sku","noads"}});
Playtics.Playtics.Flush();
```

特性
- 批量发送（默认 5s 或 50 条）、`application/x-ndjson`
- 可选 gzip（`System.IO.Compression.GZipStream`），失败自动回退
- 离线容错：队列持久化到 `Application.persistentDataPath`（ndjson 文件）
- 会话管理：默认 30 分钟闲置切换 `session_id`
- 轻量 JSON 序列化（手写），限制嵌套≤3、数组≤50
- 可选 HMAC（demo）：`x-signature: t=ts, s=hmacSha256(secret, t + '.' + body)`，仅供测试，不建议在客户端放 secret

注意
- 在移动端建议将 `endpoint` 指向你可访问的网关地址（Android 模拟器访问宿主机可用 `http://10.0.2.2:8080`）
- 若你的项目已引入 JSON 库，可自行替换 `ToJson` 实现
- 队列重启后不会恢复为对象（仅简单清空文件），如需严格离线可靠可扩展反序列化逻辑
