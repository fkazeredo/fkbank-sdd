$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$violations = @()

$excluded = '(?i)[\\/](\.git|node_modules|target|dist|build)([\\/]|$)'
# The forbidden thing is a paired *documentation* edition outside docs/manual — not an i18n source
# resource that legitimately carries a locale in its name (e.g. the app's single en-US string file).
# So the locale check only applies to documentation files, never to code/assets.
$docFile = '(?i)\.(md|markdown|adoc|rst|txt)$'
$localizedOutsideManual = Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
  $_.FullName -notlike "$root\.git\*" -and
  $_.FullName -notlike "$root\docs\manual\*" -and
  $_.FullName -notlike "$root\.claude\runtime\*" -and
  $_.FullName -notmatch $excluded -and
  $_.Name -match $docFile -and
  ($_.Name -match '(?i)(pt[-_]BR|en[-_]US)' -or $_.DirectoryName -match '(?i)[\\/](pt[-_]BR|en[-_]US)([\\/]|$)')
}
foreach ($file in $localizedOutsideManual) {
  $violations += "localized artifact outside docs/manual: $($file.FullName.Substring($root.Length + 1))"
}

$portuguesePattern = '(?i)\b(não|também|apenas|deve|deverá|entrega|decisão|decisões|segurança|arquitetura|domínio|usuário|usuários|licença|visão|objetivo|quando|então|alteração|aprovação|revisão|português)\b'
$markdown = Get-ChildItem -LiteralPath $root -Recurse -Filter '*.md' -File | Where-Object {
  $_.FullName -notlike "$root\.git\*" -and $_.FullName -notlike "$root\docs\manual\*" -and $_.FullName -notlike "$root\.claude\runtime\*" -and $_.FullName -notmatch $excluded
}
foreach ($file in $markdown) {
  $lineNumber = 0
  foreach ($line in Get-Content -Encoding utf8 -LiteralPath $file.FullName) {
    $lineNumber++
    if ($line -match $portuguesePattern) {
      $violations += "Portuguese text outside docs/manual: $($file.FullName.Substring($root.Length + 1)):$lineNumber"
    }
  }
}

if ($violations.Count) {
  $violations | ForEach-Object { [Console]::Error.WriteLine($_) }
  exit 1
}
Write-Output 'validate-doc-language: PASS'
