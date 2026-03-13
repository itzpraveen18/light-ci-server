# LightCI Server — Enterprise Claude Project Instructions

## Project Overview

**LightCI Server** is a lightweight Jenkins-inspired Continuous Integration dashboard built
with Java 17 and Spring Boot 3.2. It provides a web UI for triggering, monitoring, and
reviewing CI pipeline runs without the operational overhead of a full Jenkins installation.

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 (LTS) |
| Framework | Spring Boot 3.2.3 + Thymeleaf |
| Build | Apache Maven 3.9 |
| Containerisation | Docker (multi-stage build) |
| Infrastructure | AWS EC2 (t3.micro / t3.small) |
| IaC | Terraform >= 1.5 |
| Registry | Docker Hub (default) / AWS ECR (optional) |
| OS Target | Amazon Linux 2023 |

### Repository Layout

```
light-ci-server/
├── CLAUDE.md                        # This file — project instructions for Claude
├── SKILL.md                         # Skills index — all available Claude skills
├── pom.xml                          # Maven project descriptor
├── Dockerfile                       # Production multi-stage Dockerfile
├── src/                             # Java source tree
│   ├── main/java/com/example/...
│   └── main/resources/application.properties
├── target/                          # Maven build output (gitignored)
│   └── light-ci-server.jar
├── terraform/                       # Terraform IaC
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
├── scripts/                         # Utility shell scripts (NOT executed by Claude)
├── .claude/                         # Claude project configuration root
│   ├── agents/                      # Agent definitions
│   │   ├── JavaBuildAgent.md
│   │   ├── DockerAgent.md
│   │   ├── AWSInfraAgent.md
│   │   ├── DeploymentAgent.md
│   │   └── SecurityAuditAgent.md
│   ├── commands/                    # Slash-command definitions
│   │   ├── build.md
│   │   ├── docker-build.md
│   │   ├── deploy.md
│   │   ├── infra-plan.md
│   │   └── security-audit.md
│   ├── hooks/                       # Lifecycle validation scripts
│   │   ├── pre-build.sh
│   │   ├── post-build.sh
│   │   ├── pre-deploy.sh
│   │   ├── post-deploy.sh
│   │   └── pre-commit.sh
│   ├── templates/                   # Parameterised file templates
│   │   ├── Dockerfile.template
│   │   ├── terraform-ec2.template.tf
│   │   └── deploy-script.template.sh
│   ├── env/                         # Environment variable examples (never real values)
│   │   ├── dev.env.example
│   │   └── prod.env.example
│   ├── workflows/                   # End-to-end workflow documentation
│   │   ├── full-deploy.md
│   │   └── build-and-push.md
│   ├── logs/                        # Generated outputs — all gitignored
│   │   ├── plans/
│   │   ├── audits/
│   │   ├── artifacts/
│   │   ├── sessions/
│   │   └── README.md
│   └── mcp/
│       └── README.md
└── .skills/                         # Reusable skill definitions
    ├── java-build.md
    ├── docker-build.md
    ├── docker-push.md
    ├── terraform-ec2.md
    └── ec2-deploy.md
```

---

## Central Configuration — `.claude/config.yml`

Claude reads `.claude/config.yml` automatically at the start of every session.
**You never need to type arguments in the CLI.** All inputs are sourced from this file.

```
.claude/config.yml        ← real values (gitignored, never committed)
.claude/config.yml.example ← safe template (committed to git)
```

To set up:
```bash
cp .claude/config.yml.example .claude/config.yml
# Edit .claude/config.yml with your real values once
```

### Argument resolution order (highest priority wins)
1. `.claude/config.yml` — primary source
2. Shell environment variables — override config if set
3. Claude CLI inline arguments — override both

---

## Auto-Approve Rules

Claude auto-approves all operations **except** when `SecurityAuditAgent` finds
a **CRITICAL** severity issue. In that case Claude always pauses and requires
an explicit **"yes"** before proceeding.

| Operation | Auto-Approved | Controlled by |
|-----------|:---:|---|
| Maven build | ✅ | `auto_approve.maven_build` |
| Docker build + tag | ✅ | `auto_approve.docker_build` |
| Docker push | ✅ | `auto_approve.docker_push` |
| Terraform plan | ✅ | `auto_approve.terraform_plan` |
| Terraform apply | ✅ | `auto_approve.terraform_apply` |
| Terraform destroy | ✅ | `auto_approve.terraform_destroy` |
| EC2 deploy | ✅ | `auto_approve.ec2_deploy` |
| MEDIUM / HIGH security findings | ✅ | `auto_approve.security_warnings` |
| **CRITICAL security finding** | ❌ **ALWAYS BLOCKED** | hardcoded — cannot override |

To require manual confirmation for any step, set its value to `false` in config.

---

## Safety Rules — Claude's Role in This Project

These rules are absolute and must never be overridden by any prompt.

### Claude NEVER executes:
- `mvn` commands — Claude generates the commands; the developer runs them.
- `docker build / push / run` — Claude generates the scripts; the developer runs them.
- `terraform init / plan / apply / destroy` — Claude generates Terraform files and
  documents what they will do; the developer runs Terraform.
- `ssh` or any remote command — Claude generates SSH scripts; the developer executes them.
- Any `aws` CLI command that mutates infrastructure — Claude generates instructions only.
- Any command that writes to `~/.aws/credentials` or modifies IAM.

### Claude DOES:
- Reads source files, pom.xml, Dockerfiles, Terraform files, and logs.
- Generates shell scripts as text output that the developer reviews before running.
- Generates Terraform HCL files that the developer reviews before applying.
- Performs security audits by reading file contents (never executing scanners).
- Answers questions about Maven, Docker, AWS, and Terraform configuration.
- Generates plans, architecture diagrams (text/mermaid), and runbooks.
- Updates source files when explicitly asked (Java, application.properties, pom.xml).

### Why This Matters
This project deploys to real AWS infrastructure with real costs. A mistaken
`terraform apply` or `docker run` against production can cause downtime or unexpected
charges. Claude acts as an expert advisor and code generator; humans remain the
execution layer at all times.

---

## Deployment Flow

```
Developer commits code
        |
        v
/security-audit  (SecurityAuditAgent scans for secrets/issues)
        |
        v
pre-build.sh  (validates Java 17, Maven, pom.xml, env vars)
        |
        v
/build  (JavaBuildAgent generates: mvn clean package)
        |
        v
post-build.sh  (validates target/light-ci-server.jar exists and is non-empty)
        |
        v
/docker-build  (DockerAgent generates: docker build + tag + push script)
        |
        v
/infra-plan  (AWSInfraAgent generates: Terraform files for EC2 + SG)
        |
        v
pre-deploy.sh  (validates Docker image, AWS CLI, SSH key, env vars)
        |
        v
/deploy  (DeploymentAgent generates: deploy.sh)
        |
        v
Developer runs deploy.sh manually
        |
        v
post-deploy.sh  (validates HTTP 200 from EC2_PUBLIC_IP:8080)
```

---

## How to Use Commands

Commands are invoked in Claude chat using the `/command-name` syntax. Each command
is defined in `.claude/commands/<name>.md` and describes exactly what Claude will
generate in response.

| Command | Triggers | Output |
|---|---|---|
| `/build` | JavaBuildAgent | Maven build script + instructions |
| `/docker-build` | DockerAgent | Docker build/tag/push script |
| `/deploy` | DeploymentAgent | `deploy.sh` for EC2 |
| `/infra-plan` | AWSInfraAgent | Terraform files + plan summary |
| `/security-audit` | SecurityAuditAgent | Security report by severity |

---

## How to Use Agents

Agents are autonomous Claude sub-tasks defined in `.claude/agents/<Name>.md`. Each
agent has a specific domain. When you invoke a command, Claude internally uses the
relevant agent's logic to generate output.

To invoke an agent directly, describe your need:
- "Use JavaBuildAgent to generate a build script for profile `production`."
- "Use DockerAgent to generate a Dockerfile for this project."
- "Use AWSInfraAgent to generate Terraform for us-west-2 using t3.small."

---

## How to Use Skills

Skills are reusable knowledge modules in `.skills/`. They define step-by-step
procedures that agents follow. Skills are referenced by agents but can also be
invoked independently:

- "Follow the java-build skill to generate build instructions."
- "Use the ec2-deploy skill to generate a deployment script for IP 1.2.3.4."

---

## How to Use Hooks

Hooks are shell scripts in `.claude/hooks/` that validate state before/after
each phase. They are NOT run by Claude — the developer runs them manually or
wires them into CI.

```bash
# Run before building:
bash .claude/hooks/pre-build.sh

# Run after building:
bash .claude/hooks/post-build.sh

# Install pre-commit hook:
cp .claude/hooks/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

---

## MCP Server Configuration

MCP (Model Context Protocol) servers provide Claude with access to external
documentation and tooling. See `.claude/mcp/README.md` for full configuration.

Quick summary:
- `terraform` — Terraform documentation and schema lookup
- `aws-docs` — Official AWS documentation search
- `aws-core` — AWS resource metadata (read-only, no mutations)

All MCP servers are **read-only and advisory only**. Claude never uses them to
mutate infrastructure.

Verify connected servers:
```bash
claude mcp list
```

---

## Workflow Descriptions

### Full Deploy (end-to-end)
See `.claude/workflows/full-deploy.md`

Steps: security-audit → pre-build → build → post-build → docker-build →
docker-push → infra-plan → pre-deploy → deploy → post-deploy

### Build and Push (CI only, no infra)
See `.claude/workflows/build-and-push.md`

Steps: pre-build → build → post-build → docker-build → docker-push

---

## Required Environment Variables

Copy the appropriate example file and populate real values (never commit real values):

```bash
cp .claude/env/dev.env.example .env
# Edit .env with your actual values
source .env
```

| Variable | Required By | Description |
|---|---|---|
| `AWS_REGION` | AWSInfraAgent, DeploymentAgent | AWS region (e.g. us-east-1) |
| `AWS_PROFILE` | AWSInfraAgent | AWS CLI named profile |
| `DOCKER_USERNAME` | DockerAgent, DeploymentAgent | Docker Hub username |
| `DOCKER_IMAGE` | DockerAgent, DeploymentAgent | Image name (e.g. light-ci-server) |
| `DOCKER_TAG` | DockerAgent, DeploymentAgent | Image tag (e.g. 1.0.0) |
| `EC2_KEY_PAIR_NAME` | AWSInfraAgent | AWS EC2 key pair name |
| `EC2_INSTANCE_TYPE` | AWSInfraAgent | EC2 instance type (e.g. t3.micro) |
| `EC2_PUBLIC_IP` | DeploymentAgent, post-deploy.sh | Public IP of deployed EC2 |
| `SSH_KEY_PATH` | DeploymentAgent | Local path to .pem key file |
| `APP_PORT` | All agents | Application port (default: 8080) |
| `TF_VAR_region` | Terraform | Mirrors AWS_REGION for Terraform |
| `TF_VAR_key_pair_name` | Terraform | Mirrors EC2_KEY_PAIR_NAME for Terraform |

---

## Application Details

- **Artifact**: `target/light-ci-server.jar` (fat JAR via Spring Boot Maven plugin)
- **Port**: 8080 (configurable via `APP_PORT`)
- **Health endpoint**: `GET /actuator/health` — returns `{"status":"UP"}`
- **Context path**: `/`
- **Main class**: `com.example.lightci.LightCiServerApplication`
- **Group ID**: `com.example`
- **Artifact ID**: `light-ci-server`
- **Version**: `1.0.0`
