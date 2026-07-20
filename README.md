# FKBANK

A Nubank-style digital banking web application, built as a real product: current account, PIX,
boleto, virtual debit card, yield boxes, personal loan, profile and transaction PIN.

Backend: Java 21 · Spring Boot 4.1 · Spring Modulith (modular monolith, hexagonal + pragmatic
DDD) · PostgreSQL 16 · Flyway. Frontend: Angular 22 (standalone, zoneless, signals) · PrimeNG ·
Tailwind 4.

> **Status:** the walking skeleton (SPEC-0018) is in place — OIDC/PKCE login, an authenticated
> shell and one protected endpoint, with the architecture gates that guard everything built after
> it. The observability baseline (SPEC-0016) is in place too — metrics, structured logs and a
> browsable API contract from the first deploy. Product features arrive with their own
> specifications; see `docs/ROADMAP.md`.

## Requirements

Java 21, Node 22, Docker. The Maven and npm wrappers pin everything else.

## Run it

```bash
# 1. Database
docker compose -f compose.dev.yaml up -d

# 2. Backend (http://127.0.0.1:8080)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (http://127.0.0.1:4200)
cd frontend && npm ci && npm start
```

Open the frontend and you are redirected to the login page. The development credential is
`e2e.user` / `e2e-password` — it exists only under the `dev` and `e2e` profiles, and the `prod`
profile refuses to start while it is still configured. Real sign-up arrives with SPEC-0002.

After signing in you land on the authenticated shell: the six product areas (Account, PIX, Pay,
Boxes, Card, Credit) and the username the backend returned from `GET /api/me`.

### The whole application on one origin

```bash
node infra/e2e-preflight.mjs
docker compose -f compose.e2e.yaml up -d --wait --build
# everything on http://127.0.0.1:8090
```

An nginx edge serves the SPA and proxies the API and the Authorization Server, so the browser
sees a single origin and the issuer inside a token matches the address the browser used. Use
`127.0.0.1`, never `localhost`: on Windows `localhost` resolves to IPv6 `::1` first, where
nothing is listening.

## Verify

One deterministic command per level — never improvise the battery:

```bash
tools/quality/verify-fast     # < 5 min
tools/quality/verify-slice    # < 15 min - mvnw verify + frontend lint/test/build
tools/quality/verify-e2e      # ephemeral stack + Playwright
tools/quality/verify-release  # < 30 min
```

Use the `.ps1` variants on Windows and `.sh` on POSIX and CI. `./mvnw verify` runs the ArchUnit
suite, `ApplicationModules.verify()`, unit and Testcontainers integration tests, and the JaCoCo
floors (80% instruction / 65% branch) — a gate that stops running fails the build rather than
degrading into a silent skip.

Dependency updates are deliberately not automated (see `docs/ARCHITECTURE.md` §Stack);
vulnerabilities are still gated in CI by the Trivy supply-chain scan.

## Observability

Every request carries a correlation id (`X-Correlation-Id`, generated if none is sent, echoed
back either way) into every log line it produces, including one dispatched onto the app's async
task executor. Logs are structured JSON (one object per line, exceptions in dedicated fields).

| Endpoint | Auth | What |
|---|---|---|
| `GET /actuator/health` | public | liveness |
| `GET /actuator/prometheus` | bearer token | Prometheus exposition metrics |
| `GET /api/version` | public | the running build's own version |
| `GET /swagger-ui/index.html` | public | interactive API docs |
| `GET /v3/api-docs` | public | the OpenAPI document behind the UI, drift-checked in CI |

## Architecture in one paragraph

Exactly three packages sit below `com.fkbank`: `domain` (aggregates, value objects and ports —
banking behavior, framework-free), `application` (delivery adapters) and `infra` (persistence,
security, wiring). Dependencies point inward, bounded contexts are flat and carry
ubiquitous-language names, and the domain model is behavioral rather than a set of data
carriers. None of this is convention: ArchUnit and Spring Modulith fail the build when it
drifts.

## Documentation

| What | Where |
|---|---|
| Product | `docs/PRODUCT.md` |
| Domain, invariants, chart of accounts | `docs/DOMAIN.md` |
| Engineering rules | `docs/ARCHITECTURE.md` |
| Delivery plan | `docs/ROADMAP.md` |
| Specifications | `docs/specs/` |
| User manual | `docs/manual/product/` (en-US and pt-BR) |
| Workflow guide | `docs/manual/operational/` |
| Decisions | `docs/adr/` and each spec's Decision log |

Development follows the RELAY workflow: `/deliver-spec <id>` per slice, then
`/close-sprint <sprint>`. See `docs/workflow/README.md`.
