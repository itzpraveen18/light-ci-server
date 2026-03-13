# Workflow: Full End-to-End Deployment

This workflow takes the LightCI Server from source code to a running EC2 instance.
It covers every step from security audit through post-deploy verification.

**Important**: Claude generates scripts and plans at each step. The **developer**
executes every command. Claude never runs commands on your behalf in this workflow.

---

## Prerequisites

Before starting:
```bash
# 1. Load environment variables
cp .claude/env/dev.env.example .env
# Edit .env with your real values
source .env

# 2. Install pre-commit hook
cp .claude/hooks/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 3. Verify AWS credentials
aws sts get-caller-identity

# 4. Verify Java and Maven
java -version    # Must show 17
mvn -version     # Must show Apache Maven 3.x
```

---

## Step 1 — Security Audit

**Command**: `/security-audit` (in Claude chat)
**Agent**: SecurityAuditAgent
**What Claude generates**: A structured security report listing all findings by severity.
**Who executes**: Nobody — this is a read-only scan.
**Expected output**: Report in Claude output, saved to `.claude/logs/audits/YYYY-MM-DD-audit.md`

```
Developer action:
  1. Run /security-audit in Claude
  2. Read the report
  3. Fix any CRITICAL or HIGH findings before proceeding
  4. Re-run /security-audit if fixes were made
```

**Gate**: Do not proceed to build if CRITICAL findings exist.

---

## Step 2 — Pre-Build Validation

**Script**: `.claude/hooks/pre-build.sh`
**What it validates**: Java 17, Maven, pom.xml, disk space, env vars
**Who executes**: Developer
**Expected output**: All checks PASSED (exit code 0)

```bash
bash .claude/hooks/pre-build.sh
```

**Gate**: Script must exit 0 before running Maven.

---

## Step 3 — Maven Build

**Command**: `/build` (in Claude chat)
**Agent**: JavaBuildAgent
**Skill**: `.skills/java-build.md`
**What Claude generates**: A complete `mvn` build shell script
**Who executes**: Developer

```bash
# Claude generates this script — developer runs it:
mvn clean package -DskipTests \
  --batch-mode \
  --no-transfer-progress \
  2>&1 | tee target/build.log
```

**Expected output**: `target/light-ci-server.jar` (~30-60 MB)

**Gate**: `target/light-ci-server.jar` must exist and be non-empty.

---

## Step 4 — Post-Build Validation

**Script**: `.claude/hooks/post-build.sh`
**What it validates**: JAR exists, JAR is non-empty, Spring Boot manifest is valid
**Who executes**: Developer
**Expected output**: All checks PASSED

```bash
bash .claude/hooks/post-build.sh
```

**Sample successful output**:
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

**Gate**: Script must exit 0 before building Docker image.

---

## Step 5 — Docker Build, Tag, and Push

**Command**: `/docker-build` (in Claude chat)
**Agent**: DockerAgent
**Skills**: `.skills/docker-build.md`, `.skills/docker-push.md`
**What Claude generates**: A complete Docker build + tag + push shell script
**Who executes**: Developer

```bash
# Claude generates this script — developer runs it:
export DOCKER_USERNAME=myusername
export DOCKER_IMAGE=light-ci-server
export DOCKER_TAG=1.0.0

docker build \
  --platform linux/amd64 \
  -t "${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}" \
  -f Dockerfile \
  .

docker tag "${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}" \
           "${DOCKER_USERNAME}/${DOCKER_IMAGE}:latest"

echo "${DOCKER_PASSWORD}" | docker login --username "${DOCKER_USERNAME}" --password-stdin

docker push "${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}"
docker push "${DOCKER_USERNAME}/${DOCKER_IMAGE}:latest"
```

**Expected output**: Image visible at `https://hub.docker.com/r/${DOCKER_USERNAME}/light-ci-server`

**Gate**: Image must be pushed and visible in Docker Hub before infrastructure step.

---

## Step 6 — Infrastructure Plan and Apply

**Command**: `/infra-plan` (in Claude chat)
**Agent**: AWSInfraAgent
**Skill**: `.skills/terraform-ec2.md`
**MCP Servers**: terraform, aws-docs, aws-core (advisory only)
**What Claude generates**: Updated `terraform/main.tf`, `terraform/variables.tf`, `terraform/outputs.tf`
**Who executes**: Developer (reviews plan, then applies)

```bash
cd terraform/

# Step 6a: Initialize provider (safe, downloads plugins)
terraform init

# Step 6b: Validate syntax
terraform validate

# Step 6c: Review plan (SAFE — read-only)
terraform plan \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
  -var="instance_type=${EC2_INSTANCE_TYPE}" \
  -var="environment=dev"

# Step 6d: REVIEW THE PLAN OUTPUT CAREFULLY, then apply:
# (Creates real AWS resources — costs money)
terraform apply \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
  -var="instance_type=${EC2_INSTANCE_TYPE}" \
  -var="environment=dev"

# Step 6e: Capture EC2 IP
export EC2_PUBLIC_IP=$(terraform output -raw instance_public_ip)
echo "EC2_PUBLIC_IP=${EC2_PUBLIC_IP}" >> ../.env
echo "Instance ready at: http://${EC2_PUBLIC_IP}:8080 (after ~2 min boot)"

cd ..
```

**Resources created**:
- `aws_security_group.lightci_sg` — SSH (22) + App (8080) + all egress
- `aws_instance.lightci_server` — t3.micro, Amazon Linux 2023, 20 GB encrypted EBS

**Gate**: EC2 must be in `running` state and SSH must be reachable before deploying.

---

## Step 7 — Pre-Deploy Validation

**Script**: `.claude/hooks/pre-deploy.sh`
**What it validates**: Docker image exists, AWS CLI configured, Terraform files exist,
SSH key exists and has correct permissions, required env vars set.
**Who executes**: Developer
**Expected output**: All critical checks PASSED

```bash
source .env   # Reload with EC2_PUBLIC_IP now set
bash .claude/hooks/pre-deploy.sh
```

**Gate**: Script must exit 0 before running deploy script.

---

## Step 8 — Deploy to EC2

**Command**: `/deploy` (in Claude chat)
**Agent**: DeploymentAgent
**Skill**: `.skills/ec2-deploy.md`
**What Claude generates**: `scripts/deploy.sh` and `scripts/docker-install.sh`
**Who executes**: Developer

```bash
# If Docker not yet installed on EC2 (first-time only):
# bash scripts/docker-install.sh

# Run the deployment:
bash scripts/deploy.sh
```

**What deploy.sh does** (on the EC2 instance via SSH):
1. Pulls `${DOCKER_USERNAME}/light-ci-server:${DOCKER_TAG}` from Docker Hub
2. Stops and removes any existing `light-ci-server` container
3. Runs new container: `docker run -d --restart unless-stopped -p 8080:8080 ...`
4. Waits 30 seconds and curls `/actuator/health`

**Expected output**:
```
DEPLOYMENT SUCCESS
App URL:    http://54.123.45.67:8080
Health URL: http://54.123.45.67:8080/actuator/health
```

**Gate**: Deploy script must exit 0 (health check returns HTTP 200) before post-deploy step.

---

## Step 9 — Post-Deploy Validation

**Script**: `.claude/hooks/post-deploy.sh`
**What it validates**: HTTP 200 from health endpoint, container running (via SSH docker ps)
**Who executes**: Developer
**Expected output**: PASSED

```bash
bash .claude/hooks/post-deploy.sh
```

**Sample successful output**:
```
[PASS] TCP port 8080 is open on 54.123.45.67
[PASS] Health check PASSED — HTTP 200 from http://54.123.45.67:8080/actuator/health
[PASS] Spring Boot reports status: UP
[PASS] Application root (/) responds: HTTP 200
[PASS] Container 'light-ci-server' is running. Status: Up 2 minutes

 Post-Deploy Validation Summary
 App URL:    http://54.123.45.67:8080
 Health:     http://54.123.45.67:8080/actuator/health
 PASSED
```

---

## Summary Table

| Step | Action | Command/Script | Agent | Who Executes |
|---|---|---|---|---|
| 1 | Security audit | `/security-audit` | SecurityAuditAgent | (read-only) |
| 2 | Pre-build check | `bash .claude/hooks/pre-build.sh` | — | Developer |
| 3 | Maven build | `/build` → `mvn clean package` | JavaBuildAgent | Developer |
| 4 | Post-build check | `bash .claude/hooks/post-build.sh` | — | Developer |
| 5 | Docker build+push | `/docker-build` → `bash docker-build.sh` | DockerAgent | Developer |
| 6 | Infra plan+apply | `/infra-plan` → `terraform apply` | AWSInfraAgent | Developer |
| 7 | Pre-deploy check | `bash .claude/hooks/pre-deploy.sh` | — | Developer |
| 8 | EC2 deploy | `/deploy` → `bash scripts/deploy.sh` | DeploymentAgent | Developer |
| 9 | Post-deploy check | `bash .claude/hooks/post-deploy.sh` | — | Developer |

---

## Teardown (When Done)

To avoid ongoing AWS charges:

```bash
cd terraform/
terraform destroy \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}"
# Confirm: yes

# Verify destruction
aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=LightCI" \
  --query 'Reservations[*].Instances[*].[InstanceId,State.Name]' \
  --region "${AWS_REGION}"
```
