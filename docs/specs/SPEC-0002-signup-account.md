---
id: SPEC-0002
title: Sign-up and account opening
slug: signup-account
status: IMPLEMENTED
risk: R3
profile: critical
modules: [onboarding, customer, identity, account]
depends_on: [SPEC-0001]
relevant_adrs: [ADR-0002]
reading_list:
  domain: ["Module map (onboarding, customer, identity, account)", "Cross-cutting rules"]
  architecture: ["Backend", "Security", "Emulators"]
planned_sprint: S1
planned_release: null
owner_approved_at: 2026-07-20T15:35:08Z
owner_approved_hash: 3245eef9fc8e761ead5279aca5570146e78fdd17279ef2cc42c0a36991bfceb7
implemented_at: 2026-07-20T21:06:56Z
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
- DL-0005 — 2026-07-20 — the slice is built whole, not split. The one-session fit check flags
  six of eight slice-too-big signals (four new bounded contexts, the first emulator service, a
  change to the OIDC credential path, three screens, expected compaction during `/build`), and
  the natural seam is `OnboardingApproved → AccountOpened`. Split rejected: the journey "a
  person opens an account and logs in" delivers no customer value in halves, and the sprint
  scoping already treats it as one delivery. Decided by owner.
- DL-0006 — 2026-07-20 — the bureau integration is **synchronous with an asynchronous webhook
  completion**, not synchronous only. The bureau answers inline when it can; the `delay`
  scenario answers later through a signed callback that completes the onboarding, and
  `duplicate-webhook` proves that callback is idempotent by onboarding id (M2's natural-key
  form). Synchronous-only was rejected on two grounds: it makes `PENDING` a state the system
  can never leave — BR-5 says a resubmission *returns* the pending onboarding, not that it
  re-calls the bureau, so a person whose call timed out could never open an account — and it
  would silently drop the `duplicate-webhook` scenario the Impact and Edge cases sections both
  name. Decided by owner.
- DL-0007 — 2026-07-20 — the bureau emulator is a minimal Spring Boot service in its own Maven
  module under `emulators/bureau`, establishing the pattern the remaining five emulators
  follow. Plain-Java HTTP and Node/Express were rejected: the first repeats hand-rolled routing
  and JSON across six emulators, the second adds a second backend toolchain to CI and to every
  future emulator for no capability the Java stack lacks. Recorded as ADR-0002 because it binds
  emulators beyond this slice. Decided by owner.
- DL-0008 — 2026-07-20 — money is presented in the `pt-BR` locale (`R$ 0,00`), while interface
  text stays en-US. AC-1's `$0.00` is read as shorthand for a zero balance, the way `docs/DOMAIN.md`
  writes every BRL figure with a bare `$`, not as a literal rendering requirement. Formatting BRL
  in en-US (`R$1,234.56`) was rejected: it swaps the decimal separator and the thousands separator
  for the reader who actually holds the account, and a balance misread by a factor of a thousand is
  not a cosmetic defect. Rendering a bare `$0.00` was also rejected — it hides that the currency is
  BRL, which stops being harmless as soon as PIX and boleto amounts appear. This is a documented
  narrowing of "en-US is the only MVP locale" (`docs/ARCHITECTURE.md` §Frontend): the exception is
  money formatting only, and it lives in the single money pipe. Decided by owner.
- DL-0009 — 2026-07-20 — an applicant born on 29 February comes of age on **28 February** in a
  non-leap year, not 1 March. Counting whole elapsed years (`Period.between`) would have made
  them wait a day longer than everyone else born in February, which diverges from the civil
  practice of treating the last day of the month as the anniversary. The window is one day every
  four years for roughly one applicant in 1,461, but it is an eligibility rule on a money
  product: refusing someone the law already treats as an adult is the worse of the two errors.
  Implemented by adding the years to the birth date rather than counting the years between.
  Decided by owner.
- DL-0004 — 2026-07-18 — the declared monthly income (BR-1) is modelled by a `MonthlyIncome`
  value object holding a non-negative 2-decimal `BigDecimal`, **not** the ledger's `Money`: it is
  a self-reported reference figure feeding the limits engine (SPEC-0013), never a ledger balance
  or a moved amount, and the `customer` bounded context may not depend on `domain.ledger`
  (invariant 6). Invariant 5's `Money` discipline governs money movement and balances, which this
  is not. Decided by architecture (derived from invariants 5/6); recorded per review FIND-F1 —
  owner may override if declared income should instead carry `Money` semantics.
