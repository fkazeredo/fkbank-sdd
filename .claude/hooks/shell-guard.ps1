$ErrorActionPreference = 'Stop'
try { $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json } catch { exit 0 }
$command = [string]$payload.tool_input.command
if ([string]::IsNullOrWhiteSpace($command)) { exit 0 }
$blocked = @(
  '(?im)(^|[;&|]\s*)git\s+merge(?:\s|$)',
  '(?im)(^|[;&|]\s*)gh\s+pr\s+merge(?:\s|$)',
  '(?im)git\s+push\s+(?:--force|-f)(?:\s|$)',
  '(?im)git\s+push\s+[^\r\n;&|]*\b(?:main|develop)\b',
  '(?im)git\s+tag\s+(?:-f|--force)(?:\s|$)',
  '(?im)(?:Set-Content|Out-File|Add-Content)\b',
  '(?im)(?:python|python3|py)\b[^\r\n]*(?:open\s*\([^\)]*,\s*["''](?:w|a|x)|write_text|write_bytes)'
)
foreach ($pattern in $blocked) {
  if ($command -match $pattern) {
    [Console]::Error.WriteLine("shell-guard: blocked a mutating command that bypasses RELAY write/merge boundaries. Use repository editing tools and human-only merge/push procedures.")
    exit 2
  }
}
exit 0
