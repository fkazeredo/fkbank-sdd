#!/usr/bin/env bash
# Split completeness + provenance check (read-only). Exit 0 = pass, 2 = violation, 1 = usage/IO.
# Mirrors check-split.ps1: (a) every acceptance-criterion line in the original appears verbatim
# (trimmed) in at least one child, and (b) every child carries split_from pointing at the
# original's id. Pure bash+grep (no python3). It never writes any file.
set -uo pipefail

ORIGINAL="${1:-}"
[ -n "$ORIGINAL" ] || { echo 'usage: check-split.sh <original-spec> <child-spec> [<child-spec>...]' >&2; exit 1; }
shift
[ "$#" -ge 1 ] || { echo 'check-split: missing child specs' >&2; echo 'usage: check-split.sh <original-spec> <child-spec> [<child-spec>...]' >&2; exit 1; }
[ -f "$ORIGINAL" ] || { echo "check-split: original not found: $ORIGINAL" >&2; exit 1; }
CHILDREN=("$@")
for c in "${CHILDREN[@]}"; do [ -f "$c" ] || { echo "check-split: child not found: $c" >&2; exit 1; }; done

ORIG_ID="$(grep -oE '^id:[[:space:]]*SPEC-[0-9]{4}' "$ORIGINAL" | head -n1 | grep -oE 'SPEC-[0-9]{4}')"
[ -n "$ORIG_ID" ] || { echo "check-split: cannot determine original id in $ORIGINAL" >&2; exit 1; }

fail=0

# (a) every acceptance-criterion line in the original appears (trimmed) in >=1 child
CHILD_LINES="$(sed -E 's/^[[:space:]]*//; s/[[:space:]]*$//' "${CHILDREN[@]}")"
while IFS= read -r ac; do
  [ -n "$ac" ] || continue
  if ! grep -Fxq -e "$ac" <<<"$CHILD_LINES"; then
    echo "check-split: lost acceptance criterion: $ac" >&2
    fail=2
  fi
done < <(grep -E '^[[:space:]]*- \[[ xX]\]' "$ORIGINAL" | sed -E 's/^[[:space:]]*//; s/[[:space:]]*$//')

# (b) every child carries split_from matching the original id
for c in "${CHILDREN[@]}"; do
  if ! grep -Eq "^split_from:[[:space:]]*${ORIG_ID}[[:space:]]*$" "$c"; then
    echo "check-split: child $c missing/mismatched split_from (expected $ORIG_ID)" >&2
    fail=2
  fi
done

[ "$fail" -eq 0 ] || exit 2
echo "check-split: PASS (${#CHILDREN[@]} children)"
exit 0
