# Workflow: Build and Push (CI Only)

This workflow covers the CI portion of the pipeline: building the Java application,
validating the artifact, building the Docker image, and pushing it to Docker Hub.
No AWS infrastructure is created or modified.

Use this workflow when:
- You want to ship a new image without reprovisioning EC2
- You are iterating on the application code
- You want to verify the build passes before triggering a full deploy

---

## Prerequisites

```bash
# Load environment variables
source .env     # or: source .claude/env/dev.env.example (copy and edit first)

# Verify Docker Hub credentials are set
echo "DOCKER_USERNAME: ${DOCKER_USERNAME}"
echo "DOCKER_TAG: ${DOCKER_TAG}"

# Verify Java and Maven are installed
java -version     # Must show 17
mvn -version      # Must show Apache Maven 3.x

# Verify Docker daemon is running
docker info
```

---

## Step 1 — Pre-Build Validation

**Script**: `.claude/hooks/pre-build.sh`
**Purpose**: Validate Java 17, Maven, pom.xml, and environment before building.
**Who executes**: Developer
**Time**: ~5 seconds

```bash
bash .claude/hooks/pre-build.sh
```

**Expected output**:
```
[PASS] Java 17 is installed
[PASS] Maven is installed
[PASS] pom.xml exists at project root
[PASS] Source directory exists (42 Java files found)
[PASS] pom.xml has no uncommitted changes
[PASS] Sufficient disk space available (15000 MB)

Pre-build validation PASSED.
Safe to proceed with: mvn clean package -DskipTests
```

**If it fails**: Resolve all `[FAIL]` items before proceeding.

---

## Step 2 — Maven Build

**Command**: `/build` (in Claude chat)
**Agent**: JavaBuildAgent
**What Claude generates**: Full Maven build script
**Who executes**: Developer
**Time**: ~2-5 minutes (first run downloads dependencies)

### 2a. Get the build script from Claude
In Claude chat, type:
```
/build
```
Claude will generate a complete build script.

### 2b. Run the Maven commands

```bash
# Clean and build (skip tests for speed — run tests separately)
mvn clean package -DskipTests \
  --batch-mode \
  --no-transfer-progress \
  2>&1 | tee target/build.log
```

### 2c. Confirm success

```bash
# Check exit code
echo "Exit code: $?"

# Verify artifact exists
ls -lh target/light-ci-server.jar

# Check the build log
grep "BUILD SUCCESS" target/build.log && echo "Build confirmed successful"
```

**Expected artifact**: `target/light-ci-server.jar` (approximately 30-60 MB)

### Optional: Run tests

```bash
# Run tests separately (takes longer)
mvn test --batch-mode --no-transfer-progress 2>&1 | tee target/test.log

# Check test results
grep -E "Tests run:|BUILD" target/test.log | tail -5
```

---

## Step 3 — Post-Build Validation

**Script**: `.claude/hooks/post-build.sh`
**Purpose**: Validate the JAR artifact is valid and complete.
**Who executes**: Developer
**Time**: ~5 seconds

```bash
bash .claude/hooks/post-build.sh
```

**Expected output**:
```
[PASS] JAR artifact found: target/light-ci-server.jar
[PASS] JAR size looks healthy: 42M (44040192 bytes)
[PASS] JAR is a valid ZIP archive
[PASS] Spring Boot JAR confirmed. Spring Boot version: 3.2.3
[PASS] Main-Class in manifest: org.springframework.boot.loader.JarLauncher
[PASS] Build log shows BUILD SUCCESS
[PASS] JAR was built within the last 60 minutes

BUILD SUCCESS
Artifact: target/light-ci-server.jar
Size:     42M
```

**If it fails**: Do not proceed to Docker step. Fix the build first.

---

## Step 4 — Docker Build and Tag

**Command**: `/docker-build` (in Claude chat)
**Agent**: DockerAgent
**Skill**: `.skills/docker-build.md`
**What Claude generates**: Docker build and tag script
**Who executes**: Developer
**Time**: ~2-5 minutes (first run pulls base image layers)

### 4a. Get the Docker script from Claude
In Claude chat, type:
```
/docker-build
```
Claude will generate a Docker build + tag + push script.

### 4b. Verify Dockerfile exists

```bash
test -f Dockerfile && echo "Dockerfile found" || echo "ERROR: Dockerfile missing"
# If missing, ask Claude: "Use DockerAgent to generate a Dockerfile for this project"
```

### 4c. Build the image

```bash
IMAGE_REF="${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"

docker build \
  --platform linux/amd64 \
  --build-arg JAR_FILE=target/light-ci-server.jar \
  --build-arg APP_PORT="${APP_PORT:-8080}" \
  -t "${IMAGE_REF}" \
  -f Dockerfile \
  .
```

**Expected output**: `Successfully built <id>` and `Successfully tagged ${IMAGE_REF}`

### 4d. Tag with additional tags

```bash
# Also tag as latest for convenience during development
docker tag "${IMAGE_REF}" "${DOCKER_USERNAME}/${DOCKER_IMAGE}:latest"
```

### 4e. Verify the image

```bash
# List the image
docker images "${DOCKER_USERNAME}/${DOCKER_IMAGE}"

# Check image size (should be ~200-400 MB for Spring Boot with JRE)
docker image inspect "${IMAGE_REF}" --format='Size: {{.Size}}'

# Optional: Test the image locally before pushing
docker run -d --name lightci-test -p 8080:8080 "${IMAGE_REF}"
sleep 20
curl http://localhost:8080/actuator/health
docker stop lightci-test && docker rm lightci-test
```

---

## Step 5 — Docker Push to Registry

**Skill**: `.skills/docker-push.md`
**What Claude generates**: Docker login and push commands
**Who executes**: Developer
**Time**: ~2-10 minutes depending on image size and connection

### 5a. Login to Docker Hub

```bash
# Use Docker Hub access token (not your login password)
echo "${DOCKER_PASSWORD}" | docker login \
  --username "${DOCKER_USERNAME}" \
  --password-stdin
```

**Expected output**: `Login Succeeded`

### 5b. Push the image

```bash
IMAGE_REF="${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"

# Push the version-tagged image
docker push "${IMAGE_REF}"

# Push the latest tag
docker push "${DOCKER_USERNAME}/${DOCKER_IMAGE}:latest"
```

**Expected output** (for each push):
```
The push refers to repository [docker.io/myusername/light-ci-server]
...
1.0.0: digest: sha256:abc123... size: 1234
```

### 5c. Verify push succeeded

```bash
# Verify the image is in Docker Hub
docker manifest inspect "${IMAGE_REF}"

# Or check in browser:
echo "https://hub.docker.com/r/${DOCKER_USERNAME}/${DOCKER_IMAGE}/tags"
```

---

## Summary

| Step | Command | Time | Gate |
|---|---|---|---|
| 1. Pre-build | `bash .claude/hooks/pre-build.sh` | ~5s | Must pass |
| 2. Maven build | `mvn clean package -DskipTests` | ~2-5 min | JAR must exist |
| 3. Post-build | `bash .claude/hooks/post-build.sh` | ~5s | Must pass |
| 4. Docker build | `docker build ...` | ~2-5 min | Image must build |
| 5. Docker push | `docker push ...` | ~2-10 min | Must see digest |

---

## Verification Commands

After completing all steps, verify with:

```bash
# Confirm JAR exists and is fresh
ls -lh target/light-ci-server.jar

# Confirm image exists locally
docker images "${DOCKER_USERNAME}/${DOCKER_IMAGE}"

# Confirm image is in Docker Hub (requires internet)
docker manifest inspect "${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"

echo "Build and push complete."
echo "Image ready for deployment: ${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"
```

---

## Troubleshooting

### Build fails with OutOfMemoryError
```bash
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"
mvn clean package -DskipTests
```

### Docker push fails with "unauthorized"
```bash
docker logout
echo "${DOCKER_PASSWORD}" | docker login --username "${DOCKER_USERNAME}" --password-stdin
docker push "${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"
```

### Image build fails — JAR not found
The `COPY target/light-ci-server.jar` in Dockerfile requires the JAR to exist
in the build context. Always run the Maven build before Docker build.

### Docker daemon not running
- macOS/Windows: Open Docker Desktop
- Linux: `sudo systemctl start docker`
