param([Parameter(Mandatory=$true)][string]$SpecPath)
$ErrorActionPreference='Stop'
$resolved=(Resolve-Path -LiteralPath $SpecPath).Path
$text=[IO.File]::ReadAllText($resolved,[Text.UTF8Encoding]::new($false)).Replace("`r`n","`n").Replace("`r","`n")
$text=[regex]::Replace($text,'(?m)^owner_approved_at:\s*.*$','owner_approved_at: null')
$text=[regex]::Replace($text,'(?m)^owner_approved_hash:\s*.*$','owner_approved_hash: null')
$bytes=[Text.UTF8Encoding]::new($false).GetBytes($text)
$sha=[Security.Cryptography.SHA256]::Create()
try{([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()}
finally{$sha.Dispose()}
