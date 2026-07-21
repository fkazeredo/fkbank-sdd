---
name: design-slice
description: RELAY phase — budgeted reading, full Decision Ladder, executable plan and the binding Implementation-fit gate for a READY spec (R2+). Never changes production code. Manual invocation only.
disable-model-invocation: true
---

# /design-slice <id>

Read `.claude/rules/workflow-conventions.md` and `.claude/rules/decision-ladder.md` first.
Role: `designer` (path-guard restricts writes to `.claude/runtime/`, `docs/exec-plans/`, and
`docs/specs/` for the frontmatter transition).

## Preconditions
Spec resolved by ID, frontmatter `status: READY`, risk R2+. Working tree clean.
R0/R1 do not use this skill (inline plan in /build).

## Steps
1. Create branch `feature/<id>-<slug-from-spec-filename>`. Create `.claude/runtime/<id>/`
   + `state.json` (base SHA, risk) → state `DESIGNING`. Flip the spec frontmatter to
   `IN_PROGRESS`.
2. Budgeted reading: the spec in full + ONLY the sections its `reading_list` names. An
   empty repository or an already-summarized scope ⇒ zero exploration.
3. Executability gate: any acceptance criterion that does not map to a test AS WRITTEN ⇒
   propose the rewrite and ask (batched). Nothing is planned over an ambiguous criterion.
4. Execute the FULL Decision Ladder (rungs 1–8) for the solution approach. Record the
   `## Decision Ladder` block in the plan. For backend domain changes, explicitly assign
   invariants and state transitions to aggregates/value objects. Do not plan a record-shaped
   data model with behavior coordinated by adapters. Identify which immutable boundary types
   may remain records and which lifecycle-bearing types must be behavioral classes.
5. Write `.claude/runtime/<id>/plan.md`: Understanding · Modules touched · Files ·
   Patterns to reuse · Contracts/Data/Migrations · TDD sequence (behavior-sized steps) ·
   Builder test plan · QA focus · Docs impact · Risks · Non-goals · Open doubts (batched)
   · Decision Ladder record · **Implementation-fit gate** (decision-ladder.md
   §Implementation-fit gate — the eight signals + classification rule): classify the slice
   `FIT` | `TOO_LARGE` | `HUMAN_DECISION_REQUIRED` and WRITE the verdict to `state.json.fit`.
   A slice that does not fit the session is a slice problem, not a session problem: on
   `TOO_LARGE` propose the split BEFORE approval (never reach PLAN_APPROVED); genuinely
   ambiguous ⇒ `HUMAN_DECISION_REQUIRED`. A narrative claim ("no value when split") never
   overrides the gate; a child may be independently integrable and testable without being
   independently releasable.
6. R3/R4: copy the approved plan to `docs/exec-plans/active/PLAN-<id>.md` (durable,
   versioned — runtime is git-ignored).
7. Validate that every plan decision is strictly derived from the approved spec and durable
   architecture. Reach `PLAN_APPROVED` only when the fit verdict is `FIT` (the binding
   `tools/workflow/check-slice-gate <id> fit`, run by `/deliver-spec`, passes) and continue
   automatically. `TOO_LARGE` ⇒ end at the split proposal, not PLAN_APPROVED; a new material
   choice ⇒ `HUMAN_DECISION_REQUIRED`.

## Terminal states
PLAN_APPROVED · TOO_LARGE (fit gate blocked — split required before implementation) ·
HUMAN_DECISION_REQUIRED · CHECKPOINTED · BLOCKED

`CHECKPOINTED` is a clean-context restart, not a failure: when scope growth invalidates the fit
gate, an unexpected structural change appears, or context loss is observed, persist state,
branch, exact SHA, completed work and next action into `.claude/runtime/<id>/checkpoint.md`,
leave status `CHECKPOINTED`, and resume from that evidence in a fresh context with the same
top-level command `--resume`.

## Never
Write production code · use native Plan Mode · delegate material decisions · exceed the reading
budget without declaring it. Ultracode may freely orchestrate xhigh workflows, agents, and
background tasks to execute this design contract.

Return the terminal result to orchestration. `/approve-plan` remains only for recovery from a
legacy `AWAITING_PLAN_APPROVAL` state.
