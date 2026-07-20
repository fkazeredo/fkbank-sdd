# <PROJECT> — CLAUDE.md

<!-- Greenfield template. Copy to the repo root as CLAUDE.md and replace every <PLACEHOLDER>.
     Keep this file SHORT: it is permanent context loaded into every session. Everything else is
     read on demand. See CLAUDE.example-fkbank.md for a fully filled example. -->

<PROJECT> is <one-paragraph product description: what it is, who it serves, the main capabilities>.
Backend: <e.g. Java 21 / Spring Boot / Spring Modulith (modular monolith)>. Frontend: <e.g. Angular>.
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

- 1 active implementation · 0 parallel implementers · agent teams disabled · background tasks disabled.
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

`/close-sprint` owns the whole post-delivery path: final reconcile, verification, applicable
security assurance, closure evidence, release preparation, release PR, waiting for the protected
`main` merge, tag/GitHub Release, and the post-release sync PR. It composes `/release` internally;
the operator is never told to invoke `/release` as the next routine Sprint step. If a protected
branch merge requires the human, resume with the same `/close-sprint <sprint> --resume` command.
`/deliver-sprint` is only a convenience for the rare case where the operator wants every committed
spec delivered in one loop; after the last merge it automatically executes this same closeout.

- Every skill begins by reading `.claude/rules/workflow-conventions.md`.
- Risk drives process (R0–R4): see `.claude/rules/risk-model.md`. Every versioned change
  has a spec; Fast Track (R0/R1) uses a Light Spec and skips `/design-slice` and
  session-level `/qa`; Standard (R2) runs the full relay; Critical
  (R3/R4) adds a durable plan, `/review-pr` and extra human gates.

## Invariants (numbered — referenced across docs; do not renumber)

<!-- Invariants 3, 4, 6 (Git half), 7 and 8 are workflow-generic: keep them as written, because
     .claude/rules/* and the skills reference them BY NUMBER. Invariants 1, 2 and 5 are the
     domain slots — replace them with the rules your domain cannot violate, and keep the count. -->

1. **<Domain invariant>.** <The single most important rule of your domain — the one whose
   violation is a defect by definition. Name the module that owns it.>
2. **<Domain invariant>.** <The second: typically the correctness rule for your critical
   operations, e.g. idempotency on state-changing routes, with the test that must be born
   with them.>
3. **No pending questions in code.** No behavior covered by a pending Open Question gets
   implemented. Material doubt stops the work (invariant 8).
4. **Evidence over self-declaration.** Every conclusion points to a command, test, diff,
   artifact or observed behavior. "Looks correct" is not evidence.
5. **<Domain invariant>.** <The third: typically the precision/consistency rule for your core
   value objects — units, rounding, time zones, identifiers.>
6. **Layered prohibitions** (checked by ArchUnit, hooks and permissions; protected Git
   operations require server-side GitHub rulesets): under `<root.package>`, the only root
   packages are `domain`, `application`, and `infra`; domain entities never cross a bounded-context
   boundary nor become JSON; `domain` does not import <framework>; `application` and `infra` depend
   on `domain` but never on each other, and `domain` depends only on `domain` (never on
   `application` or `infra`); domain bounded contexts are flat (no grouping subpackages) and
   carry ubiquitous-language names — hexagonal + pragmatic DDD, no use-case/interactor classes;
   <any domain-specific access prohibition>;
   the agent never merges, never pushes to
   `main`/`develop`, never force-pushes, never moves or deletes a published tag, never
   approves its own PR.
7. **Independent gates use isolated workers.** The orchestrator automatically invokes at
   most one foreground `qa-engineer`, `pr-reviewer`, or `security-assurance-engineer` worker
   with a bounded contract and dedicated write paths. Workers never implement production
   code, spawn other workers, merge, or approve risk. Implementation concurrency remains one.
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

- Operator communication during sessions: **<pt-BR>**.
- Every repository artifact is written in **en-US**: source, tests, identifiers, comments,
  commits, Pull Requests, specifications, plans, reports, and documentation.
- Only manuals under `docs/manual/**` may have paired `<pt-BR>` and `en-US` editions. The root
  `README.md` is English-only. Localized files outside `docs/manual/**` are forbidden.
- Superseded material belongs in Git history, not in the active documentation tree, and creates
  no language exception.

## Reading map (budgeted reading — read only what the task names)

- Product truth: `docs/PRODUCT.md` · Domain truth: `docs/DOMAIN.md` (module map, <core domain
  reference tables>, invariants, cross-cutting rules)
- Engineering rules: `docs/ARCHITECTURE.md`
- The change's truth: `docs/specs/SPEC-<id>-*.md` — a slice reads ONLY the sections its
  `reading_list` names
- Slice state: `.claude/runtime/<id>/state.json` · Spec state: the spec's frontmatter (canonical,
  UTC) · Delivery tracker: `docs/ROADMAP.md` sprint tables (`Done`/`Started`/`Completed`, shown in <local> time)
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

Top-level commands continue across internal transitions automatically. Isolated workers are
foreground components, not operator-managed sessions. Each transition persists evidence before
the next begins. If execution cannot reach a terminal or external-wait state, write `BLOCKED` + a Workflow Block Report
(`.claude/templates/block-report.md`) and stop. Never keep trying past the limits in
`.claude/rules/workflow-conventions.md`.
