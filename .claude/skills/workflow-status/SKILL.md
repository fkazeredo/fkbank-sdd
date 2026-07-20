---
name: workflow-status
description: RELAY drift/fallback — read-only status of every slice (runtime states, branches, open PRs) and the expected next command. Manual invocation only.
disable-model-invocation: true
---

# /workflow-status [id]

Read-only. No role, no state changes, no actions.

## Steps
1. Read every `.claude/runtime/*/state.json` (or the one for `<id>`), the spec frontmatter
   status, `git branch --show-current`, `gh pr list`.
2. Print per slice (pt-BR): slice · runtime state · spec state · branch · open PR (never
   listed as "delivered") · expected next command. Flag inconsistencies (e.g. in-progress
   state with no live session). A spec `IN_PROGRESS` with runtime DONE whose PR is merged is NOT a
   defect but a normal pending-auto-reconcile state — surface it as such; treat it as drift only if it
   persists with no upcoming trigger.
3. If a slice's PR was merged by the human, report the merge evidence and note that the next
   `/deliver-spec` (or `/close-sprint` / `/release`) will AUTO-RECONCILE it (flip to `IMPLEMENTED` with
   `implemented_at` at the real merge instant, tick the ROADMAP row `Done` ☑ + `Completed`, move
   `docs/exec-plans/active/PLAN-<id>.md` → `docs/exec-plans/completed/`). Recommend the manual
   `/reconcile-workflow <id>` only as a FALLBACK — the Sprint's final slice, an out-of-band delivery,
   or to correct drift. This skill never changes the spec, plan, runtime, branch, or PR.

End: `SESSION OVER — next: <the most useful command per the states>`.
