#!/bin/bash
# name: pre-build
# description: Validates Java 17, Maven, pom.xml, and environment variables before Maven build
# type: hook
# trigger: pre-build
# auto_approve: true
# run: bash .claude/hooks/pre-build.md
# LightCI Server — Pre-Build Validation Hook
#
# PURPOSE: Validates the build environment before running Maven.
#          This script ONLY validates — it does NOT build anything.
#
# USAGE:
#   bash .claude/hooks/pre-build.sh
#
# EXIT CODES:
#   0 — All checks passed, safe to proceed with build
#   1 — One or more checks failed, build should not proceed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ---- Colour codes ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Colour

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; WARN_COUNT=$((WARN_COUNT + 1)); }
section() { echo ""; echo "--- $1 ---"; }

echo "============================================"
echo " LightCI Server — Pre-Build Validation"
echo " Project: ${PROJECT_ROOT}"
echo "============================================"

# ============================================================
# Check 1: Java version
# ============================================================
section "Java Version"

if ! command -v java &> /dev/null; then
  fail "Java is not installed or not on PATH."
  echo "       Install Java 17: https://adoptium.net/"
else
  JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
  if [ "${JAVA_VER}" = "17" ]; then
    pass "Java 17 is installed: $(java -version 2>&1 | head -1)"
  else
    fail "Java 17 required. Found Java ${JAVA_VER}."
    echo "       Fix (macOS):  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo "       Fix (Linux):  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk"
  fi
fi

# ============================================================
# Check 2: Maven installation
# ============================================================
section "Maven"

if ! command -v mvn &> /dev/null; then
  fail "Maven is not installed or not on PATH."
  echo "       Install: https://maven.apache.org/download.cgi"
  echo "       macOS:   brew install maven"
  echo "       Linux:   sudo apt-get install maven  OR  sudo dnf install maven"
else
  MVN_VER=$(mvn -version 2>&1 | head -1)
  pass "Maven is installed: ${MVN_VER}"
fi

# ============================================================
# Check 3: pom.xml exists
# ============================================================
section "Project Descriptor"

if [ -f "${PROJECT_ROOT}/pom.xml" ]; then
  pass "pom.xml exists at project root."
else
  fail "pom.xml not found at ${PROJECT_ROOT}/pom.xml"
  echo "       Are you running from the correct directory?"
fi

# ============================================================
# Check 4: Source directory exists
# ============================================================
section "Source Tree"

if [ -d "${PROJECT_ROOT}/src/main/java" ]; then
  JAVA_FILES=$(find "${PROJECT_ROOT}/src/main/java" -name "*.java" | wc -l | tr -d ' ')
  pass "Source directory exists (${JAVA_FILES} Java files found)."
else
  fail "src/main/java directory not found."
fi

# ============================================================
# Check 5: No uncommitted changes that could break build
# ============================================================
section "Git Status"

cd "${PROJECT_ROOT}"
if ! command -v git &> /dev/null; then
  warn "Git not found — skipping uncommitted changes check."
elif ! git rev-parse --git-dir > /dev/null 2>&1; then
  warn "Not a git repository — skipping uncommitted changes check."
else
  MODIFIED_POM=$(git diff --name-only HEAD 2>/dev/null | grep "^pom.xml$" || true)
  MODIFIED_APP_PROPS=$(git diff --name-only HEAD 2>/dev/null | grep "application.properties" || true)

  if [ -n "${MODIFIED_POM}" ]; then
    warn "pom.xml has uncommitted changes. Consider committing before building."
  else
    pass "pom.xml has no uncommitted changes."
  fi

  if [ -n "${MODIFIED_APP_PROPS}" ]; then
    warn "application.properties has uncommitted changes."
  else
    pass "application.properties has no uncommitted changes."
  fi

  STAGED=$(git diff --cached --name-only 2>/dev/null | wc -l | tr -d ' ')
  if [ "${STAGED}" -gt 0 ]; then
    warn "${STAGED} staged file(s) not yet committed. Build will use working tree state."
  fi
fi

# ============================================================
# Check 6: JAVA_HOME is set correctly (optional but helpful)
# ============================================================
section "JAVA_HOME"

if [ -z "${JAVA_HOME:-}" ]; then
  warn "JAVA_HOME is not set. Maven will use the Java on PATH."
else
  if [ -f "${JAVA_HOME}/bin/java" ]; then
    pass "JAVA_HOME is set and valid: ${JAVA_HOME}"
  else
    fail "JAVA_HOME is set but does not contain a valid Java installation: ${JAVA_HOME}"
  fi
fi

# ============================================================
# Check 7: Maven local repository (warn if first run will be slow)
# ============================================================
section "Maven Cache"

M2_REPO="${HOME}/.m2/repository"
if [ -d "${M2_REPO}" ]; then
  REPO_SIZE=$(du -sh "${M2_REPO}" 2>/dev/null | cut -f1 || echo "unknown")
  pass "Maven local repository exists (~/.m2/repository, ${REPO_SIZE})."
else
  warn "Maven local repository not found. First build will download all dependencies."
  warn "This may take 5-10 minutes on first run."
fi

# ============================================================
# Check 8: Required environment variables (optional, warn only)
# ============================================================
section "Environment Variables (for downstream steps)"

REQUIRED_VARS=("DOCKER_USERNAME" "AWS_REGION")
for VAR in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!VAR:-}" ]; then
    warn "${VAR} is not set. Required for Docker and AWS steps after build."
  else
    pass "${VAR} is set."
  fi
done

# ============================================================
# Check 9: Available disk space
# ============================================================
section "Disk Space"

AVAILABLE_KB=$(df -k "${PROJECT_ROOT}" | tail -1 | awk '{print $4}')
AVAILABLE_MB=$((AVAILABLE_KB / 1024))
if [ "${AVAILABLE_MB}" -lt 500 ]; then
  fail "Less than 500 MB of disk space available (${AVAILABLE_MB} MB). Build may fail."
else
  pass "Sufficient disk space available (${AVAILABLE_MB} MB)."
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo " Pre-Build Validation Summary"
echo ""
echo -e " ${GREEN}PASSED${NC}: ${PASS_COUNT}"
echo -e " ${YELLOW}WARNED${NC}: ${WARN_COUNT}"
echo -e " ${RED}FAILED${NC}: ${FAIL_COUNT}"
echo "============================================"

if [ "${FAIL_COUNT}" -gt 0 ]; then
  echo ""
  echo -e "${RED}Pre-build validation FAILED.${NC}"
  echo "Fix the issues above before running: mvn clean package"
  exit 1
else
  echo ""
  echo -e "${GREEN}Pre-build validation PASSED.${NC}"
  echo "Safe to proceed with: mvn clean package -DskipTests"
  exit 0
fi
