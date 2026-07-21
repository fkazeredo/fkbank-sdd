# FKBANK — CLAUDE.md

FKBANK is a Nubank-style digital banking **web application**, treated as a real product:
current account, PIX (fully emulated, high fidelity), boleto (cash-in and payment), virtual
debit card, yield boxes (savings), personal loan (credit), user profile and transaction PIN.
Backend: Java 21 / Spring Boot / Spring Modulith (modular monolith). Frontend: Angular.
This file is permanent context — keep it short; everything else is read on demand.

## Rule 0 — No over-engineering (fundamental, outranks style preferences)

The simplest solution that is correct, secure, clear and testable wins. No speculative
abstraction, no ceremony, no pattern, layer or dependency without a present, observable need
(YAGNI). Our design is **hexagonal + pragmatic DDD** — *pragmatic*: behavior on the domain
model, thin adapters, flat bounded contexts, ubiquitous-language names, no interactor/use-case
classes. When in doubt, do less and delete. The anti-over-engineering ladder is
`.claude/rules/decision-ladder.md`.

## Workflow: RELAY

Development follows the **RELAY** workflow. Its authority is this file,
`.claude/workflow-policy.yml`, `.claude/rules/`, and the invoked skill. The human-readable
index is `docs/workflow/README.md`; it does not override executable contracts. Non-negotiable:

- Ultracode orchestration is autonomous but controlled. At xhigh effort, Claude may create and
  manage dynamic workflows, agent teams, subagents, parallel implementers, and background tasks in
  whatever topology it judges most effective — subject to safe ownership + integration (invariant 7):
  disjoint file/module partitions, one accountable integrator, shared resources serialized. That is a
  correctness constraint, not a repository-level numeric limit and not operator supervision of
  internal orchestration.
- RELAY is an evidence-driven state machine, not a human ceremony. `/deliver-spec`,
  `/close-sprint`, and the optional `/deliver-sprint` advance phases automatically and
  idempotently until a real terminal or external-wait state. The operator never babysits
  internal phase commands. Granular skills remain recovery/debug entry points:

```text
Normal: /deliver-spec <id> ... then /close-sprint <sprint>
Optional whole-Sprint loop: /deliver-sprint <sprint>
Internal transitions: auto-reconcile prior merged slice → spec → design → build → QA worker → PR/CI → review worker → merge wait
Recovery: /spec /design-slice /approve-plan /build /qa /pr /review-pr /fix-pr
Support: /release /hotfix /adr /spike /impact · Drift/fallback (recovery only): /workflow-status /reconcile-workflow
```

`/close-sprint` is an autonomous Sprint-closure gate. It answers one question — *were the committed
Sprint outcomes delivered and verified as an integrated whole?* — by reconciling the final merged
slice, bringing up the integrated candidate, and running a heavier, different battery than any
single `/deliver-spec` ran over the assembled system: the complete cross-spec journey, the full
Security Assurance track, combined migrations, cross-cutting concurrency and resilience, and full
regression. It auto-fixes in-scope defects and re-runs only the affected controls, records a short
verdict and a minimal `CLOSED`/`INCOMPLETE` result in `docs/ROADMAP.md`, and drafts concise
product-facing release notes at `docs/release-notes/<sprint>.md`. It performs **no** release work:
drafting notes neither authorizes nor implies a release, and it never derives a version, prepares a
release, waits for a `main` merge, tags, publishes a GitHub Release, opens a sync PR, or
composes/returns `/release`. Releasing is a separate, owner-driven decision — the operator runs
`/release` manually when they choose to release, and handles protected-branch merges, tags, and
GitHub Releases by hand. `/deliver-sprint` is only a convenience for the rare case where the
operator wants every committed spec delivered in one loop; after the last merge it automatically
runs this same closure gate.

- Every skill begins by reading `.claude/rules/workflow-conventions.md`.
- Risk drives process (R0–R4): see `.claude/rules/risk-model.md`. Every versioned change
  has a spec; Fast Track (R0/R1) uses a Light Spec and skips `/design-slice` and
  session-level `/qa`; Standard (R2) runs the full relay; Critical
  (R3/R4) adds a durable plan, `/review-pr` and extra human gates.

## Invariants (numbered — referenced across docs; do not renumber)

1. **Append-only ledger.** A posting is immutable; a fix is a contra-posting. Only the
   `ledger` module touches balances (M1/M5).
2. **Idempotent money.** Every money-moving route enforces `Idempotency-Key` (M2) and is
   born with a deterministic race test and a replay test.
3. **No pending questions in code.** No behavior covered by a pending Open Question gets
   implemented. Material doubt stops the work (invariant 8).
4. **Evidence over self-declaration.** Every conclusion points to a command, test, diff,
   artifact or observed behavior. "Looks correct" is not evidence.
5. **Money discipline.** Money is the `Money` value object: 4 internal decimals, rounding
   to 2 (half-up) only at the edge. Never money arithmetic in the frontend.
6. **Layered prohibitions** (checked by ArchUnit, hooks and permissions; protected Git
   operations require server-side GitHub rulesets): under `com.fkbank`, the only root packages
   are `domain`, `application`, and `infra`; domain entities never cross a bounded-context
   boundary nor become JSON; `domain` does not import Spring; `application` and `infra` depend
   on `domain` but never on each other, and `domain` depends only on `domain` (never on
   `application` or `infra`); domain bounded contexts are flat (no grouping subpackages) and
   carry ubiquitous-language names — hexagonal + pragmatic DDD, no use-case/interactor classes;
   no balance access outside the ledger;
   the agent never merges, never pushes to
   `main`/`develop`, never force-pushes, never moves or deletes a published tag, never
   approves its own PR.
7. **Ultracode autonomy, accountable results.** Claude owns its internal orchestration and may use
   xhigh dynamic workflows, agent teams, subagents, parallel work, and background execution without
   repository-imposed numeric limits or operator babysitting — but parallelism is *controlled*, not
   unrestricted: every writing workstream has an explicit, disjoint file/module ownership partition,
   two workers never edit the same production module at once, shared mutable resources (database,
   Compose project, emulator state, ports, runtime state, contract snapshots) are serialized unless
   isolated, and one accountable integrator reviews and integrates every workstream before developer
   verification. No worker declares another worker's unintegrated changes verified. Claude must still
   integrate a coherent result, preserve evidence and scope, and respect role-specific write
   boundaries. Independent `qa-engineer`, `pr-reviewer`, and `security-assurance-engineer` verdicts
   remain independent in responsibility; orchestration mechanics must not turn them into
   self-approval.
8. **Human Decision Gate.** No silent assumptions, no silent conflict resolution, no
   material decision without human visibility. Stop with a `decision-request.md`
   (`.claude/rules/human-decision-gate.md`).
9. **Behavioral domain model.** Aggregates and value objects are real classes that protect valid
   state and expose ubiquitous-language behavior. Do not default domain types to Java `record`,
   public setters, generated all-arguments constructors, or data-only accessors. A `record` is
   appropriate for immutable messages and boundary data (commands, events, DTOs, projections) and
   for a value object only when it still owns meaningful validation/behavior and cannot represent
   invalid state. Lombok is approved to remove mechanical boilerplate, never to generate an anemic
   model or bypass invariants; prefer targeted annotations and keep construction rules explicit.

## Language

- Operator communication during sessions: **pt-BR**.
- Every repository artifact is written in **en-US**: source, tests, identifiers, comments,
  commits, Pull Requests, specifications, plans, reports, and documentation.
- Only manuals under `docs/manual/**` may have paired `pt-BR` and `en-US` editions. The root
  `README.md` is English-only. Localized files outside `docs/manual/**` are forbidden.
- Superseded material belongs in Git history, not in the active documentation tree, and creates
  no language exception.

## Reading map (budgeted reading — read only what the task names)

- Product truth: `docs/PRODUCT.md` · Domain truth: `docs/DOMAIN.md` (module map, chart of
  accounts, money flows, invariants M1–M5, cross-cutting rules)
- Engineering rules: `docs/ARCHITECTURE.md`
- The change's truth: `docs/specs/SPEC-<id>-*.md` — a slice reads ONLY the sections its
  `reading_list` names
- Slice state: `.claude/runtime/<id>/state.json` · Spec state: the spec's frontmatter (canonical,
  UTC) · Delivery tracker: `docs/ROADMAP.md` sprint tables (`Done`/`Started`/`Completed`, shown in Brasília time)
- Durable decisions: the spec's Decision log · `docs/adr/` · plans in `docs/exec-plans/`

## Git

GitFlow: `main`, `develop` (protected — PR only, enforced by GitHub rulesets),
`feature/*`, `bugfix/*`, `release/*`, `hotfix/*`, `chore/*`. The version changes only at
release (Prepare step); development runs on `-SNAPSHOT`; a slice never changes the version.
Tags `vX.Y.Z` exist only after a human merge to `main`.

Pushing a work branch (`feature/*`, `bugfix/*`, `chore/*`, `hotfix/*`, `release/*`) to `origin`
and opening its Pull Request against `develop` (or `main` for a release) is autonomous,
pre-authorized RELAY execution — `/pr`, `/release`, `/hotfix` do this without asking; it is not
a confirmation gate. The only limits are invariant 6's absolute prohibitions (push to
`main`/`develop` directly, force-push, merge, move/delete a published tag, self-approve a PR) —
those never require a human's "go ahead" because no instruction ever lifts them; everything
short of them is ordinary delivery, not a risky action to pause on.

## Execution

Top-level commands continue across internal transitions automatically. Ultracode owns all
workflow, agent-team, subagent, parallel, and background orchestration without operator management
or numeric limits — controlled autonomy: safe ownership + integration (invariant 7), not an
unmanaged free-for-all. Each transition persists evidence before
the next begins. If execution cannot reach a terminal or external-wait state, write `BLOCKED` + a Workflow Block Report
(`.claude/templates/block-report.md`) and stop. Never keep trying past the limits in
`.claude/rules/workflow-conventions.md`.
