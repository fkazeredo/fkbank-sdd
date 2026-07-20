# Changelog

All notable changes to FKBANK are documented here. Format: Keep a Changelog. One line per
slice under `[Unreleased]` (`SPEC-00NN — one-line summary`), consolidated into a versioned
section by `/release` Prepare. History is never deleted.

## [Unreleased]

- SPEC-0001 — Ledger core: append-only double-entry postings, a trial balance that also catches a
  balance row that is missing rather than merely wrong, with a materialized balance,
  four-decimal `Money` rounded half-up only at the edge, customer accounts that can never go
  below zero, reversal by contra-posting at most once, and a trial balance that audits the
  saved balances against the postings themselves.
- SPEC-0016 — Observability baseline: per-request correlation id (propagated into async work
  and every structured JSON log line), protected Prometheus metrics with an authorization
  failures counter, and a public, drift-checked OpenAPI UI/document.
