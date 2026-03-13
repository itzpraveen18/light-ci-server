# Skill: deploy-container-ec2

## Purpose
SSH into the EC2 instance, install Docker, pull the image, and run the container.

## Prerequisites
- EC2 instance running (provisioned via `generate-terraform-ec2`)
- EC2 public IP known (`terraform output ec2_public_ip`)
- SSH key pair downloaded (`.pem` file)
- Docker image pushed to registry (ECR or Docker Hub)

## Deployment Script

Use the generated script at `scripts/deploy.sh` or follow these steps manually.

---

## Step-by-Step

### 1. Set permissions on SSH key
```bash
chmod 400 /path/to/your-key.pem
```

### 2. SSH into the EC2 instance
```bash
ssh -i /path/to/your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

### 3. Install Docker on EC2 (Amazon Linux 2023)
```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
# Log out and back in for group to take effect
```

### 4. Authenticate with ECR (if using ECR)
```bash
aws ecr get-login-password --region <your-region> | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.<your-region>.amazonaws.com
```

### 5. Pull the Docker image

#### From ECR:
```bash
docker pull <account-id>.dkr.ecr.<your-region>.amazonaws.com/light-ci-server:latest
```

#### From Docker Hub:
```bash
docker pull <dockerhub-username>/light-ci-server:latest
```

### 6. Run the container
```bash
docker run -d \
  --name lightci \
  --restart unless-stopped \
  -p 8080:8080 \
  <IMAGE_NAME>:latest
```

### 7. Verify the container is running
```bash
docker ps
docker logs lightci
```

### 8. Access the application
```
http://<EC2_PUBLIC_IP>:8080
```

---

## Using the Deploy Script

The script at `scripts/deploy.sh` automates steps 3–7:

```bash
./scripts/deploy.sh \
  --host <EC2_PUBLIC_IP> \
  --key /path/to/your-key.pem \
  --image <IMAGE_NAME>:latest
```

---

## Notes
- Port 8080 must be open in the Security Group (handled by Terraform)
- Use `--restart unless-stopped` so the container restarts on EC2 reboot
- For production: consider using a load balancer and HTTPS
