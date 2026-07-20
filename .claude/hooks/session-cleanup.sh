#!/usr/bin/env bash
set -u
mkdir -p .claude/runtime
: > .claude/runtime/current-role
: > .claude/runtime/current-slice
# The session that is ending owns exactly one of these; a sibling session's file must survive.
[ -n "${CLAUDE_CODE_SESSION_ID:-}" ] &&
  rm -f ".claude/runtime/roles/$(printf '%s' "$CLAUDE_CODE_SESSION_ID" | tr -c '0-9A-Za-z._-' '_')"
exit 0
