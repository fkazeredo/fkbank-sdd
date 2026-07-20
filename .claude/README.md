# .claude/ — RELAY workflow implementation

This directory contains the executable RELAY workflow. Its human-readable index is
`docs/workflow/README.md`. Authoring conventions:

- **skills/** — one folder per command, including `/approve-plan`, `/deliver-spec`,
  `/deliver-sprint`, `/close-sprint`, `/security-assurance`, and the recovery/fallback `/reconcile-workflow`; every workflow skill carries
  `disable-model-invocation: true` (operators invoke top-level commands; those commands drive
  internal transitions and approved workers automatically).
  Routine operation is repeated `/deliver-spec` followed by one `/close-sprint`; that close command
  composes release preparation/finalization internally. `/deliver-sprint` is the optional whole-Sprint
  loop and includes the same closeout.
- **agents/** — isolated foreground workers: `qa-engineer`, `pr-reviewer`, and
  `security-assurance-engineer`. Delivery orchestration may invoke exactly these workers;
  manual main-session use remains available for recovery. They cannot spawn workers.
- **rules/** — the guardrails skills read on demand (conventions, decision ladder, human
  decision gate, QA ownership, risk model, security gate). Not auto-imported: budgeted
  reading is the point.
- **templates/** — decision-request, block-report, new-dependency, light-spec,
  dev-verification, qa-report skeletons.
- **hooks/** — `path-guard`, `shell-guard`, `stop-guard`, and `session-cleanup` provide
  local defense in depth. They do not replace GitHub rulesets and must not be described as
  unbypassable enforcement. PowerShell is wired in `settings.json` (Windows operator);
  `.sh` equivalents ship for POSIX environments — swap the commands if you run there.
- **runtime/** — the relay baton (state.json, plan.md, dev-verification.md, qa-report.md,
  metrics.json per slice). Git-ignored. Skills also keep `current-role` / `current-slice`
  here for the hooks.
- **workflow-policy.yml** — concrete model/effort aliases, limits, worker allow-list, QA paths,
  state-machine waits, and the final-delivery security gate. Change policy here, not in
  explanatory documents.
