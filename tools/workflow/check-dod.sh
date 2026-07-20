#!/usr/bin/env bash
set -euo pipefail
ID="${1:-}"; MODE="${2:-}"; [[ "$MODE" = --pre-pr || "$MODE" = --post-pr ]] || { echo 'usage: check-dod.sh <id> --pre-pr|--post-pr' >&2; exit 2; }
N="${ID#SPEC-}"; [[ "$N" =~ ^[0-9]+$ ]] && printf -v N '%04d' "$((10#$N))"; SID="SPEC-$N"
shopt -s nullglob; SPECS=(docs/specs/*"$N"*); [ "${#SPECS[@]}" -eq 1 ] || { echo "check-dod: expected exactly one spec for $ID, found ${#SPECS[@]}." >&2; exit 1; }
DIR=".claude/runtime/$SID"; [ -d "$DIR" ] || DIR=".claude/runtime/$ID"; FAIL=0
need(){ [ -f "$1" ] && echo "OK   $2" || { echo "MISS $2"; FAIL=1; }; }
grep -Eq '^status:[[:space:]]*(READY|IN_PROGRESS|IMPLEMENTED)[[:space:]]*$' "${SPECS[0]}" || { echo 'MISS approved spec status'; FAIL=1; }
need "$DIR/state.json" state.json; need "$DIR/dev-verification.md" 'developer verification'; need "$DIR/metrics.json" 'phase metrics'
readarray -t S < <(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(d.get("status","")); print(d.get("risk","")); print(d.get("pr_number") or d.get("pr_url") or ""); print(d.get("next_command", ""))' "$DIR/state.json" 2>/dev/null) || exit 1
STATUS="${S[0]}"; RISK="${S[1]}"; PR="${S[2]}"; NEXT="${S[3]}"
case "$RISK" in R2|R3|R4) need "$DIR/qa-report.md" "QA report ($RISK)"; need "$DIR/plan.md" 'approved plan'; grep -q PLAN_APPROVED "$DIR/plan.md" 2>/dev/null || { echo 'MISS explicit PLAN_APPROVED record'; FAIL=1; };; esac
[[ "$STATUS" != BLOCKED && "$STATUS" != HUMAN_DECISION_REQUIRED ]] || { echo "MISS blocking state $STATUS"; FAIL=1; }
tools/git/check-safe-branch.sh pr --allow-dirty || FAIL=1
if [ "$MODE" = --pre-pr ]; then [ "$STATUS" = PR_PREPARING ] || { echo 'MISS pre-pr requires PR_PREPARING'; FAIL=1; }
else case "$STATUS" in PR_OPEN|CI_PENDING|CI_FAILED|AWAITING_HUMAN_REVIEW) ;; *) echo 'MISS post-pr terminal state'; FAIL=1;; esac; [ -n "$PR" ] || { echo 'MISS PR number or URL'; FAIL=1; }; need "$DIR/pr-body.md" 'filled PR body'; [ -n "$NEXT" ] || { echo 'MISS next_command'; FAIL=1; }; fi
exit "$FAIL"
