# Changelog

All notable changes to FKBANK are documented here. Format: Keep a Changelog. One line per
slice under `[Unreleased]` (`SPEC-00NN — one-line summary`), consolidated into a versioned
section by `/release` Prepare. History is never deleted.

## [Unreleased]

- SPEC-0003 — Statement and receipts: a keyset-paginated, read-only projection over the ledger
  (newest-first, running balance derived over an account's full history, stable under
  concurrent inserts), a period navigator, and a public receipt per movement showing amount,
  rail, status and a masked counterparty CPF on peer transfers.

## [0.1.0] - 2026-07-20

First internal pilot / pre-release. Walking skeleton observable from day one, the accounting
heart proven by race tests, and a person opening an account and signing in.

- SPEC-0018 — Walking skeleton: the three-root backend structure with its ArchUnit and Modulith
  gates, default-deny authorization behind an embedded Authorization Server with OIDC and PKCE,
  Flyway over PostgreSQL, an Angular shell served through a single published edge, an end-to-end
  stack, and CI that runs all of it.
- SPEC-0016 — Observability baseline: per-request correlation id (propagated into async work
  and every structured JSON log line), protected Prometheus metrics with an authorization
  failures counter, and a public, drift-checked OpenAPI UI/document.
- SPEC-0001 — Ledger core: append-only double-entry postings, a trial balance that also catches a
  balance row that is missing rather than merely wrong, with a materialized balance,
  four-decimal `Money` rounded half-up only at the edge, customer accounts that can never go
  below zero, reversal by contra-posting at most once, and a trial balance that audits the
  saved balances against the postings themselves.
- SPEC-0002 — Sign-up and account opening: a person signs up, passes an automated KYC check
  against the bureau, has an account opened with its opening `$0.00` posting, signs in through
  OIDC and PKCE, and sees a zero balance — with same-CPF sign-ups converging to exactly one
  customer under real persistence, and sign-up idempotent by CPF while a check is pending.
