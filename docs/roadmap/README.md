# Roadmap

本目录保存 Travel AI Planner 的长期演进方案。这里的文档只描述架构方向和阶段规划，不代表当前接口已经变更，也不要求立即修改 Java 业务代码或数据库 schema。

## 文档清单

| 文档 | 主题 |
| --- | --- |
| [finance-agent-roadmap.md](finance-agent-roadmap.md) | 从 Travel AI Planner 演进为 Finance AI Analyst / Financial Agent 的长期架构方案 |
| [async-agent-runtime-roadmap.md](async-agent-runtime-roadmap.md) | 面向高并发、长耗时、多阶段 Agent 任务的异步运行时演进方案 |
| [eval-harness-roadmap.md](eval-harness-roadmap.md) | Eval Target Adapter 与 Evaluation Harness 统一接口方案 |

## 共同原则

- 保留当前已落地的 RAG、SSE、tool governance、eval、guard/gate、Redis、PostgreSQL/pgvector 等工程基础设施。
- 先抽象领域边界，再替换业务语义，避免把项目退化成单纯 prompt demo。
- 长期演进必须保持可观测、可评测、可回放、可治理。
- 金融方向定位为研究分析和信息辅助，不做自动交易、收益承诺或个性化投资建议。

