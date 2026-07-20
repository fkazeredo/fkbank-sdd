#!/usr/bin/env bash
# Supply-chain scanner wrapper (SECURITY-ASSURANCE-TRACK families 3 and 7).
#
# WHY a repository wrapper: the assurance track only accepts output from a PINNED tool
# invoked through a repository wrapper ("Tools must be pinned and invoked through repository
# wrappers before their output can become authoritative"). CI and a human investigating a
# finding therefore run the exact same command with the exact same scanner build.
#
# WHY Docker and not a build plugin: decision-ladder rung 8 forbids a new Maven/npm
# dependency without human approval. Trivy runs as a digest-pinned container, so the scanner
# never enters the application dependency graph and cannot influence the shipped artifacts.
#
# Modes:
#   deps    Task 1 - vulnerabilities (gated) + licenses (reported) of the Maven and npm graphs.
#   config  Task 2 - misconfiguration/IaC scan of the Dockerfiles and compose files.
#   images  Task 2 - vulnerabilities of the application images we ship (built, then scanned).
#   all     the three above, in order, aggregating the exit codes.
#
# Windows note: this is the POSIX/CI entry point. Running it from Git Bash needs
# MSYS_NO_PATHCONV=1 so the -v mount paths are not rewritten by MSYS.

set -uo pipefail

# Digest-pinned so a moved tag cannot silently change the scanner (supply-chain integrity).
# Tag kept alongside the digest for human readability; the digest is what Docker resolves.
TRIVY_IMAGE="aquasec/trivy:0.72.0@sha256:cffe3f5161a47a6823fbd23d985795b3ed72a4c806da4c4df16266c02accdd6f"

# Severity policy. Everything is REPORTED so no finding is invisible; only HIGH/CRITICAL
# FAILS a gated scan, which keeps the gate actionable instead of noisy.
# Deliberately no --ignore-unfixed: SECURITY-ASSURANCE-TRACK "Finding policy" states that an
# unresolved High/Critical is BLOCKED, and narrowing the gate to fixable findings would be a
# gate weakening - a material decision reserved for the owner (human-decision-gate.md).
REPORT_SEVERITIES="UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL"
GATE_SEVERITIES="HIGH,CRITICAL"

# Build outputs and dependency caches are excluded from every walk: the lockfiles and the
# Dockerfiles are the single source of truth, and walking node_modules made the misconfig
# analyzers exceed their deadline (observed locally on 0.72.0 before this was added).
SKIP_DIRS=(--skip-dirs '**/node_modules' --skip-dirs '**/dist' --skip-dirs '**/target'
           --skip-dirs '**/test-results' --skip-dirs '**/.git' --skip-dirs '**/.angular')

REPO_ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
OUT_DIR="${TRIVY_OUT_DIR:-$REPO_ROOT/target/security/supply-chain}"
CACHE_DIR="${TRIVY_CACHE_DIR:-$OUT_DIR/.cache}"
# WHY the local Maven repository is mounted: Trivy resolves parent/BOM poms to build the real
# Java dependency tree, and doing that over the network made Maven Central answer 429 Too Many
# Requests mid-scan (observed locally, scan aborted). With ~/.m2 populated by the caller
# (mvn dependency:go-offline) it resolves from the cache instead of hammering Central.
MAVEN_REPO="${MAVEN_LOCAL_REPO:-$HOME/.m2}"
mkdir -p "$OUT_DIR" "$CACHE_DIR" "$MAVEN_REPO"

FAIL=0

# The repository is mounted read-only: a scanner must never be able to mutate the candidate
# it is scanning. Reports and the vulnerability database go to separate writable mounts.
trivy() {
  docker run --rm \
    -v "$REPO_ROOT:/workspace:ro" \
    -v "$OUT_DIR:/out" \
    -v "$CACHE_DIR:/cache" \
    -v "$MAVEN_REPO:/root/.m2" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TRIVY_CACHE_DIR=/cache \
    -w /workspace \
    "$TRIVY_IMAGE" "$@"
}

# Scan once into JSON, then render twice from that JSON: the full table as evidence, and the
# gate view which exits non-zero on HIGH/CRITICAL. One scan, two views - `convert` re-renders
# a stored report without re-downloading the vulnerability database.
#
# $3 is the scanner list, repeated only so `convert` can render its summary table.
# $4 is the gate severities, or "report-only" for a scan that must never fail the build.
report_then_gate() {
  local label="$1" json="$2" scanners="$3" gate="$4"
  shift 4
  echo "=== trivy $label ==="
  # --no-progress is NOT injected here: `trivy config` rejects it (0.72.0), which made the IaC
  # gate fail to run at all. Callers that support it (fs, image) pass it themselves.
  trivy "$@" \
    --severity "$REPORT_SEVERITIES" --format json --output "/out/$json" --exit-code 0 || {
    echo "trivy $label: scan failed to run"
    FAIL=1
    return
  }
  echo "--- $label: full report (all severities) ---"
  trivy convert --scanners "$scanners" --format table "/out/$json"
  if [ "$gate" = "report-only" ]; then
    echo "--- $label: REPORT ONLY, this scan does not fail the build (see the comment above) ---"
    return
  fi
  echo "--- $label: gate ($gate) ---"
  trivy convert --scanners "$scanners" --format table \
    --severity "$gate" --exit-code 1 "/out/$json" \
    || { echo "GATE FAILED: $label has $gate findings"; FAIL=1; }
}

scan_deps() {
  # fs scan of the whole repository: picks up every pom.xml and package-lock.json in the tree. Build outputs are skipped so the lockfiles stay the single
  # source of truth and a stale local node_modules/target cannot change the verdict.
  report_then_gate "deps (vulnerabilities)" "deps-vuln.json" "vuln" "$GATE_SEVERITIES" \
    fs --scanners vuln --no-progress "${SKIP_DIRS[@]}" --timeout 15m /workspace

  # License scanning runs on every candidate and is fully reported, but it does NOT fail the
  # build yet, and that is a deliberate, recorded decision rather than an oversight.
  #
  # WHY: Trivy's default classification puts LGPL/GPL under "restricted" = HIGH, and on this
  # tree that fires on ch.qos.logback:logback-* and jakarta.{annotation,transaction}-api. Both
  # are DUAL-licensed (logback: EPL-1.0 or LGPL-2.1; jakarta.*: EPL-2.0 or
  # GPL-2.0-with-classpath-exception) and Trivy reports only the copyleft half. Gating on that
  # would make CI permanently red over standard Spring Boot components that carry no copyleft
  # obligation for us.
  #
  # Suppressing them instead would mean an agent silently writing this project's license policy.
  # Deciding which licenses are acceptable for a bank is a material decision that belongs to
  # the owner (human-decision-gate.md, "Material technical decision"). Until that policy is
  # approved and recorded, this scan reports and the owner decides; it must not be quietly
  # switched to gating, nor quietly deleted.
  report_then_gate "deps (licenses)" "deps-license.json" "license" "report-only" \
    fs --scanners license --no-progress "${SKIP_DIRS[@]}" --timeout 15m /workspace
}

scan_config() {
  # Misconfiguration/IaC scan over the whole tree. Trivy's Dockerfile check bundle is what
  # fires here (root user, missing HEALTHCHECK, unpinned base image, ...) across backend/,
  # every build context. The default scanner set is kept rather than narrowed to
  # dockerfile so a Kubernetes/Terraform manifest added later is covered without editing this
  # wrapper.
  #
  # KNOWN GAP, recorded instead of hidden (invariant 4): Trivy 0.72.0 ships no Docker Compose
  # check bundle - `--misconfig-scanners` offers azure-arm, cloudformation, dockerfile, helm,
  # kubernetes, terraform*, ansible and nothing for compose. The compose.*.yaml files below are
  # therefore in scope of the walk but no check evaluates them. Closing that gap needs a tool
  # this repository has not approved, so it is an owner decision, not an agent improvisation.
  echo "IaC/config artifacts in the tree (Dockerfiles are checked; compose has no check bundle):"
  find "$REPO_ROOT" -maxdepth 3 \( -name 'Dockerfile' -o -name 'compose.*.yaml' \) \
    -not -path '*/node_modules/*' -print | sed "s|$REPO_ROOT/||"
  report_then_gate "config (IaC/misconfiguration)" "config.json" "misconfig" "$GATE_SEVERITIES" \
    config "${SKIP_DIRS[@]}" --timeout 15m /workspace
}

scan_images() {
  # Scans the images we SHIP, not the runtime bases they start from. Scanning the base is
  # misleading in both directions: it reports what our runtime stage already removed (we delete
  # the unused Go test-ACME binary eclipse-temurin bundles, which carries that image's only HIGH
  # CVEs), and it cannot see anything a COPY introduces. Building costs a few minutes; for a
  # banking artifact, gating on the thing we actually deploy is worth it.
  local entry name context image build_context count=0
  # Image list is configurable as "<name>:<build-context>" pairs; override APP_SCAN_IMAGES to
  # match your topology.
  for entry in ${APP_SCAN_IMAGES:-backend:./backend edge:./frontend}; do
    name="${entry%%:*}"
    context="${entry#*:}"
    image="${APP_IMAGE_PREFIX:-app}-${name}:supply-chain-scan"
    build_context="$REPO_ROOT/${context#./}"
    # Greenfield: a build context that does not exist yet is skipped, not a failure.
    [ -d "$build_context" ] || { echo "trivy images: skipping ${name} (no ${context} yet)"; continue; }
    # MSYS_NO_PATHCONV=1 (needed for the -v mounts) also stops MSYS rewriting the build context,
    # and the Windows docker CLI cannot resolve a /c/... POSIX path. Convert it explicitly where
    # cygpath exists; on Linux/CI the POSIX path is already correct.
    if command -v cygpath >/dev/null 2>&1; then
      build_context=$(cygpath -w "$build_context")
    fi
    echo "=== building ${image} from ${context} ==="
    if ! docker build -q -t "$image" "$build_context" >/dev/null; then
      echo "trivy images: docker build failed for ${image} - refusing to report a pass"
      FAIL=1
      continue
    fi
    count=$((count + 1))
    report_then_gate "image $image" "image-${name}.json" \
      "vuln" "$GATE_SEVERITIES" \
      image --scanners vuln --no-progress --timeout 15m "$image"
  done
  if [ "$count" -eq 0 ]; then
    if [ "$FAIL" -eq 0 ]; then
      echo "trivy images: NOT_APPLICABLE (no application build context exists yet - pre-bootstrap)"
    else
      echo "trivy images: no application image could be built - refusing to report a pass"
    fi
  fi
}

case "${1:-all}" in
  deps)   scan_deps ;;
  config) scan_config ;;
  images) scan_images ;;
  all)    scan_deps; scan_config; scan_images ;;
  *)      echo "usage: $0 [deps|config|images|all]" >&2; exit 2 ;;
esac

echo "trivy-scan (${1:-all}): exit=$FAIL, reports in $OUT_DIR"
exit $FAIL
