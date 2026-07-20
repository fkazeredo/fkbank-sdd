param([string]$Target='candidate',[switch]$RequiresHeavy)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidence=Join-Path $root ".claude/runtime/security-$Target"
New-Item -ItemType Directory -Force -Path $evidence|Out-Null
$results=@()

# Scanners run as PINNED containers (tag + digest), never as build dependencies: decision-ladder
# rung 8 forbids adding a dependency without owner approval, and the assurance track needs a
# verdict that reproduces months later during an audit. Never float these to :latest.
$GitleaksImage='ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854'

function Run-Control([string]$Name,[scriptblock]$Command){
  # The exit code is the ONLY verdict. docker, gitleaks and maven all report progress on stderr,
  # and whenever that stream is captured (redirected, or piped by a calling orchestrator) the
  # Stop preference turns it into a terminating NativeCommandError — recording a noisy-but-clean
  # scanner as a failed control. Relaxing the preference here keeps chatter from voting.
  $prev=$ErrorActionPreference
  try{
    $ErrorActionPreference='Continue'
    $global:LASTEXITCODE=0
    &$Command
    $code=$LASTEXITCODE;if($null -eq $code){$code=0}
    if($code -ne 0){throw "exit=$code"}
    $script:results+="$Name=PASS"
  }
  catch{$script:results+="$Name=FAIL ($_)";throw}
  finally{$ErrorActionPreference=$prev}
}

function Get-GitleaksSubcommand{
  # gitleaks 8.19 replaced `detect` with `git` (and moved the repo from --source to a positional
  # argument). Probe the installed binary instead of assuming a vintage, so an operator with
  # either generation gets a real scan rather than a usage error mistaken for a clean tree.
  # Same stderr caveat as Run-Control: `2>&1 |` under the Stop preference throws, so relax it.
  $prev=$ErrorActionPreference
  try{
    $ErrorActionPreference='Continue'
    $help = & gitleaks --help 2>&1 | Out-String
    if($help -match '(?m)^\s+git\s+scan git repositories'){'git'}else{'detect'}
  }finally{$ErrorActionPreference=$prev}
}

Push-Location $root
try{
  Run-Control 'release-verification' { powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/quality/verify-release.ps1 }

  # Secrets. A local binary wins; otherwise the pinned image runs the same scan, so the control
  # executes anywhere Docker exists. It is never downgraded to a no-op just because the host
  # lacks the tool — that would turn a missing control into a silent PASS.
  if(Get-Command gitleaks -ErrorAction SilentlyContinue){
    $sub = Get-GitleaksSubcommand
    if($sub -eq 'git'){Run-Control 'secrets' { & gitleaks git --no-banner --redact $root }}
    else{Run-Control 'secrets' { & gitleaks detect --source $root --no-banner --redact }}
  }elseif(Get-Command docker -ErrorAction SilentlyContinue){
    Run-Control 'secrets(docker)' { & docker run --rm -v "${root}:/repo:ro" $GitleaksImage git --no-banner --redact /repo }
  }elseif($RequiresHeavy){throw 'BLOCKED: gitleaks (local binary or Docker) is required for this candidate'}
  else{$results+='secrets=NOT_APPLICABLE(no gitleaks binary and no Docker)'}

  # Backend. The Maven project lives at backend/pom.xml; probing the repo root made this control
  # report NOT_APPLICABLE(no backend) on a Java repo, silently skipping the entire build.
  $backendDir=$null
  if(Test-Path (Join-Path $root 'backend/pom.xml')){$backendDir=Join-Path $root 'backend'}
  elseif(Test-Path (Join-Path $root 'pom.xml')){$backendDir=$root}
  if($backendDir){
    $mvnw=Join-Path $backendDir 'mvnw.cmd'
    if(Test-Path $mvnw){
      Run-Control 'backend-tests' { Push-Location $backendDir; try{ & $mvnw -B verify }finally{ Pop-Location } }
    }elseif($RequiresHeavy){throw 'BLOCKED: Maven wrapper is required for backend assurance'}
    else{$results+='backend-tests=NOT_APPLICABLE(no Maven wrapper)'}
  }else{$results+='backend-tests=NOT_APPLICABLE(no backend)'}

  # Dynamic assurance (DAST). --build so the scan hits an image rebuilt from THIS candidate:
  # reusing a stale image would attest to code that is not under review.
  if(Test-Path 'compose.security.yaml'){
    $env:APP_SECURITY_TARGET=$Target
    try{
      Run-Control 'dynamic-security' { & docker compose -f compose.security.yaml up --build --abort-on-container-exit --exit-code-from security-tests }
    }finally{
      # Teardown must never decide the outcome: `docker compose down` writes its progress to
      # stderr, which under $ErrorActionPreference='Stop' becomes a terminating error and would
      # mask the control's real verdict (the verify-release W-01 failure mode).
      $prev=$ErrorActionPreference;$ErrorActionPreference='Continue'
      try{& docker compose -f compose.security.yaml down -v 2>&1 | Out-File -LiteralPath (Join-Path $evidence 'docker-down.log') -Encoding UTF8}
      catch{Write-Host "security teardown reported: $_"}
      finally{$ErrorActionPreference=$prev;$global:LASTEXITCODE=0}
    }
    $results+="dast-report=$evidence/zap-report.html"
  }elseif($RequiresHeavy){throw 'BLOCKED: compose.security.yaml and its pinned DAST/pentest profile are required'}
  else{$results+='dynamic-security=NOT_APPLICABLE(no application)'}

  $results|Set-Content -LiteralPath (Join-Path $evidence 'controls.txt') -Encoding UTF8
  $results|ForEach-Object{Write-Host $_}
  Write-Host 'verify-assurance: PASS'
}finally{
  # Persist whatever controls ran even when one threw, so a BLOCKED/FAIL run still leaves evidence.
  if($results.Count -gt 0){$results|Set-Content -LiteralPath (Join-Path $evidence 'controls.txt') -Encoding UTF8}
  Pop-Location
}
