# FKBANK — Roadmap

> **RECONSTRUCTED DOCUMENT — owner review required.** Rebuilt from each spec's `sprint`
> field and the follow-through notes the surviving specs cite. Clean start: nothing is
> implemented in this repository yet — every slice below is pending, including the two
> that were implemented in the previous (deleted) repository (0016, 0001 — their specs
> keep the historical traceability as a reimplementation guide). The walking skeleton
> itself, raised in the previous repository by an ad-hoc `/bootstrap` command, is now a
> first-class spec (SPEC-0018) and runs first.

Rhythm assumption: 20h+/week. Rule: real, demonstrable customer value no later than
Sprint 2 — incremental without "shipping anything and calling it done". The pilot of the
RELAY workflow is Sprint 1 itself, instrumented automatically (health checkpoint after 2
slices; formal go/no-go after the fourth delivered slice, which may occur in Sprint 2, and
must include ≥1 R2). This metric window does not change Sprint scope.

Model tier per `.claude/workflow-policy.yml` (executable authority): **sonnet** is the
default for every phase at R0–R2 and for `/pr`/`/release`/`/workflow-status` at any risk;
**opus** is required for `/spec`, `/design-slice`, `/build`, `/qa` at R3–R4 (every opus
`/design-slice` runs at `xhigh` effort, other phases default to `high`). `/spec`,
`/design-slice`, `/build` and `/pr` all run in the SAME continuous session as
`/deliver-spec <id>` — Ultracode may freely orchestrate xhigh dynamic workflows, agent teams,
parallel work, and background execution under invariant 7
and the command does not pause to let the operator switch models mid-flow. The table's
**Model** column is therefore what to set with `/model` *before* typing `/deliver-spec <id>`
— it is not a per-phase allocation. The one exception: the `qa-engineer` and `pr-reviewer`
isolated workers are model-selected by the orchestrator per this same risk rule,
independent of whatever the operator is running as. Lowering an R3/R4 phase to sonnet
weakens a gate and requires a recorded human decision; raising an R2 spec's whole session to
opus is available at operator discretion (see *Model allocation notes* below) but is not the
policy default and is not a per-phase split `/deliver-spec` can execute unattended.

Each sprint table doubles as the **delivery tracker**: `Done` (☐ pending · ☑ merged to
`develop`), `Started` (the spec's `owner_approved_at`) and `Completed` (its `implemented_at`,
stamped from the real merge instant by the automatic reconcile-sweep at the start of the next
`/deliver-spec`, or by `/close-sprint` / `/release` for the last slice). Dates use **Brasília time (BRT, UTC-3) in
`DD/MM/YYYY HH:MM:SS`** — the presentation edge; the spec frontmatter keeps the canonical UTC
instant. The frontmatter (`status`/`owner_approved_at`/`implemented_at`) stays the source of
truth; these columns mirror it: `/deliver-spec` writes the new slice's `Started` and, in the same run,
auto-reconciles the previous merged slice's `Done` ☑ + `Completed` from the real merge instant;
`/close-sprint` and `/release` sweep the last slice; `/reconcile-workflow` remains the manual fallback
for the final slice or any drift. See `.claude/rules/workflow-conventions.md` §Spec state.

## Sprint 1

**Outcome:** Walking skeleton observable from day one; the accounting heart proven by race
tests; a person opens an account and logs in.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0018 — walking skeleton | R3 | opus | — | — | Raises the entire baseline every later slice assumes: three-root structure with ArchUnit/Modulith gates, default-deny authorization (embedded AS + PKCE), Flyway/Postgres, Angular shell, E2E stack, real CI — authorization surface ⇒ R3 |
| 2 | ☐ | SPEC-0016 — observability baseline | R2 | sonnet | — | — | Ships only the observability mechanism (MDC propagation, structured logs) — no money or invariant surface touched (design-hardening option: see *Model allocation notes*); depends on SPEC-0018 |
| 3 | ☐ | SPEC-0001 — ledger core | R3 | opus | — | — | Owns every ledger invariant: append-only postings, 4-decimal `Money` math, `FOR UPDATE` ascending-id race test, reversal-at-most-once |
| 4 | ☐ | SPEC-0002 — sign-up & account | R3 | opus | — | — | Same-CPF race under real persistence, idempotent-by-CPF onboarding through bureau `delay`/`duplicate-webhook`, OIDC + PII |

## Sprint 2

**Outcome:** Real value — money enters two ways, the statement proves it, the PIN guards
it, money moves between customers.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0003 — statement & receipts | R2 | sonnet | — | — | Read-only ledger projection, no money write path (watchlist: pagination under concurrent inserts) |
| 2 | ☐ | SPEC-0004 — deposit boleto | R3 | opus | — | — | Settle-webhook vs expiry-job race to exactly one terminal status; idempotent settlement by boleto id; HMAC verification |
| 3 | ☐ | SPEC-0005 — inbound PIX | R3 | opus | — | — | Idempotency by end-to-end id proven concurrently and on 24h replay; HMAC 401 before anything posts |
| 4 | ☐ | SPEC-0006 — transaction PIN | R3 | opus | — | — | Gates every money route: parallel wrong-PIN attempts must converge to exactly one `PIN_LOCKED`, no lost update |
| 5 | ☐ | SPEC-0007 — internal transfer | R3 | opus | — | — | Classic double-spend test: two transfers draining one balance under `FOR UPDATE`; byte-identical `Idempotency-Key` replay |

## Sprint 3

**Outcome:** Full PIX experience; the app talks back on every movement.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0008 — PIX complete | R3 | opus | — | — | Densest slice in the set — SPI-timeout saga, outbound balance race, cumulative refunds, DICT lifecycle, static QR (`/design-slice` should evaluate a split) |
| 2 | ☐ | SPEC-0015 — notifications | R2 | sonnet | — | — | Downstream outbox consumer that displays but never computes or posts money; best-effort by contract (BR-4), decoupled from the money path (design-hardening option: see *Model allocation notes*) |

## Sprint 4

**Outcome:** Pay anything; spend online.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0009 — boleto payment | R3 | opus | — | — | `fail-after-confirm` compensation via contra-posting; idempotent under sequential replay and concurrent race |
| 2 | ☐ | SPEC-0010 — virtual debit card | R3 | opus | — | — | Block vs in-flight authorization race; two authorizations racing for the last funds; p99 < 300ms budget |

## Sprint 5

**Outcome:** Money grows; credit with honest CET.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0011 — yield boxes | R3 | opus | — | — | Idempotent-by-(box, date) yield batch, 4-decimal internal accumulation, concurrent add/withdraw race |
| 2 | ☐ | SPEC-0012 — personal loan | R3 | opus | — | — | Heaviest math in the product: Price amortization + CET with the financed flat fee, idempotent-by-(loan, installment) charging |

## Sprint 6

**Outcome:** Self-controlled limits; data rights; someone finds out when the ledger stops
proving itself.

| # | Done | Spec | Risk | Model | Started (BRT) | Completed (BRT) | Why |
|---|---|---|---|---|---|---|---|
| 1 | ☐ | SPEC-0013 — limits engine | R3 | opus | — | — | Pre-ledger gate on every money route; window-sum accounting across the 20:00–06:00 boundary; clock-controlled 24h anti-coercion delay |
| 2 | ☐ | SPEC-0014 — LGPD | R4 | opus | — | — | Irreversible anonymization preserving append-only postings under legal retention; irreversible steps are performed by the human |
| 3 | ☐ | SPEC-0017 — dashboards & alerting | R2 | sonnet | — | — | Config-as-code dashboards/alerts plus a synthetic-incident drill — operational, not reasoning-bound |

## Follow-through notes (cross-slice contracts the specs cite)

- SPEC-0018 delivers the security baseline (default-deny + embedded Authorization Server +
  PKCE) whose `401`/`403` events SPEC-0016's `authorization failures` counter counts;
  springdoc/OpenAPI (dependency, UI, snapshot, drift gate) stays wholly in SPEC-0016
  (SPEC-0018 DL-0004). SPEC-0002 replaces the skeleton's dev-seeded credential journey
  with real onboarding-issued credentials.
- `fkbank.ledger.postings` counter (rail-tagged) is created BY SPEC-0001 together with the
  posting use case — SPEC-0016 deliberately ships only the mechanism (DL-0008).
- The outbox queue-depth gauge samples the event-publication registry SPEC-0001 introduces.
- correlationId → async propagation ships as a mechanism in SPEC-0016 (MDC-propagating
  TaskDecorator, mechanism-level test); SPEC-0015 — the first real outbox consumer —
  re-confirms it end to end.
- SPEC-0013 (limits) plugs its change-notifications into SPEC-0015 at S6.
- Distributed tracing stays OFF until SPEC-0017 deliberately re-enables and re-tests it
  (see SPEC-0016's sequencing note about spring-modulith-starter-insight × MockMvc).

## Model allocation notes

Order, model and per-slice rationale live in the per-sprint tables above. This section
holds only what does not fit there: escalation triggers, the recorded risk-anchor calls,
and the discretionary design-hardening path for the two trickiest R2 designs.

### Evidence-based escalation watchlist (R2 slices)

- **SPEC-0003** — escalate `/build`/`/qa` only if the Testcontainers keyset-pagination test
  (no duplicated/skipped lines under concurrent inserts) or the running-balance derivation
  fails twice on the same reasoning error with context confirmed complete.
- **SPEC-0015** — escalate only if the exactly-once idempotent-consumer guarantee under
  event redelivery (or event-time burst ordering) keeps failing after the outbox contract
  and redelivery semantics are fully in context.
- **SPEC-0016** — escalate only if concurrent-request correlationId isolation shows
  cross-thread MDC contamination persisting after the `TaskDecorator` wiring is verified
  complete; a byte-level OpenAPI drift mismatch is a context/gate problem, not a model one.
- **SPEC-0017** deliberately has no watch entry: its hard parts (incident drill, alert-flap
  tuning) are operational, so a failure there points at the environment or a gate, not at
  model capability.

### Discretionary design-hardening (SPEC-0015, SPEC-0016)

Both designs are genuinely trickier than their R2 peers: 0016 must get cross-thread MDC
propagation through an async `TaskDecorator` right on the first pass (the spec's own
sequencing note already flags `spring-modulith-starter-insight` × MockMvc as a documented
gotcha), and 0015 must fix outbox-consumer dedup identity, redelivery and event-time
ordering before any code is written. That is a real signal — but `/design-slice` cannot
delegate material decisions and remains in the same top-level `/deliver-spec` operation, so
there is no automatic way to run design on a stronger model while build stays on sonnet.
If extra design rigor is wanted ahead of a failure (rather than waiting for the watchlist
trigger above), the only correct mechanism is manual: run `/model opus`, invoke
`/design-slice <id>` on its own to `PLAN_APPROVED`, then `/model sonnet` and resume with
`/deliver-spec <id> --resume` for build/QA/PR. This is operator discretion, not a policy
requirement, and costs more than the R2 default — it is not what the table's Model column
assumes for a plain `/deliver-spec <id>` run.

### Risk-anchor decisions (2026-07-17)

The risk-model anchor lists events, migration, concurrency, personal data, and DOMAIN
invariants as R3 drivers; two R2 specs brush against that list. The conflict was presented
to the owner with options and a recommendation; the owner delegated the call, and the
decisions are recorded here:

- **SPEC-0018 is R3** — the slice introduces the authorization baseline (default-deny,
  embedded Authorization Server, OIDC + PKCE) that every later slice inherits; the risk
  model lists authorization as an R3 driver and mandates going up when in doubt
  (SPEC-0018 DL-0002).

- **SPEC-0003 stays R2** — it implements M3 ("statement is derived") by construction:
  read-only over the ledger, no balance write path; the worst failure mode is misdisplayed
  data, recoverable, with no money moved. Watchlist entry above covers the residual risk.
- **SPEC-0015 stays R2** — the consumer is best-effort and decoupled from the money path
  by contract (BR-4); its failure mode is a lost or duplicated notification, never a wrong
  balance. Watchlist entry above covers the residual risk.
- **SPEC-0008 split note** — `/design-slice` must evaluate a split before betting on model
  strength: the spec stacks a timeout saga, a balance race, cumulative refunds, DICT
  lifecycle, and QR rendering — several slice-too-big signals from the decision ladder.

## Release cadence

One release per sprint after `/close-sprint`, then `/release` (internal pilot/pre-release — see the security gate
in `.claude/rules/security-gate.md`; a production release additionally requires the
approved security assurance track). SemVer set at Prepare; development on `-SNAPSHOT`.
