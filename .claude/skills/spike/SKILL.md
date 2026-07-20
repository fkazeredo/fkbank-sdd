---
name: spike
description: RELAY support — timeboxed investigation with a declared budget and a written conclusion. Never touches production code. Manual invocation only.
disable-model-invocation: true
---

# /spike <description>

Read `.claude/rules/workflow-conventions.md` first.

## Steps
1. Declare the question, the timebox (≤1 session) and the success criterion BEFORE starting.
2. Investigate: read code/docs, run throwaway experiments under `.claude/runtime/spike-*/`
   or a scratch branch that is never merged.
3. Write the conclusion to `.claude/runtime/spike-<slug>.md`: answer, evidence, options,
   recommendation, what remains unknown. If it should outlive the runtime ⇒ suggest /adr or
   a spec Decision log entry to the human.

## Never
Write production code · merge anything · exceed the timebox (stop and report instead).

End: `SESSION OVER — next: <recommendation>`.
