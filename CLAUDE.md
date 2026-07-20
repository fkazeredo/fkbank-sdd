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

- 1 active implementation · 0 parallel implementers · agent teams disabled · background tasks disabled.
- RELAY is an evidence-driven state machine, not a human ceremony. `/deliver-spec` and
  `/deliver-sprint` advance phases automatically and idempotently until a real terminal or
  external-wait state. Granular skills remain recovery/debug entry points:

```text
Normal: /deliver-spec <id> | /deliver-sprint <sprint> | /close-sprint <sprint>
Internal transitions: auto-reconcile prior merged slice → spec → design → build → QA worker → PR/CI → review worker → merge wait
Recovery: /spec /design-slice /approve-plan /build /qa /pr /review-pr /fix-pr
Support: /release /hotfix /adr /spike /impact · Drift/fallback (recovery only): /workflow-status /reconcile-workflow
```

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
7. **Independent gates use isolated workers.** The orchestrator automatically invokes at
   most one foreground `qa-engineer`, `pr-reviewer`, or `security-assurance-engineer` worker
   with a bounded contract and dedicated write paths. Workers never implement production
   code, spawn other workers, merge, or approve risk. Implementation concurrency remains one.
8. **Human Decision Gate.** No silent assumptions, no silent conflict resolution, no
   material decision without human visibility. Stop with a `decision-request.md`
   (`.claude/rules/human-decision-gate.md`).

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

Top-level commands continue across internal transitions automatically. Isolated workers are
foreground components, not operator-managed sessions. Each transition persists evidence before
the next begins. If execution cannot reach a terminal or external-wait state, write `BLOCKED` + a Workflow Block Report
(`.claude/templates/block-report.md`) and stop. Never keep trying past the limits in
`.claude/rules/workflow-conventions.md`.
