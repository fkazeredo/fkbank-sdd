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
QA never edits builder-owned tests. The builder does not edit QA-owned tests *while QA still
owns the work* — an in-flight QA cycle keeps its own artifacts. A valid finding goes to the
builder; an incorrect QA test goes back to QA **for as long as QA has a cycle left**.

**When the work has returned to the main agent, the main agent owns every file** (owner
decision, 2026-07-20). Once QA's cycles are spent, or QA has handed back a finding it will not
act on, the main agent edits QA-owned files directly instead of stalling on a boundary that has
no one on the other side. The boundary is a separation of responsibility during the cycle, not
a permanent lock, and it is never a reason to leave a known defect in the tree. Do it under the
`qa` role (`tools/workflow/start-phase`) so the hook audit still records who wrote what, and
say in the commit message that the main agent picked the work up and why.

Independence of *verdicts* is what matters and is unchanged: the main agent still may not
declare its own QA verdict, and orchestration must never turn an independent worker's judgement
into self-approval (CLAUDE.md invariant 7).

Authorship metric: hook audit + session role + the phase's commit range — never
`git log --author` (all sessions share the operator identity).

## Conduct
Do not re-run the battery `dev-verification.md` attests (exception: checks the build does
not run, e.g. mutation on R3+ money slices). Hard bounds: HTTP calls with `--max-time 15`;
E2E stack up in ≤3 min or fail loudly; declare expected duration on the report's first line;
2 total runs max. Verdicts: PASS · PASS_WITH_OBSERVATIONS · FAIL_REWORK ·
HUMAN_DECISION_REQUIRED · BLOCKED.
