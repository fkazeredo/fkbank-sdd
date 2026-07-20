#!/usr/bin/env bash
# RELAY verify-fast (target: <= 5 min) — compile + fast unit tests + frontend lint.
# Thin wrapper over the canonical build commands; no decisions of its own.
set -uo pipefail
START=$(date +%s); FAIL=0; RAN=0
if [ -f backend/mvnw ] || [ -f mvnw ]; then
  RAN=1; DIR=$( [ -f backend/mvnw ] && echo backend || echo . )
  ( cd "$DIR" && timeout 300 ./mvnw -q -DskipITs=true test ) || FAIL=1
fi
# Emulators are separate services with their own build. Their tests are the only thing proving
# an emulated rail still behaves the way the bank's tests assume, so they run here rather than
# being compiled only when someone happens to build a container.
for EMULATOR in emulators/*/; do
  [ -f "$EMULATOR/mvnw" ] || continue
  RAN=1
  ( cd "$EMULATOR" && timeout 300 ./mvnw -q -DskipITs=true test ) || FAIL=1
done
if [ -f frontend/package.json ]; then
  RAN=1
  ( cd frontend && { [ -d node_modules ] || npm ci; } && timeout 300 npm run -s lint ) || FAIL=1
fi
[ "$RAN" -eq 0 ] && echo "verify-fast: nothing to verify yet (no backend/mvnw, no frontend/package.json) — repository pre-bootstrap. OK by definition."
echo "verify-fast: $((($(date +%s)-START)))s, exit=$FAIL"; exit $FAIL
