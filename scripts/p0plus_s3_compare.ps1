param(
  [Parameter(Mandatory = $true)]
  [string]$BaseRunResultsPath,

  [Parameter(Mandatory = $true)]
  [string]$CandRunResultsPath,

  [Parameter(Mandatory = $false)]
  [string]$OutMarkdownPath
)

$ErrorActionPreference = "Stop"

function Read-Json([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { throw "File not found: $path" }
  Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json
}

function PassRate($run) {
  $total = @($run.results).Count
  if ($total -eq 0) { return [double]::NaN }
  $pass = @($run.results | Where-Object { $_.verdict -eq "PASS" }).Count
  return [math]::Round(($pass / $total), 4)
}

$base = Read-Json $BaseRunResultsPath
$cand = Read-Json $CandRunResultsPath

$baseId = $base.run_id
$candId = $cand.run_id

$baseByCase = @{}
foreach ($r in $base.results) { $baseByCase[$r.case_id] = $r }

$candByCase = @{}
foreach ($r in $cand.results) { $candByCase[$r.case_id] = $r }

$caseIds = @($baseByCase.Keys + $candByCase.Keys | Sort-Object -Unique)

$regressions = @()
$improvements = @()
$newFails = @()
$fixed = @()

foreach ($cid in $caseIds) {
  $b = $baseByCase[$cid]
  $c = $candByCase[$cid]
  if (-not $b -or -not $c) { continue }

  $bPass = ($b.verdict -eq "PASS")
  $cPass = ($c.verdict -eq "PASS")

  if ($bPass -and -not $cPass) { $regressions += $cid }
  elseif (-not $bPass -and $cPass) { $improvements += $cid }

  if (-not $bPass -and $b.verdict -eq "FAIL" -and $c.verdict -eq "FAIL") {
    # unchanged fail – do nothing
  }
  elseif ($b.verdict -ne "FAIL" -and $c.verdict -eq "FAIL") { $newFails += $cid }
  elseif ($b.verdict -eq "FAIL" -and $c.verdict -ne "FAIL") { $fixed += $cid }
}

$baseRate = PassRate $base
$candRate = PassRate $cand
$delta = if ([double]::IsNaN($baseRate) -or [double]::IsNaN($candRate)) { [double]::NaN } else { [math]::Round(($candRate - $baseRate), 4) }

Write-Host ""
Write-Host ("base: {0}  pass_rate={1:P2}" -f $baseId, $baseRate)
Write-Host ("cand: {0}  pass_rate={1:P2}" -f $candId, $candRate)
if (-not [double]::IsNaN($delta)) {
  Write-Host ("delta: {0:P2}" -f $delta)
}
Write-Host ""
Write-Host ("regressions (PASS->FAIL): {0}" -f $regressions.Count)
Write-Host ("improvements (FAIL->PASS): {0}" -f $improvements.Count)

if ($OutMarkdownPath) {
  $lines = @()
  $lines += '## compare (base vs cand)'
  $lines += ''
  $lines += ('- base run_id: `{0}`' -f $baseId)
  $lines += ('- cand run_id: `{0}`' -f $candId)
  $lines += ''
  $lines += ('- base pass_rate: {0:P2}' -f $baseRate)
  $lines += ('- cand pass_rate: {0:P2}' -f $candRate)
  if ([double]::IsNaN($delta)) { $lines += '- pass_rate_delta: N/A' } else { $lines += ('- pass_rate_delta: {0:P2}' -f $delta) }
  $lines += ''

  $lines += '### Regressions (PASS -> FAIL)'
  if ($regressions.Count -eq 0) {
    $lines += ''
    $lines += '(none)'
  } else {
    $lines += ''
    foreach ($cid in ($regressions | Sort-Object)) {
      $b = $baseByCase[$cid]
      $c = $candByCase[$cid]
      $lines += ('- `{0}`: {1} → {2} (error_code: {3})' -f $cid, $b.verdict, $c.verdict, $c.error_code)
    }
  }

  $lines += ''
  $lines += '### Improvements (FAIL -> PASS)'
  if ($improvements.Count -eq 0) {
    $lines += ''
    $lines += '(none)'
  } else {
    $lines += ''
    foreach ($cid in ($improvements | Sort-Object)) {
      $lines += ('- `{0}`' -f $cid)
    }
  }

  $md = ($lines -join "`r`n") + "`r`n"
  Set-Content -LiteralPath $OutMarkdownPath -Value $md -Encoding utf8
  Write-Host ""
  Write-Host ("Wrote: {0}" -f $OutMarkdownPath)
}

