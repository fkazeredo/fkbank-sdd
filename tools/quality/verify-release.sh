#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; START=$(date +%s); FAIL=0
"$ROOT/tools/quality/verify-slice.sh" || FAIL=1
"$ROOT/tools/quality/verify-e2e.sh" || FAIL=1
if [ -x "$ROOT/backend/mvnw" ]; then (cd "$ROOT/backend" && timeout 600 ./mvnw -q -DskipTests package) || FAIL=1
elif [ -x "$ROOT/mvnw" ]; then (cd "$ROOT" && timeout 600 ./mvnw -q -DskipTests package) || FAIL=1; fi
echo "verify-release: $(($(date +%s)-START))s, exit=$FAIL";exit "$FAIL"
