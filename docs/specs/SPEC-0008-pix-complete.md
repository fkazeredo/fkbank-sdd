---
id: SPEC-0008
title: PIX complete (keys, outbound, refund, static QR)
slug: pix-complete
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [pix, ledger, identity]
depends_on: [SPEC-0005, SPEC-0006, SPEC-0007]
relevant_adrs: []
reading_list:
  domain: ["Money flows (PIX)", "Ubiquitous language"]
  architecture: ["Emulators", "Events", "Backend (idempotency)"]
planned_sprint: S3
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0008 — PIX complete (keys, outbound, refund, static QR)

## Context
The full PIX experience: own keys in the DICT, send to anyone's key or copy-paste code,
refund what came in, get paid by static QR.

## Scope
Key management (CPF, e-mail, phone, random — register/remove in the DICT emulator), key
lookup preview, outbound PIX with PIN + Idempotency-Key, refund of an inbound PIX (total or
partial), static receive QR (payload rendering + inbound recognition).

## Out of scope
Dynamic QR (post-MVP) · scheduled PIX · portability claims between institutions.

## Business rules
- **BR-1** — One key per type per account (random keys: up to 5); registering an
  already-taken key ⇒ `409 KEY_TAKEN` (DICT emulator is authoritative).
- **BR-2** — Outbound: lookup shows recipient name/institution (masked) BEFORE confirmation;
  send requires PIN; posting `customer:available → internal:settlement:pix`; idempotent by
  key (M2); SPI timeout ⇒ status `PENDING_CONFIRMATION`, resolved by webhook or expiry
  scenario — money never disappears (either settled or contra-posted back).
- **BR-3** — Refund references an inbound PIX, total or partial, cumulative refunds ≤
  original amount; each refund is its own posting and receipt.
- **BR-4** — Static QR encodes account + optional fixed amount; an inbound PIX carrying the
  QR reference appears in the statement labeled as QR payment.

## Acceptance criteria
- [ ] Register CPF, e-mail, phone and random keys (random up to 5) ⇒ each persisted as a pix_key row and registered in the DICT emulator; an already-taken key ⇒ 409 KEY_TAKEN
- [ ] E2E (SPI confirm scenario): lookup returns masked recipient name/institution before confirm → send with valid PIN posts `customer:available → internal:settlement:pix` → emulated recipient credited → status SETTLED, with a receipt referencing the posting on both ends
- [ ] Replay outbound with same Idempotency-Key ⇒ one posting
- [ ] SPI timeout scenario ⇒ status `PENDING_CONFIRMATION`; then webhook confirm ⇒ SETTLED (original posting kept) OR expiry ⇒ auto contra-posting `internal:settlement:pix → customer:available` (money never lost)
- [ ] Partial refunds: 2 × $3.00 on a $10.00 inbound OK; a further $5.00 ⇒ `REFUND_EXCEEDS`
- [ ] Race — two concurrent outbound PIX draining a balance sufficient for only one, on the default persistence engine and isolation level (not the in-memory test profile) ⇒ exactly one settles, the other ⇒ `INSUFFICIENT_FUNDS` (422); balance never negative, exactly one posting

## Edge cases
Key removed between lookup and send (fresh validation at send) · refund of a refund
(forbidden) · QR with fixed amount paid with divergent amount (rejected by emulator
contract).

## Open Questions
_None._

## Impact
Migrations: `pix_key`, `pix_outbound`, `pix_refund` · Events: `PixSent` · Contract: keys,
lookup, send, refund, QR endpoints · Screens: PIX area (keys, send, refund, QR) · Emulator:
SPI/DICT extended (`key-taken`, `timeout`, `confirm`, `expire`, `refund`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
