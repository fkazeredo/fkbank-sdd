# Security Assurance Report — Sprint 1 release candidate

**Expected duration of this assurance run: 45 minutes.** Actual wall clock is recorded in
§10 Execution log.

| Field | Value |
|---|---|
| Candidate SHA | `d9aad61948571f4d6c596eb96a848deebd2526fb` |
| Branch | `release/0.1.0` |
| Release class | Internal pilot / pre-release (no end-user-exposed environment) |
| Track | `docs/security/SECURITY-ASSURANCE-TRACK.md` (owner-approved 2026-07-17) |
| Executed by | `security-assurance-engineer`, independent worker invoked by `/close-sprint 1` |
| Date | 2026-07-20 |
| **Verdict** | **`BLOCKED`** — mandatory control family 6 (Dynamic assurance) did not execute; see §6 and §9 |

---

## 1. Candidate integrity — PASS (with one recorded observation)

### 1.1 Exact SHA and clean tree

```
$ git rev-parse HEAD
d9aad61948571f4d6c596eb96a848deebd2526fb
$ git rev-parse --abbrev-ref HEAD
release/0.1.0
$ git status --porcelain | wc -l
0
```

The working tree is clean; every control below ran against this exact revision.

### 1.2 Delta against the merged `develop` tip

```
$ git diff --stat d354ae9 d9aad61
 docs/ROADMAP.md                                    | 2 +-
 docs/exec-plans/{active => completed}/PLAN-0002.md | 0
 docs/specs/SPEC-0002-signup-account.md             | 3 ++-
 3 files changed, 3 insertions(+), 2 deletions(-)
```

Verified independently: the delta is documentation-only. No source, test, configuration,
migration, Dockerfile or compose file differs between the candidate and the merged develop tip.

### 1.3 CI evidence — OBSERVATION: CI has not run on the exact candidate SHA

```
$ gh api repos/fkazeredo/fkbank-sdd/commits/d9aad61.../check-runs
{"message":"No commit found for SHA: d9aad619...","status":"422"}
```

`d9aad61` does not exist on `origin`, so no CI run is associated with the candidate SHA itself.
CI evidence exists only for `d354ae9`:

```
$ gh run view 29778900272 --json jobs
gitleaks                 => success
workflow-smoke           => success
supply-chain-deps        => success
supply-chain-containers  => success
verify-slice             => success
e2e                      => success
```
CodeQL on the same SHA: `success` (2026-07-20T21:06:59Z).

**Disposition.** The track asks for "CI evidence" on the candidate. Strictly, that evidence is
attached to `d354ae9`, not `d9aad61`. It is accepted here because §1.2 proves by diff that all
product code is byte-identical, and because the assurance run below re-executed the full release
battery locally against the candidate itself. This is recorded as an observation rather than
waved away: the honest statement is *"CI ran on identical product code at a different SHA"*, not
*"CI passed on the candidate"*.

### 1.4 Changed-surface inventory (Sprint 1)

| Spec | Risk | Security-relevant surface |
|---|---|---|
| SPEC-0018 | R3 | Default-deny authorization, embedded Authorization Server, OIDC + PKCE, nginx edge, Flyway/Postgres, Angular shell, CI |
| SPEC-0016 | R2 | Correlation id + MDC, structured JSON logs, token-protected Prometheus, public OpenAPI |
| SPEC-0001 | R3 | Append-only double-entry ledger, `Money` 4-decimal, `FOR UPDATE` serialization, reversal-at-most-once, trial balance |
| SPEC-0002 | R3 | Sign-up + account opening, CPF/e-mail uniqueness, PII, KYC bureau emulator + webhook, credential issuance, same-CPF race |

---

## 2. Threat model — PASS

### 2.1 Assets and data classification

| Asset | Classification | Where it lives |
|---|---|---|
| CPF | PII, LGPD-regulated | `customer.cpf`, `onboarding.cpf`, request bodies, **and application logs — see SEC-F-01** |
| Full name, birth date, declared income | PII | `customer`, `onboarding` |
| E-mail / username | PII, account identifier | `customer.email`, `credential.username` |
| Password | Secret (never at rest in clear) | `credential.password_hash` only |
| Access / refresh tokens | Bearer secret | Client-side, `Authorization` header |
| Ledger postings and balances | Financial integrity, append-only | `posting`, `balance` |
| Bureau HMAC secret | Shared secret | Environment (`FKBANK_BUREAU_HMAC_SECRET`) |
| Onboarding id | Capability token (bearer-equivalent) | URL path, **and logs — see SEC-F-11** |

### 2.2 Actors

Anonymous internet caller · Applicant (pre-authentication, holds an onboarding id) ·
Authenticated customer (OIDC token) · KYC bureau emulator (HMAC-signing webhook caller) ·
Operator / log reader · CI and the delivery agent (repository write access).

### 2.3 Trust boundaries

1. Internet → nginx edge (`frontend/nginx.conf`, port 8090).
2. Edge → backend (`/api/**`, `/oauth2/**`, `/v3/api-docs/**`).
3. Backend → PostgreSQL (internal network only in every compose file; no published port).
4. Bureau emulator → backend webhook (`POST /api/webhooks/bureau`, HMAC-authenticated).
5. Backend → log stream (JSON to stdout) — **a real boundary, and the one SEC-F-01 crosses.**
6. Developer machine / agent → GitHub repository — **unprotected, see SEC-F-02.**

### 2.4 Abuse cases mapped to controls

| Abuse case | Control | Verified in |
|---|---|---|
| Open an account for a CPF that already banks here | Partial unique index `onboarding_one_live_per_cpf` + `customer.cpf UNIQUE` | §5.5 |
| Win a same-CPF race with concurrent requests | DB uniqueness, loser caught and mapped | §5.5 |
| Forge a KYC approval | HMAC-SHA256 over raw body, constant-time compare, checked before parse | §5.3 |
| Replay a captured KYC approval | **Absent** — mitigated only by settle-once | SEC-F-05 |
| Read another customer's account | Default-deny + per-token principal | §5.2 |
| Read metrics without a token | `anyRequest().authenticated()` | §5.4 |
| Read metrics with *any* customer token | **No scope check** | SEC-F-06 |
| Exhaust CPU via password hashing | **No rate limit** | SEC-F-07 |
| Harvest CPFs from logs | **No masking on the DB-constraint path** | SEC-F-01 |
| Bypass review and push to `develop` | **No branch protection** | SEC-F-02 |

---

## 3. Static assurance — FAIL (repository-configuration control executed and failed)

Four of five controls in this family passed. The fifth executed and returned a negative result.
The family is reported `FAIL` because a control that ran and failed is not a control that passed;
the owner's acceptance of the resulting risk is recorded as a *disposition* in §8, and per
`security-gate.md` risk acceptance never converts a failed or skipped control into a verified one.

### 3.1 Secrets — PASS

Executed on the candidate by `tools/security/verify-assurance.ps1`. No local `gitleaks` binary is
installed on this host, so the wrapper's digest-pinned container path ran:
`ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c95...`

```
9:25PM INF no leaks found
```

### 3.2 SAST — PASS (on identical product code)

CodeQL (`java-kotlin`, `javascript-typescript`, `.github/workflows/codeql.yml`) concluded
`success` on `d354ae9`. Same §1.3 caveat: identical product code, different SHA. No SAST wrapper
runs CodeQL locally, so this control cannot be re-executed against `d9aad61` on this host.

### 3.3 Dependencies — PASS (on identical product code)

`supply-chain-deps` → `tools/security/supply-chain/trivy-scan.sh deps`, Trivy pinned
`aquasec/trivy:0.72.0@sha256:cffe3f51...`. Gate: `GATE_SEVERITIES="HIGH,CRITICAL"` (`trivy-scan.sh:34`),
`--exit-code 1`. Job concluded `success` ⇒ no HIGH/CRITICAL dependency vulnerability in the Maven
or npm graphs.

### 3.4 Licenses — PASS, report-only by design

`report_then_gate "deps (licenses)" ... "report-only"` (`trivy-scan.sh:119`). Licenses are scanned
and reported but do not gate. The wrapper states the reason explicitly: several runtime
dependencies are dual-licensed and *"deciding which licenses are acceptable for a bank is a
material decision that belongs to the owner"* (`trivy-scan.sh:114-118`). This is a deliberate,
documented deferral, not a silent gap. Recorded as SEC-F-12 (Low) so it is not lost.

### 3.5 Repository configuration — **FAIL**

Independently confirmed, not inherited:

```
$ gh api repos/fkazeredo/fkbank-sdd/rulesets
[]
$ gh api repos/fkazeredo/fkbank-sdd/branches/develop/protection
{"message":"Branch not protected","status":"404"}
$ gh api repos/fkazeredo/fkbank-sdd/branches/main/protection
{"message":"Branch not found","status":"404"}
$ gh api repos/fkazeredo/fkbank-sdd/branches --jq '.[].name'
chore/autonomous-workflow-behavioral-ddd
develop
feature/spec-0001-ledger-core
feature/spec-0002-signup-account
feature/spec-0016-observability-baseline
feature/spec-0018-walking-skeleton
```

The repository has no rulesets and no branch protection of any kind. `main` does not exist on
`origin`. See SEC-F-02.

---

## 4. Architecture — PASS

Enforced by `ArchitectureTest.java` + `ModulithTest.java`, executed twice in this run
(`verify-release` and `backend-tests`, both green). Independent inspection of the actual tree:

- Root packages under `com.fkbank` are exactly `domain`, `application`, `infra`. Confirmed.
- `domain/*` bounded contexts are flat: `account`, `customer`, `identity`, `ledger`, `onboarding`,
  zero subdirectories, no class directly in `com/fkbank/domain/`.
- `domain` has **no** third-party imports at all. The only `org.springframework` references are
  five fully-qualified `@ApplicationModule` annotations on `package-info.java`, covered by the
  rule's documented exemption. Zero `jakarta.persistence`, zero Jackson.
- **No domain type at a delivery boundary.** All six controllers/advice in `com.fkbank.application.api`
  were inspected: every response body is a local record, every `@RequestBody` is a local record or
  `byte[]`, and domain types appear only inside static `of(...)` mappers.

**Enforcement gaps (no live violation — recorded as SEC-F-13, Low).** The candidate complies, but
three invariant-6 clauses are enforced more weakly than they read:
"domain entities never cross a bounded-context boundary" has no ArchUnit rule (delegated to
Modulith, which *whitelists* the crossings via `allowedDependencies`); "domain entities never
become JSON" is convention only; and `onlyTheLedgerTouchesBalances` is a four-name regex that
does not match `JpaBalanceRepository` or `TrialBalance` and never inspects method calls. This is a
defense-in-depth observation about the guardrails, not a defect in the candidate.

---

## 5. Adversarial behavior — PASS (with Medium findings)

### 5.1 Sensitive logging — one confirmed defect

`backend/src/main` contains exactly two logging statements, and both are argument-safe.
`Cpf.toString()` masks (`Cpf.java:47-49,79-81`), and `Customer`/`Onboarding` route through it.
`RawPassword`, `PasswordHash` and `BureauReference` all render as `[protected]`. Error responses
set `include-stacktrace: never` and `include-message: never`.

**However — SEC-F-01, confirmed from this run's own runtime output.** A duplicate-CPF signup
causes Hibernate to log the constraint violation *including the raw CPF*:

```json
{"log":{"level":"WARN","logger":"org.hibernate.orm.jdbc.error"},
 "message":"ERROR: duplicate key value violates unique constraint \"onboarding_one_live_per_cpf\"\n
            Detalhe: Key (cpf)=(27702836261) already exists.",
 "correlationId":"b2c98d77-4c33-4af8-b714-bfac4127c839"}
```

This is not a test artifact. `org.hibernate.orm.jdbc.error` is not downgraded in any profile:

```
$ grep -rn "org.hibernate.orm.jdbc" backend/src/
(no output)
```

`application-prod.yml` sets only `org.springframework.security: WARN`. The Hibernate logger emits
at WARN, above the default root level, so the same line is written in **prod**. The trigger is an
ordinary, unauthenticated, attacker-reachable action: submitting a signup for a CPF that already
has a live application. The application's careful masking is bypassed because the CPF reaches the
log through the JDBC driver's message, not through a domain `toString()`.

### 5.2 Authorization matrix — default-deny verified

`SecurityConfig.java`, chain `@Order(2)`, rules in order:

| Matcher | Rule |
|---|---|
| `/actuator/health`, `/api/version`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | `permitAll` |
| `POST /api/signup`, `GET /api/signup/{onboardingId}` | `permitAll` |
| `POST /api/webhooks/bureau` | `permitAll` |
| `anyRequest()` | `authenticated()` |

Default-deny is genuine: `anyRequest().authenticated()` is terminal, `/actuator/prometheus` is
*not* on the allowlist, and `RoutePermissionCompletenessIT` asserts coverage. `/actuator/health`
exposes status only (`show-details` unset ⇒ Boot default `never`). CSRF is disabled for `/api/**`
only, which is sound because those routes are bearer-only and read no session cookie.

### 5.3 Webhook — HMAC correct, replay protection absent

Shared-secret HMAC-SHA256 over the raw body, header `X-Bureau-Signature`. Signature is verified
**before** the body is parsed (`BureauCallbackController.java:78-88`), and the comparison is
constant-time (`MessageDigest.isEqual`, `HmacBureauCallbackSignature.java:51`). The secret is
injected, never hardcoded in the prod path. No nonce, no timestamp, no expiry ⇒ SEC-F-05.

### 5.4 Metrics protection — verified by runtime test, and it confirms a finding

The static reading left open whether a `JwtDecoder` bean actually backs
`.jwt(Customizer.withDefaults())` — no `spring.security.oauth2.resourceserver.*` property exists
in any profile. That question is settled by an integration test that ran green in this session:

```
ObservabilityRouteSecurityIT:
  "/actuator/prometheus answers 401 without a token"        → assertThat(statusCode).isEqualTo(401)
  "/actuator/prometheus serves exposition text [with token]" → assertThat(statusCode).isEqualTo(200)
```

This discriminates: the 200 leg obtains a real access token and is decoded successfully, so the
decoder demonstrably exists and works. The same test also proves SEC-F-06 — an ordinary customer
access token, with no scope requirement, reads the full metrics endpoint.

### 5.5 Idempotency, concurrency and the same-CPF race — verified sound

Enforcement is at both layers, and the database has the final say:

- `V3__signup_account.sql:15` — `cpf VARCHAR(11) NOT NULL UNIQUE` on `customer`.
- `V5__onboarding_one_live_application_per_cpf.sql:17-21` — replaces the original
  `WHERE status = 'PENDING'` partial index with
  `CREATE UNIQUE INDEX onboarding_one_live_per_cpf ON onboarding (cpf) WHERE status IN ('PENDING','APPROVED')`.

V5 closes a real race that V3 left open (approved application no longer `PENDING` ⇒ a second
application could be inserted). The loser of a concurrent race is caught and mapped rather than
blocked (`SignUp.java:101-117`); `SignUp.submit` deliberately holds no transaction so the bureau
call does not pin a connection. `REJECTED` is intentionally outside the index so a refused
applicant may reapply. No unsafe window found.

Ledger concurrency uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`
(`BalanceJpaRepository.java:20`) — the `FOR UPDATE` serialization SPEC-0001 requires.

### 5.6 Injection — no finding

All persistence goes through Spring Data / JPA with bound parameters; the correlation-id header is
sanitized against CRLF log injection by `WELL_FORMED = [A-Za-z0-9._-]{1,64}`
(`CorrelationIdFilter.java:29`) before it is echoed. The `V3` CPF column additionally carries
`CHECK (cpf ~ '^[0-9]{11}$')`.

### 5.7 Rate and abuse behavior — no control exists

No bucket4j, no resilience4j, no nginx `limit_req` anywhere in `backend/src/main`,
`frontend/nginx.conf` or the compose files. See SEC-F-07 and SEC-F-09.

---

## 6. Dynamic assurance (DAST) — **NOT EXECUTED** (mandatory control, R3 candidate)

**No DAST scan ran against this candidate. No ZAP report exists. This is the reason the verdict
is `BLOCKED`.**

Target attempted: the approved local ephemeral stack `compose.security.yaml`, project
`app-security`, brought up by this worker and by nothing else. No external or hosted target was
contacted at any point.

### 6.1 What happened

```
$ cat .claude/runtime/security-candidate/controls.txt
release-verification=PASS
secrets(docker)=PASS
backend-tests=PASS
dynamic-security=FAIL (exit=1)
```

```
 Container app-security-db-1 Healthy
 Container app-security-backend-1 Starting
 Container app-security-backend-1 Started
 Container app-security-backend-1 Waiting
 Container app-security-backend-1 Error dependency backend failed to start
dependency failed to start: container app-security-backend-1 is unhealthy
exit=1
```

`edge` declares `depends_on: backend: condition: service_healthy`, and `security-tests` (ZAP)
declares `depends_on` on both. The backend never reported healthy, so **`edge` never started and
the ZAP container never ran a single request.** The evidence directory confirms the absence — the
scan produced no artifacts at all:

```
$ ls .claude/runtime/security-candidate/
controls.txt  docker-down.log  verify-assurance.log
```

No `zap-report.html`, no `zap-report.json`, no `zap-api-report.html`. The wrapper's
`dast-report=` line was never reached.

### 6.2 Root cause — the healthcheck in `compose.security.yaml` cannot run in the backend image

The application itself started correctly. From the security stack's own container output:

```
backend-1 | ... "logger":"org.springframework.boot.tomcat.TomcatWebServer" ...   21:28:09.816Z
backend-1 | ... "logger":"com.fkbank.FkbankApplication" ...                      21:28:09.873Z
```

The failure is in the probe, not the product. `compose.security.yaml:43`:

```yaml
test: ["CMD-SHELL", "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/me | grep -qE '401|200'"]
```

The backend runtime image ships no `curl`:

```
$ docker run --rm eclipse-temurin:21-jre-alpine sh -c "command -v curl || echo 'NO CURL'; command -v wget || echo 'NO WGET'"
NO CURL
/usr/bin/wget
```

With `curl` absent the command produces no stdout, `grep` matches nothing, the probe exits
non-zero, and it does so identically on all 40 retries — the container can never become healthy,
regardless of the application's actual state.

This is a known trap in this repository, and every other compose file already avoids it. The
backend's own `Dockerfile:34-37` uses `wget` and says so explicitly — *"busybox wget is what this
image has"* — and `compose.prod.yaml:38-39` carries the same warning. `compose.security.yaml`
overrides the Dockerfile's correct `HEALTHCHECK` with a broken one.

**Consequence: the DAST profile is structurally unable to run.** This is not a transient
environment failure that a re-run would clear; the stack cannot come up as configured, so family 6
has never executed against this candidate — and, by the same reasoning, could not have executed
against any previous one. Recorded as SEC-F-16.

### 6.3 What was therefore not assessed

The entire dynamic attack surface is unmeasured for this candidate: the ZAP baseline pass over the
edge with the AJAX spider, and the OpenAPI-seeded API scan against `/api/*`, `/login` and
`/oauth2/*`. Concretely, none of the `zap-baseline.conf` FAIL-list rules were evaluated — CSP,
anti-clickjacking, `X-Content-Type-Options`, Permissions-Policy, debug-error disclosure, PII
disclosure, open redirect, source-code disclosure, application-error disclosure, session id in
URL. Several of those (PII disclosure and application-error disclosure in particular) bear
directly on findings raised statically in §5, so their absence is not a formality.

Reporting this family as anything other than NOT_EXECUTED would be exactly the failure the track
warns about: a control whose evidence would look the same whether or not it had run.

---

## 7. Supply chain and deployment — PASS (with findings)

### 7.1 Container least privilege — PASS

| Image | Runtime user | Base |
|---|---|---|
| `backend/Dockerfile` | `USER fkbank` (non-root) | `eclipse-temurin:21-jre-alpine` |
| `frontend/Dockerfile` | `USER 101` (non-root) | `nginxinc/nginx-unprivileged:1.29-alpine` |
| `emulators/bureau/Dockerfile` | `USER fkbank` (non-root) | `eclipse-temurin:21-jre-alpine` |

All three declare `HEALTHCHECK`. No container runs as root at runtime. Base images are pinned by
tag but not by digest — Trivy's `config` and `images` scans (`supply-chain-containers`) concluded
`success` with the same HIGH/CRITICAL gate, so no gating misconfiguration exists.

### 7.2 Secret injection and secure defaults — PASS with SEC-F-03

`application-prod.yml` blanks the development defaults (`${FKBANK_BUREAU_HMAC_SECRET:}`,
`${FKBANK_ISSUER:}`) and `ProductionSecretsGuard` (`@Profile("prod")`) refuses to construct the
context if either is blank or still the development value — it fails closed, before the port
opens, and it is covered by `ProductionSecretsGuardTest`. This is a genuinely good control.

Its gap is verified first-hand: the guard checks exactly two properties
(`ISSUER_PROPERTY`, `BUREAU_SECRET_PROPERTY`), while `application.yml:8` carries
`password: ${APP_DB_PASSWORD:app}` and `application-prod.yml` contains no `datasource` override at
all. See SEC-F-03.

The SPA is correctly a **public** client: `client-authentication-methods: none`,
`require-proof-key: true` ⇒ PKCE is mandatory and there is no client secret to leak.

### 7.3 Provenance — NOT IMPLEMENTED (SEC-F-12)

```
$ grep -rlE "cyclonedx|sbom|spdx|cosign|sigstore|attestation" --include=*.yml --include=*.yaml --include=*.xml --include=*.sh --include=*.ps1 .
(no output)
```

No SBOM is produced, no image is signed, and no build provenance attestation is emitted. For an
internal pilot with locally-built images this is acceptable; it is a prerequisite for production.

### 7.4 Rollback — PASS

Flyway migrations are additive (`V3`, `V5`); `V5` drops and recreates an index only. No
destructive migration is present in Sprint 1, so roll-forward is available and rollback does not
require data recovery.

---

## 8. Findings

Severity is this worker's own assessment. Where it differs from an upstream rating, the reasoning
is stated. No **product** finding below exceeds Medium. The `BLOCKED` verdict is driven by
SEC-F-16 — an assurance-harness defect that prevented a mandatory control family from executing —
not by an unresolved High/Critical product vulnerability.

| ID | Finding | Severity | Disposition |
|---|---|---|---|
| SEC-F-16 | `compose.security.yaml:43` health-checks the backend with `curl`, which the `eclipse-temurin:21-jre-alpine` runtime image does not contain. The backend can never report healthy, so `edge` and the ZAP container never start and **the entire DAST family cannot execute** | **High (assurance)** — not a product vulnerability | **Open, and the direct cause of `BLOCKED`.** Fix: use the `wget` form already proven in `backend/Dockerfile:34-37` and `compose.prod.yaml:38-39`, or drop the override and inherit the image's own `HEALTHCHECK`. Then re-run the track. |
| SEC-F-01 | Raw CPF written to application logs on the duplicate-CPF path via `org.hibernate.orm.jdbc.error`; applies to **prod** | **Medium** | Open. Must fix before production. Set `logging.level.org.hibernate.orm.jdbc.error: OFF`/`ERROR`-with-masking, or intercept the constraint violation before Hibernate logs it. CWE-532. |
| SEC-F-02 | No branch protection or rulesets on the repository; `main` absent on `origin` | **Medium** (Critical as a production precondition) | Owner-accepted for this window (§9.2). Control executed and **failed** — acceptance is a disposition, not a pass. |
| SEC-F-03 | `ProductionSecretsGuard` does not cover `spring.datasource.password`; `${APP_DB_PASSWORD:app}` would silently apply in prod | **Medium** here; **High** if production-destined | Open. Not deployed in this candidate (prod profile is unused). Add the datasource password to the guard. |
| SEC-F-04 | `/v3/api-docs/**` and `/swagger-ui/**` are `permitAll` in **all** profiles including prod, and proxied by the edge | Medium | Open. Gate behind a profile before production. |
| SEC-F-05 | Bureau webhook has no replay protection (no nonce, timestamp or expiry) | Medium | Open. Contained today by settle-once idempotency and a private `BureauReference`, so a replay cannot be retargeted; the containment is not the control. |
| SEC-F-06 | `/actuator/prometheus` requires only `authenticated()`; any customer token reads all metrics | Medium | Open. Require a dedicated scope or a separate management port. Confirmed by `ObservabilityRouteSecurityIT`. |
| SEC-F-07 | No rate limiting on any route; `POST /api/signup` drives password hashing on the request thread | Medium | Open. CPU-exhaustion vector on an unauthenticated route. |
| SEC-F-08 | `compose.prod.yaml` never passes `FKBANK_BUREAU_HMAC_SECRET`/`FKBANK_ISSUER`, so the guard throws on every start | Medium | Open. Fails **closed**, so it is an incomplete template rather than an exposure. No working production deployment path exists in this branch. |
| SEC-F-09 | `@RequestBody byte[]` on the public webhook with no size bound ⇒ unauthenticated buffer + HMAC amplification | Low-Medium | Open. Compounds SEC-F-07. |
| SEC-F-10 | `Credential.toString()` emits the raw e-mail (username); siblings in the same PII cluster are masked | Low | Open, latent — not currently reachable by any logger. |
| SEC-F-11 | `CorrelationIdFilter` logs `request.getRequestURI()`, writing the `onboardingId` capability token into the log stream | Low-Medium | Open. Anyone with log read access can poll any applicant's status. |
| SEC-F-12 | No SBOM, image signing or provenance attestation; license scan is report-only | Low | Accepted for an internal pilot; production prerequisite. License deferral is documented and deliberate. |
| SEC-F-13 | ArchUnit enforcement weaker than invariant 6 reads (cross-context entities, domain-to-JSON, balance regex) | Low | Open. **No live violation** — the candidate is compliant; the guardrail is thin. |
| SEC-F-14 | Connection-pool exhaustion surfaces as `401` on a public route (unhandled exception forwarded to `/error`, which default-deny covers) | Low | Open. See §8.1. |
| SEC-F-15 | The owner risk acceptance for SEC-F-02 is not recorded in `docs/security/DECISIONS.md` (file contains "_No entries yet._") | Process | **Blocks the grounding of this verdict** — see §9.2. |

### 8.1 SEC-F-14 — independent disposition (not inherited)

QA carried this forward from SPEC-0018 rated MEDIUM. This worker assessed it independently and
rates it **Low**, for reasons that are about the failure's direction:

- **Mechanism confirmed statically.** `/error` is not on the `permitAll` allowlist, so an
  exception forwarded to the error dispatch is evaluated by `anyRequest().authenticated()` and
  answered `401`. Confirmed by reading `SecurityConfig.java`; a direct `GET /error` yields `401`.
- **It fails closed, not open.** The wrong status code is `401`, not `200`. No data is disclosed,
  no authorization is bypassed, and ledger integrity is untouched.
- **Real cost is operational, not security.** A resource-exhaustion incident is disguised as an
  authentication problem, which delays diagnosis. That is an observability defect.

**Honest limit on this disposition: I did not dynamically reproduce pool exhaustion.** The
approved DAST stack publishes no host ports and is destroyed by `--abort-on-container-exit` the
moment the scan ends, so driving concurrent load against it inside this run's budget was not
possible. The mechanism is confirmed by code; the *trigger* is not re-demonstrated here. Recorded
as an assessed-but-not-reproduced control rather than reported as verified.

---

## 9. Verdict

### 9.1 `BLOCKED`

The track states the rule without room for interpretation: `BLOCKED` on *"missing
approval/evidence/environment/control, mandatory skipped control"*. Family 6, Dynamic assurance,
is mandatory for a candidate containing R3 slices, and it did not execute (§6). The candidate
therefore cannot be verified, and it cannot be downgraded to `SECURITY_OBSERVATIONS` either —
that state presupposes that the applicable track *ran* and produced observations. Here a whole
family produced nothing.

`SECURITY_VERIFIED` was already unavailable for two further independent reasons:

1. The repository-configuration control in family 3 **executed and failed** (§3.5).
   `SECURITY_VERIFIED` requires all applicable mandatory controls to have executed *and passed*.
2. CI evidence does not exist on the exact candidate SHA (§1.3), only on byte-identical product
   code at `d354ae9`.

**What `BLOCKED` does not mean here.** It is not a statement that the product is unsafe. Every
control that *could* run did run, and the static, architectural and behavioral families found no
High or Critical product vulnerability. It means the release candidate has an unmeasured dynamic
attack surface, and this worker will not sign off on a surface it did not measure.

### 9.2 Path to re-verdict

1. Fix SEC-F-16 (a one-line healthcheck change in `compose.security.yaml`; no production code is
   involved, and this worker did not make it — the track forbids this role from modifying the
   candidate).
2. Re-run `tools/security/verify-assurance.ps1 -RequiresHeavy` so family 6 actually executes and
   emits `zap-report.html` / `zap-api-report.html`.
3. Disposition whatever the ZAP FAIL-list produces.
4. Owner records the SEC-F-02 acceptance in `docs/security/DECISIONS.md` (§9.3).

If the DAST pass is then clean and the owner's acceptance is recorded, the reachable verdict for
this internal pilot is `SECURITY_OBSERVATIONS` — the Medium findings in §8 remain open and are
enumerated as production preconditions in §9.5. `SECURITY_VERIFIED` remains unavailable while
§3.5 stands.

### 9.3 A second precondition, independent of the above — SEC-F-15

`security-gate.md` permits `SECURITY_OBSERVATIONS` for an internal candidate **"only with a
recorded owner risk decision."** `docs/security/DECISIONS.md` currently reads `_No entries yet._`

The owner's acceptance of SEC-F-02 was relayed to this worker through the orchestrating agent's
task description. **An agent's message is not the owner's recorded decision.** This worker cannot
write that acceptance itself — doing so would be accepting risk on the owner's behalf, which the
track reserves to the owner alone.

**Required before this verdict is properly grounded:** the owner records the SEC-F-02 acceptance
in `docs/security/DECISIONS.md` in the documented format
(`SEC-NNNN — YYYY-MM-DD — decision — decided by <owner>`), with the remediation deadline the
finding policy requires.

### 9.4 Distinction the track asks to be made explicit

A **skipped** control and a **failed** control are not the same thing, and neither is cured by
risk acceptance:

- A *not-executed* mandatory applicable control (family 6, §6) ⇒ `BLOCKED`. Nothing can be
  accepted in its place, because there is no result to accept. Risk acceptance applies to a known
  risk; an unrun scan is an unknown one.
- A *failed* control (SEC-F-02, §3.5) ⇒ a finding with a severity and a disposition. The owner may
  accept the resulting risk, and that acceptance permits `SECURITY_OBSERVATIONS` for an internal
  candidate — but it never converts the failure into a pass, and it can never produce
  `SECURITY_VERIFIED`.

Both appear in this run, and they are recorded differently on purpose. Collapsing them — treating
the unrun DAST as "no findings" — is the specific error the track's evidence rule exists to
prevent: an empty report and a clean report look identical unless you check whether the scanner
ever started.

### 9.5 Production preconditions

This candidate must not be promoted to a production environment until, at minimum: SEC-F-01
(CPF in logs), SEC-F-02 (branch protection), SEC-F-03 (DB password guard), SEC-F-04 (public API
docs), SEC-F-06 (metrics scope) and SEC-F-07 (rate limiting) are resolved, SBOM/provenance
(SEC-F-12) exists, and the track is re-executed on the production candidate SHA. Production
requires `SECURITY_VERIFIED`, which this report does not grant.

---

## 10. Execution log

### 10.1 Duration

Declared budget 45 minutes; actual wall clock approximately **36 minutes**
(2026-07-20 ~21:20Z → ~21:36Z). No control was dropped for time. The only control not attempted
for budget reasons is the dynamic reproduction of SEC-F-14, recorded as such in §8.1 and §10.5.

### 10.2 Environment

| Tool | Version |
|---|---|
| Docker | 29.5.3 (build d1c06ef) |
| git | 2.45.2.windows.1 |
| gh | 2.92.0 |
| gitleaks | v8.28.0 (digest-pinned container; no local binary on host) |
| Trivy | 0.72.0 (digest-pinned, via CI) |
| OWASP ZAP | 2.16.1 (digest-pinned) |
| Host | Windows 11 Pro 10.0.26200 |

### 10.3 Commands executed by this worker

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/workflow/start-phase.ps1 `
  -Role security -Id SPEC-0002 -Phase SECURITY_ASSURANCE -Risk R3

powershell -NoProfile -ExecutionPolicy Bypass -File tools/security/verify-assurance.ps1 `
  -Target candidate -RequiresHeavy
```

`-RequiresHeavy` was chosen deliberately: without it the wrapper degrades a missing scanner to
`NOT_APPLICABLE`, which would turn an unexecuted mandatory control into a silent pass on an R3
candidate.

### 10.4 Wrapper control results

| Control | Result | Evidence |
|---|---|---|
| `release-verification` | PASS, 268s, exit=0 | `verify-slice: 170s exit=0`, `verify-e2e: PASS`, `verify-release: 268s exit=0` |
| `secrets` (pinned container) | PASS | `INF no leaks found` |
| `backend-tests` (`mvnw -B verify`) | PASS | `[INFO] BUILD SUCCESS` |
| `dynamic-security` (ZAP, 2 passes) | **FAIL (exit=1) — did not execute** | Stack never became healthy; no ZAP artifact produced. See §6. |

Raw log: `.claude/runtime/security-candidate/verify-assurance.log` (686 KB)
Control summary: `.claude/runtime/security-candidate/controls.txt`
Teardown log: `.claude/runtime/security-candidate/docker-down.log`

### 10.5 A note on the wrapper's process exit code

The background invocation of `verify-assurance.ps1` was reported to the calling harness with
**exit code 0**, while `controls.txt` recorded `dynamic-security=FAIL (exit=1)` and the log
contained the terminating error:

```
No ...\tools\security\verify-assurance.ps1:24 caractere:21
+     if($code -ne 0){throw "exit=$code"}
    + FullyQualifiedErrorId : exit=1
```

The exit code and the actual control outcome disagreed. This report is based on the recorded
control results and the emitted artifacts, not on the process exit status. Anyone automating a
gate on this wrapper should read `controls.txt` and assert the expected artifacts exist, because
a green exit code here would have concealed an entire unexecuted control family.

### 10.6 Controls not executed, and why

| Control | Status | Reason |
|---|---|---|
| **Family 6 — DAST (ZAP baseline + OpenAPI API scan)** | **NOT EXECUTED** | The `compose.security.yaml` backend healthcheck invokes `curl`, absent from the runtime image, so the stack cannot become healthy and ZAP never starts. Mandatory for an R3 candidate ⇒ `BLOCKED`. See §6 and SEC-F-16. |
| CodeQL SAST on the exact candidate SHA | Not re-executed locally | No repository wrapper runs CodeQL locally; it is a GitHub-hosted action. Executed on byte-identical product code at `d354ae9`. |
| Trivy deps/licenses/config/images on the exact candidate SHA | Not re-executed locally | `trivy-scan.sh` is POSIX-only and this host has no working `.sh` path; executed in CI on byte-identical product code at `d354ae9`. |
| Dynamic reproduction of pool exhaustion (SEC-F-14) | Assessed statically, not reproduced | The approved DAST stack publishes no host ports and is torn down by `--abort-on-container-exit`. See §8.1. |
| Manual penetration testing | Not performed | Not required by the threat model for an internal pilot; the automated profile in `compose.security.yaml` is the approved scope. |
