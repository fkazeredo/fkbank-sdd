# RELAY verify-e2e — ephemeral stack up, Playwright, then guaranteed teardown.
# The teardown is deliberately isolated from the run's verdict: `docker compose down` writes its
# progress to stderr, and under $ErrorActionPreference='Stop' PowerShell surfaces that as a
# NativeCommandError. That terminating error escaped this script and aborted the caller mid-chain,
# costing verify-release its final status line (finding W-01). A verification that ends without
# reporting can MASK a failure, so teardown noise must never be able to end the run.
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$art=Join-Path $root '.claude/runtime/e2e-artifacts'
if(-not(Test-Path "$root/compose.e2e.yaml") -or -not(Test-Path "$root/frontend/package.json")){Write-Host 'verify-e2e: planned/not-applicable (E2E stack or frontend is absent).';exit 0}
New-Item -ItemType Directory -Force $art|Out-Null
$code=0
try{
  # --build is not optional: `up` reuses an existing image, so without it a developer machine
  # that already has an image from an earlier commit verifies THAT image and reports PASS. CI
  # never sees this because it starts with an empty image store, which is exactly what makes
  # the failure mode expensive - it only ever bites locally, and it looks like a green run.
  docker compose -f "$root/compose.e2e.yaml" up -d --wait --build
  if($LASTEXITCODE -ne 0){throw 'E2E stack failed'}
  Push-Location "$root/frontend"
  try{
    if(-not(Test-Path node_modules)){npm ci;if($LASTEXITCODE -ne 0){throw 'npm ci failed'}}
    npm run -s e2e
    if($LASTEXITCODE -ne 0){throw 'Playwright failed'}
  }finally{Pop-Location}
  Write-Host 'verify-e2e: PASS'
}catch{
  # Write-Host, not Write-Error: under the Stop preference Write-Error itself throws, which would
  # jump past the exit code and hide the reason the run failed.
  $code=1
  Write-Host "verify-e2e: FAIL - $_"
}finally{
  $ErrorActionPreference='Continue'
  try{docker compose -f "$root/compose.e2e.yaml" down -v 2>&1 | Out-File -LiteralPath "$art/docker-down.log" -Encoding UTF8}
  catch{Write-Host "verify-e2e: teardown reported: $_"}
}
exit $code
