---
name: review-pr
description: RELAY phase contract — isolated read-only PR review worker. Mandatory R3/R4, risk-triggered otherwise. Invoked automatically or manually for recovery.
disable-model-invocation: true
---

# /review-pr <number|all>

Read `.claude/rules/workflow-conventions.md` first. Role: `reviewer` (writes only the report
in `.claude/runtime/`). Run as the foreground `pr-reviewer` worker selected by orchestration.

## Steps
1. `gh pr view/diff` the target PR(s) — read-only.
2. Review against: spec fidelity · the plan's Decision Ladder record vs the diff ·
   docs/ARCHITECTURE.md rules · test honesty (assertions, race + replay on money routes) ·
   contracts · migrations · observability · diff-level security (authorization, exposure,
   masking) · risk classification correctness.
3. Write `.claude/runtime/review-pr-<n>.md`: findings (ID, severity, confidence, evidence
   file/line), what was NOT reviewed, and a short **human review focus** list (pt-BR
   summary to the operator).

## Terminal states
Report produced (no state machine change — the slice stays where /pr left it) ·
HUMAN_DECISION_REQUIRED · BLOCKED.

## Never
Comment on the PR · approve · merge · edit any file outside `.claude/runtime/` · spawn
subagents.

Return `REVIEW_PASSED`, `REVIEW_FINDINGS`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED` to the
orchestrator.
