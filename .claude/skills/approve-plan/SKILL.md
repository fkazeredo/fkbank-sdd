---
name: approve-plan
description: RELAY legacy/recovery gate — resolves a persisted AWAITING_PLAN_APPROVAL state. Normal machine-first delivery validates derived plans automatically.
disable-model-invocation: true
---

# /approve-plan <id>

Read `.claude/rules/workflow-conventions.md` first. Role: `reconciler`.

Require exactly one spec, exactly one runtime plan, and state `AWAITING_PLAN_APPROVAL`.
Present scope, non-goals, TDD sequence, risks, open doubts, and one-session fit. Approval
must be an explicit operator response; never infer it from continuing the conversation.
Record UTC date, operator, decision, observations, and `PLAN_APPROVED` in the plan and
runtime. Rejection records the reason and returns to design. Never implement or create a
branch.

End on approval: `PLAN_APPROVED`; the caller or orchestrator may enter build immediately.
