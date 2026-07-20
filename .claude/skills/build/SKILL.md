---
name: build
description: RELAY phase — TDD implementation of an approved plan (or R0/R1 inline plan), checkpoint commits, deterministic verification, dev-verification report. Manual invocation only.
disable-model-invocation: true
---

# /build <id>

Read `.claude/rules/workflow-conventions.md` first (limits!). Role: `builder` (path-guard
denies QA-owned paths). Orchestration resets phase-local context from persisted state.

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
   `.claude/runtime/<id>/dev-verification.md` (template) — commands + results, AC →
   evidence table, tests created, checks skipped + why, deviations, limitations.
6. State → `DEV_VERIFIED`.

## Rework mode (entry: QA_REWORK — max 1 cycle)
Scope = the exact executable repro of each QA finding + this slice's own suite. NEVER re-run
the full battery. Each confirmed bug ⇒ regression test that fails first (from QA's repro).
Then steps 5–6 again.

## Terminal states
DEV_VERIFIED · HUMAN_DECISION_REQUIRED · BLOCKED (2 attempts on the same failure ⇒ BLOCKED
+ block-report)

## Never
Push to develop/main · change the spec without a Decision log entry · continue past limits
· silently drop an acceptance criterion.

Return `DEV_VERIFIED`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED` to orchestration. In manual
recovery mode print the evidence-derived next command.
