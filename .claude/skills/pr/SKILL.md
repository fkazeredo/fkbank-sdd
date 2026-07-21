---
name: pr
description: RELAY phase — DoD check, Decision Ladder diff check, docs/changelog, open the PR to develop, bounded CI watch. Never merges. Manual invocation only.
disable-model-invocation: true
---

# /pr <id>

Read `.claude/rules/workflow-conventions.md` first. Role: `builder`. Fresh or same-day
builder session.

## Preconditions
`state.json` = DEV_VERIFIED (R0/R1) or QA_VERIFIED / QA_OBSERVATIONS (R2+).

## Steps
1. Run `tools/git/check-safe-branch ... pr --allow-dirty`; state → `PR_PREPARING`.
2. Diff review checklist: spec fidelity (no silent scope change) · house rules
   (docs/ARCHITECTURE.md) · tests with real assertions · **discriminating-test check**: a test
   for a critical invariant/concurrency/schema/authz/idempotency/state-transition that would look
   the same with and without the protection is rejected (workflow-conventions.md §Evidence) ·
   contracts/OpenAPI regenerated ·
   Javadoc/TSDoc on the public surface · **Decision Ladder check**: the plan's recorded
   ladder vs the actual diff (no broad re-search) · behavioral-domain check: invariants live
   on the model, changed aggregates are not records/data bags, and Lombok does not expose
   generic mutation or invalid construction.
3. Definition of Done — core: behavior + evidence + tests + scripts green + architecture
   respected + docs + limitations declared + no merge. By risk: database (migration
   immutability, locks, indexes, compatibility, rollback/roll-forward) · security
   (authorization, data, secrets, log masking, gates) · contract (API/DTO compatibility,
   consumers, errors) · operations (logs, metrics, diagnostics, rollback).
4. Docs (mandatory for any product-face change, before the PR): spec updated (Decision log;
   keep frontmatter state) · user manual under `docs/manual/**` in **both en-US and pt-BR** ·
   English `README.md` refreshed (README stays English-only) · one line in `docs/CHANGELOG.md`
   under `[Unreleased]` (`SPEC-00NN — one-line summary`).
5. Run `tools/workflow/check-dod <id> --pre-pr`. Fill
   `.github/pull_request_template.md` completely (AC → evidence → status table; attach
   the QA verdict; human review focus). Push the branch; open the PR to `develop`.
6. Record PR number/URL, body artifact and next command; state → `PR_OPEN`. Run
   `tools/workflow/check-dod <id> --post-pr`. CI watch: poll up to **15 minutes**. Outcomes: checks green →
   `AWAITING_HUMAN_REVIEW` · checks failed → `CI_FAILED` (suggest /fix-pr) · still running →
   `CI_PENDING` (never wait past the window) · no required checks configured → say exactly
   "No required CI checks are configured", state `AWAITING_HUMAN_REVIEW` — NEVER claim CI
   passed.

## Terminal states
AWAITING_HUMAN_REVIEW · CI_PENDING · CI_FAILED · PR_OPEN · HUMAN_DECISION_REQUIRED · BLOCKED

## Never
Merge · touch production code beyond docs/changelog · open a PR with an evidence-less AC
without declaring it explicitly.

End: `SESSION OVER — next: human review & merge` (+ `/review-pr <n>` first on R3/R4;
`/fix-pr <n>` if CI failed).
