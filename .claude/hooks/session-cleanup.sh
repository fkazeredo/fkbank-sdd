#!/usr/bin/env bash
set -u
mkdir -p .claude/runtime
: > .claude/runtime/current-role
: > .claude/runtime/current-slice
exit 0
