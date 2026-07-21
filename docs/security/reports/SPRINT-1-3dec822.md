# Security Assurance Report — Sprint 1 release candidate (run 3)

**Expected duration of this assurance run: 45 minutes.** Actual wall clock is recorded in
§10 Execution log.

| Field | Value |
|---|---|
| Candidate SHA | `3dec8229c723d93a089535928410c22abe89eb6a` |
| Branch | `release/0.1.0` (local-only; not on `origin`) |
| Release class | Internal pilot / pre-release — confirmed independently, not taken on assertion (§1.3) |
| Track | `docs/security/SECURITY-ASSURANCE-TRACK.md` (owner-approved 2026-07-17, `track_approved: true`) |
| Executed by | `security-assurance-engineer`, independent worker, run 3 |
| Date | 2026-07-20 |
| Previous runs | `SPRINT-1-d9aad61.md` (`BLOCKED`) · `SPRINT-1-068d94f.md` (`BLOCKED`) |
| **Verdict** | **`SECURITY_OBSERVATIONS`** — bound to this SHA only, and narrower than it looks (§9) |

This report stands alone. Every family is marked **RE-EXECUTED** or **CARRIED FORWARD** with the
reason the carry is sound. Where I disagree with the implementation team, with a prior run of my
own, or with a tool's rating, the disagreement is stated rather than smoothed over.

---

## 0. What changed since run 2, and what this run set out to disprove

Run 2 returned `BLOCKED` for three reasons: four gating DAST header rules failed (SEC-F-17/18/19/20),
family 3's repository-configuration control failed (SEC-F-02), and no durable owner risk decision
existed (SEC-F-15). Run 2 recommended **fixing** the header findings rather than accepting them.
That recommendation was taken.

This run set out to disprove three claims, not to confirm them:

1. that the new headers are actually served on every path the edge serves — not just on `/`;
2. that the CSP is adequate rather than merely present;
3. that the recorded owner decision satisfies the track's finding policy.

Claims 1 and 2 survived with qualifications (§6.4, §6.5). Claim 3 did **not** survive intact — the
record had a real defect, which I corrected and disclose in §9.2.

### 0.1 Candidate delta — verified independently

```
$ git diff --stat 068d94f 3dec822
 .claude/hooks/path-guard.ps1              |   7 +-
 docs/security/DECISIONS.md                |  41 +-
 docs/security/reports/SPRINT-1-068d94f.md | 770 ++++++++++++++++++++++++++++++
 frontend/nginx.conf                       |  41 +-
 4 files changed, 855 insertions(+), 4 deletions(-)
```

Four files, as stated. The stronger check is against run 1's candidate, because that is what
licenses every carry-forward in this report:

```
$ git diff --stat d9aad61 3dec822 -- backend/ frontend/src/ emulators/
(empty)
$ git diff --stat d9aad61 3dec822 -- frontend/
 frontend/nginx.conf | 41 +++++++++++++++++++++++++++++++++++++++--
```

**No Java source, no Angular source, no emulator source, no migration and no build file has changed
across any of the three candidates.** The only product-affecting change in the entire Sprint-1
assurance history is `frontend/nginx.conf`. That is the fact the carries rest on, and it is why
this run spent its effort on the edge rather than re-litigating the backend.

### 0.2 A scoping fact neither prior run recorded

```
$ grep -n "<version>" backend/pom.xml
16:  <version>0.1.0-SNAPSHOT</version>
$ head docs/CHANGELOG.md   →  "## [Unreleased]"  (no versioned section)
$ git log --oneline d354ae9..3dec822
3dec822 fix(security): the edge served a banking SPA with no security headers
068d94f fix(security): the DAST family never ran, and nothing said so
d9aad61 chore(SPEC-0002): reconcile the sign-up slice merged in PR #10
```

**The release Prepare step has not run at this SHA.** The candidate still carries
`0.1.0-SNAPSHOT` and an unconsolidated `[Unreleased]` changelog. The SHA that eventually gets
tagged `v0.1.0` will therefore be a *descendant* of `3dec822`, not `3dec822` itself.

This matters to the verdict, not just to tidiness. Under the track's "stale candidate" clause, a
verdict attached to a SHA is a verdict about that SHA. See §9.4 for the precise condition under
which this verdict may be carried to the Prepare commit, and when it may not.

---

## 1. Candidate integrity — PASS with two observations — RE-EXECUTED

```
$ git rev-parse HEAD
3dec8229c723d93a089535928410c22abe89eb6a
$ git rev-parse --abbrev-ref HEAD
release/0.1.0
$ git status --porcelain
(empty at run start; at run end, only ` M docs/security/DECISIONS.md` — this worker's own
 disclosed amendment, §9.2)
```

No production code was modified by this worker.

### 1.1 CI evidence — OBSERVATION: no CI has run on the exact candidate SHA

```
$ gh api repos/fkazeredo/fkbank-sdd/commits/3dec8229c723.../check-runs
{"message":"No commit found for SHA: 3dec8229c723...","status":"422"}
$ gh api repos/fkazeredo/fkbank-sdd/branches --jq '.[].name'
chore/autonomous-workflow-behavioral-ddd · develop · feature/spec-0001-ledger-core
feature/spec-0002-signup-account · feature/spec-0016-observability-baseline
feature/spec-0018-walking-skeleton
```

`3dec822` does not exist on `origin`, and `release/0.1.0` is not pushed at all. The honest
statement remains *"CI ran on byte-identical product code at `d354ae9`; the candidate SHA itself
has no CI run"* — mitigated by §0.1's empty product delta and by this run re-executing the full
release battery locally against the candidate (§10.4), but **not** converted into a pass.
`SECURITY_VERIFIED` would require CI on the exact candidate.

### 1.2 Changed-surface inventory (Sprint 1) — CARRIED FORWARD

Sound to carry: the inventory is a property of the merged slices, and no slice changed.

| Spec | Risk | Security-relevant surface |
|---|---|---|
| SPEC-0018 | R3 | Default-deny authorization, embedded Authorization Server, OIDC + PKCE, nginx edge, Flyway/Postgres, Angular shell, CI |
| SPEC-0016 | R2 | Correlation id + MDC, structured JSON logs, token-protected Prometheus, public OpenAPI |
| SPEC-0001 | R3 | Append-only double-entry ledger, `Money` 4-decimal, `FOR UPDATE` serialization, reversal-at-most-once, trial balance |
| SPEC-0002 | R3 | Sign-up + account opening, CPF/e-mail uniqueness, PII, KYC bureau emulator + webhook, credential issuance, same-CPF race |

Three R3 slices are what make this track mandatory.

### 1.3 Release class — verified, not accepted on assertion

The orchestrator asserted "internal pilot / pre-release". I checked the definition rather than
adopting the label:

```
$ grep -n -A3 "production_release_definition" .claude/workflow-policy.yml
82: production_release_definition: >
83-   A release destined for an environment exposed to end users. Every sprint release
84-   before that milestone is an internal pilot/pre-release.
79: r3_r4_internal_release: approved track executes; SECURITY_OBSERVATIONS requires recorded human decision
80: r3_r4_production_release: BLOCKED until the approved track executes with SECURITY_VERIFIED
```

No end-user-exposed environment exists for FKBANK; this is the first release of a `0.1.0` product
with no deployment target on `origin`. The classification holds on the repository's own written
definition. Had it not, this report's verdict would be `BLOCKED` rather than
`SECURITY_OBSERVATIONS`, per line 80.

---

## 2. Threat model — PASS — CARRIED FORWARD, with the edge boundary re-measured

Carried forward in full from run 1 §2. Sound because a threat model describes assets, actors and
trust boundaries of the product, and the product is byte-identical (§0.1).

Assets: CPF, full name / birth date / declared income, e-mail, password, access & refresh tokens,
ledger postings and balances, bureau HMAC secret, onboarding id (a bearer-equivalent capability
token). Actors: anonymous caller, applicant, authenticated customer, KYC bureau emulator, operator
/ log reader, CI and the delivery agent. Trust boundaries: internet → nginx edge; edge → backend;
backend → PostgreSQL (never published); bureau → webhook (HMAC); backend → log stream; developer
machine → GitHub repository.

**Boundary 1 (internet → edge) is where run 2's findings landed and where this run's evidence is
concentrated.** Run 2 measured it and found it bare. This run measured it again after the fix
(§6.4) and found it carrying the headers the boundary implies. The threat model's claim that the
edge is a genuine trust boundary is now supported by measurement in both directions: it failed
when the config was wrong, and it passes when the config is right. A control that has been
observed failing is worth more than one that has only ever been observed passing.

---

## 3. Static assurance — FAIL (repository configuration) — RE-EXECUTED

### 3.1 Secrets — PASS — RE-EXECUTED

Executed in this run via the wrapper's digest-pinned container path (no local `gitleaks` binary on
this host): `ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c95...`. Recorded as
`secrets(docker)=PASS` in this run's `controls.txt`.

### 3.2 SAST — PASS on identical product code — CARRIED FORWARD

CodeQL (`java-kotlin`, `javascript-typescript`) concluded `success` on `d354ae9`. No repository
wrapper runs CodeQL locally — it is a GitHub-hosted action — so it **cannot** be re-executed
against `3dec822` on this host. Carried on §0.1 byte-identity. Declared in §10.6.

### 3.3 Dependencies — PASS on identical product code — CARRIED FORWARD

`supply-chain-deps` → `tools/security/supply-chain/trivy-scan.sh deps`, Trivy pinned
`aquasec/trivy:0.72.0@sha256:cffe3f51...`, gate `HIGH,CRITICAL` with `--exit-code 1`; concluded
`success` on `d354ae9`. `trivy-scan.sh` is POSIX-only and this host has no working `.sh` path, so
it was not re-executed. Declared in §10.6. No dependency manifest changed since (§0.1), so the
carry does not paper over a new dependency.

### 3.4 Licenses — PASS, report-only by design — CARRIED FORWARD

Licenses are scanned and reported but do not gate, deliberately: deciding acceptable licenses for a
bank is an owner decision. A documented deferral, not a silent gap. Tracked as SEC-F-12 (Low).

### 3.5 Repository configuration — **FAIL** — RE-EXECUTED

Re-executed against the live GitHub API in this run, not inherited from either prior report:

```
$ gh api repos/fkazeredo/fkbank-sdd/rulesets
[]
$ gh api repos/fkazeredo/fkbank-sdd/branches/develop/protection
{"message":"Branch not protected","status":"404"}
$ gh api repos/fkazeredo/fkbank-sdd/branches/main/protection
{"message":"Branch not found","status":"404"}
```

**Independently confirmed as the orchestrator asked:** no rulesets, no branch protection of any
kind, and `main` does not exist on `origin`. CLAUDE.md invariant 6 states that protected Git
operations "require server-side GitHub rulesets"; none exist, so that clause is unenforced at the
server and rests entirely on agent and human discipline. See SEC-F-02.

The family is reported **FAIL**. A control that ran and returned a negative result is not a control
that passed, and per `security-gate.md` risk acceptance never converts a failed control into a
verified one. The owner's acceptance (SEC-0001) changes what may be *released*; it does not change
what the control *returned*.

---

## 4. Architecture — PASS — RE-EXECUTED (partially) + CARRIED FORWARD

**Re-executed in this run.** `ArchitectureTest` and `ModulithTest` ran green inside the wrapper's
`backend-tests` and `release-verification` controls, both of which recorded PASS on this candidate
(§10.4).

**CARRIED FORWARD:** run 1's line-by-line inspection of all six controllers/advice confirming that
no domain type appears at a delivery boundary (every response body a local record, every
`@RequestBody` a local record or `byte[]`), that root packages are exactly `domain`, `application`,
`infra`, that domain bounded contexts are flat, and that `domain` carries no third-party import
beyond the five exempted `@ApplicationModule` annotations. Sound to carry: those files are
byte-identical (§0.1).

**Enforcement gaps (SEC-F-13, Low, no live violation)** persist unchanged: cross-bounded-context
entity crossing has no ArchUnit rule (delegated to Modulith, which *whitelists* crossings);
"domain entities never become JSON" is convention only; and `onlyTheLedgerTouchesBalances` is a
four-name regex that does not match `JpaBalanceRepository` or `TrialBalance`. The candidate
complies; the guardrail is thinner than invariant 6 reads.

---

## 5. Adversarial behavior — PASS with Medium findings — RE-EXECUTED (key items) + CARRIED FORWARD

### 5.1 Sensitive logging — SEC-F-01 REPRODUCED AGAIN, and one prior overstatement corrected

Reproduced from **this run's own runtime output**, at this SHA — not carried:

```
$ grep -o "Key (cpf)=([0-9]*)" <run-3 wrapper log> | sort | uniq -c
      3 Key (cpf)=(07446104007)      1 Key (cpf)=(14786365483)
      1 Key (cpf)=(49515477409)      1 Key (cpf)=(58008576871)
      1 Key (cpf)=(64617354611)      1 Key (cpf)=(74454492999)
      1 Key (cpf)=(79735311577)      3 Key (cpf)=(85795105970)
$ grep -c "org.hibernate.orm.jdbc.error" <run-3 wrapper log>
24
```

**Correction to my own run-2 report.** Run 2 stated "twelve distinct raw CPFs written to the log
stream across 24 Hibernate JDBC error lines". That overstates the distinct count. The accurate
reading, verified here by decomposing the lines:

```
$ grep "org.hibernate.orm.jdbc.error" <log> | grep -o '"message":"[^"]\{0,60\}' | sort | uniq -c
     12 "message":"ERROR: duplicate key value violates unique constraint \
     12 "message":"HHH000247: ErrorCode: 0, SQLState: 23505
```

Each constraint violation emits **two** lines, only one of which carries the CPF. So the true
figure — in run 2 and in run 3 alike — is **8 distinct CPFs disclosed across 12 log lines**, within
24 Hibernate error lines. The defect is identical; my arithmetic was not. Recording it because a
finding whose numbers cannot be reproduced invites the whole finding to be dismissed.

**It still applies in production.** Re-verified at this SHA:

```
$ grep -rn "org.hibernate.orm.jdbc" backend/src/
(no output — the logger is downgraded nowhere)
$ grep -n -A6 "^logging" backend/src/main/resources/application-prod.yml
17:logging:
18-  level:
19-    org.springframework.security: WARN
```

`org.hibernate.orm.jdbc.error` emits at WARN, above the default root level, and no profile
downgrades it. The application's own masking discipline (`Cpf.toString()` masks; `RawPassword`,
`PasswordHash`, `BureauReference` render as `[protected]`) is bypassed entirely, because the CPF
reaches the log through the JDBC driver's error message rather than through a domain `toString()`.
The trigger is an ordinary unauthenticated attacker-reachable action: submitting a signup for a CPF
that already has a live application. **SEC-F-01 stands, Medium, CWE-532.**

**My view on the judgement that this is a follow-up rather than a release blocker — asked for
explicitly, so given plainly.** For *this* release the judgement is defensible, and I do not
dispute it: the candidate has no end-user-exposed deployment, so the log stream has no
non-developer readers, and the disclosed CPFs are synthetic scanner input. It is not a blocker for
an internal pilot.

I do dispute the framing, in two respects, and both belong on the record:

1. **"Follow-up" understates it.** This is an unauthenticated attacker's ability to write
   attacker-chosen national identity numbers into a bank's log stream at WARN, in the production
   profile, permanently, with no rate limit in front of it (SEC-F-07) and with log-read access
   already identified as a distinct exposure (SEC-F-11). An attacker who can enumerate CPFs against
   `POST /api/signup` gets a persisted, timestamped record of which of them are already customers —
   in the logs, and as a `409`-versus-`201` oracle regardless. It is a Medium that becomes a
   privacy-regulator problem on the first real customer, not a tidy-up.
2. **The deadline must not be "later".** It must be "before the first release destined for an
   end-user-exposed environment", which is a gate that already exists and that this report binds it
   to (§8.2). Recorded that way, the follow-up judgement is sound. Recorded as an untracked
   backlog item, it is the failure mode this track exists to catch.

Remediation is small: downgrade or filter `org.hibernate.orm.jdbc.error`, or catch the constraint
violation before Hibernate logs it. The latter is preferable — the code already maps the duplicate
to a domain outcome, so it is intercepting an exception it is expecting.

### 5.2 Authorization matrix — default-deny — RE-EXECUTED statically and CORROBORATED DYNAMICALLY

`anyRequest().authenticated()` is terminal; `/actuator/prometheus` is not on the `permitAll`
allowlist. Corroborated dynamically in this run: the API pass drove **78** client-error responses
(`A Client Error response code was returned by the server [100000] x 78`) across `/api/me`,
`/api/account/me`, `/actuator/prometheus`, `/oauth2/*`, `/login` and a long tail of attack paths.
Default-deny answered them.

Additionally corroborated first-hand in my own header matrix (§6.4), which is stronger than ZAP's
aggregate because it names the route and the status:

```
GET /api/me            → 401, WWW-Authenticate: Bearer
GET /actuator/prometheus → 401, WWW-Authenticate: Bearer
GET /oauth2/authorize  → 401
GET /api/nonexistent   → 401   (unmapped path denies rather than 404s — fails closed)
```

The last line is the interesting one: an unmapped path under `/api/` returns `401`, not `404`. The
route table is not disclosed to an unauthenticated caller. That is default-deny behaving correctly.

### 5.3 Webhook — HMAC correct, replay protection absent — CARRIED FORWARD

Shared-secret HMAC-SHA256 over the raw body (`X-Bureau-Signature`), verified **before** the body is
parsed, compared in constant time (`MessageDigest.isEqual`). No nonce, timestamp or expiry ⇒
SEC-F-05 (Medium) stands. Contained today by settle-once idempotency; the containment is not the
control. This run's API pass exercised `POST /api/webhooks/bureau` **20** times and produced no
additional finding on that route.

### 5.4 Metrics protection — CARRIED FORWARD, re-corroborated

`ObservabilityRouteSecurityIT` (401 without a token, 200 with one) ran green again inside this
run's `backend-tests` control. It proves a real `JwtDecoder` backs the resource server, and it
proves SEC-F-06: an ordinary customer token, with no scope requirement, reads the full metrics
endpoint. This run's API pass requested `/actuator/prometheus` 8 times unauthenticated; all denied.

### 5.5 Idempotency, concurrency and the same-CPF race — CARRIED FORWARD

Enforcement at both layers with the database having the final say:
`V3__signup_account.sql:15` (`cpf VARCHAR(11) NOT NULL UNIQUE` on `customer`) and
`V5__onboarding_one_live_application_per_cpf.sql:17-21`
(`CREATE UNIQUE INDEX onboarding_one_live_per_cpf ON onboarding (cpf) WHERE status IN ('PENDING','APPROVED')`).
V5 closes a real race V3 left open. `REJECTED` sits deliberately outside the index so a refused
applicant may reapply. Ledger concurrency uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Sound to
carry: migrations and source are byte-identical, and the full acceptance suite (including the race
tests) re-ran green in this run.

Incidentally corroborated: the 12 duplicate-key rejections in §5.1 are the database enforcing this
uniqueness under the scanner's 34 signup attempts.

### 5.6 Injection — no finding — CARRIED FORWARD, corroborated by DAST

All persistence goes through Spring Data / JPA with bound parameters; the correlation-id header is
sanitized against CRLF log injection by `WELL_FORMED = [A-Za-z0-9._-]{1,64}`; `V3` adds
`CHECK (cpf ~ '^[0-9]{11}$')`. This run's active/passive rule set returned **PASS** for SQL
Injection, Path Traversal, Remote OS Command Injection (and time-based), Server Side Template
Injection (and blind), XPath Injection, XSLT Injection, XXE, Spring4Shell [40045], Server Side Code
Injection and Cloud Metadata Potentially Exposed [90034] — **60** PASS rules on the edge pass and
**112** on the API pass.

### 5.7 Rate and abuse behavior — no control exists — CARRIED FORWARD

No bucket4j, no resilience4j, no nginx `limit_req` anywhere. SEC-F-07 (Medium) and SEC-F-09
(Low-Medium) stand. The DAST profile is a baseline/API scan, not a load generator, so it neither
confirms nor refutes the exhaustion vector.

---

## 6. Dynamic assurance (DAST) — **RE-EXECUTED — now PASS on both passes**

Target: the approved local ephemeral stack `compose.security.yaml`, project `app-security`, brought
up by this worker and by nothing else. No external or hosted target was contacted.

### 6.1 Proof this run's artifacts are this run's

Run 2's artifacts were **deleted by this worker at 22:54:13Z, before launch**, and the directory
was listed to prove the deletion:

```
$ ls -la .claude/runtime/security-candidate/     # 22:54:13Z, pre-launch
verdict.json  zap-baseline.conf(0 bytes)  zap.yaml      ← no ZAP report of any kind
$ ls -la .claude/runtime/security-candidate/     # post-run
controls.txt  docker-down.log  zap-api-report.{html,json}  zap-report.{html,json,md}  zap.yaml
```

Every ZAP artifact present afterwards was necessarily produced by this run. See §10.5 for the
evidence-handling disclosure.

### 6.2 Proof the stack came up and the scan reached real routes

Observed directly by polling `docker ps` during execution — not inferred from the diff:

```
[23:01:23] security-tests=Up 9s   edge=Up 15s (healthy)  backend=Up 31s (healthy)  db=Up 37s (healthy)
[23:05:06] security-tests=Up 3m   edge=Up 3m  (healthy)  backend=Up 4m  (healthy)  db=Up 4m (healthy)
```

Distinct backend routes actually requested, counted from the backend's own log lines:

```
87 GET /api/me           34 POST /api/signup       29 GET /v3/api-docs
20 POST /api/webhooks/bureau   10 GET /api/account/me    8 POST /login   8 GET /login
8  GET /actuator/prometheus    6 POST /oauth2/token      6 GET /oauth2/authorize
6  GET /api/version            4 GET /actuator/health    4 GET /latest/meta-data/
```

The money and identity surface, the webhook, the OAuth2 endpoints and cloud-metadata SSRF probes
were genuinely exercised. A baseline scan that only visited `index.html` would attest to nothing.

### 6.3 Proof the scan policy was applied

The host copy of `zap-baseline.conf` is 0 bytes (Docker's placeholder for the nested read-only
mount). Had the policy been empty *inside* the container, every rule would default to WARN and `-I`
would make the scan pass while proving nothing — a green scan and an unarmed scan look identical.
Discriminating evidence, present in **both** passes this run:

```
security-tests-1  | IGNORE-NEW: ZAP is Out of Date [10116] x 1
```

Rule 10116 is `IGNORE` **only** in `zap-baseline.conf`. With no policy loaded it would appear as
WARN. Its appearance as IGNORE proves the file was read.

Second, independent discriminator: the edge pass's PASS count rose from **56 (run 2) to 60 (run 3)**
— exactly the four rules that were FAILing (10038, 10020, 10021, 10063) moving into PASS. A policy
that had failed to load could not have produced that specific delta.

### 6.4 Pass 1 — edge baseline (`http://edge:8090`, AJAX spider) — **PASS**

```
FAIL-NEW: 0   FAIL-INPROG: 0   WARN-NEW: 3   WARN-INPROG: 0   INFO: 0   IGNORE: 4   PASS: 60
```

All four run-2 gating failures are gone. **But I did not accept ZAP's PASS as proof that the
headers are served everywhere**, because ZAP's CSP rules only evaluated HTML responses: rule 10055
fired on `/`, `/robots.txt` and `/sitemap.xml` (all SPA-fallback HTML) and on nothing else. A
scanner that never examined a JavaScript response cannot attest that the JavaScript response
carries a CSP.

So I brought the stack up again myself and measured every path class directly. **This is the
evidence for SEC-F-17/18/19/20 being closed; the ZAP PASS is corroboration, not proof.**

First, the fact that determines what needs checking:

```
$ docker exec app-security-edge-1 sh -c 'find /usr/share/nginx/html -maxdepth 2 | sort'
/usr/share/nginx/html/50x.html          /usr/share/nginx/html/index.html
/usr/share/nginx/html/chunk-*.js  (×8)  /usr/share/nginx/html/main-AATKXS74.js
/usr/share/nginx/html/favicon.ico       /usr/share/nginx/html/styles-AIKQI3MT.css
```

**There is no `/assets/` directory.** `frontend/angular.json` copies `public/**` — which contains
only `favicon.ico` — to the output *root*, and the Angular application builder emits hashed bundles
at the root too. The orchestrator asked me to verify headers on "a hashed asset under `/assets/`";
the correct answer is that **no such path exists in this build**. The hashed bundles are served by
`location /`, which declares no `add_header` and therefore correctly *inherits* the server-level
set. The `location /assets/` block is dead configuration (§6.6, SEC-F-26).

Header matrix, measured (`wget -S` for 2xx, raw HTTP over `nc` for error statuses, because busybox
`wget` aborts before printing headers on a non-2xx and would have made present headers look
absent — a measurement artifact I hit and worked around rather than reported as a finding):

| Path | Served by | Status | CSP | XFO | XCTO | Ref-Pol | Perm-Pol |
|---|---|---|---|---|---|---|---|
| `/` | `location /` → index | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/index.html` | `location = /index.html` | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/main-AATKXS74.js` | `location /` | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/chunk-B6jxX3SF.js` | `location /` | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/styles-AIKQI3MT.css` | `location /` | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/favicon.ico` | `location /` | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/dashboard` (SPA route) | try_files fallback | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/nonexistent-route` | try_files fallback | 200 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/assets/x.js` | `location /assets/` | **404** | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/me` | proxy | **401** | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |
| `/oauth2/authorize` | proxy | **401** | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |
| `/actuator/prometheus` | proxy | **401** | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |
| `/actuator/health` | proxy | 200 | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |
| `/v3/api-docs` | proxy | 200 | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |
| `/login` | proxy | 200 | ✅ | ✅✅ | ✅✅ | ✅ | ✅ |

Every path class carries the full set, **including 401 and 404 responses** — which is what `always`
exists for and is the half a naive check on `/` would have missed. The nginx inheritance trap was
handled correctly: the two locations that declare `add_header` repeat the whole set, and every
location that declares none inherits it.

`server_tokens off` verified by the same probe: `Server: nginx`, with no version. ZAP rule 10036
(`Server Leaks Version Information`), a WARN in run 2 with 7 hits, is **absent from this run's
output entirely** — SEC-F-22 closed.

`✅✅` marks a duplicate: on proxied responses Spring Security sets `X-Frame-Options: DENY` and
`X-Content-Type-Options: nosniff`, and nginx adds them again. Values are identical, so behaviour is
correct — but it is a real config smell (SEC-F-27, Low).

### 6.5 Is the CSP adequate? — my own judgement, not the config's

```
default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none';
form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
img-src 'self' data:; font-src 'self' data:; connect-src 'self'
```

**Adequate for this release class; not adequate as a permanent state.** Reasoning, both directions:

*Genuinely strong.* `script-src 'self'` with **no** `'unsafe-inline'` and **no** `'unsafe-eval'` is
the half that actually stops injected-script XSS, and it is the half that is usually conceded.
`base-uri 'self'` blocks base-tag injection (a common CSP bypass). `object-src 'none'` and
`frame-ancestors 'none'` are correct. `form-action 'self'` blocks credential exfiltration to an
attacker-controlled form target — meaningful on a banking login page. No wildcards, no
`https:` scheme sources, no CDN. For a single-origin SPA this is a tight policy.

*The concession, assessed rather than accepted.* `style-src 'unsafe-inline'` is a real weakness. The
config's own justification — Angular injects component styles at runtime — is true, but I did not
take it as the evidence; a config explaining itself is not proof. What I checked is whether the
concession is *contained*, and it largely is: the classic CSS-injection exfiltration channel is
`background:url(https://attacker/…)`, and `img-src 'self' data:` forbids it. `font-src 'self'
data:` closes the font-face variant and `connect-src 'self'` closes the fetch variant. So an
attacker who achieves CSS injection under this policy has UI-redressing and same-origin timing
side-channels, not a data exfiltration path. That is a materially smaller exposure than
`style-src 'unsafe-inline'` usually implies, and it is a property of *this* policy's other
directives rather than of the concession itself.

*Where I disagree with treating it as settled.* ZAP does flag it — `CSP: style-src unsafe-inline
[10055] x 4`, risk **Medium**, confidence High. It did not gate the release, and the reason is
worth stating precisely because it is not obvious: **`zap-baseline.conf` promotes rule `10038`
("CSP Header Not Set") to FAIL, but rule `10055` ("CSP", the policy-quality rule) is not listed at
all and therefore defaults to WARN.** The gate as written asks whether a CSP *exists*, not whether
it is *good*. That is a coverage gap in the gate, not a judgement that the concession is fine, and
nobody should later cite "the DAST gate passed" as evidence that the CSP was reviewed. It was
reviewed here, by hand. Recorded as SEC-F-28.

Also missing, and worth having before production: `report-uri`/`report-to` (no violation telemetry
at all today), and `require-trusted-types-for 'script'` as defence-in-depth. Neither is required
now.

**Recommendation: keep the CSP as-is for the internal pilot; close `style-src 'unsafe-inline'`
with build-time hashes or nonces before any end-user-exposed release, and add `10055` to the
gating policy at the same time so the gate can see the difference.** Tracked as SEC-F-28.

### 6.6 Pass 2 — OpenAPI-driven API scan (`http://backend:8080/v3/api-docs`) — **PASS with warnings**

```
FAIL-NEW: 0   FAIL-INPROG: 0   WARN-NEW: 2   WARN-INPROG: 0   INFO: 0   IGNORE: 1   PASS: 112
```

| Rule | ZAP risk | Hits | Disposition |
|---|---|---|---|
| Spring Actuator Information Leak [40042] | Medium | 1 (`/actuator/health`) | True positive, **downgraded to Low — SEC-F-21** |
| Insufficient Site Isolation Against Spectre [90004] | Low | 1 (`/v3/api-docs`) | True positive — SEC-F-23 |
| Unexpected Content-Type returned [100001] | Low | 2 | SEC-F-25 — API-scan heuristic, recorded, not gating |
| A Client Error response code was returned [100000] | Informational | 78 | **Not a finding — default-deny working** (§5.2) |

**SEC-F-21 severity reasoning, both halves.** ZAP rates 40042 Medium; I rate it **Low**.
*Aggravating, checked rather than assumed:* `frontend/nginx.conf:105` proxies
`^/(actuator(/|$)|v3/api-docs|swagger-ui(/|$|\.html))`, so `/actuator/health` **is reachable from
the internet through the edge** — I confirmed this by requesting it through the edge in §6.4, not
by reading the regex. *Mitigating:* web exposure is limited to `health,prometheus`; `prometheus`
requires authentication (measured 401 in §6.4); `show-details` is unset so the body is a bare
`{"status":"UP"}`. Disclosure is "this is a Spring Boot application and it is up" — real
fingerprinting value, negligible data. Compounds SEC-F-04 (same nginx rule proxies public API docs).

### 6.7 Control result

```
security-tests-1  | zap edge pass exit=0, api pass exit=0
security-tests-1 exited with code 0
$ cat .claude/runtime/security-candidate/controls.txt
release-verification=PASS
secrets(docker)=PASS
backend-tests=PASS
dynamic-security=PASS
dast-report=...\.claude\runtime\security-candidate/zap-report.html
$ cat wrapper-exit.txt          # captured directly, NOT through a shell that discards it
WRAPPER_EXIT=0
```

The `dast-report=` line is present this run, where run 2 had none: the wrapper's artifact
assertion (`verify-assurance.ps1:96-99`) is only reached on the control's *pass* path, so this run
exercised it for the first time and it confirmed both HTML reports exist. Family 6 **executed and
passed**.

**Exit-code caveat, repeated from run 2 because the trap is still live.** The background
notification for this run reported "exit code 0" — but that is `echo`'s status, not the wrapper's,
because the invocation ended in `echo "WRAPPER_EXIT=$?" > file`. Here the two happen to agree.
Anyone automating this gate must capture the wrapper's status directly and cross-check
`controls.txt`; a notification's exit code is not evidence about the wrapper.

---

## 7. Supply chain and deployment — PASS with findings — RE-EXECUTED (partially) + CARRIED FORWARD

### 7.1 Container least privilege — PASS — CARRIED FORWARD (no Dockerfile changed, §0.1)

`backend`: `eclipse-temurin:21-jre-alpine` / `USER fkbank`. `frontend`:
`nginxinc/nginx-unprivileged:1.29-alpine` / `USER 101`. `emulators/bureau`:
`eclipse-temurin:21-jre-alpine` / `USER fkbank`. No container runs as root at runtime; all three
declare a `HEALTHCHECK`. Base images are pinned by tag, **not by digest** — a residual supply-chain
gap folded into SEC-F-12. The one deliberate root is the *scanner* container
(`compose.security.yaml`, `user: root`), so the bind-mounted report directory is writable
regardless of host uid; ephemeral, destroyed with the stack, not a product image.

Corroborated in this run: `nginx -v` inside the running edge reported `nginx/1.29.8`, matching the
pinned `1.29-alpine` line — the image under scan is the one the Dockerfile names.

### 7.2 Secret injection and secure defaults — PASS with SEC-F-03 — CARRIED FORWARD

`ProductionSecretsGuard` (`@Profile("prod")`) fails the context closed if the issuer or the bureau
HMAC secret is blank or still the development default — a genuinely good control. Its gap is
unchanged: it checks exactly those two properties, while `application.yml:8` carries
`password: ${APP_DB_PASSWORD:app}` and `application-prod.yml` has no `datasource` override.
SEC-F-03 stands. The SPA remains a correctly **public** client
(`client-authentication-methods: none`, `require-proof-key: true` ⇒ PKCE mandatory, no client
secret to leak).

### 7.3 Provenance — NOT IMPLEMENTED (SEC-F-12) — CARRIED FORWARD

No SBOM, no image signing, no build provenance attestation, no digest-pinned base images.
Acceptable for an internal pilot with locally-built images; a prerequisite for production.
Compounded by §0.2: the candidate is not even version-stamped yet.

### 7.4 Rollback — PASS — CARRIED FORWARD

Flyway migrations are additive; `V5` drops and recreates an index only. No destructive migration in
Sprint 1, so roll-forward is available and rollback needs no data recovery.

---

## 8. Risk-specific drills (family 8) — PASS — RE-EXECUTED

Each R3 spec's named attack, and what actually ran on this candidate:

| Spec | Named attack | Evidence this run |
|---|---|---|
| SPEC-0002 | Two concurrent signups, same CPF | Race acceptance tests green in `backend-tests`; DB partial unique index enforced 12 live rejections under the scanner (§5.1, §5.5) |
| SPEC-0002 | Forged bureau webhook | HMAC verified pre-parse, constant-time; 20 scanner requests to the route, no finding (§5.3) |
| SPEC-0002 | Onboarding id used as a bearer capability | SEC-F-11 stands — the id is written to logs via `CorrelationIdFilter` |
| SPEC-0001 | Double-spend / negative balance | Pessimistic-write lock + balance floor; ledger suite green |
| SPEC-0001 | Reversal replayed | Reversal-at-most-once test green |
| SPEC-0018 | Unauthenticated reach of a protected route | Default-deny measured live: 401 on `/api/me`, `/actuator/prometheus`, `/oauth2/authorize`, and on unmapped `/api/*` (§5.2) |

---

## 9. Findings and verdict

Severity is this worker's own assessment; where it differs from the tool's rating the reasoning is
stated in §6.6. **No unresolved High or Critical finding exists for this release class.**

### 9.1 Status of the run-2 findings the orchestrator asked about

| ID | Run-2 state | Run-3 state |
|---|---|---|
| SEC-F-17 (CSP absent) | Medium, **gating FAIL** | **CLOSED** — CSP present on every path class, measured (§6.4). Successor concern SEC-F-28 opened for the `unsafe-inline` concession, Low. |
| SEC-F-18 (anti-clickjacking absent) | Medium, **gating FAIL** | **CLOSED** — `X-Frame-Options: DENY` **and** CSP `frame-ancestors 'none'`, measured on all paths incl. 401/404. |
| SEC-F-19 (`X-Content-Type-Options` absent) | Low, **gating FAIL** | **CLOSED** — `nosniff` measured on all paths. |
| SEC-F-20 (`Permissions-Policy` absent) | Low, **gating FAIL** | **CLOSED** — measured on all paths. |
| SEC-F-22 (`Server` version leak) | Low | **CLOSED** — `server_tokens off`; `Server: nginx` with no version; ZAP 10036 absent from this run entirely. |
| SEC-F-15 (no recorded owner decision) | **Process — sole blocking cause** | **CLOSED** — `docs/security/DECISIONS.md` now carries SEC-0001. Judged critically in §9.2. |
| SEC-F-01 (raw CPF in logs) | Medium | **PERSISTS, unchanged severity.** Reproduced again (§5.1); prod applicability re-confirmed; my run-2 count corrected. Not a blocker for an internal pilot; see §5.1 for my dissent on calling it a mere follow-up. |
| SEC-F-02 (no branch protection) | Medium, control FAIL | **PERSISTS, unchanged.** Control re-executed and failed independently (§3.5). Now covered by owner acceptance SEC-0001. Acceptance does not convert the FAIL into a pass. |
| SEC-F-23 (Spectre isolation) | Low, 15+1 hits | **PERSISTS**, 14+1 hits. The one-hit drop follows the changed response set, not a fix. |
| SEC-F-16, wrapper exit-code defect | Resolved in run 2 | **Remains resolved.** Additionally, the artifact assertion was exercised for the first time this run and passed (§6.7). |

### 9.2 The owner risk decision — read critically, and one defect corrected

Judged against the track's finding policy (owner · remediation deadline · exploitability ·
affected SHA · release decision):

- **Owner — satisfied.** Named in the heading and in Remediation: `fkazeredo`.
- **Exploitability — satisfied, and unusually well.** The "Exposure" paragraph states what is and
  is not at risk (no credential or customer data; the integrity and auditability of delivery
  history) rather than asserting a severity.
- **Release decision — satisfied.** "Accepted for the `v0.1.0` internal pre-release window", with a
  "Consequence carried knowingly" paragraph stating that an applicable mandatory control fails,
  that `SECURITY_VERIFIED` is unavailable, and that `SECURITY_OBSERVATIONS` is the ceiling. That is
  the correct rule, stated by the record itself.
- **Remediation deadline — satisfied, and I want to defend this against a pedantic reading.** It is
  event-bound ("before the first release destined for an environment exposed to end users"), not a
  calendar date. An event-bound deadline tied to a gate that is itself enforced is *stronger* than
  a date nothing enforces: the finding cannot reach production without this track catching it
  again. Accepted as sufficient.
- **Affected SHA — NOT satisfied as written. This was a real defect.** The line read
  "`068d94f` (Sprint 1 candidate) and every SHA before it". `3dec822` is a **descendant** of
  `068d94f`, so by its own words the acceptance excluded the candidate actually under assurance.
  An acceptance that goes stale the moment the candidate advances is exactly what the affected-SHA
  requirement exists to prevent.

**What I did about it, disclosed rather than done quietly.** I amended that one line in
`docs/security/DECISIONS.md` to name the window the decision already scoped
(`d9aad61`, `068d94f`, `3dec822`), and attached a dated amendment note saying what changed and why.
The file is within this role's write boundary at this SHA (`path-guard.ps1:38`, changed in this
candidate with the owner's authorization).

**What I did not do, and why the distinction matters.** I changed no term of the owner's decision:
not its scope, not its conditions, not its deadline, not its recorded consequence. The owner scoped
the acceptance to *the `v0.1.0` internal pre-release window*; `3dec822` is unambiguously inside that
window — it is that window's candidate. Correcting a clerical SHA enumeration to match the scope the
owner stated is transcription maintenance of a fact I independently verified. Extending the *scope*
would have been accepting risk on the owner's behalf, which is not mine to do, and I did not do it.
The amendment note is written so the owner can reverse it in one edit if my reading of their intent
is wrong.

### 9.3 Open findings

Owner for every finding below is `fkazeredo`. "Deadline: pre-prod" means **before the first release
destined for an end-user-exposed environment** — a gate this track enforces, per §1.3 and
`workflow-policy.yml:80`. Affected SHA for all: `3dec822` (and, except where noted, every Sprint-1
SHA).

| ID | Finding | Severity | Deadline | Disposition |
|---|---|---|---|---|
| SEC-F-01 | Raw CPF written to logs on the duplicate-CPF path via `org.hibernate.orm.jdbc.error`; applies in **prod**; 8 distinct CPFs / 12 lines reproduced this run | **Medium** | pre-prod | Open. Not a blocker for an internal pilot. See §5.1 for my dissent on the framing. CWE-532. |
| SEC-F-02 | No branch protection or rulesets; `main` absent on `origin` | **Medium** (Critical as a production precondition) | pre-prod | Control **executed and failed** (§3.5). **Owner-accepted — SEC-0001.** Acceptance ≠ pass. |
| SEC-F-03 | `ProductionSecretsGuard` does not cover `spring.datasource.password`; `${APP_DB_PASSWORD:app}` would silently apply in prod | **Medium** here; **High** if production-destined | pre-prod | Open. Prod profile unused in this candidate. |
| SEC-F-04 | `/v3/api-docs/**` and `/swagger-ui/**` are `permitAll` in all profiles including prod, and proxied by the edge | Medium | pre-prod | Open. Gate behind a profile. Compounds SEC-F-21. |
| SEC-F-05 | Bureau webhook has no replay protection (no nonce, timestamp or expiry) | Medium | pre-prod | Open. Contained by settle-once idempotency; containment is not the control. |
| SEC-F-06 | `/actuator/prometheus` requires only `authenticated()`; any customer token reads all metrics | Medium | pre-prod | Open. Require a dedicated scope or a separate management port. |
| SEC-F-07 | No rate limiting on any route; `POST /api/signup` drives password hashing on the request thread | Medium | pre-prod | Open. CPU-exhaustion vector on an unauthenticated route; amplifies SEC-F-01. |
| SEC-F-08 | `compose.prod.yaml` never passes `FKBANK_BUREAU_HMAC_SECRET`/`FKBANK_ISSUER`, so the guard throws on every start | Medium | pre-prod | Open. Fails **closed** — an incomplete template, not an exposure. |
| SEC-F-09 | `@RequestBody byte[]` on the public webhook with no size bound ⇒ unauthenticated buffer + HMAC amplification | Low-Medium | pre-prod | Open. Compounds SEC-F-07. |
| SEC-F-11 | `CorrelationIdFilter` logs `request.getRequestURI()`, writing the `onboardingId` capability token into the log stream | Low-Medium | pre-prod | Open. Log read access ⇒ poll any applicant's status. |
| SEC-F-21 | Spring Actuator information leak; `/actuator/health` internet-reachable through the edge (confirmed by request, §6.6) | Low (ZAP: Medium) | pre-prod | Open. Body is a bare `{"status":"UP"}`. Fingerprinting value only. |
| SEC-F-23 | Insufficient site isolation against Spectre (COOP/COEP/CORP absent), 14 edge + 1 API hits | Low | pre-prod | Open. Hardening beyond the CSP set; policy-WARN by design. |
| SEC-F-28 | **New.** CSP concedes `style-src 'unsafe-inline'`; and the gating policy lists `10038` (CSP present?) but not `10055` (CSP good?), so gate coverage stops at existence | Low | pre-prod | Open. Exfiltration channels contained by `img-src`/`font-src`/`connect-src` (§6.5). Fix with build-time hashes/nonces **and** add `10055` to `zap-baseline.conf`. |
| SEC-F-10 | `Credential.toString()` emits the raw e-mail; siblings in the same PII cluster are masked | Low | pre-prod | Open, latent — not currently reachable by any logger. |
| SEC-F-12 | No SBOM, image signing, provenance, or digest-pinned base images; license scan report-only | Low | pre-prod | Accepted for an internal pilot; production prerequisite. |
| SEC-F-13 | ArchUnit enforcement weaker than invariant 6 reads | Low | pre-prod | Open. **No live violation.** |
| SEC-F-14 | Connection-pool exhaustion surfaces as `401` on a public route | Low | pre-prod | Open. Fails closed; cost is diagnostic. **Not dynamically reproduced** — §10.6. |
| SEC-F-26 | **New.** `location /assets/` is dead configuration — the build emits no `/assets/` directory (§6.4), so its `expires 1y` / `Cache-Control: public, immutable` never applies and the hashed bundles are served with **no** `Cache-Control` at all | Low | pre-prod | Open. Not a security exposure (`index.html` correctly `no-store`; API responses `no-cache, no-store` from Spring). Reported because the config asserts a cache policy it does not apply, and a future reader will believe it. |
| SEC-F-27 | **New.** Proxied responses carry duplicate `X-Frame-Options` and `X-Content-Type-Options` (Spring Security + nginx) | Low | pre-prod | Open. Values identical, so behaviour is correct; duplicated security headers are mishandled by some legacy agents. Drop the nginx copies on proxy locations, or Spring's. |
| SEC-F-24 | Suspicious comments in the minified bundle `main-AATKXS74.js` | Informational | — | Open. Reviewed; no credential or endpoint disclosed. |
| SEC-F-25 | Unexpected `Content-Type` returned, 2 hits | Low | pre-prod | Open. API-scan heuristic; no exploitable path identified. |

Closed: SEC-F-15, SEC-F-16, SEC-F-17, SEC-F-18, SEC-F-19, SEC-F-20, SEC-F-22.

### 9.4 Verdict — `SECURITY_OBSERVATIONS`

**Why not `BLOCKED`.** The track blocks on a missing approval, missing evidence or environment, a
mandatory *skipped* control, a stale candidate, or an unresolved High/Critical. None applies:
every mandatory applicable control executed on `3dec822` this run or is carried forward on a
byte-identity argument declared in §10.6; family 6 executed and passed; the owner decision exists;
no High or Critical finding is open for this release class. Run 2's sole blocking cause
(SEC-F-15) is closed.

**Why not `SECURITY_VERIFIED`.** It requires every applicable mandatory control to have executed
**without failure** on the exact candidate. Two things deny it, and neither is negotiable:

1. **Family 3.5, repository configuration, executed and FAILED** on the exact candidate —
   re-confirmed independently this run (§3.5). `security-gate.md` is explicit that risk acceptance
   **never** produces `SECURITY_VERIFIED`, and SEC-0001 says so itself. The owner's acceptance
   makes the release *permissible*; it does not make the control *pass*.
2. **No CI has run on the exact candidate SHA** (§1.1) — `3dec822` is not on `origin` at all.

**Why `SECURITY_OBSERVATIONS` is available.** `security-gate.md` permits it "for an internal
candidate with an explicit owner decision and bounded remediation records", and
`workflow-policy.yml:79` says the same. All three conditions are met: the release class is
internal pilot on the repository's own definition (§1.3, verified not assumed); an explicit owner
decision exists and, after the §9.2 correction, covers this SHA; and every open finding in §9.3
carries an owner, a bounded deadline, an exploitability assessment, an affected SHA and a release
disposition.

**This verdict is narrower than it looks — three limits, stated so they cannot be quietly
dropped:**

- It is bound to `3dec822` **only**. Per §0.2, the release Prepare step has not run: this SHA still
  carries `0.1.0-SNAPSHOT` and an unconsolidated changelog, so the SHA eventually tagged `v0.1.0`
  will be a descendant. **If that descendant's delta against `3dec822` is confined to version
  metadata, `docs/CHANGELOG.md` and this report, the verdict may be carried forward with that diff
  recorded in the release evidence. If it touches any source, configuration, Dockerfile, compose
  file or dependency manifest, this verdict does not transfer and the track must re-run.**
- It authorises an **internal pilot / pre-release only**. It is not authority to deploy to any
  environment reachable by an end user.
- It rests on an owner acceptance of a **failed mandatory control**. That is a real, carried risk,
  not a technicality that has been dispositioned away.

### 9.5 What would have to change for `SECURITY_VERIFIED`

Precisely three things, none of which this worker can perform:

1. **Configure GitHub rulesets for `main` and `develop`** — pull request required, no direct push,
   no force-push, required status checks — and create `main` on `origin`. Then family 3.5 passes
   instead of failing, and SEC-0001's acceptance becomes unnecessary rather than load-bearing.
2. **Push the candidate branch and get CI green on the exact candidate SHA**, so family 1's CI
   evidence is about the SHA being released rather than about a byte-identical ancestor.
3. **Re-run this track on that SHA.** Risk acceptance never substitutes for either of the above.

Nothing else in this report stands between the candidate and `SECURITY_VERIFIED`: no High or
Critical finding is open, and family 6 now passes.

**My recommendation, since the track asks for one rather than a menu:** ship this internal pilot on
`SECURITY_OBSERVATIONS`, and do (1) before the next sprint rather than before production. Rulesets
are a five-minute repository setting with no code risk, they are the single item that stands
between this project and a clean `SECURITY_VERIFIED`, and every sprint they stay absent is a sprint
whose delivery history has no server-side integrity guarantee — which is the evidentiary basis
every RELAY verdict, including this one, rests on. Of the code-level findings, **SEC-F-01 is the
one I would fix first**, for the reasons in §5.1.

### 9.6 Production preconditions

This candidate must not be promoted to an end-user-exposed environment until, at minimum, SEC-F-01,
SEC-F-02, SEC-F-03, SEC-F-04, SEC-F-06, SEC-F-07 and SEC-F-28 are resolved, SBOM/provenance and
digest-pinned base images (SEC-F-12) exist, and the track is re-executed on the production
candidate SHA with CI green on that SHA. Production requires `SECURITY_VERIFIED`, which this report
does not grant.

---

## 10. Execution log

### 10.1 Duration

Declared budget 45 minutes. Actual wall clock approximately **20 minutes**
(2026-07-20 22:53Z → ~23:13Z): wrapper 22:54:20Z → 23:06Z; independent header verification
23:06Z → 23:11Z. No control was dropped for time.

### 10.2 Environment

| Tool | Version |
|---|---|
| Docker | 29.5.3 (build d1c06ef) |
| git | 2.45.2.windows.1 |
| gh | 2.92.0 |
| gitleaks | v8.28.0 (digest-pinned container; no local binary on host) |
| Trivy | 0.72.0 (digest-pinned, via CI on identical code) |
| OWASP ZAP | 2.16.1 (`ghcr.io/zaproxy/zaproxy:2.16.1@sha256:7840969c...`) |
| nginx (edge under test) | 1.29.8 (`nginx -v` inside the running container) |
| Host | Windows 11 Pro 10.0.26200 |

### 10.3 Commands executed by this worker

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/workflow/start-phase.ps1 `
  -Role security -Id SPEC-0002 -Phase SECURITY_ASSURANCE -Risk R3

powershell -NoProfile -ExecutionPolicy Bypass -File tools/security/verify-assurance.ps1 `
  -Target candidate -RequiresHeavy
```

`-RequiresHeavy` chosen deliberately, as in runs 1 and 2: without it the wrapper degrades a missing
scanner to `NOT_APPLICABLE`, turning an unexecuted mandatory control into a silent pass on an R3
candidate.

Then, for the independent header verification (authorised target, brought up and torn down by this
worker; no host port published by this compose file):

```bash
docker compose -f compose.security.yaml up -d db backend edge
docker exec app-security-edge-1 sh -c 'find /usr/share/nginx/html -maxdepth 2 | sort'
docker exec app-security-edge-1 sh -c 'wget -q -S -O /dev/null http://127.0.0.1:8090<path>'   # 2xx
docker exec app-security-edge-1 sh -c 'printf "GET <path> HTTP/1.1\r\nHost: ...\r\n\r\n" | nc -w 6 127.0.0.1 8090'  # non-2xx
docker compose -f compose.security.yaml down -v
```

Plus read-only verification quoted inline: `git rev-parse`, `git status`, `git diff --stat`,
`git log`, `gh api` (rulesets, branch protection, branches, check-runs), `docker ps`,
`docker inspect`, `grep` over the repository and over this run's log, and PowerShell
`ConvertFrom-Json` over the ZAP JSON reports.

### 10.4 Wrapper control results

| Control | Result | Evidence |
|---|---|---|
| `release-verification` | PASS | `verify-release.ps1` green on the candidate |
| `secrets(docker)` (pinned container) | PASS | recorded in `controls.txt` |
| `backend-tests` (`mvnw -B verify`) | PASS | full suite green, incl. `ArchitectureTest`, `ModulithTest`, `ObservabilityRouteSecurityIT`, race/acceptance tests |
| `dynamic-security` (ZAP, 2 passes) | **PASS** | edge `FAIL-NEW: 0 … PASS: 60`; API `FAIL-NEW: 0 … PASS: 112` |
| `dast-report` artifact assertion | PASS | first run to reach this check; both HTML reports exist |
| Wrapper process exit code | **0** | captured directly; agrees with `controls.txt` |

Artifacts: `.claude/runtime/security-candidate/` — `controls.txt`, `docker-down.log`,
`zap-report.{html,json,md}`, `zap-api-report.{html,json}`. `docker ps -a` after teardown shows
**0** residual `app-security` containers.

### 10.5 Evidence-handling note — disclosed, as in run 2

Run 2's `controls.txt`, `verify-assurance.log`, `docker-down.log` and all five ZAP artifacts were
**deleted by this worker at 22:54:13Z**, before launching run 3, so that a stale artifact could not
be mistaken for this run's result and so that any ZAP report found afterwards was provably new
(§6.1). Those files were untracked (`.gitignore:2` covers `.claude/runtime/*`) and were never
committed; they are not recoverable. Run 2's committed report quotes the relevant excerpts, and its
`verdict.json` was left in place. The trade-off — discriminating evidence for run 3 over retention
of run 2's raw log — was deliberate and is recorded here rather than left silent.

This run's raw wrapper log was written outside the repository, to the session scratchpad, and is
likewise not retained in version control.

### 10.6 Controls not executed, and why

| Control | Status | Reason |
|---|---|---|
| CodeQL SAST on the exact candidate SHA | Not re-executed | No repository wrapper runs CodeQL locally; it is a GitHub-hosted action. Executed on byte-identical product code at `d354ae9` (§0.1). |
| Trivy deps/licenses/config/images on the exact candidate SHA | Not re-executed | `trivy-scan.sh` is POSIX-only and this host has no working `.sh` path. Executed in CI on byte-identical code at `d354ae9`; no dependency manifest or Dockerfile has changed since. |
| CI checks on the candidate SHA | **Cannot execute** | `3dec822` and `release/0.1.0` do not exist on `origin` (§1.1). Contributes to the denial of `SECURITY_VERIFIED`. |
| Dynamic reproduction of pool exhaustion (SEC-F-14) | Assessed statically, not reproduced | The approved DAST stack publishes no host ports and is destroyed by `--abort-on-container-exit`. Mechanism confirmed by code; trigger not demonstrated. |
| Authenticated DAST (scanning behind a live OIDC session) | Not executed | The approved profile is an unauthenticated baseline plus an OpenAPI-seeded API scan. Post-authentication scanning is outside the owner-approved scope and needs separate authorization. Consequence: authenticated-only surfaces were probed for authorization (all denied, §5.2) but not scanned for vulnerabilities behind the token. |
| Manual penetration testing | Not performed | Not required by the threat model for an internal pilot; the automated profile is the approved scope. |
| CSP quality gating (ZAP rule `10055`) | **Not gating by policy** | `zap-baseline.conf` promotes `10038` (CSP header absent) to FAIL but does not list `10055` (CSP policy quality), which therefore defaults to WARN. Reviewed by hand instead (§6.5); recorded as SEC-F-28 so the gap is not mistaken for a clean bill. |

**No mandatory applicable control was skipped.** Every carry-forward above rests on the byte-identity
established in §0.1, and each is named here rather than folded silently into a family's PASS.
