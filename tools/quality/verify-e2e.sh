#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; ART="$ROOT/.claude/runtime/e2e-artifacts"
if [ ! -f "$ROOT/compose.e2e.yaml" ] || [ ! -f "$ROOT/frontend/package.json" ]; then echo 'verify-e2e: planned/not-applicable (E2E stack or frontend is absent).'; exit 0; fi
mkdir -p "$ART"
cleanup(){ docker compose -f "$ROOT/compose.e2e.yaml" down -v >"$ART/docker-down.log" 2>&1 || true; }; trap cleanup EXIT
# --build is not optional: `up` reuses an existing image, so without it a machine that already
# has an image from an earlier commit verifies THAT image and reports PASS. CI never sees this
# because it starts with an empty image store, which is what makes the failure mode expensive -
# it only bites locally, and it looks like a green run.
timeout 600 docker compose -f "$ROOT/compose.e2e.yaml" up -d --wait --build >"$ART/docker-up.log" 2>&1
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci; } && timeout 900 npm run -s e2e)
echo 'verify-e2e: PASS'
