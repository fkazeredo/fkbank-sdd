$ErrorActionPreference='SilentlyContinue'
try { $payload=[Console]::In.ReadToEnd() | ConvertFrom-Json } catch { [Console]::Error.WriteLine('stop-guard: invalid hook JSON.'); exit 2 }
if($payload.stop_hook_active -eq $true){ exit 0 }
$root=if($env:CLAUDE_PROJECT_DIR){$env:CLAUDE_PROJECT_DIR}else{(Get-Location).Path}
$runtime=Join-Path $root '.claude/runtime'; $slicePath=Join-Path $runtime 'current-slice'
if(-not (Test-Path -LiteralPath $slicePath)){ exit 0 }
$slice=(Get-Content -LiteralPath $slicePath -Raw).Trim(); if(-not $slice){ exit 0 }
$dir=Join-Path $runtime $slice; $statePath=Join-Path $dir 'state.json'
if(-not (Test-Path -LiteralPath $statePath)){ [Console]::Error.WriteLine("stop-guard: missing state.json for $slice."); exit 2 }
try{$state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json}catch{[Console]::Error.WriteLine("stop-guard: invalid state.json for $slice.");exit 2}
$inProgress=@('SPECIFYING','DESIGNING','BUILDING','QA_RUNNING','PR_PREPARING','CI_REWORK','RELEASE_PREPARING','RELEASE_FINALIZING','HOTFIX_SCOPING','HOTFIX_FINALIZING')
if($inProgress -contains $state.status){[Console]::Error.WriteLine("stop-guard: $slice remains in progress: $($state.status).");exit 2}
if(-not (Test-Path -LiteralPath (Join-Path $dir 'metrics.json'))){[Console]::Error.WriteLine("stop-guard: missing metrics.json for $slice.");exit 2}
if($state.status -eq 'BLOCKED' -and -not (Test-Path -LiteralPath (Join-Path $dir 'block-report.md'))){[Console]::Error.WriteLine("stop-guard: BLOCKED requires block-report.md.");exit 2}
if($state.status -in @('AWAITING_SPEC_INPUT','AWAITING_SPEC_APPROVAL','HUMAN_DECISION_REQUIRED','READY') -and -not ((Test-Path -LiteralPath (Join-Path $dir 'spec-path.txt')) -or (Test-Path -LiteralPath (Join-Path $dir 'decision-request.md')))){[Console]::Error.WriteLine('stop-guard: specifier requires a spec-path or decision-request artifact.');exit 2}
$transcript=[string]$payload.transcript_path
if($transcript -and (Test-Path -LiteralPath $transcript)){
  $text=Get-Content -LiteralPath $transcript -Raw
  if($text -notmatch '(SESSION OVER — next:|RELAY STATE:)'){[Console]::Error.WriteLine('stop-guard: terminal machine-state marker missing.');exit 2}
}
exit 0
