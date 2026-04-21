# 评测回放与断点恢复（设计草案 · Schema + 写路径）

**状态**：Flyway **`V3__eval_replay_checkpoint.sql`** 已落表；**`EvalCheckpointRepository`** + **`EvalChatService#maybePersistEvalCheckpoint`** 已在评测响应组装末尾 **UPSERT**（须请求体 **`conversation_id`** 非空且 plan 已解析；失败只打日志）。**读路径**已实现：会校验 plan 指纹，必要时返回 `EVAL_CHECKPOINT_PLAN_MISMATCH` / `EVAL_CHECKPOINT_RESUMED_EXHAUSTED`；并支持<strong>同一 query</strong> 复用证据快照以避免重复检索。  
**索引**：[`P1_HARNESS_GAP.md`](P1_HARNESS_GAP.md) §2「回放 / 断点恢复」。  
**开关**：`app.eval.checkpoint-persistence-enabled`（默认 **true**，见 `application.yml`）。

---

## 1. 要解决什么问题（大白话）

- **回放**：同一套题库、同一 `plan_raw`、同一配置指纹下，多次跑结果要对得上；需要 **`plan_raw` 的稳定哈希**（`plan_raw_sha256`）和可选的 **`config_snapshot_hash`** 对账。
- **断点恢复**：线性流水线（`PLAN → RETRIEVE → …`）跑到一半失败或超时，希望**不要从 0 重跑**，而是记下「已经走完的最后一个阶段」，下次从下一阶段接着试（评测与将来主线可共用思路）。
- **为什么先落库**：表结构先固定，避免后面 Java 模型与线上库来回改；写路径与 **meta 同源**（`stage_order` 最后一项、`plan` 的 SHA-256、`config_snapshot_hash`）。

---

## 2. 表 `eval_conversation_checkpoint` 各字段什么意思

| 列 | 含义 |
|----|------|
| **`conversation_id`** | 主键。与评测请求体 `conversation_id` / 主线会话 id 对齐（varchar，兼容 UUID 字符串）。**当前约定**：一行表示该会话的**最新**断点（更新覆盖）。 |
| **`plan_raw_sha256`** | 附录 E 风格 **effective plan JSON**（与 `PlanParseCoordinator` 输出一致）的 **SHA-256 小写 hex**（64 字符）。用于回放时确认「plan 没被偷偷换掉」。 |
| **`last_completed_stage`** | 最近一次**已完整结束**的线性阶段名（如 `PLAN`、`RETRIEVE`），与 `meta.stage_order` 大写枚举一致（取 `stage_order` 最后一项）。 |
| **`stage_index`** | 数值游标（0～32）：当前为 **`stage_order.size() - 1`**（与最后一阶段下标一致）。 |
| **`config_snapshot_hash`** | 可选；与评测 `meta.config_snapshot_hash` 同源时写入。 |
| **`detail`** | JSONB：当前写入 **`request_id`**、**`behavior`**、可选 **`error_code`**（**不含** query 原文），以及用于复用的 `query_sha256`、`evidence_*`（检索快照）与 `tool_*` / `eval_tool_scenario`（工具快照）。 |
| **`created_at` / `updated_at`** | 首次写入与最后更新时间。 |

**索引**：`plan_raw_sha256`、`updated_at DESC` —— 支持按指纹排查、按时间扫尾。

---

## 3. 与现有能力的关系

- **`meta.config_snapshot_hash` / `meta.config_snapshot_id`**：已落地；断点行里 **`config_snapshot_hash`** 为可选冗余，便于 join。
- **Redis `ChatMemory`**：仍管多轮消息内容；**本表不管消息正文**，只管「线性阶段进度 + plan 指纹」。
- **评测口 `POST /api/v1/eval/chat`**：在 **`EvalChatService`** 的 **`complete(..., effectivePlanJsonForCheckpoint)`** 末尾写库；**`@WebMvcTest` 切片无 `JdbcTemplate` Bean 时** `ObjectProvider<EvalCheckpointRepository>` 为空，自动跳过。

---

## 4. 建议的后续实现顺序（仍坚持小步）

1. ~~**`EvalCheckpointRepository`**（Jdbc）~~：**已完成**（`upsert` / `deleteByConversationId`）。  
2. ~~**评测路径写库**~~：**已完成**（`maybePersistEvalCheckpoint`；`app.eval.checkpoint-persistence-enabled`）。  
3. **回放契约**：请求体带 `conversation_id` 时，服务端校验 **`plan_raw` 哈希与库中一致**；若断点显示已结束则返回 `EVAL_CHECKPOINT_RESUMED_EXHAUSTED`；若哈希不一致返回 `EVAL_CHECKPOINT_PLAN_MISMATCH`。  
4. **证据复用（已落地）**：同一 `conversation_id` 下再次请求时，若 `sha256(query)` 与断点行 `detail.query_sha256` 一致，且 `detail.evidence_sources/hits` 存在，则直接复用，不再调用向量检索。  
5. **工具复用（已落地）**：同一 `conversation_id` 下再次请求时，若 `sha256(query)` 匹配且 `eval_tool_scenario` 相同，且 `detail.tool_*` 快照齐全，则直接复用 TOOL 结果，不再重复执行工具阶段。  
   - 可观测：命中复用时，响应 `meta.checkpoint_evidence_reused=true` / `meta.checkpoint_tool_reused=true`（只在命中时出现）。
   - 未命中可观测：当尝试复用但失败时返回 `meta.checkpoint_*_reuse_miss_reason`（例如 `query_hash_mismatch` / `scenario_mismatch` / `snapshot_missing`）。
6. **主线 SSE**：若产品需要「刷新后续航」，再评估是否共用本表或拆 `agent_conversation_checkpoint`。

---

## 5. 迁移与本地验证

- 启动应用或跑集成测时 Flyway 自动执行 **`V3__eval_replay_checkpoint.sql`**。  
- 集成测：**`TravelAiApplicationIntegrationTest#evalChatUpsertsCheckpointWhenConversationIdProvided`** 会写入一行并清理。  
- 手工校验：`SELECT * FROM eval_conversation_checkpoint LIMIT 10;`
