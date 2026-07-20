# RELAY verify-fast (target: <= 5 min) — compile + fast unit tests + frontend lint.
# Thin wrapper over the canonical build commands; identical decisions to verify-fast.sh.
$sw=[Diagnostics.Stopwatch]::StartNew(); $fail=0; $ran=0
$dir = $null
if (Test-Path "backend/mvnw.cmd") { $dir="backend" } elseif (Test-Path "mvnw.cmd") { $dir="." }
if ($dir) { $ran=1; Push-Location $dir; & ./mvnw.cmd -q "-DskipITs=true" test; if ($LASTEXITCODE -ne 0){$fail=1}; Pop-Location }
if (Test-Path "frontend/package.json") { $ran=1; Push-Location frontend; if (-not (Test-Path node_modules)) { npm ci; if ($LASTEXITCODE -ne 0){$fail=1} }; npm run -s lint; if ($LASTEXITCODE -ne 0){$fail=1}; Pop-Location }
if ($ran -eq 0) { Write-Host "verify-fast: nothing to verify yet (pre-bootstrap). OK by definition." }
Write-Host ("verify-fast: {0}s, exit={1}" -f [int]$sw.Elapsed.TotalSeconds, $fail); exit $fail
