# RELAY human decisions

This ledger distinguishes resolved owner decisions from genuinely pending material choices.
Machine-derived transitions do not create Human Decision Requests.

| ID | Pending decision | Blocking effect |
|---|---|---|
| HD-01 | Whether `limits` is its own `domain.limits` bounded context (delivery and technical mechanisms in `application`/`infra`) | Blocks the module map and design of SPEC-0013. |
| HD-02 | Whether `privacy` is a separate bounded context or responsibility of `domain.customer` | Blocks approval/design of SPEC-0014. |
| HD-03 | Product baseline (`docs/PRODUCT.md`) approval | Blocks spec approval; individual specs still require exact-hash approval through `/deliver-spec`. |
| HD-04 | Domain baseline, dependency map, and ledger-only rule (`docs/DOMAIN.md`) approval | Blocks design of dependent specs; material domain changes require an explicit decision. |
| HD-05 | Root package structure: only `com.fkbank.{domain,application,infra}` roots, use cases belong to domain | Recorded in ARCHITECTURE and CLAUDE.md once approved. |
| HD-06 | Roadmap and Sprint composition approval | `/deliver-sprint <sprint>` approves the exact displayed manifest; conflict or scope change blocks. |
| HD-07 | Split review for oversized specs, including SPEC-0013, SPEC-0014, and SPEC-0016 | Blocks those specs from becoming `READY`. |
| HD-08 | Credit-card scope beyond the current virtual debit-card wording | Blocks any inferred credit-card behavior. |
| HD-09 | User-profile scope beyond the documented MVP exclusions | Blocks any inferred profile behavior. |
| HD-10 | Automated final-delivery Security Assurance track approval | Execution evidence remains mandatory for applicable releases. |
| HD-11 | Explicit owner approval for each specification | `/deliver-spec` records approval of the exact validated hash; later semantic change invalidates it. |

The operator must record the decision in the spec Decision Log, ADR, baseline document, or
Security Assurance track according to `.claude/rules/human-decision-gate.md`.
