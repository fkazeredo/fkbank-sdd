# RELAY workflow

RELAY is FKBANK's evidence-driven delivery state machine. It is designed for machine execution,
not as a sequence of human ceremonies.

## Authority

The active workflow contract is composed of:

1. `CLAUDE.md` — repository invariants and authority order;
2. `.claude/workflow-policy.yml` — models, limits, workers, and terminal states;
3. `.claude/rules/` — cross-command rules;
4. `.claude/skills/*/SKILL.md` — executable command contracts;
5. `.claude/agents/` — bounded QA, review, and security roles;
6. `tools/` — deterministic verification and state utilities.

This index is explanatory. If prose conflicts with an executable contract, the authority order
in `CLAUDE.md` applies.

## Normal operation

| Command | Outcome |
|---|---|
| `/deliver-spec <id> [--resume]` | validates and delivers one spec up to a real wait state |
| `/deliver-sprint <sprint> [--resume]` | optional: delivers all committed specs, then runs the Sprint-closure gate (no release) |
| `/close-sprint <sprint> [--resume]` | normal Sprint-closure gate after individual specs: reconcile, verify the integrated candidate (heavy assembled-system battery + full Security Assurance), draft release notes, record the closure result. No release work. |

Granular commands such as `/design-slice`, `/build`, `/qa`, `/pr`, `/review-pr`,
and `/release` are internal phase contracts and recovery entries. They are not mandatory operator
ceremonies — normal operation never invokes them directly.

The routine operator flow is deliberately small:

```text
/deliver-spec <id>    # repeat for the Sprint's specs
/close-sprint <sprint>
```

`/close-sprint` is an autonomous Sprint-closure gate — *were the committed Sprint outcomes
delivered and verified as an integrated whole?* It reconciles the final merged slice, brings up the
integrated candidate, and runs a heavier, different battery than any per-slice `/deliver-spec`: the
complete cross-spec journey, the full Security Assurance track, combined migrations, cross-cutting
concurrency and resilience, and full regression. It auto-fixes in-scope defects and re-runs only the
affected controls, records a short verdict and a minimal `CLOSED`/`INCOMPLETE` result in
`docs/ROADMAP.md`, and drafts concise product-facing release notes at `docs/release-notes/<sprint>.md`.
It performs no release work — drafting notes neither authorizes nor implies a release — and returns
no follow-up command. Releasing is a separate, owner-driven decision: the operator runs `/release`
when they choose to release and handles protected-branch merges, tags, and GitHub Releases by hand.
`/deliver-sprint` is the less common one-command alternative that performs the spec loop and then
runs the identical closure gate.

An oversized spec never proceeds silently. A binding implementation-fit gate runs before build;
when a spec cannot fit one session it produces exactly one split proposal, waits for exactly one
owner confirmation, then atomically rewrites the spec and its Roadmap rows, validates the result,
and stops — no child slice is implemented in that run. This split is internal to `/deliver-spec`;
the operator never invokes a separate `/split-spec` command.

A fresh session resumes any top-level command with `--resume`. A clean-context restart is
recorded as `CHECKPOINTED` — a deliberate context reset, not a failure.

## Machine boundary

RELAY may derive reversible technical decisions from approved product and architecture. It must
stop for material ambiguity, conflict, new architecture, public-contract change, destructive
migration, weakened gate, risk acceptance, irreversible action, production authorization, or a
protected-branch merge.

When it stops, it asks and recommends. An open question is never closed by the machine's own
judgement, and options are never handed over without one of them being recommended — the first
takes the decision away from the owner, the second hands the work back. Every answer is then
recorded durably, per `.claude/rules/human-decision-gate.md`.

Three guarantees protect quality on the way to that merge. A binding implementation-fit gate runs
before any build. `DEV_VERIFIED` is strict — a fully integrated candidate whose applicable checks
pass, never a partial build left for QA to finish. A QA preflight assembles and exercises the
feature before the independent `qa-engineer` runs, so QA is never the first stage to assemble or
execute it. Parallel implementation is permitted only under safe, disjoint file ownership with an
accountable integrator that reconciles the result; orchestration stays autonomous with no
repository-imposed numeric limits.

Ownership of files follows the cycle, not a permanent map. While an independent worker owns its
work the machine leaves its artifacts alone; once that worker's cycles are spent, the work has
returned to the machine and the machine owns every file. Independence of *verdicts* is what is
absolute: no orchestration may turn an independent judgement into self-approval.

The machine never merges, force-pushes, treats missing evidence as success, or retries without
the finite limits in `.claude/workflow-policy.yml`.

## Language policy

Every repository artifact is English. Only manuals below `docs/manual/**` may have paired
`pt-BR` and `en-US` editions. `tools/workflow/validate-doc-language` enforces the repository
layout and checks Markdown outside the manual tree for Portuguese text.

## Supporting documents

- [Setup](SETUP.md)
- [Human decisions](RELAY-HUMAN-DECISIONS.md)
- [Security Assurance Track](../security/SECURITY-ASSURANCE-TRACK.md)
- [Operational workflow guide — en-US](../manual/operational/en-US/WORKFLOW-GUIDE.md)
- [Operational workflow guide — pt-BR](../manual/operational/pt-BR/WORKFLOW-GUIDE.md)

Superseded design debates, acceptance exchanges, and dated correction reports remain available
in Git history rather than the active documentation tree.
