# FKBANK greenfield replay

A clean, from-scratch replay of FKBANK using the RELAY workflow. This starter intentionally keeps
the product definition, domain model, architecture, operational guide, and all 18 specs as worked
material. It intentionally keeps no active execution state or implementation artifacts.

## Start

1. Copy this directory into a new, empty repository and initialize Git.
2. Rename `CLAUDE.example-fkbank.md` to `CLAUDE.md`. Keep
   `CLAUDE.template.md` only as the product-neutral reference.
3. Configure `.github/CODEOWNERS` and repository rulesets.
4. Run `tools/tests/relay-smoke.ps1`.
5. Start with the current `SPEC-0018`; obtain a new exact-hash approval and redesign it under
   invariant 9 before building.

Do not restore the historical `PLAN-0018`, approval hash `21d2d590...`, orphan branch state,
or compiled classes. They belong to an interrupted run that predates the behavioral-domain rule.

## Build layout

The Compose files already define `./backend` and `./frontend` as build contexts. Consequently
the walking skeleton creates `backend/Dockerfile` and `frontend/Dockerfile`. A plan proposing
`infra/docker/` is inconsistent unless Compose explicitly names those files.

## Execution model

A clean restart means RELAY derives a new design from the current spec and its `reading_list`.
Ultracode runs at xhigh with unrestricted dynamic workflows, agent teams, subagents, parallel
implementation, and background tasks. Claude owns that orchestration without operator
babysitting or repository-level numeric limits. The `qa-engineer`, `pr-reviewer`, and
`security-assurance-engineer` retain independent responsibilities, not orchestration limits.

Routine delivery repeats `/deliver-spec <id>`, then runs `/close-sprint <sprint>`.
`/deliver-sprint <sprint>` is the optional whole-Sprint loop.
