#!/usr/bin/env bash
# Reports whether a slice is waiting on a background worker, and whether that wait still looks
# alive — using signals that would come out differently if the answer were no. Mirrors
# worker-status.ps1; the reasoning is documented there.
#
# It reports signals, never a verdict. A worker's own completion notification is the only thing
# that says it finished; a report sitting on disk is not one.
set -uo pipefail

ID="${1:-}"
[ -n "$ID" ] || { echo 'usage: worker-status.sh <slice-id>' >&2; exit 2; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/.claude/runtime/$ID"
[ -d "$DIR" ] || { echo "worker-status: no runtime directory for $ID."; exit 1; }

echo "worker-status: $ID"
echo

# What the phase said it was waiting for. Absent means either nothing was spawned, or something was
# spawned without being recorded — which is itself worth seeing.
if [ -f "$DIR/awaiting.json" ]; then
  python3 - "$DIR/awaiting.json" <<'PY' || echo '  declared wait : awaiting.json is present but unreadable.'
import datetime, json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    awaiting = json.load(handle)
print(f"  declared wait : {awaiting.get('waiting_on', '?')}")
since = awaiting.get('since', '')
print(f"  since         : {since}")
try:
    started = datetime.datetime.fromisoformat(since.replace('Z', '+00:00'))
    minutes = (datetime.datetime.now(datetime.timezone.utc) - started).total_seconds() / 60
    print(f"  waiting for   : {minutes:.0f} minutes")
except Exception:
    pass
PY
else
  echo '  declared wait : none recorded.'
fi

echo
echo '  signals that discriminate'

# A compose stack belonging to this project. Nothing starts one by accident, and the uptime says
# whether it started just now or has been sitting there since an earlier run.
PROJECT="$(basename "$ROOT")"
if ! CONTAINERS="$(docker ps --format '{{.Names}} - {{.Status}}' 2>/dev/null)"; then
  echo '    containers  : could not ask docker (not running, or not installed).'
else
  MINE="$(printf '%s\n' "$CONTAINERS" | grep "^$PROJECT" || true)"
  if [ -z "$MINE" ]; then
    echo '    containers  : none for this project. No E2E stack is up right now.'
  else
    printf '%s\n' "$MINE" | while IFS= read -r line; do echo "    containers  : $line"; done
  fi
fi

# Recent writes anywhere a worker owns. Useful when positive; silence proves nothing, which is why
# it is labelled rather than interpreted.
NEWEST=""
for OWNED in ".claude/runtime/$ID" docs/tests docs/qa frontend/e2e backend/src/test/java/com/fkbank/acceptance; do
  [ -d "$ROOT/$OWNED" ] || continue
  CANDIDATE="$(find "$ROOT/$OWNED" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1)"
  [ -n "$CANDIDATE" ] || continue
  if [ -z "$NEWEST" ] || [ "${CANDIDATE%% *}" \> "${NEWEST%% *}" ]; then NEWEST="$CANDIDATE"; fi
done
if [ -n "$NEWEST" ]; then
  AGE=$(( ( $(date +%s) - ${NEWEST%%.*} ) / 60 ))
  echo "    last write  : $(basename "${NEWEST#* }") ($AGE min ago)"
  echo '                  a long gap is normal during a build and does not mean the worker died.'
else
  echo '    last write  : nothing found under the paths a worker owns.'
fi

echo
echo "  The worker's own completion notification is what says it finished. Nothing above does."
exit 0
