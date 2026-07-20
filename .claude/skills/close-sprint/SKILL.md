---
name: close-sprint
description: RELAY Sprint closure — runs the reconcile-sweep for any merged-but-unreconciled slice (typically the Sprint's last slice), reconciles delivery evidence, runs release verification, invokes the final security gate when applicable, and produces a durable closure report. Manual invocation only.
disable-model-invocation: true
---

# /close-sprint <sprint> [--resume]

Read workflow conventions, ROADMAP, security gate, workflow policy, and the Sprint runtime
manifest. Role: `orchestrator`.

1. Reconcile-sweep first: for any committed spec whose PR is merged to `develop` but still
   `IN_PROGRESS` (typically the Sprint's last slice, which has no subsequent `/deliver-spec` to
   trigger it), flip its frontmatter to `IMPLEMENTED` with `implemented_at` at the **real merge
   instant**, tick the `docs/ROADMAP.md` row (`Done` ☑ + `Completed`), and move
   `docs/exec-plans/active/PLAN-<id>.md` → `docs/exec-plans/completed/` — frontmatter and ROADMAP
   updated together (never IMPLEMENTED before the merge). Then reconcile Sprint Goal, committed specs,
   IMPLEMENTED states, PRs, exact merged SHAs, CI, defects, rework, decisions, time, tokens,
   compactions, and workflow overhead.
2. Run `verify-release` on the exact candidate SHA and preserve its output.
3. If the candidate contains R3/R4 or policy otherwise requires assurance, invoke one
   foreground `security-assurance-engineer` worker automatically with the exact SHA, risk
   inventory, approved target, control manifest, timeouts, and report path. Consume only its
   structured verdict and durable evidence. No manual session transition exists.
4. Write `docs/qa/SPRINT-<n>-CLOSURE.md` with achieved goal, completed work, incomplete work,
   carry-over, findings, first-pass CI, phase metrics, assurance verdict, and recommendation.
5. End `SPRINT_CLOSED` with `/release`, or `BLOCKED` with actionable evidence.

Never mark an unmerged spec delivered, accept security risk, merge, publish, or release.

Print exactly one compact terminal line: `RELAY STATE: <state> | evidence: <paths> | resume:
<command-or-none>`.
