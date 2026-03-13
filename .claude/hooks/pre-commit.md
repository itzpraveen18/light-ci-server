#!/bin/bash
# name: pre-commit
# description: Git pre-commit hook that scans staged files for secrets and sensitive credentials
# type: hook
# trigger: pre-commit
# auto_approve: true
# install: cp .claude/hooks/pre-commit.md .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
# LightCI Server — Git Pre-Commit Security Hook
#
# PURPOSE: Prevents accidental commit of sensitive files and credentials.
#          Scans staged files for secrets, credentials, and sensitive patterns.
#          This hook BLOCKS the commit if any issue is found.
#
# INSTALL:
#   cp .claude/hooks/pre-commit.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# BYPASS (EMERGENCY ONLY — document the reason):
#   git commit --no-verify -m "your message"
#
# EXIT CODES:
#   0 — All checks passed, commit is allowed
#   1 — Security issue found, commit is BLOCKED

set -euo pipefail

# ---- Colour codes ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

BLOCKED=0
WARNINGS=0

block() {
  echo -e "${RED}[BLOCKED]${NC} $1"
  BLOCKED=$((BLOCKED + 1))
}

warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
  WARNINGS=$((WARNINGS + 1))
}

ok() {
  echo -e "${GREEN}[OK]${NC} $1"
}

# Get list of staged files
STAGED_FILES=$(git diff --cached --name-only 2>/dev/null || true)

if [ -z "${STAGED_FILES}" ]; then
  # No staged files — nothing to check
  exit 0
fi

echo "============================================"
echo " LightCI Server — Pre-Commit Security Check"
echo "============================================"
echo ""

# ============================================================
# Check 1: AWS Access Key IDs
# ============================================================
echo "[1/7] Scanning for AWS Access Key IDs (AKIA...)..."

AWS_KEY_FOUND=0
while IFS= read -r FILE; do
  [ -f "${FILE}" ] || continue
  if grep -Eq "AKIA[0-9A-Z]{16}" "${FILE}" 2>/dev/null; then
    block "AWS Access Key ID found in: ${FILE}"
    echo "       Pattern: AKIA[0-9A-Z]{16}"
    echo "       This is a real AWS credential — NEVER commit this."
    echo "       Remove it and rotate the key immediately at:"
    echo "       https://console.aws.amazon.com/iam/home#/security_credentials"
    AWS_KEY_FOUND=1
  fi
done <<< "${STAGED_FILES}"

if [ "${AWS_KEY_FOUND}" -eq 0 ]; then
  ok "No AWS Access Key IDs found in staged files."
fi

# ============================================================
# Check 2: AWS Secret Access Keys (heuristic)
# ============================================================
echo ""
echo "[2/7] Scanning for AWS Secret Access Keys..."

SECRET_FOUND=0
while IFS= read -r FILE; do
  [ -f "${FILE}" ] || continue
  # Look for 40-char base64 string adjacent to secret key indicators
  if grep -iEq "(aws_secret_access_key|AWS_SECRET|secret_key)\s*[=:]\s*[A-Za-z0-9+/]{40}" "${FILE}" 2>/dev/null; then
    block "Possible AWS Secret Access Key found in: ${FILE}"
    echo "       Remove it and rotate the key immediately."
    SECRET_FOUND=1
  fi
done <<< "${STAGED_FILES}"

if [ "${SECRET_FOUND}" -eq 0 ]; then
  ok "No AWS Secret Access Key patterns found."
fi

# ============================================================
# Check 3: .pem and private key files
# ============================================================
echo ""
echo "[3/7] Checking for .pem / private key files..."

PEM_FOUND=0
while IFS= read -r FILE; do
  case "${FILE}" in
    *.pem|*.key|id_rsa|id_ed25519|id_ecdsa|*.p12|*.pfx|*.jks)
      block "Private key or certificate file staged: ${FILE}"
      echo "       Private keys must NEVER be committed to version control."
      echo "       Remove from staging: git reset HEAD ${FILE}"
      echo "       Add to .gitignore:   echo '${FILE}' >> .gitignore"
      PEM_FOUND=1
      ;;
    *)
      # Check file contents for PEM headers
      if [ -f "${FILE}" ] && grep -q "BEGIN.*PRIVATE KEY\|BEGIN CERTIFICATE\|BEGIN RSA\|BEGIN OPENSSH" "${FILE}" 2>/dev/null; then
        block "File contains private key / certificate data: ${FILE}"
        echo "       Remove from staging: git reset HEAD ${FILE}"
        PEM_FOUND=1
      fi
      ;;
  esac
done <<< "${STAGED_FILES}"

if [ "${PEM_FOUND}" -eq 0 ]; then
  ok "No .pem or private key files found in staged files."
fi

# ============================================================
# Check 4: Terraform state files
# ============================================================
echo ""
echo "[4/7] Checking for Terraform state files..."

TF_STATE_FOUND=0
while IFS= read -r FILE; do
  case "${FILE}" in
    terraform.tfstate|*/terraform.tfstate|*.tfstate|*.tfstate.backup)
      block "Terraform state file staged: ${FILE}"
      echo "       State files contain real resource IDs, IPs, and sensitive data."
      echo "       Remove from staging: git reset HEAD ${FILE}"
      echo "       These files are gitignored by default — check .gitignore."
      TF_STATE_FOUND=1
      ;;
  esac
done <<< "${STAGED_FILES}"

if [ "${TF_STATE_FOUND}" -eq 0 ]; then
  ok "No Terraform state files found in staged files."
fi

# ============================================================
# Check 5: .env files with real values
# ============================================================
echo ""
echo "[5/7] Checking for .env files..."

ENV_FOUND=0
while IFS= read -r FILE; do
  case "${FILE}" in
    .env|.env.local|.env.prod|.env.dev|.env.staging|*.env)
      # Allow *.env.example files
      if [[ "${FILE}" == *.env.example ]]; then
        ok "Environment example file is safe to commit: ${FILE}"
      else
        block ".env file staged: ${FILE}"
        echo "       .env files may contain real credentials."
        echo "       Remove from staging: git reset HEAD ${FILE}"
        echo "       Commit only *.env.example files (with placeholder values)."
        ENV_FOUND=1
      fi
      ;;
    .claude/env/*.env)
      if [[ "${FILE}" == *.env.example ]]; then
        ok "Claude env example file is safe to commit: ${FILE}"
      else
        block "Claude env file with real values staged: ${FILE}"
        echo "       Remove from staging: git reset HEAD ${FILE}"
        ENV_FOUND=1
      fi
      ;;
  esac
done <<< "${STAGED_FILES}"

if [ "${ENV_FOUND}" -eq 0 ] && ! echo "${STAGED_FILES}" | grep -qE '\.env$|\.env\.local$|\.env\.prod$'; then
  ok "No real .env files found in staged files."
fi

# ============================================================
# Check 6: Hardcoded IP addresses
# ============================================================
echo ""
echo "[6/7] Scanning for hardcoded IP addresses..."

HARDCODED_IP_FOUND=0
SKIP_IP_PATTERN="127\.0\.0\.1|0\.0\.0\.0|localhost|::1"

while IFS= read -r FILE; do
  [ -f "${FILE}" ] || continue
  # Skip binary files and known safe files
  case "${FILE}" in
    *.class|*.jar|*.png|*.jpg|*.gif|*.ico|*.pdf) continue ;;
  esac

  # Find non-loopback IPv4 addresses
  FOUND_IPS=$(grep -oE "\b([0-9]{1,3}\.){3}[0-9]{1,3}\b" "${FILE}" 2>/dev/null | \
    grep -vE "${SKIP_IP_PATTERN}" | \
    grep -vE "^(255\.|172\.(1[6-9]|2[0-9]|3[01])\.|10\.|192\.168\.)" || true)

  if [ -n "${FOUND_IPS}" ]; then
    warn "Hardcoded public IP address(es) in: ${FILE}"
    echo "       IPs found: $(echo "${FOUND_IPS}" | tr '\n' ' ')"
    echo "       Use environment variables instead: \${EC2_PUBLIC_IP}"
    HARDCODED_IP_FOUND=1
  fi
done <<< "${STAGED_FILES}"

if [ "${HARDCODED_IP_FOUND}" -eq 0 ]; then
  ok "No hardcoded public IP addresses found."
fi

# ============================================================
# Check 7: .claude/settings.local.json (may contain live IPs)
# ============================================================
echo ""
echo "[7/7] Checking for .claude/settings.local.json..."

while IFS= read -r FILE; do
  if [ "${FILE}" = ".claude/settings.local.json" ]; then
    block ".claude/settings.local.json staged: this file may contain live EC2 IPs."
    echo "       Remove from staging: git reset HEAD .claude/settings.local.json"
    echo "       This file is gitignored for good reason."
  fi
done <<< "${STAGED_FILES}"

if ! echo "${STAGED_FILES}" | grep -q "settings.local.json"; then
  ok ".claude/settings.local.json is not staged."
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================"
echo " Pre-Commit Security Check Summary"
echo ""
echo " Staged files: $(echo "${STAGED_FILES}" | wc -l | tr -d ' ')"
echo -e " ${RED}BLOCKED${NC}: ${BLOCKED}"
echo -e " ${YELLOW}WARNED${NC}: ${WARNINGS}"
echo "============================================"

if [ "${BLOCKED}" -gt 0 ]; then
  echo ""
  echo -e "${RED}COMMIT BLOCKED — ${BLOCKED} security issue(s) found.${NC}"
  echo ""
  echo "To fix:"
  echo "  1. Remove the flagged files from staging: git reset HEAD <file>"
  echo "  2. Add them to .gitignore"
  echo "  3. Remove any hardcoded credentials from source files"
  echo "  4. Stage your changes again and commit"
  echo ""
  echo "Emergency bypass (document the reason in the commit message):"
  echo "  git commit --no-verify -m 'your message'"
  exit 1
else
  echo ""
  echo -e "${GREEN}COMMIT ALLOWED — No blocking security issues found.${NC}"
  if [ "${WARNINGS}" -gt 0 ]; then
    echo -e "${YELLOW}Review ${WARNINGS} warning(s) above.${NC}"
  fi
  exit 0
fi
