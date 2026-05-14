# P0 数值门槛验收手册（eval `run.report`）

本文件把 [`../travel-ai-upgrade.md`](../travel-ai-upgrade.md) §「P0 完成门槛」「Plan 解析与修复一次治理」等**比例型门槛**，落成可执行的**核对步骤**：在 eval 平台对固定 dataset 跑完全量 run 后，如何用 `run.report`（及必要时 per-case 导出）逐项判断是否达标。  
**不替代**一次真实的全量 run：代码再完备，比例门槛也只能由**同版本 target + 固定题库**的聚合报告证明。

---

## 1. 适用范围与真源

| 内容 | 说明 |
| --- | --- |
| SSOT 阈值 | `docs/travel-ai-upgrade.md`（`CONTRACT_VIOLATION`、`UNKNOWN`、`TIMEOUT`、`TOOL_TIMEOUT`、`step_count`、墙钟超时、`plan_parse_*`、`replan_count` 等） |
| 聚合报告 | eval 实例导出的 **`run.report`**（字段名以你所用 eval 版本为准；本仓归档示例见 [`../DAY10_P0_CLOSURE.md`](../DAY10_P0_CLOSURE.md) §1：`pass_rate`、`error_code` TopN、`total_cases` / `completed_cases`） |
| 单条证据 | 每条 `POST /api/v1/eval/chat` 响应中的 `latency_ms`、`behavior`、`error_code`、`meta.*` |

**数据集规模**：SSOT 要求同一 dataset **≥30** case；换题库或改 `dataset_id` 后须**重跑**并更新登记（见 `travel-ai-upgrade.md`「可复现登记」）。

---

## 2. 跑完全量 run 后的核对清单

对一次已完成的全量 run，按下列顺序检查。分母默认使用 **`completed_cases`**（若平台对 `skipped` 另有约定，与 eval Owner 对齐后替换分母）。

### 2.1 协议与归因：`CONTRACT_VIOLATION = 0`

- **规则**：凡标为契约违反的 case 数必须为 **0**。
- **操作**：在 per-case 结果或 `error_code` 分桶中统计 `CONTRACT_VIOLATION`；或在 `run.report` 的契约类 bucket 中读取 fail 数。
- **本仓辅助**：`RetrievalMembershipHasherTest`、`EvalChatControllerTest` 对 E7 / `requires_citations` 路径的断言（**烟测**，不覆盖全题库）。

### 2.2 `UNKNOWN` 占比 ≤ **1%**

- **公式**：`(#(error_code == UNKNOWN 或等价未归因失败)) / completed_cases`。
- **操作**：用平台筛选器或导出 CSV 对 `error_code` 列计数；若 `UNKNOWN` 未出现在 TopN，仍应对全量列表做一次过滤，避免被「TopN 为空」掩盖。

### 2.3 超时噪声：`TIMEOUT` ≤ **2%**，`TOOL_TIMEOUT` ≤ **5%**

- **公式**：各 `#{error_code == TIMEOUT} / completed_cases`、`#{error_code == TOOL_TIMEOUT} / completed_cases`。
- **操作**：与 2.2 相同，按 `error_code` 聚合；若有子类型映射到同一码，以 eval 侧码表为准。

### 2.4 步数超限：`step_count > app.agent.max-steps` 占比 = **0%**

- **规则**：每条响应中 `meta.step_count` 不得大于配置的上限（响应内常见为 `meta.agent_max_steps_configured`，须与 `app.agent.max-steps` 一致）。
- **操作**：导出 `meta.step_count` 与上限列，筛选 `step_count > max`；计数必须为 **0**。

### 2.5 墙钟总耗时：`latency_ms > agent_total_timeout` 占比 ≤ **2%**

- **规则**：单次请求的墙钟 `latency_ms`（由 `EvalChatController` 写入）超过 `meta.agent_total_timeout_ms`（或与 `app.agent.total-timeout` 对齐的毫秒值）的 case 比例 ≤ **2%**。
- **操作**：导出两列比较；可同时查看 `meta.agent_latency_budget_exceeded`（若该 run 已写入）作为辅助信号。

### 2.6 禁止 replan：`meta.replan_count` ≤ **0**（本实现为恒 **0**）

- **操作**：抽样或全量检查 `meta.replan_count`；本仓库实现应**均为 0**。

### 2.7 Plan 解析比例

| 规则 | 操作 |
| --- | --- |
| `plan_parse_outcome=failed` 占比 ≤ **1%** | 对 `meta.plan_parse_outcome` 聚合 |
| `plan_parse_attempts=2` 占比 ≤ **10%** | 对 `meta.plan_parse_attempts` 聚合 |
| 凡 `failed` 的子集 | 逐条断言 `behavior=clarify` 且 `error_code=PARSE_ERROR`（与 SSOT 一致） |

### 2.8 串行工具与降级（定性 + 抽样）

- **`meta.tool_calls_count`** 与 `tool.used` 一致（按 SSOT）；可对 `tool/*` 标签 case 全检。
- **降级路径**：Plan 失败 / 检索空 / 工具超时等须仍有稳定 `error_code` 且 `behavior ∈ {answer, clarify, deny}`，无 HTTP 5xx / 挂死；可对 `error_code TopN` 与 FAIL 列表抽样打开 JSON 校验。

---

## 3. 本仓库离线可做的（契约烟测）

以下**不证明**比例门槛，只防止明显契约回归：

```bash
mvn test -Dtest=EvalChatControllerTest
```

重点覆盖：`plan_parse_outcome`（`success` / `repaired` / `failed`）、安全门控短路、部分 `meta` 必填字段等。

---

## 4. 归档与登记

- 过线后：将 `run_id`、`dataset_id`、`pass_rate`、`error_code TopN` 摘要写入 [`../DAY10_P0_CLOSURE.md`](../DAY10_P0_CLOSURE.md) 或周报；并在 `travel-ai-upgrade.md`「可复现登记」更新日期与 run 指针。
- 未达标：按 `error_code` → `case_id` → 单条 response 逐级下钻；区分 **target 实现**、**dataset 期望**、**eval 校验器** 三类根因后再改代码或改题。

---

## 5. 与手工表的关系

- [`../eval.md`](../eval.md)：面向 **SSE 主产品**的定性回归表。  
- **本手册**：面向 **eval 批跑 + `run.report`** 的**数值门槛**；二者互补，不可替代。
