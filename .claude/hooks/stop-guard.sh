#!/usr/bin/env bash
# Refuses to let a session end while a slice is still mid-flight, and tells the operator when it
# cannot refuse any longer. Mirrors stop-guard.ps1; the reasoning is documented there.
#
# Three things this has to get right, each of which it previously got wrong: it must push back
# more than once (exiting whenever the harness reported the hook had already fired made it a
# one-shot); it must look for the terminal marker in the last thing the assistant actually said
# (searching the whole transcript matched the marker inside the instructions describing it); and
# when the budget runs out it must address the human, not only the model.
#
# CHECKPOINTED is a legitimate resumable stop, not an in-progress state: it means a clean-context
# continuation is safer than pushing this turn further, so the guard never force-continues it. It
# is accepted only when its evidence artifact checkpoint.md is present, mirroring how BLOCKED
# requires block-report.md.
set -uo pipefail

PAYLOAD="$(cat)"
TRANSCRIPT="$(PAYLOAD="$PAYLOAD" python3 - <<'PY'
import json, os
try:
    print(json.loads(os.environ['PAYLOAD']).get('transcript_path') or '')
except Exception:
    print('INVALID')
PY
)"
[ "$TRANSCRIPT" != INVALID ] || { echo 'stop-guard: invalid hook JSON.' >&2; exit 2; }

ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"
RUNTIME="$ROOT/.claude/runtime"
COUNTER="$RUNTIME/stop-guard-nudges"

# How many times the session is pushed to carry on before the guard gives up and escalates to the
# operator. Three survives a turn that merely ran out, and is small enough that a session which
# genuinely cannot continue reaches a human quickly instead of looping.
MAX_NUDGES=3

finish_cleanly() { rm -f "$COUNTER"; exit 0; }

[ -f "$RUNTIME/current-slice" ] || finish_cleanly
ID="$(tr -d '[:space:]' < "$RUNTIME/current-slice")"
[ -n "$ID" ] || finish_cleanly

DIR="$RUNTIME/$ID"
STATE="$DIR/state.json"
[ -f "$STATE" ] || { echo "stop-guard: missing state.json for $ID." >&2; exit 2; }
STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("status",""))' "$STATE" 2>/dev/null)" \
  || { echo "stop-guard: invalid state.json for $ID." >&2; exit 2; }

case "$STATUS" in
  SPECIFYING|DESIGNING|BUILDING|QA_RUNNING|PR_PREPARING|CI_REWORK|RELEASE_PREPARING|RELEASE_FINALIZING|HOTFIX_SCOPING|HOTFIX_FINALIZING)
    # The budget is keyed to the state, so reaching a new phase earns a fresh one. Without that, a
    # long delivery would spend its whole allowance on the first phase and then coast silently
    # through every phase after it.
    SIGNATURE="$ID/$STATUS"
    NUDGES=0
    if [ -f "$COUNTER" ]; then
      RECORDED="$(cat "$COUNTER")"
      if [ "${RECORDED%%|*}" = "$SIGNATURE" ]; then
        SEEN="${RECORDED##*|}"
        case "$SEEN" in ''|*[!0-9]*) NUDGES=0 ;; *) NUDGES="$SEEN" ;; esac
      fi
    fi
    NUDGES=$((NUDGES + 1))
    printf '%s|%s' "$SIGNATURE" "$NUDGES" > "$COUNTER"

    if [ "$NUDGES" -le "$MAX_NUDGES" ]; then
      echo "stop-guard: $ID is still $STATUS - the session must not end here. Continue the phase to a terminal state, or write BLOCKED with a filled .claude/templates/block-report.md if it genuinely cannot reach one. (push $NUDGES of $MAX_NUDGES)" >&2
      exit 2
    fi

    # A Stop hook's stderr is read by the model. Only stdout carrying this shape reaches the person.
    RESUME_ID="$(printf '%s' "$ID" | sed -E 's/^SPEC-0*//')"
    python3 - "$ID" "$STATUS" "$MAX_NUDGES" "$RESUME_ID" <<'PY'
import json, sys
slice_id, status, budget, resume = sys.argv[1:5]
print(json.dumps({"systemMessage":
    f"RELAY: {slice_id} is still {status}, and the session stopped anyway after {budget} "
    f"attempts to continue it. Nothing is lost - the work is on disk and the phase is resumable. "
    f"Resume with: /deliver-spec {resume} --resume"}))
PY
    exit 0
    ;;
esac

[ -f "$DIR/metrics.json" ] || { echo "stop-guard: missing metrics.json for $ID." >&2; exit 2; }
[ "$STATUS" != BLOCKED ] || [ -f "$DIR/block-report.md" ] || { echo 'stop-guard: BLOCKED requires block-report.md.' >&2; exit 2; }
[ "$STATUS" != CHECKPOINTED ] || [ -f "$DIR/checkpoint.md" ] || { echo 'stop-guard: CHECKPOINTED requires checkpoint.md.' >&2; exit 2; }
case "$STATUS" in
  AWAITING_SPEC_INPUT|AWAITING_SPEC_APPROVAL|HUMAN_DECISION_REQUIRED|READY)
    [ -f "$DIR/spec-path.txt" ] || [ -f "$DIR/decision-request.md" ] \
      || { echo 'stop-guard: specifier artifact missing.' >&2; exit 2; } ;;
esac

if [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
  # Only what the assistant said last. The marker is described in the instructions the session was
  # given, so anything that searches the whole transcript finds it there and passes a session that
  # never printed it.
  MARKER_PRESENT="$(python3 - "$TRANSCRIPT" <<'PY'
import json, re, sys
last = None
with open(sys.argv[1], encoding='utf-8', errors='replace') as transcript:
    for line in transcript:
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except Exception:
            continue
        if entry.get('type') != 'assistant':
            continue
        said = ''.join(
            block.get('text', '')
            for block in (entry.get('message', {}).get('content') or [])
            if isinstance(block, dict) and block.get('type') == 'text'
        )
        if said.strip():
            last = said
print('1' if last is None or re.search(r'SESSION OVER . next:|RELAY STATE:', last) else '0')
PY
)"
  [ "$MARKER_PRESENT" = 1 ] || {
    echo 'stop-guard: the closing message carries no machine-state marker. End with a line of the form "RELAY STATE: <state> | evidence: <paths> | resume: <command-or-none>".' >&2
    exit 2
  }
fi

finish_cleanly
