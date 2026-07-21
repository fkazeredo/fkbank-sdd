# Developer verification — SPEC-<id>

Expected duration declared: <min> · Actual: <min> · Model/effort: <values> · Compactions: <n>

## Strict DEV_VERIFIED gate (all must hold before writing DEV_VERIFIED; mirrors state.json)
| Signal | Value | Note |
|---|---|---|
| verify-slice | <pass\|fail> | canonical `tools/quality/verify-slice` result |
| applicable E2E | <pass\|fail\|not_applicable> | trigger: <user journey / auth / public route / external integration / frontend route / Compose topology / none> |
| acceptance evidence | <complete\|incomplete> | every criterion has real executable developer evidence, NOT deferred to QA |
| candidate SHA | <git rev-parse HEAD> | the SHA this evidence was produced against |

## Commands executed and results
<!-- one line per command: command → exit/result -->

## Acceptance criteria → automated evidence
| Criterion | Test / evidence | Status |
|---|---|---|

## Discriminating evidence (per critical invariant / concurrency / schema / authz / idempotency / state-transition claim)
<!-- What would this evidence show if the claimed protection were absent or mis-scoped?
     Evidence that would look the same with and without the protection is INVALID. -->
| Claim | Test / probe | What it would show if the protection were absent or mis-scoped |
|---|---|---|

## Tests created (by type)

## Checks skipped and why

## Deviations from the plan (Decision Ladder re-opened?)

## Known limitations
