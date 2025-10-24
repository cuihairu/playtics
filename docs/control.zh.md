# 控制面（最小版）

目的：集中管理项目、API Key 与策略（限速、Props 白名单），供网关动态拉取并缓存。

接口（默认端口 8085）
- 创建/更新项目
  - POST /api/projects
  - body: {"id":"p1","name":"Demo"}
- 列出项目
  - GET /api/projects
- 生成 API Key
  - POST /api/keys
  - body: {"projectId":"p1","name":"web"}
  - 返回: { apiKey, secret, projectId, name }
- 查询 Key 详情
  - GET /api/keys/{apiKey}
  - 返回: { apiKey, secret, projectId, rpm, ipRpm, propsAllowlist }
- 更新 Key 策略
  - PUT /api/keys/{apiKey}/policy
  - body 支持：
    - 限速：`rpm`, `ipRpm`
    - 允许字段：`propsAllowlist`
    - PII：`piiEmail`(`allow|mask|drop`), `piiPhone`(`allow|mask|drop`), `piiIp`(`allow|coarse|drop`), `denyKeys`, `maskKeys`
  - 示例：
    ```json
    {
      "rpm": 800,
      "ipRpm": 400,
      "propsAllowlist": ["level","stars","amount","currency"],
      "piiEmail": "mask",
      "piiPhone": "drop",
      "piiIp": "coarse",
      "denyKeys": ["password","credit_card"],
      "maskKeys": ["email","mobile"]
    }
    ```

网关集成
- application.yaml 配置 `playtics.control.url: http://localhost:8085`
 - 网关在鉴权与限流/过滤阶段动态拉取策略，60 秒缓存（每个 API Key 独立缓存）
 - 覆盖范围：`rpm`/`ipRpm`、`propsAllowlist`、`piiEmail`/`piiPhone`/`piiIp`、`denyKeys`、`maskKeys`
- 支持覆盖：
  - 限速：每 Key/每 IP（rpm/ipRpm）
  - Props 白名单：覆盖默认 allowlist

运行
- 启动控制面：
  - `./gradlew :services:control-service:bootRun`
- 测试：
```bash
curl -sS -X POST 'http://localhost:8085/api/projects' -H 'content-type: application/json' -d '{"id":"p1","name":"Demo"}'
curl -sS -X POST 'http://localhost:8085/api/keys' -H 'content-type: application/json' -d '{"projectId":"p1","name":"web"}'
```
