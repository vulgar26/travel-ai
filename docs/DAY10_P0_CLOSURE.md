# Day10 · C 线 P0 过线收敛

- **TODAY_TOKEN**：`<DATE>-C-D10`（替换为当日）
- **范围**：travel-ai-planner 作为 eval **target**（`POST /api/v1/eval/chat`），对照 `p0-dataset-v0.jsonl` 全量批跑结论做收敛，不替代组长对 **PASS Day10** 的最终签字。

---

## 1. 证据：pass_rate / 关键门槛（引用 report）

以下为一次已落库的 **`run.report.v1` 摘录**（用于归档；**合并 Day9 安全筛查后请重新跑 run 并替换本节数字**）。

| 字段 | 值 |
|------|-----|
| `report_version` | `run.report.v1` |
| `run_id` | `run_5f443a87115340058914367451d68b91` |
| `total_cases` / `completed_cases` | 32 / 32 |
| `pass_count` | 6 |
| `fail_count` | 26 |
| `pass_rate` | **0.1875**（18.75%） |
| `skipped_rate` | 0 |
| `p95_latency_ms` | 22 |
| **error_code TopN** | `UNKNOWN` × 21，`CONTRACT_VIOLATION` × 5 |

**markdown_summary（同次 run，可原样贴周报）：**

```text
# run.report v1 - `run_5f443a87115340058914367451d68b91`

- pass_rate: 0.1875 (denominator: total_cases)
- skipped_rate: 0.0000
- p95_latency_ms: 22 ms (nearest_rank_ceiling)

## error_code TopN
- UNKNOWN x 21
- CONTRACT_VIOLATION x 5
```

### 门槛是否「达标」（诚实结论）

- **批跑闭环**（eval 能建 dataset、import、起 run、调 target、出 report）：**达标**（32/32 completed）。
- **以 pass_rate 作为 P0 产品语义门槛**（多数 case 的 `behavior` 与数据集 `expected_behavior` 一致）：**当前未达标**（18.75%）。  
  主因是 target 仍为 **P0 stub + 局部策略（Day7 RAG 开关 / Day9 输入筛查）**，**未接**真实行程推理、RAG 命中、全量策略引擎；数据集大量 case 依赖「真业务」而非仅 HTTP 形状。

**PASS Day10（C 线 P0 结束）建议由组长在以下两种定义中选其一：**

1. **收敛型**：本文件 + 剩余项清单 + 修复策略已评审通过 → 结束 C 线 P0，**P1 再接业务与 eval 规则**。  
2. **数值型**：重新跑批后 pass_rate / 关键 error_code 达到组内书面阈值 → 才算 PASS。  

（若选 2，须同步更新 **eval 判定规则** 与 **target 能力声明**，避免「契约」与「语义」混评。）

---

## 2. 剩余 regressions → 修复点 → 风险

| 剩余 regression（来自 report / 机制） | 典型根因 | 修复策略（建议 Owner） | 风险 |
|----------------------------------------|----------|------------------------|------|
| **`UNKNOWN` 占多数** | 失败未映射到稳定 `error_code`；或 eval 期望字段/枚举与响应不一致；或 `behavior` 与 `expected_behavior` 不一致导致判失败但码未细化 | **Eval**：对 travel-ai 响应做结构化校验，失败时映射到 `CONTRACT_VIOLATION` / `BEHAVIOR_MISMATCH` 等，减少 UNKNOWN。**Target**：保证异常路径仍有完整顶层字段（见下行）。 | 映射过细会掩盖真实 bug；过粗则报表无信息量。 |
| **`CONTRACT_VIOLATION`** | 缺必填字段、类型不符、`tool.outcome` 枚举、snake_case、或某路径未带 `meta.stage_order` / `step_count` 等 | **travel-ai**：对照 `eval-upgrade.md` / `EvalChatResponse` 做契约单测 + 每条分支 JSON Schema 校验。**Eval**：打印具体 violation 子字段便于一次修完。 | 双端字段表若不同步会反复扯皮；需 SSOT。 |
| **数据集 `expected_behavior` 与 stub 默认 `answer` 不一致** | 批跑仅传 `question`，未带 `eval_rag_scenario` / 真 RAG / 真 clarify 逻辑 | **产品**：接真实 GUARD/CLARIFY/DENY。**Eval**：对「当前 target 声明不支持语义类断言」的 case 标 `SKIPPED_UNSUPPORTED` 或拆 suite。 | 用请求开关「假过」会违背「业务不为评测服务」原则。 |
| **RAG / clarify / deny 类 case 未覆盖** | 无向量命中、无 score 门控、无引用闭环 | **travel-ai**：接检索与引用字段；P0 与 eval 约定 `sources` / `meta` 字段。**Eval**：按 `capabilities` 降级断言。 | 引用伪造与安全策略工程量大；需与 Vagent 对齐 error_code。 |
| **工具类 case** | 仅 `eval_tool_scenario` 触发 stub；数据集 tool case 依赖编排是否注入该字段 | **Eval**：对 `tool_policy=stub` 的 case 在请求体注入与数据集一致的策略（若规格允许）。**travel-ai**：保持 TOOL 路径契约稳定。 | 注入策略与「纯黑盒用户问句」不一致时，应用 `SKIPPED` 或单独 `tool_stress` suite。 |
| **Day9 后仍可能出现的码** | `PROMPT_INJECTION_BLOCKED` / `TOOL_OUTPUT_INJECTION_QUERY_BLOCKED` 若 eval 未列入白名单可能仍 FAIL | **Eval**：将新码纳入 PASS 规则或按 attack suite 单独统计。**travel-ai**：保持码稳定、写在 `EvalSafetyErrorCodes`。 | 规则滞后导致「已修仍红」。 |

---

## 3. 本仓已完成的 P0 能力（便于对照「剩余项」）

- **契约**：`POST /api/v1/eval/chat` 非流式 JSON，snake_case，`latency_ms` 由 Controller 写入。  
- **可观测**：`meta.stage_order` / `step_count` / `replan_count`；Day5 plan parse / repair once；Day6 工具 stub；Day7 RAG 门控 stub；Day9 输入侧高置信安全短路。  
- **回归**：`EvalChatControllerTest` 等单测锁定主干契约。

---

## 4. 建议的下一步（P1 入口，非本文件门控）

1. 重跑全量 32 case，**替换 §1 中 report 数字**，并对比 TopN `error_code` 是否从 UNKNOWN 下沉到可行动码。  
2. 与 **eval Owner** 对齐：`CONTRACT_VIOLATION` 的明细字段是否在 `eval_result.debug` 可查。  
3. 与 **组长** 确认 Day10 采用「收敛签字」还是「pass_rate 阈值」门控。

---

## 5. 门控勾选（复制到周报）

- [ ] `run.report` 最新摘要已引用（含 `run_id`、`pass_rate`、TopN error_code）  
- [ ] 本节「剩余 regressions → 修复点 → 风险」已评审  
- [ ] **PASS Day10** 定义已与组长对齐并勾选  

**签字 / 日期**：________________
