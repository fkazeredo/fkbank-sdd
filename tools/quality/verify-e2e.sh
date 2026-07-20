#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; ART="$ROOT/.claude/runtime/e2e-artifacts"
if [ ! -f "$ROOT/compose.e2e.yaml" ] || [ ! -f "$ROOT/frontend/package.json" ]; then echo 'verify-e2e: planned/not-applicable (E2E stack or frontend is absent).'; exit 0; fi
mkdir -p "$ART"
cleanup(){ docker compose -f "$ROOT/compose.e2e.yaml" down -v >"$ART/docker-down.log" 2>&1 || true; }; trap cleanup EXIT
timeout 300 docker compose -f "$ROOT/compose.e2e.yaml" up -d --wait >"$ART/docker-up.log" 2>&1
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci; } && timeout 900 npm run -s e2e)
echo 'verify-e2e: PASS'
