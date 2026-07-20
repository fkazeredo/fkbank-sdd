---
id: SPEC-0007
title: Internal transfer
slug: internal-transfer
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [transfer, ledger, account, identity]
depends_on: [SPEC-0001, SPEC-0002, SPEC-0003, SPEC-0006]
relevant_adrs: []
reading_list:
  domain: ["Money flows (internal)", "Cross-cutting rules"]
  architecture: ["Backend (idempotency)", "Persistence"]
planned_sprint: S2
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0007 — Internal transfer

## Context
Send money to another FKBANK account, protected by the PIN — the slice that closes the
money loop and crowns Sprint 2.

## Scope
Lookup destination by account number, transfer with PIN + `Idempotency-Key`, posting
`customerA:available → customerB:available`, receipts on both sides, statement lines.

## Out of scope
Scheduling · favorites/contacts (post-MVP) · external rails.

## Business rules
- **BR-1** — Destination must exist and differ from origin; self-transfer ⇒
  `422 SAME_ACCOUNT`.
- **BR-2** — Insufficient funds ⇒ `422 INSUFFICIENT_FUNDS` (given balance $100.00,
  transferring $100.01 fails; $100.00 succeeds).
- **BR-3** — Idempotent by `Idempotency-Key` (M2): replay returns the same receipt, one
  posting; same key + different payload ⇒ `409`.
- **BR-4** — Requires valid PIN (SPEC-0006); PIN failures never create postings.
- **BR-5** — Both sides get receipts and statement lines from the single posting; sender
  sees recipient name (masked CPF), recipient sees sender name.

## Acceptance criteria
- [ ] E2E (Playwright against the compose.e2e.yaml stack): a PIN-authorized transfer of amount A posts customerA:available → customerB:available; sender's statement gains exactly one debit line of A naming the recipient (masked CPF), recipient's statement gains exactly one credit line of A naming the sender, and both receipts open referencing that single posting (BR-5)
- [ ] Replay: a second request with the same Idempotency-Key and identical payload returns the byte-identical stored response and the ledger still holds exactly one transfer posting (BR-3; DOMAIN §Verifiable invariants — replay does not change the ledger)
- [ ] Race on the default persistence engine (PostgreSQL 16 via Testcontainers, real SELECT … FOR UPDATE acquired in ascending-id order — NOT the in-memory profile): two concurrent transfers each draining the full origin balance ⇒ exactly one succeeds and the other returns `422 INSUFFICIENT_FUNDS` (BR-2); afterward Σdebits = Σcredits, the origin's materialized balance = the sum of its postings, and exactly one transfer posting exists (DOMAIN §Verifiable invariants)
- [ ] Self-transfer (destination == origin) ⇒ `422 SAME_ACCOUNT` with nothing posted (BR-1); transfer to a non-existent destination ⇒ `422 DESTINATION_NOT_FOUND` with nothing posted

## Edge cases
Transfer of exactly the full balance · origin locked by PIN during flight · key reuse after
24h (still replays).

## Open Questions
_None — per-operation/daily limits arrive with SPEC-0013 (implemented in S6)._

## Impact
Migrations: `transfer` · Events: `TransferSettled` · Contract: lookup + transfer endpoints ·
Screens: transfer flow (amount → confirm → PIN → receipt) · Emulator: none

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
