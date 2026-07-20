---
name: adr
description: RELAY support — record a genuine structural decision as an ADR in docs/adr/. Manual invocation only.
disable-model-invocation: true
---

# /adr <description>

Read `.claude/rules/workflow-conventions.md` first. Only for a **genuine structural
decision** (a baseline change or a new architectural choice) — business decisions go to the
spec's Decision log. When /design-slice or /build detects a structural decision, they STOP
and suggest this skill to the human — they never invoke it.

## Steps
1. Short interview (batched): context, forces, options considered.
2. Draft on `docs/adr/ADR-TEMPLATE.md`: alternatives with trade-offs, decision,
   consequences.
3. Human approval → write `docs/adr/ADR-NNNN-<slug>.md` (next sequential number).

End: `SESSION OVER — next: <resume the interrupted phase, if any>`.
