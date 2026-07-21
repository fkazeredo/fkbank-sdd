---
id: SPEC-00NN
title: <capability name>
slug: <capability-slug>
status: AWAITING_SPEC_APPROVAL
risk: R<0-4>
profile: <light|standard|critical>
modules: []
depends_on: []
relevant_adrs: []
reading_list:
  domain: []
  architecture: []
planned_sprint: null
planned_release: null
owner_approved_at: null
owner_approved_hash: null
implemented_at: null
---

`owner_approved_hash` is SHA-256 over normalized UTF-8/LF content with both approval fields
temporarily normalized to `null`; `/deliver-spec` computes and verifies it automatically.

# SPEC-00NN — <capability name>

<!-- Frontmatter notes (delete in a real spec):
     status lifecycle: DRAFT → AWAITING_SPEC_APPROVAL → READY → IN_PROGRESS → IMPLEMENTED
       (or SUPERSEDED / BLOCKED). /design-slice flips READY → IN_PROGRESS; the merge to develop is
       auto-reconciled at the start of the next /deliver-spec (or by /close-sprint / /release for the
       final slice), flipping IN_PROGRESS → IMPLEMENTED and stamping implemented_at at the real merge
       instant; /workflow-status and /reconcile-workflow remain manual fallbacks.
     risk: R0–R4 per .claude/rules/risk-model.md — touching a DOMAIN invariant ⇒ at least R3.
     reading_list: the slice run reads ONLY these sections (budgeted reading).
     split_from (children of a split only): OPTIONAL provenance, value SPEC-<original-id> (format
       SPEC-NNNN). It points at the DELETED original spec, so it is NOT a dependency and is NEVER
       existence-checked (unlike depends_on). Omit it on a normal new spec.
     Relevant ADRs, if any, are listed under ## Impact. -->

## Context

2–4 lines: why this capability exists from the bank customer's point of view.

## Scope

What this slice includes. A slice cuts through migration → domain → API → screen and ends
demonstrable.

## Out of scope

Explicit, to kill ambiguity (what goes to another spec or post-MVP).

## Business rules

Numbered, testable, with input/output examples wherever money or math is involved.

- **BR-1** — <rule>. E.g.: given balance $100.00, transferring $100.01 ⇒ `INSUFFICIENT_FUNDS`.
- **BR-2** — <rule>. Postings: `customerA:available → customerB:available` (M1/M5).
- **BR-3** — Idempotency (M2): repeating the request with the same `Idempotency-Key` returns
  the same response and records no new posting.

## Acceptance criteria

Every criterion must be **executable** — it names three things, so it maps to a test exactly
as written, with nothing left for the test author to guess:

- **Observable** — what you look at (a returned value, an error code, a persisted row, a
  count, a rendered element).
- **Condition** — the environment, profile or state under which it must hold (which datastore
  or engine, which config profile, which starting state, concurrent or not).
- **Discriminator** — the exact value, code or threshold that separates pass from fail
  (`= 0`, `INSUFFICIENT_FUNDS`, `exactly one`, `within 100 ms`).

**Corollary (environment-sensitive criteria):** when a criterion's truth value can differ
between environments — anything touching concurrency, locking, isolation, clocks, timezones,
persistence engines or config profiles — it MUST name the environment where it holds, and its
test MUST run there. A criterion that is green under a test profile and red under the default
one is a defect in the criterion, not in the code.

Bad (not executable): *"concurrent withdrawals don't corrupt the balance"* — no discriminator
("corrupt" how? how many succeed?) and no condition (the outcome differs between an in-memory
test engine and the real one).
Good (executable): *"two concurrent withdrawals drain the same account, run against the
default persistence engine and isolation level: exactly one succeeds, the other fails with
`<error code>`, and the post-state holds one debit with Σdebits = Σcredits."*

- [ ] <observable + condition + discriminator — happy path>
- [ ] <relevant negative with its exact error code>
- [ ] <Idempotency-Key replay: identical response, zero new records>
- [ ] <race: two concurrent executions, named engine + isolation, preserve the DOMAIN.md invariants>

## Edge cases

Boundary amounts, dates 29/30/31 and leap years when calendars are involved, rounding,
concurrency, timeout/outage of the rail's emulator.

## Open Questions

| OQ | Question | Recommendation | Decision | Ref |
|---|---|---|---|---|
| OQ-a | <question> | <recommendation> | _pending_ | owner or DL-NNNN |

No behavior covered by a pending OQ gets implemented (CLAUDE.md, invariant 3).

## Impact

Migrations: <tables> · Events: <published/consumed> · Contract: <endpoints, OpenAPI
snapshot> · Screens: <front routes> · Emulator: <control scenarios needed> · Relevant
ADRs: <ADR-000N or —>

## Decision log

Durable record of business decisions made during this spec's lifecycle (interview answers,
Human Decision Gate outcomes, scope rulings). One line each: `DL-NNNN — <date> — <decision>
— decided by <owner/policy/architecture>`. Architecture decisions go to `docs/adr/` instead.

## Traceability

Fill when implementing: the tests covering each BR (class#method / E2E file).
