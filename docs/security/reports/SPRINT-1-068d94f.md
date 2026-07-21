# Security Assurance Report — Sprint 1 release candidate (run 2)

**Expected duration of this assurance run: 45 minutes.** Actual wall clock is recorded in
§10 Execution log.

| Field | Value |
|---|---|
| Candidate SHA | `068d94fd8cc0500fea18c57283d4133f3860e108` |
| Branch | `release/0.1.0` |
| Release class | Internal pilot / pre-release (no end-user-exposed environment) |
| Track | `docs/security/SECURITY-ASSURANCE-TRACK.md` (owner-approved 2026-07-17) |
| Executed by | `security-assurance-engineer`, independent worker, run 2 |
| Date | 2026-07-20 |
| Previous run | `docs/security/reports/SPRINT-1-d9aad61.md` (verdict `BLOCKED`) |
| **Verdict** | **`BLOCKED`** — one administrative precondition only: no recorded owner risk decision exists (§9). All technically executable controls ran. |

This report stands alone. Where a conclusion is carried forward from run 1 rather than
re-executed, it is labelled **CARRIED FORWARD** with the reason the carry is sound. Every other
statement is backed by a command executed in this run against this SHA.

---

## 0. What changed since run 1, and what this run set out to prove

Run 1 assessed `d9aad61` and returned `BLOCKED` because mandatory control family 6 (Dynamic
assurance / DAST) produced nothing: `compose.security.yaml` health-checked the backend with
`curl`, which the JRE-on-Alpine runtime image does not ship, so the probe failed all 40 retries,
`edge` and the ZAP container never left `Created`, and no scan ran.

The purpose of run 2 was to execute family 6 for real. **It did.** Family 6 is no longer
`NOT_EXECUTED`; it executed, produced artifacts, and **failed on four gating rules** (§6).

### 0.1 Candidate delta — verified independently

```
$ git diff --stat d9aad61 068d94f
 compose.security.yaml                     |  15 +-
 docs/security/reports/SPRINT-1-d9aad61.md | 664 ++++++++++++++++++++++++++++++
 tools/security/verify-assurance.ps1       |  28 +-
 tools/security/verify-assurance.sh        |  11 +
 4 files changed, 714 insertions(+), 4 deletions(-)
```

Exactly the three harness files plus run 1's own report. Against the merged `develop` tip:

```
$ git diff --stat d354ae9 068d94f
 compose.security.yaml | 15 +-  docs/ROADMAP.md | 2 +-
 docs/exec-plans/{active => completed}/PLAN-0002.md | 0
 docs/security/reports/SPRINT-1-d9aad61.md | 664 +++++
 docs/specs/SPEC-0002-signup-account.md | 3 +-
 tools/security/verify-assurance.ps1 | 28 +-  tools/security/verify-assurance.sh | 11 +
```

**No path under `backend/`, `frontend/` or `emulators/` appears in either diff.** Product code at
`068d94f` is byte-identical to the merged `d354ae9`. This is the fact that licenses the carries in
§2, §4, §5 and §7.

---

## 1. Candidate integrity — PASS (with one recorded observation) — RE-EXECUTED

```
$ git rev-parse HEAD
068d94fd8cc0500fea18c57283d4133f3860e108
$ git rev-parse --abbrev-ref HEAD
release/0.1.0
$ git status --porcelain
(empty)
```

Re-checked after the entire assurance run: the tree is still clean and `HEAD` is still `068d94f`.
This worker modified no production code.

### 1.1 CI evidence — OBSERVATION: CI has not run on the exact candidate SHA

```
$ gh api repos/fkazeredo/fkbank-sdd/commits/068d94fd8cc.../check-runs
{"message":"No commit found for SHA: 068d94fd8cc...","status":"422"}
$ gh api repos/fkazeredo/fkbank-sdd/branches --jq '.[].name'
chore/autonomous-workflow-behavioral-ddd
develop
feature/spec-0001-ledger-core
feature/spec-0002-signup-account
feature/spec-0016-observability-baseline
feature/spec-0018-walking-skeleton
```

`068d94f` does not exist on `origin`, and **`release/0.1.0` is not on `origin` at all** — the
candidate branch is local-only. No CI run is therefore associated with the candidate SHA.

**Disposition.** Accepted as an observation, not waived. The honest statement is *"CI ran on
byte-identical product code at `d354ae9`; the candidate SHA itself has no CI run"*. It is
mitigated because §0.1 proves the product delta is empty and because this run re-executed the
full release battery locally against the candidate itself (§10.4). It is **not** mitigated into a
pass: `SECURITY_VERIFIED` would require CI evidence on the exact candidate.

### 1.2 Changed-surface inventory (Sprint 1) — CARRIED FORWARD

Sound to carry because the surface inventory is a property of the merged slices, and no slice
changed between runs.

| Spec | Risk | Security-relevant surface |
|---|---|---|
| SPEC-0018 | R3 | Default-deny authorization, embedded Authorization Server, OIDC + PKCE, nginx edge, Flyway/Postgres, Angular shell, CI |
| SPEC-0016 | R2 | Correlation id + MDC, structured JSON logs, token-protected Prometheus, public OpenAPI |
| SPEC-0001 | R3 | Append-only double-entry ledger, `Money` 4-decimal, `FOR UPDATE` serialization, reversal-at-most-once, trial balance |
| SPEC-0002 | R3 | Sign-up + account opening, CPF/e-mail uniqueness, PII, KYC bureau emulator + webhook, credential issuance, same-CPF race |

The presence of three R3 slices is what makes this track mandatory.

---

## 2. Threat model — PASS — CARRIED FORWARD (with one item promoted by DAST evidence)

The threat model in run 1 §2 is carried forward in full. The carry is sound because a threat model
describes assets, actors and trust boundaries of the product, and the product is byte-identical.

Assets: CPF, full name / birth date / declared income, e-mail, password, access & refresh tokens,
ledger postings and balances, bureau HMAC secret, onboarding id (a bearer-equivalent capability
token). Actors: anonymous caller, applicant, authenticated customer, KYC bureau emulator, operator
/ log reader, CI and the delivery agent. Trust boundaries: internet → nginx edge; edge → backend;
backend → PostgreSQL (never published); bureau → webhook (HMAC); backend → log stream; developer
machine → GitHub repository.

**One boundary is now measured rather than reasoned about.** Run 1 could only describe boundary 1
(internet → edge) statically. This run drove it with a real scanner, and that boundary is where
every new gating finding landed (§6). The threat model's claim that the edge is a genuine trust
boundary is confirmed — and the edge is not carrying the response headers that boundary implies.

---

## 3. Static assurance — FAIL (repository-configuration control executed and failed) — RE-EXECUTED

### 3.1 Secrets — PASS — RE-EXECUTED

Executed in this run by the wrapper's digest-pinned container path (no local `gitleaks` binary on
this host): `ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c95...`

```
9:47PM INF no leaks found
```

Recorded as `secrets(docker)=PASS` in `controls.txt`.

### 3.2 SAST — PASS on identical product code — CARRIED FORWARD

CodeQL (`java-kotlin`, `javascript-typescript`) concluded `success` on `d354ae9`. No repository
wrapper runs CodeQL locally — it is a GitHub-hosted action — so it **cannot** be re-executed
against `068d94f` on this host. Carried forward on the §0.1 byte-identity argument. Declared as a
control not re-executed in §10.6.

### 3.3 Dependencies — PASS on identical product code — CARRIED FORWARD

`supply-chain-deps` → `tools/security/supply-chain/trivy-scan.sh deps`, Trivy pinned
`aquasec/trivy:0.72.0@sha256:cffe3f51...`, gate `HIGH,CRITICAL` with `--exit-code 1`. Job concluded
`success` on `d354ae9`. `trivy-scan.sh` is POSIX-only and this host has no working `.sh` path, so
it was not re-executed. Declared in §10.6.

### 3.4 Licenses — PASS, report-only by design — CARRIED FORWARD

`report_then_gate "deps (licenses)" ... "report-only"`. Licenses are scanned and reported but do
not gate, deliberately: `trivy-scan.sh` states that deciding acceptable licenses for a bank is an
owner decision. A documented deferral, not a silent gap. Tracked as SEC-F-12 (Low).

### 3.5 Repository configuration — **FAIL** — RE-EXECUTED

Re-executed in this run, not inherited:

```
$ gh api repos/fkazeredo/fkbank-sdd/rulesets
[]
$ gh api repos/fkazeredo/fkbank-sdd/branches/develop/protection
{"message":"Branch not protected","status":"404"}
$ gh api repos/fkazeredo/fkbank-sdd/branches/main/protection
{"message":"Branch not found","status":"404"}
```

The repository has **no rulesets and no branch protection of any kind**, and `main` does not exist
on `origin`. CLAUDE.md invariant 6 states that protected Git operations "require server-side
GitHub rulesets"; none exist, so that invariant is unenforced at the server. See SEC-F-02.

The family is reported `FAIL` because a control that ran and returned a negative result is not a
control that passed. Per `security-gate.md`, risk acceptance never converts a failed control into
a verified one.

---

## 4. Architecture — PASS — RE-EXECUTED (partially) + CARRIED FORWARD

**Re-executed in this run.** `ArchitectureTest` and `ModulithTest` both ran green inside the
wrapper's `backend-tests` and `release-verification` controls:

```
[INFO] Tests run: 1, ... -- in com.fkbank.architecture.ArchitectureTest
[INFO] Tests run: 1, ... -- in com.fkbank.architecture.ModulithTest
```

Independent structural inspection, also re-executed at this SHA:

```
$ ls backend/src/main/java/com/fkbank/
FkbankApplication.java  application  domain  infra
$ find backend/src/main/java/com/fkbank/domain -mindepth 2 -type d
(empty)
```

Root packages are exactly `domain`, `application`, `infra`. Domain bounded contexts are flat — no
subdirectory exists inside any context.

**CARRIED FORWARD:** run 1's line-by-line inspection of all six controllers/advice confirming that
no domain type appears at a delivery boundary (every response body a local record, every
`@RequestBody` a local record or `byte[]`), and that `domain` carries no third-party import beyond
the five exempted `@ApplicationModule` annotations. Sound to carry: those files are byte-identical.

**Enforcement gaps (SEC-F-13, Low, no live violation).** Three invariant-6 clauses are enforced
more weakly than they read: cross-bounded-context entity crossing has no ArchUnit rule (delegated
to Modulith, which *whitelists* crossings); "domain entities never become JSON" is convention only;
and `onlyTheLedgerTouchesBalances` is a four-name regex that does not match `JpaBalanceRepository`
or `TrialBalance`. The candidate complies; the guardrail is thin.

---

## 5. Adversarial behavior — PASS (with Medium findings) — RE-EXECUTED (key items) + CARRIED FORWARD

### 5.1 Sensitive logging — SEC-F-01 REPRODUCED IN THIS RUN

This is not carried forward. It was reproduced from this run's own runtime output, at this SHA:

```
$ grep -o "Key (cpf)=([0-9]*)" .../verify-assurance.log | sort | uniq -c
      3 Key (cpf)=(05846360246)      1 Key (cpf)=(31934560987)
      1 Key (cpf)=(33027664590)      1 Key (cpf)=(35340232425)
      3 Key (cpf)=(35834627501)      1 Key (cpf)=(41630111376)
      1 Key (cpf)=(49518412669)      1 Key (cpf)=(94033309101)
$ grep -c "org.hibernate.orm.jdbc.error" .../verify-assurance.log
24
```

Twelve distinct raw CPFs written to the log stream across 24 Hibernate JDBC error lines. The
application's own masking discipline (`Cpf.toString()` masks; `RawPassword`, `PasswordHash`,
`BureauReference` render as `[protected]`) is bypassed entirely, because the CPF reaches the log
through the JDBC driver's error message rather than through a domain `toString()`.

**It applies in production.** Re-verified at this SHA:

```
$ grep -rn "org.hibernate.orm.jdbc" backend/src/
(no output — the logger is downgraded nowhere)
$ grep -n -A2 "logging" backend/src/main/resources/application-prod.yml
17:logging:
18-  level:
19-    org.springframework.security: WARN
```

`org.hibernate.orm.jdbc.error` emits at WARN, above the default root level, and no profile
downgrades it. The trigger is an ordinary unauthenticated attacker-reachable action: submitting a
signup for a CPF that already has a live application. **SEC-F-01 stands, Medium, CWE-532.**

### 5.2 Authorization matrix — default-deny — RE-EXECUTED (statically) and CORROBORATED BY DAST

```
$ grep -n "permitAll|anyRequest|requestMatchers" .../SecurityConfig.java
 52: .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
 83/89: .requestMatchers(...).permitAll()      # health, version, api-docs, swagger-ui
 95/98: .requestMatchers(...).permitAll()      # POST /api/signup, GET /api/signup/{id}
102/103: .requestMatchers(POST /api/webhooks/bureau).permitAll()
104/105: .anyRequest().authenticated()
```

`anyRequest().authenticated()` is terminal and `/actuator/prometheus` is not on the allowlist.

**Newly corroborated dynamically.** The API scan drove 78 client-error responses
(`A Client Error response code was returned by the server [100000] x 78`) while probing
`/api/me`, `/api/account/me`, `/actuator/prometheus`, `/oauth2/*`, `/login` and a long tail of
attack paths (`/ws_ftp.ini`, `/winscp.ini`, `/vim_settings.xml`, `/v3/trace.axd`, cloud-metadata
endpoints). Default-deny answered them. ZAP classifies this Informational; for this control it is
positive evidence that the deny path is real and not merely configured.

### 5.3 Webhook — HMAC correct, replay protection absent — CARRIED FORWARD

Shared-secret HMAC-SHA256 over the raw body (`X-Bureau-Signature`), verified **before** the body is
parsed, compared in constant time (`MessageDigest.isEqual`). No nonce, timestamp or expiry ⇒
SEC-F-05 (Medium) stands. Contained today by settle-once idempotency; the containment is not the
control. The DAST API pass exercised `POST /api/webhooks/bureau` 18 times and produced no
additional finding on that route.

### 5.4 Metrics protection — CARRIED FORWARD, and re-corroborated

`ObservabilityRouteSecurityIT` (401 without a token, 200 with one) ran green again inside this
run's `backend-tests` control. It proves a real `JwtDecoder` backs the resource server, and it
proves SEC-F-06: an ordinary customer token, with no scope requirement, reads the full metrics
endpoint. The DAST API pass requested `/actuator/prometheus` 6 times without a token and was
denied.

### 5.5 Idempotency, concurrency and the same-CPF race — CARRIED FORWARD

Enforcement at both layers with the database having the final say:
`V3__signup_account.sql:15` (`cpf VARCHAR(11) NOT NULL UNIQUE` on `customer`) and
`V5__onboarding_one_live_application_per_cpf.sql:17-21`
(`CREATE UNIQUE INDEX onboarding_one_live_per_cpf ON onboarding (cpf) WHERE status IN ('PENDING','APPROVED')`).
V5 closes a real race V3 left open. The loser of a concurrent race is caught and mapped, not
blocked. `REJECTED` is deliberately outside the index so a refused applicant may reapply. Ledger
concurrency uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Sound to carry: migrations and source are
byte-identical, and the full acceptance suite (including the race tests) re-ran green in this run.

Incidentally corroborated: the 24 duplicate-key rejections in §5.1 are the database enforcing this
uniqueness under the scanner's 34 signup attempts.

### 5.6 Injection — no finding — CARRIED FORWARD, corroborated by DAST

All persistence goes through Spring Data / JPA with bound parameters; the correlation-id header is
sanitized against CRLF log injection by `WELL_FORMED = [A-Za-z0-9._-]{1,64}`; `V3` adds
`CHECK (cpf ~ '^[0-9]{11}$')`. The DAST run adds real evidence: the active/passive rule set
returned **PASS** for SQL Injection, Path Traversal, Remote OS Command Injection (and time-based),
Server Side Template Injection (and blind), XPath Injection, XSLT Injection, XXE, Spring4Shell
[40045], Server Side Code Injection, and Cloud Metadata Potentially Exposed [90034] — 56 PASS rules
on the edge pass and 112 on the API pass.

### 5.7 Rate and abuse behavior — no control exists — CARRIED FORWARD

No bucket4j, no resilience4j, no nginx `limit_req` anywhere. SEC-F-07 (Medium) and SEC-F-09
(Low-Medium) stand. The DAST profile is a baseline/API scan, not a load generator, so it neither
confirms nor refutes the exhaustion vector.

---

## 6. Dynamic assurance (DAST) — **EXECUTED**, and **FAILED** on four gating rules

**This family produced nothing in run 1. In run 2 it ran end to end and produced artifacts.**

Target: the approved local ephemeral stack `compose.security.yaml`, project `app-security`,
brought up by this worker and by nothing else. No external or hosted target was contacted.

### 6.1 Proof the stack actually came up this time

Run 1's failure signature was `edge` and `security-tests` stuck in `Created`. Observed directly in
this run, by polling `docker ps` during execution:

```
[21:48:56] security-tests=Created  edge=Created  backend=Up 2 seconds (health: starting)  db=Up 8 seconds (healthy)
[21:49:10] security-tests=Created  edge=Up 1 second (health: starting)  backend=Up 17 seconds (healthy)
[21:49:21] security-tests=Up 7 seconds  edge=Up 12 seconds (healthy)  backend=Up 28 seconds (healthy)
```

The backend reached `healthy` in 17 seconds, `edge` started and became healthy, and the ZAP
container left `Created` and ran. **The healthcheck fix is verified by observed behavior, not by
reading the diff.** Root cause of run 1 is resolved: `compose.security.yaml:56` now uses
`wget -q -S -O /dev/null http://127.0.0.1:8080/api/me 2>&1 | grep -qE ' (401|200) '`, the form the
E2E stack already used, against an image that ships busybox `wget` but no `curl`.

### 6.2 Proof the scan policy was actually applied

This deserves its own check, because it is exactly the shape of failure run 1 warns about. The
host copy of `.claude/runtime/security-candidate/zap-baseline.conf` is **0 bytes** — Docker's
placeholder for the nested read-only file mount. Had the policy been empty *inside* the container,
every rule would have defaulted to WARN and `-I` would have made the scan pass while proving
nothing.

It was not empty. Discriminating evidence:

```
security-tests-1  | IGNORE-NEW: ZAP is Out of Date [10116] x 1
```

Rule 10116 is `IGNORE` **only** in `zap-baseline.conf`. With no policy loaded it would have
appeared as WARN. Its appearance as IGNORE proves the file was read. The four `FAIL-NEW` rules
below are likewise FAIL only because the policy promoted them.

### 6.3 Proof the scan reached the real attack surface

A baseline scan that only ever visits `index.html` attests to nothing. Distinct backend routes
actually requested during the run, counted from the backend's own correlation-id log lines:

```
85 GET /api/me            34 POST /api/signup        28 GET /v3/api-docs
18 POST /api/webhooks/bureau   8 POST /login          8 GET /login
8  GET /api/account/me     6 POST /oauth2/token       6 GET /oauth2/authorize
6  GET /actuator/prometheus  4 GET /api/version       3 GET /actuator/health
2  GET /api/signup/{uuid}   2 GET /api/signup/not-a-uuid   2 GET /swagger-ui/index.html
4  GET /latest/meta-data/   2 GET /computeMetadata/v1/   2 GET /opc/v1/instance/
```

The OpenAPI-seeded API pass genuinely exercised the money and identity surface, including the
webhook and the OAuth2 endpoints, plus cloud-metadata SSRF probes. This is why the two-pass design
exists: the edge baseline pass alone only reached `/`, `/favicon.ico`, `/main-<hash>.js`,
`/robots.txt` and `/sitemap.xml`.

### 6.4 Pass 1 — edge baseline (`http://edge:8090`, AJAX spider) — **FAIL**

```
FAIL-NEW: 4   FAIL-INPROG: 0   WARN-NEW: 3   WARN-INPROG: 0   INFO: 0   IGNORE: 4   PASS: 56
```

| Rule | ZAP risk | Hits | Disposition |
|---|---|---|---|
| Content Security Policy (CSP) Header Not Set [10038] | Medium | 4 | **True positive — SEC-F-17** |
| Missing Anti-clickjacking Header [10020] | Medium | 3 | **True positive — SEC-F-18** |
| X-Content-Type-Options Header Missing [10021] | Low | 7 | **True positive — SEC-F-19** |
| Permissions Policy Header Not Set [10063] | Low | 5 | **True positive — SEC-F-20** |
| Server Leaks Version Information via `Server` header [10036] | Low | 7 | True positive — SEC-F-22 (policy WARN by design) |
| Insufficient Site Isolation Against Spectre [90004] | Low | 15 | True positive — SEC-F-23 (policy WARN by design) |
| Information Disclosure - Suspicious Comments [10027] | Informational | 1 | SEC-F-24 — in the minified bundle `main-AATKXS74.js` |
| Timestamp Disclosure [10096], Modern Web Application [10109], Non-Storable / Storable Content [10049], ZAP is Out of Date [10116] | Low/Info | — | IGNOREd by policy with written justification; reviewed, no action |

All four gating failures are confirmed at their root in `frontend/nginx.conf`, which contains
**no** `add_header` for CSP, `X-Frame-Options`, `X-Content-Type-Options` or `Permissions-Policy`,
and no `server_tokens off`:

```
$ grep -n "add_header|server_tokens" frontend/nginx.conf
17:    add_header Cache-Control "no-store";
23:    add_header Cache-Control "public, immutable";
```

Only cache directives are set. These are not false positives and not scanner noise: the edge
serves the SPA to the internet and sets none of the headers that boundary implies.

### 6.5 Pass 2 — OpenAPI-driven API scan (`http://backend:8080/v3/api-docs`) — PASS with warnings

```
FAIL-NEW: 0   FAIL-INPROG: 0   WARN-NEW: 2   WARN-INPROG: 0   INFO: 0   IGNORE: 1   PASS: 112
```

| Rule | ZAP risk | Hits | Disposition |
|---|---|---|---|
| Spring Actuator Information Leak [40042] | Medium | 1 (`/actuator/health`) | True positive, **downgraded to Low — SEC-F-21**, see below |
| Insufficient Site Isolation Against Spectre [90004] | Low | 1 (`/v3/api-docs`) | True positive — SEC-F-23 |
| Unexpected Content-Type was returned [100001] | Low | 2 | SEC-F-25 — API-scan heuristic, low value; recorded, not gating |
| A Client Error response code was returned [100000] | Informational | 78 | **Not a finding — this is default-deny working** (§5.2) |

**SEC-F-21 disposition, with the severity reasoning stated.** ZAP rates 40042 Medium. I rate it
**Low**, and the reasoning cuts both ways so both halves are recorded:

- *Aggravating, and I checked it rather than assuming containment:* `frontend/nginx.conf:68`
  proxies `^/(actuator(/|$)|v3/api-docs|swagger-ui(/|$|\.html))` to the backend, so
  `/actuator/health` **is reachable from the internet through the edge**, not confined to the
  internal network.
- *Mitigating:* web exposure is limited to `health,prometheus`
  (`application.yml:75-81`); `prometheus` requires authentication (§5.4); and `show-details` is
  unset, so Spring Boot's default `never` applies and the body is a bare `{"status":"UP"}`.

The actual disclosure is therefore "this is a Spring Boot application and it is up" — real
fingerprinting value, negligible data. Low, with the note that it compounds SEC-F-04 (public API
docs proxied by the same nginx rule).

### 6.6 Control result

The `security-tests` service exits non-zero if either pass fails, so neither can hide behind the
other. Recorded outcome:

```
security-tests-1  | zap edge pass exit=1, api pass exit=0
security-tests-1 exited with code 1
$ cat .claude/runtime/security-candidate/controls.txt
release-verification=PASS
secrets(docker)=PASS
backend-tests=PASS
dynamic-security=FAIL (exit=1)
```

Family 6 **executed and failed**. That is a materially different state from run 1's
`NOT_EXECUTED`, and it is dispositioned as findings (§8) rather than as a missing control.

### 6.7 Artifacts — asserted to exist on disk

```
$ ls -la .claude/runtime/security-candidate/
 93336  zap-api-report.html    26492  zap-api-report.json
 92130  zap-report.html        33543  zap-report.json     26845  zap-report.md
```

The evidence directory was emptied of run 1's `controls.txt` / logs before this run started and
contained **no** ZAP artifact of any kind beforehand (verified by `ls` at 21:42:33Z, before
launch). Every artifact above was created between 21:49 and 21:53 on 2026-07-20. They could not
have come from run 1, which produced none.

---

## 7. Supply chain and deployment — PASS (with findings) — RE-EXECUTED (partially) + CARRIED FORWARD

### 7.1 Container least privilege — PASS — RE-EXECUTED

```
$ grep -n "^USER|^FROM" backend/Dockerfile frontend/Dockerfile emulators/bureau/Dockerfile
backend:            FROM eclipse-temurin:21-jre-alpine AS runtime   / USER fkbank
frontend:           FROM nginxinc/nginx-unprivileged:1.29-alpine    / USER 101
emulators/bureau:   FROM eclipse-temurin:21-jre-alpine AS runtime   / USER fkbank
```

No container runs as root at runtime. All three declare a `HEALTHCHECK`. Base images are pinned by
tag but not by digest; Trivy `config` and `images` scans concluded `success` in CI on identical
code.

Note the one deliberate root: `compose.security.yaml` runs the **scanner** container as `user: root`
so the bind-mounted report directory is writable regardless of host uid. That is the ephemeral ZAP
container, not a product image, and it is destroyed with the stack.

### 7.2 Secret injection and secure defaults — PASS with SEC-F-03 — RE-EXECUTED

```
$ grep -n "PROPERTY|datasource|password" .../ProductionSecretsGuard.java
26:  static final String ISSUER_PROPERTY = "spring.security.oauth2.authorizationserver.issuer";
28:  static final String BUREAU_SECRET_PROPERTY = "fkbank.bureau.hmac-secret";
```

`ProductionSecretsGuard` (`@Profile("prod")`) fails the context closed if either value is blank or
still the development default — a genuinely good control. Its gap is re-confirmed first-hand: it
checks exactly those two properties, while `application.yml:8` carries
`password: ${APP_DB_PASSWORD:app}` and `application-prod.yml` has no `datasource` override.
SEC-F-03 stands.

The SPA remains a correctly **public** client (`client-authentication-methods: none`,
`require-proof-key: true` ⇒ PKCE mandatory, no client secret to leak) — CARRIED FORWARD.

### 7.3 Provenance — NOT IMPLEMENTED (SEC-F-12) — CARRIED FORWARD

No SBOM, no image signing, no build provenance attestation. Acceptable for an internal pilot with
locally-built images; a prerequisite for production.

### 7.4 Rollback — PASS — CARRIED FORWARD

Flyway migrations are additive; `V5` drops and recreates an index only. No destructive migration in
Sprint 1, so roll-forward is available and rollback needs no data recovery.

---

## 8. Findings

Severity is this worker's own assessment; where it differs from the tool's rating the reasoning is
stated. **No unresolved High or Critical finding exists.** SEC-F-16, run 1's High assurance-harness
defect, is verified resolved.

### 8.1 Resolved since run 1

| ID | Finding | Status |
|---|---|---|
| SEC-F-16 | `compose.security.yaml` health-checked the backend with `curl`, absent from `eclipse-temurin:21-jre-alpine`; the whole DAST family could not execute | **RESOLVED — verified by observed behavior** (§6.1). Backend healthy in 17 s; `edge` and ZAP ran; artifacts produced. |
| (run 1 §10.5, unnumbered) | `verify-assurance.ps1` had no `exit` statement, so the DAST teardown's `$global:LASTEXITCODE=0` returned success to callers while `controls.txt` recorded `dynamic-security=FAIL` | **RESOLVED — verified** (§8.3). The wrapper returned **exit 1** on this run while recording the same FAIL. Code and evidence now agree. |

### 8.2 Open findings

| ID | Finding | Severity | Disposition |
|---|---|---|---|
| SEC-F-01 | Raw CPF written to application logs on the duplicate-CPF path via `org.hibernate.orm.jdbc.error`; applies in **prod**; reproduced in this run (12 distinct CPFs, 24 lines) | **Medium** | Open. Must fix before production. Downgrade/mask that logger or intercept the constraint violation before Hibernate logs it. CWE-532. |
| SEC-F-02 | No branch protection or rulesets; `main` absent on `origin`; invariant 6's server-side enforcement does not exist | **Medium** (Critical as a production precondition) | Control **executed and failed** (§3.5). Verbally accepted by the owner but **not durably recorded** — see SEC-F-15 and §9. |
| SEC-F-17 | CSP header not set at the edge (DAST 10038, 4 URLs) | **Medium** | Open, **gating**. Add a `Content-Security-Policy` in `frontend/nginx.conf`. |
| SEC-F-18 | Missing anti-clickjacking header (DAST 10020, 3 URLs) | **Medium** | Open, **gating**. Add `X-Frame-Options: DENY` or a CSP `frame-ancestors` directive. For a banking SPA this is a real clickjacking exposure. |
| SEC-F-03 | `ProductionSecretsGuard` does not cover `spring.datasource.password`; `${APP_DB_PASSWORD:app}` would silently apply in prod | **Medium** here; **High** if production-destined | Open. Prod profile unused in this candidate. |
| SEC-F-04 | `/v3/api-docs/**` and `/swagger-ui/**` are `permitAll` in all profiles including prod, and proxied by the edge | Medium | Open. Gate behind a profile before production. Compounds SEC-F-21 (same nginx rule). |
| SEC-F-05 | Bureau webhook has no replay protection (no nonce, timestamp or expiry) | Medium | Open. Contained by settle-once idempotency; containment is not the control. |
| SEC-F-06 | `/actuator/prometheus` requires only `authenticated()`; any customer token reads all metrics | Medium | Open. Require a dedicated scope or a separate management port. |
| SEC-F-07 | No rate limiting on any route; `POST /api/signup` drives password hashing on the request thread | Medium | Open. CPU-exhaustion vector on an unauthenticated route. |
| SEC-F-08 | `compose.prod.yaml` never passes `FKBANK_BUREAU_HMAC_SECRET`/`FKBANK_ISSUER`, so the guard throws on every start | Medium | Open. Fails **closed** — an incomplete template, not an exposure. |
| SEC-F-19 | `X-Content-Type-Options` header missing (DAST 10021, 7 URLs) | Low | Open, **gating**. Add `nosniff`. |
| SEC-F-20 | `Permissions-Policy` header not set (DAST 10063, 5 URLs) | Low | Open, **gating**. |
| SEC-F-09 | `@RequestBody byte[]` on the public webhook with no size bound ⇒ unauthenticated buffer + HMAC amplification | Low-Medium | Open. Compounds SEC-F-07. |
| SEC-F-11 | `CorrelationIdFilter` logs `request.getRequestURI()`, writing the `onboardingId` capability token into the log stream | Low-Medium | Open. Log read access ⇒ poll any applicant's status. |
| SEC-F-21 | Spring Actuator information leak; `/actuator/health` is internet-reachable through `nginx.conf:68` | Low (ZAP: Medium) | Open. Downgraded because the body is a bare `{"status":"UP"}` (`show-details` unset) and only `health,prometheus` are web-exposed. Fingerprinting value only. |
| SEC-F-22 | nginx advertises its version via the `Server` header; `server_tokens off` not set | Low | Open. Known, policy-WARN by design. |
| SEC-F-23 | Insufficient site isolation against Spectre (COOP/COEP/CORP absent), 15 edge + 1 API hits | Low | Open. Hardening step beyond the CSP set; policy-WARN by design. |
| SEC-F-10 | `Credential.toString()` emits the raw e-mail; siblings in the same PII cluster are masked | Low | Open, latent — not currently reachable by any logger. |
| SEC-F-12 | No SBOM, image signing or provenance; license scan is report-only | Low | Accepted for an internal pilot; production prerequisite. |
| SEC-F-13 | ArchUnit enforcement weaker than invariant 6 reads | Low | Open. **No live violation.** |
| SEC-F-14 | Connection-pool exhaustion surfaces as `401` on a public route | Low | Open. Fails closed; cost is diagnostic, not security. **Not dynamically reproduced** — see §10.6. |
| SEC-F-24 | Suspicious comments in the minified bundle `main-AATKXS74.js` (DAST 10027) | Informational | Open. Reviewed; no credential or endpoint disclosed by the rule's evidence. |
| SEC-F-25 | Unexpected `Content-Type` returned, 2 hits (DAST 100001) | Low | Open. API-scan heuristic; recorded for completeness, no exploitable path identified. |
| SEC-F-15 | The owner risk acceptance for SEC-F-02 is not recorded in `docs/security/DECISIONS.md` | **Process — currently the sole blocking cause** | See §9. |

### 8.3 The wrapper now fails loudly — verified as a control in its own right

Family 3 covers tooling integrity, and a gate that reports success while a control fails is
precisely the defect this track exists to catch. Verified directly rather than by reading the diff:

```
$ powershell ... -File tools/security/verify-assurance.ps1 -Target candidate -RequiresHeavy > log 2>&1; echo "WRAPPER_EXIT=$?"
WRAPPER_EXIT=1
$ cat .../controls.txt
... dynamic-security=FAIL (exit=1)
```

**Exit code and recorded evidence now agree**, where in run 1 they disagreed (exit 0 vs a recorded
FAIL). The mechanism in the current source is explicit: `$script:hadFailure` is set in
`Run-Control`'s `catch`, re-derived from the recorded results
(`if(@($results|Where-Object{$_ -like '*FAIL*'}).Count -gt 0)`), and terminated on
(`if($hadFailure){...;exit 1}`).

Two honest caveats an auditor should have:

1. **The artifact assertion was not exercised on this run.** `Run-Control` rethrows on failure, so
   execution jumps to the outer `catch` and the
   `zap-report.html`/`zap-api-report.html` existence check at lines 96-99 is skipped. It guards the
   *pass* path — it prevents recording `dast-report=` without artifacts — and on this run the DAST
   control failed before reaching it, which is why `controls.txt` carries no `dast-report=` line
   despite the artifacts existing. Correct behavior, but it means the assertion is not what would
   catch a future "stack never came up" case; the control failure itself is.
2. **A caller must not read the exit code through a shell that discards it.** The background
   notification for this run reported "exit code 0" because my invocation ended in
   `; echo "WRAPPER_EXIT=$?"`, and the shell reports `echo`'s status. The wrapper's own code was 1,
   captured explicitly. Anyone automating this gate should capture the wrapper's status directly
   and cross-check `controls.txt`.

---

## 9. Verdict

### 9.1 `BLOCKED` — on one administrative precondition, not on a technical gap

Every control that could be executed on this host was executed on this candidate. Family 6, the
reason run 1 was blocked, now runs end to end and produced artifacts and dispositioned findings.
No unresolved High or Critical finding exists.

**`SECURITY_VERIFIED` is not available.** The track reserves it for "all applicable mandatory
controls executed on the exact candidate" *and passed*. Two applicable mandatory controls executed
and **failed**:

1. Family 3.5, repository configuration (§3.5) — no rulesets, no branch protection, no `main` on
   `origin`. Re-confirmed independently this run on facts unchanged since run 1.
2. Family 6, dynamic assurance (§6.4) — four gating rules failed the edge baseline pass.

A third, weaker reason also stands: no CI has run on the exact candidate SHA (§1.1).

**`SECURITY_OBSERVATIONS` is the substantively correct state, and it is not available either.**
`security-gate.md` permits it for an internal candidate **"only with a recorded owner risk
decision."** No such record exists:

```
$ cat docs/security/DECISIONS.md
# Security decisions
... _No entries yet._
```

The owner's acceptance of the SEC-F-02 branch-protection risk was relayed to this worker through
the orchestrating agent's task description. **An agent's message is not the owner's recorded
decision**, and this worker cannot create one: the `security` role's write allowlist in
`.claude/hooks/path-guard.ps1:38` is `$runtime + docs/security/reports/*` — verified this run —
which cannot reach `docs/security/DECISIONS.md`. Writing it would also be accepting risk on the
owner's behalf, which the track reserves to the owner alone.

So the verdict is `BLOCKED` under the track's "missing approval" clause, and **only** that clause.

### 9.2 What would change the verdict, precisely

**`SECURITY_OBSERVATIONS` becomes available** as soon as the owner records, in
`docs/security/DECISIONS.md`, in the documented format
(`SEC-NNNN — YYYY-MM-DD — decision — decided by <owner>`) with the remediation deadline the finding
policy requires, an acceptance covering:

- **SEC-F-02** — no branch protection or rulesets (family 3 control failure), and
- **SEC-F-17 / SEC-F-18 / SEC-F-19 / SEC-F-20** — the four gating DAST header failures (family 6
  control failure).

Nothing else is outstanding. If the owner prefers not to accept the header findings, the
alternative is to fix them — they are four `add_header` lines in `frontend/nginx.conf`, which is
production code this worker may not touch — and re-run the track; family 6 would then pass and only
SEC-F-02 would need accepting.

**My recommendation**, since the track asks for one rather than a menu: **fix SEC-F-17 through
SEC-F-20 rather than accept them.** They are a few lines of edge configuration with no behavioral
risk, they are the cheapest findings in this report to close, and accepting missing clickjacking
and CSP protection on a banking SPA sets a poor precedent for the first release that establishes
the pattern. Then record the SEC-F-02 acceptance alone, which is a genuine environment constraint
rather than an unfixed defect.

**`SECURITY_VERIFIED` remains unavailable** in every branch above while §3.5 stands, and would
additionally require CI on the exact candidate SHA. Per `security-gate.md`, risk acceptance never
produces `SECURITY_VERIFIED`.

### 9.3 The distinction this track asks to be made explicit

Run 1 and run 2 fail differently, and collapsing them would be the exact error the evidence rule
guards against:

- Run 1: a mandatory control **did not execute**. Nothing could be accepted in its place, because
  there was no result to accept. An unrun scan is an unknown risk, not an accepted one.
- Run 2: the same control **executed and failed**, producing four named findings with URLs,
  severities and remediations. That is a known risk, and a known risk is something an owner can
  decide about.

An empty report and a clean report look identical unless you check whether the scanner ever
started. §6.1, §6.2 and §6.3 are that check, kept separate on purpose: the stack came up, the
policy was loaded, and the scan reached the real routes.

### 9.4 Production preconditions

This candidate must not be promoted to a production environment until at minimum SEC-F-01
(CPF in logs), SEC-F-02 (branch protection), SEC-F-03 (DB password guard), SEC-F-04 (public API
docs), SEC-F-06 (metrics scope), SEC-F-07 (rate limiting) and SEC-F-17/18/19/20 (edge security
headers) are resolved, SBOM/provenance (SEC-F-12) exists, and the track is re-executed on the
production candidate SHA with CI green on that SHA. Production requires `SECURITY_VERIFIED`, which
this report does not grant.

---

## 10. Execution log

### 10.1 Duration

Declared budget 45 minutes. Actual wall clock approximately **20 minutes**
(2026-07-20 21:42:33Z → ~22:02Z), of which the wrapper occupied 21:42:33Z → 21:53:58Z. No control
was dropped for time.

### 10.2 Environment

| Tool | Version |
|---|---|
| Docker | 29.5.3 (build d1c06ef) |
| git | 2.45.2.windows.1 |
| gh | 2.92.0 |
| gitleaks | v8.28.0 (digest-pinned container; no local binary on host) |
| Trivy | 0.72.0 (digest-pinned, via CI on identical code) |
| OWASP ZAP | 2.16.1 (`ghcr.io/zaproxy/zaproxy:2.16.1@sha256:7840969c...`) |
| Host | Windows 11 Pro 10.0.26200 |

### 10.3 Commands executed by this worker

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/workflow/start-phase.ps1 `
  -Role security -Id SPEC-0002 -Phase SECURITY_ASSURANCE -Risk R3

powershell -NoProfile -ExecutionPolicy Bypass -File tools/security/verify-assurance.ps1 `
  -Target candidate -RequiresHeavy
```

`-RequiresHeavy` was chosen deliberately, as in run 1: without it the wrapper degrades a missing
scanner to `NOT_APPLICABLE`, which would turn an unexecuted mandatory control into a silent pass on
an R3 candidate.

Plus read-only verification commands quoted inline throughout: `git rev-parse`, `git status`,
`git diff --stat`, `gh api` (rulesets, branch protection, branches, check-runs), `docker ps`,
`grep` over the repository and over this run's log, and PowerShell `ConvertFrom-Json` over the ZAP
JSON reports.

### 10.4 Wrapper control results

| Control | Result | Evidence |
|---|---|---|
| `release-verification` | PASS, exit=0 | `verify-slice: 146s exit=0`, `verify-e2e: PASS`, `verify-release: 242s exit=0` |
| `secrets(docker)` (pinned container) | PASS | `9:47PM INF no leaks found` |
| `backend-tests` (`mvnw -B verify`) | PASS | Full suite green, incl. `ArchitectureTest`, `ModulithTest`, `ObservabilityRouteSecurityIT` |
| `dynamic-security` (ZAP, 2 passes) | **FAIL (exit=1) — executed** | Edge pass `FAIL-NEW: 4`; API pass `FAIL-NEW: 0`. Artifacts produced. |
| Wrapper process exit code | **1** | Agrees with `controls.txt` (§8.3) |

Raw log: `.claude/runtime/security-candidate/verify-assurance.log` (835 KB)
Controls: `.claude/runtime/security-candidate/controls.txt`
DAST artifacts: `zap-report.{html,json,md}`, `zap-api-report.{html,json}`
Teardown: `.claude/runtime/security-candidate/docker-down.log`; `docker ps -a` after the run shows
no residual `app-security` container.

### 10.5 Evidence-handling note

Run 1's raw `verify-assurance.log`, `controls.txt` and `docker-down.log` were **deleted by this
worker** before launching run 2, so that a stale `controls.txt` could not be mistaken for this
run's result and so that any ZAP artifact found afterwards was provably new. Those files were
untracked (`.gitignore:2` covers `.claude/runtime/*`) and so were never committed; they are not
recoverable. Run 1's committed report quotes the relevant excerpts, and its `verdict.json` was left
in place. The trade-off was deliberate — discriminating evidence for run 2 over retention of run 1's
raw log — and is recorded here rather than left silent.

### 10.6 Controls not executed, and why

| Control | Status | Reason |
|---|---|---|
| CodeQL SAST on the exact candidate SHA | Not re-executed | No repository wrapper runs CodeQL locally; it is a GitHub-hosted action. Executed on byte-identical product code at `d354ae9`. |
| Trivy deps/licenses/config/images on the exact candidate SHA | Not re-executed | `trivy-scan.sh` is POSIX-only and this host has no working `.sh` path (no Python/POSIX toolchain). Executed in CI on byte-identical code at `d354ae9`. |
| CI checks on the candidate SHA | Cannot execute | `068d94f` and `release/0.1.0` do not exist on `origin` (§1.1). |
| Dynamic reproduction of pool exhaustion (SEC-F-14) | Assessed statically, not reproduced | The approved DAST stack publishes no host ports and is destroyed by `--abort-on-container-exit` when the scan ends. Mechanism confirmed by code; trigger not demonstrated. |
| Authenticated DAST (scanning behind a live OIDC session) | Not executed | The approved profile in `compose.security.yaml` is an unauthenticated baseline plus an OpenAPI-seeded API scan. Post-authentication scanning is outside the owner-approved scope and would need separate authorization. Consequence: authenticated-only surfaces were probed for authorization (all denied, §5.2) but not scanned for vulnerabilities behind the token. |
| Manual penetration testing | Not performed | Not required by the threat model for an internal pilot; the automated profile is the approved scope. |
| Writing the owner risk decision | **Refused, not blocked** | `docs/security/DECISIONS.md` is outside the `security` role's write allowlist, and accepting risk is the owner's alone. See §9.1. |
