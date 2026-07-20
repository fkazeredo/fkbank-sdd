# FKBANK — Product

> Approved product baseline. Specifications remain the delivery contracts for individual
> capabilities and may refine this document through an explicit product decision.

## Vision

FKBANK is a Nubank-style digital bank delivered as a **web application**, built and
operated as a real product ("pretend it's real"): a person opens an account alone, moves
money the ways a Brazilian expects (PIX above all — fully and faithfully emulated), pays
and generates boletos, spends online with a virtual debit card, grows money in yield
boxes, takes a personal loan with transparent total cost, and controls their own limits —
all guarded by a transaction PIN and honest LGPD rights.

Every external system (SPI/DICT, boleto clearinghouse, KYC/credit bureau, card network,
CDI index, e-mail) is a **high-fidelity emulator** with a business API and a control API —
the core never knows it is not talking to the real rail (ARCHITECTURE §Emulators).

## Product areas (MVP)

| Area | What the customer gets | Specs |
|---|---|---|
| Current account | Sign-up with automatic KYC, account (branch 0001 + number), home with balance, statement with running balance, receipt per movement | 0002, 0003 |
| Money in | Deposit boleto; inbound PIX | 0004, 0005 |
| PIX (complete, faithfully emulated) | Own keys in the DICT, lookup preview, send with PIN, refunds (total/partial), static QR | 0005, 0008 |
| Payments & transfers | Internal transfer; pay any third-party boleto | 0007, 0009 |
| Card | Virtual debit card: issue, reveal (PIN-gated), online purchases, block/unblock, refunds, timeline | 0010 |
| Investments / boxes | Named yield boxes, add/withdraw, daily CDI yield, accumulated yield view | 0011 |
| Credit | Simulation with CET before contracting, contracting with PIN, disbursement, fixed installments auto-charged, tracking | 0012 |
| Profile & security | Transaction PIN (setup, lockout, reset), "My limits" (day/night/favorites, anti-coercion delay), notifications, LGPD privacy center | 0006, 0013, 0015, 0014 |
| Operations | Walking skeleton (secure login shell, real CI gates, deploy-ready stack), observability baseline (metrics, structured logs, OpenAPI UI) and dashboards/alerting as code | 0018, 0016, 0017 |

## Out of scope (MVP) — consolidated from the specs

Password recovery · profile photo · biometrics/WebAuthn · boleto PDF rendering · DDA ·
scheduled transfers/PIX/payments · dynamic QR · PIX portability claims · hold+capture card
mechanics · physical card · contactless/POS simulation · multiple yield products · box
goals with dates · early loan settlement with discount · refinancing · variable rates ·
collections beyond retry · marketing messages · push/SMS/WhatsApp channels · multi-currency
· device registration ("street mode" is native-app territory) · data sharing with third
parties · Kubernetes.

## Non-functional anchors

- Money integrity above everything: the DOMAIN invariants are the product.
- Card authorization latency budget: p99 < 300 ms local (SPEC-0010 BR-2) — the one numeric
  NFR with a name on it.
- Observable from the first deploy (SPEC-0016), alerting that pages on ledger-invariant
  violation (SPEC-0017).
- en-US is the only MVP locale; i18n by key from day 1. The user manual ships bilingual
  (en-US + pt-BR).

## Success criteria (MVP)

A new person can, unassisted: open an account (KYC approved) → cash in by boleto and PIX →
transfer and pay with PIN under their own limits → use PIX end to end (keys, send, refund,
QR) → spend on the virtual card → grow money in a box → contract a loan seeing the CET →
receive notifications → exercise LGPD rights — with every movement provable in the
statement, every receipt traceable to postings, and the ledger verification job green.
