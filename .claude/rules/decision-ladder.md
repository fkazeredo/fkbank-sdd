# Decision Ladder (anti-over-engineering — mandatory)

Order (never skip a rung):
1. **Necessity / YAGNI** — explicit need? observable result? in scope? already served?
   speculative? An explicit requirement is NEVER silently dropped: if you judge it useless
   or redundant ⇒ HUMAN_DECISION_REQUIRED.
2. **Existing project reuse** — components, use cases, validators, mappers, repositories,
   value objects, Angular components/directives/pipes/guards/interceptors, design tokens,
   scripts, events, fixtures, internal APIs. Proportional search. Do NOT force reuse that
   increases coupling, mixes ownership, needs confusing conditionals, violates a bounded
   context or creates an improper dependency.
3. **Standard library / primary framework** — Java: java.time, java.nio.file, collections,
   records, Bean Validation, Spring, Spring Data, JUnit. Angular/TS: language APIs, Intl,
   URL APIs, forms, router, HttpClient, pipes, validators, signals/RxJS per house pattern,
   installed CDK.
4. **Browser / platform capability** — semantic HTML, native validation, CSS, browser APIs,
   the database, the JVM, the deploy platform. Reject native only for a concrete reason
   (compatibility, accessibility, localization, UX, behavior, requirement).
5. **Already-installed dependency** — check pom.xml / package.json first.
6. **Simplest clear solution** — prefer the smallest solution that is clear, correct,
   secure, testable and consistent with the project. No illegible one-liners, no abstraction
   that only hides complexity.
7. **Minimum new code** — only the approved behavior; existing patterns; small interfaces;
   proportional tests; no internal frameworks; no anticipation.
8. **New external dependency = last resort** — requires human approval with the
   `.claude/templates/new-dependency.md` record filled.

## Where it executes
- `/spec`: rung 1 per acceptance criterion + duplicate-capability check. No deep code search.
- `/design-slice`: the FULL ladder, once, recorded as `## Decision Ladder` in plan.md (R2+),
  plus the implementation-fit gate (§Implementation-fit gate).
- `/build`: re-opens the ladder ONLY on deviation (unplanned component, dependency,
  abstraction, ownership change, new contract, unmapped reuse). Material deviation ⇒
  HUMAN_DECISION_REQUIRED.
- `/pr` and `/review-pr`: CHECK the recorded ladder against the actual diff. Never repeat
  the broad search.
- R0/R1: apply mentally; one short summary line in the result.

## Implementation-fit gate (canonical home — every other file references this)
The binding check that an approved spec is deliverable in one clean build session. It runs near the
start of delivery, before production code: R2+ produces it in `/design-slice` (the old one-session
fit check, upgraded to this formal gate); R0/R1 (no `/design-slice`) run it inline in `/deliver-spec`
before `/build`. The result is written to `state.json.fit` and CONSUMED by `/deliver-spec` (binding):
before a slice may enter BUILDING, orchestration runs `tools/workflow/check-slice-gate <id> fit`,
which passes only when `fit == FIT`.

The eight signals:
1. More than three independent vertical behaviors.
2. More than two bounded contexts receive behavioral changes.
3. Backend, frontend, migration, and external integration are all changed by the same spec.
4. A new emulator or external service is introduced together with a complete product journey.
5. A structural or architectural change is combined with product behavior.
6. A new cross-context contract is combined with full UI delivery.
7. The implementation is expected not to fit in one clean build context/session.
8. One or more acceptance criteria would receive their first real end-to-end execution only during
   independent QA.

Classification:
- **TOO_LARGE** — at least **three** signals present, OR any **single** condition that makes
  one-session implementation clearly unsafe even with fewer than three (e.g. a destructive migration
  combined with a product journey; multiple money-moving behaviors with concurrency requirements).
- **HUMAN_DECISION_REQUIRED** — the information needed to classify is genuinely ambiguous.
- **FIT** — otherwise.

Binding consequences: FIT ⇒ continue automatically. TOO_LARGE ⇒ do not begin implementation; generate
the split proposal and stop for one owner confirmation (`human-decision-gate.md` §Oversized spec).
Ambiguous ⇒ stop with exactly one concrete Human Decision Request. **A narrative claim (e.g. "no
customer value when split") NEVER overrides the fit gate.** Child specs may be independently
integrable and testable without being independently releasable to users. No rigid file-count limit —
the question is cohesion, verifiability and one-session completion.
