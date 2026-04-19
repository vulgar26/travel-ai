## Harness vs Eval（这套规范解决什么）

### 结论（最重要的一句）

- **eval**：负责“跑批 + 判分 + 产出 run 数据”（`run_id`、`run.report`、逐 case results）。
- **harness**：负责“把多次 run 的数据变成可运营的回归证据”（分桶、compare、周报模板、存档与门禁）。

换句话说：**eval 产出数据；harness 约定如何取数/处理/对比/存档。**

---

## 1) Harness 会不会“直接获取最新数据”？

### P0+ 当前形态（我们现在就是这样）

- harness **不直接调用 eval API**（不依赖 eval 服务在线）。
- harness **读取 eval 导出的落盘 JSON**（例如：`eval_run_run_*.json` / `run.report`），然后：
  - 计算 **tags 分桶**
  - 计算 **base vs cand compare**
  - 生成周报/日报可复制片段

### P1 形态（后续可选增强）

- 可以在 harness 脚本里增加“从 eval API 拉取 run results 并落盘”的步骤（自动化取数）。
- 但这不是必须；**先保证口径与产物稳定**，再谈“自动拉取”。

---

## 2) Harness 需要“系统/服务”吗？

- **不需要**。harness 可以是：
  - 一组脚本（PowerShell/Python）
  - 一组模板（周报/日报）
  - 一组门禁规则（PR 必须附 run+compare）

eval 是独立系统没问题：**eval 负责产 run；harness 负责把 run 变成可复盘证据**。

---

## 3) 我们在 travel-ai 仓库里约定的“最小 Harness”产物

### 3.1 必备脚本（P0+ S3）

- **tags 分桶**：`scripts/p0plus_s3_bucket_report.ps1`
  - 输入：某次 run 的 results JSON + 冻结 dataset JSONL
  - 输出：`p0plus_s3_<run_id>_buckets.md`
- **compare**：`scripts/p0plus_s3_compare.ps1`
  - 输入：base run results JSON + cand run results JSON
  - 输出：`p0plus_s3_compare_<base>_vs_<cand>.md`

### 3.2 模板（交付给组长/周报复制用）

- 周报模板：`docs/P0PLUS_S3_WEEKLY_REPORT_TEMPLATE.md`
- 周报可复制字段：[`docs/P0PLUS_S3_WEEKLY_REPORT_TEMPLATE.md`](P0PLUS_S3_WEEKLY_REPORT_TEMPLATE.md)（填 `run_id` 与指标后贴周报）

---

## 4) 强制门禁（团队协作规则）

### 4.1 任何“Agent 能力/工具治理/受控编排”的 PR 必须附

- **cand run**（全量 ≥30 case）：`run_id` + `run.report` 摘要
- **compare**：相对一个明确 base run 的 compare 摘要（至少包含）
  - `pass_rate_delta`
  - regressions（PASS->FAIL）的 `case_id` 列表（TopN 也可，但必须可追溯到全文）
- **tags 分桶**（至少包含）
  - `attack/*`
  - `rag/empty`
  - `rag/low_conf`

> 原则：禁止只报一个 pass_rate 百分比；必须能回答“哪里回退了、为什么回退、是否可接受”。

### 4.2 base run 的选择规则（避免口径漂移）

- 默认 base 选“上一轮主分支已验收”的 run（或组长指定的基线 run）。
- 必须在 PR/周报里明确写出：`base run_id` 与 `cand run_id`。

---

## 5) 本地运行标准流程（Windows PowerShell）

### 5.1 前置：准备两份文件

- 冻结 dataset：`D:\Projects\Vagent\plans\datasets\p0-dataset-v0.jsonl`
- 从 eval 导出并落盘：
  - `D:\Projects\travel-ai-planner\eval_run_run_<runId>_results.json`

### 5.2 生成 tags 分桶

```powershell
$runId = "run_82a88781aeab472586a4c5d6673b466d"
$runJson = "D:\Projects\travel-ai-planner\eval_run_run_${runId}_results.json"
$ds = "D:\Projects\Vagent\plans\datasets\p0-dataset-v0.jsonl"
$out = "D:\Projects\travel-ai-planner\p0plus_s3_${runId}_buckets.md"

powershell -NoProfile -ExecutionPolicy Bypass -File D:\Projects\travel-ai-planner\scripts\p0plus_s3_bucket_report.ps1 `
  -RunResultsPath $runJson `
  -DatasetPath $ds `
  -OutMarkdownPath $out
```

### 5.3 生成 compare（base vs cand）

```powershell
$base = "run_a5cb14dfdaa34a28927d7c65c4863dec"
$cand = "run_82a88781aeab472586a4c5d6673b466d"
$baseJson = "D:\Projects\travel-ai-planner\eval_run_run_${base}_results.json"
$candJson = "D:\Projects\travel-ai-planner\eval_run_run_${cand}_results.json"
$out = "D:\Projects\travel-ai-planner\p0plus_s3_compare_${base}_vs_${cand}.md"

powershell -NoProfile -ExecutionPolicy Bypass -File D:\Projects\travel-ai-planner\scripts\p0plus_s3_compare.ps1 `
  -BaseRunResultsPath $baseJson `
  -CandRunResultsPath $candJson `
  -OutMarkdownPath $out
```

---

## 6) 存档约定（可审计）

- `p0plus_s3_<run_id>_buckets.md`
- `p0plus_s3_compare_<base>_vs_<cand>.md`
- 周报/日报直接引用以上两个文件（或复制其内容）。

