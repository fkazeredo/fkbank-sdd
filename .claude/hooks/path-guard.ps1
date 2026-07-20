$ErrorActionPreference = 'Stop'
try { $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json } catch { exit 0 }
$filePath = [string]$payload.tool_input.file_path
if (-not $filePath) { $filePath = [string]$payload.tool_input.path }
if (-not $filePath) { exit 0 }
$projectRoot = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$runtimeRoot = Join-Path $projectRoot '.claude/runtime'
$roleFile = Join-Path $runtimeRoot 'current-role'
if (-not (Test-Path -LiteralPath $roleFile)) { exit 0 }
$role = (Get-Content -LiteralPath $roleFile -Raw).Trim()
if (-not $role) { exit 0 }
$p = $filePath.Replace('\','/')
$root = $projectRoot.Replace('\','/').TrimEnd('/')
if ($p.StartsWith("$root/", [StringComparison]::OrdinalIgnoreCase)) { $p = $p.Substring($root.Length + 1) }
function Test-Pattern([string]$path,[string[]]$patterns) { foreach($pattern in $patterns){ if($path -like $pattern){ return $true } }; return $false }
$qa = @('*/src/test/*/acceptance/*','src/test/*/acceptance/*','*/src/test/*/contract/*','src/test/*/contract/*','frontend/e2e/*','qa/*','docs/tests/*','docs/qa/*')
$runtime = @('.claude/runtime/*')
$allowed = @()
switch ($role) {
  'specifier' { $allowed = $runtime + @('docs/specs/*','docs/adr/*','docs/PRODUCT.md','docs/DOMAIN.md','docs/ARCHITECTURE.md','docs/ROADMAP.md') }
  'designer' { $allowed = $runtime + @('docs/specs/*','docs/exec-plans/*') }
  'qa' { $allowed = $runtime + $qa }
  'reviewer' { $allowed = $runtime }
  'security' { $allowed = $runtime + @('docs/security/reports/*') }
  'orchestrator' { $allowed = $runtime + @('docs/specs/*','docs/exec-plans/*','docs/qa/*','docs/CHANGELOG.md','docs/ROADMAP.md') }
  'reconciler' { $allowed = $runtime + @('docs/specs/*','docs/exec-plans/*','docs/CHANGELOG.md','docs/ROADMAP.md') }
  'releaser' {
    $manifestPath = Join-Path $runtimeRoot 'phase-manifest.json'
    if (Test-Path -LiteralPath $manifestPath) {
      try { $allowed = @((Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json).allowed_paths) } catch { $allowed = @() }
    }
  }
  'builder' {
    if (Test-Pattern $p $qa) { [Console]::Error.WriteLine("path-guard: builder cannot edit QA-owned path: $p"); exit 2 }
    exit 0
  }
  default { [Console]::Error.WriteLine("path-guard: unknown active role '$role'."); exit 2 }
}
if (-not (Test-Pattern $p $allowed)) { [Console]::Error.WriteLine("path-guard: role '$role' cannot write '$p'. Allowed paths are phase-scoped."); exit 2 }
exit 0
