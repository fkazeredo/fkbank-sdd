# Security Assurance Report — Sprint 1 release candidate (run 5)

**Expected duration of this assurance run: ~20 minutes** (evidence dispositioning, not a re-run of
the ~15-minute wrapper). Actual wall clock is recorded in §10 Execution log.

| Field | Value |
|---|---|
| Candidate SHA | `e12711b1a4be65fc76e435c5f173197c91b6ee79` |
| Branch | `release/0.1.0` (local-only; not on `origin`) |
| Release class | Internal pilot / pre-release — re-confirmed on the repository's own written definition (§1.3) |
| Track | `docs/security/SECURITY-ASSURANCE-TRACK.md` (owner-approved 2026-07-17, `track_approved: true`) |
| Executed by | `security-assurance-engineer`, independent worker, run 5 |
| Date | 2026-07-20 |
| Baseline run | `SPRINT-1-3dec822.md` (`SECURITY_OBSERVATIONS`) — families 1–8 established there |
| Prior runs | `SPRINT-1-d9aad61.md` (`BLOCKED`) · `SPRINT-1-068d94f.md` (`BLOCKED`) · `SPRINT-1-3dec822.md` (`SECURITY_OBSERVATIONS`) |
| **Verdict** | **`SECURITY_OBSERVATIONS`** — bound to `e12711b` only; narrower than it looks (§9.4) |

This report stands alone. Every family is marked **RE-EXECUTED**, **RE-CONFIRMED** or **CARRIED
FORWARD** with the reason the carry is sound. Where the frontend delta breaks a carry that run 3
could make on byte-identity, that is stated plainly rather than smoothed over (§3.2).

## 0. How this run's evidence was produced — disclosed, because it is unusual

The deterministic assurance wrapper (`tools/security/verify-assurance.ps1 -Target candidate
-RequiresHeavy`) was **executed to completion by the orchestrator**, not by this worker. The reason
is recorded transparently because an auditor must see it: three prior subagent runs could not span
the wrapper's ~15-minute wall clock, and one returned before it finished and produced no verdict.
Re-running the wrapper here would repeat that exact failure. The independence that invariant 7
requires is the **verdict and the dispositioning** — a digest-pinned container scan produces the
same bytes regardless of who typed `docker compose`. What is not delegated, and is done here, is
**verifying the evidence is real and discriminating** and issuing the judgment. Nothing below is
taken on the wrapper's "PASS" on faith; each control's evidence was checked for whether it would
look different if the claim were false (§6.1).

## 0.1 Candidate delta — verified independently

```
$ git diff --stat 3dec822 e12711b
 backend/pom.xml                                    |   2 +-
 .../onboarding/SignUpHttpAcceptanceIT.java         |  11 +-
 docs/CHANGELOG.md                                  |  19 +-
 docs/qa/SPRINT-1-CLOSURE.md                        | 155 ++
 docs/release-notes/0.1.0.md                        |  63 ++
 docs/security/DECISIONS.md                         |  17 +-
 docs/security/reports/SPRINT-1-3dec822.md          | 916 ++
 frontend/package-lock.json                         |   4 +-
 frontend/package.json                              |   2 +-
 frontend/src/app/features/signup/*                 | (TS/HTML/messages + tests)
 frontend/src/app/shared/i18n/messages.ts           |   3 +-
```

Commits: `ea4e1ed` (F-01 fix, frontend signup) · `61631c3` (release Prepare 0.1.0) ·
`e12711b` (test-only UTC clock fix). Verified byte-identity of the surfaces the carries rest on:

```
$ git diff --stat 3dec822 e12711b -- frontend/nginx.conf      → (empty)
$ git diff --stat 3dec822 e12711b -- backend/src/main         → (empty)
$ git diff --stat 3dec822 e12711b -- emulators/               → (empty)
$ git diff --stat 3dec822 e12711b -- **/Dockerfile compose*   → (empty)
```

**The edge, all backend production source, the emulators, every Dockerfile and every compose file
are byte-identical to run 3's candidate.** The product changes in this delta are confined to the
Angular sign-up feature (client-side) and its i18n messages.

The manifest changes are **version metadata only**, not a dependency delta — the discriminating
detail, because a dependency change would break the family-3.3 carry:

```
$ git diff 3dec822 e12711b -- backend/pom.xml     → -<version>0.1.0-SNAPSHOT</version> +<version>0.1.0</version>
$ git diff 3dec822 e12711b -- frontend/package.json → -"version": "0.0.0" +"version": "0.1.0"
$ git diff 3dec822 e12711b -- frontend/package-lock.json → only the project's own "version" fields
```

No dependency was added, removed or upgraded; the `dependencies` block is unchanged. This is the
version stamp run 3 §0.2 observed had **not** yet run — the release Prepare step (`61631c3`) has now
run at this candidate, closing that observation.

## 0.2 Run 3's verdict does not auto-carry — and that is why the track re-ran

Run 3 §9.4 set the carry condition explicitly: a descendant whose delta is "confined to version
metadata, `docs/CHANGELOG.md` and this report" may carry the verdict; one that "touches any source,
configuration, Dockerfile, compose file or dependency manifest" does not, and the track must
re-run. This delta **touches product source** (the F-01 Angular change). So run 3's verdict does
**not** transfer, and the correct handling is a fresh track execution against `e12711b` — which is
what happened: the wrapper re-ran, and its DAST re-scanned the newly-built frontend bundle (§6).
This report is a fresh assessment, not a carry of run 3's conclusion. Backend/emulator/arch/deps
carry forward on the byte-identity above; the frontend and the edge-facing DAST were re-executed.

---

## 1. Candidate integrity — PASS with two observations — RE-EXECUTED

```
$ git rev-parse HEAD            → e12711b1a4be65fc76e435c5f173197c91b6ee79
$ git rev-parse --abbrev-ref HEAD → release/0.1.0
$ git status --porcelain        → clean at run start
```

No production code was modified by this worker.

### 1.1 CI evidence — OBSERVATION: no CI on the exact candidate SHA

```
$ gh api repos/fkazeredo/fkbank-sdd/commits/e12711b.../check-runs
{"message":"No commit found for SHA: e12711b...","status":"422"}
$ gh api repos/fkazeredo/fkbank-sdd/branches --jq '.[].name'
chore/autonomous-workflow-behavioral-ddd · develop · feature/spec-0001-ledger-core
feature/spec-0002-signup-account · feature/spec-0016-observability-baseline
feature/spec-0018-walking-skeleton
```

`e12711b` does not exist on `origin`; `release/0.1.0` is not pushed. Same posture as run 3.
Mitigated for backend by §0.1 byte-identity and by this run re-executing the release battery
locally (§10.4). **Not** converted into a pass; it is one of the two reasons `SECURITY_VERIFIED`
is unavailable (§9.3). For the frontend, see §3.2 — CI is also where CodeQL would have run on the
exact changed bundle.

### 1.2 Changed-surface inventory — CARRIED FORWARD (unchanged slice set)

The merged slice set is unchanged: SPEC-0018 (R3), SPEC-0016 (R2), SPEC-0001 (R3), SPEC-0002 (R3).
Three R3 slices are what make this track mandatory. The F-01 fix and the test fix are refinements
to already-delivered SPEC-0002 surface, adding no new route, contract, or trust boundary.

### 1.3 Release class — re-confirmed, not accepted on assertion

`.claude/workflow-policy.yml` defines a production release as "destined for an environment exposed
to end users; every sprint release before that milestone is an internal pilot/pre-release." No
end-user-exposed environment exists; `e12711b`/`release/0.1.0` is not even on `origin`. The
classification holds on the repository's own definition. Were it production, this verdict would be
`BLOCKED` (family 3.5 fails), not `SECURITY_OBSERVATIONS`.

---

## 2. Threat model — PASS — CARRIED FORWARD

Carried from run 3 §2 / run 1 §2. Sound because a threat model describes the product's assets,
actors and trust boundaries, and the only product change is a **client-side** tightening of how the
sign-up screen renders a rejected submission (§3.6). It introduces no asset, no actor, no route and
no trust boundary; it narrows an existing rendering path. Boundary 1 (internet → edge) is
byte-identical (`nginx.conf` unchanged) and was re-measured by DAST this run (§6).

---

## 3. Static assurance — FAIL (repository configuration) — RE-EXECUTED / RE-CONFIRMED

### 3.1 Secrets — PASS — RE-EXECUTED
`secrets(docker)=PASS` in this run's `controls.txt`, via the wrapper's digest-pinned
`ghcr.io/gitleaks/gitleaks` container path. Executed against `e12711b`.

### 3.2 SAST — PARTIALLY CARRIED, frontend delta manually reviewed — declared, not waved through
This is the one carry run 3 could make on full byte-identity that this run **cannot**. CodeQL
(`java-kotlin`, `javascript-typescript`) concluded `success` on `d354ae9`; it is a GitHub-hosted
action and cannot run locally on this host, and `e12711b` is not on `origin`.
- **Backend half:** sound to carry — `backend/src/main` is byte-identical (§0.1).
- **Frontend half:** the sign-up TypeScript changed, so the `javascript-typescript` result at
  `d354ae9` does **not** cover the exact bundle under assurance. I did not carry it on byte-identity
  because that would be false. Instead I reviewed the F-01 diff against the sink classes CodeQL
  would flag: no `eval`/`Function`, no `innerHTML`, no `bypassSecurityTrust`, no `DomSanitizer`
  bypass anywhere in the sign-up feature (`grep` returned none); the new `readProblem` only reads two
  type-checked string fields; the new `fullNameValidator` is bounded pure-string logic; no new
  dependency. No SAST-class issue is introduced by the delta.
- **Residual, folded into an existing gate:** automated SAST on the exact changed frontend was not
  executed. This closes with the same pre-prod requirement that already binds this candidate — CI
  green on the exact candidate SHA (§9.3 #2, §9.5). Recorded in §10.6 so it is not mistaken for a
  clean automated pass.

### 3.3 Dependencies — PASS on identical manifests — CARRIED FORWARD
No dependency manifest change (§0.1: only project version fields moved). Trivy `deps` concluded
`success` in CI on byte-identical dependency sets at `d354ae9`; `trivy-scan.sh` is POSIX-only on
this host. The carry does not paper over a new dependency, because there is none.

### 3.4 Licenses — PASS, report-only by design — CARRIED FORWARD (SEC-F-12, Low).

### 3.5 Repository configuration — **FAIL** — RE-CONFIRMED independently on `e12711b`

```
$ gh api repos/fkazeredo/fkbank-sdd/rulesets                       → []
$ gh api repos/fkazeredo/fkbank-sdd/branches/develop/protection    → 404 "Branch not protected"
$ gh api repos/fkazeredo/fkbank-sdd/branches/main/protection       → 404 "Branch not found"
```

No rulesets, no branch protection of any kind, `main` absent from `origin`, `develop` the
unprotected default. Independently re-run against the live GitHub API this run. CLAUDE.md invariant
6's "protected — PR only, enforced by GitHub rulesets" is unenforced at the server. Reported
**FAIL**: a control that ran and returned a negative result is not a control that passed. Owner
acceptance SEC-0001 changes what may be *released*, not what the control *returned*
(`security-gate.md`: risk acceptance never converts a failed control into a verified one). See
SEC-F-02.

### 3.6 The F-01 fix, read for security — no product finding
Commit `ea4e1ed` replaced the sign-up service's `readFieldErrors` — which probed a nested error
payload (`properties.errors`/`errors`, per-entry `field`/`message`/`detail`/`reason`) into a
per-field map — with `readProblem`, which reads exactly two type-checked strings from the flat
422 problem+json: `code` and `detail` (each `typeof === 'string' ? … : null`). The component renders
a recognised stable `code` (`WEAK_PASSWORD`, `UNDERAGE_CUSTOMER`) as a **screen-owned local i18n
message** pinned to its field, and otherwise sets the banner to `problem.detail ?? t('signup.failed')`.

The `detail` on a 422 is the domain's **own static-validation** output, not a bureau payload — a
bureau rejection is a separate `rejected` onboarding outcome, not a 422. The banner renders through
Angular's default text interpolation (`@if (banner(); as message)`), and the sign-up feature
contains no `innerHTML`/`bypassSecurityTrust` sink, so even a hostile `detail` string would render
as inert text — no XSS. The change **narrows** what the screen will surface (from arbitrary
server-keyed per-field strings to two type-checked fields) and is a tightening, not a new exposure.
F-01 is resolved and raises no security finding.

---

## 4. Architecture — PASS — RE-EXECUTED + CARRIED FORWARD
`ArchitectureTest` and `ModulithTest` ran green in this run's `backend-tests`/`release-verification`
controls (log lines 1284–1286, `Tests run: 1, Failures: 0` each). Run 1's controller/boundary
inspection carries forward on backend byte-identity; the frontend change touches no backend
boundary. Enforcement gaps SEC-F-13 (Low, no live violation) persist unchanged.

---

## 5. Adversarial behavior — PASS with Medium findings — RE-EXECUTED (key items) + CARRIED FORWARD

### 5.1 Sensitive logging — SEC-F-01 REPRODUCED FIRST-HAND at `e12711b`
Not carried — reproduced from this run's own wrapper log:
```
$ grep -c "org.hibernate.orm.jdbc" <run-5 log>                    → 24
$ grep -o "Key (cpf)=([0-9]*)" <run-5 log> | sort | uniq -c
   8 distinct CPFs: 01309861277, 31515916804, 52463105070 (x3), 53140495544 (x3),
   65608620542, 65987399696, 72632197007, 83158729318
```
8 distinct raw CPFs disclosed across the duplicate-key constraint violations within 24 Hibernate
error lines — the exact run-3 pattern, on this candidate's own run. Backend byte-identical, so the
production applicability (WARN level, no profile downgrade) is unchanged. **SEC-F-01 stands,
Medium, CWE-532.** Not a blocker for an internal pilot (no non-developer log readers; synthetic
scanner CPFs), but its deadline is event-bound to the first end-user-exposed release, not "later"
(§9.3). My run-3 dissent on calling it a mere follow-up stands.

### 5.2 Authorization matrix — default-deny — RE-CONFIRMED dynamically
The API pass drove client-error responses across `/api/me`, `/api/account/me`,
`/actuator/prometheus`, `/oauth2/*`, `/login` and a long attack tail (ZAP alert `100000`,
Informational, present in `zap-api-report.json`). Default-deny answered them; `anyRequest()
.authenticated()` is byte-identical. `MeEndpointSecurityIT`, `RoutePermissionCompletenessIT`,
`ObservabilityRouteSecurityIT` ran green this run (log 572, 784, 826).

### 5.3–5.7 — CARRIED FORWARD (backend byte-identical)
Webhook HMAC pre-parse + constant-time, no replay protection (SEC-F-05, Medium; 20 scanner
requests to the route, no new finding). Metrics protected by `authenticated()` only (SEC-F-06). CPF
uniqueness enforced at DB (`V3` unique + `V5` partial index); the 8 duplicate-key rejections in §5.1
are that index enforcing under the scanner. No rate limiting anywhere (SEC-F-07/09). Injection: 60
edge / 112 API PASS rules, SQLi/Path-Traversal/OS-Command/SSTI/XXE/Spring4Shell all PASS.

---

## 6. Dynamic assurance (DAST) — RE-EXECUTED — PASS on both passes — the crux, checked for discrimination

Target: the approved local ephemeral stack `compose.security.yaml`, project `app-security`. No
external or hosted target contacted. The wrapper's stack came up and was torn down (docker-down.log:
`app-security-{security-tests,edge,backend,db}-1` all Stopped/Removed, network removed).

### 6.1 The four discrimination checks — each would look different if the claim were false

1. **Artifacts exist and are non-empty.** `zap-report.json` 20,463 B · `zap-report.html` 62,049 B ·
   `zap-api-report.json` 26,494 B · `zap-api-report.html` 93,340 B. Both JSONs parse as real ZAP
   reports carrying per-alert `pluginid`/`riskdesc`.
2. **The scan policy loaded — not an empty policy defaulting every rule to WARN.** Rule `10116`
   ("ZAP is Out of Date") is `IGNORE` **only** in `zap-baseline.conf`; with no policy it defaults
   to WARN. It appears as IGNORE in **both** passes — edge log line 2896 (`IGNORE: … [10116] x 1`)
   and API log line 3249 (`IGNORE-NEW: … [10116] x 1`), and as `riskdesc "Low (High)"` in both
   JSONs. An unarmed scan could not produce this.
3. **The real attack surface was reached — not just static URLs.** Counted from the backend's own
   log lines in the wrapper run: `POST /api/signup` ×34, `GET /api/me` ×88, `GET /api/account/me`
   ×10, `POST /api/webhooks/bureau` ×20, `POST /oauth2/token` ×6, `GET /oauth2/authorize` ×6,
   `GET /actuator/prometheus` ×8, `GET /latest/meta-data` ×4 (cloud-metadata SSRF probe),
   `GET /v3/api-docs` ×30. The money/identity surface, the webhook, OAuth2 and SSRF probes were
   genuinely exercised — essentially identical to run 3. A scan that only fetched `index.html`
   would show none of these.
4. **No High/Critical hides in either report.** `grep '"riskcode": "3"'` → **0** in both
   `zap-report.json` and `zap-api-report.json`.

### 6.2 Edge baseline pass (`http://edge:8090`, AJAX spider) — PASS
```
FAIL-NEW: 0   FAIL-INPROG: 0   WARN-NEW: 3   WARN-INPROG: 0   INFO: 0   IGNORE: 4   PASS: 60   (log 2909)
```
The **four gating header rules remain PASS** (log: `Anti-clickjacking [10020]`,
`X-Content-Type-Options [10021]`, `CSP Header Not Set [10038]`, `Permissions Policy [10063]` all
under PASS; none appears as an alert in `zap-report.json`). Run 3's SEC-F-17/18/19/20 stay closed.
This pass scanned the **new** F-01 Angular bundle and raised no new edge finding. Edge alert
inventory (`zap-report.json`):

| Rule | ZAP risk | Disposition |
|---|---|---|
| `10055` CSP: style-src unsafe-inline | Medium (High), WARN ×2 | **SEC-F-28** (Low, not gating). `10038` gates CSP *existence* and passes; `10055` (CSP *quality*) is not listed in `zap-baseline.conf` and defaults to WARN. Count 4→2 tracks the smaller HTML/SPA-fallback response set of the new bundle, not a regression. |
| `90004` Spectre isolation | Low (Medium), WARN ×14 | SEC-F-23, hardening beyond the CSP set. |
| `10027` Suspicious Comments | Informational, WARN ×1 | SEC-F-24. Reviewed; no credential/endpoint disclosed. |
| `10049`/`10096`/`10109`/`10116` | Info/Low | IGNORE by policy (cache/timestamp/modern-app/out-of-date heuristics). |

### 6.3 OpenAPI-driven API pass (`http://backend:8080/v3/api-docs`) — PASS
```
FAIL-NEW: 0   FAIL-INPROG: 0   WARN-NEW: 2   WARN-INPROG: 0   INFO: 0   IGNORE: 1   PASS: 112   (log 3255)
```

| Rule | ZAP risk | Disposition |
|---|---|---|
| `40042` Spring Actuator Information Leak | Medium (Medium), WARN ×1 | **SEC-F-21** — I rate Low. `/actuator/health` is edge-reachable but `show-details` unset, body a bare `{"status":"UP"}`; web exposure limited to `health,prometheus`, prometheus authenticated. |
| `90004` Spectre isolation | Low (Medium), WARN ×1 | SEC-F-23. |
| `100000` Client Error response | Informational | **Not a finding** — default-deny working (§5.2). |
| `100001` Unexpected Content-Type | Low | SEC-F-25 — API-scan heuristic, no exploitable path. |

Counts and dispositions match run 3. **No new finding appeared in either pass; no gating rule
failed; no High/Critical.**

### 6.4 Control result
`controls.txt`: `dynamic-security=PASS`. Log: `zap edge pass exit=0, api pass exit=0` (3256),
`security-tests-1 exited with code 0` (3257). Family 6 executed and passed on `e12711b`.

---

## 7. Supply chain and deployment — PASS with findings — CARRIED FORWARD
No Dockerfile, compose or base-image changed (§0.1). Containers run non-root with HEALTHCHECKs;
base images pinned by tag not digest (SEC-F-12). `ProductionSecretsGuard` gap SEC-F-03 persists.
No SBOM/signing/provenance (SEC-F-12). Flyway additive, rollback safe (SEC-F). Run 3 §0.2's
"not version-stamped yet" observation is now **closed** — `61631c3` stamped `0.1.0`.

---

## 8. Risk-specific drills (family 8) — PASS — RE-EXECUTED
Same-CPF race: acceptance tests green in `backend-tests`; DB partial unique index enforced 8 live
rejections under the scanner (§5.1). Forged webhook: HMAC pre-parse; 20 scanner requests, no
finding. Onboarding-id-as-bearer: SEC-F-11 stands (code byte-identical). Ledger double-spend /
reversal-replay: suites green. Unauthenticated reach of protected routes: default-deny measured
(§5.2). Additionally, the SPEC-0002 age-boundary acceptance tests — the subject of the `e12711b`
test-only fix — ran green (log 1315, "in the age boundary": 5 tests, 0 failures), now reading the
server's UTC clock deterministically.

---

## 9. Findings and verdict

No unresolved High or Critical finding exists for this release class (0 `riskcode: 3` in either
DAST report; SEC-F-01 is Medium). The open-findings register is unchanged from run 3 §9.3 — all
carried on backend/edge byte-identity, each already carrying owner `fkazeredo`, an event-bound
pre-prod deadline, an exploitability note and an affected-SHA disposition. Findings that closed in
run 3 (SEC-F-15/16/17/18/19/20/22) remain closed. **F-01 (the empty-dead-end product finding) is
resolved (§3.6) and raises no security finding.** Two new evidence notes this run:

- **SAST on the exact changed frontend not automated** (§3.2) — bounded, manually reviewed, closed
  by the existing pre-prod "CI green on exact SHA" gate. Not a new SEC-F; folded into that gate.
- SEC-F-28 count moved 4→2 (response-set artifact, not a fix or regression).

### 9.1 Standing findings register — CARRIED FORWARD from run 3 §9.3
SEC-F-01 (Medium, raw CPF in logs — reproduced §5.1), SEC-F-02 (Medium, no branch protection —
re-confirmed §3.5, owner-accepted SEC-0001), SEC-F-03/04/05/06/07 (Medium), SEC-F-09/10/11/12/13/14
(Low–Low-Medium), SEC-F-21/23/24/25/26/27/28 (Low/Info). Affected SHA for all: the `v0.1.0`
pre-release window, which includes `e12711b`. Full text and dispositions in `SPRINT-1-3dec822.md`
§9.3; none changed severity or disposition on this candidate.

### 9.2 Owner risk decision (SEC-0001) — covers `e12711b`
SEC-0001 accepts SEC-F-02 (branch protection absent) for "the `v0.1.0` internal pre-release window".
`e12711b` is the version-`0.1.0`-stamped `release/0.1.0` tip — unambiguously inside that stated
scope. Run 3 amended the affected-SHA line to a scope statement ("Every SHA in the `v0.1.0`
pre-release window") plus an illustrative enumeration. To keep the enumeration from going stale as
the candidate advanced — the exact defect run 3 fixed — I added a dated amendment naming `e12711b`
within the already-scoped window, after re-confirming the finding on it (§3.5). **No term of the
owner's decision was changed:** not its scope, conditions, deadline, or recorded consequence that
`SECURITY_VERIFIED` is unavailable. Extending the scope would be accepting risk for the owner, which
is not mine to do; enumerating a SHA the stated scope already covers is transcription of a fact I
verified. The finding policy's five elements (owner · deadline · exploitability · affected SHA ·
release decision) are all satisfied for `e12711b`.

### 9.3 Why not `SECURITY_VERIFIED` — two non-negotiable denials
1. **Family 3.5 (repository configuration) executed and FAILED** on `e12711b` (§3.5). Risk
   acceptance never produces `SECURITY_VERIFIED` (`security-gate.md`); SEC-0001 says so itself.
2. **No CI on the exact candidate SHA** (§1.1) — `e12711b` is not on `origin`. This also means
   automated SAST did not run on the exact changed frontend (§3.2).

### 9.4 Verdict — `SECURITY_OBSERVATIONS`, bound to `e12711b` only
Available because `security-gate.md` and `workflow-policy.yml:79` permit it for an internal
candidate with an explicit owner decision and bounded remediation records — all satisfied: internal
pilot on the repository's own definition (§1.3), owner decision SEC-0001 covering this SHA (§9.2),
every open finding bounded (§9.1). Not `BLOCKED`: every mandatory applicable control executed on
`e12711b` or carries forward on a declared byte-identity/manual-review basis; family 6 executed and
passed with discrimination confirmed (§6.1); no mandatory control was silently skipped; no
High/Critical is open.

**Three limits, stated so they cannot be quietly dropped:**
- Bound to `e12711b` **only**. This is the version-stamped release tip; any further commit that
  touches source, config, Dockerfile, compose or a dependency manifest voids this verdict and
  re-runs the track. A change confined to docs/this report may carry with the diff recorded.
- Authorises an **internal pilot / pre-release only** — not deployment to any end-user-reachable
  environment.
- Rests on an owner acceptance of a **failed mandatory control** (SEC-F-02) — a real carried risk.

### 9.5 What would make `SECURITY_VERIFIED` reachable (none performable by this worker)
1. Configure GitHub rulesets for `main`/`develop` and create `main` on `origin` → family 3.5 passes.
2. Push the candidate and get CI green on the exact SHA → family 1 CI evidence is about `e12711b`,
   and CodeQL runs on the exact changed frontend (§3.2).
3. Re-run this track on that SHA. Risk acceptance substitutes for neither.

**Recommendation (the track asks for one):** ship this internal pilot on `SECURITY_OBSERVATIONS`;
do (1) before the next sprint — a five-minute repository setting that is the single item between
this project and a clean `SECURITY_VERIFIED`. Of the code-level findings, fix SEC-F-01 first.

### 9.6 Production preconditions
Not for an end-user-exposed environment until at least SEC-F-01/02/03/04/06/07/28 are resolved,
SBOM/provenance/digest-pinned images (SEC-F-12) exist, and the track is re-executed on the
production candidate SHA with CI green on that SHA — which also supplies automated SAST on the exact
frontend (§3.2). Production requires `SECURITY_VERIFIED`, which this report does not grant.

---

## 10. Execution log

### 10.1 Duration
Declared ~20 minutes for evidence dispositioning. The ~15-minute wrapper was run separately by the
orchestrator (§0). No control was dropped for time; the wrapper was not re-run, by design.

### 10.2 Environment (from the wrapper run and this worker's checks)
Docker 29.5.x · git 2.45.2 · gh 2.92.0 · gitleaks v8.28.0 (digest-pinned container) · Trivy 0.72.0
(CI on identical code) · OWASP ZAP 2.16.1 (`ghcr.io/zaproxy/zaproxy:2.16.1`) · nginx 1.29 edge
(byte-identical config) · Host Windows 11 Pro 10.0.26200.

### 10.3 Commands executed by this worker (read-only + report writes)
`start-phase.ps1 -Role security -Id SPEC-0002 -Phase SECURITY_ASSURANCE -Risk R3`;
`git rev-parse/status/diff/log`; `gh api` (rulesets, branch protection ×2, branches, check-runs);
`grep`/`wc`/`ls` over the wrapper log and both ZAP JSON/HTML; `grep` over `frontend/src` for
unsafe-HTML sinks. No DAST stack was brought up by this worker; the wrapper evidence was read, not
re-generated.

### 10.4 Wrapper control results (executed by the orchestrator, verified here)
| Control | Result | Evidence |
|---|---|---|
| `release-verification` | PASS | `verify-release: 241s, exit=0` (log 1245) |
| `secrets(docker)` (pinned) | PASS | `controls.txt` |
| `backend-tests` (`mvnw verify`) | PASS | full suite green incl. Architecture/Modulith/security ITs and age-boundary tests (log 1284–1340, 1315, 572/784/826) |
| `dynamic-security` (ZAP ×2) | PASS | edge `FAIL-NEW: 0 … PASS: 60`; API `FAIL-NEW: 0 … PASS: 112`; discrimination §6.1 |
| stack teardown | clean | `docker-down.log`: 4 `app-security` containers + network Removed |

### 10.5 Evidence provenance
All ZAP artifacts, `controls.txt`, `verify-assurance.log` and `docker-down.log` under
`.claude/runtime/security-candidate/` were produced by the orchestrator's single wrapper run against
`e12711b` and read here unmodified. `.gitignore` covers `.claude/runtime/*`; this raw evidence is
not committed. The committed record is this report and run 3's.

### 10.6 Controls not (fully) executed, and why
| Control | Status | Reason |
|---|---|---|
| CodeQL SAST on the exact **changed frontend** | Not automated; delta manually reviewed | GitHub-hosted action, cannot run on this host; `e12711b` not on `origin`. Backend half carried on byte-identity; frontend delta reviewed for sinks (§3.2). Residual closed by the pre-prod CI-on-exact-SHA gate. |
| Trivy deps/licenses/config/images on exact SHA | Not re-executed | `trivy-scan.sh` POSIX-only; no manifest/Dockerfile changed (§0.1). CI-green on byte-identical sets at `d354ae9`. |
| CI checks on the candidate SHA | Cannot execute | `e12711b`/`release/0.1.0` absent from `origin` (§1.1). Denies `SECURITY_VERIFIED`. |
| Authenticated DAST / manual pen-test | Not executed | Outside the owner-approved profile (unauthenticated baseline + OpenAPI-seeded API scan). |
| CSP quality gating (ZAP `10055`) | Not gating by policy | `zap-baseline.conf` promotes `10038` to FAIL, not `10055`. Reviewed by hand; SEC-F-28. |

**No mandatory applicable control was silently skipped.** Every carry or partial execution above is
named, and the one carry run 3 made on byte-identity that this run could not (frontend SAST) is
disclosed rather than folded into a family PASS.
