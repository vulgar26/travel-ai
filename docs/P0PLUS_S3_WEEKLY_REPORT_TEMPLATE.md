## P0+ S3 weekly report (travel-ai)

### Full run (frozen dataset)

- dataset: `D:\Projects\Vagent\plans\datasets\p0-dataset-v0.jsonl`
- run_id: `________`
- report: `run.report.v1`
- total_cases: __ (completed: __)
- pass_rate: __
- p95_latency_ms: __
- error_code TopN: __

### Tag buckets (required)

- `attack/*`: __ / __
- `rag/empty`: __ / __
- `rag/low_conf`: __ / __

Re-generate buckets (PowerShell):

```powershell
$runId = "run_________"
$runJson = "D:\Projects\travel-ai-planner\eval_run_run_${runId}_results.json"
$ds = "D:\Projects\Vagent\plans\datasets\p0-dataset-v0.jsonl"
$out = "D:\Projects\travel-ai-planner\p0plus_s3_${runId}_buckets.md"

powershell -NoProfile -ExecutionPolicy Bypass -File D:\Projects\travel-ai-planner\scripts\p0plus_s3_bucket_report.ps1 `
  -RunResultsPath $runJson `
  -DatasetPath $ds `
  -OutMarkdownPath $out
```

### Compare (base vs cand)

- base run_id: `________`
- cand run_id: `________`
- pass_rate_delta: __
- regressions (PASS → FAIL) TopN: __

Re-generate compare (PowerShell):

```powershell
$base = "run_________"
$cand = "run_________"
$baseJson = "D:\Projects\travel-ai-planner\eval_run_run_${base}_results.json"
$candJson = "D:\Projects\travel-ai-planner\eval_run_run_${cand}_results.json"
$out = "D:\Projects\travel-ai-planner\p0plus_s3_compare_${base}_vs_${cand}.md"

powershell -NoProfile -ExecutionPolicy Bypass -File D:\Projects\travel-ai-planner\scripts\p0plus_s3_compare.ps1 `
  -BaseRunResultsPath $baseJson `
  -CandRunResultsPath $candJson `
  -OutMarkdownPath $out
```

