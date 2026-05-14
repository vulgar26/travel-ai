# 实现对照表（本仓代码 ↔ Vagent `travel-ai-upgrade.md`）

**维护约定**：以 `src/main/java` 与 `application*.yml` 为真源；本文件随合入更新。**外部计划**路径：`D:\Projects\Vagent\plans\travel-ai-upgrade.md`（不在本仓库内，此处仅摘要对照）。

**更新日期**：2026-04-23（§1：**P1-3** `user_feedback` + **`/travel/feedback`**；§4：`POST /travel/chat` + `max-query-chars`；`docs/eval.md` 链至 **`LLM_REAL_USAGE_RUNBOOK`**；§5：429 限流体 `error`+`message`；**`meta.config_snapshot_id`**；可选 **`meta.config_snapshot`**；评测 **`meta.policy_events[]`**；**`context_truncation_reasons`** 含检索候选上限与改写截断；**Flyway V3** `eval_conversation_checkpoint`：**写库 + 读路径续跑**；含历史 §4/§5 条目）

---

## 1. 主线 `TravelAgent`（SSE `/travel/chat`）

| `travel-ai-upgrade` 要点 | 本仓状态 | 代码 / 配置入口 |
|--------------------------|----------|-----------------|
| P0 固定线性阶段、禁止 DAG / 阶段 Map 驱动 | **已满足**：`runLinearStages` 固定调用 `stagePlan` → `stageRetrieve` → `stageTool` → `stageGuard`，无「阶段名→处理器」注册表 | `TravelAgent.java` |
| 阶段顺序 `plan→retrieve→tool→write→guard`（文档写法） | **部分偏差**：实现顺序为 **`PLAN → RETRIEVE → TOOL → GUARD → WRITE`**（门控在流式写之前） | 同上 |
| Plan-and-Execute：结构化 Plan JSON | **已满足（物理跳过）**：`MainLinePlanProposer` + 无记忆 `ChatClient`；`app.agent.plan-stage.enabled` 控制是否调 LLM；**PLAN 末**经 `PlanParseCoordinator`（附录 E + repair 一次）回写 `planJson`；仍失败则降级 JSON；**计划文本注入** `finalPromptForLlm`；**按 `steps[*].stage`** 经 `PlanPhysicalStagePolicy` 物理跳过 RETRIEVE/TOOL/GUARD（`GUARD` 在含 `RETRIEVE` 时隐式执行）；评测 `meta.stage_order` 与 `EvalLinearAgentPipeline` 同步 | `PlanPhysicalStagePolicy.java`、`MainLinePlanProposer.java`、`PlanParseCoordinator.java`、`TravelAgent.java`、`EvalChatService.java`、`EvalLinearAgentPipeline.java` |
| SSE 流内回显 `plan_parse_*`（与评测 meta / 日志对账） | **已满足**：`Flux` 最前发送 **`event: plan_parse`**，`data` 为 JSON（`plan_parse_outcome`、`plan_parse_attempts`、`plan_draft_source`、`plan_parse_resolved`、`request_id`） | `TravelAgent.java` |
| `app.agent.max-steps` / `total-timeout` / `tool-timeout` 等统一收口 | **部分满足**：`AppAgentProperties` 绑定 `app.agent.*`；`TravelAgent` / `WeatherTool` / **`EvalChatService`** 共用；评测 `meta` 回显 `agent_*_timeout_ms` / `agent_max_steps_configured`，`latency_ms` 与 `agent_total_timeout_ms` 比较写入 `agent_latency_budget_exceeded`；**整段评测**在 `EvalChatController` 用 `app.agent.total-timeout` 包裹 `buildStubResponse`（超时则 `AGENT_TOTAL_TIMEOUT`） | `AppAgentProperties.java`、`EvalChatService.java`、`EvalChatController.java`、`EvalChatTimeoutExecutorConfig.java` |
| 检索去重按业务 id | **已满足**：`mergeAndDedupeDocuments` | `TravelAgent.java` |
| QueryRewriter 畸形兜底 | **已满足**：失败/空行回退与补齐、`max-line-length` | `QueryRewriter.java`、`app.rag.rewrite.max-line-length` |
| 零命中门控 | **已满足**：`RetrieveEmptyHitGate` + `app.rag.empty-hits-behavior` | `RetrieveEmptyHitGate.java`、`TravelAgent.java` |
| 串行工具、熔断、限流 | **已满足**：天气路径使用 `ToolExecutor` + `ToolCircuitBreaker` + `ToolRateLimiter` | `com.travel.ai.tools.*`、`WeatherTool.java` |
| P0 长期记忆 / 隐私：`user_profile`、删除权、最小化槽位、默认关闭注入；从对话抽取须可确认 | **已满足（基线 + 抽取）**：同上；**抽取**为无记忆 `profileExtractionChatClient` + `ChatMemory` 摘录；默认 `require-confirm` 仅 Redis pending，`confirm-extraction` 落库；`after-chat` 可选 | `ProfileExtraction*.java`、`ProfileExtractionChatClientConfig.java`、`UserProfilePendingExtractionStore.java`、`UserProfileController` |
| P1-3 反馈闭环：`feedback` 表 + API；点赞/点踩/评分 + 可选文本；与 eval 标签/样例联动 | **已满足（基线）**：表 **`user_feedback`**（Flyway **V4**）；**`POST /travel/feedback`**（**201**）、**`GET /travel/feedback`**；JWT **`user_id`** 隔离；`app.feedback.*` | `FeedbackController.java`、`FeedbackService.java`、`FeedbackJdbcRepository.java`、`AppFeedbackProperties.java`、`V4__user_feedback.sql` |

---

## 2. 评测口 `POST /api/v1/eval/chat`

| `travel-ai-upgrade` / eval SSOT 要点 | 本仓状态 | 代码入口 |
|----------------------------------------|----------|----------|
| 非流式 JSON、snake_case、`latency_ms` | **已满足** | `EvalChatController.java`、`EvalChatResponse` DTO |
| `meta.stage_order` / `step_count` / `replan_count` | **已满足**（`replan_count` 恒 0） | `EvalChatService.java`、`EvalLinearAgentPipeline.java` |
| 评测 stub 管线阶段顺序与主线一致 | **已满足**：与主线同为固定顺序下的 **plan 驱动物理跳过**；全量时 **`PLAN→RETRIEVE→TOOL→GUARD→WRITE`**（`EvalLinearAgentPipeline` / `EvalChatService` / `meta.stage_order`） | `PlanPhysicalStagePolicy.java`、`EvalLinearAgentPipeline.java`、`EvalChatService.java` |
| Plan 解析 repair once、`plan_parse_attempts/outcome` | **已满足**（评测路径）；协调器中性包 **`PlanParseCoordinator`** | `PlanParseCoordinator.java`、`PlanParser.java` |
| E7 membership、`X-Eval-*` HMAC | **已满足** | `RetrievalMembershipHasher.java`、`EvalMembershipHttpContext.java` |
| 评测 HTTP 网关（文档外增量） | **已满足**：`X-Eval-Gateway-Key` ↔ `app.eval.gateway-key`（`AppEvalProperties`），未配置则评测路径 401 | `AppEvalProperties.java`、`EvalGatewayAuthFilter.java`、`SecurityConfig.java` |
| `meta.low_confidence` + **`low_confidence_reasons`**（对外 JSON） | **已满足** | `EvalChatMeta.java`、`EvalChatService.java` |
| RAG stub、`eval_rag_scenario` | **已满足** | `EvalRagGateScenarios.java` |
| TOOL stub、`eval_tool_scenario` | **已满足** | `EvalToolStageRunner.java` |
| 输入安全门控（Day9） | **已满足** | `EvalChatSafetyGate.java`、`EvalQuerySafetyPolicy.java` |
| `sources[]` 系统生成、非 LLM 编造；与 SSE 引用块对齐说明 | **已满足（评测 JSON）**；SSE 为纯文本首包，字段粒度见 **`docs/eval/SOURCES_EVAL_VS_SSE.md`** | `EvalChatService.java`、`TravelAgent#buildCitationBlock` |
| Reflection / recovery：`meta.recovery_action`、`meta.self_check`、`eval_reflection_scenario` | **已满足（stub）**：`EvalReflectionSupport` + `app.eval.reflection-meta-enabled`；与 `replan_count=0` 正交，无额外 plan LLM | `EvalReflectionSupport.java`、`EvalChatMeta.java`、`EvalChatRequest.java`、`EvalChatService.java` |

---

## 3. 安全与限流（`UPGRADE_PLAN` / `travel-ai-upgrade` 交叉项）

| 项 | 状态 | 入口 |
|----|------|------|
| 默认 `anyRequest().authenticated()` + 白名单 | **已满足** | `SecurityConfig.java` |
| 登录 + 聊天分桶限流、可配置 | **已满足** | `RateLimitingFilter.java`、`app.rate-limit.*` |
| JWT 弱密钥：docker/prod 等 profile **fail-fast** | **已满足** | `JwtSecretStartupValidator.java` |
| Filter 链 **401/403** 与全局 REST 常见 4xx 返回 **`application/json`**（`error`+`message`） | **已满足** | `SecurityConfig.java`、`JsonApiErrorSupport.java`、`RestApiExceptionHandler.java`；`EvalGatewayAuthFilter` 复用同一写出逻辑 |

---

## 4. 明确未做或仅部分覆盖（后续迭代）

以下在 `travel-ai-upgrade.md` 或本仓 `STATUS.md` 中仍为**缺口 / 大项未闭合**（其它已收口项见 §1–§3 表格，勿与本节重复）：

- **长期记忆** `user_profile` / 删除权 / 从对话抽取（默认待确认）/ 删画像时可选清 Redis：**已做**。**未做**：独立「按字段」合规审计流；**短期记忆保留期**仍见 `RedisChatMemory`（天级 TTL + 条数上限），与画像表正交。
- **按 plan `steps` 物理跳过阶段**：**已做**（`PlanPhysicalStagePolicy` + 主线 `TravelAgent` + 评测 `EvalChatService` / `EvalLinearAgentPipeline`；默认合法 plan 仍含全阶段以保持既有 eval 契约）。  
- **`conversationId` 归口**：已实现 `POST /travel/conversations` 签发 + Redis 登记；**推荐** `POST /travel/chat/{id}`（JSON body）拉 SSE；`GET /travel/chat/{id}?query=` 仍兼容（`Deprecation`）；路径校验；`app.conversation.max-query-chars`；`app.conversation.require-registration` 为 `true` 时强校验归属（默认 `false` 兼容演示/测试，见 `application.yml`）。
- **`attack/*` 等题库与文档对齐**：实现见 `EvalChatSafetyGate` / `EvalQuerySafetyPolicy` / `EvalBehaviorPolicy`；**可导入的示例与建议 `tags`** 已汇总于 **`docs/eval.md`** §「评测口：对抗与安全…」。**默认 CI** 已跑离线/容器内契约（见 [**`docs/eval/CI_AND_REMOTE_EVAL.md`**](eval/CI_AND_REMOTE_EVAL.md)）；**对公网部署 target 的全量 dataset 回归**仍由 Vagent/staging 流程承担，未并入本仓 `ci.yml`。

---

## 5. 建议的下一步（与外部计划对齐的优先级）

**已收口（原 §5 1–3，仅留档）**：`AppEvalProperties`（`app.eval.*`）；评测整段 **`app.agent.total-timeout`** 硬中断（`AGENT_TOTAL_TIMEOUT`）；评测与主线阶段顺序 **`…TOOL→GUARD→WRITE`**；外部 `travel-ai-upgrade.md` §「与 eval 的对接」已补网关与超时表述。

**已收口（§5 原第 1 条）**：`conversationId`——`POST /travel/conversations`、`ConversationRegistry`、路径校验、`app.conversation.require-registration`。

**已收口（§5 原第 2 条）**：按 `plan.steps` 物理跳过阶段（`PlanPhysicalStagePolicy`；评测默认 plan 仍为五步全量）。

**已收口（§5 原第 3 条）**：Reflection / recovery 评测契约 stub（`meta.recovery_action`、`meta.self_check`、`eval_reflection_scenario`，`app.eval.reflection-meta-enabled`）。

**当前推荐执行顺序**：

1. ~~**长期记忆与隐私治理**（`user_profile`、删除权、可选 prompt 注入）~~：**已收口（基线）**；后续可选：自动摘要写入（须用户确认）、更细 retention。  
2. ~~**工程债（错误体）**~~：**已收口（鉴权 + 常见 REST + 429）**：除 `/knowledge/upload` 与 `KnowledgeControllerAdvice` 外，`SecurityConfig` 的 401/403 与 `RestApiExceptionHandler`（`ResponseStatusException` 等）均为 JSON；**`RateLimitingFilter` 的 429** 亦经 **`JsonApiErrorSupport`** 输出 `error`+`message`（`error=RATE_LIMITED`）；评测网关错误与上述同形。  
3. **P1-0 harness 分步**：`context_truncated`（含 **`retrieval_candidates_capped`** / **`retrieval_query_line_truncated`** 等）、`config_snapshot_hash`、**`meta.config_snapshot_id`**、可选 **`meta.config_snapshot`**（**`app.eval.config-snapshot-meta-enabled`**）、字符级 **`context_*`/`token_source=estimate`**、可选 **provider usage**（**`llm_mode=real`** + **`eval_tags`** 门禁 + **`app.eval.llm-real-*`**）、**`meta.policy_events[]`**（门控/策略短路轨迹）、**评测断点续跑**（`eval_conversation_checkpoint` 读写 + stub 续跑 + 快照复用，见 **`docs/eval/EVAL_REPLAY_CHECKPOINT.md`**）等已在代码落地；**SSOT 上仍可选的扩展**（Tokenizer 真预算、Supervisor-Worker 等）仍以 **`docs/eval/P1_HARNESS_GAP.md`**、**[`docs/eval/LLM_REAL_USAGE_RUNBOOK.md`](eval/LLM_REAL_USAGE_RUNBOOK.md)** 为索引。

**维护提醒**：若调整 SSE 线性阶段顺序，须同步 `EvalLinearAgentPipeline`、`EvalChatService` 中手工 `stage_order` 与相关契约测试。  
