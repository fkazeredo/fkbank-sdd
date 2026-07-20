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
  plus the one-session fit check.
- `/build`: re-opens the ladder ONLY on deviation (unplanned component, dependency,
  abstraction, ownership change, new contract, unmapped reuse). Material deviation ⇒
  HUMAN_DECISION_REQUIRED.
- `/pr` and `/review-pr`: CHECK the recorded ladder against the actual diff. Never repeat
  the broad search.
- R0/R1: apply mentally; one short summary line in the result.

## Slice-too-big signals (split before derived-plan validation)
More than three independent vertical behaviors · structural change + feature in one slice ·
new cross-context contract + full UI · migration + backfill + behavior together · multiple
external integrations · independent rollout plans · parts releasable/testable separately ·
expected compaction during /build. No rigid file-count limit — the question is cohesion,
verifiability and one-session completion.
