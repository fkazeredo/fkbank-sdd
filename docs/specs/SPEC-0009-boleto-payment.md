---
id: SPEC-0009
title: Boleto payment
slug: boleto-payment
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [billpay, ledger, identity]
depends_on: [SPEC-0004, SPEC-0006]
relevant_adrs: []
reading_list:
  domain: ["Money flows (boleto payment)"]
  architecture: ["Emulators", "Backend (idempotency)"]
planned_sprint: S4
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0009 — Boleto payment

## Context
Pay any third-party boleto by its digitable line — lookup at the clearinghouse, confirm,
pay with PIN, get the receipt.

## Scope
Digitable-line lookup (payee, amount, due date from the clearinghouse emulator), payment
with PIN + Idempotency-Key, posting `customer:available → internal:settlement:boleto`,
receipt, statement line.

## Out of scope
Scheduling for a future date (post-MVP) · partial payment · DDA (registered boletos inbox).

## Business rules
- **BR-1** — Lookup validates the digitable line (checksum) locally, then queries the
  clearinghouse; unknown boleto ⇒ `404 BOLETO_NOT_FOUND`; malformed line fails before any
  external call.
- **BR-2** — Overdue boletos: payable while the clearinghouse returns them as payable
  (amount may include emulated interest); the customer sees original vs charged amount.
- **BR-3** — Payment requires PIN; idempotent by key (M2); insufficient funds ⇒ 422 and no
  external confirmation is sent.
- **BR-4** — Confirmation to the clearinghouse and the posting live in one consistent unit:
  clearinghouse `fail` scenario ⇒ contra-posting + status `FAILED`, customer's money back,
  both visible in the statement.
- **BR-5** — A boleto already paid (by anyone) ⇒ `409 ALREADY_PAID` at lookup or payment.

## Acceptance criteria
- [ ] E2E (compose.e2e.yaml, ephemeral Postgres + clearinghouse emulator): funded customer with a valid PIN pays a clearinghouse-payable boleto by digitable line → operation status `PAID`, exactly one posting `customer:available → internal:settlement:boleto` for the charged amount, a receipt referencing that posting, and a statement debit line for the charged amount
- [ ] Invalid-checksum digitable line ⇒ rejected before any clearinghouse call (emulator lookup-call count = 0) with `400 INVALID_BOLETO_LINE`; a well-formed but unknown boleto ⇒ `404 BOLETO_NOT_FOUND` after the clearinghouse query
- [ ] On the default engine (PostgreSQL 16, not the in-memory test profile): two payment requests with the same Idempotency-Key and identical payload — sequential replay AND the concurrent race test — yield exactly one posting `customer:available → internal:settlement:boleto`, the replay returns the stored response, and ledger totals are unchanged
- [ ] Clearinghouse `fail-after-confirm` scenario ⇒ contra-posting `internal:settlement:boleto → customer:available` reverses the debit, operation status `FAILED`, available balance restored to the pre-payment value, and both the debit and the contra appear in the statement
- [ ] Already-paid boleto (emulator `already-paid`, paid by anyone) ⇒ `409 ALREADY_PAID` at lookup or at payment, and no posting is created

## Edge cases
Due date = today · amount changed between lookup and pay (re-validate at pay) · pay your
own deposit boleto (allowed — it is a boleto like any other).

## Open Questions
_None._

## Impact
Migrations: `boleto_payment` · Events: `BoletoPaid` · Contract: lookup + pay endpoints ·
Screens: pay flow (line → review → PIN → receipt) · Emulator: clearinghouse extended
(`lookup`, `already-paid`, `fail-after-confirm`, `overdue-with-interest`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
