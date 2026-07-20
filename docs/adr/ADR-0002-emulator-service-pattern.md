# ADR-0002 — How an external-system emulator is built

Status: Accepted · Date: 2026-07-20 · Slice: SPEC-0002

## Context

The architecture already fixes *what* an emulator is: every external system is its own service
under `emulators/<name>`, exposing the realistic business contract the core consumes plus a
control API that triggers scenarios, running as a container in the development compose profile
and in the E2E stack, with configurable latency and failure, in-memory state, and deterministic
behaviour under a seed. A baseline decision explicitly rejected in-process stubs for money
rails, because a stub exercises no network, no signing, no timeout and no reconciliation.

What was never fixed is *how* such a service is built. The KYC bureau is the first one, and
five more follow it — the PIX settlement system and key directory, the boleto clearinghouse,
the card network, and the CDI index. Whatever this slice does becomes the shape the other five
copy, so the choice is worth stating once rather than re-arguing per rail.

The repository is Java 21 with Maven and an Angular frontend; CI already builds both.

## Options considered

### A minimal Spring Boot service in its own Maven module

The same toolchain, the same test libraries, the same CI wiring, and the idiom every
contributor to this repository already reads. Testcontainers boots the resulting image, which
is how the architecture describes contract-testing an emulator.

The cost is a container start per E2E stack — roughly ten seconds, paid once per stack rather
than once per test — and one more Maven module in the reactor.

### Plain Java over `com.sun.net.httpserver`

Starts in milliseconds and adds no dependency at all. The cost is that routing, JSON
serialization, request validation and configuration are hand-written, and then hand-written
again for each of the six emulators. It trades a runtime cost we pay once per stack for a
maintenance cost paid by every future emulator author.

### A Node/Express service

Small and quick to write in isolation. It adds a second backend toolchain to CI, to the
container build, and to every future emulator, in exchange for no capability the Java stack
lacks here.

## Decision

Emulators are minimal Spring Boot services, one Maven module per emulator under
`emulators/<name>`, starting with `emulators/bureau`.

Each exposes two surfaces that stay strictly separated:

- the **business API**, which mimics the real external contract and is the only surface the
  core's anti-corruption layer knows;
- the **control API** under `/control`, which exists purely so tests and demos can select a
  scenario, and which the core never calls.

State is in memory and resets with the container. Behaviour is deterministic under a
configured seed, so a scenario that fails in CI fails the same way locally.

## Consequences

Easier: a new emulator is a new module that copies an existing one, with routing, JSON,
validation and configuration already solved; contract tests boot the real image over a real
network, so signing, timeouts and retries are exercised rather than assumed; a contributor
reads one stack instead of two.

Harder: the E2E stack grows a container per emulated system, and its start-up time grows with
it — worth watching as the remaining five arrive, and worth revisiting if the stack becomes
slow enough to discourage running it. The emulators must also stay honestly separated from the
core: the control API is a testing affordance, and any leak of it into production code paths
would mean the core knows it is talking to an emulator, which is exactly what the pattern
exists to prevent.
