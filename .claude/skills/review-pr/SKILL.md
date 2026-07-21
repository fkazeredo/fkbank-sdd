---
name: review-pr
description: RELAY phase contract — isolated read-only PR review worker. Mandatory R3/R4, risk-triggered otherwise. Invoked automatically or manually for recovery.
disable-model-invocation: true
---

# /review-pr <number|all>

Read `.claude/rules/workflow-conventions.md` first. Role: `reviewer` (writes only the report
in `.claude/runtime/`). Run as the independent `pr-reviewer` responsibility inside the Ultracode workflow.

## Steps
1. `gh pr view/diff` the target PR(s) — read-only.
2. Review against: spec fidelity · the plan's Decision Ladder record vs the diff ·
   docs/ARCHITECTURE.md rules · test honesty (assertions, race + replay on money routes) ·
   **discriminating test evidence**: for every critical invariant, concurrency control, schema
   constraint, authorization rule, idempotency rule and state transition, ask "what would this
   test show if the protection were absent or mis-scoped?" — evidence identical with and without
   the protection is INVALID (a concurrency test must cross the race window, a schema test attack
   the exact constraint, a regression fail-then-pass, a mocked timeout does not prove real client
   timeout; for an R3/R4 concurrency or schema invariant at least one deterministic structural
   test must accompany any probabilistic trials) · contracts · migrations · observability ·
   diff-level security (authorization,
   exposure, masking) · risk classification correctness · behavioral DDD (aggregate invariants
   and transitions live on domain classes; records are justified messages/self-validating values;
   Lombok does not create setters or bypass valid construction).
3. Write `.claude/runtime/review-pr-<n>.md`: findings (ID, severity, confidence, evidence
   file/line), what was NOT reviewed, and a short **human review focus** list (pt-BR
   summary to the operator). Write findings factually (workflow-conventions.md §Reporting
   language): name the failed behavior, its evidence, root cause and missing prevention — no
   theatrics, no minimizing.

## Terminal states
Report produced (no state machine change — the slice stays where /pr left it) ·
HUMAN_DECISION_REQUIRED · BLOCKED.

## Never
Comment on the PR · approve · merge · edit any file outside `.claude/runtime/` · spawn
subagents.

Return `REVIEW_PASSED`, `REVIEW_FINDINGS`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED` to the
orchestrator.
