---
id: SPEC-0003
title: Statement and receipts
slug: statement-receipts
status: AWAITING_SPEC_APPROVAL
risk: R2
profile: standard
modules: [account, ledger]
depends_on: [SPEC-0001, SPEC-0002]
relevant_adrs: []
reading_list:
  domain: ["Money flows", "Cross-cutting rules (6)"]
  architecture: ["Backend", "Frontend"]
planned_sprint: S2
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0003 — Statement and receipts

## Context
The statement is the product's face and the customer's proof. Derived from the ledger,
never a parallel table (M3).

## Scope
Statement endpoint + screen (period filter, direction filter, pagination), receipt per
movement (unique id, shareable view), running balance per line.

## Out of scope
Export (PDF/CSV, post-MVP) · notifications.

## Business rules
- **BR-1** — Every statement line maps to posting(s); the running balance of the last line
  equals the account balance. E.g.: deposits 10.00 + 5.00, transfer out 3.00 ⇒ lines show
  10.00 / 15.00 / 12.00 and balance is 12.00.
- **BR-2** — Every completed movement has exactly one receipt with a public unique id;
  the receipt shows parties (masked), amount, timestamp, rail and status.
- **BR-3** — Default period: current month; filters by period and direction (in/out);
  newest first; stable pagination (no duplicates/gaps across pages).
- **BR-4** — Amounts formatted by the single money pipe; masked identifiers (CPF ⇒
  `***.456.789-**`).

## Acceptance criteria
- [ ] After SPEC-0004/0005/0007 movements, each statement line maps to its posting(s), ordered newest-first, with a per-line running balance that accumulates chronologically (10.00 → 15.00 → 12.00, BR-1); the newest line (shown first) carries running balance = the current account balance (12.00), derived from the ledger, not a stored column
- [ ] Fetching a completed movement's receipt by its public unique id returns exactly one receipt showing parties (CPF masked as `***.456.789-**`), amount, timestamp, rail and status matching that movement's posting(s)
- [ ] With period (default current month) and direction (in/out) filters and newest-first order, paging through the statement while new movements are inserted between page fetches shows no duplicated and no skipped line across pages — verified on the default PostgreSQL engine (Testcontainers), not an in-memory profile
- [ ] E2E: deposit a known amount → statement renders a line with that amount and its running balance (BR-1) → open its receipt by id showing that amount, timestamp, rail and status (BR-2)

## Edge cases
Empty statement · movement at month boundary (period edges inclusive) · reversal rendering
(shows both original and contra).

## Open Questions
_None._

## Impact
Migrations: `receipt` · Events: consumes `PostingRecorded` · Contract: statement + receipt
endpoints · Screens: statement, receipt · Emulator: none

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
