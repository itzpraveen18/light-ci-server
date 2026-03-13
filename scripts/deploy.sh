#!/bin/bash
# deploy.sh — SSH into EC2 and run the LightCI container
#
# Usage:
#   ./scripts/deploy.sh --host <EC2_PUBLIC_IP> --key <PATH_TO_PEM> --image <IMAGE_NAME>
#
# Example:
#   ./scripts/deploy.sh \
#     --host 54.123.45.67 \
#     --key ~/.ssh/my-key.pem \
#     --image 123456789.dkr.ecr.us-east-1.amazonaws.com/light-ci-server:latest

set -e

# ── Parse arguments ────────────────────────────────────────────────────────────
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --host)  EC2_HOST="$2"; shift ;;
    --key)   SSH_KEY="$2";  shift ;;
    --image) IMAGE="$2";    shift ;;
    *) echo "Unknown parameter: $1"; exit 1 ;;
  esac
  shift
done

# ── Validate ──────────────────────────────────────────────────────────────────
[ -z "$EC2_HOST" ] && { echo "ERROR: --host is required"; exit 1; }
[ -z "$SSH_KEY" ]  && { echo "ERROR: --key is required";  exit 1; }
[ -z "$IMAGE" ]    && { echo "ERROR: --image is required"; exit 1; }
[ -f "$SSH_KEY" ]  || { echo "ERROR: Key file not found: $SSH_KEY"; exit 1; }

chmod 400 "$SSH_KEY"

echo "============================================"
echo " LightCI Deploy"
echo "  Host  : $EC2_HOST"
echo "  Image : $IMAGE"
echo "============================================"

# ── Deploy via SSH ─────────────────────────────────────────────────────────────
ssh -i "$SSH_KEY" \
    -o StrictHostKeyChecking=no \
    ec2-user@"$EC2_HOST" \
    bash -s -- "$IMAGE" << 'REMOTE'

  IMAGE=$1
  echo "[deploy] Pulling image: $IMAGE"
  docker pull "$IMAGE"

  echo "[deploy] Stopping old container (if any)..."
  docker rm -f lightci 2>/dev/null || true

  echo "[deploy] Starting container..."
  docker run -d \
    --name lightci \
    --restart unless-stopped \
    -p 8080:8080 \
    "$IMAGE"

  echo "[deploy] Container status:"
  docker ps --filter name=lightci

  echo "[deploy] Done. App available at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080"
REMOTE

echo ""
echo "Application URL: http://$EC2_HOST:8080"
