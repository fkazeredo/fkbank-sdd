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

## Traceability

| Acceptance criterion | Tests |
|---|---|
| Posting moves both balances; each equals Σ of its postings; Σ debits = Σ credits | `LedgerPostingIT.movesMoneyAndKeepsTheBooksBalanced`, `.recordsBothLegs`, `.allowsDrainingToZero` |
| Debit below zero on a customer account ⇒ `INSUFFICIENT_FUNDS`, nothing written | `LedgerPostingIT.refusesToOverdrawAndWritesNothing` · `BalanceTest` (8 cases) · schema `posting_amount_positive` |
| Reversal writes a contra referencing the original; balances restored; original immutable | `LedgerReversalIT.reversalRestoresBothBalances`, `.originalPostingStaysImmutable` · `LedgerSchemaIT.refusesToUpdateAPosting`, `.refusesToDeleteAPosting` |
| Race under real PostgreSQL with `FOR UPDATE` in ascending id order | `LedgerConcurrencyIT.exactlyOneOfTwoConcurrentDrainsCommits`, `.opposingTransfersDoNotDeadlock` |
| Verification routine flags a tampered balance and none on a clean ledger | `LedgerVerificationIT` (3 cases) · `TrialBalanceTest` (5 cases) |
| jqwik on `Money`: edge rounding half-up, 4 internal decimals, N-way split sums back | `MoneyPropertiesTest` (7 properties) · `MoneyTest` (15 cases) |
| BR-6 — reversal at most once, never a reversal of a reversal | `LedgerReversalIT.refusesASecondReversal`, `.refusesToReverseAReversal`, `.reversalIsSubjectToTheSignRule` · `PostingTest.refusesToReverseAReversal` · `LedgerSchemaIT.refusesASecondReversal` (partial unique index) |
| M5 — no balance access outside the ledger | `ArchitectureTest.onlyTheLedgerTouchesBalances` |

Backend tests live under `backend/src/test/java/com/fkbank/domain/ledger/` except
`LedgerSchemaIT` (`infra/persistence/ledger/`), `ArchitectureTest` (`architecture/`) and the
updated `FlywayBaselineIT` (`infra/persistence/`). This slice adds no route and no screen, so it
has no frontend or user-manual surface.

**Lock proven by deliberate mutation.** With `@Lock(PESSIMISTIC_WRITE)` removed,
`exactlyOneOfTwoConcurrentDrainsCommits` fails with `[COMMITTED, COMMITTED]` (a double spend)
and `opposingTransfersDoNotDeadlock` fails with a lost update (`BRL 125.0000` where `BRL
85.0000` is correct). Restored before commit; the race test passes because of the lock, not
alongside it.

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
- DL-0011 — 2026-07-20 — `Money` lives in `domain.ledger` rather than a shared domain package:
  ArchUnit forbids any type directly under `com.fkbank.domain`, and the ledger owns money —
  decided by the architecture baseline (existing executable rule).
- DL-0012 — 2026-07-20 — Repository ports sit flat in `domain.ledger`, not in a
  `domain.ledger.port` subpackage. `docs/ARCHITECTURE.md` mentions the latter in prose while the
  same document, CLAUDE.md invariant 6 and the ArchUnit flatness rule require the former; the
  executable rule and the existing `domain.identity` layout decide — decided by the architecture
  baseline. The stale prose is flagged for a follow-up documentation fix.
- DL-0014 — 2026-07-20 — OPEN: the architecture's "PIT ≥60% on money-moving modules" floor is
  configured but not evidenced. PIT's coverage minion crashes at start-up on the development
  machine (`UNKNOWN_ERROR`) across the two permitted correction attempts; the profile ships
  correct but unproven, and no build step claims the floor is met. Needs an owner decision —
  see `docs/exec-plans/active/DEP-0001-ledger-dependencies.md`.
- DL-0013 — 2026-07-20 — Integration tests share one application context through a common base
  class, and each context's connection pool is capped: five independent contexts exhausted
  PostgreSQL's connection limit and failed unrelated test classes with "too many clients
  already" — decided by the build (observed failure).
