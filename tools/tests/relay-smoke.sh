#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; RUN="$(mktemp -d)"; trap 'rm -rf "$RUN" "$ROOT/.claude/runtime/SPEC-0099"' EXIT
run(){ local name="$1";shift; local start=$SECONDS; echo "START $name"; timeout 60 "$@"; echo "PASS  $name $((SECONDS-start))s"; }
# expect a specific exit code (including a non-zero one, which run() cannot assert under set -e).
expect(){ local name="$1" want="$2";shift 2; local start=$SECONDS; echo "START $name"; local got=0; timeout 60 "$@" >/dev/null 2>&1 || got=$?; [ "$got" = "$want" ] || { echo "FAIL  $name: expected exit $want, got $got" >&2; exit 1; }; echo "PASS  $name $((SECONDS-start))s"; }
# Write a frontmatter-complete fixture spec ($dir/<id>-fixture.md); args after $sf are acceptance criteria.
mkspec(){ local dir="$1" id="$2" dep="$3" sf="$4"; shift 4; { echo '---'; echo "id: $id"; echo "title: Fixture $id"; echo 'status: DRAFT'; echo 'risk: R1'; echo 'profile: light'; echo 'modules: []'; echo "depends_on: $dep"; echo 'relevant_adrs: []'; echo 'reading_list:'; echo '  domain: []'; echo '  architecture: []'; echo 'planned_sprint: null'; echo 'planned_release: null'; echo 'owner_approved_at: null'; echo 'owner_approved_hash: null'; [ -z "$sf" ] || echo "split_from: $sf"; echo '---'; echo; echo "# $id - Fixture"; echo; echo '## Business rules'; echo '- BR-1 - fixture rule.'; echo; echo '## Acceptance criteria'; for ac in "$@"; do echo "- [ ] $ac"; done; echo; echo '## Decision log'; echo '- DL-0001 - 2026-07-20 - fixture - decided by policy'; } > "$dir/$id-fixture.md"; }
# Materialize the gitignored gate fixture at the real root (check-slice-gate needs a resolvable HEAD).
mk99(){
  mkdir -p "$ROOT/.claude/runtime/SPEC-0099"
  python3 - "$1" "$ROOT/.claude/runtime/SPEC-0099/state.json" <<'PY'
import json,sys
s=json.loads(sys.argv[1]); s.setdefault('fit_signals',[1]); s.setdefault('fit_unsafe_condition',False); s.setdefault('execution_mode','sequential'); s.setdefault('e2e_applicable',s.get('e2e')!='not_applicable')
open(sys.argv[2],'w',encoding='utf-8').write(json.dumps(s,separators=(',',':')))
PY
  printf 'fixture report' > "$ROOT/.claude/runtime/SPEC-0099/dev-verification.md"
  printf '{"candidate_sha":"%s","commands":[{"command":"tools/quality/verify-slice","status":"pass","evidence":"exit 0"}],"acceptance_criteria":[{"criterion":"fixture","status":"pass","evidence":"FixtureIT"}],"skipped_mandatory_controls":[],"known_limitations":[]}' "$(git -C "$ROOT" rev-parse HEAD)" > "$ROOT/.claude/runtime/SPEC-0099/dev-verification.json"
}
GATE="$ROOT/tools/workflow/check-slice-gate.sh"; SPLIT="$ROOT/tools/workflow/check-split.sh"; SPECS="$ROOT/tools/workflow/validate-specs.sh"
ACA='returns 200 on the happy path'; ACB='returns CONFLICT on a duplicate request'; ACG='replaying the request is idempotent'

run validate-specs "$ROOT/tools/workflow/validate-specs.sh"
run validate-doc-language "$ROOT/tools/workflow/validate-doc-language.sh"
run spec-hash bash -c 's=$(ls "$1"/docs/specs/SPEC-[0-9][0-9][0-9][0-9]-*.md 2>/dev/null | head -1); [ -n "$s" ] || { echo "no spec to hash" >&2; exit 1; }; a=$("$1/tools/workflow/spec-hash.sh" "$s"); [[ "$a" =~ ^[0-9a-f]{64}$ ]]' _ "$ROOT"
run shell-syntax bash -n "$ROOT"/.claude/hooks/*.sh "$ROOT"/tools/*/*.sh
run manual-skills bash -c '! grep -L "^disable-model-invocation: true$" "$1"/.claude/skills/*/SKILL.md | grep .' _ "$ROOT"
run settings-shape python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); raw=open(sys.argv[1]).read(); assert not __import__("re").search(r"\x27[^\x27]*\$\{CLAUDE_PROJECT_DIR\}[^\x27]*\x27",raw); hs=[]; [hs.extend(x["hooks"]) for group in d["hooks"].values() for x in group]; assert all(h.get("command")=="powershell.exe" and h.get("args") and h.get("timeout") for h in hs)' "$ROOT/.claude/settings.json"
run machine-orchestration python3 -c 'import json,sys,pathlib; r=pathlib.Path(sys.argv[1]); d=json.load(open(r/".claude/settings.json")); assert "Agent" in d["permissions"]["allow"]; assert "Agent" not in d["permissions"]["deny"]; p=(r/".claude/workflow-policy.yml").read_text(); expected=("mode: ultracode","effort: xhigh","dynamic_workflows: unrestricted","agent_teams: unrestricted","background_tasks: unrestricted","repository_numeric_limits: none"); assert all(x in p for x in expected); assert all((r/".claude/skills"/x/"SKILL.md").is_file() for x in ("deliver-spec","deliver-sprint","close-sprint","security-assurance"))' "$ROOT"
run workflow-guide-workers python3 -c 'import pathlib,sys; r=pathlib.Path(sys.argv[1]); guides=[r/"docs/manual/operational/en-US/WORKFLOW-GUIDE.md",r/"docs/manual/operational/pt-BR/WORKFLOW-GUIDE.md"]; workers=("qa-engineer","pr-reviewer","security-assurance-engineer"); commands=("/deliver-spec","/deliver-sprint","/close-sprint"); assert all(g.is_file() and all(f"### `{w}`" in g.read_text(encoding="utf-8") for w in workers) and all(c in g.read_text(encoding="utf-8") for c in commands) for g in guides)' "$ROOT"

# close-sprint is a closure gate, not a release: it carries no release-only state, declares the
# terminals, runs Security Assurance, drafts release notes, and the policy records it does not own release.
run close-sprint-closure-gate python3 -c 'import sys,pathlib; r=pathlib.Path(sys.argv[1]); skill=(r/".claude/skills/close-sprint/SKILL.md").read_text(encoding="utf-8"); assert all(t not in skill for t in ("AWAITING_MAIN_MERGE","AWAITING_DEVELOP_SYNC_MERGE","SPRINT_RELEASED","RELEASE_PREPARING","RELEASE_FINALIZING")), "close-sprint still carries a release state"; assert all(t in skill for t in ("SPRINT_CLOSED","SPRINT_INCOMPLETE","Security Assurance","docs/release-notes/")), "close-sprint missing required closure content"; p=(r/".claude/workflow-policy.yml").read_text(encoding="utf-8"); assert "close_sprint_owns_release: true" not in p; assert "close_sprint_owns_release: false" in p' "$ROOT"

HEAD="$(git -C "$ROOT" rev-parse HEAD)"
# (1)(2) fit gate reads state.json.fit.
STATE_GREEN="\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\""
mk99 "{$STATE_GREEN}"
expect 'gate fit FIT' 0 bash "$GATE" 0099 fit
mk99 "{${STATE_GREEN/\"fit\":\"FIT\"/\"fit\":\"TOO_LARGE\"}}"
expect 'gate fit TOO_LARGE' 2 bash "$GATE" 0099 fit
# (8) dev-verified gate: all signals green passes; a failed verify-slice is blocked.
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\"}"
expect 'gate dev-verified green' 0 bash "$GATE" 0099 dev-verified
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"fail\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\"}"
expect 'gate dev-verified verify-slice fail' 2 bash "$GATE" 0099 dev-verified
# (9) dev-verified gate blocks a failed E2E.
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"fail\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\"}"
expect 'gate dev-verified e2e fail' 2 bash "$GATE" 0099 dev-verified
# (11) qa-preflight passes on fresh DEV_VERIFIED evidence; a stale candidate SHA is blocked.
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\"}"
expect 'gate qa-preflight fresh' 0 bash "$GATE" 0099 qa-preflight
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"0000000000000000000000000000000000000000\"}"
expect 'gate qa-preflight stale' 2 bash "$GATE" 0099 qa-preflight
# (10) qa-preflight must not mutate state.json on the failing slice.
echo "START gate qa-preflight read-only"
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"0000000000000000000000000000000000000000\"}"
cp "$ROOT/.claude/runtime/SPEC-0099/state.json" "$RUN/pre99"
pf=0; bash "$GATE" 0099 qa-preflight >/dev/null 2>&1 || pf=$?
[ "$pf" = 2 ] || { echo "FAIL  gate qa-preflight read-only: expected exit 2, got $pf" >&2; exit 1; }
cmp -s "$RUN/pre99" "$ROOT/.claude/runtime/SPEC-0099/state.json" || { echo "FAIL  gate qa-preflight read-only: state.json was mutated" >&2; exit 1; }
echo "PASS  gate qa-preflight read-only"
# (14) parallel gate: disjoint ownership passes, overlap is blocked unless serialized.
mk99 "{\"slice\":\"SPEC-0099\",\"status\":\"DEV_VERIFIED\",\"fit\":\"FIT\",\"fit_signals\":[1],\"fit_unsafe_condition\":false,\"execution_mode\":\"sequential\",\"verify_slice\":\"pass\",\"e2e\":\"pass\",\"e2e_applicable\":true,\"acceptance_evidence\":\"complete\",\"candidate_sha\":\"$HEAD\"}"
printf '%s' '{"integrator":"ws-a","serialize":false,"workstreams":[{"id":"ws-a","paths":["backend/a/**"]},{"id":"ws-b","paths":["backend/b/**"]}]}' > "$ROOT/.claude/runtime/SPEC-0099/parallel-plan.json"
expect 'gate parallel disjoint' 0 bash "$GATE" 0099 parallel
printf '%s' '{"integrator":"ws-a","serialize":false,"workstreams":[{"id":"ws-a","paths":["backend/shared/**"]},{"id":"ws-b","paths":["backend/shared/**"]}]}' > "$ROOT/.claude/runtime/SPEC-0099/parallel-plan.json"
expect 'gate parallel overlap' 2 bash "$GATE" 0099 parallel
printf '%s' '{"integrator":"ws-a","serialize":true,"workstreams":[{"id":"ws-a","paths":["backend/shared/**"]},{"id":"ws-b","paths":["backend/shared/**"]}]}' > "$ROOT/.claude/runtime/SPEC-0099/parallel-plan.json"
expect 'gate parallel overlap serialized' 0 bash "$GATE" 0099 parallel

# (3) check-split accepts a split whose children carry split_from and cover every criterion.
S3="$RUN/split3/docs/specs"; mkdir -p "$S3"
mkspec "$S3" SPEC-9002 '[]' '' "$ACA" "$ACB" "$ACG"; mkspec "$S3" SPEC-9003 '[]' 'SPEC-9002' "$ACA" "$ACB"; mkspec "$S3" SPEC-9004 '[]' 'SPEC-9002' "$ACG"
expect 'check-split complete coverage' 0 bash "$SPLIT" "$S3/SPEC-9002-fixture.md" "$S3/SPEC-9003-fixture.md" "$S3/SPEC-9004-fixture.md"
# (4) check-split must not modify the fixtures.
echo "START check-split read-only"
S4="$RUN/split4/docs/specs"; mkdir -p "$S4"
mkspec "$S4" SPEC-9002 '[]' '' "$ACA" "$ACB" "$ACG"; mkspec "$S4" SPEC-9003 '[]' 'SPEC-9002' "$ACA" "$ACB"; mkspec "$S4" SPEC-9004 '[]' 'SPEC-9002' "$ACG"
before="$(cd "$S4" && for f in *; do cksum "$f"; done)"
bash "$SPLIT" "$S4/SPEC-9002-fixture.md" "$S4/SPEC-9003-fixture.md" "$S4/SPEC-9004-fixture.md" >/dev/null 2>&1 || true
after="$(cd "$S4" && for f in *; do cksum "$f"; done)"
[ "$before" = "$after" ] || { echo "FAIL  check-split read-only: fixtures were mutated" >&2; exit 1; }
echo "PASS  check-split read-only"
# (5) check-split rejects a split that drops an acceptance criterion (gamma never lands in a child).
S5="$RUN/split5/docs/specs"; mkdir -p "$S5"
mkspec "$S5" SPEC-9002 '[]' '' "$ACA" "$ACB" "$ACG"; mkspec "$S5" SPEC-9003 '[]' 'SPEC-9002' "$ACA" "$ACB"; mkspec "$S5" SPEC-9004 '[]' 'SPEC-9002'
expect 'check-split dropped AC' 2 bash "$SPLIT" "$S5/SPEC-9002-fixture.md" "$S5/SPEC-9003-fixture.md" "$S5/SPEC-9004-fixture.md"
# (7) check-split rejects a child that omits split_from.
S7="$RUN/split7/docs/specs"; mkdir -p "$S7"
mkspec "$S7" SPEC-9002 '[]' '' "$ACA" "$ACB" "$ACG"; mkspec "$S7" SPEC-9003 '[]' 'SPEC-9002' "$ACA" "$ACB"; mkspec "$S7" SPEC-9004 '[]' '' "$ACG"
expect 'check-split missing split_from' 2 bash "$SPLIT" "$S7/SPEC-9002-fixture.md" "$S7/SPEC-9003-fixture.md" "$S7/SPEC-9004-fixture.md"

# (6) validate-specs rejects a depends_on that names an absent spec.
V6="$RUN/vs6/docs/specs"; mkdir -p "$V6"; mkspec "$V6" SPEC-9009 '[SPEC-9999]' '' "$ACA"
expect 'validate-specs missing dependency' 1 bash -c 'cd "$1" && "$2"' _ "$RUN/vs6" "$SPECS"
# (7) validate-specs never existence-checks split_from (SPEC-0002 is absent yet the spec is valid).
V7="$RUN/vs7/docs/specs"; mkdir -p "$V7"; mkspec "$V7" SPEC-9002 '[]' 'SPEC-0002' "$ACA"
run 'validate-specs split_from unchecked' bash -c 'cd "$1" && "$2"' _ "$RUN/vs7" "$SPECS"

# (13) CHECKPOINTED must appear in the workflow-policy.yml resumable_context_states list.
run resumable-context-states bash -c 'sed -n "/^resumable_context_states:/,/^[A-Za-z_]/p" "$1/.claude/workflow-policy.yml" | grep -q "CHECKPOINTED"' _ "$ROOT"

# (12) stop-guard treats CHECKPOINTED as terminal only with a checkpoint artifact.
echo "START stop-guard CHECKPOINTED"
SG="$RUN/sg"; mkdir -p "$SG/.claude/runtime/SPEC-0099"
printf 'SPEC-0099' > "$SG/.claude/runtime/current-slice"
printf '[]' > "$SG/.claude/runtime/SPEC-0099/metrics.json"
printf '%s' '{"slice":"SPEC-0099","status":"CHECKPOINTED"}' > "$SG/.claude/runtime/SPEC-0099/state.json"
sg=0; printf '%s' '{}' | CLAUDE_PROJECT_DIR="$SG" bash "$ROOT/.claude/hooks/stop-guard.sh" >/dev/null 2>&1 || sg=$?
[ "$sg" = 2 ] || { echo "FAIL  stop-guard CHECKPOINTED: without checkpoint.md expected 2, got $sg" >&2; exit 1; }
printf 'checkpoint' > "$SG/.claude/runtime/SPEC-0099/checkpoint.md"
sg=0; printf '%s' '{}' | CLAUDE_PROJECT_DIR="$SG" bash "$ROOT/.claude/hooks/stop-guard.sh" >/dev/null 2>&1 || sg=$?
[ "$sg" = 0 ] || { echo "FAIL  stop-guard CHECKPOINTED: with checkpoint.md expected 0, got $sg" >&2; exit 1; }
echo "PASS  stop-guard CHECKPOINTED"

echo 'relay-smoke: PASS'
