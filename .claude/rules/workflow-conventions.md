# Workflow conventions (read at the start of EVERY skill)

## Autonomous execution boundary (owner-reinforced)
Pushing a `feature/bugfix/chore/hotfix/release` work branch to `origin` and opening its Pull
Request (`/pr`, `/release`, `/hotfix`) is ordinary, autonomous RELAY execution — never pause to
ask permission for it. The only actions that stop for a human are CLAUDE.md invariant 6's
absolute prohibitions: commit or push directly to `main`/`develop`, force-push, merge, move or
delete a published tag, approve your own PR. Nothing else about Git in this workflow is a
confirmation gate.

## Language
Talk to the operator in pt-BR, continuously — never leave them without knowing what is
happening. Every repository artifact is en-US. Only manuals below `docs/manual/**` may have
paired `pt-BR` and `en-US` editions. The root README, specs, plans, reports, commits, Pull
Requests, code, tests, identifiers, and comments are English-only. There are no historical or
workflow exceptions. Run `tools/workflow/validate-doc-language` before claiming documentation
conformance.

## ID resolution
Skill arguments accept `7` ≡ `0007` ≡ `SPEC-0007`. Resolve with glob `docs/specs/*0007*`.
More than one match ⇒ `HUMAN_DECISION_REQUIRED` (list the matches). No match ⇒ list the
available IDs and end the session without changing anything.

## Roles and persistent state

Normal operation is machine-first: a top-level command advances all eligible transitions in
one invocation. Granular phase skills are contracts used by orchestration and recovery tools.
First actions of each transition, in order:
1. Write your role to `.claude/runtime/current-role` (`designer` | `builder` | `qa` |
   `specifier` | `reviewer` | `security` | `orchestrator` | `reconciler` | `releaser`) and the operation id to
   `.claude/runtime/current-slice`, using `tools/workflow/start-phase`.
2. Write the entry state to `.claude/runtime/<id>/state.json`
   (`{"slice":"SPEC-00NN","status":"<STATE>","risk":"RN","base_sha":"...","updated":"ISO"}`).
After each transition:
1. Write the terminal state to `state.json` (+ counters you consumed).
2. Append phase metrics to `.claude/runtime/<id>/metrics.json` (duration, model, compaction
   events observed, files read, retries).
3. Clear `current-role` and `current-slice` (write empty files).
4. Return a structured state/evidence result to orchestration. Only the top-level command
   prints a compact terminal summary and resume command.

In-progress states (a session must never end on these): SPECIFYING, DESIGNING, BUILDING,
QA_RUNNING, PR_PREPARING, CI_REWORK, RELEASE_PREPARING, RELEASE_FINALIZING,
HOTFIX_SCOPING, HOTFIX_FINALIZING. A phase must declare its finite terminal states. If one
cannot be reached: write `BLOCKED`, produce `.claude/templates/block-report.md` filled, stop.

## Spec state vs slice state
Spec lifecycle lives in structured YAML frontmatter: DRAFT → AWAITING_SPEC_APPROVAL → READY →
IN_PROGRESS → IMPLEMENTED (or SUPERSEDED / BLOCKED), carrying `owner_approved_at` (delivery
start) and `implemented_at` (delivery end). Slice execution state lives in
`.claude/runtime/<id>/state.json`. Invoking `/deliver-spec` or an approved Sprint manifest is
explicit owner approval of the exact validated spec hash and may produce READY.
`/design-slice` flips the spec to IN_PROGRESS; the human merge makes the slice reconcilable, and the
next `/deliver-spec` (or `/close-sprint` / `/release`) automatically sweeps any merged-but-unreconciled
slice — flipping it to IMPLEMENTED and stamping `implemented_at` with the real merge instant (never
before the merge). `/workflow-status` and `/reconcile-workflow` are retained only as drift/fallback
tools (final slice, out-of-band deliveries, correcting drift), no longer a required routine step.

**Delivery status is recorded in two committed places, kept in sync — never one without the
other:** (1) the spec frontmatter (`status` + `owner_approved_at` + `implemented_at`, the source
of truth), and (2) the `docs/ROADMAP.md` sprint table (`Done` ☐ pending / ☑ merged · `Started` ·
`Completed`, shown in Brasília time — BRT/UTC-3, `DD/MM/YYYY HH:MM:SS`; the frontmatter keeps the
canonical UTC instant). `/deliver-spec` writes the new slice's ROADMAP `Started` when it approves and, in the same run, first
auto-reconciles any prior merged-but-unreconciled slice — ticking its `Done` ☑ and writing `Completed`
(Brasília time) from the real merge instant, alongside the frontmatter IMPLEMENTED flip. `/close-sprint`
and `/release` run the same reconcile-sweep so the last slice (which has no subsequent `/deliver-spec`)
is closed out; `/reconcile-workflow` remains the manual fallback for the final slice or any drift.

## Hard limits (from .claude/workflow-policy.yml — never exceed, never negotiate)
Ultracode orchestration has no repository-level limits: xhigh dynamic workflows, agent teams,
subagents, parallel implementation, recursive orchestration, and background tasks are available
to Claude without operator babysitting. Outcome limits remain: 2 QA runs per slice · 1 QA rework
cycle · 1 automatic
CI fix · 1 flaky retry (diagnosis only) · 2 attempts on the same failure · 3 spec interview
blocks · 15 min CI watch · 0 merges · 0 force pushes.

## Questions in batch
In `/spec` and `/design-slice`, accumulate material doubts and present ONE Human Decision
Request per checkpoint (targets: ≤1 for R2, ≤2 for R3 after the interview). Targets never
authorize assuming an answer — a new material ambiguity stops the flow again. During
`/build`, before asking: consult spec → plan → ADRs → Decision log → an unequivocal existing
pattern. Still material and ambiguous ⇒ stop (see human-decision-gate.md).

## Evidence
Every claim of success points to a command + its output, a test, a diff, an artifact or an
observed behavior. Declare skipped checks and why. Never state "CI passed" when no required
checks are configured — say "No required CI checks are configured."

## Every delivery: user-facing docs, then CI monitoring and correction
Before opening the PR (`/pr`), update the user-facing docs the delivery touched: the user
manual under `docs/manual/**` in **both en-US and pt-BR**, and the English `README.md` (the
README stays English-only — only `docs/manual/**` is paired; see §Language above). A delivery
that changed the product face without updating both is incomplete.

After the PR opens, CI is **monitored and corrected**, never left red-and-forgotten: `/pr` (and
`/deliver-spec`) watch the PR checks for up to 15 minutes; a failing check gets the single
allowed automatic correction cycle (`/fix-pr`, or `/deliver-spec`'s one CI fix) and a re-watch.
A PR is only mergeable with green required checks; the human merges (the agent never does).

**Watch CI by event, never by fixed sleeps.** Blocking on `sleep` and re-polling wastes the
operator's time twice over: it reacts late when a job fails early, and it re-checks a run that is
still queued. Use the run watcher, which returns the moment the run settles:

```bash
gh run watch <run-id> --exit-status   # blocks until done; non-zero exit if any job failed
gh pr checks <pr> --watch --fail-fast # equivalent at PR level; returns on the first failure
```

Get the run id from `gh pr checks <pr>` or `gh run list --branch <branch> --limit 1`.

Polling still has its place — alongside the watcher, to report progress while a long run is in
flight — but it must be **short and bounded**: `gh pr checks <pr>` every **20–30 seconds**, never
the multi-minute sleeps that leave the operator staring at nothing. A job in this repository
takes 30 s to 2 min, so a 3-minute sleep means learning about a failure minutes after it
happened. Prefer the watcher to decide *when to act*, and short polls only to *narrate*.

Two further rules that cost real time when ignored:
- **Read the failing job's log before changing anything** (`gh run view --job <id> --log-failed`).
  A red check whose cause was guessed burns the single correction cycle on the wrong fix.
- **A second failure with a different cause is not a second attempt at the same failure.** The
  "2 attempts on the same failure" limit is per failure; a genuinely new cause is a new failure,
  but the "1 automatic CI fix" budget still binds the session — once spent, stop and ask.

## Verification scripts
One deterministic command per level — never improvise the battery:
`tools/quality/verify-fast` (≤5 min) · `tools/quality/verify-slice` (≤15 min) ·
`tools/quality/verify-release` (≤30 min). Use the `.ps1` on the Windows operator machine,
`.sh` on POSIX/CI. Every external process gets a timeout; a hung process is a failure.
