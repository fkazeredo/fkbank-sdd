---
id: SPEC-0010
title: Virtual debit card
slug: virtual-debit-card
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [card, ledger, identity]
depends_on: [SPEC-0001, SPEC-0006]
relevant_adrs: []
reading_list:
  domain: ["Money flows (card)", "Domain decisions (direct debit)"]
  architecture: ["Emulators", "Persistence"]
planned_sprint: S4
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0010 — Virtual debit card

## Context
Spend online with a virtual debit card: issue it, the network emulator sends authorization
requests, approval debits the balance on the spot (market-decided: direct debit in the MVP).

## Scope
Issuance (PAN/expiry/CVV shown once, PIN-gated reveal), authorization webhook (approve/
decline against balance and card state), refund (contra-posting), block/unblock, purchase
timeline.

## Out of scope
Hold+capture mechanics (post-MVP fidelity) · physical card · credit/statement · contactless
simulation.

## Business rules
- **BR-1** — One active virtual card per account in the MVP; re-issue replaces (old PAN
  becomes DECLINED-on-use with `CARD_REPLACED`).
- **BR-2** — Authorization request (HMAC-signed) is approved iff card ACTIVE and balance ≥
  amount; approval posts `customer:available → internal:settlement:card` in the
  authorization response window (latency budget: p99 < 300ms local — measured by test).
- **BR-3** — Decline reasons are explicit: `INSUFFICIENT_FUNDS`, `CARD_BLOCKED`,
  `CARD_REPLACED`; declines post nothing.
- **BR-4** — Authorization is idempotent by network transaction id: duplicated request ⇒
  same answer, one posting (M2).
- **BR-5** — Refund webhook credits back by contra-posting, cumulative ≤ original.
- **BR-6** — Block/unblock requires PIN and is immediate: an in-flight authorization after
  block ⇒ declined (race-tested).
- **BR-7** — MVP purchase scope: online purchases via the network emulator only (no POS/
  contactless simulation).

## Acceptance criteria
- [ ] Issue card (PIN to reveal), emulator `purchase` approved ⇒ balance down, timeline +
      statement show the merchant
- [ ] `purchase` above balance ⇒ declined INSUFFICIENT_FUNDS, nothing posted
- [ ] Duplicate network transaction id ⇒ one posting
- [ ] Block then `purchase` ⇒ declined CARD_BLOCKED; unblock restores
- [ ] Race: block vs in-flight authorization ⇒ consistent single outcome
- [ ] Refund credits back; over-refund rejected

## Edge cases
Authorization for exactly the full balance · two authorizations racing for the last funds ·
refund arriving for a declined purchase (rejected).

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `card`, `card_authorization` · Events: `CardAuthorized`, `CardDeclined` ·
Contract: issue/block endpoints + auth webhook · Screens: card area (issue, reveal, block,
timeline) · Emulator: card network (`purchase`, `refund`, `duplicate`, `bad-signature`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
