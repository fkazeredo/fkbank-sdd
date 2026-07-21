---
name: qa
description: RELAY phase contract — isolated QA worker, black-box first then white-box, with QA-only writes. Invoked automatically or manually for recovery.
disable-model-invocation: true
---

# /qa <id>

Read `.claude/rules/workflow-conventions.md` and `.claude/rules/qa-ownership.md` first.
Role: `qa` (path-guard enforces QA-owned paths). Run as the independent `qa-engineer`
responsibility inside the Ultracode workflow (strong model for R3/R4 per workflow-policy.yml).

## Preconditions
`state.json` = DEV_VERIFIED. Risk R2+. Before QA is spawned, orchestration runs the read-only
QA preflight `tools/workflow/check-slice-gate <id> qa-preflight`; a failing preflight consumes NO
QA run and returns the slice to `BUILDING` (missing or stale developer evidence — this is NOT a
QA failure). QA is adversarial and independent — falsify the criteria, inspect test quality, add
independent acceptance coverage — but is NEVER the first stage to assemble or execute the
complete feature; that integration is the builder's, proven before QA begins.

## Steps
1. State → `QA_RUNNING`. Read the spec + `dev-verification.md` + `state.json`. Treat
   dev-verification as the builder's attestation — do NOT re-run the attested battery
   (exception: checks the build does not run, e.g. mutation testing on R3+ money slices).
2. **Black-box pass** (before reading ANY implementation or diff): bring the stack up
   (E2E up ≤3 min or fail loudly); execute EVERY acceptance criterion literally, in the
   environment/profile the criterion names; error paths and journeys; adversarial layer by
   profile — Standard (R2) or Critical (R3/R4: money boundaries, concurrency probes,
   authorization matrix). Conditional lenses by diff triggers declared in dev-verification:
   migration ⇒ database lens (expand/contract, locks, existing data) · auth/PII/webhook ⇒
   security lens (access, isolation, injection, sensitive logs, server-derived values) ·
   hot money route ⇒ performance lens (the spec's latency budget).
3. **FREEZE**: write the complete black-box block into `.claude/runtime/<id>/qa-report.md`
   BEFORE opening any code.
4. **White-box pass**: diff · tests (real assertions? race/replay present on money?) ·
   contracts/OpenAPI · transactions (rollback, lock order) · logs/masking · migrations ·
   architecture rules · coverage gaps.
5. Write acceptance/contract/E2E tests you own (QA-owned paths ONLY). Promote useful manual
   cases to `docs/tests/TB-<id>.md` (numbered steps, preconditions, expected per step).
6. Verdict → state: QA_VERIFIED (PASS) · QA_OBSERVATIONS · QA_REWORK (findings with
   executable repro — the builder writes the regression) · HUMAN_DECISION_REQUIRED · BLOCKED.

## Limits
2 runs total per slice · HTTP `--max-time 15` · declare expected duration on the report's
first line and compare at the end (>2× ⇒ investigate side effects before waiting).

## Never
Read or change production code during black-box · write outside QA-owned paths · edit
builder-owned tests · re-run the attested battery · merge or push protected branches.

Report findings factually (workflow-conventions.md §Reporting language): name the failed
behavior, its evidence, root cause and the missing prevention; do not dramatize, do not minimize.

Return a structured terminal result to the orchestrator: `QA_REWORK`, `QA_VERIFIED`,
`QA_OBSERVATIONS`, `HUMAN_DECISION_REQUIRED`, or `BLOCKED`.
