#!/bin/bash
# LightCI Server — Post-Build Validation Hook
#
# PURPOSE: Validates that the Maven build produced a valid JAR artifact.
#          This script ONLY validates — it does NOT deploy or run anything.
#
# USAGE:
#   bash .claude/hooks/post-build.sh
#
# EXIT CODES:
#   0 — Build artifact is valid
#   1 — Build artifact is missing or invalid

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ---- Colour codes ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; WARN_COUNT=$((WARN_COUNT + 1)); }
info() { echo -e "${CYAN}[INFO]${NC} $1"; }
section() { echo ""; echo "--- $1 ---"; }

JAR="${PROJECT_ROOT}/target/light-ci-server.jar"
# Minimum expected size for a Spring Boot fat JAR: 10 MB (10485760 bytes)
MIN_SIZE_BYTES=10485760

echo "============================================"
echo " LightCI Server — Post-Build Validation"
echo " Project: ${PROJECT_ROOT}"
echo "============================================"

# ============================================================
# Check 1: JAR file exists
# ============================================================
section "JAR Artifact Existence"

if [ ! -f "${JAR}" ]; then
  fail "Expected artifact not found: ${JAR}"
  echo ""
  echo "       Possible causes:"
  echo "       1. Maven build was not run yet — run: mvn clean package -DskipTests"
  echo "       2. Build failed — check target/build.log for errors"
  echo "       3. Wrong working directory — this script must run from project root"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo ""
  echo "============================================"
  echo -e " ${RED}Post-build validation FAILED.${NC}"
  echo "============================================"
  exit 1
fi

pass "JAR artifact found: ${JAR}"

# ============================================================
# Check 2: JAR is not empty / has minimum expected size
# ============================================================
section "JAR Size Validation"

JAR_BYTES=$(wc -c < "${JAR}" | tr -d ' ')
JAR_SIZE_HUMAN=$(du -sh "${JAR}" | cut -f1)

if [ "${JAR_BYTES}" -lt 1000 ]; then
  fail "JAR file is essentially empty (${JAR_BYTES} bytes). Build likely failed silently."
elif [ "${JAR_BYTES}" -lt "${MIN_SIZE_BYTES}" ]; then
  warn "JAR is smaller than expected for a Spring Boot fat JAR."
  warn "Size: ${JAR_SIZE_HUMAN} (${JAR_BYTES} bytes). Expected >= $(( MIN_SIZE_BYTES / 1048576 )) MB."
  warn "This may be normal if the project has minimal dependencies."
else
  pass "JAR size looks healthy: ${JAR_SIZE_HUMAN} (${JAR_BYTES} bytes)."
fi

# ============================================================
# Check 3: JAR is a valid ZIP/JAR file
# ============================================================
section "JAR Format Validation"

if command -v unzip &> /dev/null; then
  if unzip -t "${JAR}" > /dev/null 2>&1; then
    pass "JAR is a valid ZIP archive."
  else
    fail "JAR file is corrupt or not a valid ZIP/JAR archive."
  fi
elif command -v jar &> /dev/null; then
  if jar tf "${JAR}" > /dev/null 2>&1; then
    pass "JAR is a valid archive (verified with jar command)."
  else
    fail "JAR file is corrupt or not a valid JAR archive."
  fi
else
  warn "Neither 'unzip' nor 'jar' command available — skipping format check."
fi

# ============================================================
# Check 4: Verify it's a Spring Boot executable JAR
# ============================================================
section "Spring Boot Manifest"

if command -v unzip &> /dev/null; then
  MANIFEST=$(unzip -p "${JAR}" META-INF/MANIFEST.MF 2>/dev/null || echo "")
  if echo "${MANIFEST}" | grep -q "Spring-Boot-Version"; then
    SB_VERSION=$(echo "${MANIFEST}" | grep "Spring-Boot-Version" | cut -d' ' -f2 | tr -d '\r')
    pass "Spring Boot JAR confirmed. Spring Boot version: ${SB_VERSION}"
  else
    warn "Could not confirm Spring Boot manifest. This may not be a fat JAR."
  fi

  if echo "${MANIFEST}" | grep -q "Main-Class"; then
    MAIN_CLASS=$(echo "${MANIFEST}" | grep "Main-Class" | cut -d' ' -f2 | tr -d '\r')
    pass "Main-Class in manifest: ${MAIN_CLASS}"
  else
    warn "No Main-Class found in manifest. JAR may not be executable."
  fi
else
  warn "unzip not available — skipping manifest check."
fi

# ============================================================
# Check 5: target/build.log exists (evidence of Maven run)
# ============================================================
section "Build Log"

BUILD_LOG="${PROJECT_ROOT}/target/build.log"
if [ -f "${BUILD_LOG}" ]; then
  LOG_SIZE=$(du -sh "${BUILD_LOG}" | cut -f1)
  if grep -q "BUILD SUCCESS" "${BUILD_LOG}" 2>/dev/null; then
    pass "Build log shows BUILD SUCCESS (${LOG_SIZE})."
  elif grep -q "BUILD FAILURE" "${BUILD_LOG}" 2>/dev/null; then
    fail "Build log shows BUILD FAILURE. Check ${BUILD_LOG} for details."
  else
    warn "Build log exists (${LOG_SIZE}) but BUILD SUCCESS/FAILURE marker not found."
  fi
else
  warn "Build log not found at ${BUILD_LOG}."
  warn "Consider piping Maven output: mvn clean package 2>&1 | tee target/build.log"
fi

# ============================================================
# Check 6: Timestamp — JAR should be recently built
# ============================================================
section "Build Freshness"

if command -v find &> /dev/null; then
  RECENT=$(find "${JAR}" -mmin -60 2>/dev/null || true)
  if [ -n "${RECENT}" ]; then
    pass "JAR was built within the last 60 minutes (fresh build)."
  else
    JAR_DATE=$(date -r "${JAR}" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "unknown")
    warn "JAR appears to be older than 60 minutes (last modified: ${JAR_DATE})."
    warn "Consider rebuilding to ensure you're deploying latest code."
  fi
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo " Post-Build Validation Summary"
echo ""
echo " Artifact:  ${JAR}"
echo " Size:      ${JAR_SIZE_HUMAN:-unknown}"
echo ""
echo -e " ${GREEN}PASSED${NC}: ${PASS_COUNT}"
echo -e " ${YELLOW}WARNED${NC}: ${WARN_COUNT}"
echo -e " ${RED}FAILED${NC}: ${FAIL_COUNT}"
echo "============================================"

if [ "${FAIL_COUNT}" -gt 0 ]; then
  echo ""
  echo -e "${RED}Post-build validation FAILED.${NC}"
  echo "Do not proceed to Docker build until the JAR issue is resolved."
  exit 1
else
  echo ""
  echo -e "${GREEN}Post-build validation PASSED.${NC}"
  echo "Build artifact is valid."
  echo ""
  echo "Next steps:"
  echo "  1. Build Docker image:   use /docker-build command in Claude"
  echo "  2. Or test locally:      java -jar ${JAR}"
  exit 0
fi
