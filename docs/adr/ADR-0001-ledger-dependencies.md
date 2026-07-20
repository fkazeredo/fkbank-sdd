# ADR-0001 — Dependencies introduced by the ledger core

Status: Accepted · Date: 2026-07-20 · Slice: SPEC-0001

## Context

The ledger core is the first module that moves money, and it is the first slice to trigger three
obligations the architecture had already written down but nothing had yet needed:

- `docs/ARCHITECTURE.md` §Testing requires property-based testing on the money arithmetic
  ("jqwik mandatory on the math") and a PIT mutation floor of 60% on money-moving modules, naming
  the ledger first in that list.
- The slice's own decision log fixes the Spring Modulith JPA event registry as the outbox base,
  and the roadmap's follow-through notes have a later observability gauge sampling "the
  event-publication registry SPEC-0001 introduces".

None of the three is a free choice made during design. They are recorded here because the
reasoning should outlive the delivery that happened to trip over them, and because a future
reader deserves to know which ones actually work.

## Options considered

### jqwik (test scope)

- **Alternative — JUnit parameterized tests.** Proves the examples someone thought of. Rounding
  and split defects live in the inputs nobody writes down, which is the whole reason the rule
  exists.
- **Alternative — a hand-rolled generator loop.** An unmaintained reimplementation of shrinking
  and reproducible seeds: the two things that make a failing property useful rather than a
  mystery.

### spring-modulith-starter-jpa

- **Alternative — a hand-written outbox table plus a relay**, with retry, backoff and a dead
  letter path. That is precisely what the starter provides, tested and versioned.
- **Alternative — plain `ApplicationEventPublisher`.** Loses every event on restart, so it
  cannot serve as an outbox at all.

### PIT (pitest), build scope, profile-activated

- **Alternative — JaCoCo alone**, already configured. Coverage says a line executed; it does not
  say an assertion would have noticed the line being wrong. On the module that owns every
  balance, that is the whole distinction.
- **Alternative — bind PIT to the default lifecycle.** Mutation analysis costs minutes and would
  be paid by every build and every CI run for a check that belongs to deliberate review of a
  money module.

## Decision

Adopt all three. jqwik and PIT are test/build scope and never reach a runtime artifact; PIT sits
behind a `mutation` profile invoked as `./mvnw -Pmutation verify`. The event publication table is
owned by Flyway rather than auto-created at start-up, so the shape of a table that will hold
unprocessed business facts is reviewed like any other schema change.

Licences: jqwik EPL-2.0 (test scope, not distributed), Modulith Apache-2.0 (matching the rest of
the Spring stack), PIT Apache-2.0 (build plugin).

## Consequences

Easier: the money arithmetic is stated as properties over the whole input space rather than as
examples; a business fact commits in the same transaction as the money it announces and survives
a restart; the ledger can be held to a stronger standard than line coverage.

Harder: the `event_publication` table needs the same backup and retention treatment as the rest
of the schema, and events persisted there must never carry secrets — `PostingRecorded` carries
account identifiers and an amount, no personal data.

### Open — the mutation floor is not met

**PIT does not run in this environment.** Its coverage minion exits at start-up before generating
a single mutant:

```
PIT >> INFO : Created 15 mutation test units in pre scan
PIT >> INFO : Sending 32 test classes to minion
PIT >> SEVERE : Coverage generator Minion exited abnormally due to UNKNOWN_ERROR
```

Corrections attempted and their outcome:

1. Binding the goal to `verify` rather than `test` — same crash.
2. Excluding the Spring integration tests from the analysed set — same crash.
3. Independent review then found that exclusion had hollowed out the floor it protected:
   `Ledger` was reachable only through those integration tests, so the one class the floor exists
   for would have scored zero even on a working run. `LedgerTest` now covers `Ledger` against fake
   ports, closing that gap — but PIT still crashes identically, so the floor remains unmeasured.

The cause is unidentified and may be specific to this machine's JDK or to running under the Maven
wrapper on Windows; CI runs Linux, where it is untested. **No build step claims the floor is
met.** Revisit by debugging PIT, by running it only in CI, or by revisiting the floor itself.
Tracked as DL-0014 on SPEC-0001.
