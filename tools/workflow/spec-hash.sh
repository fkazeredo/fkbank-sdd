#!/usr/bin/env bash
set -euo pipefail
[ "$#" -eq 1 ] || { echo 'usage: spec-hash.sh <spec-path>' >&2; exit 2; }
python3 - "$1" <<'PY'
import hashlib,re,sys
text=open(sys.argv[1],encoding='utf-8',newline=None).read()
text=re.sub(r'^owner_approved_at:\s*.*$', 'owner_approved_at: null', text, flags=re.M)
text=re.sub(r'^owner_approved_hash:\s*.*$', 'owner_approved_hash: null', text, flags=re.M)
print(hashlib.sha256(text.encode('utf-8')).hexdigest())
PY
