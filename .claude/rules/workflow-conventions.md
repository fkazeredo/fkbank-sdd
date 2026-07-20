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

## Code comments and Javadoc

Comments and Javadoc explain the code to a reader who has never seen the delivery process.
Never cite `CLAUDE.md`, a spec ID (`SPEC-00NN`), an ADR number, a decision-log entry
(`DL-00NN`), or a spec's own rule ID (`OR-N`/`BR-N`) inside source comments — write the
reasoning itself instead of pointing at the document that states it. That traceability
belongs in the spec's Traceability section and the PR description; both `/build` and
`/review-pr` check new/changed comments against this rule.

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

**Role state is per session, not global.** `tools/workflow/start-phase` writes the declared role to
`.claude/runtime/roles/<session>` as well as to the shared `current-role`, and the path guard reads
the session-scoped file first. A background worker and the agent that spawned it share the runtime
directory, so whichever called `start-phase` last used to decide what *everyone* could write —
harmless when it narrowed the caller's rights, and a hole when it widened them. The shared file is
still written and is still the fallback, so anything without a session behaves exactly as before.
Nothing about this changes how a phase is declared: keep calling `start-phase`, once per
transition.

In-progress states (a session must never end on these): SPECIFYING, DESIGNING, BUILDING,
QA_RUNNING, PR_PREPARING, CI_REWORK, RELEASE_PREPARING, RELEASE_FINALIZING,
HOTFIX_SCOPING, HOTFIX_FINALIZING. A phase must declare its finite terminal states. If one
cannot be reached: write `BLOCKED`, produce `.claude/templates/block-report.md` filled, stop.

**This is enforced, not merely written down.** `.claude/hooks/stop-guard` runs on every stop,
reads `current-slice` and that slice's `state.json`, and refuses the stop while the status is one
of the in-progress states — pushing the session to carry on up to three times per phase. A turn
that simply ran out therefore resumes by itself instead of leaving the work parked.

The budget is deliberately finite. When it is spent the guard stops arguing with the session and
tells **the operator** instead, naming the slice, the state it is stuck in and the resume command.
Two failure modes are being avoided at once: a session that quietly abandons a half-finished
phase, and a session that loops forever because something genuinely cannot proceed. Silence is not
one of the outcomes.

What this cannot do is prevent a turn from ending — nothing inside the workflow can. What it
guarantees is that an unfinished phase is either continued automatically or reported out loud.

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

## Never decide an open question alone (owner-reinforced, 2026-07-20)

An open question is never closed by the agent's own judgement, however well reasoned. When a
question is genuinely open — a conflict, an ambiguity, a material technical or scope choice, a
gate that cannot be met, a rule that turns out to be unexecutable — **stop and ask the owner,
and always state a recommendation.** Presenting options without recommending one pushes the
work back onto the owner; deciding without asking takes it away from them. Do both: the facts,
the options with trade-offs, and the one you would pick, with why.

This does not license silence in the other direction. **Keep recording decisions durably** —
the spec's Decision log, an ADR, the security document, the release state, the manual, per
`human-decision-gate.md` §Durable record destination. Ask first, then write the answer down
where it outlives the session. A decision that was never recorded will be re-litigated; a
decision that was never asked about was never the agent's to make.

What still does NOT require asking is unchanged and listed in `human-decision-gate.md`
§What does NOT require asking: a written rule, an existing unequivocal pattern, a reversible
local choice, formatter output, and the autonomous push/PR boundary above. Applying a rule that
is already written is not deciding an open question — it is reading.

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

**Evidence has to discriminate.** Pointing at a command is not enough: a signal that would look
exactly the same if the claim were false is a coincidence wearing the costume of proof. Before
citing a signal, ask what it would show if you were wrong. If the answer is "the same thing", it
proves nothing and must not be offered.

A worked example, because this failure is easy to repeat. "A `java` process is running, so the
build is running" is not evidence: an editor's language server is a `java` process and is there
whether or not anything is building. "Containers for this project report an uptime of seconds" is
evidence: nothing brings a compose stack up by accident. Both came from a real command against a
real machine; only one of them could have come out differently.

## Monitoring a background worker

A phase that spawns an independent worker — QA, review, security — cannot finish until that
worker answers. Two things must be true while it waits, and neither is optional.

**Write down what you are waiting for.** When a worker is spawned, the phase writes
`.claude/runtime/<id>/awaiting.json`, and deletes it when the worker returns:

```json
{
  "waiting_on": "independent qa-engineer worker, run 2 of 2",
  "since": "2026-07-20T20:05:00Z",
  "resumes_when": "the worker returns its verdict and the harness re-invokes the session",
  "liveness": "docker ps for this project's compose stack; writes under the worker's own paths"
}
```

It exists so a wait is legible from outside the session: the operator, the stop guard and the next
session can all tell an ongoing wait from an abandoned phase without interrogating the agent that
stopped answering.

**The worker's completion notification is authoritative. Nothing else is.** Do not poll for it,
and never infer a verdict from artifacts the worker happens to have written — a report on disk is
not a verdict until the worker says it is. Run `tools/workflow/worker-status <id>` when liveness
is genuinely in doubt; it gathers the signals that discriminate and states plainly which ones it
could not obtain.

Two traps, both hit while delivering SPEC-0002:

- **Silence is not death.** A worker running a long verification writes nothing for ten minutes or
  more, because the build does not touch the files being watched. Timestamps go blind for exactly
  as long as the most interesting phase lasts.
- **A process list is not liveness.** Matching on a runtime's name finds every unrelated process
  that shares it. Prefer a signal tied to this project — its compose stack, its own paths — over
  one tied to a language.

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
