# Skill: push-docker-image

## Purpose
Push the built Docker image to a container registry (Amazon ECR or Docker Hub).

---

## Option A: Amazon ECR (Preferred when using AWS)

### Prerequisites
- AWS CLI installed and configured (`aws configure`)
- ECR repository created

### 1. Create ECR Repository (one time)
```bash
aws ecr create-repository \
  --repository-name light-ci-server \
  --region <your-region>
```

### 2. Authenticate Docker with ECR
```bash
aws ecr get-login-password --region <your-region> | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.<your-region>.amazonaws.com
```

### 3. Tag the Image
```bash
docker tag light-ci-server:latest \
  <account-id>.dkr.ecr.<your-region>.amazonaws.com/light-ci-server:latest
```

### 4. Push the Image
```bash
docker push \
  <account-id>.dkr.ecr.<your-region>.amazonaws.com/light-ci-server:latest
```

### 5. Verify
```bash
aws ecr list-images \
  --repository-name light-ci-server \
  --region <your-region>
```

---

## Option B: Docker Hub

### Prerequisites
- Docker Hub account
- Logged in: `docker login -u <dockerhub-username>`

### 1. Tag the Image
```bash
docker tag light-ci-server:latest <dockerhub-username>/light-ci-server:latest
```

### 2. Push the Image
```bash
docker push <dockerhub-username>/light-ci-server:latest
```

### 3. Verify
```bash
docker search <dockerhub-username>/light-ci-server
```

---

## Notes
- Replace `<account-id>`, `<your-region>`, `<dockerhub-username>` with actual values
- ECR images are private by default; Docker Hub repos can be public or private
- ECR is preferred for AWS deployments — avoids Docker Hub rate limits on EC2
