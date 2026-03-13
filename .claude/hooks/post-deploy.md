#!/bin/bash
# name: post-deploy
# description: Validates application health and HTTP 200 response on EC2 after deployment
# type: hook
# trigger: post-deploy
# auto_approve: true
# run: bash .claude/hooks/post-deploy.md
# LightCI Server — Post-Deploy Validation Hook
#
# PURPOSE: Validates that the application is running on EC2 after deployment.
#          This script is READ-ONLY — it makes HTTP requests and SSH reads only.
#          It does NOT modify anything on the server.
#
# USAGE:
#   source .env
#   bash .claude/hooks/post-deploy.sh
#
# REQUIRED ENV VARS:
#   EC2_PUBLIC_IP  — Public IP of the deployed EC2 instance
#   SSH_KEY_PATH   — Path to .pem key (for docker ps check)
#   APP_PORT       — Application port (default: 8080)
#
# EXIT CODES:
#   0 — Application is healthy and responding
#   1 — Application is not responding or health check failed

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

# ---- Configuration ----
EC2_PUBLIC_IP="${EC2_PUBLIC_IP:?EC2_PUBLIC_IP is required. Run: source .env}"
APP_PORT="${APP_PORT:-8080}"
SSH_KEY_PATH="${SSH_KEY_PATH:-}"
SSH_USER="${SSH_USER:-ec2-user}"
CONTAINER_NAME="${CONTAINER_NAME:-light-ci-server}"

BASE_URL="http://${EC2_PUBLIC_IP}:${APP_PORT}"
HEALTH_URL="${BASE_URL}/actuator/health"
MAX_RETRIES=5
RETRY_DELAY=10

echo "============================================"
echo " LightCI Server — Post-Deploy Validation"
echo ""
echo " EC2 IP:  ${EC2_PUBLIC_IP}"
echo " Port:    ${APP_PORT}"
echo " URL:     ${BASE_URL}"
echo "============================================"

# ============================================================
# Check 1: EC2 port is open (TCP connectivity)
# ============================================================
section "Network Connectivity"

if command -v nc &> /dev/null; then
  if nc -z -w 5 "${EC2_PUBLIC_IP}" "${APP_PORT}" 2>/dev/null; then
    pass "TCP port ${APP_PORT} is open on ${EC2_PUBLIC_IP}."
  else
    fail "TCP port ${APP_PORT} is not reachable on ${EC2_PUBLIC_IP}."
    echo "       Check: Security group allows port ${APP_PORT} from 0.0.0.0/0"
    echo "       Check: EC2 instance is running"
    echo "       Check: Docker container is running on EC2"
  fi
elif command -v curl &> /dev/null; then
  info "nc not available — will test connectivity via HTTP request."
else
  warn "Neither nc nor curl available — skipping TCP connectivity check."
fi

# ============================================================
# Check 2: HTTP health endpoint — with retries
# ============================================================
section "Application Health Check"

ATTEMPT=0
HTTP_STATUS="000"
HEALTH_BODY=""

while [ "${ATTEMPT}" -lt "${MAX_RETRIES}" ]; do
  ATTEMPT=$((ATTEMPT + 1))
  echo "  Attempt ${ATTEMPT}/${MAX_RETRIES}: GET ${HEALTH_URL}"

  HTTP_RESPONSE=$(curl -s -w "\n%{http_code}" \
    --max-time 15 \
    --connect-timeout 5 \
    "${HEALTH_URL}" 2>/dev/null || echo -e "\n000")

  HTTP_STATUS=$(echo "${HTTP_RESPONSE}" | tail -1)
  HEALTH_BODY=$(echo "${HTTP_RESPONSE}" | head -n -1)

  if [ "${HTTP_STATUS}" = "200" ]; then
    break
  else
    echo "  HTTP ${HTTP_STATUS} — waiting ${RETRY_DELAY}s before retry..."
    if [ "${ATTEMPT}" -lt "${MAX_RETRIES}" ]; then
      sleep "${RETRY_DELAY}"
    fi
  fi
done

if [ "${HTTP_STATUS}" = "200" ]; then
  pass "Health check PASSED — HTTP 200 from ${HEALTH_URL}"
  info "Response: ${HEALTH_BODY}"

  # Check the JSON response body for "status":"UP"
  if echo "${HEALTH_BODY}" | grep -q '"status":"UP"'; then
    pass "Spring Boot reports status: UP"
  elif echo "${HEALTH_BODY}" | grep -q '"status"'; then
    HEALTH_STATUS=$(echo "${HEALTH_BODY}" | grep -o '"status":"[^"]*"' | head -1)
    warn "Spring Boot health status is not UP: ${HEALTH_STATUS}"
  fi
else
  fail "Health check FAILED after ${MAX_RETRIES} attempts. Last status: HTTP ${HTTP_STATUS}"
  echo ""
  echo "  Troubleshooting steps:"
  echo "  1. Check container logs:"
  if [ -n "${SSH_KEY_PATH}" ] && [ -f "${SSH_KEY_PATH}" ]; then
    echo "     ssh -i ${SSH_KEY_PATH} ${SSH_USER}@${EC2_PUBLIC_IP} 'docker logs ${CONTAINER_NAME} --tail 50'"
  else
    echo "     ssh -i <KEY_PATH> ${SSH_USER}@${EC2_PUBLIC_IP} 'docker logs ${CONTAINER_NAME} --tail 50'"
  fi
  echo "  2. Check if container is running:"
  echo "     ssh ... 'docker ps'"
  echo "  3. Spring Boot may still be starting — wait 30s and retry."
fi

# ============================================================
# Check 3: Application root endpoint
# ============================================================
section "Application Root"

ROOT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  --max-time 10 \
  --connect-timeout 5 \
  "${BASE_URL}/" 2>/dev/null || echo "000")

if [ "${ROOT_STATUS}" = "200" ] || [ "${ROOT_STATUS}" = "302" ]; then
  pass "Application root (/) responds: HTTP ${ROOT_STATUS}"
elif [ "${ROOT_STATUS}" = "000" ]; then
  warn "Could not reach application root — may still be starting."
else
  info "Application root returned HTTP ${ROOT_STATUS} (may be expected redirect or auth)."
fi

# ============================================================
# Check 4: Docker container status via SSH (if SSH key available)
# ============================================================
section "Container Status (via SSH)"

if [ -z "${SSH_KEY_PATH}" ]; then
  warn "SSH_KEY_PATH not set — skipping remote Docker container check."
elif [ ! -f "${SSH_KEY_PATH}" ]; then
  warn "SSH key not found at ${SSH_KEY_PATH} — skipping remote check."
else
  echo "  Fetching docker ps output from EC2..."

  DOCKER_PS=$(ssh -i "${SSH_KEY_PATH}" \
      -o StrictHostKeyChecking=no \
      -o ConnectTimeout=15 \
      -o BatchMode=yes \
      "${SSH_USER}@${EC2_PUBLIC_IP}" \
      "docker ps --filter name=${CONTAINER_NAME} --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'" \
      2>/dev/null || echo "SSH_FAILED")

  if [ "${DOCKER_PS}" = "SSH_FAILED" ]; then
    warn "Could not SSH to EC2 — skipping container status check."
  elif echo "${DOCKER_PS}" | grep -q "${CONTAINER_NAME}"; then
    CONTAINER_STATUS=$(echo "${DOCKER_PS}" | grep "${CONTAINER_NAME}" | awk '{print $3, $4}')
    pass "Container '${CONTAINER_NAME}' is running. Status: ${CONTAINER_STATUS}"
    echo ""
    echo "${DOCKER_PS}"
  else
    fail "Container '${CONTAINER_NAME}' is NOT running on EC2."
    echo ""
    echo "All running containers:"
    ssh -i "${SSH_KEY_PATH}" \
        -o StrictHostKeyChecking=no \
        -o ConnectTimeout=10 \
        "${SSH_USER}@${EC2_PUBLIC_IP}" \
        "docker ps" 2>/dev/null || echo "(could not fetch)"
  fi
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo " Post-Deploy Validation Summary"
echo ""
echo " App URL:    ${BASE_URL}"
echo " Health:     ${HEALTH_URL}"
echo ""
echo -e " ${GREEN}PASSED${NC}: ${PASS_COUNT}"
echo -e " ${YELLOW}WARNED${NC}: ${WARN_COUNT}"
echo -e " ${RED}FAILED${NC}: ${FAIL_COUNT}"
echo "============================================"

if [ "${FAIL_COUNT}" -gt 0 ]; then
  echo ""
  echo -e "${RED}Post-deploy validation FAILED.${NC}"
  echo "The application is not responding correctly. Check the issues above."
  exit 1
else
  echo ""
  echo -e "${GREEN}Post-deploy validation PASSED.${NC}"
  echo ""
  echo "Application is live:"
  echo "  Open:   ${BASE_URL}"
  echo "  Health: ${HEALTH_URL}"
  exit 0
fi
