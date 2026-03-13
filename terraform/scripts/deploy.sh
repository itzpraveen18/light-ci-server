#!/bin/bash
set -e

# Parse arguments
HOST=""
KEY=""
IMAGE="praveenaws7/light-ci-server:latest"

while [[ $# -gt 0 ]]; do
  case $1 in
    --host)
      HOST="$2"
      shift 2
      ;;
    --key)
      KEY="$2"
      shift 2
      ;;
    --image)
      IMAGE="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

if [ -z "$HOST" ] || [ -z "$KEY" ]; then
  echo "Usage: $0 --host <EC2_IP> --key <PEM_PATH> [--image <IMAGE>]"
  exit 1
fi

echo "Deploying $IMAGE to $HOST..."

# Install Docker and run container on EC2
ssh -i "$KEY" -o StrictHostKeyChecking=no ec2-user@"$HOST" "
  set -e

  echo 'Installing Docker...'
  sudo yum update -y
  sudo yum install -y docker
  sudo systemctl start docker
  sudo usermod -a -G docker ec2-user

  echo 'Pulling Docker image...'
  docker pull $IMAGE

  echo 'Stopping any existing container...'
  docker stop light-ci-server || true
  docker rm light-ci-server || true

  echo 'Starting new container...'
  docker run -d \
    --name light-ci-server \
    -p 8080:8080 \
    --restart always \
    $IMAGE

  echo 'Container started successfully!'
  docker ps | grep light-ci-server
"

echo "✓ Deployment complete!"
echo "App should be accessible at: http://$HOST:8080"
