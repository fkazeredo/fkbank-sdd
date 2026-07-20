---
id: SPEC-0006
title: Transaction PIN
slug: transaction-pin
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [identity]
depends_on: [SPEC-0002]
relevant_adrs: []
reading_list:
  domain: ["Cross-cutting rules (2)"]
  architecture: ["Security"]
planned_sprint: S2
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0006 — Transaction PIN

## Context
The short password that guards money movements — distinct from the login password (product
decision OQ-4, market-decided).

## Scope
PIN setup (first movement prompts it), backend verification primitive used by every
money-moving endpoint, failure counter, lockout, reset via login password.

## Out of scope
Biometrics/WebAuthn (post-MVP) · PIN on read-only operations (never).

## Business rules
- **BR-1** — PIN is exactly 4 digits, stored with a strong hash; never logged, never
  returned; sequences like `0000`/`1234` and 4 identical digits rejected at setup.
- **BR-2** — Every money-moving request carries the PIN; wrong PIN ⇒ `403 INVALID_PIN` and
  the movement does not reach the ledger.
- **BR-3** — 3 consecutive failures ⇒ movements blocked (`PIN_LOCKED`) until reset with the
  login password; counter resets on success.
- **BR-4** — PIN checks are rate-limited per account (no brute force via parallel calls) —
  the race test proves attempts 1-2-3 in parallel cause exactly one lock state.

## Acceptance criteria
- [ ] Given an account with no PIN set, the first money-moving request returns `428 PIN_SETUP_REQUIRED` (never 403); after the PIN is set via the setup flow, the same request carrying the correct 4-digit PIN creates the movement's ledger posting
- [ ] With a PIN already set, a money-moving request carrying a wrong 4-digit PIN returns `403 INVALID_PIN` and creates no ledger posting; the 3rd consecutive wrong attempt transitions the account to `PIN_LOCKED`
- [ ] An account in `PIN_LOCKED` rejects every money-moving request with code `PIN_LOCKED` and creates no posting; a reset authenticated by the correct login password clears the lock and the failure counter, after which a correct-PIN movement creates a posting; after wrong-wrong-correct the counter is back to 0, so the next single wrong attempt returns `INVALID_PIN`, not `PIN_LOCKED`
- [ ] Under the default persistence engine and isolation level (PostgreSQL, not the in-memory test profile): 3 wrong-PIN attempts fired in parallel on the same account converge to exactly one `PIN_LOCKED` state (no counter overshoot / lost update) and no request reaches the ledger

## Edge cases
Wrong-wrong-right (counter resets) · reset while locked · PIN attempt on account without
PIN set (prompt setup, not 403).

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `transaction_pin` · Events: none · Contract: setup/verify embedded in movement
endpoints · Screens: PIN setup + PIN prompt component · Emulator: none

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
