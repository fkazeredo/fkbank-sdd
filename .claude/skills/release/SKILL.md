---
name: release
description: RELAY support — first runs the reconcile-sweep to close out any merged-but-unreconciled slice (typically the Sprint's last, which has no subsequent /deliver-spec), then release Prepare/Finalize with SemVer, changelog consolidation, security gate, human merges and tagging only after main merge. Manual invocation only.
disable-model-invocation: true
---

# /release <version> [--production]

Read `.claude/rules/workflow-conventions.md` and `.claude/rules/security-gate.md` first.
Role: `releaser`. Auto-detect the step by state. Without `--production`, this is an
internal pilot/pre-release (see the production definition in workflow-policy.yml).

## Prepare (from develop)
1. Reconcile-sweep + pre-checks: first close out any prior slice merged-but-not-yet-reconciled (a
   spec still `IN_PROGRESS` whose PR is merged to develop — typically the Sprint's last slice, which
   has no later `/deliver-spec` to trigger it): flip to `status: IMPLEMENTED` + stamp `implemented_at`
   at the **real merge instant**, tick the `docs/ROADMAP.md` row (`Done` ☑ + `Completed`), and move
   `docs/exec-plans/active/PLAN-<id>.md` → `docs/exec-plans/completed/`; never mark IMPLEMENTED before
   the merge (`/reconcile-workflow` remains the manual fallback for residual drift). Then pre-checks:
   valid SemVer · develop up to date · clean tree · tag absent · sprint slices merged.
   After creating `release/x.y.z`, run `check-safe-branch release` and start role `releaser`
   with phase `release-prepare` plus an explicit allow-list manifest (version files,
   lockfiles changed by versioning, changelog, release notes, and runtime only).
2. **Security gate:** release contains ≥1 R3/R4 slice?
   - No ⇒ record `SECURITY_NOT_APPLICABLE` + justification in the release notes.
   - Yes + track not approved + `--production` ⇒ **BLOCKED** (production waits for the
     approved track). Yes + internal ⇒ HUMAN_DECISION_REQUIRED: present the risk, record
     the human decision, state `SECURITY_OBSERVATIONS`. Never write SECURITY_VERIFIED
     without the track executed.
3. Branch `release/x.y.z` → set version (`tools/release/set-version`) removing `-SNAPSHOT`
   → consolidate CHANGELOG `[Unreleased]` → `## [x.y.z] - date` (new empty `[Unreleased]`
   above; history never deleted) → narrative release note in `docs/release-notes/` →
   `tools/quality/verify-release` → PR `release/x.y.z → main` → state `AWAITING_MAIN_MERGE`.

## Finalize (after the HUMAN merge to main)
Confirm PR merged + CI green on the exact SHA → start `release-finalize` with an explicit
allow-list manifest → annotated tag `vx.y.z` (only here — never
before the human merge) → push the tag → GitHub Release with the note → branch
`chore/post-release-x.y.z` from main → bump to next `-SNAPSHOT` → **sync PR to develop**
(never a direct back-merge — develop is protected) → state `AWAITING_DEVELOP_SYNC_MERGE`.

## Never
Tag from develop · merge any PR · move/delete a published tag · weaken the security gate.

End: `SESSION OVER — next: human merge (main)` or `human merge (develop sync)`.
