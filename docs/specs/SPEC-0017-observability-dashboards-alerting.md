---
id: SPEC-0017
title: Observability dashboards and alerting
slug: observability-dashboards-alerting
status: AWAITING_SPEC_APPROVAL
risk: R2
profile: standard
modules: [infra]
depends_on: [SPEC-0016]
relevant_adrs: []
reading_list:
  domain: []
  architecture: ["Observability", "Baseline decisions"]
planned_sprint: S6
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0017 — Observability dashboards and alerting

## Context

Metrics and logs with nowhere to look and nothing that pages anyone are exactly as blind as
having neither. This closes the loop SPEC-0016 opened: someone (the owner, solo) actually
finds out when the ledger stops proving itself.

## Scope

Grafana provisioned as code (`infra/grafana/`) with two dashboards; Prometheus alerting
rules provisioned as code; `docs/RUNBOOK.md` with one entry per alert; a synthetic-incident
acceptance drill proving the loop fires end to end, not just that the stack is installed.

## Out of scope

On-call/paging service integration — one person; alerts land in a channel/inbox in the MVP,
not a pager. Distributed tracing. Log-based alerting beyond what's listed below.

## Business rules

Operational contracts (OR), same acceptance rigor as a BR:

- **OR-1** — Golden-signals dashboard per module: request rate, error rate, p50/p95/p99
  latency, saturation (DB pool, JVM heap). Defined as JSON in `infra/grafana/dashboards/`
  and loaded automatically on Grafana startup — never a dashboard clicked together by hand
  and left undocumented in a UI nobody else can reproduce.
- **OR-2** — Money dashboard: postings/min per rail, ledger verification job result as a
  pass/fail time series, outbox queue depth, card-authorization p99 latency (the one NFR in
  `docs/ARCHITECTURE.md` with a numeric budget).
- **OR-3** — Alert rules, provisioned as code, at minimum:
  - **Ledger invariant violation** (the verification job reports a mismatch) → highest
    severity in the system, pages immediately — this is money integrity, not availability.
  - Outbox queue depth above threshold sustained for N minutes → warning.
  - Error rate above threshold on any money-moving endpoint → warning, escalates if
    sustained.
  - Any `*-ACL` health check failing (emulator unreachable) → warning.
- **OR-4** — Every alert rule ships with one `docs/RUNBOOK.md` entry: what it means, the
  first response step, where to look next. An alert without a runbook entry doesn't ship.

## Acceptance criteria

- [ ] Both dashboards render with real data after exercising the R1–R5 journeys
- [ ] Synthetic incident: kill Postgres in the isolated/staging stack → the ledger
      verification job fails on its next scheduled run → the alert fires and is visible in
      Grafana's alert state within the rule's evaluation window
- [ ] A deliberately duplicated outbox message does not fire a false queue-depth alert
      (idempotent consumer, per M2, keeps the metric accurate)
- [ ] `docs/RUNBOOK.md` has one entry per alert rule in OR-3, each followable by someone who
      didn't write the code
- [ ] Restarting the Grafana/Prometheus containers does not lose dashboards or rules (they
      reload from `infra/grafana/`, not from clicked-together UI state)

## Edge cases

Alert flapping at the exact threshold boundary (rules need a for-duration, not an instant
trigger) · alert firing during a planned deploy window (documented as expected in the
runbook entry, not silenced permanently).

## Open Questions

_None._

## Impact

Migrations: none · Events: none · Contract: none (internal ops surface — Grafana is its own
UI) · Screens: none · Emulator: none

## Traceability

Fill when implementing: the tests/drills covering each OR.

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
