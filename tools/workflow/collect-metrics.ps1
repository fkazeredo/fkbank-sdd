# Aggregate .claude/runtime/*/metrics.json into CSV on stdout.
"slice,phase,duration_min,model,effort,compactions,files_read,retries"
Get-ChildItem ".claude/runtime" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
  $f = Join-Path $_.FullName "metrics.json"
  if (Test-Path $f) {
    $slice = $_.Name
    $rows = Get-Content $f -Raw | ConvertFrom-Json
    if ($rows -isnot [array]) { $rows = @($rows) }
    foreach ($r in $rows) {
      $s = if ($r.slice) { $r.slice } else { $slice }
      "{0},{1},{2},{3},{4},{5},{6},{7}" -f $s,$r.phase,$r.duration_min,$r.model,$r.effort,$r.compactions,$r.files_read,$r.retries
    }
  }
}
exit 0
