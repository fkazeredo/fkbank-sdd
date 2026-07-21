#!/usr/bin/env bash
# Binding slice gates (read-only). Exit 0 = pass, 2 = gate violated, 1 = usage/IO error.
# Mirrors check-slice-gate.ps1. The runtime dir is resolved from the id alone: a numeric id is
# padded to SPEC-NNNN, an already-formed SPEC-NNNN is accepted. Root is $CLAUDE_PROJECT_DIR or the
# repo root (this script's dir/../..). state.json lives at <root>/.claude/runtime/<SPEC-NNNN>.
# The flat-field gates use bash/grep/git plus python3 for structured JSON evidence; the parallel
# gate also requires python3 when a parallel-plan.json is present.
set -uo pipefail

usage() {
  [ -n "${1:-}" ] && echo "check-slice-gate: $1" >&2
  echo 'usage: check-slice-gate.sh <id> <fit|dev-verified|qa-preflight|parallel>' >&2
  exit 1
}

ID="${1:-}"; GATE="${2:-}"
[ -n "$ID" ] || usage 'missing id'
case "$GATE" in fit|dev-verified|qa-preflight|parallel) ;; *) usage "unknown gate '${GATE:-}'";; esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
N="${ID#SPEC-}"; [[ "$N" =~ ^[0-9]+$ ]] && printf -v N '%04d' "$((10#$N))"
SID="SPEC-$N"
DIR="$ROOT/.claude/runtime/$SID"
STATE="$DIR/state.json"

# --- parallel: absent plan is safe only with an explicit sequential declaration ---
if [ "$GATE" = parallel ]; then
  PLAN="$DIR/parallel-plan.json"
  if [ ! -f "$PLAN" ]; then
    [ -f "$STATE" ] || usage "missing state.json for $SID"
    grep -Eq '"execution_mode"[[:space:]]*:[[:space:]]*"sequential"' "$STATE" && exit 0
    echo 'check-slice-gate: execution_mode must be sequential or parallel-plan.json must exist' >&2
    exit 2
  fi
  command -v python3 >/dev/null 2>&1 || { echo 'check-slice-gate: python3 is required to validate parallel-plan.json.' >&2; exit 1; }
  python3 - "$PLAN" <<'PY'
import json, sys
try:
    plan = json.load(open(sys.argv[1], encoding='utf-8'))
except Exception as exc:
    print(f"check-slice-gate: invalid JSON in {sys.argv[1]}: {exc}", file=sys.stderr)
    sys.exit(1)
if plan.get('serialize') is True:
    sys.exit(0)                    # rule 10 fallback: serialized work is always safe
failures = []
ws = plan.get('workstreams') or []
if not ws:
    failures.append('no workstreams declared')
ids = [str(w.get('id', '')) for w in ws]
top = str(plan.get('integrator') or '')
flagged = [w for w in ws if w.get('integrator') is True]
if not ((top and top in ids) or len(flagged) == 1):
    failures.append('exactly one integrator required (top-level integrator naming a workstream, or one workstream with integrator:true)')
def prefix(path):
    path = str(path).strip()
    for suffix in ('/**', '/*', '*'):
        if path.endswith(suffix):
            path = path[:-len(suffix)]
            break
    return path.rstrip('/')
def overlaps(a, b):
    if a == b or a == '' or b == '':
        return True
    if b.startswith(a) and len(b) > len(a) and b[len(a)] == '/':
        return True
    if a.startswith(b) and len(a) > len(b) and a[len(b)] == '/':
        return True
    return False
for i in range(len(ws)):
    for j in range(i + 1, len(ws)):
        for pa in (ws[i].get('paths') or []):
            for pb in (ws[j].get('paths') or []):
                if overlaps(prefix(pa), prefix(pb)):
                    failures.append(f"workstreams '{ids[i]}' and '{ids[j]}' overlap on paths '{pa}' / '{pb}'")
owners = {}
for w in ws:
    for r in (w.get('shared_resources') or []):
        owners.setdefault(str(r), []).append(w)
for resource, sharers in owners.items():
    if len(sharers) >= 2:
        for w in sharers:
            if resource not in [str(x) for x in (w.get('isolated') or [])]:
                failures.append(f"shared resource '{resource}' used by multiple workstreams but not isolated in workstream '{w.get('id')}'")
if failures:
    for line in failures:
        print(f"check-slice-gate: {line}", file=sys.stderr)
    sys.exit(2)
sys.exit(0)
PY
  exit $?
fi

# --- flat-field gates: read state.json ---
[ -f "$STATE" ] || { echo "check-slice-gate: missing state.json for $SID." >&2; exit 1; }

field() {
  local line
  line="$(grep -oE "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$STATE" 2>/dev/null | head -n1)"
  [ -n "$line" ] || return 0
  printf '%s' "$line" | sed -E "s/.*:[[:space:]]*\"([^\"]*)\".*/\1/"
}

FIT="$(field fit)"; VERIFY="$(field verify_slice)"; ACCEPT="$(field acceptance_evidence)"
E2E="$(field e2e)"; CSHA="$(field candidate_sha)"; STATUS="$(field status)"

if [ "$GATE" = fit ]; then
  command -v python3 >/dev/null 2>&1 || usage 'python3 is required to validate fit evidence'
  python3 - "$STATE" <<'PY'
import json, sys
try: s=json.load(open(sys.argv[1], encoding='utf-8'))
except Exception as e: print(f'check-slice-gate: invalid state.json: {e}', file=sys.stderr); sys.exit(1)
signals=s.get('fit_signals')
if not isinstance(signals,list) or any(type(x) is not int or x < 1 or x > 8 for x in signals) or len(signals)!=len(set(signals)):
 print('check-slice-gate: fit_signals must contain unique integers 1..8', file=sys.stderr); sys.exit(2)
if s.get('fit')=='FIT' and (len(signals)>=3 or s.get('fit_unsafe_condition') is True):
 print('check-slice-gate: declared FIT conflicts with detected signals/unsafe condition', file=sys.stderr); sys.exit(2)
PY
  rc=$?; [ "$rc" = 0 ] || exit "$rc"
  case "$FIT" in
    FIT) exit 0 ;;
    TOO_LARGE) echo 'check-slice-gate: fit=TOO_LARGE => split required.' >&2; exit 2 ;;
    HUMAN_DECISION_REQUIRED) echo 'check-slice-gate: fit=HUMAN_DECISION_REQUIRED => owner decision.' >&2; exit 2 ;;
    '') echo 'check-slice-gate: fit not classified.' >&2; exit 2 ;;
    *) echo "check-slice-gate: fit is '$FIT' (must be FIT)." >&2; exit 2 ;;
  esac
fi

fail=0
note() { echo "check-slice-gate: $1" >&2; fail=2; }

[ "$FIT" = FIT ] || note "fit is '$FIT' (must be FIT)"
[ "$VERIFY" = pass ] || note "verify_slice is '$VERIFY' (must be pass)"
[ "$ACCEPT" = complete ] || note "acceptance_evidence is '$ACCEPT' (must be complete)"
case "$E2E" in pass|not_applicable) ;; *) note "e2e is '$E2E' (must be pass or not_applicable)";; esac
if [ -z "$CSHA" ]; then
  note 'candidate_sha is absent'
else
  HEAD_SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || true)"
  if [ -z "$HEAD_SHA" ]; then note 'candidate_sha cannot be compared: no current HEAD'
  elif [ "$CSHA" != "$HEAD_SHA" ]; then note "candidate_sha '$CSHA' does not match current HEAD '$HEAD_SHA'"
  fi
fi
[ -f "$DIR/dev-verification.md" ] || note 'dev-verification.md is missing'
[ -f "$DIR/dev-verification.json" ] || note 'dev-verification.json is missing'
if [ -f "$DIR/dev-verification.json" ]; then
  command -v python3 >/dev/null 2>&1 || usage 'python3 is required to validate dev-verification.json'
  python3 - "$DIR/dev-verification.json" "$CSHA" <<'PY' || fail=2
import json,sys
try: e=json.load(open(sys.argv[1],encoding='utf-8'))
except Exception as x: print(f'check-slice-gate: invalid dev-verification.json: {x}',file=sys.stderr);sys.exit(2)
bad=[]
if e.get('candidate_sha') != sys.argv[2]: bad.append('evidence candidate_sha mismatch')
cmd=e.get('commands') or []
if not cmd or any(not x.get('command') or x.get('status')!='pass' or not x.get('evidence') for x in cmd): bad.append('commands require command/status=pass/evidence')
acs=e.get('acceptance_criteria') or []
if not acs or any(not x.get('criterion') or x.get('status')!='pass' or not x.get('evidence') for x in acs): bad.append('acceptance criteria require criterion/status=pass/evidence')
if e.get('skipped_mandatory_controls'): bad.append('mandatory controls were skipped')
for x in bad: print('check-slice-gate: '+x,file=sys.stderr)
sys.exit(2 if bad else 0)
PY
fi
if [ "$E2E" = not_applicable ] && ! grep -Eq '"e2e_applicable"[[:space:]]*:[[:space:]]*false' "$STATE"; then note 'e2e=not_applicable requires e2e_applicable=false'; fi

if [ "$GATE" = qa-preflight ]; then
  [ "$STATUS" = DEV_VERIFIED ] || note "status is '$STATUS' (must be DEV_VERIFIED)"
fi

exit "$fail"
