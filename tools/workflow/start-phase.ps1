param(
  [Parameter(Mandatory=$true)][ValidateSet('specifier','designer','builder','qa','reviewer','security','orchestrator','reconciler','releaser')][string]$Role,
  [Parameter(Mandatory=$true)][ValidatePattern('^(SPEC-(?:DRAFT-[0-9A-Za-z-]+|\d{4})|RELEASE-[0-9A-Za-z.-]+|HOTFIX-[0-9A-Za-z.-]+)$')][string]$Id,
  [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$Phase,
  [string]$Risk = '',
  [string[]]$AllowedPaths = @()
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $root '.claude/runtime'; New-Item -ItemType Directory -Force $runtime | Out-Null
$sliceDir=Join-Path $runtime $Id; New-Item -ItemType Directory -Force $sliceDir | Out-Null
$sha=(git -C $root rev-parse HEAD 2>$null); if($LASTEXITCODE -ne 0){ throw 'start-phase: not a Git repository.' }
$state=[ordered]@{slice=$Id;status=$Phase;risk=$Risk;base_sha=$sha.Trim();updated=(Get-Date).ToUniversalTime().ToString('o')}
$state | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $sliceDir 'state.json') -Encoding UTF8
Set-Content -LiteralPath (Join-Path $runtime 'current-role') -Value $Role -NoNewline
Set-Content -LiteralPath (Join-Path $runtime 'current-slice') -Value $Id -NoNewline

# A background worker and the agent that spawned it share this directory, and each one calling
# start-phase overwrites the other's role. Whoever wrote last then decides what everyone may
# touch - which fails safe when it narrows the caller's rights and fails open when it widens
# them. Writing a per-session copy alongside the shared one lets the guard resolve the role of
# the session actually making the call; the shared file stays for tools that have no session.
$session = $env:CLAUDE_CODE_SESSION_ID
if ($session) {
  $sessions = Join-Path $runtime 'roles'; New-Item -ItemType Directory -Force $sessions | Out-Null
  $safe = ($session -replace '[^0-9A-Za-z._-]', '_')
  Set-Content -LiteralPath (Join-Path $sessions $safe) -Value $Role -NoNewline
}
[ordered]@{role=$Role;phase=$Phase;allowed_paths=@($AllowedPaths)} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'phase-manifest.json') -Encoding UTF8
"start-phase: $Role/$Phase for $Id"
