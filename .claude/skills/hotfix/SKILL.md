---
name: hotfix
description: RELAY support — hotfix from main with mandatory reproduction test first, reduced-scope QA when invariants are touched, human merges, tag + sync PR. Manual invocation only.
disable-model-invocation: true
---

# /hotfix <version> <short-description>

Read `.claude/rules/workflow-conventions.md` first. Scoping/finalization role: `releaser`;
functional correction role: `builder`. Urgency never removes tests.

## Steps
1. As releaser, record `HOTFIX_SCOPED`; branch `hotfix/x.y.z-<slug>` from **main**, run
   `check-safe-branch hotfix`, and produce a Light or Critical spec per risk.
2. In a new builder phase, write the **reproduction test FIRST**; it must fail before the fix.
3. Fix via TDD → `HOTFIX_DEV_VERIFIED` after `tools/quality/verify-slice`.
4. R3/R4 and any DOMAIN invariant enter `HOTFIX_QA_REQUIRED`; independent QA finishes at
   `HOTFIX_QA_VERIFIED`. Mandatory minimum checks: static analysis, secrets, dependencies,
   authorization, regression tests, and explicit human risk decision. Emergency deferral
   records scope, owner, deadline, and post-release Security Assurance artifact; it never
   produces `SECURITY_VERIFIED`.
5. PR to main → `AWAITING_HOTFIX_MAIN_MERGE`; human merge only. As releaser, confirm the
   exact merged SHA, enter `HOTFIX_FINALIZING`, tag/release, open the sync PR to develop,
   then `AWAITING_HOTFIX_SYNC_MERGE`; only a human-observed sync merge reaches `HOTFIX_DONE`.

## Never
Skip the failing-first reproduction test · push directly · merge · tag before the human
merge.

End: `SESSION OVER — next: human merge (main)` then the sync PR.
