# 评测回放与断点恢复（设计草案 · Schema + 写路径）

**状态**：Flyway **`V3__eval_replay_checkpoint.sql`** 已落表；**`EvalCheckpointRepository`** + **`EvalChatService#maybePersistEvalCheckpoint`** 已在评测响应组装末尾 **UPSERT**（须请求体 **`conversation_id`** 非空且 plan 已解析；失败只打日志）。**读路径 / 续跑校验**仍待下一小步。  
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
| **`detail`** | JSONB：当前写入 **`request_id`**、**`behavior`**、可选 **`error_code`**（**不含** query 原文）。 |
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
3. **回放契约**：请求体带 `conversation_id` 时，服务端校验 **`plan_raw` 哈希与库中一致**后再「从 `stage_index+1` 续跑」（与 `P1_HARNESS_GAP` SSOT 对齐）。  
4. **主线 SSE**：若产品需要「刷新后续航」，再评估是否共用本表或拆 `agent_conversation_checkpoint`。

---

## 5. 迁移与本地验证

- 启动应用或跑集成测时 Flyway 自动执行 **`V3__eval_replay_checkpoint.sql`**。  
- 集成测：**`TravelAiApplicationIntegrationTest#evalChatUpsertsCheckpointWhenConversationIdProvided`** 会写入一行并清理。  
- 手工校验：`SELECT * FROM eval_conversation_checkpoint LIMIT 10;`
