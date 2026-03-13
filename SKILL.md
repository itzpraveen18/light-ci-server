# LightCI Server — Skills Index

This file is the canonical index of all Claude skills available in this project.
Skills live in `.skills/` and define reusable step-by-step procedures that agents follow.
Claude never executes skills directly — it generates plans and scripts based on them.

---

## Skills Overview

| Skill File | Skill Name | Purpose | Used By Agent |
|---|---|---|---|
| `.skills/java-build.md` | `java-build` | Maven build lifecycle | JavaBuildAgent |
| `.skills/docker-build.md` | `docker-build` | Docker image build and tag | DockerAgent |
| `.skills/docker-push.md` | `docker-push` | Docker image push to registry | DockerAgent |
| `.skills/terraform-ec2.md` | `terraform-ec2` | Generate Terraform for EC2 + SG | AWSInfraAgent |
| `.skills/ec2-deploy.md` | `ec2-deploy` | Generate EC2 SSH deployment script | DeploymentAgent |

---

## Skill: java-build

**File**: `.skills/java-build.md`

**Purpose**: Generates Maven build instructions for the LightCI Server Spring Boot application.

**Inputs**:
- `pom.xml` path (default: project root)
- Maven goals (default: `clean package`)
- Java version (default: 17)
- Skip tests flag (optional, default: false)
- Maven profile (optional)

**Outputs**:
- Shell script with `mvn` commands
- Expected artifact path: `target/light-ci-server.jar`
- Build verification commands

**Used By**: JavaBuildAgent

**Invocation example**:
```
"Follow the java-build skill to generate a build script that skips tests."
```

---

## Skill: docker-build

**File**: `.skills/docker-build.md`

**Purpose**: Generates Docker build and tag instructions for the LightCI Server image.

**Inputs**:
- `DOCKER_USERNAME` — Docker Hub username
- `DOCKER_IMAGE` — image name (e.g. `light-ci-server`)
- `DOCKER_TAG` — image tag (e.g. `1.0.0`, `latest`)
- `Dockerfile` path (default: project root)
- Build platform (optional, e.g. `linux/amd64`)

**Outputs**:
- Shell script with `docker build` and `docker tag` commands
- Tagged image reference: `${DOCKER_USERNAME}/${DOCKER_IMAGE}:${DOCKER_TAG}`

**Used By**: DockerAgent

**Invocation example**:
```
"Use the docker-build skill to generate a build script for tag v2.1.0."
```

---

## Skill: docker-push

**File**: `.skills/docker-push.md`

**Purpose**: Generates Docker push instructions to Docker Hub or AWS ECR.

**Inputs**:
- `DOCKER_USERNAME` — Docker Hub username (or ECR registry URL)
- `DOCKER_IMAGE` — image name
- `DOCKER_TAG` — image tag
- Registry type: `dockerhub` or `ecr`
- `AWS_REGION` (required for ECR only)
- `AWS_ACCOUNT_ID` (required for ECR only)

**Outputs**:
- Shell script with `docker login` and `docker push` commands
- Push verification command (`docker manifest inspect`)

**Used By**: DockerAgent

**Invocation example**:
```
"Use the docker-push skill to generate ECR push instructions for us-east-1."
```

---

## Skill: terraform-ec2

**File**: `.skills/terraform-ec2.md`

**Purpose**: Generates complete Terraform HCL files to provision an EC2 instance for
the LightCI Server, including VPC/subnet data sources, security group, and instance.

**Inputs**:
- `AWS_REGION` — target region (e.g. `us-east-1`)
- `EC2_INSTANCE_TYPE` — instance type (e.g. `t3.micro`)
- `EC2_KEY_PAIR_NAME` — existing AWS key pair name
- `APP_PORT` — application port (default: 8080)
- AMI ID (optional; skill selects latest Amazon Linux 2023 AMI if omitted)

**Outputs**:
- `terraform/main.tf` — provider, data sources, security group, EC2 instance
- `terraform/variables.tf` — input variable declarations
- `terraform/outputs.tf` — public IP, app URL, SSH command

**MCP Servers used**: `terraform` (schema lookup), `aws-docs` (AMI reference)

**Used By**: AWSInfraAgent

**Invocation example**:
```
"Use the terraform-ec2 skill to generate Terraform for a t3.small in eu-west-1."
```

---

## Skill: ec2-deploy

**File**: `.skills/ec2-deploy.md`

**Purpose**: Generates a complete shell deployment script that, when run by the
developer, SSHes into an EC2 instance, installs Docker, pulls the application image,
and starts the container. Claude generates this script only — it never runs it.

**Inputs**:
- `EC2_PUBLIC_IP` — public IP of the EC2 instance
- `SSH_KEY_PATH` — local path to the `.pem` key file
- `DOCKER_USERNAME` — Docker Hub username
- `DOCKER_IMAGE` — image name
- `DOCKER_TAG` — image tag
- `APP_PORT` — container port to expose (default: 8080)

**Outputs**:
- `scripts/deploy.sh` — full deployment script with SSH + Docker commands
- `scripts/docker-install.sh` — standalone Docker installation script for EC2

**Used By**: DeploymentAgent

**Invocation example**:
```
"Use the ec2-deploy skill to generate a deploy.sh for EC2 at 54.123.45.67
 using key ~/keys/lightci.pem and image myuser/light-ci-server:1.0.0."
```

---

## How Skills Relate to Commands

```
/build          --> JavaBuildAgent --> java-build skill
/docker-build   --> DockerAgent    --> docker-build skill + docker-push skill
/deploy         --> DeploymentAgent--> ec2-deploy skill
/infra-plan     --> AWSInfraAgent  --> terraform-ec2 skill
/security-audit --> SecurityAuditAgent (no skill — reads and analyses files directly)
```

---

## Adding a New Skill

1. Create `.skills/<skill-name>.md` following the existing skill format.
2. Register it in this file (SKILL.md) with all fields.
3. Reference it in the appropriate agent definition under `.claude/agents/`.
4. If a new command should invoke it, create `.claude/commands/<command>.md`.
