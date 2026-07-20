---
name: impact
description: RELAY support — impact analysis via ONE bounded read-only subagent (foreground), only after explicit human approval in-session. Manual invocation only.
disable-model-invocation: true
---

# /impact <question>

Read `.claude/rules/workflow-conventions.md` first.

## Steps
1. Present the question and the subagent budget (what it will read, expected scope) and
   ask the operator to approve — `permissions.ask` will also prompt; both are intended.
2. On approval, dispatch ONE bounded read-only subagent (the term of art — never "fork"):
   foreground, tools Read/Grep/Glob only, the declared budget in its prompt. One at a time,
   never in parallel, never in background (background tasks are disabled).
3. Synthesize the answer (pt-BR) with file/line evidence. In a small repository, prefer
   inline Grep in this session over any subagent — say so and do it.

## Never
More than one subagent · background execution · write tools in the subagent · spawn
without the operator's explicit approval.

End: `SESSION OVER — next: <the phase this analysis serves>`.
