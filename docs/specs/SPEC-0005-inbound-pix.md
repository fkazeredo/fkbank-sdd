---
id: SPEC-0005
title: Inbound PIX
slug: inbound-pix
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [pix, ledger, account]
depends_on: [SPEC-0001, SPEC-0002, SPEC-0003]
relevant_adrs: []
reading_list:
  domain: ["Money flows (PIX)", "Module map (pix)"]
  architecture: ["Emulators", "Events"]
planned_sprint: S2
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0005 — Inbound PIX

## Context
The second cash-in rail: someone "at another bank" sends PIX to the customer; the SPI
emulator delivers it; the balance reflects instantly.

## Scope
Inbound PIX webhook from the SPI emulator (addressed by account, since keys arrive in
SPEC-0008), posting `internal:settlement:pix → customer:available`, receipt, statement line.

## Out of scope
Keys/DICT, outbound, refunds, QR — all SPEC-0008.

## Business rules
- **BR-1** — Inbound webhook is HMAC-signed (an invalid signature ⇒ `401`, nothing posted —
  same as the boleto settlement webhook, SPEC-0004 BR-2) and idempotent by end-to-end id:
  duplicate ⇒ one posting (M2).
- **BR-2** — Unknown target account ⇒ rejected with `ACCOUNT_NOT_FOUND`; nothing posted
  (the emulator's "return to sender" is its concern).
- **BR-3** — On credit: posting, receipt (payer name/institution masked), statement line —
  visible in the app within seconds (E2E asserts it without manual refresh tricks).
- **BR-4** — Any positive amount accepted pre-limits sprint (S6 revisits).

## Acceptance criteria
- [ ] E2E (SPI emulator `send-in`, real HTTP+DB stack): a valid inbound PIX ⇒ `customer:available` rises by exactly the credited amount (posting `internal:settlement:pix → customer:available`); a statement credit line and a receipt referencing that posting appear with payer name/institution masked (not cleartext); all visible without a manual refresh within seconds
- [ ] The same end-to-end id delivered twice — sequentially or concurrently, including a replay 24h later — ⇒ exactly one posting exists and the duplicate returns the same result posting nothing new (M2); two distinct e2e ids (even same amount, same second) ⇒ two postings. Asserted against PostgreSQL (Testcontainers) at its default isolation level, not the in-memory test profile
- [ ] Inbound webhook with an invalid HMAC signature (emulator `bad-signature`) ⇒ HTTP 401; no posting recorded and balance unchanged (BR-1)
- [ ] Inbound webhook (valid HMAC) targeting an unknown target account (emulator `unknown-account`) ⇒ rejected with error `ACCOUNT_NOT_FOUND`; no posting recorded, ledger and balance unchanged

## Edge cases
Two different PIX with the same amount arriving in the same second (distinct e2e ids) ·
webhook replay after 24h (still idempotent).

## Open Questions
_None._

## Impact
Migrations: `pix_inbound` · Events: `PixReceived` · Contract: webhook · Screens: statement
already covers it · Emulator: SPI (`send-in`, `duplicate-webhook`, `bad-signature`,
`unknown-account`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
