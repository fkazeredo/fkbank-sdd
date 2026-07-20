#!/usr/bin/env bash
set -euo pipefail
PAYLOAD="$(cat)"
COMMAND="$(PAYLOAD="$PAYLOAD" python3 - <<'PY'
import json, os
try:
    print((json.loads(os.environ['PAYLOAD']).get('tool_input') or {}).get('command') or '')
except Exception:
    print('')
PY
)"
[ -n "$COMMAND" ] || exit 0
if printf '%s' "$COMMAND" | grep -Eiq '(^|[;&|][[:space:]]*)git[[:space:]]+merge([[:space:]]|$)|(^|[;&|][[:space:]]*)gh[[:space:]]+pr[[:space:]]+merge([[:space:]]|$)|git[[:space:]]+push[[:space:]]+(--force|-f)([[:space:]]|$)|git[[:space:]]+push[^;&|]*(main|develop)|git[[:space:]]+tag[[:space:]]+(-f|--force)([[:space:]]|$)|(Set-Content|Out-File|Add-Content)|((python|python3|py).*(write_text|write_bytes|open\([^)]*,[[:space:]]*["'"'](w|a|x)))'; then
  echo 'shell-guard: blocked a mutating command that bypasses RELAY write/merge boundaries. Use repository editing tools and human-only merge/push procedures.' >&2
  exit 2
fi
exit 0
