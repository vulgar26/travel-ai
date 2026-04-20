## travel-ai 升级方案（研发助手 Agent：可解释、可控、可回归）

### 一句话定位（简历口径）

研发助手 **Agent**（Spring AI + 工具 + 可选私有知识检索），强调**计划-执行、工具闭环、记忆、降级**与**可回归评测**。

> **本文件位置**：仓库内副本为 **`docs/travel-ai-upgrade.md`**（与 `docs/UPGRADE_PLAN.md`、`docs/IMPLEMENTATION_MATRIX.md` 配套）。若与 `D:\Projects\Vagent\plans\travel-ai-upgrade.md` 上游有冲突，以 **本仓库矩阵 + `src`** 为交付真源，并回写本节「实现状态快照」。

---

## 本仓库实现状态快照（以代码为准 · 2026-04-18）

> **真源**：[`IMPLEMENTATION_MATRIX.md`](IMPLEMENTATION_MATRIX.md) + `src/main/java` + `application*.yml`。下表回答「规划写了什么 / 本仓做到哪一步」，不重复矩阵中的逐条表格。

### 已在本仓库落地

- **线性可控 Agent（SSE）**：`TravelAgent` 固定 **`PLAN → RETRIEVE → TOOL → GUARD → WRITE`**（**门控在流式写作前**）；无 DAG、无「阶段名→处理器」注册表式动态跳转。
- **Plan-and-Execute + repair once**：`MainLinePlanProposer`、`PlanParseCoordinator`、`PlanParser`；`meta.plan_parse_attempts` / `plan_parse_outcome`；SSE 最前 **`event: plan_parse`**（JSON `data`）与评测 meta 对账。
- **按 plan `steps` 物理跳过阶段**：`PlanPhysicalStagePolicy`；评测侧 `EvalLinearAgentPipeline` / `EvalChatService` 与主线阶段语义对齐。
- **评测口 `POST /api/v1/eval/chat`**：非流式 JSON、`latency_ms`、`capabilities`、`meta.stage_order` / `step_count` / **`replan_count` 恒 0**；**`X-Eval-Gateway-Key`**（`EvalGatewayAuthFilter` + `AppEvalProperties`）；E7 **`meta.retrieval_hit_id_hashes`** 与配套头（`RetrievalMembershipHasher`、`EvalMembershipHttpContext`）。
- **工具治理（串行路径）**：`ToolExecutor` + **`ToolCircuitBreaker` + `ToolRateLimiter`**、`WeatherTool` 超时/降级；与评测 TOOL stub、`eval_tool_scenario` 联动。
- **RAG 基线**：`QueryRewriter` 畸形兜底、`TravelAgent#mergeAndDedupeDocuments`、**`RetrieveEmptyHitGate`** + `app.rag.empty-hits-behavior`。
- **配置收口**：`app.agent.*`、`app.rag.*`、`app.eval.*`、`app.rate-limit.*`、`app.conversation.*` 等（见 `application.yml`）。
- **安全与 API 错误体**：JWT、`SecurityConfig`（默认需认证 + 白名单）、`JwtSecretStartupValidator`；**401/403 与常见 4xx** 统一 **`application/json`**（`error` + `message`，`JsonApiErrorSupport` / `RestApiExceptionHandler`；评测网关错误同形）。
- **`user_profile` 与抽取（基线）**：Flyway + JDBC（如 `UserProfileJdbcRepository`）、JWT 维度隔离、reset/confirm 等 API；对话抽取 **默认待用户确认** 再落库（Redis pending 等）；**尚未**实现下文 P1-7 满配的 **`user_profile_events` 审计链 / 按事件回滚 / 导出**。
- **`conversationId` 归口**：`POST /travel/conversations`、`ConversationRegistry`、路径校验、`app.conversation.require-registration`。
- **输入安全门控（评测路径）**：`EvalChatSafetyGate`、`EvalQuerySafetyPolicy` 等与数据集标签（含 `attack/*` 类）联动。

### 部分落地 / 需用 eval 持续验收（≠ 未写代码）

- **P0 数值门槛**（如 `CONTRACT_VIOLATION=0`、`UNKNOWN`/`TIMEOUT` 占比、`plan_parse_outcome=failed` 占比等）：字段与降级路径已具备；**是否长期达标**仍依赖固定 dataset 与每次全量 run 的 **`run.report`** 聚合。**已定稿**：可复现核对步骤见 **[`docs/eval/P0_THRESHOLD_RUNBOOK.md`](eval/P0_THRESHOLD_RUNBOOK.md)**（分母、公式、与 `meta`/`latency_ms` 的对照及离线烟测范围）。文中登记的 `run_id` 仅为**时点证据**，换题库须重跑并更新登记。
- **对抗与安全样例**：工程侧有门控与错误码；**已定稿**：`docs/eval.md` §「评测口：对抗与安全 + RAG/tool 确定性用例」给出与 **`EvalChatSafetyGate` / `EvalQuerySafetyPolicy` / `EvalBehaviorPolicy`** 对齐的示例 `query`、预期 `behavior`/`error_code` 及**建议数据集 `tags`**（便于导入 Vagent 与分桶）。**仍待推进**：把行表批量导入为 ≥20 条正式 case、CI 对公网 target 全量跑（见未做清单）。
- **`sources[]` 系统构造**：评测路径由 **`EvalChatService#retrieveEvidence`** 从 `Document` 映射为 **`sources[]` / `retrieval_hits[]`**（禁止 LLM 编造）。**SSE** 由 **`TravelAgent#buildCitationBlock`** 输出「【引用片段】」纯文本；**同源、不同载体**（snippet 截断 300 vs 200 等）。**已定稿**：[**`docs/eval/SOURCES_EVAL_VS_SSE.md`**](eval/SOURCES_EVAL_VS_SSE.md)。**仍可选**：统一截断长度、或增加 SSE 结构化 `sources` 事件（见该文 §4）。
- **P1-0 harness 扩展项**：如显式 **token 预算**、`config_snapshot_json`、断点恢复、Supervisor-Worker 等：**未**按 SSOT 满配。**已收口小步**：`meta.context_truncated` + `context_truncation_reasons[]` 已在评测口落地（用于区分“回归来自预算截断 vs 策略变化”）；缺口与后续顺序见 [**`docs/eval/P1_HARNESS_GAP.md`**](eval/P1_HARNESS_GAP.md)。

### 本仓库尚未做（仍为规划 / P1+）

- **反馈闭环**：`feedback` 表 + API（**P1-3**）。
- **混合检索 + rerank**（**P1-2**）：全文/BM25 与向量融合、供应商 rerank、开关与 A/B 验收。
- **ReAct / 多跳**（**P1-4 / P1-4b**）及 `hop_trace[]`、`app.agent.max-hops` 等。
- **evidenceMap + quote-only 完整语义**（**P1-5**）：`EvalGuardrailsCapability` 等仅占**能力位**；**未**实现 SSOT 所述由服务端从草稿+证据生成 `evidenceMap[]` 及 quote-only 硬校验全流程。
- **DAG / 状态机 + 并行工具**（**P1-6**）。
- **长期记忆治理满配**（**P1-7**）：显式写入意图解析器、`MEMORY_WRITE_INTENT` 级硬门槛、事件表 diff、按事件 revert、导出、以及 `memory/*` 评测集全套等。
- **CI 对公网真实 target 的全量回归**（依赖 runner/隧道，见 `eval-upgrade` A0 类叙述）。
- **主聊天 POST + query 长度上限**（`UPGRADE_PLAN` **P5-2** 可选）：当前对外仍以 **GET** `/travel/chat/{conversationId}?query=` 为主。

### 读本文档时的顺序提醒（历史笔误）

- 正文若出现 **`… → tool → write → guard`**：为旧叙述习惯；**本仓库实现与评测**均为 **`… → TOOL → GUARD → WRITE`**。

---

## 现状（已具备）

基于 `docs/ARCHITECTURE.md` 与 `docs/UPGRADE_PLAN.md`：

- **主链路**：改写 →（可选）检索 →（可选）工具 → **门控 GUARD** → 上下文增强 → **SSE 流式写作（WRITE）**（与上节顺序一致）
- **工程底座**：JWT、限流、Docker Compose、Flyway、Testcontainers/CI、SSE 心跳与断线感知、超时与降级、perf 分段耗时
- **可解释性雏形**：SSE 首段输出引用片段；`docs/eval.md` 具备最小评测题集

### 可复现登记（eval target，`POST /api/v1/eval/chat`，2026-04-18）

- **证据 run**：`run_id=run_6106023bf5354e3089cf1d8b7c4421b4`（`eval.report`：`pass_rate=1.0`，32/32 completed，`error_code` TopN 为空）。`dataset_id` 以 eval 实例为准（示例：`ds_0d30f48d494443a096e281c7addba519`）；**重新导入题库会生成新 `dataset_id`，须重跑并更新登记**。
- **E7 hashed membership**：已实现 `meta.retrieval_hit_id_hashes[]` + 配套 `meta.retrieval_*` 口径字段；请求须带 `X-Eval-Token`、`X-Eval-Target-Id`、`X-Eval-Dataset-Id`、`X-Eval-Case-Id`（与 `plans/eval-upgrade.md` 统一请求一致）。**SSOT**：`eval-upgrade.md` E7；代码：`RetrievalMembershipHasher`、`EvalMembershipHttpContext`、`EvalChatService`。
- **与主链路（阶段顺序）**：本仓库已实现 **评测 stub 与 SSE `TravelAgent` 同为** `PLAN → RETRIEVE → TOOL → GUARD → WRITE`（`meta.stage_order` 与 `EvalLinearAgentPipeline` 一致；短路路径可为子序列如 `PLAN→GUARD`）。正文若仍见 `…tool→write→guard` 为**历史笔误**；实现与评测均以 **GUARD 在 WRITE 前** 为准。

> **换题材说明（只换皮不换骨）**：将“旅行规划”叙事替换为“研发助手”（PR/Issue triage、变更影响说明、代码审阅草稿、发布说明生成等）时，P0/P1 的工程目标（可控流水线/工具治理/记忆治理/可回归评测）保持不变；仅替换示例 prompt、演示 UI 文案与评测 case 的业务表述，避免大规模重构拖慢 P0+。

---

## Harness 统一口径（两层）

> **定义**：Harness = 用于“可复现、可对比”地运行被测系统的封装层。统一口径下，本项目把 harness 拆成两层并分别验收。

### 1. Evaluation Harness（评测 harness）

- 负责测什么、怎么判、怎么归因、怎么 compare：dataset 导入、run 执行触发、`run.report` 聚合、`compare(base vs cand)`、`/report/buckets` 分桶统计。
- 把结果落成可运营证据：失败清单（TopN + case_id）、regressions/improvements。

### 2. Execution Harness（执行 harness）

- 负责被测 target 内“模型之外的一切”：受控编排（固定/受控阶段顺序）、工具治理（超时/降级/熔断）、上下文预算与可观测 trace、门控与降级收口，以及 **config_snapshot** 的可回放信息。
- 要求 target 用结构化字段把执行证据提供给 eval：如 `meta.stage_order[]`、`meta.step_count`、`tool_outcome`/`tool_calls_count`、`hop_trace[]`（多跳）、`low_confidence_reasons[]`、`error_code` 等（字段口径以 SSOT 契约为准）。

## P0（必须做）：把“单链路 RAG”升级成“可控 Agent”

## P0 数据合规 / 隐私边界（强制）

travel-ai 新增 `user_profile`、摘要与评测接口后，必须把隐私边界当成 P0：

### 数据分类（P0）

- **PII**：精确出发地/当前位置、身份证明、手机号/邮箱、完整行程细节（可能间接识别）
- **偏好画像**：预算、强度、禁忌、住宿偏好等（属于敏感配置）
- **对话内容**：query/answer/sources snippet（可能包含敏感信息）

### 最小化采集（P0）

- `user_profile` 仅保存“规划必需”的 **≤10 个 slots**（你已确认），避免存精确地理坐标与可识别信息
- 评测接口 `POST /api/v1/eval/chat` 仅用于测试：默认不把原始 query/answer 落库（仅返回给 eval）
- `sources.snippet` 必须截断（例如 ≤300 字符）并可选返回 `snippet_hash`

### 保留期（Retention，P0）

- `user_profile`：长期保存（用户可删除/清空）
- `conversation_summary`：按会话保留，可配置 TTL（如 30/90 天）
- `eval/chat` 产生的数据：不在业务侧长期落库（除非显式 debug 开关）

### 删除权（P0）

- 提供用户自助清理：
  - 清空长期偏好（profile reset）
  - 删除会话（含摘要/消息）并级联删除相关缓存

### 访问控制与脱敏（P0）

- 仅本人可读写自己的 `user_profile`（从 JWT user identity 取 key）
- 日志禁止打印：完整 query、完整 sources 原文、工具返回全文；仅打印长度/命中数/hash

## P0 验收定义（强制：没有验收就不算完成）

本节定义 travel-ai 自身的 **P0 完成门槛**（可被 eval 的 `run.report` 直接统计/判定），用于确保“可控 Agent”约束本身不会被通用 PASS/FAIL 掩盖。

### P0 完成门槛（项目级，必须全部满足）

以下指标以同一 dataset（≥30 case）为统计口径（或以 `eval-upgrade.md` 的 P0 下限为准）：

- **协议与归因可用**：
  - `CONTRACT_VIOLATION = 0`
  - `UNKNOWN` 占比 ≤ **1%**
- **超时噪声受控**：
  - `TIMEOUT` 占比 ≤ **2%**
  - `TOOL_TIMEOUT` 占比 ≤ **5%**
- **可控 Agent 约束达标**：
  - `step_count` 超限占比（`step_count > app.agent.max-steps`）= **0**
  - 总耗时超限占比（`latency_ms > app.agent.total-timeout`）≤ **2%**

### 控制流限制的可验收条款（P0）

- **固定线性阶段顺序**（不得动态分支成图）：语义为 `plan → retrieve(optional) → tool(optional) → guard → write`（**门控在写作之前**；若上文偶见 `…write→guard` 字样，以本节顺序为准）
  - `meta.step_count` 必须等于“实际执行过的阶段数”（仅串行阶段计数）
  - `meta` 中必须返回 `stage_order[]`（推荐枚举序列：`PLAN|RETRIEVE|TOOL|GUARD|WRITE`），用于回归验证“无跳转/无回环”
- **禁止动态 replan loop**：
  - `meta.replan_count`（P0 必须返回）必须满足 `replan_count <= 0`（P0 不允许 replan；P1 再开放）

### Plan 解析与“修复一次”治理（P0）

要求业务侧在 `meta` 返回（P0 必需）：

- `meta.plan_parse_attempts`（1 或 2）
- `meta.plan_parse_outcome`：`success|repaired|failed`

验收阈值（P0）：

- `plan_parse_outcome=failed` 占比 ≤ **1%**
- `plan_parse_attempts=2` 占比 ≤ **10%**（修复一次不应成为常态路径）
- 任意 `plan_parse_outcome=failed` 的 case：必须以 `behavior=clarify` 结束且 `error_code=PARSE_ERROR`

### 串行工具与降级矩阵覆盖（P0）

- **串行工具**：同一请求内工具调用不得并行；`meta.tool_calls_count`（P0 必须返回）应与 `tool.used` 一致
- **降级覆盖**：对降级矩阵中列出的失败点（Plan 解析失败/检索 0 命中/工具超时/工具错误/反思解析失败）：
  - 必须输出 `error_code` 且 `behavior` 以 `answer|clarify|deny` 正常结束（不得异常退出/挂死）
  - 对 `PARSE_ERROR`：只允许“修复一次”后降级，禁止多次重试

## P0/P1 依赖排序（先做什么能跑、什么后置也不影响）

> 目标：避免一次性上全家桶导致不可控。travel-ai 的顺序必须保证：**任何时刻都能用 eval 跑通并定位失败原因**。

### Step 0（先决条件：能测）

- 先实现评测专用接口 `POST /api/v1/eval/chat`（须同时满足 **HTTP 网关密钥** `X-Eval-Gateway-Key` 与 **membership** 相关头如 `X-Eval-Token`，见下文 §「与 eval 的对接」），能返回 `answer/behavior/sources/tool/meta/latency_ms`。
- 在此之前，DAG/反思/多记忆都先不动：否则“做了也测不准”。

### Step 1（可跑 Agent 骨架）

- **先做最小可控闭环**（P0 不引入 DAG 与并行复杂度）：
  - 单步/半结构化 plan（只产出 1 个 plan，不做多步循环与复杂分支）
  - 串行工具调用（检索→工具→写作），每一步都有超时与降级
  - `meta.mode` + `step_count`（`mode` 为单词字段，符合 snake_case；此处 step_count 仅统计串行阶段数）

> 说明：DAG/状态机与并行工具是“复杂度放大器”。必须在 eval 稳定、且 run 对比证明收益后再引入（下沉到 P1）。

### Step 2（可控性：限步/限时/降级覆盖）

- 总超时、tool 超时、串行工具调用失败降级（P0 只要求串行）
- 统一降级输出：`behavior=clarify|deny` + `error_code`（见 `plans/eval-upgrade.md` 归因口径）

并行工具调用属于 P1 性能优化项：必须通过 eval 报告证明收益且不引入 regressions 后再开启。

### Step 3（可解释性：sources 由系统生成）

- 检索命中后由系统构造 `sources[]`（id/title/snippet/score），禁止 LLM 生成 sources
- 空命中/低置信门控（先用命中数=0；拿到 score 后再启用阈值）

### Step 4（反思：一次性补救）

- Reflection 只允许 0 或 1 次补救动作；解析失败必须降级并归因 `PARSE_ERROR`

### Step 5（P1：质量增强，可后置）

- 混合检索 + **可插拔 rerank（可选，需 eval 证明净收益后再开启）**
- 结构化长期记忆（user_profile + DST）+ 摘要（阈值触发，需显式确认与可回滚）
- evidence map / quote-only
- **DAG/状态机（可选）**：仅当需要多分支/重规划/回放时引入
- **并行工具（可选）**：仅当性能瓶颈明确，且回归无 regressions 时引入

### travel-ai 的“可控 Agent”定义（P0）

满足以下即认为达成“可控”（可被 eval 验证，而不是主观感受）：

- **限步**：一次评测请求的工作流步骤数 `step_count <= app.agent.max-steps`
- **限时**：总耗时 `latency_ms <= app.agent.total-timeout`（超时必须以 `behavior=deny|clarify` 或降级文本结束，不能挂死）
- **可解释**：当 `requires_citations=true` 时，返回 `sources[]` 且每条有 `id/title/snippet/score`
- **可观测**：`meta` 必须输出 `mode`（AGENT/BASELINE）、`guardrail_triggered`、`retrieve_hit_count`、`low_confidence`、`tool_outcome`
- **可回归**：在同一 dataset 上，`runA vs runB` 能输出 regressions（PASS→FAIL case 列表），且能按 `error_code` 归因

### 低置信/空命中门控（阈值如何定）

- **强制配置化**：`app.rag.empty-hits-behavior` 与 `app.guardrails.low-confidence-score-gate.enabled`
- **P0 默认（不启用基于 score 的门控）**：
  - `empty-hits-behavior=clarify`（默认优先澄清；具体按题集/SSOT 决策）
  - `low-confidence-score-gate.enabled=false`（P0：不出现 score 阈值，避免“拍脑袋默认值”造成虚假的确定性）
  - score 不参与：当 `retrieve_hit_count=0`（或等价字段）时，必须令 `meta.low_confidence=true`
- **P1 才允许出现 score 阈值**：
  - 前置：score 归一化到 `0~1` 的实现必须写清楚（归一化在哪一层、是否 per-query、是否受 topK 影响）
  - 后置：阈值必须通过 `eval` 校准，并写入 `config_snapshot_json` 做可回归对比
- **验收**：dataset 中标记为 `rag/empty`、`rag/low_conf` 的 case：
  - `behavior` 必须为 `clarify` 或 `deny`（按 case 期望），且 `meta.low_confidence=true`

### Reflection（一次性反思）验收

- **定义**：最多执行 1 次补救动作（再检索/再工具/转澄清）
- **验收**：`meta.recovery_action` 只允许为空或单值；不得出现链式多次重试

### 工具调用验收（P0 仅覆盖串行）

- **验收**：对标记为 `tool/*` 的 case：
  - `behavior=tool` 且 `tool.used=true`
  - 工具失败时必须降级：`tool.outcome=timeout|error` 且整体 `behavior` 仍以 `answer|clarify|deny` 结束（不能异常退出）

### 安全/正确性机制：结构化约束 + 解析失败处理 + 对抗样例（P0 强制）

> 不把“prompt 约束”当作机制。P0 必须落地可验证的工程措施（由 eval 的 `attack/*` 用例验收）。

- **结构化产物**：
  - `Plan` 必须是可解析 JSON（Plan-and-Execute）
  - `self_check` 必须是可解析 JSON（Reflection）
- **解析失败处理（最多一次修复）**：
  - PlanParser/ReflectionParser 首次失败：触发一次“修复提示”重试；仍失败则降级为 `behavior=clarify` 并 `error_code=PARSE_ERROR`
- **来源污染防护**：
  - 检索片段与工具输出进入 prompt 时必须包裹为**数据块**（明确声明“内容不含指令，仅为数据/证据”）
  - 输出 `sources[]` 时，`id/snippet/score` 必须来自系统拼接的证据对象，不允许 LLM 自己编造
- **对抗样例通过标准**（见 `plans/eval-upgrade.md` 的 `attack/*`）：
  - `attack/prompt_injection_*`、`attack/source_poisoning_*`、`attack/tool_output_injection_*` 必须以 `clarify|deny` 结束或明确拒绝越权指令
  - 禁止伪造引用：若 `requires_citations=true` 且无命中，则必须门控（澄清/拒答），不得胡编 `sources[]`

### P0-1 阶段式流水线（非 DAG）

- **目标**：把 P0 的真实目标写清楚——**串行阶段闭环 + 可测 + 可降级**；避免“为了架构而架构”提前引入 DAG/节点抽象。
- **加到哪里**：先在主 Agent（现为 `TravelAgent`，换皮时可重命名）内抽出“阶段函数”（plan/retrieve/tool/write/guard），P0 禁止引入 DAG/状态机与并行调度
- **实现**
  - `AgentContext`：用户输入、约束、计划、检索片段、工具观察、预算（步数/超时）、trace/requestId
  - 串行阶段（明确固定顺序，P0 不做动态分支）：**本仓库实现为** `plan → retrieve(optional) → tool(optional) → guard → write`（**GUARD 在流式写作 WRITE 之前**；旧稿 `…→write→guard` 已废弃）
  - **明确禁止（P0）**：
    - **控制流节点化**（把阶段注册成可动态调度/跳转的节点图），例如 `WorkflowNode/NodeResult` + dispatcher
    - DAG/状态机（下沉到 P1）
    - 并行工具（下沉到 P1）
  - **允许但不等价于 DAG（澄清歧义）**：
    - 允许用类/模块组织代码（例如 `PlanStage/GuardStage/ReflectStage`），但它们必须由**固定线性控制流**直接调用，不得通过“节点注册表/路由/动态跳转”驱动执行。
- **配置**
  - `app.agent.max-steps`
  - `app.agent.total-timeout`
  - `app.agent.tool-timeout`
- **观测**：延续 `[perf]`，补 `step_count`、`tool_outcome`、`replan_reason`
- **测试**：节点顺序/分支单测；Mock 工具的集成测试

### P0-2 Plan-and-Execute（计划-执行）+ 输出解释器

- **加到哪里**：`PlanStage` + `PlanParser` + `Executor`
- **实现**
  - LLM 输出结构化 `Plan`（JSON）：`steps[]`、`tools[]`、`constraints`
  - **严格 schema 输出**：在 system prompt 中给出 Plan 的 JSON Schema，并要求输出“纯 JSON”（无 markdown 包裹）
  - **容错解析（tolerant parse）**：
    - 允许：多余字段（忽略）、字段顺序变化、数字/字符串轻微类型漂移（可转换）
    - 不允许：缺少关键字段（如 `constraints` 或 `tools` 的结构缺失）
  - **修复一次（repair once）**：
    - 第一次解析失败：触发 1 次“修复提示”（把错误原因与期望 schema 返回给模型），要求只返回修复后的纯 JSON
    - 第二次仍失败：**不得走“直接回答”**，统一降级为 `behavior=clarify` 并 `error_code=PARSE_ERROR`
  - **修复提示回显限制（P0 强制，防滥用/防注入面扩大）**：
    - 修复提示只能包含**结构化错误摘要**，例如：
      - `error_code=PARSE_ERROR`
      - `violations[]`：`{ path, rule, expectedType?, message }`（只描述“缺字段/类型错误/非法枚举/JSON 非法”等）
      - `schema_excerpt`：仅包含**期望 schema 的最小片段**（字段名/类型/必填），不得包含业务数据

    - **允许回显的字段集（强制枚举；不在此集合内的任何字段一律禁止）**：
      - `error_code`
      - `violations[]`：`{ path, rule, expectedType?, message }`
      - `schema_excerpt`
      - （可选，用于排障而非重放内容）
        - `queryHash`（对用户 query 做不可逆 hash）
        - `contextSummaryLen`（用户上下文摘要长度的整数；不回显内容）

    - **严禁回显**（只要出现就视为安全边界违规）：
      - 原始 `tool` 输出全文（或大段片段）
      - 原始检索 `sources[].snippet` / KB 长片段
      - 用户 query/上下文的任何文本内容（包括“脱敏后的全文/摘要”）
      - 任何可能携带指令的文本块（防止把注入内容再次喂给模型）
    - **长度上限**：
      - `violations` 文本总长度 ≤ 2KB
      - `schema_excerpt` ≤ 2KB
    - **脱敏**：修复提示中不得出现 PII/token/key；日志同样禁止打印修复提示原文
  - **失败计数与观测**（P0 必须）：
    - `meta.plan_parse_attempts`（1 或 2）
    - `meta.plan_parse_outcome`：`success|repaired|failed`
    - 指标：`plan_parse_failed_total`、`plan_parse_repaired_total`
  - 执行严格按 plan（避免自由循环）
- **配置**：`app.agent.plan.enabled`、`app.agent.plan.max-steps`

### P0-3 串行工具调用 + 总超时 + 降级策略（P0 只做串行）

- **加到哪里**：`ToolStage`（串行阶段函数）
- **实现**
  - tool 级超时 + 总超时
  - 部分失败可降级（写入 `meta.tool_outcome`，不让整轮崩）

### 全分支降级/回滚矩阵（P0 必须覆盖）

| 子系统失败点 | 观测信号 | 降级行为（必须） | 归因（error_code/字段） |
|---|---|---|---|
| Plan 解析失败 | `PARSE_ERROR` | 触发一次修复；仍失败→`behavior=clarify`（让用户补信息） | `error_code=PARSE_ERROR` |
| 检索 0 命中 | `retrieve_hit_count=0` | `behavior=clarify|deny`（按 case/配置）且不输出伪 sources | `RETRIEVE_EMPTY` + `meta.low_confidence=true` |
| 工具超时/失败 | `tool.outcome=timeout|error` | 继续输出部分方案/改问法；不可直接崩溃 | `TOOL_TIMEOUT/TOOL_ERROR` |
| rerank 不可用（P1） | 超时/异常 | 回退为“未重排”的融合顺序 | `tool` 或 `meta.rerank_outcome=error`（建议新增） |
| 反思解析失败 | `PARSE_ERROR` | 最多一次修复；仍失败→`behavior=clarify|deny` | `PARSE_ERROR` |

### P0-4 工作记忆（会话内）与可控上下文（P0 只做短期/工作记忆）

- **加到哪里**
  - 短期：保持 conversationId + chat memory（现有）
  - 工作记忆：`AgentContext`（可选落库 run）
- **实现**
  - 仅在会话内维护：本次任务约束、工具观察、检索片段摘要
  - 任何“长期写入”在 P0 禁止自动发生（避免隐私与错误累积）
- **配置**：
  - `app.memory.working.enabled=true`
  - `app.memory.long-term.enabled=false`（P0 默认关）
- **测试**：会话内上下文不串号；清理逻辑正确（断线/取消不残留）

### P0-5 幻觉处理（引用约束 + 置信门控 + 工具一致性）

- **加到哪里**：`GuardStage`（或 `WriteStage` 后处理）
- **实现**
  - 走检索时回答必须带 `sources[]`（chunkId/来源/摘要）
  - 低置信（`doc_count=0`/低分）→ 澄清/拒答/允许常识回答（配置化）
  - 与工具结果冲突 → 触发一次反思补救或明确提示不确定
- **配置**
  - `app.guardrails.require-citations`
  - `app.rag.empty-hits-behavior`（对齐概念）

### P0-6 Reflection（一次性反思补救）

- **加到哪里**：`ReflectStage` 或 `GuardStage` 触发
- **实现**
  - LLM 产出 `self_check`（缺约束/证据弱/工具冲突）
  - 命中则最多一次补救：再工具/再检索/转澄清，并记录 `recovery_action`
- **配置**：`app.agent.reflection.enabled`、`app.agent.reflection.max-retries=1`

---

## P1（加分项）：多 Agent、编排模式与检索增强

### P1-0 harness 工程（可回归基建，避免“框架感”但测不出来）

> **背景**：你提到的“热门框架”（Claude/Codex/Cursor/Gemini 等）之所以看起来强，很大一部分来自 **harness**：把行为拆成可测的 stage、把工具与上下文做可控注入、把回归与对比自动化。这里不讨论具体厂商实现细节，只落地我们可做的工程化接口与门禁。

- **目标**：让“研发助手 Agent”的每次能力升级都能在同一 dataset 上做 **可重复的 A/B**，并输出足够的 `meta` 进行归因（而不是靠演示口述）。
- **harness 组成（建议）**
  - **可控注入**：请求体允许注入 `tool_policy`、`mode`、（可选）`eval_tool_scenario`、（可选）固定随机种子或 `config_snapshot_id`
  - **可观测快照**：`meta` 固定输出 `stage_order[]/step_count/replan_count/tool_outcome/reasons[]` 等，并可选输出 `config_snapshot_json`（见 `plans/eval-upgrade.md`）
  - **回放与对比**：同一输入在固定 config 下可复跑；输出落 `run.report` + `compare`
- **验收**：新增任何“Agent 能力”PR 时，必须提供：
  - 至少 1 次全量 run 的 `run_id`
  - 对比基线 run 的 compare 摘要（regressions 可解释）

#### 借鉴 HelloAgents 的工程机制（不复刻框架形态）

> **原则**：HelloAgents 更像“通用框架”；travel-ai 目标是“可验收的研发助手 Agent 应用模板”。因此只借鉴其工程化机制，并把每一项落在 **meta 字段 + 报表分桶 + 回归门禁** 上。

- **工具协议（ToolResponse 思路）**
  - **落地**：统一 `tool` + `meta.tool_*` 的字段语义（`used/name/outcome/error_code/latency_ms`），并将“超时/异常/被策略禁止/被熔断”区分为稳定 outcome。
  - **验收**：`TOOL_TIMEOUT/TOOL_ERROR` 占比可控；失败仍以 `answer|clarify|deny` 结束；TopN 可按 outcome 分桶。
- **上下文工程（token 预算/截断）**
  - **落地**：为 `AgentContext` 引入明确的上下文预算（历史/检索/工具输出各自上限），对工具输出做截断与摘要，并在 `meta` 输出 `context_truncated=true` 等可观测信号（不回显原文）。
  - **验收**：在长对话/长工具输出 case 下不触发异常退出；`UNKNOWN` 不因 prompt 爆长而上升。
- **会话/任务持久化（断点恢复）**
  - **落地**：最小版仅保存“可恢复现场”（`plan_raw_hash`、`stage_order`、最后一次 `error_code`/`tool_outcome`）；P1 再扩展为“按 conversationId 恢复到最近成功 stage”。
  - **验收**：中途超时/取消后可重试恢复；不会重复执行已完成的工具调用（需幂等或显式禁止）。
- **熔断器（CircuitBreaker）**
  - **落地**：按 `toolName` + scope（用户/租户/全局）统计连续失败；超过阈值短期禁用并降级；`meta` 标注 `tool_disabled_by_circuit_breaker=true`。
  - **验收**：外部工具抖动时，`TIMEOUT/TOOL_TIMEOUT` 不“拖垮全量”；禁用/恢复行为可解释可回归。
- **子任务/子代理（Sub-agent）**
  - **落地**：对应 **P1-1 Supervisor-Worker**：用“固定角色 Worker + 工具白名单 + 上下文隔离”完成拆分，不做自由群聊。
  - **验收**：每个 Worker 的输入输出可追踪（stage trace），且不会绕过 guardrails/工具白名单。
- **决策日志（DevLog/Trace）**
  - **落地**：对关键分支写入结构化决策事件（`policy_id/rule_id/decision/reasons[]`），用于 compare 回归解释（不写入敏感原文）。
  - **验收**：任意 FAIL/降级都能追溯到明确决策事件；周报可按 policy/rule 分桶。

### P1-0b 编排模式库（五种常见模式；受控开启，避免黑盒 DAG）

> **原则**：P0 只允许 **固定线性流水线**（见 P0-1）。P1 才引入更复杂编排，但必须“可解释、可回归、可降级”。

1) **Linear Pipeline（默认）**：**本仓库为** `PLAN → (RETRIEVE) → (TOOL) → GUARD → WRITE`（P0/P1 均可用；旧序 `…WRITE→GUARD` 不适用本实现）  
2) **Plan-and-Execute（强约束）**：结构化 plan 驱动执行（P0 已覆盖 P0-2）  
3) **ReAct（强约束）**：允许 Observation→Action 的多步探索，但必须限步/限时/限工具白名单（见 P1-4）  
4) **Supervisor-Worker（受控团队）**：Supervisor 分配固定角色 Worker（Retriever/ToolRunner/Writer/Guard），禁止自由群聊（见 P1-1）  
5) **DAG/State Machine（里程碑）**：显式节点与分支、可回放与重规划（见 P1-6；必须先有稳定 harness 与两轮 compare 证明净收益）

### P1-1 层级 Multi-Agent（Supervisor-Worker，受控工作流）

- **加到哪里**：workflow 角色化节点（不做自由群聊）
- **实现**
  - Supervisor：产出 plan + 分配
  - Workers：Retriever / ToolRunner / Writer / Guard
  - 任务分配先规则（按“需检索/需工具/需写作”映射），后续再谈算法化
- **配置**：`app.agent.roles.enabled`

### P1-2 混合检索 +（可选）重排（Rerank：默认关，A/B 证明后再开）

- **加到哪里**：检索组件（`PgVectorStore` 外围或新 `HybridRetriever`）
- **实现**
  - 向量 TopK + 关键词/BM25 TopK 融合（如 RRF），合并去重再截断
  - **重排（可选）**：对融合后的候选做二次打分，取前 N（默认关闭）
    - **选型（已确认的默认实现）**：优先使用 **供应商 Rerank API（C）**，但必须先通过 eval 做 A/B（无重排 vs 有重排）证明净收益
    - **工程约束**：候选数上限（如 50）、总超时（如 2–3s）、失败降级为“不重排仅按融合顺序”
    - **缓存**：`(queryHash, candidateIdListHash, rerankModelVersion)` → rerank 结果 TTL（降低成本、降低尾延迟）
    - **开关**：`app.rag.rerank.enabled=false`（默认关），只在 eval 验收通过后打开

#### Rerank 风险评估（必须写进设计并在 eval 中验证）

| 风险维度 | 关注点 | 约束/对策（P1 必须） |
|---|---|---|
| 成本 | rerank 每次请求额外计费 | 仅对“候选数>阈值/问题类型”启用；强缓存；候选上限 |
| 延迟 | 外部调用增加 RTT，放大 P95 | 总超时；并发隔离；失败即降级；指标记录 `rerank_latency_ms` |
| 可用性 | 供应商抖动/限流 | 熔断/重试（仅幂等）；降级为无重排；在 meta 标注 outcome |
| 鉴权/密钥 | key 泄露风险 | 只用 env/secret；日志脱敏；启动校验（docker/prod fail-fast） |
| 合规/出境 | query/片段是否可外发 | **开关**：`app.rag.rerank.allow-external=true/false`；默认按合规要求；必要时仅发 hash/标题而非原文（若供应商支持） |
| 缓存一致性 | embedding/model 变更导致缓存污染 | cache key 必含 `rerankModelVersion`；版本变更清缓存 |
| 质量副作用 | rerank 可能误排导致召回变差 | 必须做 eval A/B；compare 不得出现明显 regressions；失败样例沉淀 |

#### A/B 验收门槛（P1）

在同一 dataset、同一 config snapshot（除 rerank 开关外）下：

- `passRate` 提升或不下降
- `requires_citations` 通过率不下降
- P95 `latency_ms` 不恶化超过阈值（例如 +10%）
- regressions（PASS→FAIL）数量不高于阈值（例如 ≤2），且有可解释原因

### P1-3 反馈闭环

- **加到哪里**：新增 `feedback` 表 + API
- **实现**：点赞/点踩/评分（1–5）+ 可选文本；与 eval 的标签/失败样例联动

### P1-4 ReAct（强约束版，可选）

仅当你需要“探索式多步”时启用，务必限制步数/预算/工具白名单/总超时，并把每步 Observation 落日志/落库便于 eval 归因。

### P1-4b 多跳推理（Multi-hop reasoning：受控多步检索/工具链）

> **定义**：同一问题需要 ≥2 次“获取证据（检索/工具）→ 归纳/推断 → 再获取证据/再推断”才能完成的任务（例如：代码变更影响分析、PR review 证据追溯、故障定位链路）。
>
> **定位**：多跳是“研发助手更像 Agent”的关键能力，但也是**成本与黑盒 loop**放大器；因此只在 P1 受控开启，并必须通过 harness + compare 验收。

#### 加到哪里

- 优先作为 **ReAct 的受控变体**：在 `PLAN/RETRIEVE/TOOL/WRITE/GUARD` 的线性骨架不变前提下，允许 `RETRIEVE/TOOL` 段出现最多 \(N\) 次“再取证”（multi-hop），但每一步都必须写入 trace（见下）。
- 若使用 **Supervisor-Worker**（P1-1）：可将“第 1 跳定位范围”与“第 2 跳补证据”分配给不同 Worker，但仍禁止自由群聊与无界循环。

#### 实现约束（P1 强制）

- **限步（hard cap）**：`app.agent.max-hops=3`（或更小）；超过立即降级为 `behavior=clarify` 并给出“需要更多信息/缩小范围”的提示。
- **限时（hard cap）**：总超时沿用 `app.agent.total-timeout`；每步检索/工具沿用各自超时；不得无限重试。
- **工具白名单**：多跳仅允许调用“可幂等/可观测”的工具（如检索/查询类）；禁止在多跳中触发不可逆写入型工具（除非单独设计补偿与审计）。
- **失败降级**：任一 hop 的检索/工具失败必须进入稳定降级路径（不让整轮崩），并落 `error_code`/outcome。

#### 可观测（P1 强制；用于回归与归因）

- `meta` 需补（或在 `config_snapshot_json` 中体现）：
  - `hop_count`（实际 hops 数）
  - `hop_trace[]`（结构化事件；不含敏感原文）：`{ hop, stage, decision, reasons[], tool_outcome?, retrieve_hit_count? }`
  - `multi_hop_enabled=true/false`、`multi_hop_outcome=ok|capped|degraded`

#### harness 与验收（P1 强制）

- dataset 增加 `multi_hop/*` 标签用例（至少覆盖）：
  - `multi_hop/impact_analysis`
  - `multi_hop/root_cause`
  - `multi_hop/pr_review_evidence`
- 在同一 dataset、同一 config snapshot 下对比：
  - **多跳关闭** vs **多跳开启**：`passRate` 不下降（或提升），`UNKNOWN/TIMEOUT` 不显著恶化
  - regressions 必须可解释（可由 `hop_trace`/`policy_id` 定位）

### P1-5 evidence map + quote-only（安全/正确性增强）

> 这两项的验收与字段口径见 `plans/eval-upgrade.md`（P1-S1/P1-S2）。

- **evidence map**：在评测接口 `POST /api/v1/eval/chat` 响应中返回 `evidenceMap[]`，由服务端从“回答草稿 + 证据对象”生成（不是让 LLM 自由编造 sourceIds）。
- **quote-only**：对带 `guardrail/quote_only` 标签的 case：
  - 在 `meta.quote_only=true`
  - 输出时限制数字/关键实体必须来自 `sources[].snippet`（至少先做数字一致性），否则返回 `behavior=clarify|deny` 并带 `error_code`

### P1-6 DAG/状态机 + 并行工具（性能/复杂度里程碑）

- **前置条件**：eval P0 稳定 + 至少 2 轮 run 对比无 regressions
- **目标**：
  - DAG：把串行阶段升级为显式节点与分支（支持 replan、分支回放）
  - 并行：仅对互不依赖工具并行，并严格限总超时与取消传播
- **验收**：
  - `latency_ms`/（可选）`ttft_ms` 有显著改善
  - `passRate` 不下降，且 `error_code` 分布不恶化（无新增高比例 TIMEOUT/UNKNOWN）

### P1-7 长期记忆（user_profile + DST）与“写入治理”（隐私/错误累积的硬门槛）

> 反驳“抽 slots 入库就完事”的理想化做法：长期记忆必须先有**显式确认、可解释变更记录、可清空/导出/忘记**，否则不进入 P0。

- **启用条件（P1 验收前不得默认开启）**
  - `app.memory.long-term.enabled=true` 仅在通过下列验收后开启

#### 写入规则（必须）

- **显式确认写入**：只有用户明确表达“记住/以后都…/偏好是…”且通过解析器识别为 `MEMORY_WRITE_INTENT` 才允许写入
- **禁止隐式写入**：模型推断出的偏好不得直接落库

#### 变更可解释与可回滚（必须）

- 新增 `user_profile_events`（或等价事件表）记录：
  - `before/after`（字段级 diff）、`reason`（用户原话摘要）、`created_at`
- 支持：
  - 一键清空 profile（reset）
  - 导出 profile（export）
  - 按事件回滚（revert last N / revert by eventId）

#### 错误累积防护（必须）

- profile 字段写入必须带 `confidence`（规则来源：显式用户语句=high；否则拒绝写入）
- 每次使用 profile 进入 plan 前，在 `meta` 中输出“本轮使用了哪些 profile 字段”（可解释）

#### eval 证明收益（必须）

- dataset 增加 `memory/*` 标签 case：
  - `memory/write_confirmed`：用户显式写入后下一轮必须生效
  - `memory/no_implicit_write`：未显式写入时不得持久化
  - `memory/reset`：reset 后不得继续使用旧偏好
- run 对比：开启长期记忆前后 `passRate` 需提升或至少不下降，且不引入新的隐私/注入失败

---

## 与 eval 的对接（必须）

> **实现真源**：本仓库（`EvalChatController`、`EvalGatewayAuthFilter`、`AppEvalProperties` / `application.yml` 中 `app.eval.*`）。

### HTTP 网关（与 membership 分离）

- **所有** `POST /api/v1/eval/chat`（及同前缀 `/api/v1/eval/**` 受该 Filter 保护的路径）必须在请求头携带 **`X-Eval-Gateway-Key`**，且与服务端配置一致。
- **配置键**：`app.eval.gateway-key`（环境变量 **`APP_EVAL_GATEWAY_KEY`** 覆盖）。绑定类：`AppEvalProperties`（`@ConfigurationProperties(prefix="app.eval")`）。
- **未配置网关密钥**：服务端应对评测路径返回 **401**（错误体含 `EVAL_GATEWAY_NOT_CONFIGURED` 类提示），避免评测口误暴露在公网。
- **密钥错误/缺失**：**401**（`EVAL_GATEWAY_UNAUTHORIZED` 等）；须与 **`X-Eval-Token`**（参与 E7 `k_case` / `meta.retrieval_hit_id_hashes` 的 HMAC 材料）**语义分离**——二者不得混用为同一密钥。

### Membership / 证据头（与 eval-upgrade 一致）

- travel-ai 作为 eval 的一个 `target`：**baseUrl** + **`X-Eval-Token`** +（跑 E7 / citations 时）**`X-Eval-Target-Id`、`X-Eval-Dataset-Id`、`X-Eval-Case-Id`**
- 可选：**`X-Eval-Membership-Top-N`**（候选截断上限；服务端与 `AppAgentProperties` / 检索逻辑对齐，见实现仓 `EvalChatController`）

### 接口与契约

- 评测专用**非流式**接口：`POST /api/v1/eval/chat`，返回统一 JSON（见 `plans/eval-upgrade.md` 契约），并必须包含 `capabilities`。
- **`meta` 与超时对账**：响应含 `meta.agent_total_timeout_ms` / `meta.agent_tool_timeout_ms` / `meta.agent_llm_stream_timeout_ms` / `meta.agent_max_steps_configured`（与 `app.agent.*` 对齐）；Controller 写入 `latency_ms` 后可出现 `meta.agent_latency_budget_exceeded`。**整段墙钟**：服务端用 `app.agent.total-timeout` 包裹单次 stub 执行，超时返回 `behavior=clarify`、`error_code=AGENT_TOTAL_TIMEOUT`（见实现仓 `EvalChatService#buildTotalTimeoutStubResponse`）。
- **TOOL stub 上限**：`eval_tool_scenario=timeout` 时，`Future#get` 的等待上限为 `min(app.eval.tool-timeout-ms, app.agent.tool-timeout)`（毫秒，各自有下限保护），见 `EvalToolStageRunner`。

### 数据集与 CI

- 固定评测集：以 `docs/eval.md` 为雏形扩展到 20–50 条，并跑 baseline run 与改动 run 对比
- **GitHub Actions 对「真实 target」的远程全量回归**：依赖 **公网可达（或 self-hosted runner / 隧道）**；**安排在 P0+ 升级收口之后**，与 Vagent 对称，详见 `plans/eval-upgrade.md` **§P0+ 自动化 A0**。

---

## 附录（执行视图，P0 必读）

- **P0 两周 MVP / 落地改动地图 / 统一契约字段表 / error_code 与前缀对齐**：若上游 Vagent 仓库中有 `plans/p0-execution-map.md`，以该文为契约索引；本仓执行视图以 **`IMPLEMENTATION_MATRIX.md`** 与 **`UPGRADE_PLAN.md`** 为准。

