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
| `/deliver-sprint <sprint> [--resume]` | delivers committed specs sequentially by dependency |
| `/close-sprint <sprint> [--resume]` | reconciles the increment and runs final assurance |

Granular commands such as `/design-slice`, `/build`, `/qa`, `/pr`, and `/review-pr` are internal
phase contracts and recovery entries. They are not mandatory operator ceremonies.

## Machine boundary

RELAY may derive reversible technical decisions from approved product and architecture. It must
stop for material ambiguity, conflict, new architecture, public-contract change, destructive
migration, weakened gate, risk acceptance, irreversible action, production authorization, or a
protected-branch merge.

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
