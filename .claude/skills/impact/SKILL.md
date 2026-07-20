---
name: impact
description: RELAY support -- orchestrated impact analysis with evidence. Ultracode may use unrestricted xhigh dynamic workflows, agents, and background tasks.
disable-model-invocation: true
---

# /impact <question>

Read `.claude/rules/workflow-conventions.md` first.

Ultracode owns the analysis topology. It may create any useful dynamic workflow, agent team,
subagent hierarchy, parallel workstream, or background task without asking the operator to approve
the orchestration itself. Select depth and breadth from the question and risk.

Synthesize one coherent pt-BR answer with file/line or command evidence, disagreements resolved,
unknowns declared, and the affected delivery phase identified. Human approval is required only
when the analysis reaches a material product/architecture decision governed by invariant 8, not
to authorize agents or parallelism.

End: `SESSION OVER -- next: <the phase this analysis serves>`.
