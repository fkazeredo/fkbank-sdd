#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; RUN="$(mktemp -d)"; trap 'rm -rf "$RUN"' EXIT
run(){ local name="$1";shift; local start=$SECONDS; echo "START $name"; timeout 60 "$@"; echo "PASS  $name $((SECONDS-start))s"; }
run validate-specs "$ROOT/tools/workflow/validate-specs.sh"
run validate-doc-language "$ROOT/tools/workflow/validate-doc-language.sh"
run spec-hash bash -c 's=$(ls "$1"/docs/specs/SPEC-[0-9][0-9][0-9][0-9]-*.md 2>/dev/null | head -1); [ -n "$s" ] || { echo "no spec to hash" >&2; exit 1; }; a=$("$1/tools/workflow/spec-hash.sh" "$s"); [[ "$a" =~ ^[0-9a-f]{64}$ ]]' _ "$ROOT"
run shell-syntax bash -n "$ROOT"/.claude/hooks/*.sh "$ROOT"/tools/*/*.sh
run manual-skills bash -c '! grep -L "^disable-model-invocation: true$" "$1"/.claude/skills/*/SKILL.md | grep .' _ "$ROOT"
run settings-shape python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); raw=open(sys.argv[1]).read(); assert not __import__("re").search(r"\x27[^\x27]*\$\{CLAUDE_PROJECT_DIR\}[^\x27]*\x27",raw); hs=[]; [hs.extend(x["hooks"]) for group in d["hooks"].values() for x in group]; assert all(h.get("command")=="powershell.exe" and h.get("args") and h.get("timeout") for h in hs)' "$ROOT/.claude/settings.json"
run machine-orchestration python3 -c 'import json,sys,pathlib; r=pathlib.Path(sys.argv[1]); d=json.load(open(r/".claude/settings.json")); assert "Agent" in d["permissions"]["allow"]; assert "Agent" not in d["permissions"]["deny"]; p=(r/".claude/workflow-policy.yml").read_text(); expected=("mode: ultracode","effort: xhigh","dynamic_workflows: unrestricted","agent_teams: unrestricted","background_tasks: unrestricted","repository_numeric_limits: none"); assert all(x in p for x in expected); assert all((r/".claude/skills"/x/"SKILL.md").is_file() for x in ("deliver-spec","deliver-sprint","close-sprint","security-assurance"))' "$ROOT"
run workflow-guide-workers python3 -c 'import pathlib,sys; r=pathlib.Path(sys.argv[1]); guides=[r/"docs/manual/operational/en-US/WORKFLOW-GUIDE.md",r/"docs/manual/operational/pt-BR/WORKFLOW-GUIDE.md"]; workers=("qa-engineer","pr-reviewer","security-assurance-engineer"); commands=("/deliver-spec","/deliver-sprint","/close-sprint"); assert all(g.is_file() and all(f"### `{w}`" in g.read_text(encoding="utf-8") for w in workers) and all(c in g.read_text(encoding="utf-8") for c in commands) for g in guides)' "$ROOT"
echo 'relay-smoke: PASS'
