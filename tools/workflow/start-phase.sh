#!/usr/bin/env bash
set -euo pipefail
ROLE="${1:-}"; ID="${2:-}"; PHASE="${3:-}"; RISK="${4:-}"; shift $(( $# >= 4 ? 4 : $# ))
case "$ROLE" in specifier|designer|builder|qa|reviewer|security|orchestrator|reconciler|releaser) ;; *) echo "start-phase: invalid role '$ROLE'." >&2; exit 2;; esac
[[ "$ID" =~ ^(SPEC-(DRAFT-[0-9A-Za-z-]+|[0-9]{4})|RELEASE-[0-9A-Za-z.-]+|HOTFIX-[0-9A-Za-z.-]+)$ ]] || { echo "start-phase: invalid id '$ID'." >&2; exit 2; }
[ -n "$PHASE" ] || { echo 'start-phase: phase is required.' >&2; exit 2; }
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SHA="$(git -C "$ROOT" rev-parse HEAD)"
DIR="$ROOT/.claude/runtime/$ID"; mkdir -p "$DIR"
python3 - "$DIR/state.json" "$ID" "$PHASE" "$RISK" "$SHA" <<'PY'
import datetime,json,sys
json.dump({'slice':sys.argv[2],'status':sys.argv[3],'risk':sys.argv[4],'base_sha':sys.argv[5],'updated':datetime.datetime.now(datetime.timezone.utc).isoformat()},open(sys.argv[1],'w'),indent=2)
PY
printf '%s' "$ROLE" > "$ROOT/.claude/runtime/current-role"; printf '%s' "$ID" > "$ROOT/.claude/runtime/current-slice"
# A background worker and the agent that spawned it share this directory, and each call to
# start-phase overwrites the other's role - fine when it narrows the caller's rights, dangerous
# when it widens them. The per-session copy lets the guard resolve the role of the session
# actually making the call; the shared file stays for tools that have no session.
if [ -n "${CLAUDE_CODE_SESSION_ID:-}" ]; then
  mkdir -p "$ROOT/.claude/runtime/roles"
  SAFE_SESSION="$(printf '%s' "$CLAUDE_CODE_SESSION_ID" | tr -c '0-9A-Za-z._-' '_')"
  printf '%s' "$ROLE" > "$ROOT/.claude/runtime/roles/$SAFE_SESSION"
fi
python3 - "$ROOT/.claude/runtime/phase-manifest.json" "$ROLE" "$PHASE" "$@" <<'PY'
import json,sys
json.dump({'role':sys.argv[2],'phase':sys.argv[3],'allowed_paths':sys.argv[4:]},open(sys.argv[1],'w'),indent=2)
PY
echo "start-phase: $ROLE/$PHASE for $ID"
