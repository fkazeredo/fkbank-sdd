---
id: SPEC-0016
title: Observability baseline
slug: observability-baseline
status: IN_PROGRESS
risk: R2
profile: standard
modules: [application, infra]
depends_on: [SPEC-0018]
relevant_adrs: []
reading_list:
  domain: []
  architecture: ["Observability", "Stack"]
planned_sprint: S1
planned_release: null
owner_approved_at: 2026-07-20T08:48:16Z
owner_approved_hash: 4fec593f77cee9b39b6d0b211d9ab0b348f4c0e36676c0f390d2ec2abb1b7302
split_review_required: true
---

# SPEC-0016 — Observability baseline

## Context

The walking skeleton must be observable from day one — a deploy nobody can see into isn't
really "in production," it's just running blind. This baseline gives every later slice
metrics and structured logs for free, and makes the API contract browsable.

## Scope

Micrometer wired to Spring Boot Actuator with `/actuator/prometheus` exposed; structured
JSON logging with `correlationId` propagated per request and into every log line, including
async/outbox-triggered ones; `/actuator/health` and `/api/version` public; interactive
OpenAPI/Swagger UI reachable and backed by the same document the CI drift gate snapshots.

## Out of scope

Grafana dashboards, alert rules, Loki ingestion pipeline — SPEC-0017 (S6). Distributed
tracing (post-MVP).

## Business rules

This spec is infrastructure, not money logic — no BR-numbered business rules. These are
operational contracts (OR), held to the same acceptance rigor as a BR:

- **OR-1** — Every HTTP request gets a `correlationId` (from an incoming header if present
  and well-formed, else generated); it's echoed in the response header and present in every
  log line produced while handling that request, including lines emitted by async
  processing the request triggered (e.g. an outbox consumer).
- **OR-2** — `/actuator/prometheus` exposes at minimum: request rate/latency/error-rate per
  endpoint and JVM/DB-pool health (Micrometer defaults), plus custom counters/gauges for the
  business signals named in `docs/ARCHITECTURE.md` §Observability (postings/min per rail,
  authorization failures, outbox queue depth).
- **OR-3** — Logs are structured JSON, one object per line: timestamp, level, logger,
  correlationId, message, and structured exception fields (class, message, stack) — never a
  free-text stack trace folded into the message field.
- **OR-4** — The interactive OpenAPI UI is reachable at the springdoc default path and lists
  every controller; the OpenAPI JSON it serves is the exact document the CI drift gate
  snapshots — one source, never two documents that can diverge.

## Acceptance criteria

- [ ] A request without an incoming correlationId gets one generated; a request WITH a
      well-formed one echoes it back, and it appears in every log line for that request
- [ ] `/actuator/prometheus` returns Prometheus exposition format; the `authorization
      failures` counter appears after at least one exercised operation (the other two OR-2
      business signals — postings/min per rail, outbox queue depth — have no real subject
      until SPEC-0001; see the Impact section's sequencing note)
- [ ] Logs emitted during a request are valid JSON, one per line, parseable without regex
- [ ] The OpenAPI UI loads and lists all R0–R1 endpoints; the served OpenAPI JSON matches
      the committed snapshot byte-for-byte
- [ ] correlationId survives into an async path: no outbox exists yet (lands with
      SPEC-0001), so this is proven at the mechanism level — dispatch work through the
      app's async task executor from within a request and confirm the MDC context (and a
      log line it emits) carries the same id as the triggering request. SPEC-0015, the
      first real outbox consumer, re-confirms this end-to-end (see Impact section)

## Edge cases

Malformed incoming correlationId header (generate a fresh one rather than trust unsanitized
input) · concurrent requests don't cross-contaminate correlationIds (thread-local/reactor
context test) · logging under load doesn't block request threads (async appender) · a
correlationId header past the servlet container's own line-length limit (Tomcat default
~8KB) never reaches the filter at all — Tomcat rejects the connection with a bare 400 before
the app sees it, so no id is echoed and the log line for that rejection carries none either;
the in-app 64-char well-formed limit (QA-verified: 64 accepted, 65 regenerated) makes this
Tomcat-level edge unreachable in practice, so it's noted here rather than coded around.

## Open Questions

_None._

## Impact

Migrations: none · Events: none (cross-cutting) · Contract: `/actuator/prometheus`,
OpenAPI UI, `/v3/api-docs` · Screens: none · Emulator: none

**Sequencing note:** this slice runs before ledger
(SPEC-0001) and the outbox on purpose, so two OR-2 business signals have no real subject
yet. Mechanism ships now, real data lands with the slice that creates the subject — see
`docs/ROADMAP.md` follow-through notes under SPEC-0001 and SPEC-0015:
- `fkbank.ledger.postings` counter is NOT created in this slice — there's no posting use
  case yet to attach meaningful `rail` tag values to. SPEC-0001 creates the counter
  (name + tags) and its increments together, in the same change, when postings land.
- Outbox queue-depth gauge is NOT created here (no queue to sample without a real outbox) —
  SPEC-0001 introduces the event-publication registry it samples.
- correlationId → async propagation ships as a generic mechanism (MDC-propagating
  `TaskDecorator` on the app's task executor), proven by a mechanism-level test, not a real
  outbox consumer (none exists yet). SPEC-0015, the first real outbox consumer, confirms it
  end-to-end.
- `authorization failures` is the OR-2 signal whose supporting security mechanism
  (default-deny + embedded Authorization Server) is delivered by SPEC-0018, the walking
  skeleton this slice depends on; this slice creates the counter and exercises it against
  the `401`/`403` events that baseline already produces.

**Distributed tracing switched off (`management.tracing.enabled: false`):** already Out of
scope per this spec, but also required today — Spring Modulith's observability
instrumentation (`spring-modulith-starter-insight`), gated behind that same property, may proxy
request-handling beans in ways that affect MockMvc once the app has multiple Modulith modules.
SPEC-0017 may re-enable tracing only after deliberately testing that combination.

**Public interactive docs, protected metrics (deliberate, QA-flagged as worth recording):**
`/v3/api-docs` and the Swagger UI are reachable without a token; `/actuator/prometheus` is
not. `docs/ARCHITECTURE.md` §Security's default-deny stance means every route is an explicit
choice, not an oversight — API documentation is customer/partner-facing by nature, while
metrics can carry business-volume signals better kept behind auth. Revisit if the API surface
ever includes anything sensitive enough that even its *shape* shouldn't be public.

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

- DL-0009 — 2026-07-17 — Baseline dependency made explicit: the walking skeleton
  (backend + frontend + CI) this spec assumes is delivered by SPEC-0018, commissioned by
  the owner when `/deliver-spec SPEC-0016` found an empty repository; `depends_on` now
  lists SPEC-0018 — decided by owner. (Numbered past DL-0008, which the roadmap cites from
  the previous repository's decision history.)
- DL-0010 — 2026-07-20T08:48:16Z — `/deliver-spec SPEC-0016` approved for delivery at content
  hash `4fec593f77cee9b39b6d0b211d9ab0b348f4c0e36676c0f390d2ec2abb1b7302`, its `depends_on`
  (SPEC-0018) now IMPLEMENTED — decided by owner (franklin.azeredo).

## Traceability

| Rule | Tests |
|---|---|
| OR-1 (correlationId per request, async propagation) | `CorrelationIdFilterTest` (8 cases) · `MdcTaskDecoratorTest` (3 cases) · `AsyncCorrelationPropagationIT` |
| OR-2 (`/actuator/prometheus`, authorization failures counter) | `ObservabilityRouteSecurityIT` (3 cases) |
| OR-3 (structured JSON logs) | `StructuredLoggingIT` (2 cases) |
| OR-4 (OpenAPI UI + drift gate) | `OpenApiAccessibilityIT` (2 cases) · `OpenApiSnapshotIT` |
| `/api/version` public | `VersionEndpointIT` |
| Security allowlist completeness | `RoutePermissionCompletenessIT` (updated) |
| Observability surface reachable through the deployed edge (QA rework: `@LocalServerPort` integration tests bypass nginx) | `frontend/e2e/observability-edge.spec.ts` (5 cases, QA-owned) |

All backend tests under `backend/src/test/java/com/fkbank/infra/observability/` unless noted
otherwise (`VersionEndpointIT` in `application/api`, `RoutePermissionCompletenessIT` in
`infra/security`). This slice has no frontend/UI feature, but does add backend routes exposed
through the nginx edge, which is exactly what `observability-edge.spec.ts` verifies.
