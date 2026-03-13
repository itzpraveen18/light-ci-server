# LightCI Server — Claude Project Instructions

## Project Overview

A lightweight Jenkins-inspired CI dashboard built with Java 17 + Spring Boot 3.2.
Claude's role: generate plans, scripts, and configuration only. Never execute commands.

## Tech Stack

- Java 17, Maven 3.9
- Spring Boot 3.2 + Thymeleaf
- Docker (multi-stage build)
- AWS EC2 (deployment target)
- Terraform (infrastructure provisioning)

---

## Execution Rules

Claude CAN and SHOULD execute the following when asked:
- `mvn clean package` — build the JAR
- `docker build`, `docker tag`, `docker push` — build and push images
- `terraform init`, `terraform plan` — safe, read-only operations

Claude MUST ask for confirmation before:
- `terraform apply` — creates real AWS resources and incurs cost
- `terraform destroy` — deletes AWS infrastructure
- `docker run` on EC2 — deploys to production

---

## Deployment Flow

```
Developer Code
      ↓
Maven Build  (mvn clean package)
      ↓
Docker Image Build  (docker build)
      ↓
Push to Container Registry  (ECR or Docker Hub)
      ↓
Terraform: Create EC2 Instance
      ↓
SSH + Install Docker on EC2
      ↓
Pull Image + Run Container
      ↓
http://EC2_PUBLIC_IP:8080
```

---

## Skills (Reusable Automation)

Skills live in `.skills/`. Always reference an existing skill before writing new logic.

| Skill File | Purpose |
|------------|---------|
| `.skills/build-java-project.md` | Maven build instructions |
| `.skills/build-docker-image.md` | Docker image build + tag |
| `.skills/push-docker-image.md` | Push to ECR or Docker Hub |
| `.skills/generate-terraform-ec2.md` | Terraform EC2 + SG provisioning |
| `.skills/deploy-container-ec2.md` | SSH + Docker run on EC2 |

---

## Hooks

Hook descriptions live in `.claude/hooks/`. Claude describes when hooks run — never triggers them.

| Hook | Trigger |
|------|---------|
| `pre-build` | Before `mvn clean package` |
| `post-build` | After JAR is generated |
| `pre-deploy` | Before docker push / terraform apply |
| `post-deploy` | After container is running on EC2 |

---

## Build Process

Reference skill: `.skills/build-java-project.md`

```bash
cd light-ci-server
mvn clean package -DskipTests
# Output: target/light-ci-server.jar
```

---

## Docker Requirements

Reference skill: `.skills/build-docker-image.md`

- Dockerfile exists at project root
- Base image: `amazoncorretto:17-alpine` (ARM64 + AMD64 compatible)
- Exposes port `8080`
- Non-root user (`appuser`)

---

## Container Registry

Prefer **Amazon ECR** when AWS context is present, otherwise Docker Hub.

ECR repo pattern: `<account-id>.dkr.ecr.<region>.amazonaws.com/light-ci-server`
Docker Hub pattern: `<dockerhub-username>/light-ci-server`

---

## AWS Infrastructure

Reference skill: `.skills/generate-terraform-ec2.md`
Terraform files: `terraform/`

- Instance type: `t2.micro` (free tier)
- Default VPC + default subnet
- Security Group inbound: port 22 (SSH), port 8080 (app)
- Security Group outbound: all (`0.0.0.0/0`)
- Access: `0.0.0.0/0`

---

## Subagents

Claude may spawn these subagents for specialized tasks:

| Subagent | Responsibility |
|----------|---------------|
| `JavaBuildAgent` | Maven build, test, packaging |
| `DockerAgent` | Dockerfile, image build, push |
| `AWSInfraAgent` | Terraform, EC2, ECR, Security Groups |
| `DeploymentAgent` | SSH scripts, docker run on EC2 |

---

## MCP Servers (configured in `.mcp.json`)

| Server | Command | Purpose |
|--------|---------|---------|
| `terraform` | `npx @hashicorp/terraform-mcp-server` | Terraform registry docs, provider schemas, module lookup |
| `aws-docs` | `uvx awslabs.aws-documentation-mcp-server@latest` | Search AWS official documentation |
| `aws-core` | `uvx awslabs.core-mcp-server@latest` | AWS service guidance, best practices |

**When to use each:**
- `terraform` → looking up provider resource arguments, module versions, Terraform syntax
- `aws-docs` → EC2 instance types, IAM policies, ECR authentication steps
- `aws-core` → AWS SDK usage, service limits, region availability

MCP usage is **advisory only** — Claude reads docs and generates code/config, never applies changes directly.

---

## Expected Output from Claude

When assisting with deployment, provide:

1. Build instructions (`mvn clean package`)
2. Dockerfile (already exists)
3. Terraform files (`terraform/`)
4. Deployment scripts (`scripts/`)
5. Architecture explanation

Claude executes build and Docker commands directly. Terraform apply and EC2 deploy steps require explicit user confirmation first.
