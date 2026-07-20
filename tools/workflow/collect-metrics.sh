#!/usr/bin/env bash
# Aggregate .claude/runtime/*/metrics.json into CSV on stdout.
set -u
echo "slice,phase,duration_min,model,effort,compactions,files_read,retries"
for f in .claude/runtime/*/metrics.json; do
  [ -f "$f" ] || continue
  python3 -c 'import json,sys
rows=json.load(open(sys.argv[1]))
rows=rows if isinstance(rows,list) else [rows]
slice=sys.argv[1].split("/")[-2]
for r in rows:
  print(",".join(str(r.get(k,"")) for k in ["slice","phase","duration_min","model","effort","compactions","files_read","retries"]) if r.get("slice") else ",".join([slice]+[str(r.get(k,"")) for k in ["phase","duration_min","model","effort","compactions","files_read","retries"]]))' "$f"
done
exit 0
