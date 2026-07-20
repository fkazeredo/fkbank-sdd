#!/usr/bin/env bash
# Print every slice's runtime state (read-only).
set -u
found=0
for f in .claude/runtime/*/state.json; do
  [ -f "$f" ] || continue; found=1
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(f"{d.get(\"slice\",\"?\"):<12} {d.get(\"status\",\"?\"):<24} risk={d.get(\"risk\",\"?\")} updated={d.get(\"updated\",\"?\")}")' "$f"
done
[ "$found" -eq 0 ] && echo "check-state: no active slices in .claude/runtime/."
exit 0
