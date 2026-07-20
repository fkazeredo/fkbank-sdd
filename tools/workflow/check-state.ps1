# Print every slice's runtime state (read-only).
$found=$false
Get-ChildItem ".claude/runtime" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
  $f = Join-Path $_.FullName "state.json"
  if (Test-Path $f) {
    $found=$true; $d = Get-Content $f -Raw | ConvertFrom-Json
    "{0,-12} {1,-24} risk={2} updated={3}" -f $d.slice,$d.status,$d.risk,$d.updated
  }
}
if (-not $found) { "check-state: no active slices in .claude/runtime/." }
exit 0
