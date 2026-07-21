# Sprint 1 — closure report

Candidate: `e12711b` on `release/0.1.0` — the SHA the release carries, with the final assurance
verdict on it (see §4). Closed 2026-07-20. Release class: **internal pilot / pre-release**.

## 1. Goal and outcome

> Walking skeleton observable from day one; the accounting heart proven by race tests; a person
> opens an account and logs in.

Met. All four committed specs are merged to `develop` and `IMPLEMENTED`. A person can sign up,
pass KYC against the bureau emulator, have an account opened with its `$0.00` opening posting,
sign in through OIDC + PKCE, and see a zero balance — exercised end to end in a real browser by
`frontend/e2e/signup-account.spec.ts` and `login-shell.spec.ts`.

## 2. Scope delivered

| Spec | Risk | PR | Merge SHA | Merged (UTC) |
|---|---|---|---|---|
| SPEC-0018 — walking skeleton | R3 | [#7](https://github.com/fkazeredo/fkbank-sdd/pull/7) | `15fd4aa` | 2026-07-20T08:31:18Z |
| SPEC-0016 — observability baseline | R2 | [#8](https://github.com/fkazeredo/fkbank-sdd/pull/8) | `0c7139a` | 2026-07-20T11:21:11Z |
| SPEC-0001 — ledger core | R3 | [#9](https://github.com/fkazeredo/fkbank-sdd/pull/9) | `9032cd9` | 2026-07-20T15:31:19Z |
| SPEC-0002 — sign-up & account | R3 | [#10](https://github.com/fkazeredo/fkbank-sdd/pull/10) | `d354ae9` | 2026-07-20T21:06:56Z |

Nothing was carried out of Sprint 1 incomplete. Sprint 2 scope is unchanged.

## 3. Verification evidence

- `tools/quality/verify-release` on the candidate: **PASS**, exit 0, 294 s, E2E 8/8 —
  `.claude/runtime/verify-release-sprint1.log`.
- `tools/quality/verify-e2e` after the edge security-header change: **PASS**, 8/8 including the
  PKCE login journey and sign-up in real Chromium — `.claude/runtime/verify-e2e-headers.log`.
- CI on PR #10: 9/9 required checks green on `798de6b` (CodeQL ×2, e2e, gitleaks,
  supply-chain-containers, supply-chain-deps, verify-slice, workflow-smoke).
- CI has **not** run on `3dec822` or any `release/0.1.0` commit: the branch is not on `origin`
  yet. The release PR is what puts it there.

## 4. Security assurance

**Verdict: `SECURITY_OBSERVATIONS`** on the release candidate `e12711b` —
`docs/security/reports/SPRINT-1-e12711b.md`.

It took five runs. Each failure was real and is worth keeping, because every one of them was a
control that would have certified something false if left unexamined:

| Run | SHA | Verdict | Why |
|---|---|---|---|
| 1 | `d9aad61` | BLOCKED | Family 6 (DAST) **NOT_EXECUTED** — `compose.security.yaml` health-checked the backend with `curl`, absent from the JRE-on-Alpine image. The stack never came up; no scan had ever run, on any candidate. |
| 2 | `068d94f` | BLOCKED | Family 6 executed for the first time and **failed four gating rules**: no CSP, nothing forbidding framing, no `nosniff`, no `Permissions-Policy`. Fixed by adding the edge security headers. |
| 3 | `3dec822` | SECURITY_OBSERVATIONS | Gating rules closed, verified across fifteen path classes; every applicable mandatory control executed. |
| 4 | `61631c3` | (no verdict) | The subagent worker ended before its own ~15-min wrapper finished and produced no verdict — a subagent's budget does not span the wrapper. No result to trust. |
| 5 | `e12711b` | **SECURITY_OBSERVATIONS** | The orchestrator ran the deterministic wrapper to completion; an independent worker judged the produced evidence and issued the verdict. Every discrimination check passed. |

Two things surfaced only because run 5 actually executed on the final candidate rather than
carrying run 3 forward:

- Run 5's first attempt (on `61631c3`) **failed `verify-release`**: an age-boundary acceptance
  test built its boundary birth date from `LocalDate.now()` (the JVM's Brasília zone) while the
  server evaluates age in UTC. After 21:00 BRT the two name different days and the boundary
  flipped — green in a UTC-clocked CI, red here. Fixed in `e12711b` (§7).
- The final DAST executed on a candidate that includes the F-01 fix; run 3's edge result could
  not be carried because the product's frontend had changed.

`SECURITY_VERIFIED` is unavailable, and this is not a technicality: family 3's
repository-configuration control **fails** on the exact candidate (no branch protection), and
there is no CI on the exact SHA until the release PR puts it on `origin`. The branch-protection
gap is the consequence the owner accepted knowingly in `SEC-0001` (`docs/security/DECISIONS.md`).

Three things stand between this project and a clean `SECURITY_VERIFIED`: configure rulesets on
`main` and `develop`, publish the candidate branch and get CI green on the exact SHA, and re-run
the track there. The assurance engineer's recommendation is to do the first **before the next
sprint** rather than before production — it is a settings change, not code.

## 5. Failed or waived gates

Seven, each recorded because a closure report that lists only successes is not evidence.

**5.1 — The mandatory R3 review of SPEC-0002 never ran before the merge.** PR #10 has zero
reviews, no review report existed, and the slice's state recorded no `review_verdict`, while all
three earlier slices have all three artifacts. `.claude/rules/risk-model.md` makes the isolated
review worker mandatory at R3. It was run post-hoc during this closure and returned
**`REVIEW_FINDINGS` — 1 High, 6 Medium, 5 Low** (`.claude/runtime/SPEC-0002/review-report.md`).
The findings are real and are listed in §7. Running it late recovered the information; it did
not recover the chance to act on it before merging, which is the actual cost.

**5.2 — No branch protection exists.** `gh api .../rulesets` returns `[]`,
`.../branches/develop/protection` returns 404, and `main` does not exist on `origin`. CLAUDE.md
§Git and invariant 6 both assert server-side enforcement by GitHub rulesets. There is none: the
agent's own restraint is the entire control. Owner-accepted as `SEC-0001` with remediation due
before the first production release.

**5.3 — The release does not follow the documented tag path.** CLAUDE.md §Git states tags
`vX.Y.Z` exist only after a human merge to `main`. `main` does not exist on `origin`, so the
owner directed that the release PR target `develop`, and subsequently took the tag entirely:
the owner creates `main` and the tag by hand. The agent creates and pushes no tag and publishes
no GitHub Release for this candidate. Recorded in `.claude/runtime/SPRINT-1/decision-request.md`.

**5.4 — The DAST control family had never executed, on any candidate.** See §4 run 1. The bug
was one line, and the E2E stack the security stack claims to mirror already carried the working
form with a comment describing that exact trap. Fixed in `068d94f`.

**5.5 — The assurance wrapper reported success while a control failed.**
`tools/security/verify-assurance.ps1` had no `exit` statement and leaned on an implicit code
that the DAST teardown overwrote with `0`, while `controls.txt` recorded
`dynamic-security=FAIL`. Any gate keyed on that exit code would have passed an entirely
unexecuted control family. Fixed in `068d94f`; both wrappers now assert the ZAP artifacts exist
before recording a pass. Run 3 confirmed the wrapper and the evidence now agree.

**5.6 — SPEC-0001 consumed three QA runs against a policy limit of two.** Authorised by the
owner at the time and recorded as DL-0019 in that spec. Noted here so the exception is visible
at sprint level rather than only inside the slice.

**5.7 — An environment-flaky test passed CI and failed the release verification.** The
SPEC-0002 age-boundary acceptance test built its boundary birth dates from `LocalDate.now()`
(the JVM's default zone) while the server evaluates age in UTC. CI runs in UTC, so the two
agreed and the test was green; the release verification ran on the Brasília-clocked operator
machine after 21:00, where UTC has already rolled to the next day, and the boundary flipped
(expected `422`, got `201`). A test whose result depends on the wall-clock zone is not a gate,
it is a coin that lands heads in CI. Fixed in `e12711b` — the test now reads
`LocalDate.now(ZoneOffset.UTC)`, matching the server. The deeper product question (which civil
timezone defines coming-of-age) is F-02.

## 6. The SHA this verdict covers

The final assurance verdict was reached on **`e12711b`**, the release-candidate tip, not carried
forward from an earlier SHA. Run 3 (`3dec822`) had set the carry-forward condition explicitly —
the verdict holds only if the later delta is limited to version metadata, changelog and assurance
documents. That condition was **not** met: the F-01 fix (`ea4e1ed`) changed production frontend
code, which voided the carry and forced a real re-run. So the track re-executed end to end on the
candidate that actually ships. The one coverage limit the re-run declared openly: automated SAST
(CodeQL) did not run on the exact SHA because the branch is not yet on `origin` — the changed
frontend was reviewed by hand instead, and CI on the exact SHA arrives when the release PR pushes
the branch. Any further commit to this branch before the merge re-opens the same question.

## 7. Carry-over into Sprint 2

Nothing here blocks the internal pilot. Both independent workers said so on their own evidence,
and no Critical finding exists.

**Fixed during closure**, before the release, on the owner's instruction:

- **F-01 (was High) — fixed in `ea4e1ed`.** A rejected sign-up rendered a general "review the
  details" with no field highlighted: the frontend read the 422 looking for an `errors` array
  that Spring's flat `ProblemDetail` never sends, and the full-name field accepted a one-word
  name the server then refused. The frontend now reads the `code`/`detail` a 422 actually
  carries and mirrors the server's full-name rule inline. Its service tests had asserted the
  invented body shape and so passed against the wrong contract; they now assert the real shape.
  Verified: frontend unit 109/109, lint clean, verify-e2e 8/8.

**Required before any production, end-user-facing release:**

| ID | Sev | What |
|---|---|---|
| SEC-F-01 | Medium | Raw CPF reaches application logs through `org.hibernate.orm.jdbc.error` on the duplicate-CPF path, bypassing the app's own `Cpf.masked()` discipline. `application-prod.yml` does not downgrade that logger, so it applies in production. An unauthenticated caller chooses which CPFs get written. CWE-532. |
| F-02 | Medium | The coming-of-age timezone is unsettled across layers, and it is never convention in a bank. The backend evaluates age in **UTC**; the frontend uses browser-local time; the leap-day rule (born `2008-02-29`, adult on `2026-02-28`) is enforced backend-only. The age-boundary **test** flakiness (§5.7) was one symptom, now fixed by matching the test to the backend's UTC — but which civil timezone *should* define adulthood (UTC vs America/Sao_Paulo) is an owner decision, not the test's to settle. |
| F-03 | Medium | BR-1's e-mail uniqueness is unenforced while an application is `PENDING`. Two CPFs sharing one e-mail both reach approval; the second violates `customer.email UNIQUE`, returning 409 **to the bureau**, rolling back settlement and stranding the onboarding in `PENDING`. CPF has a partial unique index for this window; e-mail has none, and no test covers it. |
| F-04 | Medium | A stranded `PENDING` application has no recovery path: the bureau decision runs only on a fresh insert, resubmission short-circuits, and no scheduled retry or expiry exists. With the V5 index that CPF can then never open an account — the failure the async design was recorded as preventing. |

**Also carried, lower priority:** F-05 (a pending application's `onboardingId` is returned to
anyone who knows the CPF, exposing the KYC outcome via the public status route), F-06 (V5 has no
cleanup for the stray `PENDING`+`APPROVED` state its own header documents — latent only: the dev
volume `compose.dev.yaml` declares does not exist on any machine here, so every start is a fresh
database), F-07 (neither concurrency test asserts the loser's outcome; both pass against
pre-fix code), F-08..F-12 (comment-hygiene violations in five test files, tautological
idempotency assertions, a file/class name mismatch, an `isActive()` filter missing from one
credential lookup, and a plan that still describes the pre-V5 index predicate), and SEC-F-26/27/28
(dead `location /assets/` block, duplicated headers on proxied responses, and the CSP's
`style-src 'unsafe-inline'` concession).

**One measured process defect, not a finding about the product.** A subagent runs under the
**same** `CLAUDE_CODE_SESSION_ID` as the session that spawned it. `tools/workflow/start-phase`
writes a session-scoped role file so a worker cannot overwrite its parent's role, and
`path-guard` prefers it — but that mitigation assumes distinct session ids, and subagents inherit
the parent's. Both therefore write the same file, and the guard cannot tell an orchestrator from
its own worker. This blocked legitimate writes twice during this closure and, more seriously,
means the authorship audit trail the QA-ownership rules depend on does not distinguish who wrote
what while a worker is running. Recorded as an open item on SPEC-0002 before this sprint; now
diagnosed with the exact cause.

## 8. Evidence index

- Assurance reports: `docs/security/reports/SPRINT-1-{d9aad61,068d94f,3dec822,e12711b}.md`
  (the last is the verdict of record)
- Risk acceptance: `docs/security/DECISIONS.md` — `SEC-0001`
- Post-hoc review: `.claude/runtime/SPEC-0002/review-report.md`
- QA reports: `.claude/runtime/SPEC-{0018,0016,0002}/qa-report.md`, `docs/qa/QA-0001.md`
- Test books: `docs/tests/TB-{0018,0016,0001,0002}.md`
- Verification logs: `.claude/runtime/verify-release-sprint1.log`,
  `.claude/runtime/verify-e2e-headers.log`, `.claude/runtime/verify-e2e-f01.log`,
  `.claude/runtime/security-candidate/verify-assurance.log` (+ `controls.txt`, ZAP reports)
- Release: `docs/release-notes/0.1.0.md`, `docs/CHANGELOG.md` §[0.1.0]
- Owner decisions taken during closure: `.claude/runtime/SPRINT-1/decision-request.md`
- ADRs from this sprint: `docs/adr/ADR-0001-ledger-dependencies.md`,
  `docs/adr/ADR-0002-emulator-service-pattern.md`
