#!/usr/bin/env bash
set -euo pipefail
PAYLOAD="$(cat)"
PARSED="$(PAYLOAD="$PAYLOAD" python3 - <<'PY'
import json, os
try:
    t=json.loads(os.environ['PAYLOAD']).get('tool_input') or {}
    print(t.get('file_path') or t.get('path') or '')
except Exception: print('')
PY
)"
[ -n "$PARSED" ] || exit 0
ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"; ROLE_FILE="$ROOT/.claude/runtime/current-role"
[ -f "$ROLE_FILE" ] || exit 0
ROLE="$(tr -d '[:space:]' < "$ROLE_FILE")"; [ -n "$ROLE" ] || exit 0
P="${PARSED//\\//}"; ROOT="${ROOT//\\//}"; P="${P#"${ROOT%/}/"}"
matches() { local path="$1"; shift; local pat; for pat in "$@"; do case "$path" in $pat) return 0;; esac; done; return 1; }
QA=('*/src/test/*/acceptance/*' 'src/test/*/acceptance/*' '*/src/test/*/contract/*' 'src/test/*/contract/*' 'frontend/e2e/*' 'qa/*' 'docs/tests/*' 'docs/qa/*')
RUNTIME=('.claude/runtime/*'); ALLOWED=()
case "$ROLE" in
  specifier) ALLOWED=("${RUNTIME[@]}" 'docs/specs/*' 'docs/adr/*' 'docs/PRODUCT.md' 'docs/DOMAIN.md' 'docs/ARCHITECTURE.md' 'docs/ROADMAP.md');;
  designer) ALLOWED=("${RUNTIME[@]}" 'docs/specs/*' 'docs/exec-plans/*');;
  qa) ALLOWED=("${RUNTIME[@]}" "${QA[@]}");;
  reviewer) ALLOWED=("${RUNTIME[@]}");;
  security) ALLOWED=("${RUNTIME[@]}" 'docs/security/reports/*');;
  orchestrator) ALLOWED=("${RUNTIME[@]}" 'docs/specs/*' 'docs/exec-plans/*' 'docs/qa/*' 'docs/CHANGELOG.md' 'docs/ROADMAP.md');;
  reconciler) ALLOWED=("${RUNTIME[@]}" 'docs/specs/*' 'docs/exec-plans/*' 'docs/CHANGELOG.md' 'docs/ROADMAP.md');;
  builder) matches "$P" "${QA[@]}" && { echo "path-guard: builder cannot edit QA-owned path: $P" >&2; exit 2; }; exit 0;;
  releaser)
    MANIFEST="$ROOT/.claude/runtime/phase-manifest.json"
    [ -f "$MANIFEST" ] || { echo 'path-guard: releaser has no phase manifest.' >&2; exit 2; }
    mapfile -t ALLOWED < <(python3 -c 'import json,sys; print(*json.load(open(sys.argv[1])).get("allowed_paths",[]),sep="\n")' "$MANIFEST");;
  *) echo "path-guard: unknown active role '$ROLE'." >&2; exit 2;;
esac
matches "$P" "${ALLOWED[@]}" || { echo "path-guard: role '$ROLE' cannot write '$P'." >&2; exit 2; }
exit 0
