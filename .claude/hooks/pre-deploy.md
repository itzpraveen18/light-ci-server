#!/bin/bash
# name: pre-deploy
# description: Validates Docker image, AWS CLI config, SSH key, and env vars before EC2 deploy
# type: hook
# trigger: pre-deploy
# auto_approve: true
# run: bash .claude/hooks/pre-deploy.md
# LightCI Server — Pre-Deploy Validation Hook
#
# PURPOSE: Validates all prerequisites before deploying to EC2.
#          This script ONLY validates — it does NOT deploy anything.
#
# USAGE:
#   source .env
#   bash .claude/hooks/pre-deploy.sh
#
# EXIT CODES:
#   0 — All checks passed, safe to proceed with deployment
#   1 — One or more checks failed, deployment should not proceed

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

echo "============================================"
echo " LightCI Server — Pre-Deploy Validation"
echo " Project: ${PROJECT_ROOT}"
echo "============================================"

# ============================================================
# Check 1: Required environment variables
# ============================================================
section "Required Environment Variables"

check_env() {
  local VAR="$1"
  local DESCRIPTION="$2"
  if [ -z "${!VAR:-}" ]; then
    fail "${VAR} is not set. ${DESCRIPTION}"
    echo "       Set it in .env file and run: source .env"
  else
    pass "${VAR} is set: ${!VAR}"
  fi
}

check_env "DOCKER_USERNAME"   "Required to authenticate with Docker Hub."
check_env "AWS_REGION"        "Required for AWS CLI and Terraform."
check_env "EC2_KEY_PAIR_NAME" "Name of the AWS key pair to attach to EC2."

# These warn rather than fail — they may be set after infra provisioning
if [ -z "${EC2_PUBLIC_IP:-}" ]; then
  warn "EC2_PUBLIC_IP is not set. Set this after running: terraform output instance_public_ip"
else
  pass "EC2_PUBLIC_IP is set: ${EC2_PUBLIC_IP}"
fi

if [ -z "${SSH_KEY_PATH:-}" ]; then
  warn "SSH_KEY_PATH is not set. Required for deployment scripts."
else
  pass "SSH_KEY_PATH is set: ${SSH_KEY_PATH}"
fi

# ============================================================
# Check 2: Docker image exists locally
# ============================================================
section "Docker Image"

if ! command -v docker &> /dev/null; then
  fail "Docker is not installed or not on PATH."
else
  DOCKER_IMAGE="${DOCKER_IMAGE:-light-ci-server}"
  DOCKER_TAG="${DOCKER_TAG:-latest}"
  IMAGE_REF="${DOCKER_USERNAME:-unknown}/${DOCKER_IMAGE}:${DOCKER_TAG}"

  if docker image inspect "${IMAGE_REF}" > /dev/null 2>&1; then
    IMAGE_SIZE=$(docker image inspect "${IMAGE_REF}" --format='{{.Size}}' 2>/dev/null || echo "0")
    IMAGE_SIZE_MB=$((IMAGE_SIZE / 1048576))
    pass "Docker image found locally: ${IMAGE_REF} (${IMAGE_SIZE_MB} MB)"
  else
    warn "Docker image not found locally: ${IMAGE_REF}"
    warn "Image may need to be built first, OR will be pulled on EC2 from Docker Hub."
    warn "Verify it exists in Docker Hub: https://hub.docker.com/r/${DOCKER_USERNAME:-<username>}/${DOCKER_IMAGE}"
  fi
fi

# ============================================================
# Check 3: AWS CLI is installed and configured
# ============================================================
section "AWS CLI Configuration"

if ! command -v aws &> /dev/null; then
  fail "AWS CLI is not installed or not on PATH."
  echo "       Install: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
else
  AWS_CLI_VERSION=$(aws --version 2>&1 | head -1)
  pass "AWS CLI is installed: ${AWS_CLI_VERSION}"

  # Test identity — read-only, does not modify anything
  CALLER_IDENTITY=$(aws sts get-caller-identity \
    ${AWS_PROFILE:+--profile "${AWS_PROFILE}"} \
    --region "${AWS_REGION:-us-east-1}" \
    --output json 2>&1 || echo "ERROR")

  if echo "${CALLER_IDENTITY}" | grep -q '"Account"'; then
    AWS_ACCOUNT=$(echo "${CALLER_IDENTITY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Account'])" 2>/dev/null || \
      echo "${CALLER_IDENTITY}" | grep '"Account"' | sed 's/.*": "\([^"]*\)".*/\1/')
    AWS_ARN=$(echo "${CALLER_IDENTITY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Arn'])" 2>/dev/null || echo "see JSON above")
    pass "AWS credentials are valid. Account: ${AWS_ACCOUNT}"
    info "Identity ARN: ${AWS_ARN}"
  else
    fail "AWS credentials are not configured or are expired."
    echo "       Configure: aws configure"
    echo "       Or set:    AWS_PROFILE, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY"
    echo "       Error: ${CALLER_IDENTITY}"
  fi
fi

# ============================================================
# Check 4: Terraform files exist
# ============================================================
section "Terraform Files"

TF_DIR="${PROJECT_ROOT}/terraform"
TF_FILES=("main.tf" "variables.tf" "outputs.tf")
TF_MISSING=0

for TF_FILE in "${TF_FILES[@]}"; do
  if [ -f "${TF_DIR}/${TF_FILE}" ]; then
    pass "Terraform file exists: terraform/${TF_FILE}"
  else
    fail "Terraform file missing: terraform/${TF_FILE}"
    echo "       Generate it: use /infra-plan command in Claude"
    TF_MISSING=$((TF_MISSING + 1))
  fi
done

# Check Terraform state (existence of state = infrastructure is provisioned)
if [ -f "${TF_DIR}/terraform.tfstate" ]; then
  TF_STATE_SIZE=$(du -sh "${TF_DIR}/terraform.tfstate" | cut -f1)
  pass "Terraform state file exists (${TF_STATE_SIZE}) — infrastructure appears provisioned."
else
  warn "terraform.tfstate not found. Infrastructure may not be provisioned yet."
  warn "Run: cd terraform && terraform init && terraform apply"
fi

# ============================================================
# Check 5: SSH key file exists and has correct permissions
# ============================================================
section "SSH Key"

if [ -z "${SSH_KEY_PATH:-}" ]; then
  warn "SSH_KEY_PATH not set — skipping SSH key validation."
else
  if [ ! -f "${SSH_KEY_PATH}" ]; then
    fail "SSH key file not found: ${SSH_KEY_PATH}"
    echo "       Download your .pem key from the AWS Console EC2 key pairs page."
  else
    # Check permissions
    KEY_PERMS=$(stat -c "%a" "${SSH_KEY_PATH}" 2>/dev/null || stat -f "%Lp" "${SSH_KEY_PATH}" 2>/dev/null || echo "unknown")
    if [ "${KEY_PERMS}" = "400" ] || [ "${KEY_PERMS}" = "600" ]; then
      pass "SSH key exists with correct permissions (${KEY_PERMS}): ${SSH_KEY_PATH}"
    else
      warn "SSH key permissions are ${KEY_PERMS}. Should be 400 or 600."
      warn "Fix: chmod 400 ${SSH_KEY_PATH}"
    fi
  fi
fi

# ============================================================
# Check 6: Terraform is installed
# ============================================================
section "Terraform CLI"

if ! command -v terraform &> /dev/null; then
  warn "Terraform is not installed. Required to run: terraform plan / terraform apply."
  warn "Install: https://developer.hashicorp.com/terraform/downloads"
else
  TF_VERSION=$(terraform -version | head -1)
  pass "Terraform is installed: ${TF_VERSION}"
fi

# ============================================================
# Check 7: Deployment scripts exist
# ============================================================
section "Deployment Scripts"

if [ -f "${PROJECT_ROOT}/scripts/deploy.sh" ]; then
  pass "scripts/deploy.sh exists."
else
  warn "scripts/deploy.sh not found."
  warn "Generate it: use /deploy command in Claude"
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo " Pre-Deploy Validation Summary"
echo ""
echo -e " ${GREEN}PASSED${NC}: ${PASS_COUNT}"
echo -e " ${YELLOW}WARNED${NC}: ${WARN_COUNT}"
echo -e " ${RED}FAILED${NC}: ${FAIL_COUNT}"
echo "============================================"

if [ "${FAIL_COUNT}" -gt 0 ]; then
  echo ""
  echo -e "${RED}Pre-deploy validation FAILED.${NC}"
  echo "Resolve the failures above before deploying."
  exit 1
else
  echo ""
  echo -e "${GREEN}Pre-deploy validation PASSED.${NC}"
  if [ "${WARN_COUNT}" -gt 0 ]; then
    echo "Review warnings above before proceeding."
  fi
  echo ""
  echo "Next steps:"
  echo "  1. Review deploy script:  cat scripts/deploy.sh"
  echo "  2. Run deployment:        bash scripts/deploy.sh"
  exit 0
fi
