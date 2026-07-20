---
name: close-sprint
description: Autonomous RELAY Sprint closeout — reconciles and verifies the increment, runs final assurance, prepares the release, waits for human protected-branch merges, finalizes the release and opens the develop sync PR. One operator command, idempotently resumable.
disable-model-invocation: true
---

# /close-sprint <sprint> [--resume]

Read workflow conventions, ROADMAP, security gate, workflow policy, and the Sprint runtime
manifest. Role: `orchestrator`.

This is the normal command after the operator has delivered the Sprint's specs individually.
It owns every routine step through release completion. Do not return a chain of commands and do
not ask the operator to invoke `/release`, `/security-assurance`, or reconciliation manually.

1. Reconcile-sweep first: for any committed spec whose PR is merged to `develop` but still
   `IN_PROGRESS` (typically the Sprint's last slice, which has no subsequent `/deliver-spec` to
   trigger it), flip its frontmatter to `IMPLEMENTED` with `implemented_at` at the **real merge
   instant**, tick the `docs/ROADMAP.md` row (`Done` ☑ + `Completed`), and move
   `docs/exec-plans/active/PLAN-<id>.md` → `docs/exec-plans/completed/` — frontmatter and ROADMAP
   updated together (never IMPLEMENTED before the merge). Then reconcile Sprint Goal, committed specs,
   IMPLEMENTED states, PRs, exact merged SHAs, CI, defects, rework and decisions.
2. Run `verify-release` on the exact candidate SHA and preserve its output.
3. If the candidate contains R3/R4 or policy otherwise requires assurance, invoke one
   foreground `security-assurance-engineer` worker automatically with the exact SHA, risk
   inventory, approved target, control manifest, timeouts, and report path. Consume only its
   structured verdict and durable evidence. No manual session transition exists.
4. Write a concise `docs/qa/SPRINT-<n>-CLOSURE.md` containing the durable facts needed to audit
   the outcome: goal, completed/incomplete scope, carry-over, failed or waived gates, assurance
   verdict, and exact evidence. Metrics, token counts, compactions, phase diaries and narrative
   recommendations are included only when they expose a measured problem; they are not a routine
   documentation quota.
5. After `SPRINT_CLOSED`, compose the `/release` contract internally. Derive the normal Sprint
   version deterministically as the next minor version from the latest valid release (first release
   is `0.1.0`; patch versions remain for `/hotfix`), unless an explicit approved roadmap version
   exists. Prepare the release branch, version files, changelog, short release note, verification,
   push and PR without another operator command.
6. At a required protected-branch merge, end only at `AWAITING_MAIN_MERGE` (or keep watching within
   the policy window). The resume command is this same `/close-sprint <sprint> --resume`. After the
   human merge, automatically finalize the tag/GitHub Release and open the post-release sync PR by
   executing the remaining `/release` contract. After that merge, the same resume command verifies
   synchronization and ends `SPRINT_RELEASED`.

Idempotency is mandatory: detect and reuse an existing closure report, release branch, PR, tag,
GitHub Release, and sync PR when they match the exact candidate. Never duplicate them.

Never mark an unmerged spec delivered, accept security risk, merge, publish, or release.

Print concise progress while executing. At a genuine wait or terminal state print exactly one
compact terminal line: `RELAY STATE: <state> | evidence: <paths> | resume:
<same-close-sprint-command-or-none>`.
