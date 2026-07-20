# DEP-0001 — Dependencies introduced by the ledger core

Three dependencies land with SPEC-0001. None is a free choice made during design: each is
required by an already-approved document. The records exist so the reasoning outlives the
delivery.

---

## 1. jqwik (test scope)

- **Problem:** Money rounding must hold across the whole input space, not on the handful of
  examples a developer happens to think of. The spec's sixth acceptance criterion requires
  half-up rounding to 2 decimals with 4 internal decimals preserved, and requires that any
  N-way split sums exactly back to the original.
- **Standard alternatives:** JUnit 5 parameterized tests with hand-picked cases. They prove the
  cases chosen and nothing about the ones not chosen — which is exactly where rounding bugs live.
- **Platform alternatives:** None; the JDK ships no property-based testing.
- **Existing dependencies:** JUnit 5 and AssertJ are present; neither generates inputs.
- **Minimal custom implementation:** A hand-rolled generator loop. It would be an unmaintained,
  untested reimplementation of shrinking and reproducible seeds — the two things that make a
  failing property useful.
- **Proposed dependency:** `net.jqwik:jqwik`, test scope only. Never reaches a runtime artifact.
- **Maintenance:** Actively maintained, the de facto property-based testing library for the JVM.
- **License:** EPL-2.0. Test scope, not distributed.
- **Runtime or bundle impact:** None — test scope.
- **Security impact:** None at runtime; no production attack surface.
- **Operational impact:** Adds test execution time bounded by the configured try count.
- **Recommendation:** Adopt. `docs/ARCHITECTURE.md` §Testing already states "jqwik mandatory on
  the math", and the spec names it verbatim in an acceptance criterion. This record documents a
  standing decision rather than proposing a new one.

---

## 2. spring-modulith-starter-jpa

- **Problem:** `PostingRecorded` must be published so later consumers (notifications, the outbox
  queue-depth gauge) receive it, and the publication must commit in the same transaction as the
  posting. An event published outside the transaction can announce money that was rolled back.
- **Standard alternatives:** A hand-written outbox table plus a relay. That is precisely the
  mechanism this starter provides, tested and versioned.
- **Platform alternatives:** Plain Spring `ApplicationEventPublisher` without persistence — loses
  every event on restart, so it cannot serve as an outbox.
- **Existing dependencies:** `spring-modulith-starter-core` is present but carries no event
  persistence.
- **Minimal custom implementation:** An outbox table, a relay, retry with backoff, and a dead
  letter path — a meaningful amount of infrastructure to own and test.
- **Proposed dependency:** `org.springframework.modulith:spring-modulith-starter-jpa`, managed by
  the Modulith BOM already declared.
- **Maintenance:** Maintained by the Spring team; version governed by the existing BOM.
- **License:** Apache-2.0, matching the rest of the Spring stack.
- **Runtime or bundle impact:** One additional table and a small runtime component.
- **Security impact:** The event payload is persisted, so events must not carry secrets.
  `PostingRecorded` carries account identifiers and an amount, no personal data.
- **Operational impact:** The `event_publication` table needs the same backup and retention
  treatment as the rest of the schema; it is created under Flyway control rather than
  auto-generated so its shape stays reviewable.
- **Recommendation:** Adopt. The spec's DL-0004 already fixes "the Spring Modulith JPA event
  registry as the outbox base", and the roadmap's follow-through notes have a later gauge
  sampling "the event-publication registry SPEC-0001 introduces".

---

## 3. PIT / pitest (build plugin, profile-activated)

- **Problem:** Line coverage says a test executed a line; it does not say an assertion would have
  noticed the line being wrong. On the module that owns every balance, that difference matters.
- **Standard alternatives:** JaCoCo alone, already present — measures execution, not detection.
- **Platform alternatives:** None.
- **Existing dependencies:** JaCoCo is configured with the inherited 80%/65% floors.
- **Minimal custom implementation:** Not reasonably possible.
- **Proposed dependency:** `org.pitest:pitest-maven` plus its JUnit 5 plugin, build scope,
  activated by an explicit profile.
- **Maintenance:** Long-lived, actively maintained, the standard JVM mutation testing tool.
- **License:** Apache-2.0.
- **Runtime or bundle impact:** None — a build plugin.
- **Security impact:** None.
- **Operational impact:** Mutation analysis is slow. Binding it to a profile keeps it a check QA
  invokes deliberately on money-moving modules instead of a cost paid by every build and every
  CI run.
- **Recommendation:** Adopt, profile-activated. `docs/ARCHITECTURE.md` §Testing sets a
  "PIT ≥60% on money-moving modules (ledger, ...)" floor, and the ledger is the first such module
  to exist. `.claude/rules/qa-ownership.md` anticipates exactly this shape, listing mutation on
  R3+ money slices as a check the normal build does not run.
