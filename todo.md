# TODO（短期执行）

面向 v0.2.0 Phase 1 的落地事项与代码内 TODO 梳理。

参考：docs/roadmap.zh.md

## P0 立即项
- [x] 后端：禁止删除最后一位 SUPER_ADMIN（UserService.deleteUser 校验）
  - 变更：UserRepo 增加 `countByGlobalRoleAndDeletedAtIsNull(GlobalRole)`；UserService 删除逻辑校验计数
  - 验收：当仅剩 1 位 SUPER_ADMIN 时，删除应抛错；存在 >=2 位时可删除
- [x] Web SDK：在 `assignAllAndExpose` 中应用 targeting（platform/appVersion）
  - 说明：与 `assignAllWithTargeting` 对齐，完善基础定向逻辑；需要 `country` 时建议使用后者
  - 验收：当 `config.targeting` 不匹配时不分配也不曝光

## P0 Phase 1.1 多层级租户模型
- [ ] DDL/Migration 校验：组织/游戏/环境/用户模型与索引齐备（含软删字段）
- [ ] API 草案与实现：组织/游戏/环境增删改查；约束与校验（跨租户隔离）
- [ ] API Key 管理：按 org/game/env 维度的限流/PII/配额策略
- [ ] 审计与监控：关键操作审计日志；关键指标埋点

## P0 Phase 1.2 企业级权限（RBAC）
- [ ] 权限模型落地：Super Admin/Org Admin/Game Admin/Analyst/Viewer
- [ ] 鉴权：JWT/Cookie 会话；方法级权限注解与自定义校验器
- [ ] 角色管理：用户角色分配/移除接口，范围（GLOBAL/ORG/GAME/ENV）一致性校验

## P0 Phase 1.3 管理控制台（Web UI）
- [ ] 页面：Dashboard/组织/游戏/环境/API Key/用户/系统设置（Ant Design）
- [ ] 接口对接：与 Control Service API 对齐；状态展现与操作反馈

## P1 计费与配额（Phase 1.4）
- [ ] 计费维度：事件量/API 调用/存储/保留期
- [ ] 配额执行：Gateway/Control Service 统一扣配额与告警

## 代码清单（跟踪）
- [ ] services/control-service：补充单元测试（UserService 删除最后 SUPER_ADMIN）
- [ ] sdks/web：增加针对 targeting 的用例（构建后脚本或轻量测试）

