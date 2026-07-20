---
id: SPEC-0012
title: Personal loan
slug: personal-loan
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [credit, ledger, identity]
depends_on: [SPEC-0001, SPEC-0006]
relevant_adrs: []
reading_list:
  domain: ["Money flows (credit)", "Cross-cutting rules (5)"]
  architecture: ["Testing (jqwik)", "Events"]
planned_sprint: S5
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0012 — Personal loan

## Context
Simulate, see the true cost (CET), contract, money in the account, fixed installments
auto-debited — the standard neobank loan (market-decided).

## Scope
Limit check (bureau emulator), simulation (amount × installments ⇒ installment value, total,
monthly rate and CET), contracting with PIN, disbursement posting, immutable schedule,
daily batch charging due installments, loan tracking screen.

## Out of scope
Early settlement with discount (post-MVP) · refinancing · collections beyond retry
(post-MVP) · variable rates.

## Business rules
- **BR-1** — Limit comes from the bureau emulator per customer; requested amount above
  limit ⇒ `422 ABOVE_LIMIT`. Amount $100.00–limit, 2–24 installments.
- **BR-2** — Fixed monthly rate, Price amortization (equal installments); CET (includes the
  flat $25.00 contracting fee, financed into the loan) is displayed on every simulation BEFORE contracting. Example:
  $1,000.00 in 12× at 2.0%/mo ⇒ installment $94.56; with the fee financed, CET ≈ 2.37%/mo
  (jqwik pins the formula, example pins the vector).
- **BR-3** — Contracting (PIN + Idempotency-Key) generates the immutable schedule and the
  disbursement posting `internal:credit:disbursement → customer:available` atomically.
- **BR-4** — Charging batch (business days): debits due installments
  `customer:available → internal:credit:receivable:{loan}` idempotently by (loan,
  installment); insufficient funds ⇒ installment `OVERDUE`, retried next business day —
  no partial debit in MVP.
- **BR-5** — All installments PAID ⇒ loan `SETTLED`; tracking screen shows schedule with
  per-installment status.

## Acceptance criteria
- [ ] Simulation shows installment, total, rate and CET matching the pinned vector
- [ ] Contract with PIN ⇒ disbursement in balance + statement, schedule visible
- [ ] Replay contracting with same key ⇒ one loan, one disbursement
- [ ] Batch debits a due installment once; re-run ⇒ no double charge
- [ ] Insufficient funds ⇒ OVERDUE, next-day retry succeeds after cash-in
- [ ] jqwik: Price schedule properties (Σ amortization = principal; equal installments ±1¢)

## Edge cases
Contract on the 31st (due dates on shorter months) · exactly one installment (n=2 lower
bound vs n=1 rejected) · OVERDUE then box withdraw brings funds mid-day.

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `loan`, `installment` · Events: `LoanDisbursed`, `InstallmentCharged` ·
Contract: simulate/contract/tracking endpoints · Screens: simulation, contracting,
tracking · Emulator: bureau extended (`limit`, `no-limit`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
