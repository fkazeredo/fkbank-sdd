---
name: close-sprint
description: Autonomous RELAY Sprint-closure gate — reconciles the delivered specs, brings up the integrated candidate, runs the heavy assembled-system closure battery (complete cross-spec journey, full Security Assurance, combined migrations, regression) distinct from the per-slice checks, auto-fixes in-scope defects and re-runs only the affected controls, records a short verdict, drafts concise product-facing release notes, and updates the Roadmap. It prepares and publishes nothing for release. Terminal at SPRINT_CLOSED or SPRINT_INCOMPLETE; idempotently resumable.
disable-model-invocation: true
---

# /close-sprint <sprint> [--resume]

Read `.claude/rules/workflow-conventions.md`, `docs/ROADMAP.md`, `.claude/rules/risk-model.md`,
`.claude/rules/security-gate.md`, `.claude/workflow-policy.yml`, and
`docs/security/SECURITY-ASSURANCE-TRACK.md`. Role: `orchestrator`.

This is the normal command after the operator has delivered the Sprint's specs individually. It
answers one question — **"were the committed Sprint outcomes delivered and verified as an integrated
whole?"** — by verifying the assembled system with a heavier, different battery than any single
`/deliver-spec` ran, and records the answer. It runs autonomously and never pauses for a routine
confirmation. It performs **no** release work: it drafts product-facing release notes but chooses no
version, prepares no release, and publishes nothing. It stops for a human only when a genuinely
unresolved material product or risk decision prevents an honest verdict.

## Closure flow

```text
/close-sprint <sprint>
→ reconcile the delivered specs' evidence and the final merged slice
→ bring up the integrated candidate (the develop tip with every Sprint spec merged)
→ run the heavy closure battery over the assembled system (§Closure battery)
→ auto-fix what is within the Sprint's scope, then re-run only the affected controls
→ record the short Sprint Assurance verdict
→ draft concise product-facing release notes (§Release-notes draft)
→ update docs/ROADMAP.md (§Durable record)
→ end at SPRINT_CLOSED or SPRINT_INCOMPLETE
```

## Responsibilities

1. Resolve the Sprint from `docs/ROADMAP.md` (accept `1` ≡ `Sprint 1` ≡ `S1`).
2. Read the Sprint Goal and the committed spec list.
3. Confirm each committed spec's exact delivery state (frontmatter status, merged SHA) and reconcile
   the final merged-but-unreconciled slice — the last slice has no subsequent `/deliver-spec` to
   trigger it: flip its frontmatter to `IMPLEMENTED` with `implemented_at` at the **real merge
   instant**, tick the `docs/ROADMAP.md` row (`Done` ☑ + `Completed`, Brasília time; frontmatter
   keeps the canonical UTC), and move `docs/exec-plans/active/PLAN-<id>.md` →
   `docs/exec-plans/completed/`. Never mark IMPLEMENTED before the merge.
4. Confirm every committed spec is merged to `develop`, and that its per-slice
   developer-verification, QA, CI, and PR-review evidence (per risk profile) belongs to the merged
   candidate or to an ancestor no later merged change invalidated. Reuse that evidence — do not
   mechanically repeat the per-slice battery.
5. Bring up the integrated candidate and run the heavy closure battery over the assembled system
   (§Closure battery), including the complete Security Assurance track (§Security Assurance).
6. Auto-fix in-scope defects and re-run only the affected controls (§Auto-fix).
7. Determine the outcome: Sprint Goal achieved or not; completed scope; incomplete scope; carry-over
   spec IDs; open blocking findings; the Sprint Assurance verdict.
8. Draft the product-facing release notes (§Release-notes draft).
9. Record the result in `docs/ROADMAP.md` (§Durable record).
10. End with exactly one concise terminal line.

## Closure battery (the assembled system, not the per-slice tests)

`/deliver-spec` verified each slice in isolation. `/close-sprint` does not mechanically repeat those
tests; it validates the system the slices now form together, over the integrated candidate — a
heavier, different battery:

- a complete user journey crossing all delivered specs;
- DAST against the real running stack;
- secrets scan;
- software-composition analysis and known-vulnerability checks;
- license checks;
- container-image checks;
- Dockerfile and Compose/IaC checks;
- the authorization matrix across slices;
- endpoint-exposure review;
- security headers and configuration;
- architecture and dependency rules;
- cross-cutting concurrency;
- resilience and behavior under failure;
- database integrity and the combined migration sequence;
- applicable financial controls;
- full regression on the integrated candidate.

Run it through the canonical wrappers — `tools/quality/verify-release` for the assembled functional
and regression suite, and the complete Security Assurance track for the security families
(§Security Assurance). Reuse an existing result only when it belongs to the exact integrated
candidate SHA; otherwise run it. Never let per-slice evidence stand in for a cross-spec interaction
it never exercised.

## Security Assurance (complete track)

Run the complete approved Security Assurance track over the integrated candidate whenever the Sprint
contains at least one R3/R4 slice (a Sprint of only R0–R2 slices with no relevant attack surface
records `SECURITY_NOT_APPLICABLE` with its justification). This is the **assembled-system** security
verification — DAST against the real stack, secrets, SCA and known vulnerabilities, licenses,
container images, Dockerfiles and Compose/IaC, the cross-slice authorization matrix, endpoint
exposure, and security headers — deliberately **different** from the per-slice engineering-level
security checks each `/deliver-spec` already ran; it is never a mechanical repeat of those. Do not
ask the operator to approve security execution. Because the heavy wrapper outlasts a subagent, the orchestrator runs
`tools/security/verify-assurance.ps1 -Target <candidate> -RequiresHeavy` (or the `.sh`) to
completion itself, then the independent `security-assurance-engineer` worker judges the produced
automated evidence, performs and records the threat-model, authorization/isolation, endpoint,
header, abuse-case and risk-specific manual families, and issues the verdict — wrapper success is
only `AUTOMATED_CONTROLS_PASS`, never a security verdict. The report must enumerate all eight
mandatory families from `docs/security/SECURITY-ASSURANCE-TRACK.md`, their status/evidence, and the
exact candidate SHA; a missing family is `BLOCKED`. Reuse a
complete-track verdict only when it belongs to the exact candidate SHA.

- `SECURITY_VERIFIED` or `SECURITY_NOT_APPLICABLE` ⇒ continue.
- `SECURITY_OBSERVATIONS` ⇒ continue automatically when every observation is Low/Medium, bounded
  by the approved policy, has an owner and deadline, and does not require accepting a material
  product, financial, privacy, or exploitability risk. Only that material residual risk requires
  one owner risk decision for the exact candidate.
- Any open High/Critical finding, `BLOCKED`, or a policy-bounded observation without an owner and
  deadline ⇒ the Sprint is not cleanly closed ⇒ `SPRINT_INCOMPLETE`.
- A genuinely unresolved risk-acceptance decision needed to claim the Sprint Goal ⇒ one consolidated
  Human Decision Request.

Never accept risk on the owner's behalf, and never record `SECURITY_VERIFIED` without the track
actually executed.

## Auto-fix (within scope only)

When the battery surfaces a defect within the Sprint's delivered scope, read and increment
`.claude/runtime/<sprint>/close-state.json:correction_cycles`. If it is already `1`, do not modify
the code again: record the defect as carry-over and end `SPRINT_INCOMPLETE`. Otherwise apply the
single automatic correction cycle the policy allows (`.claude/workflow-policy.yml` limits), persist
the increment before re-running only the affected controls — not the whole battery. A fix that would add
behavior, cross a bounded context, change a public contract, need a new dependency, or exceed the
correction budget is out of scope: stop with a Human Decision Request, or record it as carry-over
and `SPRINT_INCOMPLETE`. Every fix is verified before it counts; a bug fix begins with a failing
test.

## Release-notes draft (no release)

Write a concise, product-facing draft to `docs/release-notes/<sprint>.md`, where `<sprint>` is the
short Sprint label (e.g. `docs/release-notes/S1.md`). Drafting release notes neither authorizes nor
implies a release. Build it only from the committed specs, the Roadmap, the Changelog `[Unreleased]`
section, and the final integrated candidate; invent no capability. If the file exists, update it
idempotently instead of creating another.

Include only: the Sprint name and Goal; delivered capabilities; user-visible changes; relevant
operational or deployment changes; real known limitations that matter to users or operators; the
exact integrated candidate SHA; the Sprint Assurance verdict; and where the evidence lives.

Exclude: implementation diaries; token or compaction metrics; worker activity; command or test
counts; findings already corrected; internal workflow problems; speculative future work; a semantic
version the owner has not chosen; and any tag, publication, or `main`-merge instruction. Do not
describe the increment as already released. The future release process may reuse this draft, but
`/close-sprint` stops after preparing it — publication stays manual and owner-controlled.

## Durable record (minimal)

Update only the Sprint's closure fields in `docs/ROADMAP.md`, in sync with the spec frontmatter that
is the source of truth: `Status` (`CLOSED`/`INCOMPLETE`), `Closed` (canonical UTC, only when
closed), `Goal` (`ACHIEVED`/`NOT_ACHIEVED`), `Carry-over` (spec IDs or `none`), `Blocking` (finding
IDs or `none`). The Roadmap fields plus the release-notes draft are the durable record — write no
long closure report, retrospective, process analysis, or speculative recommendation.

## This command never

Chooses or derives a semantic version; edits Maven or npm versions; creates a release branch;
consolidates a versioned Changelog section; opens a Pull Request to `main`; waits for a `main` merge;
creates or pushes a Git tag; publishes a GitHub Release; creates a post-release synchronization
branch or Pull Request; describes the increment as already released; composes or returns `/release`;
or claims that every closed Sprint must produce a release. It never merges, pushes a protected
branch, marks an unmerged spec delivered, or accepts security risk. It never instructs the operator
to invoke `/release`, `/security-assurance`, `/reconcile-workflow`, `/workflow-status`, `/qa`,
`/review-pr`, or any other internal or recovery phase.

Reconciling a merged spec, bringing up the integrated candidate, reusing or running verification and
the Security Assurance track, auto-fixing an in-scope defect, updating the Roadmap, drafting the
release notes, and creating the local closure commit are ordinary autonomous execution. Record the
tested integrated code SHA as `candidate_sha` and the later documentation commit as
`closure_commit`; they are distinct values. Pushing a branch or opening a Pull Request is optional
repository housekeeping and is never required to claim the Sprint closure verdict.

## Terminal states and resume

`SPRINT_CLOSED` (Goal achieved; every committed spec merged; the assembled-system battery and
Security Assurance pass or carry only recorded non-blocking observations) · `SPRINT_INCOMPLETE`
(incomplete scope, carry-over, an open blocking defect, or an open High/Critical finding) ·
`HUMAN_DECISION_REQUIRED` (an unresolved material product or risk decision blocks an honest verdict)
· `BLOCKED` (closure cannot be verified — fill `.claude/templates/block-report.md`).

Idempotent: a re-run reuses an assurance/battery result on the exact candidate SHA, the recorded
ROADMAP fields, the reconcile state, and the existing release-notes draft; it never duplicates them.
Resume with the same `/close-sprint <sprint> --resume`.

Print concise progress while running. End with exactly one line:
`RELAY STATE: <state> | evidence: docs/ROADMAP.md, docs/release-notes/<sprint>.md | resume: <same-close-sprint-command-or-none>`.
