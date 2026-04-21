# 评测回放与断点恢复（设计草案 · Schema 先行）

**状态**：Flyway **`V3__eval_replay_checkpoint.sql`** 已落表；**应用层尚未接入** `EvalChatService` / `TravelAgent`（下一小步再接线）。  
**索引**：[`P1_HARNESS_GAP.md`](P1_HARNESS_GAP.md) §2「回放 / 断点恢复」。

---

## 1. 要解决什么问题（大白话）

- **回放**：同一套题库、同一 `plan_raw`、同一配置指纹下，多次跑结果要对得上；需要 **`plan_raw` 的稳定哈希**（`plan_raw_sha256`）和可选的 **`config_snapshot_hash`** 对账。
- **断点恢复**：线性流水线（`PLAN → RETRIEVE → …`）跑到一半失败或超时，希望**不要从 0 重跑**，而是记下「已经走完的最后一个阶段」，下次从下一阶段接着试（评测与将来主线可共用思路）。
- **为什么先落库**：表结构先固定，避免后面 Java 模型与线上库来回改；**不写业务代码**也能让 DBA / 评测平台先对齐字段名。

---

## 2. 表 `eval_conversation_checkpoint` 各字段什么意思

| 列 | 含义 |
|----|------|
| **`conversation_id`** | 主键。与评测请求体 `conversation_id` / 主线会话 id 对齐（varchar，兼容 UUID 字符串）。**当前约定**：一行表示该会话的**最新**断点（更新覆盖）。 |
| **`plan_raw_sha256`** | 附录 E 风格 `plan_raw` 的 **SHA-256 小写 hex**（64 字符）。用于回放时确认「plan 没被偷偷换掉」。 |
| **`last_completed_stage`** | 最近一次**已完整结束**的线性阶段名（如 `PLAN`、`RETRIEVE`），与 `meta.stage_order` 大写枚举一致。 |
| **`stage_index`** | 可选数值游标（0～32）：与 `stage_order` 下标或内部流水线步号对齐的具体数值，**由应用层定义**；默认 0。 |
| **`config_snapshot_hash`** | 可选；与评测 `meta.config_snapshot_hash` 同源时可写入，便于「配置 + plan」联合对账。 |
| **`detail`** | JSONB 扩展位：例如 `request_id`、工具 outcome 摘要键、**不包含**用户 query 原文（隐私与体积）。 |
| **`created_at` / `updated_at`** | 首次写入与最后更新时间。 |

**索引**：`plan_raw_sha256`、`updated_at DESC` —— 支持按指纹排查、按时间扫尾。

---

## 3. 与现有能力的关系

- **`meta.config_snapshot_hash` / `meta.config_snapshot_id`**：已落地；断点行里 **`config_snapshot_hash`** 为可选冗余，便于 join。
- **Redis `ChatMemory`**：仍管多轮消息内容；**本表不管消息正文**，只管「线性阶段进度 + plan 指纹」。
- **评测口 `POST /api/v1/eval/chat`**：后续可在 `complete(...)` 或短路返回前 **UPSERT** 本表（需注入 `JdbcTemplate` / Repository）；读路径可用于「带 `conversation_id` 的续跑」用例。

---

## 4. 建议的后续实现顺序（仍坚持小步）

1. **`EvalCheckpointRepository`**（Jdbc）：`upsertCheckpoint` / `findByConversationId`。  
2. **评测路径**：在 `EvalChatService` 成功跑完 `EvalLinearAgentPipeline` 或稳定短路点前，写入/更新一行（**失败策略**：写库失败只打日志，不影响 eval HTTP）。  
3. **回放契约**：请求体可选带 `conversation_id` + 服务端校验 `plan_raw` 哈希与库中一致后再续跑（与 `P1_HARNESS_GAP` SSOT 对齐）。  
4. **主线 SSE**：若产品需要「刷新后续航」，再评估是否共用本表或拆 `agent_conversation_checkpoint`。

---

## 5. 迁移与本地验证

- 启动应用或跑集成测时 Flyway 自动执行 **`V3__eval_replay_checkpoint.sql`**。  
- 手工校验：`SELECT * FROM eval_conversation_checkpoint LIMIT 1;`（空表为正常，直至应用接线）。
