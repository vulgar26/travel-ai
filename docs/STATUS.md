# 项目现状（以代码为准）

**更新时间**：2026-04-21  
**仓库**：`travel-ai-planner`  
**实现真源**：`src/main/java`、`src/main/resources/application.yml`  
**与 Vagent 计划对照**：[`docs/IMPLEMENTATION_MATRIX.md`](IMPLEMENTATION_MATRIX.md)（逐项对应 `travel-ai-upgrade.md` 类目标，含「已做 / 未做 / 偏差」）

---

## 一句话

Spring Boot 3 + Spring AI（DashScope）的 **出行规划演示后端**：**JWT** 保护业务接口；**SSE** 对话走 **固定线性阶段**（计划 → 检索 → 工具 → 门控 → 流式写作）；**pgvector RAG** + Redis 会话记忆；**评测专用** `POST /api/v1/eval/chat`（网关密钥 + membership 头 + 非流式契约）。

---

## 已实现（摘要）

| 域 | 说明 |
|----|------|
| 对话 | `POST /travel/conversations` 签发并登记 `conversationId`；**推荐** `POST /travel/chat/{id}` + JSON `{"query":"…"}`（SSE；`app.conversation.max-query-chars`）；`GET …?query=` **兼容**（响应 `Deprecation: true`）；路径校验；可选 `app.conversation.require-registration`；`引用首包` + `data` 流 + `comment` 心跳；`[perf]` 日志 |
| 主线编排 | `TravelAgent`：`PLAN → … → WRITE` 固定顺序；`RETRIEVE`/`TOOL`/`GUARD` 按 plan `steps` 物理跳过（`PlanPhysicalStagePolicy`）；`RetrieveEmptyHitGate`；计划 JSON 可经 LLM 产出（`app.agent.plan-stage.enabled`） |
| RAG | `QueryRewriter`（失败兜底 + 行长限制）、合并去重检索、`user_id` 过滤 |
| 工具 | `WeatherTool`；`ToolExecutor` / 熔断 / 限流 / 可观测；HTTP 超时见 `app.agent.tool-timeout` |
| Agent 配置 | `app.agent.total-timeout`、`llm-stream-timeout`、`max-steps`、`tool-timeout`（见 `application.yml`） |
| 安全 | Spring Security：`/api/v1/eval/**` 需认证 + **`X-Eval-Gateway-Key`**；其余业务需 JWT；`JwtSecretStartupValidator` 在 docker/prod 等 profile 强校验密钥 |
| 限流 | Bucket4j：`app.rate-limit.chat.*`、`app.rate-limit.login.*`；超额 **429** JSON 与鉴权同形（`error`+`message`，`JsonApiErrorSupport`） |
| 长期画像 | Postgres `user_profile`；`GET/PUT/PATCH/DELETE /travel/profile`（删除可选 `clearChatMemory` 清 Redis 会话）；`app.memory.long-term` 控制是否注入主线 prompt；**从对话抽取**（`ProfileExtractionService` + `POST/GET/POST/DELETE …/extract-suggestion|pending-extraction|confirm-extraction`）；`app.memory.auto-extract.after-chat` 可选在 SSE 完成后异步写待确认或直接落库；Redis 会话仍为短期记忆（TTL 见 `RedisChatMemory`） |
| 评测 | `EvalChatService` 等：plan 解析 repair once、TOOL/RAG/safety stub、`meta` 含 `low_confidence_reasons`、**`recovery_action` / `self_check`（reflection stub）**、E7 membership |
| 工程 | Docker Compose、Flyway、Actuator、Testcontainers 集成测试（含 eval 网关 401/200） |

---

## 已知差距 / 风险（仍有效）

- **`conversationId`**：已支持服务端签发与 Redis 登记；生产可设 `app.conversation.require-registration=true` 强制先 `POST /travel/conversations` 再拉 SSE。  
- **部分接口错误体**：`/knowledge/upload` 已统一为 JSON 成功/失败体；其它历史路径若仍返回非 JSON，可按需逐项收口。  
- **长期记忆**：已实现画像存储、删除权与「从对话 LLM 抽取 → 默认待确认 → 显式 confirm」；**未**实现客户端内嵌式一键确认 UI 与复杂合规审计流。  
- **阶段顺序**：评测 stub 与主线均为 `…TOOL→GUARD→WRITE`（详见 `IMPLEMENTATION_MATRIX.md`）。  
- **未配置 `APP_EVAL_GATEWAY_KEY`** 时：评测路径全部 401（有意防误暴露）。

---

## 文档索引

| 文档 | 用途 |
|------|------|
| [`IMPLEMENTATION_MATRIX.md`](IMPLEMENTATION_MATRIX.md) | 本仓 ↔ `travel-ai-upgrade` 对照与后续计划 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 链路、安全、SSE、Compose |
| [`UPGRADE_PLAN.md`](UPGRADE_PLAN.md) | 仓库内 P0–P5 评审项（与矩阵互补） |
| [`DAY10_P0_CLOSURE.md`](DAY10_P0_CLOSURE.md) | Day10 过线证据与里程碑留痕 |
| [`HARNESS_RULES.md`](HARNESS_RULES.md) | harness 脚本与周报模板 |
| [`eval.md`](eval.md) | 手工 RAG 回归问题集 |
| [`TOOL_GOVERNANCE_SPEC.md`](TOOL_GOVERNANCE_SPEC.md) | 工具归因与观测字段约定 |

---

## 历史归档

更早日程（Week 2–4 Day 编号、Day 4 向量行数验收等）已收敛进 Git 历史；**当前以本文件 + `IMPLEMENTATION_MATRIX` 为准**，避免多份 STATUS 口径分叉。
