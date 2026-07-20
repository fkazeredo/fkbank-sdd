# Human Decision Gate

Principle: no silent assumptions · no silent conflict resolution · no material decision
without human visibility. State: `HUMAN_DECISION_REQUIRED`.

## Stop when
Conflict: operator × spec · spec × domain · spec × ADR · ADR × architecture · docs × code ·
test × criterion · contract × implementation · security × requirement · main × develop ·
manual × behavior.
Functional ambiguity: multiple interpretations · incomplete rule · uncertain actor or
permission · undefined error · timezone · rounding · calendar · uncovered state ·
unverifiable criterion.
Material technical decision: new dependency · architecture · bounded context · public
contract · destructive migration · incompatibility · new infrastructure · gate weakening ·
risk · security.
Scope: extra functionality · behavior removal · work outside the spec · slice too big ·
structural refactor discovered mid-build.

## What does NOT require asking (convention discriminator: a WRITTEN rule exists)
Formatter output · local naming · private organization · reversible choice · a clear
existing pattern · a detail the spec covers · a decision already in an ADR / Decision log /
`.claude/rules/` · pushing a `feature/bugfix/chore/hotfix/release` work branch to `origin` and
opening its Pull Request against `develop`/`main` (CLAUDE.md §Git; `/pr`/`/release`/`/hotfix`'s
own contract — distinct from invariant 6's absolute push/merge/tag/self-approve prohibitions,
which no instruction ever overrides). If the answer is written, apply it and note it in the PR.
In a bank, timezone, rounding, currency, limits, permissions and data retention are NEVER
convention.

## Procedure
1. Stop before the change. 2. Write the state. 3. Fill `.claude/templates/decision-request.md`
into `.claude/runtime/<id>/decision-request.md`. 4. Present facts + sources. 5. Options with
trade-offs. 6. Recommend one. 7. Wait. 8. Record the decision durably. 9. Resume.

## Durable record destination
Business → the spec (Decision log) · Architecture → ADR · Security → security document ·
Risk → accepted risk · Reversible/local → runtime · Release → release state · User-facing →
manual.
