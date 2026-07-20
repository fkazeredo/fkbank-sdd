# RELAY verify-release (target: <= 30 min) — slice battery + E2E + release packaging.
# Steps are isolated and the summary line is unconditional on purpose (finding W-01): a child
# script may set $ErrorActionPreference='Stop' and turn a native command's stderr into a
# terminating error, which previously aborted this chain before it could report. A release
# verification that ends without printing its verdict can MASK a failure, so every step is
# contained and its result folded into the aggregate exit code instead of ending the run.
$ErrorActionPreference='Continue'
$sw=[Diagnostics.Stopwatch]::StartNew()
$fail=0

function Invoke-Step([string]$Name,[scriptblock]$Step){
  # Records failure in $script:fail rather than returning it: a `return` value here would merge
  # into the step's own pipeline output and corrupt what the caller sees.
  try{
    $global:LASTEXITCODE=0
    & $Step
    if($LASTEXITCODE -ne 0){$script:fail=1;Write-Host ("verify-release: step '{0}' failed (exit={1})" -f $Name,$LASTEXITCODE)}
  }catch{
    $script:fail=1
    Write-Host ("verify-release: step '{0}' threw: {1}" -f $Name,$_)
  }
}

$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Invoke-Step 'verify-slice' { & "$PSScriptRoot/verify-slice.ps1" }
Invoke-Step 'verify-e2e'   { & "$PSScriptRoot/verify-e2e.ps1" }

$pkgDir=$null
if(Test-Path "$root/backend/mvnw.cmd"){$pkgDir="$root/backend"}elseif(Test-Path "$root/mvnw.cmd"){$pkgDir=$root}
if($pkgDir){
  # try/finally around Push-Location: a throwing package step used to leak the working directory
  # into whatever ran next.
  Invoke-Step 'package' { Push-Location $pkgDir; try{ & ./mvnw.cmd -q -DskipTests package }finally{ Pop-Location } }
}

Write-Host ("verify-release: {0}s, exit={1}" -f [int]$sw.Elapsed.TotalSeconds,$fail)
exit $fail
