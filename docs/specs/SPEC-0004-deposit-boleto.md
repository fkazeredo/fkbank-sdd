---
id: SPEC-0004
title: Deposit boleto cash-in
slug: deposit-boleto
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [billpay, ledger, account]
depends_on: [SPEC-0001, SPEC-0002, SPEC-0003]
relevant_adrs: []
reading_list:
  domain: ["Money flows (deposit)", "Module map (billpay)"]
  architecture: ["Emulators", "Events", "Backend"]
planned_sprint: S2
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0004 — Deposit boleto cash-in

## Context
The first way money enters: the customer generates a deposit boleto, "pays it elsewhere",
the clearinghouse (emulator) settles it, the balance reflects.

## Scope
Generate deposit boleto (digitable line + amount + expiry), settlement webhook from the
clearinghouse emulator, posting `internal:settlement:boleto → customer:available`, receipt.

## Out of scope
Paying third-party boletos (SPEC-0009) · boleto PDF rendering (digitable line + copy button
suffice in MVP).

## Business rules
- **BR-1** — Amount between $5.00 and $50,000.00 (pre-limits-engine cap); expiry = 3 business days;
  expired boletos cannot settle ⇒ `EXPIRED`.
- **BR-2** — Settlement webhook is HMAC-signed; invalid signature ⇒ `401`, nothing posted.
- **BR-3** — Settlement is idempotent by boleto id: duplicated webhook ⇒ same result, one
  posting (M2).
- **BR-4** — On settle: posting recorded, status `SETTLED`, receipt issued, statement line
  appears.
- **BR-5** — A boleto settles once, for its exact amount; divergent-amount webhook ⇒
  rejected, flagged for operator triage (Human Decision Gate).

## Acceptance criteria
- [ ] E2E: generate a boleto for a valid amount A ($5.00 ≤ A ≤ $50,000.00), emulator `settle` ⇒ posting `internal:settlement:boleto → customer:available` for exactly A, boleto status `SETTLED`, available balance rises by exactly A, one statement line of +A, receipt exists referencing that posting
- [ ] After a boleto has already settled once, re-delivering the same settlement webhook (same boleto id) ⇒ same response, still exactly one posting for that boleto, available balance unchanged (idempotent by boleto id)
- [ ] Settlement webhook with an invalid HMAC signature ⇒ HTTP 401, no posting recorded, boleto stays in its pre-webhook status, available balance unchanged
- [ ] `settle` webhook for a boleto past its 3-business-day expiry ⇒ rejected with `EXPIRED`, no posting recorded, boleto status `EXPIRED`, available balance unchanged
- [ ] Race, against the default persistence engine and isolation level (not the in-memory test profile): settle webhook and expiry job fire concurrently ⇒ exactly one terminal status — either `SETTLED` with exactly one posting for amount A, or `EXPIRED` with zero postings — never both, never a second posting

## Edge cases
Amount exactly at min/max · expiry crossing a weekend (business days) · webhook arriving
before the generate transaction commits (retry window).

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `deposit_boleto` · Events: `DepositSettled` · Contract: generate/status
endpoints + webhook · Screens: deposit (generate + copy line) · Emulator: clearinghouse
(`settle`, `expire`, `duplicate-webhook`, `bad-signature`, `divergent-amount`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
