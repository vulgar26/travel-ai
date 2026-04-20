# P1-0 Execution Harness：本仓 `meta` 对照与缺口（分步基线）

**SSOT**：`docs/travel-ai-upgrade.md` §「P1-0 harness 工程」。  
**本文件角色**：基线盘点与分步建议（P1-0 harness 的「作战地图」）；用于把 SSOT 拆成可合入的小 PR。

**代码真源**：`com.travel.ai.eval.dto.EvalChatMeta`、`EvalChatService`。

---

## 1. 已在评测 `meta` 中落地（可与 `run.report` / compare 联动）

下列字段在 `EvalChatMeta` 中已实现（JSON **snake_case**），且评测路径会按场景写入；非短路、非对应阶段时多为 `@JsonInclude.NON_NULL` 省略。

| 能力域 | `meta` 字段（示例） | 说明 |
|--------|---------------------|------|
| 阶段与步数 | `stage_order`, `step_count`, `replan_count` | P0 线性流水线；`replan_count` 恒 `0` |
| 超时对账 | `agent_total_timeout_ms`, `agent_tool_timeout_ms`, `agent_llm_stream_timeout_ms`, `agent_max_steps_configured`, `agent_latency_budget_exceeded` | 与 `app.agent.*` 对齐 |
| Plan 解析 | `plan_parse_attempts`, `plan_parse_outcome` | repair once 治理 |
| 工具可观测 | `tool_calls_count`, `tool_outcome`, `tool_latency_ms`, `tool_disabled_by_circuit_breaker`, `tool_rate_limited`, `tool_output_truncated` | 与 TOOL stub / 熔断 / 限流 对齐 |
| 门控 / RAG | `low_confidence`, `low_confidence_reasons`, `retrieve_hit_count` | 含 stub 场景 |
| E7 证据 | `retrieval_hit_id_hashes`, `retrieval_hit_id_hash_alg`, `retrieval_hit_id_hash_key_derivation`, `retrieval_candidate_limit_n`, `retrieval_candidate_total`, `canonical_hit_id_scheme` | membership 对账 |
| 安全短路 | `eval_safety_rule_id`（与 `low_confidence_reasons` 等） | 仅 SafetyGate 命中时 |
| Reflection stub | `recovery_action`, `self_check` | 受 `app.eval.reflection-meta-enabled` 控制 |

以上已覆盖 P1-0 文案中的大量「**可观测快照**」子集（阶段、工具、超时、plan、检索证据、安全归因）。

---

## 2. 相对 SSOT 仍缺或仅部分覆盖（后续小步候选）

| SSOT / 叙述项 | 当前状态 | 建议下一小步（按风险从低到高） |
|---------------|----------|----------------------------------|
| **`config_snapshot_json` / `config_snapshot_id`** | **已做（hash）**：新增 `meta.config_snapshot_hash`（含 `alg/scope`），覆盖 `app.agent.*` 与 `app.eval.*` 的白名单键；仍未输出明文 JSON | 后续如需可回放明细，再加小型 `meta.config_snapshot`（严格白名单，不含密钥） |
| **统一 `context_truncated`**（历史 / 检索 / 工具块总预算） | **已做（评测路径）**：新增 `meta.context_truncated` + `meta.context_truncation_reasons[]`（当前覆盖 `sources_snippet_truncated` 与 `tool_output_truncated`） | 后续可扩展到历史对话截断 / promptBase 截断等更“总预算”的场景 |
| **显式 token 计数**（`prompt_tokens` 等） | **部分落地（近似）**：新增 `meta.context_char_count` / `meta.context_token_estimate` 与组成项；并输出预算口径（`meta.context_budget_sources_snippet_max_chars=300`、`meta.context_budget_chars_per_token_estimate=4`）；尚未接入供应商真 token | 后续如需“真 token”，再接 tokenizer/供应商返回，并在文档中声明口径差异 |
| **回放 / 断点恢复**（`plan_raw_hash`、按 `conversationId` 恢复 stage） | **未**做 | 独立里程碑；先文档与表结构，再实现 |
| **`hop_trace[]` / multi-hop** | **未**做 | 属 P1-4b，与 harness 正交 |
| **DevLog / `policy_id` 决策事件库** | 部分能力由 **`eval_safety_rule_id`**、**`low_confidence_reasons`** 承担；**无**通用决策日志流 | 可扩展 `meta.policy_events[]`（结构化、无敏感原文） |

---

## 3. 推荐迭代顺序（与本仓库「每次一小步」一致）

1. 缺口盘点与链接进 `README` / `UPGRADE_PLAN` / `travel-ai-upgrade` —— **已完成**。  
2. **`context_truncated` + 原因列表**：仅评测路径、仅截断可客观判定处（如 `sources[].snippet` 达 300、或工具输出截断处统一 OR）—— **已完成**。  
3. **`config_snapshot_hash` 或小 JSON**：从 `Environment` / `AppAgentProperties` 序列化白名单键 —— **已完成（hash）**。  
4. **Token 或字符预算**：先落字符级近似（趋势/异常检测），后续再决定是否接 tokenizer 真值 —— **已完成（近似）**。

每步单独 PR：便于 `EvalChatControllerTest` 与 `run.report` 回归。
