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

## Oversized spec (TOO_LARGE) → one-question split
When the implementation-fit gate (`decision-ladder.md` §Implementation-fit gate) returns
`TOO_LARGE`, splitting the spec is a material technical/scope decision. `/deliver-spec` first
generates a concrete split proposal — child ids/titles/goals/scope/acceptance criteria, which
original criteria move to each child, the Roadmap rows that replace the original, every `depends_on`
to redirect, the original file to delete — and then stops with **exactly one question: "Do you want
me to apply this split?"** Decline ⇒ leave every file unchanged and end at `HUMAN_DECISION_REQUIRED`.
Approve ⇒ one atomic spec/Roadmap rewrite, then validation, then stop before implementing the first
child. **A narrative claim ("no value when split", "no customer value") NEVER overrides the fit
gate.**

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

Steps 6 and 7 are not optional and not interchangeable (owner-reinforced, 2026-07-20). Never
close an open question on your own judgement, and never hand the owner a menu without saying
which option you would take and why. Step 8 is equally binding: asking replaces deciding alone,
it does not replace writing the answer down.

## Durable record destination
Business → the spec (Decision log) · Architecture → ADR · Security → security document ·
Risk → accepted risk · Reversible/local → runtime · Release → release state · User-facing →
manual.
