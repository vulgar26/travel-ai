# 文档导航

本目录只保留面试官和新接手开发者需要直接阅读的文档。阶段记录、历史计划和周报模板已归档到 [archive/](archive/)。

## 推荐阅读顺序

| 文档 | 作用 |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 核心请求链路、Agent 阶段、SSE、鉴权、限流、超时与 Compose 说明 |
| [LOCAL_DEV.md](LOCAL_DEV.md) | Windows + Docker Desktop + IDEA 本地启动与常见排错 |
| [demo.md](demo.md) | 手动演示流程和 curl 示例 |
| [FRONTEND_DEMO.md](FRONTEND_DEMO.md) | 前端展示页能力、接口清单和手动验收步骤 |
| [eval.md](eval.md) | 评测接口、RAG/工具/安全样例和回归验证入口 |
| [eval/SOURCES_EVAL_VS_SSE.md](eval/SOURCES_EVAL_VS_SSE.md) | eval `sources[]` 与 SSE 引用片段的证据口径说明 |
| [TOOL_GOVERNANCE_SPEC.md](TOOL_GOVERNANCE_SPEC.md) | 工具调用归因、超时、限流、熔断和观测字段约定 |
| [ACTUATOR_HEALTH_BASICS.md](ACTUATOR_HEALTH_BASICS.md) | Actuator 健康检查和探活基础说明 |

## 归档内容

[archive/](archive/) 中保留开发过程材料，默认不作为公开首页阅读入口：

- 阶段总结、Day/P0/P1 记录和周报模板。
- 升级计划、产品路线图和历史实现对照。
- 简历准备稿和个人开发计划。
- eval harness 的历史 gap、阈值、远程回归和断点恢复说明。

这些文件用于追溯，不代表当前 README 的主叙事；当前能力以根目录 [README.md](../README.md)、源码和保留文档为准。
