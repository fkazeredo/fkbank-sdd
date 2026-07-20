# Risk model (R0–R4) and proportional tracks

Classification is set during `/spec` (frontmatter `risk:`) and approved with the exact spec hash
when the owner invokes `/deliver-spec` or approves the Sprint manifest.
Anchor: if `docs/DOMAIN.md` declares invariants, touching an invariant ⇒ at least R3.
When in doubt, go up.

- **R0 trivial** — text, typo, docs, metadata, comment, formatting.
  Track: `/spec --profile light` → `/build` → verify-fast → `/pr`. Light Spec required.
- **R1 low** — localized bug, small UI, reversible config, provable internal refactor,
  test maintenance. Track: `/spec --profile light` → machine-validated inline plan (≤15 lines)
  → `/build` → focused verification → `/pr`. Independent QA is not required unless evidence
  raises the risk.
- **R2 normal** — common feature, backend+frontend, simple persistence, internal contract,
  non-critical rule. Machine track: design → plan validation → build → isolated QA worker →
  PR/CI. Review worker is evidence-triggered.
- **R3 high** — money, authorization, events, migration, concurrency, personal data,
  important external integration, public contract, any DOMAIN invariant.
  Machine track: durable design → plan validation → build → isolated QA worker → PR/CI →
  isolated review worker (mandatory) → security gate at final delivery.
- **R4 critical** — irreversibility, data deletion, high regulatory impact, production,
  hard recovery, critical secrets. Track: R3 + additional human gates; irreversible steps
  are performed by the human, never the agent; specialized assurance required.

Model policy per phase × risk: `.claude/workflow-policy.yml`.
