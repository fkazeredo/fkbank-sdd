# Split completeness + provenance check (read-only). Exit 0 = pass, 2 = violation, 1 = usage/IO.
# Proves a spec split lost nothing: (a) every acceptance-criterion line in the original appears
# verbatim (trimmed) in at least one child, and (b) every child carries split_from pointing at the
# original's id. It never writes any file.
param([string]$Original, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Children)
$ErrorActionPreference = 'SilentlyContinue'

function Deny-Usage([string]$Message) {
  if ($Message) { [Console]::Error.WriteLine("check-split: $Message") }
  [Console]::Error.WriteLine('usage: check-split.ps1 -Original <path> -Children <path>[,<path>...]')
  exit 1
}

if (-not $Original) { Deny-Usage 'missing -Original' }
# Accept every invocation form: positional (orig a b c), -Children a b c, -Children a,b (native
# array), and a single comma-glued token 'a,b' as passed by powershell.exe -File across a process
# boundary. Spec paths never contain commas, so splitting on comma is safe.
$Children = @($Children | Where-Object { $_ } | ForEach-Object { $_ -split ',' } | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() })
if ($Children.Count -eq 0) { Deny-Usage 'missing -Children' }
if (-not (Test-Path -LiteralPath $Original)) { Deny-Usage "original not found: $Original" }

$origText = Get-Content -LiteralPath $Original -Raw
$origId = ([regex]::Match($origText, '(?m)^id:\s*(SPEC-\d{4})\s*$')).Groups[1].Value
if (-not $origId) { Deny-Usage "cannot determine original id in $Original" }

$acLines = @()
foreach ($line in ($origText -split "\r?\n")) {
  if ($line -match '^\s*- \[[ xX]\]') { $acLines += $line.Trim() }
}

$childTrimmed = New-Object 'System.Collections.Generic.HashSet[string]'
$childText = @{}
foreach ($c in $Children) {
  if (-not (Test-Path -LiteralPath $c)) { Deny-Usage "child not found: $c" }
  $text = Get-Content -LiteralPath $c -Raw
  $childText[$c] = $text
  foreach ($line in ($text -split "\r?\n")) { [void]$childTrimmed.Add($line.Trim()) }
}

$violations = @()
foreach ($ac in $acLines) {
  if (-not $childTrimmed.Contains($ac)) { $violations += "lost acceptance criterion: $ac" }
}
foreach ($c in $Children) {
  $sf = ([regex]::Match($childText[$c], '(?m)^split_from:\s*(SPEC-\d{4})\s*$')).Groups[1].Value
  if ($sf -ne $origId) { $violations += "child $c missing/mismatched split_from (expected $origId, found '$sf')" }
}

if ($violations.Count) { $violations | ForEach-Object { [Console]::Error.WriteLine("check-split: $_") }; exit 2 }
"check-split: PASS ($($Children.Count) children, $($acLines.Count) criteria)"
exit 0
