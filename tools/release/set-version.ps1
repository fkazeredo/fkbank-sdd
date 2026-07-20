param([Parameter(Mandatory=$true)][string]$Version)
$ErrorActionPreference='Stop'
if($Version -notmatch '^\d+\.\d+\.\d+(-SNAPSHOT)?$'){Write-Error 'set-version: version must be X.Y.Z or X.Y.Z-SNAPSHOT.';exit 2}
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path; $backup=Join-Path ([IO.Path]::GetTempPath()) ("relay-version-"+[guid]::NewGuid())
New-Item -ItemType Directory $backup|Out-Null; $files=@('pom.xml','backend/pom.xml','frontend/package.json','frontend/package-lock.json')|Where-Object{Test-Path (Join-Path $root $_)}
if(-not $files){Remove-Item -LiteralPath $backup -Recurse;Write-Error 'set-version: nothing to version yet.';exit 1}
foreach($f in $files){$dst=Join-Path $backup $f;New-Item -ItemType Directory -Force (Split-Path $dst)|Out-Null;Copy-Item (Join-Path $root $f) $dst}
function Invoke-Bounded([string]$File,[string[]]$Arguments,[string]$Directory,[int]$Seconds){
  $p=Start-Process -FilePath $File -ArgumentList $Arguments -WorkingDirectory $Directory -PassThru -NoNewWindow
  if(-not $p.WaitForExit($Seconds*1000)){taskkill /PID $p.Id /T /F|Out-Null;throw "timeout after ${Seconds}s: $File"}
  if($p.ExitCode -ne 0){throw "$File exited $($p.ExitCode)"}
}
try{
  if(Test-Path "$root/backend/mvnw.cmd"){Invoke-Bounded "$root/backend/mvnw.cmd" @('-q','versions:set',"-DnewVersion=$Version",'-DgenerateBackupPoms=false') "$root/backend" 300}
  elseif(Test-Path "$root/mvnw.cmd"){Invoke-Bounded "$root/mvnw.cmd" @('-q','versions:set',"-DnewVersion=$Version",'-DgenerateBackupPoms=false') $root 300}
  elseif((Test-Path "$root/backend/pom.xml") -or (Test-Path "$root/pom.xml")){throw 'pom.xml exists but no Maven wrapper was found'}
  $front=$Version -replace '-SNAPSHOT$',''; if(Test-Path "$root/frontend/package.json"){Invoke-Bounded 'npm.cmd' @('version','--no-git-tag-version',$front) "$root/frontend" 180}
  "set-version: confirmed $Version (frontend $front)"
}catch{
  foreach($f in $files){Copy-Item (Join-Path $backup $f) (Join-Path $root $f) -Force};Write-Error "set-version: failed and restored version files: $_";exit 1
}finally{Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction SilentlyContinue}
