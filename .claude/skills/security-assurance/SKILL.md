---
name: security-assurance
description: RELAY final-delivery gate -- independent risk-based heavy security validation and durable verdict. Manual invocation only.
disable-model-invocation: true
---

# /security-assurance <sprint|version>

Read workflow conventions, the security gate, workflow policy, and
`docs/security/SECURITY-ASSURANCE-TRACK.md`. Role: `security`. Run as an independent assurance responsibility within Ultracode orchestration
`security-assurance-engineer` worker selected automatically by closure orchestration.

## Preconditions

All intended slices are merged into the candidate branch; authoritative CI is green on the
exact SHA; the worktree is clean; the track is approved; the candidate and risk inventory are
recorded. Otherwise end `BLOCKED` without claiming assurance.

## Controls

1. Freeze SHA, scope, exposed surfaces, assets, trust boundaries, data classes, and R3/R4
   slices in `docs/security/reports/<target>-security-assurance.md`.
2. Review the threat model: authentication, authorization, tenant/customer isolation,
   injection, SSRF, deserialization, replay/idempotency, concurrency, secrets, sensitive logs,
   privacy, abuse cases, and money invariants.
3. Run the repository's canonical SAST, secret, dependency, and license checks. Run container
   and IaC scanning when those assets exist.
4. Run adversarial authorization/isolation tests and negative contract tests.
5. Start the release-like environment and run DAST plus the approved automated penetration
   test profile against only the declared local/test target. Never target production.
6. Execute risk-specific abuse cases for every R3/R4 slice, including race and replay attacks
   for money-moving behavior.
7. Preserve commands, versions, results, artifacts, false-positive dispositions, residual
   risks, and required manual penetration-test work.

Canonical entry point: `tools/security/verify-assurance.ps1 -Target <target> -RequiresHeavy`
on Windows or `tools/security/verify-assurance.sh <target> --requires-heavy` on POSIX. A
missing mandatory pinned tool/profile returns `BLOCKED`; never downgrade it to a warning.

## Verdict

- `SECURITY_VERIFIED`: every mandatory applicable control ran and no unresolved High/Critical
  finding remains.
- `SECURITY_OBSERVATIONS`: only when policy permits a non-production candidate and every
  unresolved item has an owner and deadline; requires explicit owner risk decision.
- `BLOCKED`: track not approved, environment unavailable, mandatory control skipped, evidence
  incomplete, or unresolved High/Critical finding.

Never accept risk, alter production code, weaken tooling, test an undeclared external target,
merge, or publish. End: `SESSION OVER -- next: /close-sprint <sprint> --resume`.
