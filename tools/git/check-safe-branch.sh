#!/usr/bin/env bash
set -euo pipefail
PHASE="${1:-implementation}"; ALLOW_DIRTY="${2:-}"
case "$PHASE" in branch-source) PAT='^develop$';; implementation) PAT='^(feature|bugfix|chore)/.+';; pr) PAT='^(feature|bugfix|chore|hotfix|release)/.+';; release) PAT='^release/.+';; hotfix) PAT='^hotfix/.+';; reconcile) PAT='^(chore|feature|bugfix|hotfix|release)/.+';; *) echo "check-safe-branch: invalid phase '$PHASE'." >&2; exit 2;; esac
BRANCH="$(git branch --show-current 2>/dev/null || true)"
[ -n "$BRANCH" ] || { echo 'check-safe-branch: detached HEAD or not a Git repository. Switch to the phase branch.' >&2; exit 1; }
if [ "$PHASE" = branch-source ]; then [[ "$BRANCH" =~ $PAT ]] || { echo "check-safe-branch: '$BRANCH' is not the required develop source." >&2; exit 1; }; else [[ "$BRANCH" != main && "$BRANCH" != develop && "$BRANCH" =~ $PAT ]] || { echo "check-safe-branch: '$BRANCH' is incompatible with phase '$PHASE'." >&2; exit 1; }; fi
[ "$ALLOW_DIRTY" = --allow-dirty ] || [ -z "$(git status --porcelain)" ] || { echo 'check-safe-branch: working tree is unexpectedly dirty.' >&2; exit 1; }
echo "check-safe-branch: OK ($BRANCH, phase=$PHASE)"
