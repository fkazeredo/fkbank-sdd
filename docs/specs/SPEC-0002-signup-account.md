---
id: SPEC-0002
title: Sign-up and account opening
slug: signup-account
status: READY
risk: R3
profile: critical
modules: [onboarding, customer, identity, account]
depends_on: [SPEC-0001]
relevant_adrs: []
reading_list:
  domain: ["Module map (onboarding, customer, identity, account)", "Cross-cutting rules"]
  architecture: ["Backend", "Security", "Emulators"]
planned_sprint: S1
planned_release: null
owner_approved_at: 2026-07-20T15:35:08Z
owner_approved_hash: 3245eef9fc8e761ead5279aca5570146e78fdd17279ef2cc42c0a36991bfceb7
---

# SPEC-0002 — Sign-up and account opening

## Context
A person creates their FKBANK account alone: sign-up, automatic KYC, account opened, first
login, home with a zero balance.

## Scope
Sign-up form → KYC check (bureau emulator) → account created (branch 0001 + sequential
number) → login (OIDC) → home showing balance and account details.

## Out of scope
Transaction PIN (SPEC-0006) · profile editing · password recovery (post-MVP).

## Business rules
- **BR-1** — Sign-up requires full name, CPF (unique, format-validated), e-mail (unique),
  password, birth date (18+) and **declared monthly income** (feeds the limits engine,
  SPEC-0013). Duplicate CPF or e-mail ⇒ `409 DUPLICATE_CUSTOMER`.
- **BR-2** — Password: min 8 chars, at least 1 letter and 1 digit.
- **BR-3** — KYC: bureau emulator returns APPROVED or REJECTED. REJECTED ⇒ onboarding ends
  `REJECTED`, no customer credentials activated, no account; the person sees the reason
  category (not the raw bureau payload).
- **BR-4** — On approval: account opened and the `$0.00` opening posting recorded (M1);
  `AccountOpened` published.
- **BR-5** — Sign-up is idempotent by CPF while `PENDING`: resubmitting returns the current
  onboarding status, creating nothing.

## Acceptance criteria
- [ ] Happy path E2E (bureau `approve` scenario, previously-unused CPF): sign-up → KYC APPROVED → OIDC login → home renders balance `$0.00` and account details (branch `0001` + sequential number)
- [ ] Duplicate CPF (a customer with that CPF already exists) ⇒ `409 DUPLICATE_CUSTOMER`; customer / onboarding / credential row counts unchanged
- [ ] Bureau `decline` scenario ⇒ onboarding status `REJECTED`, no account row, no credential activated, message shows the reason category (raw bureau payload absent)
- [ ] Race — two concurrent same-CPF sign-ups under the default persistence engine and isolation level (not the in-memory test profile) ⇒ exactly one customer row and one account row persist (unique CPF)
- [ ] Bureau `delay` (timeout) scenario ⇒ onboarding stays `PENDING`; resubmitting the same CPF returns that same PENDING onboarding and creates no new customer / onboarding / account (idempotent, never duplicated)

## Edge cases
CPF with formatting vs digits-only (normalize) · birth date exactly 18 today · bureau
duplicate-webhook scenario.

## Open Questions

_None — all remaining OQs approved by the owner (2026-07-15)._

## Impact
Migrations: `customer`, `onboarding`, `credential` · Events: `OnboardingApproved`,
`AccountOpened` · Contract: sign-up/status endpoints + OpenAPI snapshot · Screens: sign-up,
login, home · Emulator: bureau (`approve`, `decline`, `delay`, `duplicate-webhook`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

- DL-0002 — 2026-07-18 — BR-4 "$0.00 opening posting (M1)" is satisfied by opening the
  customer available ledger account (`customer:available:{customerId}`) with a `$0.00`
  materialized balance and publishing `AccountOpened`; there is **no** zero-amount `posting`
  row, because `posting.amount` carries `CHECK (amount > 0)` and `Ledger.record()` rejects
  non-positive amounts (both SPEC-0001, immutable). No acceptance criterion requires a
  posting row (AC-1 requires `balance $0.00` on home). Decided by architecture (derived from
  the durable ledger schema + contract; no new decision).
- DL-0004 — 2026-07-18 — the declared monthly income (BR-1) is modelled by a `MonthlyIncome`
  value object holding a non-negative 2-decimal `BigDecimal`, **not** the ledger's `Money`: it is
  a self-reported reference figure feeding the limits engine (SPEC-0013), never a ledger balance
  or a moved amount, and the `customer` bounded context may not depend on `domain.ledger`
  (invariant 6). Invariant 5's `Money` discipline governs money movement and balances, which this
  is not. Decided by architecture (derived from invariants 5/6); recorded per review FIND-F1 —
  owner may override if declared income should instead carry `Money` semantics.
