$ErrorActionPreference='Stop';$fail=0;$known=@{};$modFile='docs/DOMAIN-MODULES.txt';$allowedModules=@(if(Test-Path $modFile){Get-Content $modFile|ForEach-Object{($_ -split '#')[0].Trim()}|Where-Object{$_}})
$specs=@(Get-ChildItem 'docs/specs' -File | Where-Object {$_.Name -match '^SPEC-\d{4}-.+\.md$'})
foreach($f in $specs){$text=Get-Content $f.FullName -Raw;$m=[regex]::Match($text,'(?s)\A---\r?\n(.*?)\r?\n---');if(-not$m.Success){Write-Error "$($f.Name): missing frontmatter";$fail=1;continue};$fm=$m.Groups[1].Value
  $id=([regex]::Match($fm,'(?m)^id:\s*(SPEC-\d{4})\s*$')).Groups[1].Value;if(-not$id){Write-Error "$($f.Name): invalid id";$fail=1;continue};$known[$id]=$f.Name
  foreach($key in @('title','status','risk','profile','modules','depends_on','relevant_adrs','reading_list','planned_sprint','planned_release','owner_approved_at','owner_approved_hash')){if($fm -notmatch "(?m)^${key}:\s*"){Write-Error "$($f.Name): missing $key";$fail=1}}
  if($fm -match '(?m)^status:\s*READY\s*$' -and $fm -match '(?m)^owner_approved_at:\s*null\s*$'){Write-Error "$($f.Name): READY without owner approval";$fail=1}
  if($fm -match '(?m)^status:\s*(READY|IN_PROGRESS|IMPLEMENTED)\s*$' -and $fm -match '(?m)^owner_approved_hash:\s*null\s*$'){Write-Error "$($f.Name): approved lifecycle state without content hash";$fail=1}
  if($fm -match 'SPEC-\d{4}\.\.'){Write-Error "$($f.Name): dependency range not expanded";$fail=1}
  $sf=[regex]::Match($fm,'(?m)^split_from:\s*(\S+)\s*$');if($sf.Success -and $sf.Groups[1].Value -notmatch '^SPEC-\d{4}$'){Write-Error "$($f.Name): invalid split_from";$fail=1}
  foreach($section in @('Business rules','Decision log')){if($text -notmatch "(?m)^## $([regex]::Escape($section))\s*$"){Write-Error "$($f.Name): missing ## $section";$fail=1}}
  $moduleLine=([regex]::Match($fm,'(?m)^modules:\s*\[(.*?)\]\s*$')).Groups[1].Value
  foreach($module in ($moduleLine -split ',' | ForEach-Object {$_.Trim().Trim('"').Trim("'")} | Where-Object {$_})){if($allowedModules.Count -and $module -notin $allowedModules){Write-Error "$($f.Name): unknown module $module";$fail=1}}
}
foreach($f in $specs){$fm=([regex]::Match((Get-Content $f.FullName -Raw),'(?s)\A---\r?\n(.*?)\r?\n---')).Groups[1].Value;foreach($d in [regex]::Matches(([regex]::Match($fm,'(?m)^depends_on:\s*\[(.*?)\]')).Groups[1].Value,'SPEC-\d{4}').Value){if(-not$known.ContainsKey($d)){Write-Error "$($f.Name): dependency $d does not exist";$fail=1}}}
if($fail){exit 1};"validate-specs: PASS ($($specs.Count) specs)"
