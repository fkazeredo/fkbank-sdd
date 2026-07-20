#!/usr/bin/env bash
# RELAY verify-slice (target: <= 15 min) — full backend verify (tests + ArchUnit + coverage
# floors + OpenAPI drift, per docs/ARCHITECTURE.md) + frontend lint/test/build.
set -uo pipefail
START=$(date +%s); FAIL=0; RAN=0
if [ -f backend/mvnw ] || [ -f mvnw ]; then
  RAN=1; DIR=$( [ -f backend/mvnw ] && echo backend || echo . )
  ( cd "$DIR" && timeout 900 ./mvnw -q verify ) || FAIL=1
fi
if [ -f frontend/package.json ]; then
  RAN=1
  ( cd frontend && { [ -d node_modules ] || npm ci; } && timeout 300 npm run -s lint && timeout 600 npm test -- --watch=false && timeout 600 npm run -s build ) || FAIL=1
fi
[ "$RAN" -eq 0 ] && echo "verify-slice: nothing to verify yet (pre-bootstrap). OK by definition."
echo "verify-slice: $((($(date +%s)-START)))s, exit=$FAIL"; exit $FAIL
