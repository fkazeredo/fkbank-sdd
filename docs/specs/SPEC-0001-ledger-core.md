---
id: SPEC-0001
title: Ledger core
slug: ledger-core
status: IMPLEMENTED
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
implemented_at: 2026-07-20T15:31:19Z
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
| Ledger's own decisions in isolation (lock order, debit-before-credit, refusal precedence, stable codes) | `LedgerTest` (19 cases against fake ports) |
| Outbox base actually works, not just its column list | `LedgerEventPublicationIT` (publication persisted and completed through a real module listener) |
| Reversal chains and wholesale deletion refused by the database | `LedgerSchemaIT.refusesToTruncateThePostingTable` · schema triggers `posting_no_reversal_chain`, `balance_no_truncate` |

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
- DL-0015 — 2026-07-20 — CLOSED: the two test-quality gaps QA run 2 raised are fixed. QA-09 —
  the metric test that could not fail is renamed to state what it actually asserts (a refused
  movement is not counted) and says plainly that the after-commit guarantee is proven by the
  acceptance suite instead. QA-10 — `LedgerTest` now asserts the `UNKNOWN_ACCOUNT` and
  `UNKNOWN_POSTING` codes, so a silent return to an untyped exception fails the build.
- DL-0016 — 2026-07-20 — Accepted structural property, not a defect: `TrialBalance.isBalanced()`
  cannot report an imbalance today. A posting carries one amount debited from one account and
  credited to another, so total debits equal total credits by construction. The comparison stays
  because a posting that grows a third leg — a fee, a split settlement — is where it stops being
  free, and a check already in place fails loudly instead of being remembered. What catches a
  corrupt ledger today is the per-account comparison, which a tampered balance does trip while
  the totals still agree — decided by owner after independent review (QA-05).
- DL-0017 — 2026-07-20 — Reversal chains and wholesale deletion are now refused by the database,
  not only by the ledger. A partial unique index already stopped one posting being reversed
  twice, but said nothing about A reversed by B and then B reversed by C: every link unique,
  the balances still adding up, and the money moved a third time on a correction nobody
  authorised. A `BEFORE INSERT` trigger refuses it, since a `CHECK` cannot see another row.
  `TRUNCATE` guards now cover `balance` as well as `posting` — emptying `balance` would leave
  every posting intact and nothing in the product able to rebuild what it held. Deliberately not
  added: a database-level guard against a negative customer balance, which needs a join to the
  account kind and which the application role could disable anyway — that is a privilege-model
  question, not a schema one — decided by owner after independent review (QA-06, QA-07).
  Incomplete as written: `TRUNCATE` was guarded and `DELETE` was not, and the audit would not have
  reported the resulting state at all. Both closed by DL-0020.
- DL-0020 — 2026-07-20 — CLOSED, and the earlier entry was wrong twice over. The trial balance
  walked the balance rows, so an account with postings and no balance row was never examined:
  deleting the row produced `isConsistent() == true` over the exact damage the audit exists to
  find, and both the migration comment and QA's own run-2 report claimed the opposite. The audit
  now walks every account either side knows about and treats a missing row as drift — an unknown
  position is not a matching one. `balance` also gained a `DELETE` guard, since guarding only
  `TRUNCATE` left the same state one ordinary statement away, and the shared trigger function now
  names the table it fired on instead of always saying `posting` — found by QA run 3 (QA-11, QA-12,
  QA-14, QA-15).
- DL-0021 — 2026-07-20 — The audit takes its own transaction (`REQUIRES_NEW`). Isolation belongs to
  the transaction, not the method, so joining a caller's transaction silently handed the audit that
  transaction's level — making the repeatable-read guarantee in DL-0018 true everywhere except
  inside a batch or job, the caller most likely to need it — found by QA run 3 (QA-13).
- DL-0022 — 2026-07-20 — The mutation floor is met: 130 mutations, 99 killed (76%) against a
  required 60%, so DL-0014 is closed. The tool had never started because Spring Boot 4.1 brings
  JUnit Platform 6 while every `pitest-junit5-plugin` release targets 1.9, and both landed on the
  analysis JVM's classpath — a 1.9 launcher beside a 6.x engine, reported only as the fork exiting
  abnormally. Excluding the plugin's platform jars and pinning the launcher to the project's own
  version fixes it. The two earlier attempts failed because they changed configuration without ever
  reading the fork's error; turning on the correct verbose flag produced the cause in one run —
  decided by the build (observed failure).
- DL-0023 — 2026-07-20 — Role state is per session. `start-phase` writes
  `.claude/runtime/roles/<session>` alongside the shared `current-role`, and the path guard prefers
  it. A background worker and its parent share the runtime directory, so the last caller of
  `start-phase` decided what every concurrent session could write — safe when it narrowed rights,
  a hole when it widened them. Observed live: a QA worker's role blocked an unrelated edit in the
  main session. Verified in both directions before landing (session role overriding a permissive
  shared file, and overriding a restrictive one). The shared file remains the fallback so anything
  without a session is unaffected — decided by owner.
- DL-0019 — 2026-07-20 — A third QA run was authorised as an explicit exception to the two-run
  policy limit. The post-review fixes were made after QA's budget was spent, so they carried no
  independent verdict, and one of them changes transaction semantics on the money path. The
  exception is scoped to the post-review diff rather than reopening the slice, and it does not
  change the limit for future slices — decided by owner. Also decided in the same exchange: the
  spec's `implemented_at` is NOT stamped early. It stays empty until the automatic reconcile
  sweep records the real merge instant, exactly as SPEC-0016 was closed — decided by owner.
- DL-0018 — 2026-07-20 — `trialBalance()` runs at repeatable read, read-only, while movements
  keep the database default. The audit reads postings, balances and both totals and compares
  figures that must describe the same instant; at read committed a movement landing midway makes
  it report drift on an account that is perfectly fine, and an audit that cries wolf is one people
  learn to ignore. Movements are left alone because they already serialize on the row locks they
  take, so a stricter level would only add contention on the hottest path — decided by owner
  after independent review (reviewer finding 1).
- DL-0014 — 2026-07-20 — OPEN: the architecture's "PIT ≥60% on money-moving modules" floor is not
  evidenced. Two problems were stacked, and one is now closed. **Corrected after independent
  review**: an earlier version of this entry called the profile "correct but unproven", which was
  wrong — `excludedTestClasses` removed the integration tests that were the only ones exercising
  `Ledger`, so the class the floor exists for would have scored zero even on a working run.
  `LedgerTest` (19 cases against fake ports) now covers `Ledger` directly and the configuration is
  coherent. What remains is that **PIT still does not start**: its coverage minion exits
  `UNKNOWN_ERROR` before generating a mutant, identically across three measurements. The cause is
  unidentified and may be specific to this machine's JDK or the Maven wrapper on Windows; CI runs
  Linux, untested. No build step claims the floor is met. Owner decision — see
  `docs/adr/ADR-0001-ledger-dependencies.md`.
- DL-0013 — 2026-07-20 — Integration tests share one application context through a common base
  class, and each context's connection pool is capped: five independent contexts exhausted
  PostgreSQL's connection limit and failed unrelated test classes with "too many clients
  already" — decided by the build (observed failure).
