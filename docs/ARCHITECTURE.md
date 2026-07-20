<!-- Transcribed to the RELAY workflow on 2026-07-16. Subsequent owner-approved architecture
     decisions are recorded here and in the relevant specification Decision Logs. -->
# FKBANK — Architecture (single file)

Engineering rules in one place. Index: [Stack](#stack) · [Style](#style) ·
[Backend](#backend) · [Persistence](#persistence) · [Events](#events-and-integrations) ·
[Emulators](#emulators) · [Frontend](#frontend) · [Testing](#testing) ·
[Security](#security) · [Observability](#observability) · [CI and deploy](#ci-and-deploy) ·
[Baseline decisions](#baseline-decisions)

## Stack

Inherited from fkerp-java-sdd, not up for re-discussion: Java 21 · Spring Boot 4.1 · Spring
Modulith 2.1 · Spring Data JPA + PostgreSQL 16 · Flyway · Spring Security (OAuth2 Resource
Server + embedded Authorization Server) · springdoc-openapi · Angular 22 (standalone,
zoneless, signals) · PrimeNG (Aura) + Tailwind 4 · JUnit 5, Testcontainers, ArchUnit, jqwik,
PIT · Vitest, Playwright · Micrometer/Prometheus/Loki/Alloy/Grafana · Docker Compose ·
GitHub Actions + gitleaks + Dependabot.

Lombok is an approved backend build dependency and must be included when the backend module is
created. It may remove mechanical Java boilerplate, but it does not define the domain model and
must not hide construction rules or make invalid state easy to create.

## Style

The backend is a **hexagonal (ports & adapters) modular monolith with pragmatic DDD** —
well-defined from the start and non-negotiable. Exactly three packages sit directly below
`com.fkbank`, mapping to the hexagonal roles: `domain` is the core (aggregates, value objects,
domain services, ports — pure banking behavior, framework-free), `application` holds the
inbound/driving adapters (REST/queue/stream/etc.), and `infra` holds the outbound/driven
adapters and configuration (persistence, security, messaging, wiring). No fourth root package
is permitted. Pragmatic means no ceremony: no interactor/use-case classes, no empty
layers or grouping subpackages — behavior lives on the domain model, adapters stay thin. These
rules are ArchUnit-enforced so the style cannot silently drift back.

### Behavioral domain modeling

The domain is not a collection of data carriers. Aggregates and value objects are classes with
encapsulated state, valid construction and ubiquitous-language operations. State changes happen
through behavior that enforces invariants; delivery and persistence adapters must not assemble or
mutate domain state field by field. Prefer intention-revealing methods such as `approve`, `reject`,
`post`, `lock`, or `reverse` over generic setters and externally coordinated state transitions.

Java `record` is not the default domain type. Use it freely for immutable boundary messages such
as commands, domain events, DTOs and projections. A small value object may remain a record only when
its compact constructor rejects invalid values and the type owns the relevant behavior. Aggregates,
entities with lifecycle, and concepts whose invariants evolve are regular classes. Existing records
encountered in a changed slice must be evaluated by responsibility: refactor an anemic domain type
when the slice needs its behavior, but do not perform unrelated repository-wide churn.

Lombok is allowed for targeted boilerplate reduction (`@Getter`, `@EqualsAndHashCode`,
`@ToString`, carefully scoped constructors/builders). `@Data`, public setters, and generated
all-arguments constructors are forbidden on domain aggregates and entities because they expose
state instead of protecting it. Hand-write factories and behavior when that makes invariants clear.

- `domain.<bounded-context>` owns aggregates, value objects, domain services, use cases,
  domain events, and ports, placed **directly in the module package — bounded contexts are
  flat, with no grouping subpackages** (no `model`/`service`/`usecase`/`event`/`port`
  folders). Enforced by ArchUnit. This layer contains all banking
  behavior and does not import Spring. Domain types carry **ubiquitous-language names**; there
  are no use-case/interactor classes (`DescribeX`, `FetchX`) — trivial
  behavior lives on the model, and an application service appears only for real orchestration,
  named for the domain activity (ArchUnit-enforced). The domain **never depends on
  `application` or `infra`** — dependencies point inward to the core; `domain` may depend only
  on `domain`. `application` and `infra` depend on `domain`, never on each other. All four
  directions are ArchUnit-enforced.
- `application` contains delivery mechanisms only: `api`, `queue`, `stream`, `websocket`,
  and `scheduler`. It translates transport input into calls to domain use cases and maps
  domain output to transport DTOs. It depends on `domain`, never on `infra`.
- `infra` contains technical implementations: `persistence`, `security`, `messaging`,
  `observability`, `configuration`, and `integration`. It implements domain ports and depends
  on `domain`, never on `application`.

Bounded contexts are modules below `com.fkbank.domain`; cross-context collaboration uses
published domain events or explicit domain ports/contracts. Composition and framework wiring
live in `infra.configuration`. ArchUnit enforces the three roots, direction of dependencies,
bounded-context isolation, and the acyclic graph.

Spring Modulith uses `spring.modulith.detection-strategy=explicitly-annotated`; each
`domain.<bounded-context>` package is explicitly annotated as an application module. The
`application` and `infra` roots are adapters, not bounded-context modules. Named interfaces
expose only the context's `usecase`, `port`, and `event` contracts required by adapters or
other contexts. `ApplicationModules.verify()` and ArchUnit both run in `verify-slice` once the
backend exists (the backend skeleton is delivered by **SPEC-0018**, S1).

**The domain model is persistence-ignorant.** Aggregates carry invariants without Spring or
Jakarta Persistence annotations. Repository contracts live in `domain.<context>.port` and JPA
entities/repositories/mappers live in `infra.persistence.<context>`. A mapper may be omitted
only when the persistence technology can map the domain without framework annotations or
runtime coupling. Domain entities never leave their bounded context and never become JSON.

## Backend

- DTOs at the edge; an entity never crosses a module nor becomes JSON.
- Constructor injection; no `*Impl`; business exceptions carry a stable `code` + i18n message.
- Money: a `Money` value object (`BigDecimal` + currency), explicit and tested rounding.
- Idempotency mechanics (M2): a filter reads `Idempotency-Key`; single table
  `idempotency_record(operation, key, request_hash, response, status)` with a unique
  constraint; replay returns the stored response; same key with a different payload ⇒ `409`.
- Reference data vs enum (inherited): a business enum only for state machines (`*Status`),
  technical classes, or values fixed by law; everything else is reference data seeded by
  migration.
- Dates/times in UTC in the database; timezone only at the presentation edge.
- Javadoc required on every public delivery endpoint in `application` and every public
  domain use case — the surface a new dev reads first. Not required elsewhere;
  comments explain *why*, not *what*. Checked in review (`/review-pr` and human review), not a CI gate (Rule Zero).

## Persistence

- Flyway: an applied migration is immutable; a fix is a new migration.
- Balance concurrency: `SELECT ... FOR UPDATE` on the posting's accounts, always acquired in
  a stable order (ascending id) to avoid deadlocks (see Baseline decisions). Card authorization goes
  through the same path — the lock is short by design.
- Transactions: one use case = one transaction; posting + operation record + outbox in the
  same commit. Transaction boundaries are applied by an `infra` decorator/interceptor around
  domain use-case ports; domain code carries no `@Transactional`.

## Events and integrations

- Inside the monolith: Modulith events (business facts, past-tense names).
- Outward (notification, future consumers): transactional outbox in `infra` (the
  event-publication registry SPEC-0001 introduces), delivered by a relay; idempotent
  consumers; retry with backoff and a logical DLQ.
- External integration only through the owning module's ACL; emulator contracts are
  versioned and contract-tested (Testcontainers boots the emulator).

## Emulators

Pattern (see Baseline decisions): every external system (SPI/DICT, boleto clearinghouse, KYC/credit
bureau, card network, CDI index) is its own service under `emulators/<name>` with:

1. **Business API** — the realistic contract the core consumes;
2. **Control API** — `POST /control/scenario` to trigger scenarios (approve, decline,
   delay, fail, refund) in tests and demos;
3. A container in `docker-compose` (profile `emulators`) and in the E2E stack;
4. Configurable latency and failure; in-memory state; deterministic under a seed.

The core never knows it talks to an emulator; swapping in a real integration means changing
the URL in the ACL.

## Frontend

- Standalone components, signals, zoneless. **Folder structure (market-standard, features
  first):** `core/` holds app-wide singletons provided once (auth, guards, interceptors, runtime
  config, current-user); `shared/` holds cross-feature reusables (i18n, UI, models); `layout/`
  holds the app frame (the authenticated shell); `features/<feature>/` is one self-contained
  folder per product feature (Account, PIX, Pay, Boxes, Card, Credit, …) so the features are
  unmistakably clear — new features land as new `features/*` folders, never scattered.
  Non-component files carry their role in the name (`*.service.ts`, `*.guard.ts`,
  `*.interceptor.ts`, `*.pipe.ts`); components use Angular's concise style (`shell.ts`).
- PrimeNG + Tailwind; authenticated shell with product navigation (Account, PIX, Pay,
  Boxes, Card, Credit).
- Money formatted by a single pipe; never money arithmetic in the frontend.
- i18n by key from day 1 (en-US as the only MVP locale); accessibility: keyboard navigation
  on the main journeys.
- TSDoc required on public service methods and non-obvious computed signals; not required
  on components' internal logic. Checked in review (`/review-pr` and human review), not a CI gate (Rule Zero).

## Testing

- Pyramid: unit (pure domain) → per-module integration (Testcontainers: Postgres + the
  rail's emulator) → contract (OpenAPI snapshot with a drift gate) → Playwright E2E per
  journey, against the isolated stack (`compose.e2e.yaml`, ephemeral database).
- Inherited floors: JaCoCo 80% instruction / 65% branch; PIT ≥60% on money-moving modules
  (ledger, transfer, pix, billpay, card, savings, credit).
- jqwik mandatory on the math: `Money` rounding, yield, CET, schedules.
- **Every money-moving route is born with a deterministic race test** (two concurrent
  executions; the `docs/DOMAIN.md` invariants hold at the end) and an `Idempotency-Key`
  replay test.
- ArchUnit: exactly three direct `com.fkbank` packages; `domain` imports neither Spring nor
  Jakarta Persistence; `application` and `infra` depend inward on `domain` and not directly on
  each other; bounded contexts access only named ports/events/use cases; domain entities never
  reach delivery DTOs; no balance access outside `domain.ledger`.
- Spring Modulith: explicitly annotated bounded contexts, allowed dependencies/named interfaces,
  no cycles, and `ApplicationModules.verify()` in the architecture test suite.
- Isolation: count assertions require `@BeforeEach` cleanup (a known defect class of the
  house).

## Security

- OIDC + PKCE against the embedded Authorization Server; **default-deny** authorization with
  a completeness test (every route mapped to a permission). Delivered by **SPEC-0018** (S1)
  as part of the walking skeleton.
- Transaction PIN (OQ-4) verified in the backend on every movement; strong hash; failure
  counter and lockout.
- LGPD: consent recorded; erasure anonymizes the registration and preserves postings;
  masking of CPF/keys/sensitive values in logs.
- Secrets via environment variables; fail-fast on dev defaults in `prod`; gitleaks in CI.

## Observability

- `correlationId` per request, propagated to events, logs, and async/outbox processing
  (structured JSON). Delivered by **SPEC-0016** (S1) — metrics, logs, OpenAPI UI, so the
  walking skeleton is observable from its first deploy, not blind until hardening.
- Minimal business metrics: postings/min per rail, authorization failures, outbox queue,
  ledger verification job result (pass/fail time series).
- Dashboards and alert rules are **provisioned as code** (`infra/grafana/`) — never
  clicked together in a UI and left undocumented. Delivered by **SPEC-0017** (S6), with
  `docs/RUNBOOK.md` holding one entry per alert. Money-integrity alerts (ledger invariant
  violation) page immediately; the rest are warnings.
- `/api/version` exposed; interactive OpenAPI UI reachable — the API contract is browsable,
  not just internally validated by the CI drift gate.

## CI and deploy

- Pipeline gates (PR and main): backend build (`mvnw verify` = tests + ArchUnit + floors +
  OpenAPI drift) · frontend lint+test+build · E2E · gitleaks · CodeQL.
- Gitflow: protected `main`/`develop` (GitHub rulesets), PR required; SemVer set once per
  release — development on `-SNAPSHOT`, a slice never changes the version (RELAY decisions 27-28); images
  tagged in GHCR.
- Deploy: Docker Compose on a VM with a TLS proxy (`compose.prod.yaml`); Postgres without a
  public port; daily backups with retention and restore drills (inherited).

## Baseline decisions

The starting architecture is this file; these are the baseline choices where a real
alternative was rejected. Changes from here on are recorded as ADRs in `docs/adr/`
(template there), via `/adr` — this section never grows.

- **Modular monolith (Spring Modulith).** Rejected: microservices — operational and
  consistency cost with no problem demanding it (Rule Zero).
- **VM + Docker Compose, no Kubernetes.** Rejected: orchestrators/managed platforms —
  daily complexity for problems this project doesn't have; portability = move the compose.
- **Append-only ledger, materialized balance verified by a job.** Rejected: authoritative
  balance column (drifts, doesn't audit) and full event sourcing (beyond need).
- **Pessimistic lock for balance serialization** (`FOR UPDATE`, stable order). Rejected:
  optimistic + retry — breaks the card-authorization latency budget and muddies idempotency.
- **High-fidelity emulator per external system.** Rejected: in-process stubs for money
  rails — they exercise no network, webhook signing, timeouts or reconciliation.
- **Embedded Authorization Server.** Rejected: Keycloak — daily operational cost, no MVP
  gain; the OIDC contract keeps a future swap cheap.
- **Idempotency via `Idempotency-Key` + single replay table.** Rejected: per-rail dedupe
  (N copies of one mechanic) and business-constraint-only uniqueness (blocks legitimate
  repeats).
