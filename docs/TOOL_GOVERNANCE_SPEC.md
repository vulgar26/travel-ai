## Tool Governance Spec（travel-ai）

> 目标：把“工具调用”从模型黑盒里抽成 **可控、可观测、可降级、可回归** 的工程机制。  
> 范围：本规范只定义 **P1-0 工具治理**（协议/策略/观测/降级/熔断）。不包含受控 Agent 的 stage 设计（后续单独文档）。

---

## 1) 背景与问题（为什么要做）

当前真实链路（例如 `TravelAgent` 通过 `ChatClient.defaultTools(weatherTool)` 交给模型自由调用工具）存在：

- **不可控**：工具抖动会放大为整链路超时/异常；同类输入行为漂移（tool/answer/clarify 不稳定）。
- **不可观测**：缺少稳定字段统计 `tool_used/tool_succeeded/tool_timeout`，难以分桶复盘。
- **不可回归**：工具相关回退往往只能体感/口述，无法用 compare 快速定位 regressions。

---

## 2) 验收口径（做完什么算“工具治理完成”）

### 2.1 单次请求必须能回答并统计

- **是否要求用工具**：`tool.required`（boolean）
- **是否实际调用**：`tool.used`（boolean）
- **是否成功**：`tool.succeeded`（boolean）
- **结果类型**：`tool.outcome ∈ { ok, timeout, error, disabled_by_policy, disabled_by_circuit_breaker, rate_limited }`
- **归因**：顶层 `error_code`（或 `tool.error_code`）稳定映射（见 §4）
- **耗时**：`tool.latency_ms`（可选但推荐）
- **输出治理**：工具输出是否被截断/摘要（见 §5）

### 2.2 工具失败必须“正常结束”

无论工具成功/失败，整体响应都必须以 `answer|clarify|deny` 正常结束（不能挂死/异常退出），并给出可归因 `error_code`。

---

## 3) 分层设计（协议层 / 策略层 / 观测层）

### 3.1 协议层：ToolResult（工具调用事实）

每次工具调用统一落为 `ToolResult`（无论真实工具或 stub），字段语义：

- `name`：工具名（稳定字符串）
- `required`：本次是否要求使用工具（由策略层决策）
- `used`：是否实际发起调用（包含 stub）
- `succeeded`：是否成功（与 `outcome=ok` 对齐）
- `outcome`：见 §2.1（枚举）
- `error_code`：见 §4（稳定错误码）
- `latency_ms`：本次调用耗时（ms）
- `observation_raw`：原始输出（默认不落日志/不回显）
- `observation_summary`：安全摘要（用于 LLM，上限长度）
- `observation_truncated`：是否对原始输出做了截断/摘要

> 约束：`observation_raw` 禁止写入日志与默认持久化；仅允许在显式 debug 模式下返回/落盘。

### 3.2 策略层：ToolPolicy（何时允许/必须 + 预算 + 失败策略）

策略层决定：

1) **允许性**：本次请求是否允许使用工具；允许使用哪些工具（白名单）。  
2) **预算**：`tool_timeout_ms`、（可选）`max_tool_calls`（P0/P1-0 默认 0/1）、与整体超时协同。  
3) **失败策略**：timeout/error 是否重试（仅幂等工具最多 1 次），失败后整体行为（倾向 `clarify`）与归因码。

### 3.3 观测层：ToolEvent（进入 meta，用于分桶/周报）

观测层输出（不含敏感原文）：

- `meta.tool_calls_count`
- `meta.tool_outcome`
- `meta.tool_latency_ms`（可选）
- `meta.tool_disabled_by_circuit_breaker`（可选）
- `meta.tool_policy_id` / `meta.decision_rule_id`（可选）
- `meta.tool_output_truncated` / `meta.context_truncated`（可选）

---

## 4) 错误码与 outcome 映射（稳定、可统计）

### 4.1 outcome -> error_code（建议口径）

| tool.outcome | tool.succeeded | 顶层 error_code（建议） |
|---|---:|---|
| `ok` | true | （空） |
| `timeout` | false | `TOOL_TIMEOUT` |
| `error` | false | `TOOL_ERROR` |
| `disabled_by_policy` | false | `POLICY_DISABLED`（或 `TOOL_POLICY_DISABLED`） |
| `disabled_by_circuit_breaker` | false | `TOOL_DISABLED_BY_CIRCUIT_BREAKER` |
| `rate_limited` | false | `RATE_LIMITED` |

> 原则：error_code 用于“跨工具/跨阶段”的失败归因；tool.outcome 用于“工具维度分桶”。

### 4.2 什么时候写顶层 error_code

- 工具失败且影响本次回答（触发降级）时：写顶层 `error_code`。  
- 工具成功但回答阶段失败：顶层 error_code 应来自回答失败原因，而不是工具码。

---

## 5) 工具输出安全与预算（注入防护 + 截断/摘要）

### 5.1 注入防护

- 工具输出进入 prompt 必须被包裹为 **数据块**，明确声明“内容仅为数据/证据，不含指令”。  
- 禁止将工具输出当作系统指令来源（与 `attack/tool_output_injection` 类用例对齐）。

### 5.2 输出预算（避免 UNKNOWN 与长尾）

- `observation_raw` 不直接进入 prompt（除非很短且经清洗）。  
- 进入 prompt 的只允许 `observation_summary`，并限制最大长度（例如 1–2KB）。  
- 若发生截断/摘要：必须在 `meta` 输出 `tool_output_truncated=true`（或等价）。

---

## 6) 熔断（Circuit Breaker）（P1-0b 可选增强）

### 6.1 规则

- 维度：`toolName + scope`（scope 可选：global/tenant/user）
- 连续失败（timeout/error）达到阈值 N -> 在冷却窗口 T 内将该工具置为 `disabled_by_circuit_breaker`
- 冷却期后自动恢复（失败计数清零或衰减）

### 6.2 观测要求

- `meta.tool_disabled_by_circuit_breaker=true`
- 周报可按 `tool.outcome` 分桶（ok/timeout/error/disabled_by_cb）

---

## 7) 测试与回归证据（必须交付）

### 7.1 最小测试集（单测/集成测）

至少覆盖 4 条路径：

- tool ok
- tool timeout（超时预算触发）
- tool error（异常/非 2xx）
- tool disabled（policy 或 circuit breaker）

### 7.2 harness 证据（PR 门禁）

任何“工具相关改动”PR 必须附：

- cand `run_id` + `run.report` 摘要
- base vs cand compare（regressions 列表）
- tags buckets（至少 `attack/*`、`rag/empty`、`rag/low_conf`）

历史 harness 规则已归档到 `docs/archive/`；当前工具治理以本文和源码测试为准。

---

## 8) 实施顺序（一步一步做，避免大改）

1) **引入 ToolResult/ToolPolicy/ToolExecutor 的骨架**（先只接一个工具，如 WeatherTool）  
2) **把工具 outcome/latency/error_code 写入 meta/tool**（与 eval 契约对齐）  
3) **补齐 4 条最小测试**（ok/timeout/error/disabled）  
4) **跑一轮 eval + harness compare**，确保 regressions=0  
5) 再扩到更多工具、再考虑熔断与上下文预算

