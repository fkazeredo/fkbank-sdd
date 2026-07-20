# FKBANK — Domain (single file)

> Approved domain baseline. Section names and numbered cross-cutting rules are stable because
> specifications reference them directly. Material changes require an explicit domain decision.

Index: [Module map](#module-map) · [Chart of accounts](#chart-of-accounts) ·
[Money flows](#money-flows) · [Invariants](#invariants) ·
[Verifiable invariants](#verifiable-invariants) · [Cross-cutting rules](#cross-cutting-rules) ·
[Ubiquitous language](#ubiquitous-language) · [Domain decisions](#domain-decisions)

## Module map

One module per bounded context below `com.fkbank.domain`. The only root packages below
`com.fkbank` are `domain`, `application`, and `infra`. Cross-context collaboration uses
published domain events or explicit ports/contracts; package internals remain private.
Delivery mechanisms belong to `application`; technical implementations belong to `infra`.
Spring Modulith discovers only explicitly annotated `domain.<bounded-context>` packages;
adapter roots are not domain modules.

| Module | Owns | May depend on |
|---|---|---|
| identity | Credentials, authorization policy, transaction PIN, lockout | — |
| onboarding | Sign-up flow and KYC orchestration | customer, identity, account (ports/events) |
| customer | Registration data, consents, LGPD lifecycle (export, correction, erasure-anonymize) | — |
| account | Accounts (branch 0001 + sequential number), statement, receipts | ledger (ports/events) |
| ledger | Chart of accounts, double-entry postings, materialized balance, verification job | — |
| limits | User and regulatory transaction limits (SPEC-0013) | identity (ports/events) |
| transfer | Internal transfers between FKBANK accounts | ledger, account, identity, limits (ports/events) |
| pix | PIX keys/DICT, inbound, outbound, refunds, static QR | ledger, account, identity, limits (ports/events) |
| billpay | Deposit boleto cash-in and third-party boleto payment | ledger, account, identity, limits (ports/events) |
| card | Virtual debit card, authorization, refunds | ledger, identity, limits (ports/events) |
| savings | Yield boxes and daily yield | ledger, identity (ports/events) |
| credit | Personal loan: limit, simulation (Price/CET), contracting, charging | ledger, identity (ports/events) |
| notification | Notification center and outbound notification intent | published domain events |

Security wiring, HTTP filters, logging, asynchronous execution, metrics, persistence, and
external-system clients are technical concerns under `infra`; REST, queue, stream, WebSocket,
and scheduler entry points are delivery concerns under `application`. Neither category is a
domain module.

The "may depend on" column is the canonical dependency baseline. Dependencies remain acyclic
and cross bounded-context boundaries only through declared ports, use cases, or events.

## Chart of accounts

Seeded by migration (SPEC-0001). Account id ordering is the lock-acquisition order (BR-5).

| Account | Kind | Sign rule |
|---|---|---|
| `customer:available` (per customer) | Customer | **Never below zero** |
| `customer:box:{id}` (per box) | Customer | **Never below zero** |
| `internal:settlement:boleto` | Internal settlement | May go negative |
| `internal:settlement:pix` | Internal settlement | May go negative |
| `internal:settlement:card` | Internal settlement | May go negative |
| `internal:expense:yield` | Internal expense | May go negative |
| `internal:credit:disbursement` | Internal credit | May go negative |
| `internal:credit:receivable:{loan}` (per loan) | Internal credit | May go negative |

A violating posting raises `INSUFFICIENT_FUNDS` and writes nothing (SPEC-0001 BR-2).

## Money flows

Every flow is a double-entry posting in the ledger — no rail bypasses it.

- **Deposit boleto settle** (SPEC-0004): `internal:settlement:boleto → customer:available`
- **Inbound PIX** (SPEC-0005): `internal:settlement:pix → customer:available`
- **Internal transfer** (SPEC-0007): `customerA:available → customerB:available`
- **Outbound PIX** (SPEC-0008): `customer:available → internal:settlement:pix`; SPI expiry
  auto contra-posting back (money never lost); refund of an inbound = its own posting
- **Boleto payment** (SPEC-0009): `customer:available → internal:settlement:boleto`;
  clearinghouse fail-after-confirm ⇒ contra-posting back
- **Card authorization** (SPEC-0010): `customer:available → internal:settlement:card`
  inside the authorization response window (direct debit — see Domain decisions); refund =
  contra-posting, cumulative ≤ original
- **Box add / withdraw** (SPEC-0011): `customer:available ↔ customer:box:{id}`
- **Daily yield** (SPEC-0011): `internal:expense:yield → customer:box:{id}`, business days,
  idempotent by (box, date)
- **Loan disbursement** (SPEC-0012): `internal:credit:disbursement → customer:available`
- **Installment charge** (SPEC-0012): `customer:available → internal:credit:receivable:{loan}`,
  idempotent by (loan, installment)
- **Reversal** (any rail, SPEC-0001): a contra-posting referencing the original; at most
  once; a reversal is never itself reversed (BR-6)

## Invariants

The five mechanical invariants every module obeys (referenced everywhere as M1–M5):

- **M1 — Append-only ledger.** A posting is a debit/credit pair, atomic, immutable; a fix
  is a contra-posting. Enforced by a DB trigger + immutable entity.
- **M2 — Idempotent money.** Every money movement is idempotent: `Idempotency-Key` at the
  REST edge (single replay table; same key + different payload ⇒ `409`) or a natural key at
  webhooks/batches (boleto id, end-to-end id, network tx id, (box, date), (loan, installment)).
- **M3 — Statement is derived.** Statement and receipts derive from postings; never a
  parallel balance/statement table.
- **M4 — Money value object.** `BigDecimal` + currency; 4 internal decimals; rounding to 2
  (half-up) only at the presentation edge; never money arithmetic in the frontend.
- **M5 — Only the ledger touches balances.** No module reads or writes a balance except
  through the ledger's internal API (ArchUnit-enforced).

## Verifiable invariants

Asserted by tests (race tests must hold these at the end) and by the ledger verification job:

1. Globally and per posting: Σ debits = Σ credits.
2. Every account's materialized balance = Σ of its postings.
3. `customer:available` and `customer:box:*` balances ≥ 0 at all times.
4. An idempotent replay does not change the ledger (same response, zero new postings).
5. A posting is reversed at most once; a reversal is never reversed (`REVERSAL_NOT_ALLOWED`).

## Cross-cutting rules

Numbered — specs cite them by number; do not renumber.

1. **Ledger-only money.** Every money movement, on every rail, is a ledger posting; no code
   path moves value outside it. This is the cross-cutting expression of M1 and M5.
2. **Transaction PIN** (SPEC-0006): every money-moving request carries the 4-digit PIN,
   verified in the backend; wrong PIN never reaches the ledger; 3 failures ⇒ lockout;
   rate-limited per account.
3. **Layered limits** (SPEC-0013): every money-moving route consults the limits engine
   BEFORE the ledger; hierarchy favorites > daytime > nighttime; `422 LIMIT_EXCEEDED`
   names the limit hit; nothing posted.
4. **Box yield** (SPEC-0011): business days only; `balance at day start × daily CDI
   factor`; 4-decimal internal math; idempotent by (box, date); no retroactive catch-up.
5. **Credit transparency** (SPEC-0012): fixed monthly rate, Price amortization; CET
   (including the financed contracting fee) is displayed on every simulation BEFORE
   contracting; immutable schedule.
6. **Statement, receipts and masking** (SPEC-0003): every statement line maps to
   posting(s); running balance of the newest line = account balance; every completed
   movement has exactly one receipt with a public unique id; amounts through the single
   money pipe; identifiers masked (CPF ⇒ `***.456.789-**`).
7. **LGPD** (SPEC-0014): versioned consent, append-only consent log; export on request;
   erasure anonymizes registration irreversibly while postings/receipts remain under legal
   retention, unlinked; CPF/keys/PAN masked in every log and non-essential screen.

## Ubiquitous language

**Posting** (immutable debit/credit pair) · **Contra-posting** (the reversal posting) ·
**Materialized balance** (per-account cache, verified against Σ postings) · **Rail**
(a money channel: pix, boleto, card, transfer, yield, credit) · **Box** (named yield
sub-account) · **Digitable line** (boleto's typeable code) · **End-to-end id** (PIX unique
transaction id) · **DICT** (PIX key directory) · **SPI** (PIX settlement system) ·
**Receipt** (public proof of one completed movement) · **Idempotency-Key** (client replay
header, M2) · **KYC** (bureau approval at onboarding) · **CDI** (daily yield index) ·
**CET** (total effective cost of credit) · **Price schedule** (equal-installment
amortization) · **Test book** (human-executable manual test script, `docs/tests/TB-*.md`) ·
**Slice** (one spec implemented end to end: migration → domain → API → screen).

## Domain decisions

Product-level decisions the specs depend on (engineering baselines live in
`docs/ARCHITECTURE.md` §Baseline decisions):

- **Direct debit card** (SPEC-0010): the MVP virtual debit card debits `customer:available`
  in the authorization window — no hold+capture. Rejected: hold+capture fidelity (post-MVP).
- **Transaction PIN separate from the login password** (SPEC-0006, market-decided OQ-4).
- **Limits hierarchy** favorites > daytime > nighttime; nighttime cap $1,000 between
  20:00–06:00 (customer may shift start to 22:00); favorite default = 2× daytime, valid in
  both windows; raises take effect after 24h, lowers immediately (SPEC-0013, owner decision
  2026-07-15).
- **Web application — no device registration/fingerprinting** (owner decision 2026-07-15).
- **Product timezone UTC-3 fixed in the MVP**: daily counters reset at product-timezone
  midnight; database stores UTC (SPEC-0013 edge cases; ARCHITECTURE §Backend).
- **Yield without catch-up**: days without a published CDI index yield nothing (SPEC-0011).
- **Erasure preserves the ledger**: LGPD erasure anonymizes the person, never the postings
  (SPEC-0014).
