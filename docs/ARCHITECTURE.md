# ARCHITECTURE — 最简链路说明（更新至 Week 4 · 可解释 RAG）

本项目的核心是“一条可解释的 RAG 链路”：**改写 → 检索 → 拼上下文 → 流式生成**，并在此基础上补齐了最小上线安全与可靠性（鉴权 / 会话隔离 / 限流 / 超时 / Actuator 探活 / SSE 心跳与断线感知）。

## 1. 请求链路（文本流程图）

客户端请求（SSE，经鉴权与限流）  
→ `TravelController`：`GET /travel/chat/{conversationId}?query=...`  
→ `TravelAgent.chat(conversationId, userMessage)`  
→ `QueryRewriter.rewrite(userMessage)`（生成 3 条检索 query）  
→ `VectorStore.similaritySearch(...)`（对每条 query 检索 TopK，合并/去重/限制条数）  
→ 拼接 `promptWithContext`（将检索文本注入到最终 prompt）  
→ `ChatClient.prompt(promptWithContext)`（携带 `conversationId` 与当前用户作为 chat memory key）  
→ 以 `Flux<ServerSentEvent<String>>` 形式 SSE 流式返回（正文为 `data`，空闲时 `comment` 心跳）给客户端

## 2. 单链路约束（为什么要这样做）

- 每次请求仅保留 **一套** “检索 + 上下文注入”逻辑，避免重复检索导致的成本与延迟不可控。
- 检索到的文本必须进入最终 prompt（否则 RAG 只是“检索了但没用上”）。

## 3. 关键可观测点（最小版）

一次请求至少记录以下信息（不打印完整隐私内容）：

- 检索条数：`docs.size()`
- 最终 prompt 长度：`promptWithContext.length()`

## 4. 相关源码入口

- 对话 SSE 入口：`src/main/java/com/powernode/springmvc/controller/TravelController.java`
- RAG 链路实现：`src/main/java/com/powernode/springmvc/agent/TravelAgent.java`
- 查询改写：`src/main/java/com/powernode/springmvc/agent/QueryRewriter.java`
- 向量检索：`src/main/java/com/powernode/springmvc/config/PgVectorStore.java`（`metadata` JSON 持久化；`similaritySearch` 对 `user_id` 等式过滤走 SQL `metadata->>'user_id'`）
- 评测问题集：`docs/eval.md`

## 5. 安全与可靠性（Week 2 进展）

### 5.1 鉴权与会话隔离

- 所有业务接口（`/travel/**`、`/knowledge/**`）通过 `SecurityConfig` 受 Spring Security 保护。
- `POST /auth/login` 使用内存用户（如 `demo/demo123`）与 `JwtService` 签发 JWT，客户端在后续请求中通过 `Authorization: Bearer ...` 访问。
- `JwtAuthFilter` 在每次请求前解析 JWT，将当前用户写入 `SecurityContext`，`TravelAgent` 与 `KnowledgeServiceImpl` 从中读取用户名，用于：
  - 写入向量 metadata（`user_id` 字段）；
  - 读取向量时按 `user_id` 过滤，实现“谁上传谁检索”。

### 5.2 限流

- `RateLimitingFilter` 是一个全局 `OncePerRequestFilter`，在 `JwtAuthFilter` 之后执行：
  - 针对 `/travel/chat/**` 使用 Bucket4j + Caffeine 为每个用户/IP 创建独立 token bucket。
  - 默认策略：每用户/IP 每分钟 5 次请求（可配置），超额时立即返回 HTTP 429，Body 为统一的 JSON：
    - `{"code":"RATE_LIMITED","message":"请求过于频繁，请稍后再试"}`
  - 登录用户用 `user:{username}` 作为限流 key，匿名用户退化为按 IP 限流。

### 5.3 超时与降级

- **LLM 调用**（`TravelAgent.chat`）：
  - 在 **内容流** `Flux<String>` 上增加 `.timeout(Duration.ofSeconds(20))`，整体超时后通过 `.onErrorResume(...)` 记录错误并返回一条系统提示，再封装为 `ServerSentEvent` 与心跳流合并输出；避免 SSE 永久挂起。
  - `doFinally` 清理 MDC 中的 `requestId`，保证日志上下文不串号。

- **天气工具**（`WeatherTool`）：
  - 通过 `application-local.yml` 配置 `weather.timeout-ms` 与 `weather.api-url`，初始化 OkHttpClient 的 connect / read / write / call timeout。
  - 若未配置 `api-url`，走本地模拟天气文案；若配置了但调用超时或异常，则记录 warn/error 日志并返回“查询超时/暂时不可用”的降级文本。

### 5.4 日志噪音治理

- 由于 SSE 使用异步 dispatch，Tomcat 在 async 收尾阶段会再次触发 Spring Security filter chain。
- 通过在 `SecurityConfig` 中增加 `securityMatcher(request -> request.getDispatcherType() == REQUEST)`，仅对真正的 HTTP 请求（`DispatcherType.REQUEST`）应用 SecurityFilterChain，避免在 SSE 收尾阶段出现多余的 `AccessDeniedException` 与 “response already committed” 日志。

### 5.5 运行探活（Actuator）

- 依赖：`spring-boot-starter-actuator`。
- `application.yml` 中 `management.endpoints.web.exposure.include` 仅暴露 `health`、`info`；`management.endpoint.health.show-details` / `show-components` 为 `when_authorized`，匿名调用只得到聚合状态（如 `{"status":"UP"}`），带 JWT 时可看 DB、Redis 等组件详情。
- 配置了 `livenessstate` / `readinessstate` 用于 `health` 语义判断，并开启了 `management.endpoint.health.probes.enabled`；因此子路径 `/actuator/health/liveness` 与 `/actuator/health/readiness` 返回 `200`（匿名可访问）。
- `SecurityConfig` 对 `/actuator/health/**` 与 `/actuator/info` 使用 `permitAll()`，负载均衡与 Docker/K8s 健康检查无需携带 Token。

更细的 Actuator 概念说明见：`docs/ACTUATOR_HEALTH_BASICS.md`。

### 5.6 SSE 工程化（心跳与断线）

- **返回模型**：`TravelController` / `TravelAgent.chat(...)` 使用 **`Flux<ServerSentEvent<String>>`**，而不是裸 `Flux<String>`，以便区分 **业务 `data`** 与 **保活 `comment`**（符合 SSE 文本格式：`data:` 与以 `:` 开头的注释行）。
- **心跳**：`Flux.interval` 按 `app.sse.heartbeat-seconds`（默认 15s）发送 `ServerSentEvent.comment("keepalive")`；正文流 `contentFlux` 经 **`.share()`** 与心跳合并（`Flux.merge`），避免重复订阅导致 **重复调用 LLM**。正文结束后通过 **`takeUntilOther(contentFlux.then())`** 停止心跳。
- **断线**：客户端关闭连接时，下游取消订阅会触发 **`doOnCancel`** 日志（`SSE 订阅已取消…`）。Tomcat 在收尾写 Socket 时偶发 **`IOException`（连接被对端中止）** 属于常见现象，与业务失败不同，可在运维上降级或忽略。
- **响应头**：`TravelController` 设置 `Cache-Control: no-cache, no-store` 与 `X-Accel-Buffering: no`，减轻缓存与反向代理缓冲对流式响应的影响。

## 6. Docker Compose（Week 3）

- 仓库根目录 `docker-compose.yml` 定义 **`app` + `postgres`（`pgvector/pgvector:pg16`）+ `redis`**，同一默认网络内通过服务名互访。
- **库表结构**：由 **Flyway** 在应用启动时执行 `classpath:db/migration`（当前 **`V1__init_pgvector.sql`**：创建 `vector` 扩展与 **`vector_store`**，列与 `PgVectorStore` 一致）。
- 环境变量通过 **`.env`**（由 `.env.example` 复制）注入 **`SPRING_DATASOURCE_*`、`SPRING_DATA_REDIS_*`、`APP_JWT_SECRET`、`SPRING_AI_DASHSCOPE_API_KEY`** 等，避免把密钥写进镜像。
- 宿主机映射 **`8081`**（应用）、**`5433→5432`**（Postgres）、**`6380→6379`**（Redis），降低与本机已装数据库的端口冲突概率。
