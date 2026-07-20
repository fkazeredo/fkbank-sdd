---
id: SPEC-0018
title: Walking skeleton
slug: walking-skeleton
status: READY
risk: R3
profile: critical
modules: [identity, application, infra]
depends_on: []
relevant_adrs: []
reading_list:
  domain: ["Module map"]
  architecture: ["Stack", "Style", "Backend", "Persistence", "Frontend", "Testing", "Security", "CI and deploy", "Baseline decisions"]
planned_sprint: S1
planned_release: null
owner_approved_at: 2026-07-20T06:19:40Z
owner_approved_hash: 3b92f204a3cce77ee8cb897ffe46dd8dcb3e69950ab2e1359c6b0b080b38fe05
split_review_required: true
---

# SPEC-0018 — Walking skeleton

## Context

The repository holds the full document set but zero production code — every Sprint 1 slice
assumes a running application that does not exist yet. This slice raises the thinnest
end-to-end thread of the real architecture: a person logs in through OIDC/PKCE and the
authenticated shell calls one protected endpoint. Everything later slices assume — the
three-root structure, the security baseline, the build gates, the E2E stack — is born here,
enforced, and demonstrable.

## Scope

One vertical thread through the whole documented architecture, nothing more:

- **Backend** (`backend/`, Maven wrapper committed): Java 21 · Spring Boot 4.1 · Spring
  Modulith 2.1 · Lombok for targeted boilerplate reduction (never `@Data`/public setters on
  domain aggregates or entities). Exactly three root packages under `com.fkbank` (`domain`, `application`,
  `infra`); first explicitly-annotated bounded context `domain.identity` with one use case
  (describe the authenticated principal) delivered at `GET /api/me` through
  `application.api`; `infra.persistence` (Flyway + PostgreSQL 16), `infra.security`
  (embedded Authorization Server + OAuth2 resource server, default-deny),
  `infra.configuration` (wiring).
- **Architecture gates, active from the first build**: ArchUnit suite (root-package law,
  dependency directions, no Spring/Jakarta Persistence imports in `domain`, bounded-context
  isolation) + `ApplicationModules.verify()` + JaCoCo floors (80% instruction / 65% branch)
  bound to `./mvnw verify`; Testcontainers integration test proving the V1 baseline
  migration applies against PostgreSQL 16.
- **Frontend** (`frontend/`): Angular 22 (standalone, zoneless, signals) · PrimeNG (Aura) +
  Tailwind 4; OIDC + PKCE login against the embedded Authorization Server; authenticated
  shell with the six product navigation placeholders (Account, PIX, Pay, Boxes, Card,
  Credit); i18n by key (en-US); Vitest + lint + build.
- **E2E**: Playwright smoke journey (login → shell → `/api/me`) against an ephemeral
  `compose.e2e.yaml` stack published on the single IPv4 origin `http://127.0.0.1:8090`,
  with an environment preflight that tears down stale stacks and frees the port.
- **Runtime artifacts**: `compose.dev.yaml` (PostgreSQL for local dev), `compose.e2e.yaml`
  (full ephemeral stack), `compose.prod.yaml` + Dockerfiles (deploy-ready; no cutover).
- **CI completion**: `verify-slice`/E2E jobs start executing the real build (the scripts
  already auto-detect `backend/`/`frontend/`); add CodeQL workflow and Dependabot config
  (Maven, npm, GitHub Actions) per ARCHITECTURE §CI and deploy.

## Out of scope

Actuator, metrics, structured logging, correlationId, springdoc/OpenAPI UI and the OpenAPI
snapshot/drift gate — all of SPEC-0016, which layers directly on this skeleton. Business
tables and domain rules (SPEC-0001, SPEC-0002). Real customer credentials and sign-up
(SPEC-0002). Transaction PIN (SPEC-0006). Emulators (each arrives with its consuming spec).
Money pipe in the frontend (first money screen, SPEC-0003). VM cutover, TLS proxy
provisioning and GHCR image publication (release concern; owner had no VM as of
2026-07-15). PIT floor (applies to money-moving modules; none exists yet).

## Business rules

This spec is infrastructure, not money logic — no BR-numbered business rules. These are
operational contracts (OR), held to the same acceptance rigor as a BR:

- **OR-1 — Root package law.** The only packages directly under `com.fkbank` are `domain`,
  `application`, and `infra`; `application` and `infra` depend on `domain` and never on
  each other; `domain` imports neither Spring nor Jakarta Persistence. Enforced by ArchUnit
  and `ApplicationModules.verify()` failing the build, not by convention.
- **OR-2 — Default-deny.** Every HTTP route requires authentication unless it is on the
  explicit public allowlist, which is empty in this slice (`/actuator/health` and
  `/api/version` go public in SPEC-0016). A route-permission completeness test enumerates
  every registered route; unmapped routes = 0. Unauthenticated ⇒ `401`; authenticated but
  unauthorized ⇒ `403`.
- **OR-3 — OIDC + PKCE only.** The SPA is a public client (PKCE, no client secret); tokens
  are issued by the embedded Authorization Server. The seeded login credential exists only
  in the `dev` and `e2e` profiles; booting the `prod` profile with any dev-default secret
  aborts startup (fail-fast).
- **OR-4 — Persistence baseline.** An applied Flyway migration is immutable — a fix is a
  new migration; the database stores UTC. V1 is a baseline marker only: the first business
  tables belong to SPEC-0001/SPEC-0002.
- **OR-5 — One toolchain truth.** `tools/quality/verify-fast`, `verify-slice` and CI run
  the same canonical commands (`./mvnw verify`; npm lint/test/build; Playwright E2E). Gates
  are bound to the default build lifecycle so a gate that stops running fails the build —
  it never degrades into a silent skip.

## Acceptance criteria

- [ ] With PostgreSQL 16 from `compose.dev.yaml` up and the `dev` profile active, the
      backend starts and `flyway_schema_history` contains exactly one applied migration
      (the V1 baseline) with `success = true`
- [ ] `./mvnw verify` (same command locally and in CI) executes the ArchUnit suite,
      `ApplicationModules.verify()`, unit + Testcontainers integration tests, and the
      JaCoCo check (80% instruction / 65% branch) — exit 0 with every gate reported as
      executed, none skipped
- [ ] Unauthenticated `GET /api/me` under the real security configuration (not a mocked
      test slice) ⇒ `401` with the standard error contract and no stack trace in the body;
      the same request with a bearer token obtained through the PKCE flow against the
      embedded Authorization Server ⇒ `200` carrying the authenticated username
- [ ] The route-permission completeness test enumerates every registered HTTP route and
      reports exactly 0 routes without a permission mapping or allowlist entry
- [ ] Playwright journey against the ephemeral `compose.e2e.yaml` stack on
      `http://127.0.0.1:8090`: the seeded `e2e` credential logs in via PKCE redirect, the
      authenticated shell renders all six navigation entries (Account, PIX, Pay, Boxes,
      Card, Credit) and displays the username returned by `/api/me` — journey green on the
      first run
- [ ] Booting with the `prod` profile while any dev-default secret is still in place aborts
      startup with a configuration error naming the offending property, before any port is
      opened
- [ ] A PR touching `backend/` and `frontend/` runs GitHub Actions `verify-slice` executing
      the real `./mvnw verify` plus frontend lint/test/build, and the E2E job executes the
      Playwright smoke — logs show real execution (not the pre-bootstrap no-op) and all
      required checks are green

## Edge cases

Dev box shares `localhost:4200`/`:8080` with another project bound on IPv6 (`::1`), and
Windows resolves `localhost` to IPv6 first — hence the single IPv4 origin
`127.0.0.1:8090` and the preflight teardown (DL-0005) · Windows operator vs POSIX CI: every
script ships `.ps1` + `.sh` variants and the Maven/npm wrappers pin the toolchain ·
Testcontainers requires a Docker daemon — its absence fails the build loudly, never skips
the integration tests · zoneless Angular + PrimeNG compatibility is asserted by the Vitest
suite running zoneless from day one.

## Failure modes

PostgreSQL unreachable at boot ⇒ fail fast with a clear connection error (no half-started
app) · embedded Authorization Server misconfigured ⇒ the E2E login journey fails loudly ·
dev-default secrets reaching `prod` ⇒ blocked by the fail-fast boot check (OR-3) · CI
runner without Docker ⇒ Testcontainers tests fail the build (never silently skipped).

## Concurrency / Idempotency

No money-moving route exists in this slice, so there is no `Idempotency-Key` surface yet —
M2 mechanics (filter + single replay table) are born with the first money-moving spec, as
required by CLAUDE.md invariant 2. The authenticated endpoint is stateless and read-only;
no shared mutable state is introduced.

## Audit

No business audit trail yet (no business events exist). Authentication/authorization
failures surface as `401`/`403` with framework-standard logging; structured JSON logs,
correlationId and the authorization-failures metric are SPEC-0016, which counts the events
this slice's default-deny produces.

## Rollback

No production environment, no business data, no business tables (V1 is a baseline marker).
The slice is purely additive: rollback = revert the merge commit. No expand/contract
migration concerns exist at this point.

## Open Questions

_None._

## Impact

Migrations: V1 baseline (no business tables) · Events: none · Contract: `GET /api/me`
(OpenAPI snapshot and UI deferred to SPEC-0016 — single owner for the contract document,
DL-0004) · Screens: login redirect + authenticated shell with six navigation placeholders ·
Emulator: none · Relevant ADRs: — (baseline choices live in `docs/ARCHITECTURE.md`
§Baseline decisions).

**Sequencing note:** SPEC-0016 depends on this slice — it layers metrics, structured logs
and the OpenAPI UI on the skeleton, and its `authorization failures` counter counts the
`401`/`403` events this slice's default-deny baseline produces. SPEC-0002 replaces the
dev-seeded credential journey with real onboarding-issued credentials.

**Split review (frontmatter flag):** `/design-slice` must evaluate a split before planning —
backend skeleton + CI activation vs frontend shell + E2E stack are separable if the
one-session fit check fails; the walking-skeleton thread (login → `/api/me`) must land whole
in whichever slice completes it.

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|policy|architecture|assumption>`

- DL-0001 — 2026-07-17 — Walking skeleton commissioned as its own spec after `/deliver-spec
  SPEC-0016` exposed the unresolved baseline (empty repository); SPEC-0016 now depends on
  SPEC-0018 — decided by owner.
- DL-0002 — 2026-07-17 — Risk R3: this slice delivers the authorization baseline
  (default-deny, embedded Authorization Server, PKCE); the risk model lists authorization
  as an R3 driver and mandates going up when in doubt — decided by policy.
- DL-0003 — 2026-07-17 — A seeded login credential exists only in `dev`/`e2e` profiles to
  make the PKCE journey demonstrable before SPEC-0002 delivers real onboarding; `prod`
  fail-fasts on dev defaults — decided by assumption (visible for owner approval with this
  spec's hash).
- DL-0004 — 2026-07-17 — springdoc/OpenAPI (dependency, UI, snapshot, drift gate) stays
  wholly in SPEC-0016 so the contract document has a single owner — decided by architecture.
- DL-0005 — 2026-07-15 — Dev/E2E stack publishes a single IPv4 origin `127.0.0.1:8090` with
  an environment preflight (the dev box's other project binds `localhost:4200`/`:8080` on
  IPv6) — decided by owner (carried forward from the previous repository).
- DL-0006 — 2026-07-17 — The minimal vertical exercises `domain.identity` (owner of
  credentials and authorization policy per DOMAIN §Module map); no new `platform` bounded
  context is created — decided by architecture.
- DL-0007 — 2026-07-20 — Two earlier delivery attempts were interrupted by the operator and
  their feature branches abandoned (commits dangling, never pushed, never merged). The
  operator directed a clean restart from `develop`, discarding all leftover working-tree
  state; nothing tracked was lost, since neither attempt had any tracked file outside
  `docs/`. Delivery restarts from `develop`@`4edf424` — decided by owner.
- DL-0008 — 2026-07-20 — Spec approved at content hash
  `3b92f204a3cce77ee8cb897ffe46dd8dcb3e69950ab2e1359c6b0b080b38fe05` — the hash of this file
  as presented for approval (`develop`@`4edf424`, i.e. before this approval record was
  appended); reverify with `git show 4edf424:docs/specs/SPEC-0018-walking-skeleton.md`. The
  content is unchanged since the Lombok/behavioral-DDD edit in `81862e4`, so the design is
  derived under CLAUDE.md invariant 9 from the start — decided by owner via
  `/deliver-spec 18`.

## Traceability

Fill when implementing: the tests covering each OR (class#method / E2E file).
