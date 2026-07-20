# RELAY setup

RELAY is already installed in this repository. This guide verifies a workstation and the
server-side controls required before the first product delivery.

## Prerequisites

- Git and an authenticated GitHub CLI for Pull Request operations;
- Claude Code with the project configuration in `.claude/settings.json`;
- PowerShell on Windows or Bash and Python 3 on Linux CI;
- Java 21, Node.js 22, and Docker when product modules become applicable.

## Local validation

Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/tests/relay-smoke.ps1
```

Linux:

```bash
timeout 300 tools/tests/relay-smoke.sh
```

Pre-bootstrap wrappers may report a product gate as not applicable. They must never report an
unavailable mandatory control as passed.

## GitHub protection

Protect `main` and `develop` with rulesets that require Pull Requests, prevent force pushes and
deletion, and require the observed CI checks. Protect `v*` tags against update and deletion.
Agents may prepare branches and Pull Requests, but merges remain human-only.

## First operation

Use a single top-level command:

```text
/deliver-spec <id>
/deliver-sprint <sprint>
/close-sprint <sprint>
```

The command validates current evidence, advances eligible transitions, and stops only at a
material decision, external wait, human merge, risk acceptance, or bounded failure.
