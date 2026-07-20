---
id: SPEC-0014
title: LGPD rights and data lifecycle
slug: lgpd
status: AWAITING_SPEC_APPROVAL
risk: R4
profile: critical
modules: [customer, application, infra]
depends_on: [SPEC-0002]
relevant_adrs: []
reading_list:
  domain: ["Cross-cutting rules (7)"]
  architecture: ["Security"]
planned_sprint: "S6 (implementation)"
planned_release: null
owner_approved_at: null
owner_approved_hash: null
split_review_required: true
---

# SPEC-0014 — LGPD rights and data lifecycle

## Context
LGPD (Lei 13.709/2018) grants data subjects concrete rights; a bank must honor them while
keeping financial records for legal retention. This spec implements the rights a digital
bank exposes in-app.

## Scope
Consent registry, data access/export, correction, erasure-anonymize, consent revocation,
privacy screen.

## Out of scope
DPO workflow tooling · cookie management (web shell keeps it minimal) · data-sharing with
third parties (none exists in the MVP).

## Business rules
- **BR-1 (consent)** — Consent for data processing is collected at sign-up with versioned
  terms; every consent/revocation event is stored append-only (who, when, term version).
- **BR-2 (access/portability)** — The customer can request a full export of their data;
  it is generated asynchronously and delivered as a JSON download in-app, notified when
  ready. Contains registration data, consents, accounts, statement and receipts.
- **BR-3 (correction)** — Registration data (name, e-mail, declared income) is editable
  with PIN; CPF and birth date are immutable (identity attributes) — correction of those
  means a support path, out of MVP.
- **BR-4 (erasure-anonymize)** — Account with zero balance and no active loan can request
  erasure: registration data is irreversibly anonymized, credentials destroyed, PIX keys
  removed; **postings and receipts remain** under legal retention, unlinked from any
  identifiable subject. Active products ⇒ `422 ACCOUNT_ACTIVE` with what must be settled.
- **BR-5 (revocation)** — Revoking operational consent equals requesting erasure (a bank
  cannot operate without processing the data); the flow explains this before confirming.
- **BR-6 (masking)** — CPF, PIX keys and card PAN are masked in every log and every screen
  where the full value is not strictly needed; full values never appear in logs (tested).

## Acceptance criteria
- [ ] Export contains all subject data and is downloadable after async generation
- [ ] Erasure on a zeroed account anonymizes the customer; statement/receipts survive
      unlinked; login impossible afterwards
- [ ] Erasure with balance or active loan ⇒ 422 with reasons
- [ ] Consent log shows sign-up consent and any revocation, append-only
- [ ] Log-masking test: no full CPF/PAN/key appears in captured logs of the E2E run

## Edge cases
Erasure requested while a deposit boleto is open (must be canceled/expired first) · export
requested twice (idempotent per day) · anonymized account receiving a PIX (rejected as
ACCOUNT_NOT_FOUND).

## Open Questions
_None — rights map directly to LGPD Art. 18; retention follows financial-records rules._

## Impact
Migrations: `consent_log`, anonymization fields · Events: `CustomerAnonymized` · Contract:
privacy endpoints · Screens: privacy center (consents, export, erase) · Emulator: none

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
