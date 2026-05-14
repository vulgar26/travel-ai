以下为 **6 条** 可投递表述；每条均可在仓库中对上代码或文档。

1. **RAG 与可解释性**：实现「查询改写 → 多路向量检索 → 上下文拼接 → SSE 流式生成」单链路；SSE 首包输出检索引用片段（id/来源/正文预览），并配合 `docs/eval.md` 做固定问题回归。证据：`TravelAgent.java`、`QueryRewriter.java`、`docs/eval.md`。

2. **向量存储与数据层**：基于 Spring AI `VectorStore` 抽象，用 JDBC + PostgreSQL **pgvector** 持久化向量与 `metadata`（含 `user_id`），检索时 SQL 过滤实现用户隔离；**Flyway** 管理建表。证据：`PgVectorStore.java`、`db/migration/V1__init_pgvector.sql`。

3. **安全与稳定性闭环**：**Spring Security + JWT** 保护业务接口；向量与 Redis ChatMemory 与登录用户绑定；**Bucket4j** 对聊天接口限流（429 + JSON）；LLM 与天气工具统一超时与降级（Reactor `timeout`、OkHttp）。证据：`SecurityConfig`、`JwtAuthFilter`、`RateLimitingFilter`、`TravelAgent`、`WeatherTool`。

4. **可观测与 SSE 工程化**：MDC `requestId` 贯穿；`TravelAgent` 输出 **`[perf]`** 分段耗时（改写/检索/首 token/流 wall）；`ServerSentEvent` 区分正文与 **comment 心跳**，`share()` 避免重复调用 LLM，断线 `doOnCancel` 可追踪。证据：`TravelAgent.java`、`docs/ARCHITECTURE.md` §3、§5.6。

5. **可部署与 CI**：根目录 **Docker Compose**（App + pgvector Postgres + Redis）、Actuator 健康探活；**Testcontainers** 集成测试 + **GitHub Actions** 跑 `mvn test`。证据：`docker-compose.yml`、`TravelAiApplicationIntegrationTest`、`.github/workflows`。

6. **最小前端与交付形态**：**Vite + React** 登录页与 SSE 消费（`fetch` + Bearer），开发代理 `/api`；根 `README` 与 `frontend/README` 说明联调方式。证据：`frontend/`、`README.md`。
