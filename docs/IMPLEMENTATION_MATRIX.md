# 实现对照表（本仓代码 ↔ Vagent `travel-ai-upgrade.md`）

**维护约定**：以 `src/main/java` 与 `application*.yml` 为真源；本文件随合入更新。**外部计划**路径：`D:\Projects\Vagent\plans\travel-ai-upgrade.md`（不在本仓库内，此处仅摘要对照）。

**更新日期**：2026-04-19（agent 超时配置收口）

---

## 1. 主线 `TravelAgent`（SSE `/travel/chat`）

| `travel-ai-upgrade` 要点 | 本仓状态 | 代码 / 配置入口 |
|--------------------------|----------|-----------------|
| P0 固定线性阶段、禁止 DAG / 阶段 Map 驱动 | **已满足**：`runLinearStages` 固定调用 `stagePlan` → `stageRetrieve` → `stageTool` → `stageGuard`，无「阶段名→处理器」注册表 | `TravelAgent.java` |
| 阶段顺序 `plan→retrieve→tool→write→guard`（文档写法） | **部分偏差**：实现顺序为 **`PLAN → RETRIEVE → TOOL → GUARD → WRITE`**（门控在流式写之前） | 同上 |
| Plan-and-Execute：结构化 Plan JSON | **部分满足**：`MainLinePlanProposer` + 无记忆 `ChatClient`；`app.agent.plan-stage.enabled` 控制是否调 LLM；**PLAN 末**经 `PlanParseCoordinator`（附录 E + repair 一次）回写 `planJson`；仍失败则降级 JSON；**计划文本注入** `finalPromptForLlm`，**非**按 `steps` 跳过物理阶段 | `MainLinePlanProposer.java`、`PlanParseCoordinator.java`、`TravelAgent.java`、`MainLinePlanChatClientConfig.java`、`application.yml` |
| `app.agent.max-steps` / `total-timeout` / `tool-timeout` 等统一收口 | **部分满足**：`AppAgentProperties` 绑定 `app.agent.*`；`TravelAgent` / `WeatherTool` / **`EvalChatService`** 共用；评测 `meta` 回显 `agent_*_timeout_ms` / `agent_max_steps_configured`，`latency_ms` 与 `agent_total_timeout_ms` 比较写入 `agent_latency_budget_exceeded`；**整段评测**在 `EvalChatController` 用 `app.agent.total-timeout` 包裹 `buildStubResponse`（超时则 `AGENT_TOTAL_TIMEOUT`） | `AppAgentProperties.java`、`EvalChatService.java`、`EvalChatController.java`、`EvalChatTimeoutExecutorConfig.java` |
| 检索去重按业务 id | **已满足**：`mergeAndDedupeDocuments` | `TravelAgent.java` |
| QueryRewriter 畸形兜底 | **已满足**：失败/空行回退与补齐、`max-line-length` | `QueryRewriter.java`、`app.rag.rewrite.max-line-length` |
| 零命中门控 | **已满足**：`RetrieveEmptyHitGate` + `app.rag.empty-hits-behavior` | `RetrieveEmptyHitGate.java`、`TravelAgent.java` |
| 串行工具、熔断、限流 | **已满足**：天气路径使用 `ToolExecutor` + `ToolCircuitBreaker` + `ToolRateLimiter` | `com.travel.ai.tools.*`、`WeatherTool.java` |

---

## 2. 评测口 `POST /api/v1/eval/chat`

| `travel-ai-upgrade` / eval SSOT 要点 | 本仓状态 | 代码入口 |
|----------------------------------------|----------|----------|
| 非流式 JSON、snake_case、`latency_ms` | **已满足** | `EvalChatController.java`、`EvalChatResponse` DTO |
| `meta.stage_order` / `step_count` / `replan_count` | **已满足**（`replan_count` 恒 0） | `EvalChatService.java`、`EvalLinearAgentPipeline.java` |
| 评测 stub 管线阶段顺序与主线一致 | **已满足**：**`PLAN→RETRIEVE→TOOL→GUARD→WRITE`**（`EvalLinearAgentPipeline` / `EvalChatService` / `meta.stage_order`） | `EvalLinearAgentPipeline.java`、`EvalChatService.java` |
| Plan 解析 repair once、`plan_parse_attempts/outcome` | **已满足**（评测路径）；协调器中性包 **`PlanParseCoordinator`** | `PlanParseCoordinator.java`、`PlanParser.java` |
| E7 membership、`X-Eval-*` HMAC | **已满足** | `RetrievalMembershipHasher.java`、`EvalMembershipHttpContext.java` |
| 评测 HTTP 网关（文档外增量） | **已满足**：`X-Eval-Gateway-Key` ↔ `app.eval.gateway-key`（`AppEvalProperties`），未配置则评测路径 401 | `AppEvalProperties.java`、`EvalGatewayAuthFilter.java`、`SecurityConfig.java` |
| `meta.low_confidence` + **`low_confidence_reasons`**（对外 JSON） | **已满足** | `EvalChatMeta.java`、`EvalChatService.java` |
| RAG stub、`eval_rag_scenario` | **已满足** | `EvalRagGateScenarios.java` |
| TOOL stub、`eval_tool_scenario` | **已满足** | `EvalToolStageRunner.java` |
| 输入安全门控（Day9） | **已满足** | `EvalChatSafetyGate.java`、`EvalQuerySafetyPolicy.java` |
| `sources[]` 系统生成、非 LLM 编造 | **评测路径已构造** | `EvalChatService.java` |

---

## 3. 安全与限流（`UPGRADE_PLAN` / `travel-ai-upgrade` 交叉项）

| 项 | 状态 | 入口 |
|----|------|------|
| 默认 `anyRequest().authenticated()` + 白名单 | **已满足** | `SecurityConfig.java` |
| 登录 + 聊天分桶限流、可配置 | **已满足** | `RateLimitingFilter.java`、`app.rate-limit.*` |
| JWT 弱密钥：docker/prod 等 profile **fail-fast** | **已满足** | `JwtSecretStartupValidator.java` |

---

## 4. 明确未做或仅部分覆盖（后续迭代）

以下在 `travel-ai-upgrade.md` 中出现，但**本仓尚未**完整落地或仅局部存在：

- **评测整段 HTTP 强制中断**：`latency_ms` 仍可能大于 `agent_total_timeout_ms`（未在评测线程上套 `total-timeout`）；`EvalToolStageRunner` 的 timeout stub 已与 `app.agent.tool-timeout` 取 min。
- **Reflection / recovery**（一次性反思）、`self_check` JSON、`meta.recovery_action`。
- **长期记忆** `user_profile` / 保留期 / 删除权（文档 P0 整节隐私治理）。
- **主线与评测**阶段顺序已统一为 **`…TOOL→GUARD→WRITE`**（`TravelAgent` 与 `EvalLinearAgentPipeline` 一致）。  
- **按 plan `steps` 物理跳过阶段**：与当前 P0「固定五次调用」类注释一致，**未做**；若要做须改契约与测试（见 `TravelAgent` 类注释与外部计划 P0-1）。

---

## 5. 建议的下一步（与外部计划对齐的优先级）

1. **配置收口（续）**：`app.eval.*` 已收口为 **`AppEvalProperties`**（网关密钥、`tool-timeout-ms`、`stub-work-sleep-ms`）；TOOL stub deadline 仍与 `AppAgentProperties` 取 min；整段 `total-timeout` 已接。  
2. **阶段顺序**：SSOT 为 **`…GUARD→WRITE`**；若将来调整 SSE 编排，须同步 `EvalLinearAgentPipeline`、`EvalChatService` 中手工 `stage_order` 与契约测试。  
3. **外部计划对齐**：`D:\Projects\Vagent\plans\travel-ai-upgrade.md` 已扩充 §「与 eval 的对接」：**`X-Eval-Gateway-Key`** / `APP_EVAL_GATEWAY_KEY`、`AGENT_TOTAL_TIMEOUT`、meta 超时对账、TOOL stub min 规则；并修正阶段顺序为 **`…GUARD→WRITE`** 的表述。  
4. **P0-2 加深（续）**：按 `steps` 物理跳过阶段、SSE 回显 `plan_parse_*` 等（主线已与 `PlanParseCoordinator` / `PlanV1` 对齐）。  
