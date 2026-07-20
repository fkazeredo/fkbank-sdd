#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
failed=0

while IFS= read -r file; do
  rel="${file#"$ROOT/"}"
  case "$rel" in docs/manual/*|.git/*|.claude/runtime/*) continue ;; esac
  # The forbidden thing is a paired *documentation* edition outside docs/manual — not an i18n source
  # resource that legitimately carries a locale in its name (e.g. the app's single en-US string file).
  case "$rel" in *.md|*.markdown|*.adoc|*.rst|*.txt) ;; *) continue ;; esac
  if [[ "$rel" =~ (^|/)(pt[-_]BR|en[-_]US)(/|$) ]] || [[ "$(basename "$rel")" =~ (pt[-_]BR|en[-_]US) ]]; then
    echo "localized artifact outside docs/manual: $rel" >&2
    failed=1
  fi
done < <(find "$ROOT" -type d \( -name .git -o -name node_modules -o -name target -o -name dist -o -name build \) -prune -o -type f -print)

pattern='não|também|apenas|deve|deverá|entrega|decisão|decisões|segurança|arquitetura|domínio|usuário|usuários|licença|visão|objetivo|quando|então|alteração|aprovação|revisão|português'
while IFS= read -r file; do
  rel="${file#"$ROOT/"}"
  case "$rel" in docs/manual/*|.git/*|.claude/runtime/*) continue ;; esac
  if grep -Ein "\b($pattern)\b" "$file" >/dev/null; then
    echo "Portuguese text outside docs/manual: $rel" >&2
    failed=1
  fi
done < <(find "$ROOT" -type d \( -name .git -o -name node_modules -o -name target -o -name dist -o -name build \) -prune -o -type f -name '*.md' -print)

(( failed == 0 )) || exit 1
echo 'validate-doc-language: PASS'
