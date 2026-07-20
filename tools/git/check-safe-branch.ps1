param(
  [ValidateSet('branch-source','implementation','pr','release','hotfix','reconcile')][string]$Phase='implementation',
  [switch]$AllowDirty
)
$ErrorActionPreference='Stop'
$branch=(git branch --show-current 2>$null).Trim()
if($LASTEXITCODE -ne 0 -or -not $branch){Write-Error 'check-safe-branch: detached HEAD or not a Git repository. Switch to the phase branch.';exit 1}
$patterns=@{
  'branch-source'='^develop$';
  implementation='^(feature|bugfix|chore)/.+'; pr='^(feature|bugfix|chore|hotfix|release)/.+';
  release='^release/.+'; hotfix='^hotfix/.+'; reconcile='^(chore|feature|bugfix|hotfix|release)/.+'
}
if(($Phase -ne 'branch-source' -and $branch -in @('main','develop')) -or $branch -notmatch $patterns[$Phase]){Write-Error "check-safe-branch: '$branch' is incompatible with phase '$Phase'. Use the matching GitFlow branch; main/develop are protected for implementation.";exit 1}
if(-not $AllowDirty -and (git status --porcelain)){Write-Error 'check-safe-branch: working tree is unexpectedly dirty. Commit/stash intentionally or rerun only where the phase explicitly permits known changes.';exit 1}
"check-safe-branch: OK ($branch, phase=$Phase)"
