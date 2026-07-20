---
name: fix-pr
description: RELAY support — triage a red CI or human review comments and apply ONE automatic correction round. Second failure blocks. Manual invocation only.
disable-model-invocation: true
---

# /fix-pr <number>

Read `.claude/rules/workflow-conventions.md` first. Role: `builder`.

## Preconditions
Open PR with red CI (`state.json` = CI_FAILED) or actionable human review comments.

## Steps
1. Run `tools/git/check-safe-branch ... pr --allow-dirty`. State → `CI_REWORK`. Triage the CI log / comments into exactly one class:
   config/permission · flaky/isolation · real regression · snapshot drift · infra.
2. Apply **one** correction for the diagnosed class. Flaky: 1 retry for DIAGNOSIS only —
   passing on retry does not re-trust the test; it becomes an isolation fix or a recorded
   debt.
3. Push. Watch the second CI execution (≤15 min): green → `AWAITING_HUMAN_REVIEW` ·
   still running → `CI_PENDING` · **failed again → `BLOCKED`** + block-report (never a
   second automatic round).

## Never
More than one automatic correction round · disable or weaken a gate to pass · merge.

End: `SESSION OVER — next: human review & merge` or the block report's recommendation.
