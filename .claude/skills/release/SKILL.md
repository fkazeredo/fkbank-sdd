---
name: release
description: RELAY support — reconciles merged-but-unreconciled slices, then assists with owner-controlled release preparation and finalization. It never tags, pushes, merges, or publishes. Manual invocation only.
disable-model-invocation: true
---

# /release <version> [--production]

This is the operator-driven release command. `/close-sprint` does **not** compose or trigger it:
Sprint closure only verifies delivery, and releasing is a separate decision the owner makes and
invokes explicitly here (whether releasing a closed Sprint or an out-of-band change). The owner
handles the protected-branch merges, tags, and GitHub Releases this contract prepares.

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
     approved track). Yes + internal ⇒ continue when observations are Low/Medium, policy-bounded,
     request one decision only for material residual risk. Never write SECURITY_VERIFIED without
     the track executed.
3. Branch `release/x.y.z` → set version (`tools/release/set-version`) removing `-SNAPSHOT`
   → consolidate CHANGELOG `[Unreleased]` → `## [x.y.z] - date` (new empty `[Unreleased]`
   above; history never deleted) → narrative release note in `docs/release-notes/` →
   `tools/quality/verify-release` → PR `release/x.y.z → main` → state `AWAITING_MAIN_MERGE`.

## Finalize assistance (after the HUMAN merge to main)
Confirm the owner-reported merge and CI status, then print the exact candidate SHA, proposed tag,
release-note path and post-release synchronization steps. Stop at `READY_FOR_OWNER_RELEASE`.
The owner alone creates or pushes the tag, publishes the GitHub Release, and decides whether to
create the post-release version/synchronization branch. This command never performs those actions.

## Never
Tag or push anything · publish a GitHub Release · merge any PR · move/delete a published tag ·
create a post-release synchronization branch · weaken the security gate.

End: `SESSION OVER — next: owner-controlled release actions`.
