# Binding slice gates (read-only). Exit 0 = pass, 2 = gate violated, 1 = usage/IO error.
# The runtime dir is resolved from the id alone (no docs/specs lookup): a numeric id is padded to
# SPEC-NNNN, an already-formed SPEC-NNNN is accepted as-is. Root is $CLAUDE_PROJECT_DIR or the repo
# root (this script's dir/../..). state.json lives at <root>/.claude/runtime/<SPEC-NNNN>.
param([string]$Id, [string]$Gate)
$ErrorActionPreference = 'SilentlyContinue'

function Deny-Usage([string]$Message) {
  if ($Message) { [Console]::Error.WriteLine("check-slice-gate: $Message") }
  [Console]::Error.WriteLine('usage: check-slice-gate.ps1 -Id <id> -Gate <fit|dev-verified|qa-preflight|parallel>')
  exit 1
}

if (-not $Id) { Deny-Usage 'missing -Id' }
if ($Gate -notin @('fit', 'dev-verified', 'qa-preflight', 'parallel')) { Deny-Usage "unknown gate '$Gate'" }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
$n = $Id -replace '^SPEC-', ''
if ($n -match '^\d+$') { $n = '{0:0000}' -f [int]$n }
$sid = "SPEC-$n"
$dir = Join-Path (Join-Path $root '.claude/runtime') $sid

# --- parallel: reads parallel-plan.json, not state.json (absent plan => sequential => safe) ---
if ($Gate -eq 'parallel') {
  $planPath = Join-Path $dir 'parallel-plan.json'
  if (-not (Test-Path -LiteralPath $planPath)) {
    # Absence is safe only when the orchestrator explicitly records sequential execution.
    $statePath = Join-Path $dir 'state.json'
    if (-not (Test-Path -LiteralPath $statePath)) { Deny-Usage "missing state.json for $sid" }
    try { $parallelState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
    catch { Deny-Usage "invalid JSON in $statePath" }
    if ([string]$parallelState.execution_mode -eq 'sequential') { exit 0 }
    [Console]::Error.WriteLine('check-slice-gate: execution_mode must be sequential or parallel-plan.json must exist')
    exit 2
  }
  try { $plan = Get-Content -LiteralPath $planPath -Raw | ConvertFrom-Json }
  catch { Deny-Usage "invalid JSON in $planPath" }

  if ($plan.serialize -eq $true) { exit 0 }   # rule 10 fallback: serialized work is always safe

  $failures = @()
  $workstreams = @($plan.workstreams | Where-Object { $_ })
  if ($workstreams.Count -eq 0) { $failures += 'no workstreams declared' }

  # exactly one integrator: a top-level id naming a workstream, or one workstream flagged integrator:true
  $wsIds = @($workstreams | ForEach-Object { [string]$_.id })
  $topLevel = [string]$plan.integrator
  $flagged = @($workstreams | Where-Object { $_.integrator -eq $true })
  if (-not (($topLevel -and ($wsIds -contains $topLevel)) -or ($flagged.Count -eq 1))) {
    $failures += 'exactly one integrator required (top-level integrator naming a workstream, or one workstream with integrator:true)'
  }

  function Get-PathPrefix([string]$p) {
    $p = $p.Trim()
    $p = $p -replace '/\*\*$', '' -replace '/\*$', '' -replace '\*$', '' -replace '/$', ''
    return $p
  }
  function Test-PathOverlap([string]$a, [string]$b) {
    if ($a -eq $b) { return $true }
    if ($a -eq '' -or $b -eq '') { return $true }
    if ($b.Length -gt $a.Length -and $b.StartsWith($a) -and $b.Substring($a.Length, 1) -eq '/') { return $true }
    if ($a.Length -gt $b.Length -and $a.StartsWith($b) -and $a.Substring($b.Length, 1) -eq '/') { return $true }
    return $false
  }
  for ($i = 0; $i -lt $workstreams.Count; $i++) {
    for ($j = $i + 1; $j -lt $workstreams.Count; $j++) {
      $pathsI = @($workstreams[$i].paths | Where-Object { $_ })
      $pathsJ = @($workstreams[$j].paths | Where-Object { $_ })
      foreach ($pa in $pathsI) {
        foreach ($pb in $pathsJ) {
          if (Test-PathOverlap (Get-PathPrefix ([string]$pa)) (Get-PathPrefix ([string]$pb))) {
            $failures += "workstreams '$([string]$workstreams[$i].id)' and '$([string]$workstreams[$j].id)' overlap on paths '$pa' / '$pb'"
          }
        }
      }
    }
  }

  # a shared mutable resource in >1 workstream is only safe if each such workstream isolates it
  $resourceOwners = @{}
  foreach ($ws in $workstreams) {
    foreach ($r in @($ws.shared_resources | Where-Object { $_ })) {
      $rk = [string]$r
      if (-not $resourceOwners.ContainsKey($rk)) { $resourceOwners[$rk] = @() }
      $resourceOwners[$rk] += $ws
    }
  }
  foreach ($rk in $resourceOwners.Keys) {
    $owners = @($resourceOwners[$rk])
    if ($owners.Count -ge 2) {
      foreach ($ws in $owners) {
        $iso = @($ws.isolated | Where-Object { $_ } | ForEach-Object { [string]$_ })
        if ($iso -notcontains $rk) {
          $failures += "shared resource '$rk' used by multiple workstreams but not isolated in workstream '$([string]$ws.id)'"
        }
      }
    }
  }

  if ($failures.Count) { $failures | ForEach-Object { [Console]::Error.WriteLine("check-slice-gate: $_") }; exit 2 }
  exit 0
}

# --- flat-field gates: read state.json ---
$statePath = Join-Path $dir 'state.json'
if (-not (Test-Path -LiteralPath $statePath)) {
  [Console]::Error.WriteLine("check-slice-gate: missing state.json for $sid.")
  exit 1
}
try { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
catch { [Console]::Error.WriteLine("check-slice-gate: invalid state.json for $sid."); exit 1 }

if ($Gate -eq 'fit') {
  $fit = [string]$state.fit
  if ('fit_signals' -notin @($state.PSObject.Properties.Name) -or 'fit_unsafe_condition' -notin @($state.PSObject.Properties.Name)) {
    [Console]::Error.WriteLine('check-slice-gate: fit_signals and fit_unsafe_condition are required'); exit 2
  }
  $signals = @($state.fit_signals | Where-Object { $_ -ne $null })
  $invalidSignals = @($signals | Where-Object { ([string]$_) -notmatch '^[1-8]$' })
  if ($invalidSignals.Count) { [Console]::Error.WriteLine('check-slice-gate: fit_signals must contain only unique integers 1..8'); exit 2 }
  if (@($signals | Select-Object -Unique).Count -ne $signals.Count) { [Console]::Error.WriteLine('check-slice-gate: fit_signals contains duplicates'); exit 2 }
  $unsafe = $state.fit_unsafe_condition -eq $true
  if ($fit -eq 'FIT') {
    if ($signals.Count -ge 3 -or $unsafe) { [Console]::Error.WriteLine("check-slice-gate: declared FIT conflicts with $($signals.Count) signal(s) / unsafe=$unsafe"); exit 2 }
    exit 0
  }
  $reason = switch ($fit) {
    'TOO_LARGE' { 'fit=TOO_LARGE => split required' }
    'HUMAN_DECISION_REQUIRED' { 'fit=HUMAN_DECISION_REQUIRED => owner decision' }
    '' { 'fit not classified' }
    default { "fit is '$fit' (must be FIT)" }
  }
  [Console]::Error.WriteLine("check-slice-gate: $reason")
  exit 2
}

# dev-verified conditions, shared by dev-verified and qa-preflight
$failures = @()
if ([string]$state.fit -ne 'FIT') { $failures += "fit is '$([string]$state.fit)' (must be FIT)" }
if ([string]$state.verify_slice -ne 'pass') { $failures += "verify_slice is '$([string]$state.verify_slice)' (must be pass)" }
if ([string]$state.acceptance_evidence -ne 'complete') { $failures += "acceptance_evidence is '$([string]$state.acceptance_evidence)' (must be complete)" }
if ([string]$state.e2e -notin @('pass', 'not_applicable')) { $failures += "e2e is '$([string]$state.e2e)' (must be pass or not_applicable)" }
$csha = [string]$state.candidate_sha
if (-not $csha) {
  $failures += 'candidate_sha is absent'
} else {
  $head = ([string](& git -C $root rev-parse HEAD 2>$null)).Trim()
  if (-not $head) { $failures += 'candidate_sha cannot be compared: no current HEAD' }
  elseif ($csha -ne $head) { $failures += "candidate_sha '$csha' does not match current HEAD '$head'" }
}
if (-not (Test-Path -LiteralPath (Join-Path $dir 'dev-verification.md'))) { $failures += 'dev-verification.md is missing' }
$evidencePath = Join-Path $dir 'dev-verification.json'
if (-not (Test-Path -LiteralPath $evidencePath)) {
  $failures += 'dev-verification.json is missing'
} else {
  try { $evidence = Get-Content -LiteralPath $evidencePath -Raw | ConvertFrom-Json }
  catch { $failures += 'dev-verification.json is invalid'; $evidence = $null }
  if ($evidence) {
    if ([string]$evidence.candidate_sha -ne $csha) { $failures += 'dev-verification.json candidate_sha does not match state.json' }
    $commands = @($evidence.commands | Where-Object { $_ })
    if ($commands.Count -eq 0) { $failures += 'no executed commands recorded' }
    foreach ($cmd in $commands) {
      if (-not [string]$cmd.command -or [string]$cmd.status -ne 'pass' -or -not [string]$cmd.evidence) { $failures += 'every command requires command, status=pass, and evidence' }
    }
    $criteria = @($evidence.acceptance_criteria | Where-Object { $_ })
    if ($criteria.Count -eq 0) { $failures += 'no acceptance-criterion evidence recorded' }
    foreach ($criterion in $criteria) {
      if (-not [string]$criterion.criterion -or [string]$criterion.status -ne 'pass' -or -not [string]$criterion.evidence) { $failures += 'every acceptance criterion requires criterion, status=pass, and executable evidence' }
    }
    if (@($evidence.skipped_mandatory_controls | Where-Object { $_ }).Count -gt 0) { $failures += 'mandatory controls were skipped' }
  }
}
if ([string]$state.e2e -eq 'not_applicable' -and $state.e2e_applicable -ne $false) { $failures += 'e2e=not_applicable requires e2e_applicable=false' }

if ($Gate -eq 'qa-preflight') {
  if ([string]$state.status -ne 'DEV_VERIFIED') { $failures += "status is '$([string]$state.status)' (must be DEV_VERIFIED)" }
}

if ($failures.Count) { $failures | ForEach-Object { [Console]::Error.WriteLine("check-slice-gate: $_") }; exit 2 }
exit 0
