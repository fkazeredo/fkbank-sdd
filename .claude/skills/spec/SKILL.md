---
name: spec
description: RELAY authoring/recovery command — interview only for material unknowns and produce a validated specification draft. Delivery approval belongs to deliver-spec.
disable-model-invocation: true
---

# /spec <description> [--profile light|standard|critical]

Read `.claude/rules/workflow-conventions.md` first. Role: `specifier`. Start with a unique
`SPEC-DRAFT-<UTC timestamp>` identity through `tools/workflow/start-phase`; replace that
runtime identity explicitly after the definitive ID is assigned. This skill NEVER
implements, never creates branches, and never invents a business rule by inference.

## Preconditions
Git repository healthy. No existing spec already covers this capability — run the
duplicate-capability check: glob `docs/specs/`, compare titles/scopes; overlap ⇒ present it
and stop with `HUMAN_DECISION_REQUIRED` (extend the existing spec vs create a new one).

## Steps
1. Classify preliminary type and risk (`.claude/rules/risk-model.md`; DOMAIN-invariant
   touch ⇒ at least R3). Apply Decision Ladder rung 1 (necessity/YAGNI) to the requested
   capability — an explicit request judged useless is a human decision, never a silent drop.
2. Interview in batches — ALL questions of a block in one message (Problem/Outcome · Scope/
   Out of scope · Business rules · Contracts/Data · Quality/Environments). Hard limit: 3
   blocks. Accumulate material doubts; ≤1 Human Decision Request for R2, ≤2 for R3.
3. Draft on the profile template (`.claude/templates/light-spec.md` for R0/R1;
   `docs/specs/SPEC-TEMPLATE.md` for R2+): Light (R0/R1) · Standard
   (R2) · Critical (R3/R4 — adds Failure modes, Concurrency/Idempotency, Audit, Rollback).
4. Acceptance-criteria gate: every criterion must name observable + condition/environment +
   discriminator. When truth can vary across environments/profiles, the criterion NAMES the
   environment. Gherkin allowed; Gherkin without a named environment where truth varies is
   REJECTED by this skill.
5. Write the validated spec with risk, sprint, modules, dependencies, reading list,
   `status: AWAITING_SPEC_APPROVAL`, and both approval fields null. Do not add a ceremonial
   approval prompt. `/deliver-spec` later validates and approves the exact content hash.

## Terminal states
AWAITING_SPEC_APPROVAL · AWAITING_SPEC_INPUT · HUMAN_DECISION_REQUIRED · BLOCKED

## Never
Implement · create branches · assume an unanswered material question · exceed 3 interview
blocks (park the rest as Open Questions and stop).

End: `RELAY STATE: AWAITING_SPEC_APPROVAL | evidence: <spec-path> | resume: /deliver-spec <id>`.
