# 实验平台（A/B）MVP

目标：提供可配置的实验（variants/权重/目标规则），SDK 侧一致性分流与曝光事件，ClickHouse 侧快速聚合曝光/转化指标。

数据模型（控制面）
- Experiment: { id, projectId, name, status(draft|running|paused), salt, config }
- config 示例：
```json
{
  "variants": [ {"name":"A","weight":50}, {"name":"B","weight":50} ],
  "targeting": { "platform":["android","ios"], "appVersionMin":"1.0.0", "countries":["US","CN"] },
  "metrics": { "primary":"level_complete", "secondary":["revenue"] }
}
```

控制面 API（简要）
- POST /api/experiments {id,projectId,name,status,salt,config}
- GET /api/experiments?projectId=&status=
- POST /api/experiments/{id}/publish | /pause | DELETE /api/experiments/{id}
- Public 配置：GET /api/config/{projectId} → [{id,salt,config}]

SDK 分流与曝光（伪代码，TypeScript）
```ts
function assign(exp: {id:string,salt:string,variants:{name:string,weight:number}[]}, key: string): string {
  // key: userId or deviceId; use stable hashing
  const sum = exp.variants.reduce((a,v)=>a+v.weight,0);
  const h = murmur3(exp.id + ':' + (exp.salt||'') + ':' + key) % sum;
  let acc = 0; for (const v of exp.variants) { acc += v.weight; if (h < acc) return v.name; }
  return exp.variants[0].name;
}
// 使用：从 /api/config/{projectId} 拉取实验；过滤 targeting；按 userId/设备分配 variant；上报曝光
playtics.expose(exp.id, variant);
```

事件规范
- 曝光：`experiment_exposure`，props: { exp: <id>, variant: <name> }
- 转化事件：按业务上报，如 `level_complete` / `purchase`

ClickHouse 聚合（示例）
- 曝光：从 events 过滤 `experiment_exposure`，聚合到按日/variant
- 转化：按曝光用户集合在窗口内统计转化事件（SQL 在 queries_experiment.sql）

仪表（Superset）
- 每日曝光量（按 variant）
- 转化率（按 variant）
- Uplift（B vs A）
