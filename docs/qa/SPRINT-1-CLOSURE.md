# Sprint 1 — closure report

Candidate: `3dec822` on `release/0.1.0` (assurance ran here; see §6 for the SHA the release
actually carries). Closed 2026-07-20. Release class: **internal pilot / pre-release**.

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

**Verdict: `SECURITY_OBSERVATIONS`** on `3dec822` — `docs/security/reports/SPRINT-1-3dec822.md`.

Three runs were needed, and the first two failed for reasons worth keeping:

| Run | SHA | Verdict | Why |
|---|---|---|---|
| 1 | `d9aad61` | BLOCKED | Family 6 (DAST) **NOT_EXECUTED** — `compose.security.yaml` health-checked the backend with `curl`, which the JRE-on-Alpine runtime image does not ship. The stack never came up; no scan had ever run. |
| 2 | `068d94f` | BLOCKED | Family 6 executed for the first time and **failed four gating rules**: no CSP, nothing forbidding framing, no `nosniff`, no `Permissions-Policy`. |
| 3 | `3dec822` | SECURITY_OBSERVATIONS | Gating rules closed and verified across fifteen path classes; every applicable mandatory control executed. |

`SECURITY_VERIFIED` is unavailable, and this is not a technicality: family 3's
repository-configuration control **fails** on the exact candidate. That is the consequence the
owner accepted knowingly in `SEC-0001` (`docs/security/DECISIONS.md`).

Three things stand between this project and a clean `SECURITY_VERIFIED`: configure rulesets on
`main` and `develop`, publish the candidate branch and get CI green on the exact SHA, and re-run
the track there. The assurance engineer's recommendation is to do the first **before the next
sprint** rather than before production — it is a settings change, not code.

## 5. Failed or waived gates

Six, each recorded because a closure report that lists only successes is not evidence.

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

## 6. The SHA this verdict covers

The assurance verdict was reached on `3dec822`. Two commits follow it in this closure —
`c7980c6` (assurance report and the `SEC-0001` amendment) and the release Prepare commit. The
assurance engineer set the condition explicitly: **the verdict carries forward only if the delta
is limited to version metadata, the changelog and assurance documents.** Any change to source,
configuration, `Dockerfile`, compose or a dependency manifest voids it and requires re-execution.
That condition holds for the commits above; it must be re-checked if anything else lands on this
branch before the release.

## 7. Carry-over into Sprint 2

Nothing here blocks the internal pilot. Both independent workers said so on their own evidence,
and no Critical finding exists.

**Required before any production, end-user-facing release:**

| ID | Sev | What |
|---|---|---|
| F-01 | High | A `422` renders "review the details below" with no field errors: the frontend probes for `errors`/`properties.errors`, which Spring's flat `ProblemDetail` never emits. Full name `Bob` passes the frontend's `minLength(2)` but fails the backend's `FullName` (≥3 chars **and** a space) — a permanent dead-end with nothing on screen explaining it. |
| SEC-F-01 | Medium | Raw CPF reaches application logs through `org.hibernate.orm.jdbc.error` on the duplicate-CPF path, bypassing the app's own `Cpf.masked()` discipline. `application-prod.yml` does not downgrade that logger, so it applies in production. An unauthenticated caller chooses which CPFs get written. CWE-532. |
| F-02 | Medium | The leap-day coming-of-age rule is backend-only. Born `2008-02-29`, on `2026-02-28` the backend says adult and the frontend says underage — the form blocks and the request is never sent. `todayAsIsoDate` also uses browser-local time against a UTC backend. |
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

- Assurance reports: `docs/security/reports/SPRINT-1-{d9aad61,068d94f,3dec822}.md`
- Risk acceptance: `docs/security/DECISIONS.md` — `SEC-0001`
- Post-hoc review: `.claude/runtime/SPEC-0002/review-report.md`
- QA reports: `.claude/runtime/SPEC-{0018,0016,0002}/qa-report.md`, `docs/qa/QA-0001.md`
- Test books: `docs/tests/TB-{0018,0016,0001,0002}.md`
- Verification logs: `.claude/runtime/verify-release-sprint1.log`,
  `.claude/runtime/verify-e2e-headers.log`
- Owner decisions taken during closure: `.claude/runtime/SPRINT-1/decision-request.md`
- ADRs from this sprint: `docs/adr/ADR-0001-ledger-dependencies.md`,
  `docs/adr/ADR-0002-emulator-service-pattern.md`
