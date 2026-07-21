# FKBANK Security Assurance Track

Status: **APPROVED BY OWNER — 2026-07-17** (`track_approved: true` in
`.claude/workflow-policy.yml`).

This is the independent, risk-based final-delivery security gate. It supplements slice-level
engineering security and runs against the exact Sprint/release candidate after authoritative
CI. The `security-assurance-engineer` owns execution; only the owner accepts residual risk.

## Applicability

- Candidate containing only R0–R2 slices: the closure report may record
  `SECURITY_NOT_APPLICABLE` when it demonstrates that no security-relevant surface changed.
- Candidate containing any R3/R4 slice: this track is mandatory.
- Production release: every mandatory applicable control must execute. Risk acceptance never
  creates `SECURITY_VERIFIED`.

## Mandatory control families

1. **Candidate integrity** — exact SHA, clean tree, CI evidence, inventory of changed surfaces.
2. **Threat model** — assets, actors, trust boundaries, abuse cases, data classification, and
   risk mapping for authentication, authorization, isolation, money, privacy, and operations.
3. **Static assurance** — SAST, secrets, dependencies, licenses, and repository configuration.
4. **Architecture** — only `com.fkbank.{domain,application,infra}` roots; dependency direction;
   bounded-context isolation; no framework imports in domain; no domain entity at a delivery
   boundary.
5. **Adversarial behavior** — authorization matrix, cross-customer isolation, injection,
   replay, idempotency, concurrency, rate/abuse behavior, sensitive logging, and privacy.
6. **Dynamic assurance** — release-like local/test deployment, DAST, and the approved automated
   penetration profile. External or production targets require separate written authorization.
7. **Supply chain and deployment** — container and IaC scanning when applicable, provenance,
   least privilege, secret injection, secure defaults, and rollback/roll-forward evidence.
8. **Risk-specific drills** — every R3/R4 spec contributes named attacks and expected evidence.

Tools must be pinned and invoked through repository wrappers before their output can become
authoritative. Until a wrapper exists for an applicable mandatory control, the verdict is
`BLOCKED`; an agent must not improvise an unreviewed scanner and call the track complete.

## Finding policy

- Critical or High unresolved: `BLOCKED`.
- Medium/Low: owner, remediation deadline, exploitability and affected SHA are required.
  Policy-bounded observations may continue automatically for an internal candidate; only a
  material product, financial, privacy, or exploitability decision requires owner acceptance.
- False positive: reproducible disposition and reviewer evidence are required.
- Manual penetration work: scope, authorization, tester, environment, timebox, evidence, and
  retest must be recorded when the threat model requires it.

## Verdicts

- `SECURITY_VERIFIED`: all applicable mandatory controls executed on the exact candidate and
  no unresolved High/Critical finding exists.
- `SECURITY_OBSERVATIONS`: permitted only for an internal/non-production candidate with an
  explicit owner decision and bounded remediation records.
- `BLOCKED`: missing approval/evidence/environment/control, mandatory skipped control, stale
  candidate, or unresolved High/Critical finding.

Reports live in `docs/security/reports/` and include commands, tool versions, timestamps,
artifacts, skipped controls, findings, dispositions, residual risks, and exact SHA.
