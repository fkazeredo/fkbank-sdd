---
name: reconcile-workflow
description: RELAY fallback/recovery — reconciles runtime and repository evidence for the final slice, out-of-band deliveries, or drift, when the automatic closeout in /deliver-spec (and /close-sprint / /release) will not or did not run. Manual invocation only.
disable-model-invocation: true
---

# /reconcile-workflow <id>

Read `.claude/rules/workflow-conventions.md` first. Role: `reconciler`. Compare state,
spec, plan, branch, commits, PR and merge evidence without inventing progress. If a commit
is required, run `check-safe-branch reconcile`; never merge. Conflict or ambiguous human
action becomes `HUMAN_DECISION_REQUIRED`.

This is the same closeout sweep `/deliver-spec` now runs automatically at the start of the next
slice (and `/close-sprint` / `/release` run for the last slice); use this manual command only as the
fallback when no such trigger will fire, or to correct drift. When human merge evidence moves a spec
to `IMPLEMENTED`: stamp `implemented_at` (the real merge instant) in the spec frontmatter, tick the
`docs/ROADMAP.md` sprint row (`Done` ☑ + `Completed` = `implemented_at`), and move its durable R3/R4
plan from `docs/exec-plans/active/PLAN-<id>.md` to `docs/exec-plans/completed/` — all in the same
reconciliation, frontmatter and ROADMAP row kept in sync.

End: `SESSION OVER — next: <evidence-derived command>`.
