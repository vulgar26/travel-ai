# Day10 · C 线 P0 过线收敛

- **TODAY_TOKEN**：`<DATE>-C-D10`（替换为当日）
- **范围**：travel-ai-planner 作为 eval **target**（`POST /api/v1/eval/chat`），对照 `p0-dataset-v0.jsonl` 全量批跑结论做收敛，不替代组长对 **PASS Day10** 的最终签字。

---

## 1. 证据：pass_rate / 关键门槛（引用 report）

以下为 **2026-04-18** 已落库的 **`run.report.v1` 摘录**（含 **E7 hashed membership** 修复后重跑；用于归档）。

| 字段 | 值 |
|------|-----|
| `report_version` | `run.report.v1` |
| `run_id` | `run_6106023bf5354e3089cf1d8b7c4421b4` |
| `dataset_id`（eval 实例） | `ds_0d30f48d494443a096e281c7addba519` |
| `total_cases` / `completed_cases` | 32 / 32 |
| `pass_count` / `fail_count` | 32 / 0 |
| `pass_rate` | **1.0** |
| `skipped_rate` | 0 |
| `p95_latency_ms` | 328 |
| **error_code TopN** | **（无）** |

**markdown_summary（同次 run，可原样贴周报）：**

```text
# run.report v1 - `run_6106023bf5354e3089cf1d8b7c4421b4`

- pass_rate: 1.0000 (denominator: total_cases)
- skipped_rate: 0.0000
- p95_latency_ms: 328 ms (nearest_rank_ceiling)

## error_code TopN
(none)
```

### 门槛是否「达标」（诚实结论）

- **批跑闭环**（eval 能建 dataset、import、起 run、调 target、出 report）：**达标**（32/32 completed）。
- **契约与引用闭环（eval-upgrade.md E7）**：**达标**。target 在带 `X-Eval-Token` + `X-Eval-Target-Id` + `X-Eval-Dataset-Id` + `X-Eval-Case-Id` 的请求上返回非空 `meta.retrieval_hit_id_hashes[]` 及 `retrieval_candidate_limit_n` / `retrieval_candidate_total` / `canonical_hit_id_scheme`；`requires_citations` 路径上 eval 侧 `membership_ok: true`（`hashes_v1`）。
- **以 pass_rate 作为 P0 语义门槛**（`expected_behavior` 一致）：**本 run 数值达标**（100%）。实现仍以 **评测口 stub + 策略分支** 为主；主产品 SSE 与 `travel-ai-upgrade.md` 全文 P0（如主链路 `write→guard` 顺序、隐私整节）须与组长 **PASS Day10** 定义分开验收。

**PASS Day10（C 线 P0 结束）** 仍建议组长在「收敛签字」与「数值型门槛」中二选一留痕；本文件 §1 可作为 **数值型** 证据附件。

---

## 2. 剩余 regressions → 修复点 → 风险

| 剩余 regression（来自 report / 机制） | 典型根因 | 修复策略（建议 Owner） | 风险 |
|----------------------------------------|----------|------------------------|------|
| ~~**`CONTRACT_VIOLATION`（membership）**~~ | ~~`requires_citations` 路径缺 `meta.retrieval_hit_id_hashes`~~ | ~~**travel-ai**：按 `eval-upgrade.md` E7 实现 HMAC 与 `X-Eval-*` header 派生 `k_case`~~ **已在 2026-04-18 合入并验证（见 §1 run）。** | — |
| **`UNKNOWN` 占多数**（历史 run） | 失败未映射到稳定 `error_code`；或 eval 期望与响应不一致 | **Eval**：结构化校验与码表映射。**Target**：异常路径仍带完整顶层字段。 | 过粗/过细权衡。 |
| **主产品 vs 评测口** | SSE `TravelAgent` 与 `/api/v1/eval/chat` 能力不一致 | **产品**：里程碑 **A** 已对齐「固定阶段顺序」骨架；**meta/引用/GUARD 真规则** 仍须与 eval SSOT 收敛（见 `travel-ai-upgrade.md`）。 | 混评契约与语义。 |
| **数据集 `expected_behavior` 与真业务** | stub 不覆盖真实规划推理 | **产品 / eval**：能力声明、SKIP 规则或拆 suite。 | 「假过」风险。 |

---

## 3. 本仓已完成的 P0 能力（便于对照「剩余项」）

- **契约**：`POST /api/v1/eval/chat` 非流式 JSON，snake_case，`latency_ms` 由 Controller 写入。  
- **可观测**：`meta.stage_order` / `step_count` / `replan_count`；Day5 plan parse / repair once；Day6 工具 stub；Day7 RAG 门控 stub；Day9 输入侧高置信安全短路。  
- **E7 hashed membership**：`RetrievalMembershipHasher` + `EvalMembershipHttpContext`；响应 `meta.retrieval_hit_id_hashes[]` 及 `retrieval_hit_id_hash_alg` / `retrieval_hit_id_hash_key_derivation` / `retrieval_candidate_*` / `canonical_hit_id_scheme`（与 `D:\Projects\Vagent\plans\eval-upgrade.md` 一致）。  
- **回归**：`EvalChatControllerTest`（含 membership 单测）、`RetrievalMembershipHasherTest`。
- **主线 P0-1 编排骨架（里程碑 A，2026-04-18）**：`TravelAgent` 内 `runLinearStages` 固定串行 `PLAN → RETRIEVE → TOOL → GUARD → WRITE`（`MainAgentTurnContext` 承载阶段产物）；`PLAN` / `GUARD` 当前为占位；受控天气工具仍在 `TOOL`。**验收留痕（手工）**：`POST /auth/login` 取 JWT 后 `GET /travel/chat/{conversationId}?query=...`；日志中同一 `requestId` 下 `[stage]` 顺序完整；无「天气」意图时 `TOOL done` 可为 0ms；无知识命中时「最终 prompt 字符数」与用户 query 字面长度一致；SSE 仍为引用块首包 + 正文流 + `comment` 心跳。**说明**：`WRITE` 阶段 `doOnNext` 等跑在 Reactor `boundedElastic` 上时，日志 pattern 里 `%X{requestId}` 可能为空（MDC 未跨线程传播），但 `[perf]` 行内仍显式打印 `requestId=`，不挡本里程碑验收。

---

## 4. 建议的下一步（P1 入口，非本文件门控）

1. **里程碑 B（主链路）**：统一 `app.agent.total-timeout` / `tool-timeout` / `max-steps` 与降级矩阵收口；可选 **Reactor MDC 传播** 便于全链路 trace；`GUARD` 从 noop 演进为真实门控（引用/置信/空命中策略与升级文档对齐）。主线与 `POST /api/v1/eval/chat` 的 `meta`/契约继续对齐，见 `Vagent/plans/travel-ai-upgrade.md`。  
2. 与 **eval Owner** 对齐：`dataset_id` 变更时 §1 须重跑并重贴 report。  
3. 与 **组长** 确认 Day10：本 §1 是否足以勾选 **PASS Day10（C 线 / 数值型）**。

---

## 5. 门控勾选（复制到周报）

- [x] `run.report` 最新摘要已引用（含 `run_id`、`pass_rate`、TopN error_code）— 见 **§1 `run_6106023…`**  
- [x] **里程碑 A**：`TravelAgent` 固定线性阶段已落地并完成手工验收留痕（见 **§3** 最后一条）  
- [ ] 本节「剩余 regressions → 修复点 → 风险」已评审（主产品/UNKNOWN 等）  
- [ ] **PASS Day10** 定义已与组长对齐并勾选  

**签字 / 日期**：________________
