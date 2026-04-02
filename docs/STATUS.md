# 项目现状清单（Day 1）

更新时间：2026-04-02  
仓库：`travel-ai-planner`

## 一句话定位（当前版）

**面向开发者演示的「智能出行规划」后端原型**：基于 Spring Boot 3 + Spring AI Alibaba（通义千问）实现 **SSE 流式对话**，支持 **上传 txt 文档入库 → RAG 检索增强 → 输出行程建议**，并集成工具调用（天气）。

## 目标用户 & 不做什么（能力边界）

- **目标用户**：想要“可演示、可讲清楚工程链路”的后端/RAG 项目作品集（面试/简历展示）。
- **不做什么（当前明确不覆盖）**：
  - 不承诺生产级安全/鉴权（目前实现的是最小上线安全包：JWT 登录 + 基于用户的会话隔离 + 最小限流与超时保护，而非复杂权限体系）。
  - 不提供前端 UI（当前以接口 + SSE 为主）。
  - 不做复杂产品功能（支付、订单、地图等）。

## 已实现能力（基线）

- **SSE 流式输出**：`GET /travel/chat/{conversationId}` 返回 `text/event-stream`，串联查询改写 → RAG 检索 → 上下文拼接 → LLM 生成。
- **知识上传入库**：`POST /knowledge/upload` 上传文件后由服务端进行校验（大小/类型/空文件）、切分并写入向量库，携带 `user_id` 等 metadata 支持按用户隔离检索。
- **最小安全与运维包**（Week 2 已闭环）：
  - 基于 Spring Security 6 + JWT 的登录与鉴权：`POST /auth/login` 返回 JWT，`/travel/**`、`/knowledge/**` 需 `Authorization: Bearer ...`。
  - 会话与用户绑定：通过 `SecurityContext` 获取当前用户，将向量检索与 Redis ChatMemory 的 key 绑定到登录用户名，避免多用户越权。
  - 限流与超时：`RateLimitingFilter` 使用 Bucket4j + Caffeine 对 `/travel/chat` 做按用户/IP 的每分钟限流（超额返回 429 + 统一 JSON 错误体），`TravelAgent.chat` 与 `WeatherTool` 分别对 LLM 与天气接口增加超时与友好降级提示。
  - **运行探活**：Spring Boot Actuator 暴露 `health`、`info`；匿名可访问 `GET /actuator/health` 与 `GET /actuator/info`。当前 `/actuator/health/liveness` 与 `/actuator/health/readiness` 返回 `200`（已开启 probes 端点）；详情仅在已认证请求下展开。
  - **SSE 工程化**：`TravelAgent` 以 `Flux<ServerSentEvent<String>>` 输出流式结果；空闲时发送 **SSE comment 心跳**（`app.sse.heartbeat-seconds`）；客户端断开时 `doOnCancel` 打日志，便于确认上游取消订阅（Tomcat 偶发 `IOException` 为断线常见现象，可忽略）。
- **技术栈（以代码/配置为准）**：
  - Spring Boot 3
  - Spring AI Alibaba DashScope（通义千问 `qwen3.5-plus`）
  - PostgreSQL + pgvector（向量持久化/检索）
  - Redis（对话记忆/缓存相关能力）
  - Spring Security 6 + JWT（鉴权与用户隔离）
  - Bucket4j + Caffeine（限流）

## 当前对外接口（可演示路径）

1) **上传知识**（建议先准备一个 `.txt`）

- `POST /knowledge/upload`
- 表单字段：`file`

2) **发起对话（SSE）**

- `GET /travel/chat/{conversationId}?query=...`
- `conversationId` 当前由客户端传入（后续会改成服务端生成并做所有权校验）

## 关键现状风险（必须尽快修复）

- **密钥泄露风险（P0）**（已通过 Day 2 缓解）：`application.yml` 中的 DashScope / 天气 key 已改为环境变量占位，真实值通过 `application-local.yml` / 环境变量注入，且 `.gitignore` 避免本地配置被提交；已完成已泄露 key 的轮换。
- **接口错误体不规范（P1）**：上传接口异常直接返回字符串 `"上传失败：..."`，不利于前后端/脚本稳定解析（后续会做统一错误体）。
- **多租户/越权风险（P0~P1）**（处理中）：已在向量检索与 ChatMemory 中按 `user_id` 做隔离，后续还需在 `conversationId` 管理层面增加所有权校验与生成策略。

## 下一步（对应计划 Week 3 起）

- **可部署与可复现**：Dockerfile / docker-compose、环境变量化 DB/Redis、（可选）CI 跑测试。
- **产品侧仍待加强**：`conversationId` 服务端生成与所有权校验、统一错误体与上传接口结构化返回等（见上文「关键现状风险」）。

## Week 2 里程碑概览（截至 Day 6 · 周总结）

- **鉴权闭环打通**：`/auth/login` + JWT + Spring Security filter chain，未登录访问业务接口会返回 401，登录后可正常建立 SSE。
- **RAG 与用户身份打通**：向量检索与知识上传时都从 `SecurityContext` 读取当前用户名，写入/过滤 `user_id` metadata，实现“谁上传谁检索”的最小多租户隔离。
- **限流与稳定性**：
  - `/travel/chat/**` 通过 `RateLimitingFilter` 做每用户/IP 每分钟限流，超额统一返回 429 + JSON 错误体（不会 500）。
  - LLM 调用通过 Reactor `.timeout + onErrorResume` 设置整体超时时间，超时或异常时给出清晰系统提示，避免 SSE 长时间挂起。
  - 天气工具 `WeatherTool` 使用 OkHttp 设置连接/读取超时，对超时与调用异常记录日志并返回人类可读的降级文案。
- **日志与噪音治理**：通过在 `SecurityConfig` 中限制 SecurityFilterChain 仅作用于 `DispatcherType.REQUEST`，消除了 SSE 异步收尾阶段多余的 `AccessDenied` + “response already committed” 噪音日志，使日志更聚焦于真实异常。
- **Actuator 健康检查**：已接入 `spring-boot-starter-actuator` 与 `management.*` 配置；`mvn compile` 通过。依赖启动后可用 `curl -s http://localhost:8081/actuator/health` 验证（Postgres/Redis 可达时组件才会全部 UP）。
- **SSE 心跳与断线**：`ServerSentEvent` 区分 `data` 与 `comment` 心跳；Apifox 等客户端断开后可看到 `SSE 订阅已取消` 日志；`docs/ARCHITECTURE.md` §5.6 已记录设计与已知噪音。
- **文档与简历**：`docs/ARCHITECTURE.md` 已同步 Week2 全量；`docs/RESUME_BULLETS.md` 增补「安全与上线」三条可投递表述。

## 验收记录（工程习惯）

### Day 4 — 无副作用验证（幂等/不污染）

目的：验证“默认不开初始化”时启动不灌示例数据；上传行为可预期；重启不重复写入。

- **启动参数**：`--app.knowledge.init.enabled=false`（覆盖本地 profile）
- **vector_store 行数**
  - N0（启动后）：46（无“知识库初始化完成”日志）
  - N1（上传 1 次 `test.txt` 后）：47
  - N2（上传同一文件第 2 次后）：48
  - 重启后再查询：48（无额外写入）

