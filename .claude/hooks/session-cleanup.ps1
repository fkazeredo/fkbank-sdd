$ErrorActionPreference = 'SilentlyContinue'
$runtime = Join-Path (Get-Location) '.claude/runtime'
foreach ($name in @('current-role','current-slice')) {
  $path = Join-Path $runtime $name
  if (Test-Path -LiteralPath $path) { Set-Content -LiteralPath $path -Value '' -NoNewline }
}
# The session that is ending owns exactly one of these; a sibling session's file must survive.
$session = $env:CLAUDE_CODE_SESSION_ID
if ($session) {
  $safe = ($session -replace '[^0-9A-Za-z._-]', '_')
  Remove-Item -LiteralPath (Join-Path $runtime (Join-Path 'roles' $safe)) -Force -ErrorAction SilentlyContinue
}
exit 0
