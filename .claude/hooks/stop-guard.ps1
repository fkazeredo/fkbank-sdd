# Refuses to let a session end while a slice is still mid-flight, and tells the operator when it
# cannot refuse any longer.
#
# Three things this has to get right, each of which it previously got wrong:
#
# 1. It must push back more than once. Exiting quietly whenever the harness reported that this
#    hook had already fired made the guard a one-shot: it nudged the first time and then let every
#    later stop through, which is indistinguishable from having no guard at all on exactly the long
#    sessions that need one. A per-state budget replaces that — enough pushes to recover from a
#    turn that simply ran out, bounded so a genuinely stuck session cannot spin forever.
#
# 2. The terminal marker must be looked for in the last thing the assistant actually said.
#    Searching the whole transcript matched the marker wherever it appeared, including inside the
#    instructions that describe it, so the check reported success from the first message of every
#    session.
#
# 3. When the budget runs out it must say so to the human, not only to the model. A guard whose
#    warnings are addressed exclusively to the thing that stopped responding is a guard nobody
#    reads. Exhausting the budget prints a message the operator sees, so an unfinished slice is
#    loud rather than silent.
#
# CHECKPOINTED is a legitimate resumable stop, not an in-progress state: it means a clean-context
# continuation is safer than pushing this turn further, so the guard never force-continues it. It
# is accepted only when its evidence artifact checkpoint.md is present, mirroring how BLOCKED
# requires block-report.md.

$ErrorActionPreference = 'SilentlyContinue'

try { $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json }
catch { [Console]::Error.WriteLine('stop-guard: invalid hook JSON.'); exit 2 }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$runtime = Join-Path $root '.claude/runtime'
$slicePath = Join-Path $runtime 'current-slice'
$counterPath = Join-Path $runtime 'stop-guard-nudges'

# How many times the session is pushed to carry on before the guard gives up and escalates to the
# operator. Three survives a turn that merely ran out, and is small enough that a session which
# genuinely cannot continue reaches a human quickly instead of looping.
$maxNudges = 3

function Complete-Cleanly {
  param([string]$Counter)
  Remove-Item -LiteralPath $Counter -Force -ErrorAction SilentlyContinue
  exit 0
}

function Send-OperatorAlert {
  param([string]$Message)
  # A Stop hook's stderr is read by the model. Only stdout carrying this shape reaches the person.
  [Console]::Out.WriteLine(([ordered]@{ systemMessage = $Message } | ConvertTo-Json -Compress))
}

if (-not (Test-Path -LiteralPath $slicePath)) { Complete-Cleanly $counterPath }
$slice = (Get-Content -LiteralPath $slicePath -Raw).Trim()
if (-not $slice) { Complete-Cleanly $counterPath }

$dir = Join-Path $runtime $slice
$statePath = Join-Path $dir 'state.json'
if (-not (Test-Path -LiteralPath $statePath)) {
  [Console]::Error.WriteLine("stop-guard: missing state.json for $slice.")
  exit 2
}
try { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
catch { [Console]::Error.WriteLine("stop-guard: invalid state.json for $slice."); exit 2 }

$inProgress = @(
  'SPECIFYING', 'DESIGNING', 'BUILDING', 'QA_RUNNING', 'PR_PREPARING', 'CI_REWORK',
  'RELEASE_PREPARING', 'RELEASE_FINALIZING', 'HOTFIX_SCOPING', 'HOTFIX_FINALIZING'
)

if ($inProgress -contains $state.status) {
  # The budget is keyed to the state, so reaching a new phase earns a fresh one. Without that, a
  # long delivery would spend its whole allowance on the first phase and then coast silently
  # through every phase after it.
  $signature = "$slice/$($state.status)"
  $nudges = 0
  if (Test-Path -LiteralPath $counterPath) {
    $recorded = (Get-Content -LiteralPath $counterPath -Raw).Trim() -split '\|'
    if ($recorded.Count -eq 2 -and $recorded[0] -eq $signature) {
      [void][int]::TryParse($recorded[1], [ref]$nudges)
    }
  }
  $nudges++
  Set-Content -LiteralPath $counterPath -Value "$signature|$nudges" -NoNewline -Encoding UTF8

  if ($nudges -le $maxNudges) {
    [Console]::Error.WriteLine(
      "stop-guard: $slice is still $($state.status) - the session must not end here. Continue the " +
      "phase to a terminal state, or write BLOCKED with a filled " +
      ".claude/templates/block-report.md if it genuinely cannot reach one. " +
      "(push $nudges of $maxNudges)")
    exit 2
  }

  $resumeId = $slice -replace '^SPEC-0*', ''
  Send-OperatorAlert (
    "RELAY: $slice is still $($state.status), and the session stopped anyway after $maxNudges " +
    "attempts to continue it. Nothing is lost - the work is on disk and the phase is resumable. " +
    "Resume with: /deliver-spec $resumeId --resume")
  exit 0
}

if (-not (Test-Path -LiteralPath (Join-Path $dir 'metrics.json'))) {
  [Console]::Error.WriteLine("stop-guard: missing metrics.json for $slice.")
  exit 2
}
if ($state.status -eq 'BLOCKED' -and -not (Test-Path -LiteralPath (Join-Path $dir 'block-report.md'))) {
  [Console]::Error.WriteLine('stop-guard: BLOCKED requires block-report.md.')
  exit 2
}
if ($state.status -eq 'CHECKPOINTED' -and -not (Test-Path -LiteralPath (Join-Path $dir 'checkpoint.md'))) {
  [Console]::Error.WriteLine('stop-guard: CHECKPOINTED requires checkpoint.md.')
  exit 2
}
if ($state.status -in @('AWAITING_SPEC_INPUT', 'AWAITING_SPEC_APPROVAL', 'HUMAN_DECISION_REQUIRED', 'READY') `
    -and -not ((Test-Path -LiteralPath (Join-Path $dir 'spec-path.txt')) -or (Test-Path -LiteralPath (Join-Path $dir 'decision-request.md')))) {
  [Console]::Error.WriteLine('stop-guard: specifier requires a spec-path or decision-request artifact.')
  exit 2
}

$transcript = [string]$payload.transcript_path
if ($transcript -and (Test-Path -LiteralPath $transcript)) {
  # Only what the assistant said last. The marker is described in the instructions the session was
  # given, so anything that searches the whole transcript finds it there and passes a session that
  # never printed it.
  $lastSaid = $null
  foreach ($line in [IO.File]::ReadLines($transcript)) {
    if (-not $line.Trim()) { continue }
    try { $entry = $line | ConvertFrom-Json } catch { continue }
    if ($entry.type -ne 'assistant') { continue }
    $said = ''
    foreach ($block in @($entry.message.content)) {
      if ($block.type -eq 'text') { $said += $block.text }
    }
    if ($said.Trim()) { $lastSaid = $said }
  }
  if ($lastSaid -and ($lastSaid -notmatch '(SESSION OVER . next:|RELAY STATE:)')) {
    [Console]::Error.WriteLine(
      'stop-guard: the closing message carries no machine-state marker. End with a line of the ' +
      'form "RELAY STATE: <state> | evidence: <paths> | resume: <command-or-none>".')
    exit 2
  }
}

Complete-Cleanly $counterPath
