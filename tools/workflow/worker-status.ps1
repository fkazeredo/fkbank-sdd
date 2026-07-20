# Reports whether a slice is waiting on a background worker, and whether that wait still looks
# alive — using signals that would come out differently if the answer were no.
#
# Written after a session spent a quarter of an hour asserting a worker was healthy on the strength
# of two signals that proved nothing: file timestamps, which go blind for exactly as long as a build
# runs, and a process list matched on `java`, which finds an editor's language server whether or not
# anything is building. Both were real commands against a real machine. Neither could have come out
# differently had the worker been dead.
#
# This reports signals, never a verdict. A worker's own completion notification is the only thing
# that says it finished; a report sitting on disk is not one.

param(
  [Parameter(Mandatory = $true)][string]$Id
)

$ErrorActionPreference = 'SilentlyContinue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$dir = Join-Path $root ".claude/runtime/$Id"

if (-not (Test-Path -LiteralPath $dir)) {
  Write-Host "worker-status: no runtime directory for $Id."
  exit 1
}

Write-Host "worker-status: $Id"
Write-Host ''

# What the phase said it was waiting for. Absent means either nothing was spawned, or something was
# spawned without being recorded — which is itself worth seeing.
$awaitingPath = Join-Path $dir 'awaiting.json'
if (Test-Path -LiteralPath $awaitingPath) {
  $awaiting = $null
  try { $awaiting = Get-Content -LiteralPath $awaitingPath -Raw | ConvertFrom-Json } catch { }
  if ($awaiting) {
    Write-Host "  declared wait : $($awaiting.waiting_on)"
    Write-Host "  since         : $($awaiting.since)"
    $since = [datetime]::MinValue
    if ([datetime]::TryParse([string]$awaiting.since, [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AdjustToUniversal -bor [Globalization.DateTimeStyles]::AssumeUniversal,
        [ref]$since)) {
      Write-Host ("  waiting for   : {0:N0} minutes" -f ([datetime]::UtcNow - $since).TotalMinutes)
    }
  } else {
    Write-Host '  declared wait : awaiting.json is present but unreadable.'
  }
} else {
  Write-Host '  declared wait : none recorded.'
}

Write-Host ''
Write-Host '  signals that discriminate'

# A compose stack belonging to this project. Nothing starts one by accident, and the uptime says
# whether it started just now or has been sitting there since an earlier run.
# "docker answered, nothing is running" and "docker could not be asked" are different facts, and a
# script written to keep signals honest must not collapse them: an empty list exits zero.
$compose = @(docker ps --format '{{.Names}}|{{.Status}}' 2>$null)
if ($LASTEXITCODE -ne 0) {
  Write-Host '    containers  : could not ask docker (not running, or not installed).'
} else {
  $project = Split-Path $root -Leaf
  $mine = @($compose | Where-Object { $_ -like "$project*" })
  if ($mine.Count -eq 0) {
    Write-Host '    containers  : none for this project. No E2E stack is up right now.'
  } else {
    foreach ($container in $mine) {
      $parts = $container -split '\|', 2
      Write-Host "    containers  : $($parts[0]) - $($parts[1])"
    }
  }
}

# Recent writes anywhere a worker owns. Useful when positive; silence proves nothing, which is why
# it is labelled rather than interpreted.
$owned = @(
  ".claude/runtime/$Id",
  'docs/tests',
  'docs/qa',
  'frontend/e2e',
  'backend/src/test/java/com/fkbank/acceptance'
)
$newest = $null
foreach ($path in $owned) {
  $full = Join-Path $root $path
  if (-not (Test-Path -LiteralPath $full)) { continue }
  $candidate = Get-ChildItem -LiteralPath $full -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
  if ($candidate -and (-not $newest -or $candidate.LastWriteTimeUtc -gt $newest.LastWriteTimeUtc)) {
    $newest = $candidate
  }
}
if ($newest) {
  $age = [datetime]::UtcNow - $newest.LastWriteTimeUtc
  Write-Host ("    last write  : {0} ({1:N0} min ago)" -f $newest.Name, $age.TotalMinutes)
  Write-Host '                  a long gap is normal during a build and does not mean the worker died.'
} else {
  Write-Host '    last write  : nothing found under the paths a worker owns.'
}

Write-Host ''
Write-Host "  The worker's own completion notification is what says it finished. Nothing above does."
exit 0
