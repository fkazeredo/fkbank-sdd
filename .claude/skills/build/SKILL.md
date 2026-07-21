---
name: build
description: RELAY phase — TDD implementation of an approved plan (or R0/R1 inline plan), checkpoint commits, deterministic verification, dev-verification report. Manual invocation only.
disable-model-invocation: true
---

# /build <id>

Read `.claude/rules/workflow-conventions.md` first (limits!). Role: `builder` (path-guard
denies QA-owned paths). Orchestration resets phase-local context from persisted state.
Once QA has no cycle left, or hands back a finding it will not act on, the work has returned to
the main agent and QA-owned files are its to fix — do that under the `qa` role so the hook audit
stays truthful (`.claude/rules/qa-ownership.md` §Bidirectional boundary).

## Preconditions (one of)
- R2+: `state.json` = PLAN_APPROVED and the approval record exists in the plan.
- R0/R1: Light Spec READY; derive and validate an inline plan ≤15 lines before code. Stop only
  if that derivation exposes a material decision not approved by the spec.
- Rework mode: `state.json` = QA_REWORK (see below).

## Steps
1. Resolve the spec. For R0/R1, when no branch exists, require `develop` as the clean
   origin, run `tools/git/check-safe-branch branch-source`, and create exactly
   `chore/<id>-<slug>` (R0 docs/chore), `bugfix/<id>-<slug>` (R1 bug), or
   `feature/<id>-<slug>` (R1 feature/refactor). Record it in runtime. On the work branch,
   run `check-safe-branch implementation`. State → `BUILDING`; read plan + spec + named files.
2. TDD micro-cycles per coherent behavior: failing test (RED) → minimum to green →
   refactor → `tools/quality/verify-fast` → **commit** (checkpoint — recovery from any
   dead session is git + runtime, never memory). Money routes: the deterministic race test
   and the `Idempotency-Key` replay test are written WITH the behavior, not after
   (CLAUDE.md invariant 2).
   For domain work, tests exercise ubiquitous-language behavior and invalid-state rejection,
   not only generated accessors. Aggregates/entities use encapsulated classes; records remain
   limited to suitable immutable messages or self-validating value objects. Lombok may remove
   boilerplate, but `@Data`, public setters and invariant-bypassing constructors are forbidden
   on aggregates and entities.
   **Discriminating evidence** (workflow-conventions.md §Evidence): every test for a critical
   invariant, concurrency control, schema constraint, authorization rule, idempotency rule or
   state transition must answer "what would this show if the protection were absent or
   mis-scoped?" A concurrency test crosses the intended race window (not merely two concurrent
   starts); a schema test attacks the exact constraint; a state-machine test covers before,
   during and after the protected state; a regression test fails against the defect and passes
   after the fix; a mocked external timeout does not prove real client timeout behavior. R3/R4
   concurrency or schema invariants need ≥1 deterministic structural test alongside probabilistic
   trials. Evidence that looks the same with and without the protection is invalid.
3. Deviation from the plan (unplanned component/dependency/abstraction/contract/reuse):
   re-open the Decision Ladder for that decision. Material ⇒ HUMAN_DECISION_REQUIRED
   (batch doubts per checkpoint). New production dependency ⇒ always human, with
   `.claude/templates/new-dependency.md`.
4. **Structural atomicity:** an unexpected structural tree
   change (package moves, module split) ⇒ stop → HUMAN_DECISION_REQUIRED with options:
   (a) replan the slice on the new structure; (b) park the structural change as its own
   slice; (c) abandon it and finish on the current structure. Any prior aggregate PASS
   becomes **STALE**: keep historical results, re-run at minimum `verify-slice` plus
   architecture, test discovery, packaging and affected integrations; a QA verdict issued
   before the change is invalidated.
5. Finish: run `tools/quality/verify-slice` in full. Fill
   `.claude/runtime/<id>/dev-verification.md` (human-readable report) and
   `.claude/runtime/<id>/dev-verification.json` (machine-readable evidence, using the template) — commands + results, AC →
   evidence table (with the discriminating question answered per protection), tests created,
   checks skipped + why, deviations, limitations. Write the report factually
   (workflow-conventions.md §Reporting language): name any failed behavior, its evidence, root
   cause and missing prevention; do not dramatize or minimize.
6. **Strict DEV_VERIFIED gate.** `DEV_VERIFIED` means a fully integrated, passing candidate —
   not a partial implementation awaiting QA. Write to `state.json`: `verify_slice`
   (`pass`/`fail`), `e2e` (`pass`/`fail`/`not_applicable`), `e2e_applicable` (boolean), `acceptance_evidence`
   (`complete`/`incomplete`), and `candidate_sha` (the HEAD the evidence was produced against).
   Run `tools/workflow/check-slice-gate <id> dev-verified`; set `DEV_VERIFIED` only when it
   passes AND all nine hold: (1) `verify-slice` passes; (2) every mandatory check applicable to
   the diff passes; (3) E2E passes when the change affects a user journey, auth flow, public
   route, external integration, frontend route, or Compose topology; (4) every acceptance
   criterion has real executable developer evidence in the appropriate environment; (5) required
   concurrency behavior exercised against the real persistence engine + declared isolation;
   (6) required external timeout/callback/retry/emulator behavior exercised across the real
   process/network boundary; (7) no mandatory gate recorded as a "known limitation"; (8) no
   failing test excused merely because it predates a change that intentionally invalidated its
   assumption; (9) no acceptance behavior deferred to QA for its first execution. Any condition
   false ⇒ stay `BUILDING`, or write `BLOCKED`/`HUMAN_DECISION_REQUIRED`. **"QA owns this test
   path" never means the builder skips validating the behavior**: the builder need not author
   QA-owned acceptance tests, but the behavior MUST already have been exercised successfully
   (builder integration test, existing E2E, real-stack command, or another executable probe)
   before QA begins.

## Rework mode (entry: QA_REWORK — max 1 cycle)
Scope = the exact executable repro of each QA finding + this slice's own suite. NEVER re-run
the full battery. Each confirmed bug ⇒ regression test that fails first (from QA's repro).
Then steps 5–6 again.

## Terminal states
DEV_VERIFIED · CHECKPOINTED · HUMAN_DECISION_REQUIRED · BLOCKED (2 attempts on the same failure
⇒ BLOCKED + block-report)

`CHECKPOINTED` when a clean-context continuation is safer than pushing this turn (scope grew and
invalidated the fit gate, an unexpected structural change appeared, unrelated integration
failures accumulated, context loss was observed, or a worker ownership/runtime-state collision
occurred): persist `.claude/runtime/<id>/checkpoint.md` (state, branch, exact SHA, completed
work, failing evidence, next action), leave status `CHECKPOINTED`, and resume from that evidence
in a fresh context with the same top-level command `--resume`.

## Never
Push to develop/main · change the spec without a Decision log entry · continue past limits
· silently drop an acceptance criterion.

Return `DEV_VERIFIED`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED` to orchestration. In manual
recovery mode print the evidence-derived next command.
