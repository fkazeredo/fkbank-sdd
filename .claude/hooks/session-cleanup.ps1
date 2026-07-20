$ErrorActionPreference = 'SilentlyContinue'
$runtime = Join-Path (Get-Location) '.claude/runtime'
foreach ($name in @('current-role','current-slice')) {
  $path = Join-Path $runtime $name
  if (Test-Path -LiteralPath $path) { Set-Content -LiteralPath $path -Value '' -NoNewline }
}
exit 0
