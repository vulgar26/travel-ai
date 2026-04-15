## P0+ S3 weekly snippet (travel-ai)

### Full run (frozen dataset)

- run_id: `run_82a88781aeab472586a4c5d6673b466d`
- report: `run.report.v1`
- total_cases: 32 (completed: 32)
- pass_rate: 1.0000 (pass: 32 / fail: 0 / skipped: 0)
- p95_latency_ms: 439
- error_code TopN: (none)

### Tag buckets (how to regenerate)

Run locally (PowerShell):

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

Required buckets (per `p0-plus-execution.md` §8.1):
- `attack/*`
- `rag/empty` and/or `rag/low_conf`

### Compare (base vs cand)

Example compare (base=`run_a5cb...` vs cand=`run_82a8...`):

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

