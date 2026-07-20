# QA independence and test ownership

## Two passes, in order (never merge them)
1. **Black-box** — before reading any implementation: spec, environment, API/UI, execute
   every acceptance criterion LITERALLY in the environment/profile the criterion names,
   error paths, journeys, adversarial layer per risk profile. FREEZE: write the black-box
   findings block into `qa-report.md` BEFORE opening any code.
2. **White-box** — after freezing: diff, tests (real assertions?), contracts, transactions
   (rollback, locks), logs/masking, migrations (expand/contract, existing data),
   architecture, coverage gaps.

## Ownership by test type
| Type | Owner |
|---|---|
| Unit | Builder |
| Component | Builder |
| Internal integration | Builder |
| Bug regression | Builder (from QA's executable repro — invariant: bug ⇒ failing test first) |
| Acceptance | QA |
| Black-box API | QA |
| External contract | QA |
| Critical E2E | QA |
| Test book (docs/tests/TB-<id>.md) | QA |

## QA-owned write paths (hook-enforced; source of truth: workflow-policy.yml)
`**/src/test/**/acceptance/**` · `**/src/test/**/contract/**` · `frontend/e2e/**` · `qa/**`
· `docs/tests/**` · `docs/qa/**` · `.claude/runtime/**`
(Day-0 alternative: a separate `acceptance-tests/` module — decide against the real build.)

## Bidirectional boundary
QA never edits builder-owned tests; the builder never edits QA-owned tests during the normal
cycle. An incorrect QA test goes back to QA; a valid finding goes to the builder. Exceptions
require a recorded human decision. Authorship metric: hook audit + session role + the
phase's commit range — never `git log --author` (all sessions share the operator identity).

## Conduct
Do not re-run the battery `dev-verification.md` attests (exception: checks the build does
not run, e.g. mutation on R3+ money slices). Hard bounds: HTTP calls with `--max-time 15`;
E2E stack up in ≤3 min or fail loudly; declare expected duration on the report's first line;
2 total runs max. Verdicts: PASS · PASS_WITH_OBSERVATIONS · FAIL_REWORK ·
HUMAN_DECISION_REQUIRED · BLOCKED.
