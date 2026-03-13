#!/bin/bash
# install-docker-ec2.sh — Install Docker on Amazon Linux 2023 EC2 instance
#
# Run this script ON the EC2 instance, not locally.
# The Terraform user_data in main.tf already runs this automatically on first boot.
#
# Manual usage (after SSH into EC2):
#   bash install-docker-ec2.sh

set -e

echo "[docker-install] Updating system packages..."
sudo yum update -y

echo "[docker-install] Installing Docker..."
sudo yum install -y docker

echo "[docker-install] Starting Docker service..."
sudo systemctl start docker
sudo systemctl enable docker

echo "[docker-install] Adding ec2-user to docker group..."
sudo usermod -aG docker ec2-user

echo "[docker-install] Docker version:"
docker --version

echo ""
echo "[docker-install] Done."
echo "NOTE: Log out and back in (or run 'newgrp docker') for group changes to take effect."
