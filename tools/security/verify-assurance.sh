#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:-candidate}"; MODE="${2:-}"; EVIDENCE="$ROOT/.claude/runtime/security-$TARGET"
mkdir -p "$EVIDENCE"; cd "$ROOT"; RESULTS=()
RESULTS+=("candidate-sha=$(git -C "$ROOT" rev-parse HEAD)")

# Scanners run as PINNED containers (tag + digest), never as build dependencies: decision-ladder
# rung 8 forbids adding a dependency without owner approval, and the assurance track needs a
# verdict that reproduces months later during an audit. Never float these to :latest.
GITLEAKS_IMAGE='ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854'

# Tear the DAST stack down and persist whatever controls ran, even when one of them failed —
# a BLOCKED or FAIL run must still leave evidence behind.
DAST_STARTED=0
cleanup(){
  if [ "$DAST_STARTED" = "1" ]; then
    docker compose -f "$ROOT/compose.security.yaml" down -v >"$EVIDENCE/docker-down.log" 2>&1 || true
  fi
  if [ ${#RESULTS[@]} -gt 0 ]; then printf '%s\n' "${RESULTS[@]}" > "$EVIDENCE/controls.txt"; fi
}
trap cleanup EXIT

timeout 1800 tools/quality/verify-release.sh; RESULTS+=("release-verification=PASS")

# Secrets. A local binary wins; otherwise the pinned image runs the same scan, so the control
# executes anywhere Docker exists. It is never downgraded to a no-op just because the host lacks
# the tool — that would turn a missing control into a silent PASS.
if command -v gitleaks >/dev/null 2>&1; then
  # gitleaks 8.19 replaced `detect` with `git` (and moved the repo from --source to a positional
  # argument). Probe the installed binary instead of assuming a vintage, so an operator with
  # either generation gets a real scan rather than a usage error mistaken for a clean tree.
  if gitleaks --help 2>&1 | grep -qE '^[[:space:]]+git[[:space:]]+scan git repositories'; then
    timeout 600 gitleaks git --no-banner --redact "$ROOT"
  else
    timeout 600 gitleaks detect --source "$ROOT" --no-banner --redact
  fi
  RESULTS+=("secrets=PASS")
elif command -v docker >/dev/null 2>&1; then
  timeout 900 docker run --rm -v "$ROOT:/repo:ro" "$GITLEAKS_IMAGE" git --no-banner --redact /repo
  RESULTS+=("secrets(docker)=PASS")
elif [ "$MODE" = "--requires-heavy" ]; then
  echo 'BLOCKED: gitleaks (local binary or Docker) is required for this candidate' >&2; exit 1
else RESULTS+=("secrets=NOT_APPLICABLE(no gitleaks binary and no Docker)"); fi

# Canonical digest-pinned dependency, license, configuration, container and IaC controls.
if [ -x tools/security/supply-chain/trivy-scan.sh ]; then
  timeout 3600 tools/security/supply-chain/trivy-scan.sh all
  RESULTS+=("supply-chain-and-deployment=PASS")
elif [ "$MODE" = "--requires-heavy" ]; then
  echo 'BLOCKED: canonical Trivy supply-chain wrapper is missing or not executable' >&2; exit 1
else RESULTS+=("supply-chain-and-deployment=NOT_APPLICABLE(no wrapper)"); fi

# Backend. The Maven project lives at backend/pom.xml; probing the repo root made this control
# report NOT_APPLICABLE(no backend) on a Java repo, silently skipping the entire build.
BACKEND_DIR=""
if [ -f "$ROOT/backend/pom.xml" ]; then BACKEND_DIR="$ROOT/backend"
elif [ -f "$ROOT/pom.xml" ]; then BACKEND_DIR="$ROOT"; fi
if [ -n "$BACKEND_DIR" ]; then
  if [ -x "$BACKEND_DIR/mvnw" ]; then
    (cd "$BACKEND_DIR" && timeout 1800 ./mvnw -B verify); RESULTS+=("backend-tests=PASS")
  elif [ "$MODE" = "--requires-heavy" ]; then
    echo 'BLOCKED: Maven wrapper is required for backend assurance' >&2; exit 1
  else RESULTS+=("backend-tests=NOT_APPLICABLE(no Maven wrapper)"); fi
else RESULTS+=("backend-tests=NOT_APPLICABLE(no backend)"); fi

# Dynamic assurance (DAST). --build so the scan hits an image rebuilt from THIS candidate:
# reusing a stale image would attest to code that is not under review.
if [ -f compose.security.yaml ]; then
  export APP_SECURITY_TARGET="$TARGET"
  DAST_STARTED=1
  timeout 2700 docker compose -f compose.security.yaml up --build --abort-on-container-exit --exit-code-from security-tests
  # A zero exit is not proof the scan happened. Assert the artifacts exist before recording a
  # PASS, so a recorded report path can never point at a file that was never written.
  MISSING=""
  for artifact in zap-report.html zap-api-report.html; do
    [ -f "$EVIDENCE/$artifact" ] || MISSING="$MISSING $artifact"
  done
  if [ -n "$MISSING" ]; then
    RESULTS+=("dynamic-security=FAIL (no ZAP artifact:$MISSING)")
    printf '%s\n' "${RESULTS[@]}" > "$EVIDENCE/controls.txt"
    echo "verify-assurance: FAIL - DAST produced no report:$MISSING" >&2; exit 1
  fi
  RESULTS+=("dynamic-security=PASS" "dast-report=$EVIDENCE/zap-report.html")
elif [ "$MODE" = "--requires-heavy" ]; then
  echo 'BLOCKED: compose.security.yaml and its pinned DAST/pentest profile are required' >&2; exit 1
else RESULTS+=("dynamic-security=NOT_APPLICABLE(no application)"); fi

printf '%s\n' "${RESULTS[@]}" | tee "$EVIDENCE/controls.txt"
echo 'verify-assurance: AUTOMATED_CONTROLS_PASS (independent review is still required for a security verdict)'
