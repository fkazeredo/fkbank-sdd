# Security decisions

Durable record of security-posture decisions (human-approved). Format:
`SEC-NNNN — YYYY-MM-DD — decision — decided by <owner>`.

## SEC-0001 — 2026-07-20 — branch protection accepted as absent for the v0.1.0 window — decided by fkazeredo

Transcribed by the agent under the `security` role at the owner's instruction; the acceptance is
the owner's, made in the `/close-sprint 1` session after being shown the finding, the options and
a recommendation against accepting. Corresponds to finding **SEC-F-02** in
`docs/security/reports/SPRINT-1-068d94f.md`.

**Finding.** The repository has no branch protection of any kind: `gh api
repos/fkazeredo/fkbank-sdd/rulesets` returns `[]`, and
`.../branches/develop/protection` returns 404 "Branch not protected". `main` does not exist on
`origin` at all; the default branch is `develop`. Independently confirmed by the
security-assurance worker on both candidates.

**Why it matters.** CLAUDE.md §Git and invariant 6 both assert that `main` and `develop` are
"protected — PR only, enforced by GitHub rulesets", and the agent's refusal to push, merge or
force-push is written as a complement to a server-side control. With no server-side control,
that complement is the whole of it. Any client — an agent that misreads a rule, a script, a
human in a hurry — can push straight to `develop`, force-push over merged history, or delete it.
The append-only guarantee the product makes about its ledger has no equivalent guarantee about
the history that proves the ledger was built correctly.

**Exposure.** Public repository, single maintainer. No credential or customer data is at risk;
what is at risk is the integrity and auditability of delivery history, which is the evidentiary
basis every RELAY verdict rests on.

**Decision.** Accepted for the `v0.1.0` internal pre-release window. The owner was offered the
alternative of configuring rulesets before the release — twice, the second time after the cost
was known to include the loss of `SECURITY_VERIFIED` — and chose acceptance both times.

**Consequence carried knowingly.** An applicable mandatory control (assurance family 3,
repository configuration) fails on the exact candidate. `SECURITY_VERIFIED` is therefore
unavailable for this release by the track's own rule, and risk acceptance never creates it. The
candidate can reach at most `SECURITY_OBSERVATIONS`.

**Remediation.** Configure repository rulesets for `main` and `develop` — pull request required,
no direct push, no force-push, required status checks — before the first release destined for an
environment exposed to end users. Owner: fkazeredo. Deadline: before the first production
release, per the production-release definition in `.claude/workflow-policy.yml`.

**Affected SHA.** Every SHA in the `v0.1.0` pre-release window, which is the window the decision
above scopes. Named explicitly: `d9aad61`, `068d94f` and `3dec822` (the `release/0.1.0` tip under
assurance run 3). The finding is a property of the GitHub repository's configuration, not of any
commit's content, so it holds identically on every one of them.

> **Amendment — 2026-07-20, security-assurance run 3.** As first written this line read
> "`068d94f` (Sprint 1 candidate) and every SHA before it", which by its own words excluded
> `3dec822` — a *descendant* of `068d94f` and the actual candidate under assurance. The
> acceptance would have gone stale the moment the candidate advanced, which is precisely what the
> track's affected-SHA requirement exists to prevent. Corrected by the security-assurance worker
> to name the window the decision already scoped. **No term of the owner's decision was changed:**
> not its scope (the `v0.1.0` internal pre-release window), not its conditions, not its
> remediation deadline, and not its recorded consequence that `SECURITY_VERIFIED` is unavailable.
> The underlying finding was independently re-confirmed on `3dec822` (rulesets `[]`,
> `branches/develop/protection` 404, `main` absent from `origin`). If the owner intended a
> narrower SHA scope than the stated window, this amendment is the thing to reverse.

> **Amendment — 2026-07-20, security-assurance run 5.** The `v0.1.0` window advanced to its
> version-stamped tip: `ea4e1ed` (F-01 frontend fix), `61631c3` (release Prepare `0.1.0`) and
> `e12711b` (test-only UTC clock fix), the `release/0.1.0` tip now under assurance. `e12711b` is
> enumerated here so the affected-SHA list does not go stale as the candidate advanced — the same
> defect the run-3 amendment corrected. The finding was independently re-confirmed on `e12711b`
> (rulesets `[]`, `branches/develop/protection` 404, `branches/main/protection` 404 — `main` absent
> from `origin`); it is a property of the GitHub repository's configuration, not of any commit's
> content, so it holds identically. **No term of the owner's decision was changed:** not its scope
> (the `v0.1.0` internal pre-release window, which `e12711b` is inside), not its conditions,
> remediation deadline, or recorded consequence that `SECURITY_VERIFIED` is unavailable.
