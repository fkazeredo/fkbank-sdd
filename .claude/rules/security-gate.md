# Security in the core and final-delivery assurance

## Engineering-level security (every slice, when applicable)
Spec includes: actors, permissions, assets, data, trust boundaries, abuse cases, controls,
logs, auditability, rollback. PR/CI gates (progressive): secrets (gitleaks), dependencies,
static analysis, authorization tests, contracts, architecture rules.

## Specialized final-delivery gate
Verdicts: SECURITY_NOT_APPLICABLE · SECURITY_VERIFIED · SECURITY_OBSERVATIONS · BLOCKED.

- Release with only R0–R2 slices ⇒ `SECURITY_NOT_APPLICABLE` is valid when no relevant
  attack surface demands the track — record the justification.
- A Sprint or release containing ≥1 R3/R4 slice ⇒ the approved specialized track is mandatory.
  `/close-sprint` runs the complete track over the Sprint's integrated candidate at closure (the
  orchestrator runs the heavy wrapper; the independent `security-assurance-engineer` worker judges
  the evidence and issues the verdict); `/release` runs it — or reuses a matching exact-candidate
  verdict — for the release candidate; an explicit `/security-assurance` is the manual entry.
  Internal candidates may end in `SECURITY_OBSERVATIONS` when every observation is Low/Medium,
  policy-bounded, and has an owner and deadline; only material residual risk requires a recorded
  owner decision. Production requires `SECURITY_VERIFIED` on the exact candidate SHA.
- Risk acceptance NEVER produces `SECURITY_VERIFIED` — that state is reserved for the track
  actually executed.
- Production release definition: destined for an environment exposed to end users; every
  sprint release before that milestone is internal pilot/pre-release.
