# P1-0 Execution Harness：本仓 `meta` 对照与缺口（分步基线）

**SSOT**：`docs/travel-ai-upgrade.md` §「P1-0 harness 工程」。  
**本文件角色**：**第 1 小步**——只做**盘点与分步建议**，不修改运行时行为；便于后续 PR 逐项闭合「可观测快照」缺口。

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
| **`config_snapshot_json` / `config_snapshot_id`** | `meta` **无**整段配置快照；仅有分散超时等字段 | 新增可选 `meta.config_snapshot`（**小 JSON**：`app.agent.*` 子集 + `app.eval.*` 关键键），或仅 **`config_snapshot_hash`**（SHA-256 配置串）避免体积与泄密 |
| **统一 `context_truncated`**（历史 / 检索 / 工具块总预算） | 仅有 **`tool_output_truncated`**；**无**「整段 prompt 因预算被截断」总开关 | 在 `EvalChatService` 组装进模型前的文本路径上统计截断，写 `meta.context_truncated` + 可选 `context_truncation_reasons[]` |
| **显式 token 计数**（`prompt_tokens` 等） | **未**接入 tokenizer；日志有 perf，**无**稳定 `meta` 数字 | 依赖 Spring AI / 供应商 API 若可取得则写入；否则用 **字符预算近似** + 文档声明误差 |
| **回放 / 断点恢复**（`plan_raw_hash`、按 `conversationId` 恢复 stage） | **未**做 | 独立里程碑；先文档与表结构，再实现 |
| **`hop_trace[]` / multi-hop** | **未**做 | 属 P1-4b，与 harness 正交 |
| **DevLog / `policy_id` 决策事件库** | 部分能力由 **`eval_safety_rule_id`**、**`low_confidence_reasons`** 承担；**无**通用决策日志流 | 可扩展 `meta.policy_events[]`（结构化、无敏感原文） |

---

## 3. 推荐迭代顺序（与本仓库「每次一小步」一致）

1. **（本文档）** 缺口盘点与链接进 `README` / `UPGRADE_PLAN` / `travel-ai-upgrade` —— **当前 commit**。  
2. **`context_truncated` + 原因列表**：仅评测路径、仅截断可客观判定处（如 `sources[].snippet` 已达 300、或工具输出截断处统一 OR）。  
3. **`config_snapshot_hash` 或小 JSON**：从 `Environment` / `AppAgentProperties` 序列化白名单键。  
4. **Token 或字符预算**：与产品确认是否必须 tokenizer 真值。

每步单独 PR：便于 `EvalChatControllerTest` 与 `run.report` 回归。
