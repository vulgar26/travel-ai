# P1-0 Execution Harness：本仓 `meta` 对照与缺口（分步基线）

**SSOT**：`docs/travel-ai-upgrade.md` §「P1-0 harness 工程」。  
**本文件角色**：基线盘点与分步建议（P1-0 harness 的「作战地图」）；用于把 SSOT 拆成可合入的小 PR。  
**回放断点设计（schema 先行）**：[`EVAL_REPLAY_CHECKPOINT.md`](EVAL_REPLAY_CHECKPOINT.md)。

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
| 策略轨迹 | `policy_events[]`（`policy_type` / `stage` / `behavior` / `rule_id` / `error_code`） | 门控或确定性策略短路时（无事件则省略） |
| Reflection stub | `recovery_action`, `self_check` | 受 `app.eval.reflection-meta-enabled` 控制 |
| 配置快照 | `config_snapshot_hash`、`config_snapshot_hash_alg`、`config_snapshot_hash_scope`、`config_snapshot_id` | `id` 与 hash 同步出现；可选明文 **`config_snapshot`** 见下行 |
| 配置快照（可选明文） | `config_snapshot`（`Map`，与 hash 同源键） | 仅 **`app.eval.config-snapshot-meta-enabled=true`** |
| 上下文截断汇总 | `context_truncated`, `context_truncation_reasons[]` | 原因码见 §2「统一 `context_truncated`」行（snippet / 工具 / 检索上限 / 改写单行等） |

以上已覆盖 P1-0 文案中的大量「**可观测快照**」子集（阶段、工具、超时、plan、检索证据、安全归因）。

---

## 2. 相对 SSOT 仍缺或仅部分覆盖（后续小步候选）

| SSOT / 叙述项 | 当前状态 | 建议下一小步（按风险从低到高） |
|---------------|----------|----------------------------------|
| **`config_snapshot_json` / `config_snapshot_id`** | **已做**：`meta.config_snapshot_hash`（含 `alg/scope`）+ **`meta.config_snapshot_id`**（稳定引用，形如 `travel-ai:config-snapshot/v1/sha256/<hash>`，与 hash 一一对应）；当 **`app.eval.config-snapshot-meta-enabled=true`** 时另写入 **`meta.config_snapshot`** 明文键值（默认 **false** 防 meta 膨胀） | 后续：请求体回显校验、或外存 blob 与 id 解耦后再扩展 |
| **统一 `context_truncated`**（历史 / 检索 / 工具块总预算） | **已做（评测路径）**：`meta.context_truncated` + `meta.context_truncation_reasons[]` 覆盖 `sources_snippet_truncated`、`tool_output_truncated`、**`retrieval_candidates_capped`**（去重后命中数大于 `retrieval_candidate_limit_n`）、**`retrieval_query_line_truncated`**（非 `EVAL` 模式下 `QueryRewriter` 单行超长截断） | 后续：历史多轮记忆截断、主线 promptBase 等（与评测口解耦或另开字段） |
| **显式 token 计数**（`prompt_tokens` 等） | **组合模式落地**：默认离线近似（`meta.context_*` + `token_source=estimate`）；当请求 `llm_mode=real` 且服务端 `app.eval.llm-real-enabled=true` 时，评测口触发一次真实 LLM 调用并 best-effort 写入 `meta.prompt_tokens/completion_tokens/total_tokens`（`token_source=provider`）。默认还要求请求体 `eval_tags` 命中配置前缀（默认 `cost/`，见 `app.eval.llm-real-require-tag-match` / `llm-real-required-tag-prefixes`），否则跳过探针并写 `provider_usage_failure_reason=tag_gate_no_tags|tag_gate_no_match`。另：`meta.provider_usage_available` / `timeout|no_usage|error|no_client` 等。主产品（SSE）也会在日志输出 `[usage]`（反射提取，失败则回退估算）。 | **已定稿**：跑法/成本/合规见 [**`LLM_REAL_USAGE_RUNBOOK.md`**](LLM_REAL_USAGE_RUNBOOK.md) |
| **回放 / 断点恢复**（`plan_raw_hash`、按 `conversationId` 恢复 stage） | **Schema 已落**（Flyway **`V3__eval_replay_checkpoint.sql`**，表 **`eval_conversation_checkpoint`**）；**应用层未**接（无 Repository / 评测写路径） | 下一小步：`EvalCheckpointRepository` + `EvalChatService` 稳定点 UPSERT；说明见 [**`EVAL_REPLAY_CHECKPOINT.md`**](EVAL_REPLAY_CHECKPOINT.md) |
| **`hop_trace[]` / multi-hop** | **未**做 | 属 P1-4b，与 harness 正交 |
| **DevLog / `policy_id` 决策事件库** | 部分能力由 **`eval_safety_rule_id`**、**`low_confidence_reasons`** 承担；另已追加 **`meta.policy_events[]`**（结构化、无敏感原文；覆盖 SafetyGate / QuerySafety / BehaviorPolicy / RAG 门控 / TOOL 阶段归因） | 后续：如需与外部 DevLog id 强绑定，再引入 `policy_id` 或外存引用 |

---

## 3. 推荐迭代顺序（与本仓库「每次一小步」一致）

1. 缺口盘点与链接进 `README` / `UPGRADE_PLAN` / `travel-ai-upgrade` —— **已完成**。  
2. **`context_truncated` + 原因列表**：仅评测路径、仅截断可客观判定处 —— **已完成**（含 `sources_snippet_truncated`、`tool_output_truncated`、**`retrieval_candidates_capped`**、**`retrieval_query_line_truncated`**；见 `EvalChatControllerTest`）。  
3. **`config_snapshot_hash` 或小 JSON**：从 `Environment` / `AppAgentProperties` 序列化白名单键 —— **已完成（hash）**。  
4. **Token 或字符预算**：先落字符级近似（趋势/异常检测），后续再决定是否接 tokenizer 真值 —— **已完成（近似）**。  
5. **可选 `meta.config_snapshot` 明文键值**：与 `config_snapshot_hash` 同源白名单，**`app.eval.config-snapshot-meta-enabled`** 控制 —— **已完成**（见 `EvalChatConfigSnapshotMetaMvcTest`）。  
6. **`meta.policy_events[]`**：SafetyGate / QuerySafety / BehaviorPolicy / RAG 门控 / TOOL 阶段等短路点写入结构化事件 —— **已完成**（见 `EvalChatPolicyEventsMvcTest`）。  
7. **`meta.config_snapshot_id`**：与 `config_snapshot_hash` 同源、带版本前缀的稳定字符串 id —— **已完成**（契约见 `EvalChatControllerTest#metaConfigSnapshotHash_presentAndStableFields`）。  
8. **`context_truncation_reasons` 扩展（检索）**：候选条数上限、改写单行截断 —— **已完成**（`QueryRewriter#rewriteWithOutcome` + `EvalChatService#stampContextTruncationOnMeta`）。  
9. **回放断点表结构**：Flyway V3 + 设计说明 —— **已完成（仅 schema + 文档）**（见 **`docs/eval/EVAL_REPLAY_CHECKPOINT.md`**）。

每步单独 PR：便于 `EvalChatControllerTest` 与 `run.report` 回归。
