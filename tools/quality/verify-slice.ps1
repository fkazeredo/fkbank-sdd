# RELAY verify-slice (target: <= 15 min) — full backend verify + frontend lint/test/build.
$sw=[Diagnostics.Stopwatch]::StartNew(); $fail=0; $ran=0
$dir = $null
if (Test-Path "backend/mvnw.cmd") { $dir="backend" } elseif (Test-Path "mvnw.cmd") { $dir="." }
if ($dir) { $ran=1; Push-Location $dir; & ./mvnw.cmd -q verify; if ($LASTEXITCODE -ne 0){$fail=1}; Pop-Location }
# Emulators are separate services with their own build. Their tests are the only thing proving
# an emulated rail still behaves the way the bank's tests assume, so they run here rather than
# being compiled only when someone happens to build a container.
foreach ($emulator in (Get-ChildItem -Path "emulators" -Directory -ErrorAction SilentlyContinue)) {
  if (Test-Path (Join-Path $emulator.FullName "mvnw.cmd")) {
    $ran=1; Push-Location $emulator.FullName
    & ./mvnw.cmd -q verify; if ($LASTEXITCODE -ne 0){$fail=1}
    Pop-Location
  }
}
if (Test-Path "frontend/package.json") {
  $ran=1; Push-Location frontend
  if (-not (Test-Path node_modules)) { npm ci; if ($LASTEXITCODE -ne 0){$fail=1} }
  npm run -s lint; if ($LASTEXITCODE -ne 0){$fail=1}
  npm test -- --watch=false; if ($LASTEXITCODE -ne 0){$fail=1}
  npm run -s build; if ($LASTEXITCODE -ne 0){$fail=1}
  Pop-Location
}
if ($ran -eq 0) { Write-Host "verify-slice: nothing to verify yet (pre-bootstrap). OK by definition." }
Write-Host ("verify-slice: {0}s, exit={1}" -f [int]$sw.Elapsed.TotalSeconds, $fail); exit $fail
