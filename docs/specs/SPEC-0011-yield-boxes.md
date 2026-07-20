---
id: SPEC-0011
title: Yield boxes
slug: yield-boxes
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [savings, ledger]
depends_on: [SPEC-0001, SPEC-0006]
relevant_adrs: []
reading_list:
  domain: ["Money flows (boxes, yield)", "Cross-cutting rules (4)"]
  architecture: ["Testing (jqwik)", "Events"]
planned_sprint: S5
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0011 — Yield boxes

## Context
Named sub-accounts where money grows daily — the "keep it separate and watch it earn"
feature.

## Scope
Create/rename/close box, add (available → box) and withdraw (box → available) with PIN,
daily yield batch using the CDI index emulator, box detail with accumulated yield.

## Out of scope
Goals/targets with dates · multiple yield products · withdraw locks.

## Business rules
- **BR-1** — Up to 10 boxes per account; closing requires balance zero (withdraw first).
- **BR-2** — Add/withdraw are ledger postings between `available` and `box:{id}`, PIN-gated,
  idempotent by key; withdraw above box balance ⇒ `INSUFFICIENT_FUNDS`.
- **BR-3** — Yield: business days only, `box balance at day start × daily CDI factor`,
  4-decimal internal math, credited as `internal:expense:yield → box` — idempotent by
  (box, date): re-running the batch never double-credits.
- **BR-4** — Days without a published index yield nothing (no retroactive catch-up in MVP);
  the box detail shows total accumulated yield separately from deposits.

## Acceptance criteria
- [ ] Create box, add $100.00, run batch with index scenario ⇒ yield credited, statement
      of the box shows it
- [ ] Batch re-run for the same date ⇒ no double credit
- [ ] Withdraw all + close; close with balance ⇒ rejected
- [ ] jqwik: yield rounding properties (never negative, Σ credited = expected within cents)
- [ ] Race: add and withdraw concurrently on one box ⇒ invariants hold

## Edge cases
Box created mid-day (yields from next business day) · index published twice for one date
(second ignored) · balance $0.01 (yield rounds to zero, internally accumulates).

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `box` · Events: `YieldCredited` · Contract: box CRUD + move endpoints ·
Screens: boxes list/detail, add/withdraw flows · Emulator: CDI index (`publish`, `skip-day`,
`duplicate-publish`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
