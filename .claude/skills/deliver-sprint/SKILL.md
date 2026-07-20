---
name: deliver-sprint
description: Machine-first RELAY command — executes a Sprint slice by slice using deliver-spec, dependency ordered and idempotently resumable. Closing remains a separate command.
disable-model-invocation: true
---

# /deliver-sprint <sprint> [--resume]

Read workflow conventions, `docs/ROADMAP.md`, and the frontmatter of specs assigned to the
Sprint. Role: `orchestrator`.

1. On first invocation, show Sprint Goal, ordered slices, risk distribution, dependencies,
   unresolved Human Decisions, and the exact spec hashes. Invocation approves this exact Sprint
   commitment and the included spec versions; record a runtime manifest.
2. Topologically order slices while preserving roadmap order where dependencies permit. Never
   run implementations in parallel.
3. For the next incomplete slice, execute the `/deliver-spec` contract. Persist current slice,
   completed slices, PR/merge evidence, carry-over, metrics, and next action.
4. QA and review workers are invoked automatically by each `/deliver-spec`. Pause only for a
   material decision, CI failure outside the automatic-fix limit, unavailable external system,
   or human merge. `--resume` verifies evidence before continuing.
5. A blocked prerequisite blocks dependent slices. An unrelated later slice may continue only
   when the Sprint manifest explicitly proves independence and the owner accepts the carry-over.
6. After all committed slices have human merge evidence, end `SPRINT_DELIVERED`. Sprint closure
   is deliberately separate: the next command is `/close-sprint <sprint>`.

Never change Sprint scope silently, merge, push protected branches, bypass independent phases,
or convert missing evidence into success.

Print exactly one compact terminal line: `RELAY STATE: <state> | evidence: <paths> | resume:
<command-or-none>`.
