---
name: deliver-spec
description: Machine-first RELAY command -- first auto-reconciles any prior merged-but-unreconciled slice at the real merge instant, then advances one exact spec through design, build, automatic isolated QA/review, feature push, PR and CI. Stops only at real external or material-decision states.
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
   any prior slice that is merged-but-not-yet-reconciled -- a spec still `IN_PROGRESS` whose PR is
   merged to `develop` -- and close it out at the **real merge instant**: flip its frontmatter to
   `status: IMPLEMENTED` + stamp `implemented_at`, tick its `docs/ROADMAP.md` sprint row (`Done` [x] +
   `Completed`, Brasilia time; frontmatter keeps the canonical UTC instant), and move
   `docs/exec-plans/active/PLAN-<id>.md` -> `docs/exec-plans/completed/` -- frontmatter and ROADMAP row
   updated together, in sync (the two-committed-places invariant). The first slice has no predecessor
   => skip cleanly. Never mark `IMPLEMENTED` before the merge. Then resolve exactly one spec, validate
   frontmatter/sections, show a compact scope/risk summary, record approval (frontmatter
   `owner_approved_at`/`owner_approved_hash` and the new slice's `docs/ROADMAP.md` sprint row
   `Started`), and set `READY`.
2. R0/R1: create an inline plan and classify implementation-fit inline (decision-ladder.md
   §Implementation-fit gate), writing `state.json.fit`; R2+: execute the `/design-slice`
   contract, which classifies fit and writes it. A plan strictly derived from the approved spec
   may record owner approval from this command; a material choice still requires a separate
   Human Decision Request. Do not execute `/build` yet — the fit gate (step 3) is binding.
3. **Fit gate (binding — consumes `/design-slice`'s result).** Run
   `tools/workflow/check-slice-gate <id> fit`; enter BUILDING only when `fit == FIT`.
   `TOO_LARGE` ⇒ do not implement; run **Spec splitting** below. `HUMAN_DECISION_REQUIRED` ⇒
   stop with one Human Decision Request. On `FIT`, execute the `/build` contract and canonical
   verification. Never merge or push a protected branch.
4. **QA preflight (before spawning QA).** Run `tools/workflow/check-slice-gate <id> qa-preflight`
   (read-only). A failing preflight consumes NO QA run and is NOT a QA failure: return the slice
   to `BUILDING`, name the missing or stale developer evidence, and re-verify. Only on a passing
   preflight, invoke the independent `qa-engineer` workflow automatically with the spec,
   observable target, dev attestation, risk profile, budget, and QA-only write paths. Consume
   only its structured verdict and evidence. On `QA_REWORK`, perform the single allowed
   correction cycle (which re-runs the preflight) and invoke QA once more automatically.
5. Execute `/pr`, push only the work branch, open the PR, and watch CI within policy limits.
   R3/R4 automatically invoke the independent read-only `pr-reviewer` workflow after the PR
   exists; R0-R2 invoke it only on policy/diff triggers.
6. Automatically apply the one permitted CI correction and recheck. End only at
   `AWAITING_HUMAN_MERGE`, `CI_PENDING`, `CHECKPOINTED`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED`.

When a clean-context continuation is safer than pushing this turn — the fit gate is invalidated
by scope growth, an unexpected structural change appears, unrelated integration failures
accumulate, context loss is observed, or a worker ownership/runtime-state collision occurs —
persist state, branch, exact SHA, completed work, failing evidence and next action into
`.claude/runtime/<id>/checkpoint.md`, set status `CHECKPOINTED`, and stop. Resume from that
evidence in a fresh context with `/deliver-spec <id> --resume` — never a different phase command.

## Spec splitting (on TOO_LARGE)

Do not implement an oversized spec. First GENERATE a concrete proposal, then ask ONE question.

The proposal (shown BEFORE asking) presents: the original id + title; the detected fit signals;
the recommended child count (1..N); for each child its id/title/goal/scope/acceptance
criteria/dependencies; which original acceptance criteria move to each child; the Roadmap rows
that replace the original row; every `depends_on` reference to be redirected; the original spec
file to be deleted. Use the next available numeric IDs (SPEC-0019, SPEC-0020, …) — NEVER suffixed
IDs like `SPEC-0002A`. Prefer vertical, independently testable children; do not split purely by
layer unless the architecture makes that genuinely executable. Every original business rule,
edge case, failure mode, open decision and acceptance criterion must have a destination — no
silent scope loss, no invented behavior.

Ask EXACTLY ONE question: "Do you want me to apply this split?"
- **Decline** ⇒ leave all files unchanged; do not implement the oversized spec; end at
  `HUMAN_DECISION_REQUIRED`; recommend refining the split or narrowing the original.
- **Approve** ⇒ ONE atomic documentation transformation: (1) create all child spec files;
  (2) add ONLY `split_from: SPEC-<original-id>` to each child's frontmatter — no other new field;
  (3) move the original rules/edge cases/decisions/acceptance criteria into the appropriate
  children; (4) redirect every affected `depends_on`; (5) replace the original executable Roadmap
  row with the child rows; (6) preserve the original capability name in ONE short Roadmap group
  comment, e.g. `<!-- Split from SPEC-0002: Sign-up and account opening -->`; (7) delete the
  original spec file; (8) run `tools/workflow/validate-specs`, `tools/workflow/check-split`
  (proves no acceptance criterion was lost), `tools/workflow/validate-doc-language`, and the
  workflow smoke tests; (9) if any validation fails, repair the transformation before reporting
  success; (10) STOP after successful validation — do NOT begin implementing the first child.

Do NOT introduce: a SPLIT lifecycle state; a permanent split matrix; a split manifest; a
split-specific content hash; an archive copy of the deleted spec; a split report; an extra
approval phase; a Changelog entry merely for splitting. Git history + the Roadmap group comment
+ `split_from` are the only traceability.

The command is idempotent. Reinvocation reads spec hash, runtime, Git, PR, CI, QA and review
evidence, skips completed transitions, and advances from the first incomplete state. The opening
reconcile-sweep is idempotent too: a prior slice already `IMPLEMENTED` (or with no merged
predecessor) is detected as closed and skipped, and `implemented_at` is never re-stamped.

Print exactly one compact terminal line: `RELAY STATE: <state> | evidence: <paths> | resume:
<command-or-none>`. No phase-by-phase approval prompts.
