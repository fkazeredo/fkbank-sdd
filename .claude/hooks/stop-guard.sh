#!/usr/bin/env bash
set -euo pipefail
PAYLOAD="$(cat)"
FIELDS="$(PAYLOAD="$PAYLOAD" python3 - <<'PY'
import json,os
try:
 d=json.loads(os.environ['PAYLOAD']); print('1' if d.get('stop_hook_active') else '0'); print(d.get('transcript_path') or '')
except Exception: print('INVALID'); print('')
PY
)"
ACTIVE="$(printf '%s\n' "$FIELDS" | sed -n '1p')"; TRANSCRIPT="$(printf '%s\n' "$FIELDS" | sed -n '2p')"
[ "$ACTIVE" != INVALID ] || { echo 'stop-guard: invalid hook JSON.' >&2; exit 2; }
[ "$ACTIVE" != 1 ] || exit 0
ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"; [ -f "$ROOT/.claude/runtime/current-slice" ] || exit 0
ID="$(tr -d '[:space:]' < "$ROOT/.claude/runtime/current-slice")"; [ -n "$ID" ] || exit 0
DIR="$ROOT/.claude/runtime/$ID"; STATE="$DIR/state.json"
[ -f "$STATE" ] || { echo "stop-guard: missing state.json for $ID." >&2; exit 2; }
STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("status",""))' "$STATE" 2>/dev/null)" || { echo "stop-guard: invalid state.json for $ID." >&2; exit 2; }
case "$STATUS" in SPECIFYING|DESIGNING|BUILDING|QA_RUNNING|PR_PREPARING|CI_REWORK|RELEASE_PREPARING|RELEASE_FINALIZING|HOTFIX_SCOPING|HOTFIX_FINALIZING) echo "stop-guard: $ID remains in progress: $STATUS." >&2; exit 2;; esac
[ -f "$DIR/metrics.json" ] || { echo "stop-guard: missing metrics.json for $ID." >&2; exit 2; }
[ "$STATUS" != BLOCKED ] || [ -f "$DIR/block-report.md" ] || { echo 'stop-guard: BLOCKED requires block-report.md.' >&2; exit 2; }
case "$STATUS" in AWAITING_SPEC_INPUT|AWAITING_SPEC_APPROVAL|HUMAN_DECISION_REQUIRED|READY) [ -f "$DIR/spec-path.txt" ] || [ -f "$DIR/decision-request.md" ] || { echo 'stop-guard: specifier artifact missing.' >&2; exit 2; };; esac
[ -z "$TRANSCRIPT" ] || [ ! -f "$TRANSCRIPT" ] || grep -Eq 'SESSION OVER — next:|RELAY STATE:' "$TRANSCRIPT" || { echo 'stop-guard: terminal machine-state marker missing.' >&2; exit 2; }
exit 0
