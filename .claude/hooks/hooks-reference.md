# Hooks Reference

Hooks are shell scripts that run automatically at specific points in the build/deploy lifecycle.
Claude describes when hooks should run — the developer triggers them.

---

## Hook: pre-build

**File:** `.claude/hooks/pre-build.sh`
**Triggers before:** `mvn clean package`

**Purpose:**
- Validate Java version (`java -version`)
- Validate Maven version (`mvn -version`)
- Check `pom.xml` exists
- Clean old artifacts (`rm -rf target/`)
- Set environment variables for the build

**Example:**
```bash
#!/bin/bash
set -e
echo "[pre-build] Checking Java version..."
java -version 2>&1 | grep "17" || { echo "Java 17 required"; exit 1; }
echo "[pre-build] Cleaning old artifacts..."
rm -rf target/
echo "[pre-build] Done."
```

---

## Hook: post-build

**File:** `.claude/hooks/post-build.sh`
**Triggers after:** `mvn clean package` completes successfully

**Purpose:**
- Verify JAR was created at `target/light-ci-server.jar`
- Print JAR size
- Run smoke test (optional: start JAR briefly and check HTTP response)

**Example:**
```bash
#!/bin/bash
set -e
JAR="target/light-ci-server.jar"
[ -f "$JAR" ] || { echo "[post-build] JAR not found!"; exit 1; }
echo "[post-build] JAR size: $(du -sh $JAR | cut -f1)"
echo "[post-build] Build succeeded."
```

---

## Hook: pre-deploy

**File:** `.claude/hooks/pre-deploy.sh`
**Triggers before:** `docker push` or `terraform apply`

**Purpose:**
- Confirm Docker image exists locally (`docker images`)
- Confirm AWS CLI is configured (`aws sts get-caller-identity`)
- Validate Terraform files exist
- Prompt developer for confirmation before cloud changes

**Example:**
```bash
#!/bin/bash
set -e
echo "[pre-deploy] Verifying Docker image..."
docker images | grep "light-ci-server" || { echo "Image not found. Run docker build first."; exit 1; }
echo "[pre-deploy] Verifying AWS credentials..."
aws sts get-caller-identity
echo "[pre-deploy] Ready to deploy."
```

---

## Hook: post-deploy

**File:** `.claude/hooks/post-deploy.sh`
**Triggers after:** Container is running on EC2

**Purpose:**
- Curl the application health endpoint to confirm it's live
- Print the public URL
- Optionally send a notification (Slack, email)

**Example:**
```bash
#!/bin/bash
set -e
EC2_IP=$1
echo "[post-deploy] Checking application at http://$EC2_IP:8080 ..."
curl -sf "http://$EC2_IP:8080" > /dev/null && \
  echo "[post-deploy] Application is live at http://$EC2_IP:8080" || \
  echo "[post-deploy] WARNING: Application did not respond."
```

---

## Hook Execution Order

```
pre-build
    ↓
mvn clean package
    ↓
post-build
    ↓
docker build + docker push
    ↓
pre-deploy
    ↓
terraform apply + SSH deploy
    ↓
post-deploy
```

---

## Notes
- Hooks must be made executable: `chmod +x .claude/hooks/*.sh`
- Hooks are advisory — Claude describes them but never triggers them
- Add hooks to your CI/CD pipeline (GitHub Actions, Jenkins, etc.) as needed
