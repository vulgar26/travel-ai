param(
  [Parameter(Mandatory = $true)]
  [string]$RunResultsPath,

  [Parameter(Mandatory = $true)]
  [string]$DatasetPath,

  [Parameter(Mandatory = $false)]
  [string]$OutMarkdownPath
)

$ErrorActionPreference = "Stop"

function Read-Json([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { throw "File not found: $path" }
  Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json
}

function Read-JsonLines([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { throw "File not found: $path" }
  Get-Content -LiteralPath $path -Encoding utf8 | Where-Object { $_ -and $_.Trim().Length -gt 0 } | ForEach-Object { $_ | ConvertFrom-Json }
}

$run = Read-Json $RunResultsPath
$cases = Read-JsonLines $DatasetPath

$caseById = @{}
foreach ($c in $cases) {
  $caseById[$c.case_id] = $c
}

$rows = @()
foreach ($r in $run.results) {
  $cid = $r.case_id
  $c = $caseById[$cid]
  if (-not $c) {
    throw "Dataset missing case_id referenced by run results: $cid"
  }
  $tags = @()
  if ($c.tags) { $tags = @($c.tags) }
  $rows += [pscustomobject]@{
    case_id      = $cid
    verdict      = $r.verdict
    error_code   = $r.error_code
    latency_ms   = $r.latency_ms
    expected     = $c.expected_behavior
    tool_policy  = $c.tool_policy
    requires_citations = [bool]$c.requires_citations
    tags         = $tags
  }
}

function Bucket([string]$name, [scriptblock]$predicate) {
  $bucketRows = $rows | Where-Object $predicate
  $total = @($bucketRows).Count
  $pass = @($bucketRows | Where-Object { $_.verdict -eq "PASS" }).Count
  $fail = @($bucketRows | Where-Object { $_.verdict -eq "FAIL" }).Count
  $skip = @($bucketRows | Where-Object { $_.verdict -eq "SKIPPED" }).Count
  $rate = if ($total -eq 0) { [double]::NaN } else { [math]::Round(($pass / $total), 4) }
  [pscustomobject]@{
    bucket = $name
    total  = $total
    pass   = $pass
    fail   = $fail
    skipped = $skip
    pass_rate = $rate
  }
}

$buckets = @(
  (Bucket "all" { $true }),
  (Bucket "attack/*" { $_.tags | Where-Object { $_ -like "attack/*" } | ForEach-Object { $true } | Select-Object -First 1 }),
  (Bucket "rag/empty" { $_.tags -contains "rag/empty" }),
  (Bucket "rag/low_conf" { $_.tags -contains "rag/low_conf" }),
  (Bucket "rag/basic" { $_.tags -contains "rag/basic" }),
  (Bucket "tool_policy=stub" { $_.tool_policy -eq "stub" }),
  (Bucket "tool_policy=disabled" { $_.tool_policy -eq "disabled" }),
  (Bucket "requires_citations" { $_.requires_citations -eq $true })
)

$runId = $run.run_id
Write-Host ""
Write-Host ("run_id: {0}" -f $runId)
Write-Host ""
$buckets | Format-Table -AutoSize

if ($OutMarkdownPath) {
  $lines = @()
  $lines += '## travel-ai P0+ S3 buckets'
  $lines += ''
  $lines += ('- run_id: `{0}`' -f $runId)
  $lines += ''
  $lines += '| bucket | total | pass | fail | skipped | pass_rate |'
  $lines += '|---|---:|---:|---:|---:|---:|'
  foreach ($b in $buckets) {
    $pr = if ([double]::IsNaN($b.pass_rate)) { "N/A" } else { "{0:P2}" -f $b.pass_rate }
    $lines += ("| {0} | {1} | {2} | {3} | {4} | {5} |" -f $b.bucket, $b.total, $b.pass, $b.fail, $b.skipped, $pr)
  }
  $md = ($lines -join "`r`n") + "`r`n"
  Set-Content -LiteralPath $OutMarkdownPath -Value $md -Encoding utf8
  Write-Host ""
  Write-Host ("Wrote: {0}" -f $OutMarkdownPath)
}

