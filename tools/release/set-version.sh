#!/usr/bin/env bash
set -euo pipefail
V="${1:-}"; [[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]] || { echo 'set-version: version must be X.Y.Z or X.Y.Z-SNAPSHOT.' >&2; exit 2; }
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
FILES=(); for f in "$ROOT/pom.xml" "$ROOT/backend/pom.xml" "$ROOT/frontend/package.json" "$ROOT/frontend/package-lock.json"; do [ -f "$f" ] && FILES+=("$f"); done
[ "${#FILES[@]}" -gt 0 ] || { echo 'set-version: nothing to version yet.' >&2; exit 1; }
for f in "${FILES[@]}"; do mkdir -p "$TMP$(dirname "${f#$ROOT}")"; cp "$f" "$TMP${f#$ROOT}"; done
rollback(){ for f in "${FILES[@]}"; do cp "$TMP${f#$ROOT}" "$f"; done; echo 'set-version: failed; restored version files. Inspect tool output before retrying.' >&2; }
trap 'code=$?; [ $code -eq 0 ] || rollback; rm -rf "$TMP"; exit $code' EXIT
if [ -x "$ROOT/backend/mvnw" ]; then (cd "$ROOT/backend" && timeout 300 ./mvnw -q versions:set -DnewVersion="$V" -DgenerateBackupPoms=false)
elif [ -x "$ROOT/mvnw" ]; then (cd "$ROOT" && timeout 300 ./mvnw -q versions:set -DnewVersion="$V" -DgenerateBackupPoms=false)
elif [ -f "$ROOT/backend/pom.xml" ] || [ -f "$ROOT/pom.xml" ]; then echo 'set-version: pom.xml exists but no executable Maven wrapper was found.' >&2; exit 1
fi
FRONT="${V%-SNAPSHOT}"; if [ -f "$ROOT/frontend/package.json" ]; then (cd "$ROOT/frontend" && timeout 180 npm version --no-git-tag-version "$FRONT" >/dev/null); fi
echo "set-version: confirmed $V (frontend $FRONT)"
