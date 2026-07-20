---
name: deliver-spec
description: Machine-first RELAY command — first auto-reconciles any prior merged-but-unreconciled slice at the real merge instant, then advances one exact spec through design, build, automatic isolated QA/review, feature push, PR and CI. Stops only at real external or material-decision states.
disable-model-invocation: true
---

# /deliver-spec <id> [--resume]

Read workflow conventions and the resolved spec. Role: `orchestrator`. This is the
owner-approved exception to manual phase-by-phase invocation; it composes existing phase
contracts but never relaxes their gates or limits.

## Approval semantics

Invoking this command without `--resume` is explicit owner approval of the exact spec content
hash shown by the command. Record the hash, UTC timestamp, and operator in the Decision Log and
frontmatter. Refuse implicit approval when the spec has open material questions, conflicts,
unknown scope, or unresolved baseline dependencies. Any later semantic spec change invalidates
the approval and stops with `HUMAN_DECISION_REQUIRED`.

The approval hash is SHA-256 over UTF-8 content with line endings normalized to LF and the
`owner_approved_at`/`owner_approved_hash` values normalized to `null`. This avoids a
self-referential hash and makes verification deterministic across Windows and POSIX.
Use `tools/workflow/spec-hash.ps1` or `.sh`; never reproduce the algorithm ad hoc.

## Execution

1. **Reconcile-sweep first (automatic delivery closeout).** Before approving the new slice, detect
   any prior slice that is merged-but-not-yet-reconciled — a spec still `IN_PROGRESS` whose PR is
   merged to `develop` — and close it out at the **real merge instant**: flip its frontmatter to
   `status: IMPLEMENTED` + stamp `implemented_at`, tick its `docs/ROADMAP.md` sprint row (`Done` ☑ +
   `Completed`, Brasília time; frontmatter keeps the canonical UTC instant), and move
   `docs/exec-plans/active/PLAN-<id>.md` → `docs/exec-plans/completed/` — frontmatter and ROADMAP row
   updated together, in sync (the two-committed-places invariant). The first slice has no predecessor
   ⇒ skip cleanly. Never mark `IMPLEMENTED` before the merge. Then resolve exactly one spec, validate
   frontmatter/sections, show a compact scope/risk summary, record approval (frontmatter
   `owner_approved_at`/`owner_approved_hash` and the new slice's `docs/ROADMAP.md` sprint row
   `Started`), and set `READY`.
2. R0/R1: create an inline plan and execute `/build`; R2+: execute the `/design-slice` contract.
   A plan strictly derived from the approved spec may record owner approval from this command;
   a material choice still requires a separate Human Decision Request.
3. Execute the `/build` contract and canonical verification. Never merge or push a protected
   branch.
4. Invoke one foreground `qa-engineer` worker automatically with the spec, observable target,
   dev attestation, risk profile, budget, and QA-only write paths. Consume only its structured
   verdict and evidence. On `QA_REWORK`, perform the single allowed correction cycle and invoke
   QA once more automatically.
5. Execute `/pr`, push only the work branch, open the PR, and watch CI within policy limits.
   R3/R4 automatically invoke one foreground read-only `pr-reviewer` worker after the PR
   exists; R0–R2 invoke it only on policy/diff triggers.
6. Automatically apply the one permitted CI correction and recheck. End only at
   `AWAITING_HUMAN_MERGE`, `CI_PENDING`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED`.

The command is idempotent. Reinvocation reads spec hash, runtime, Git, PR, CI, QA and review
evidence, skips completed transitions, and advances from the first incomplete state. The opening
reconcile-sweep is idempotent too: a prior slice already `IMPLEMENTED` (or with no merged
predecessor) is detected as closed and skipped, and `implemented_at` is never re-stamped.

Print exactly one compact terminal line: `RELAY STATE: <state> | evidence: <paths> | resume:
<command-or-none>`. No phase-by-phase approval prompts.
