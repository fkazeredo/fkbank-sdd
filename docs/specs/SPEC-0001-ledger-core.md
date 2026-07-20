---
id: SPEC-0001
title: Ledger core
slug: ledger-core
status: IN_PROGRESS
risk: R3
profile: critical
modules: [ledger, infra]
depends_on: []
relevant_adrs: []
reading_list:
  domain: ["Chart of accounts", "Money flows", "Invariants"]
  architecture: ["Backend", "Persistence"]
planned_sprint: S1
planned_release: null
owner_approved_at: 2026-07-20T11:48:05Z
owner_approved_hash: cd29e975d28c191d6e593a6e4d8aae9705ee89e22db252010af2f8ab653a61e4
---

# SPEC-0001 — Ledger core

## Context
The accounting heart. Every other module commands postings here; nothing else may touch a
balance (M1–M5).

## Scope
Chart of accounts (seeded by migration), double-entry posting API (internal, not REST),
derived + materialized balance, verification routine.

## Out of scope
Any REST endpoint, any screen, any rail. Multi-currency (BRL-denominated `Money` only,
currency field present).

## Business rules
- **BR-1** — A posting is a debit/credit pair over two accounts, same amount, atomic,
  immutable. Reversal = contra-posting referencing the original. E.g.: reverse
  `A→B $10.00` ⇒ new posting `B→A $10.00 (reverses #123)`.
- **BR-2** — Customer `available` and `box` accounts can never go below zero; internal
  accounts may. Violating posting ⇒ `INSUFFICIENT_FUNDS`, nothing written.
- **BR-3** — `Money`: 4 internal decimals for math; rounding to 2 (half-up) only at the
  edge. E.g.: yield calc `10.00 × 0.000456 = 0.00456` → statement shows `0.00`, internal
  keeps `0.0046`.
- **BR-4** — Materialized balance updates in the posting's transaction; a verification
  routine recomputes balance = Σ postings and Σdebits = Σcredits, per account and global.
- **BR-5** — Posting acquires row locks on both accounts in ascending id order (baseline
  decision: pessimistic).
- **BR-6** — A posting is reversed at most once, and a reversal is never itself reversed. A repeat
  or nested reversal ⇒ `REVERSAL_NOT_ALLOWED`, nothing written. Enforced in the ledger (not
  delegated to callers) and backed by a partial unique index — otherwise a second contra whose debit
  lands on a negative-allowed internal account fabricates a balance the verification routine cannot
  detect. A reversal is itself a posting, so it is subject to BR-2 (it may raise
  `INSUFFICIENT_FUNDS`). Added in rework after review (owner-decided).

## Acceptance criteria
- [ ] Posting of $X from account A to B: materialized balance(A) −$X, balance(B) +$X, each equals Σ of its own postings, and Σ debits = Σ credits holds globally
- [ ] A debit that would drive a customer available/box account below zero ⇒ raises INSUFFICIENT_FUNDS; no posting or balance row written and every balance unchanged
- [ ] Reversing posting #N writes a new contra-posting (B→A, same $X) referencing #N; both balances return to their pre-#N values; row #N stays immutable (no UPDATE/DELETE)
- [ ] Race against PostgreSQL via Testcontainers (not an in-memory profile), with pessimistic SELECT ... FOR UPDATE acquired in ascending id order: 2 concurrent postings each draining an account funded for only one ⇒ exactly one commits, the other fails INSUFFICIENT_FUNDS, final balance ≥ 0, and Σ debits = Σ credits holds at the end
- [ ] Verification routine over a ledger with one account's materialized balance manually set ≠ Σ of its postings (test-only path) flags that account as inconsistent; over an untampered ledger it flags none
- [ ] jqwik on Money: edge rounding to 2 decimals is half-up while math keeps 4 internal decimals (e.g. 0.00456 → 0.00, internal 0.0046); any N-way split sums exactly back to the original with the remainder on the last entry
- [ ] Reversing an already-reversed posting, or reversing a reversal, ⇒ `REVERSAL_NOT_ALLOWED` with nothing written; the account keeps its single-reversal balance (no fabrication) and verification stays consistent (BR-6)

## Edge cases
Amount 0 or negative (rejected) · posting an account against itself (rejected) · splits
that don't divide evenly (remainder to last entry).

## Open Questions
_None — resolved by PRODUCT/DOMAIN and baseline decisions._

Idempotency scope: the ledger core exposes no idempotency mechanism — `Idempotency-Key`
enforcement lives at the REST edge and lands with the first money-moving route, so this slice
ships the mandatory race test but no idempotency-replay test. ASSUMED (DL-0007).

## Impact
Migrations: `account`, `posting`, `balance` · Events: `PostingRecorded` · Contract: none ·
Screens: none · Emulator: none

## Decision log

- DL-0004 — Event publication uses the Spring Modulith JPA event registry as the outbox base
  — decided by the architecture baseline.
- DL-0007 — Ledger-level idempotency is excluded; Idempotency-Key enforcement lives at the
  delivery edge and arrives with the first money-moving route — decided by the domain baseline.
- DL-0008 — The rail-agnostic `fkbank.ledger.postings` counter belongs to SPEC-0001 rather
  than SPEC-0016 — decided by the roadmap follow-through contract.
- DL-0010 — 2026-07-20 — Delivery approved against spec content hash
  `cd29e975d28c191d6e593a6e4d8aae9705ee89e22db252010af2f8ab653a61e4` at
  2026-07-20T11:48:05Z by franklin.azeredo — decided by owner.
