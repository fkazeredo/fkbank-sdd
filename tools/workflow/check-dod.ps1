param([Parameter(Mandatory=$true)][string]$Id,[Parameter(Mandatory=$true)][ValidateSet('pre-pr','post-pr')][string]$Mode)
$ErrorActionPreference='SilentlyContinue'; $fail=0
$n=$Id -replace '^SPEC-',''; if($n -match '^\d+$'){$n='{0:0000}' -f [int]$n}; $sid="SPEC-$n"
$specs=@(Get-ChildItem 'docs/specs' -Filter "*$n*" -File); if($specs.Count -ne 1){Write-Error "check-dod: expected exactly one spec for $Id, found $($specs.Count).";exit 1}
$dir=".claude/runtime/$sid"; if(-not(Test-Path $dir)){$dir=".claude/runtime/$Id"}
try{$state=Get-Content "$dir/state.json" -Raw|ConvertFrom-Json}catch{Write-Error 'check-dod: missing or invalid state.json.';exit 1}
function Need($path,$label){if(Test-Path $path){"OK   $label"}else{"MISS $label";$script:fail=1}}
$front=(Get-Content $specs[0].FullName -Raw); if($front -notmatch '(?m)^status:\s*(READY|IN_PROGRESS|IMPLEMENTED)\s*$'){"MISS approved spec status";$fail=1}else{"OK   approved spec status"}
Need "$dir/dev-verification.md" 'developer verification'; Need "$dir/metrics.json" 'phase metrics'
if($state.risk -in @('R2','R3','R4')){Need "$dir/qa-report.md" "QA report ($($state.risk))"; Need "$dir/plan.md" 'approved plan'; if((Get-Content "$dir/plan.md" -Raw) -notmatch 'PLAN_APPROVED'){"MISS explicit PLAN_APPROVED record";$fail=1}}
if($state.status -in @('BLOCKED','HUMAN_DECISION_REQUIRED')){"MISS blocking state $($state.status)";$fail=1}
& "$PSScriptRoot/../git/check-safe-branch.ps1" -Phase pr -AllowDirty; if($LASTEXITCODE -ne 0){$fail=1}
if($Mode -eq 'pre-pr'){if($state.status -ne 'PR_PREPARING'){"MISS pre-pr requires PR_PREPARING";$fail=1}}
else{
  if($state.status -notin @('PR_OPEN','CI_PENDING','CI_FAILED','AWAITING_HUMAN_REVIEW')){"MISS post-pr terminal state";$fail=1}
  if(-not $state.pr_number -and -not $state.pr_url){"MISS PR number or URL";$fail=1}
  Need "$dir/pr-body.md" 'filled PR body'; if(-not $state.next_command){"MISS next_command";$fail=1}
}
exit $fail
