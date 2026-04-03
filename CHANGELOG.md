# Changelog

## v0.1-mvp — 2026-04-03

首个可对外演示的 MVP，侧重「一条 RAG 链路 + 最小上线包 + 可复现交付」。

- **RAG**：查询改写 → 多路 pgvector 检索 → 上下文增强 → SSE 流式输出；首包输出检索引用片段（可解释性）。
- **安全与运行**：JWT、用户级向量隔离、聊天限流、LLM/工具超时降级；Actuator 健康探活。
- **交付**：根目录 Docker Compose（App + Postgres + Redis + Flyway）；Testcontainers 集成测试；GitHub Actions `mvn test`。
- **产品感**：`frontend/` 最小 Vite + React（登录 + SSE）；`docs/eval.md` 固定问题回归。
- **性能粗测**：`TravelAgent` INFO 日志前缀 `[perf]`（改写 / 检索 / 首 token / 流 wall），见 `docs/ARCHITECTURE.md` §3。
