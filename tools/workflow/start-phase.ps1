param(
  [Parameter(Mandatory=$true)][ValidateSet('specifier','designer','builder','qa','reviewer','reconciler','releaser')][string]$Role,
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
[ordered]@{role=$Role;phase=$Phase;allowed_paths=@($AllowedPaths)} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'phase-manifest.json') -Encoding UTF8
"start-phase: $Role/$Phase for $Id"
