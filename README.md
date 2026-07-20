# RELAY boilerplate — spec-driven delivery starter

A ready-to-run starter for building a product with the **RELAY** workflow: the agent
scaffolding (`.claude/`), the documentation tree (`docs/`), the executable tooling (`tools/`),
CI, and the container topology — with every piece of *workflow control*, *decision log*, and
*delivery progress* reset to an initial state, and every project-specific reference genericized
for a greenfield repository.

It also carries FKBANK's full 18-spec backlog and definition docs intact, so it doubles as a
from-scratch replay of that project.

## Layout

```
boilerplate/
├── CLAUDE.template.md          # greenfield permanent context — fill the <PLACEHOLDERS>
├── CLAUDE.example-fkbank.md    # the same file, fully filled, as a worked example
├── .claude/                    # skills, rules, agents, hooks, templates, settings, policy
├── docs/                       # product/domain/architecture truth, specs, workflow guides
│   └── DOMAIN-MODULES.txt      # the module allow-list validate-specs enforces
├── tools/                      # workflow, quality, git, release, security, smoke tests
├── .github/                    # CI, CodeQL, dependabot, CODEOWNERS, PR template
├── infra/e2e-preflight.mjs     # frees the single E2E origin before the stack starts
├── compose.{dev,e2e,prod,security}.yaml
├── .gitignore  .vscode/
```

## Verified, not assumed

Run from inside this folder on a tree with no application code at all:

| Check | Command | Result |
|---|---|---|
| Full RELAY smoke (10 tests) | `tools/tests/relay-smoke.ps1` | `PASS (10 tests)` |
| Spec frontmatter + module gate | `tools/workflow/validate-specs.ps1` | `PASS (18 specs)` |
| en-US artifact policy | `tools/workflow/validate-doc-language.ps1` | `PASS` |
| Greenfield build gates | `tools/quality/verify-{fast,slice,e2e}.sh` | `exit=0`, "pre-bootstrap. OK by definition" |
| Shell/JS syntax | `bash -n tools/*/*.sh`, `node --check` | clean |

The smoke test covers the path/shell/stop hooks, the worker allow-list, the skill contracts,
and deterministic spec-approval hashing — so the scaffolding is proven wired, not just present.

## Using it

**(A) A brand-new project.** Copy everything into a fresh repo, then:

1. `mv CLAUDE.template.md CLAUDE.md` and fill every `<PLACEHOLDER>` (keep the invariant
   numbering — `.claude/rules/*` and the skills reference invariants **by number**).
2. Replace `docs/PRODUCT.md`, `docs/DOMAIN.md`, `docs/ARCHITECTURE.md` with your own, and
   delete `docs/specs/SPEC-00*.md` (keep `SPEC-TEMPLATE.md`).
3. Put your bounded contexts in `docs/DOMAIN-MODULES.txt` — or delete the file to disable the
   module check until your domain is mapped.
4. Set `.github/CODEOWNERS` to your handle, and rename `app` in the compose files.
5. Write your first spec with `/spec`, then `/deliver-spec <id>`.

Routine delivery deliberately needs no phase babysitting: repeat `/deliver-spec <id>` for the
Sprint's specs, then run one `/close-sprint <sprint>`. That command owns reconciliation, final
verification and assurance, the closure record, release preparation and release finalization.
Protected-branch merges remain human-only; resume the same `/close-sprint --resume` afterward.
`/deliver-sprint <sprint>` is the optional, less common shortcut that performs the entire spec
loop and then runs the identical closeout.

**(B) Replay FKBANK from scratch.** Copy everything in, `mv CLAUDE.example-fkbank.md CLAUDE.md`,
and run the backlog in `docs/ROADMAP.md` order starting at `SPEC-0018` (walking skeleton) —
which is the slice that creates `backend/`, `frontend/` and the real application topology.

## What was reset to an initial state

- **Workflow controls** — `.claude/runtime/**` emptied to `.gitkeep` + empty `current-role` /
  `current-slice`.
- **Spec delivery state** — the 4 delivered specs (0001, 0002, 0016, 0018) reset from
  `IMPLEMENTED` to `AWAITING_SPEC_APPROVAL`, approval/implementation timestamps cleared. All 18
  specs are a pending backlog.
- **Decision logs** — `docs/security/DECISIONS.md` emptied; each spec's `## Decision log` lost
  its *delivery-act* entries (the `/deliver-spec` approval and `/design-slice` fit-check
  records) while keeping the definitional architecture/domain/policy decisions;
  `docs/workflow/RELAY-HUMAN-DECISIONS.md` resolved rows returned to pending.
- **Trackers** — `docs/ROADMAP.md` Sprint-1 rows back to `Done ☐` / `—`; `CHANGELOG.md`
  `[Unreleased]` emptied.
- **Delivered artifacts removed** — completed exec-plans, the sprint QA closure and security
  assurance reports, test books, and the delivered product manual (directories kept).

## Greenfield adaptations made to the tooling

| File | Was | Now |
|---|---|---|
| `tools/workflow/validate-specs.{sh,ps1}` | FKBANK module list hardcoded | reads `docs/DOMAIN-MODULES.txt`; **skips** the check when absent |
| `tools/tests/relay-smoke.{sh,ps1}` | hashed `SPEC-0001-ledger-core.md` | hashes the first spec it finds |
| `tools/security/verify-assurance.{sh,ps1}` | `FKBANK_SECURITY_TARGET` | `APP_SECURITY_TARGET` |
| `tools/security/supply-chain/trivy-scan.sh` | fixed `backend`/`bureau`/`frontend` images | `APP_SCAN_IMAGES` + `APP_IMAGE_PREFIX`; missing build contexts are skipped, not failures |
| `.github/workflows/ci.yml` | primed the bureau emulator's Maven cache | skips cleanly when `backend/mvnw` does not exist yet |
| `.github/CODEOWNERS` | `@fkazeredo` | `@your-github-handle` |
| `compose.*.yaml` | FKBANK services, `FKBANK_*` vars, KYC emulator | `db` + `backend` + `edge`, `APP_*` vars, emulator slot commented as an example |
| `infra/e2e-preflight.mjs` | port 8090 hardcoded | `E2E_HOST` / `E2E_PORT` |
| `tools/security/zap-baseline.conf` | FKBANK/ledger rationale | same rule policy, stack-neutral justifications |

The `tools/quality/verify-*` scripts needed no change: they already report
"pre-bootstrap. OK by definition" when there is no application yet.

## Caveats

- The `.sh` tools call `python3`; the `.ps1` twins do not. That matches the project's own
  convention — `.ps1` on a Windows workstation, `.sh` on POSIX/CI — but a POSIX box without
  Python will fail the shell variants.
- `docs/PRODUCT.md`, `docs/DOMAIN.md`, `docs/ARCHITECTURE.md` and the specs are deliberately
  **still FKBANK's**: mode B needs them, and they are what mode A overwrites.
- `docs/workflow/RELAY-HUMAN-DECISIONS.md` resolved rows (HD-01, HD-03, HD-04, HD-05, HD-10)
  were rephrased from their resolutions back into open questions — wording derived from the
  originals, worth a glance.
- CI assumes `backend/` (Maven) and `frontend/` (npm). Adjust the job matrix if your topology
  differs.
