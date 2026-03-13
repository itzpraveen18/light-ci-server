# Skill: build-docker-image

## Purpose
Build and tag a Docker image for the LightCI Server application.

## Prerequisites
- Docker running (`docker info`)
- `Dockerfile` present at project root
- JAR built at `target/light-ci-server.jar` OR Docker will build it (multi-stage)

## Dockerfile Summary
The existing `Dockerfile` uses a 3-stage build:
1. **Stage 1** (`maven:3.9-amazoncorretto-17`) — builds the fat JAR
2. **Stage 2** (`amazoncorretto:17-alpine`) — runs `jlink` to create a minimal JRE
3. **Stage 3** (`alpine:3.19`) — final image (~148 MB) with custom JRE + JAR only

## Build Commands

### Build locally (default tag)
```bash
docker build -t light-ci-server:latest .
```

### Build with a version tag
```bash
docker build -t light-ci-server:1.0.0 .
```

### Build for Docker Hub
```bash
docker build -t <dockerhub-username>/light-ci-server:latest .
```

### Build for Amazon ECR
```bash
docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/light-ci-server:latest .
```

## Tag an Existing Image

### For Docker Hub
```bash
docker tag light-ci-server:latest <dockerhub-username>/light-ci-server:latest
```

### For Amazon ECR
```bash
docker tag light-ci-server:latest <account-id>.dkr.ecr.<region>.amazonaws.com/light-ci-server:latest
```

## Verify the Image
```bash
docker images | grep light-ci-server
# Check size — should be ~148 MB
```

## Test the Image Locally
```bash
docker run -d -p 9090:8080 --name lightci-test light-ci-server:latest
# Open http://localhost:9090
```

## Notes
- Image supports both ARM64 (Apple Silicon) and AMD64 (Linux/EC2)
- The multi-stage build keeps the final image minimal — no Maven, no JDK, no source
- Non-root user `appuser` is used for security
