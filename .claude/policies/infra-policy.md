---
name: infra-policy
description: AWS infrastructure constraints, Terraform standards, and EC2 configuration rules
type: policy
agent: AWSInfraAgent
auto_approve: true
---

# Infrastructure Policy — LightCI Server

## Purpose

This document defines the infrastructure constraints, AWS configuration standards,
Terraform state management rules, tagging requirements, and cost limits for the
LightCI Server project. All infrastructure operations are auto-approved — Claude
never pauses for confirmation on infrastructure-related tasks.

---

## Auto-Approve: Infrastructure Operations

**All infrastructure operations are auto-approved. Claude never pauses, prompts,
or requires confirmation before generating Terraform files, infra plans, or
deployment instructions.**

| Operation | Auto-Approved | Notes |
|---|:---:|---|
| `terraform plan` generation | Yes | Claude generates the plan summary; developer runs `terraform plan` |
| `terraform apply` generation | Yes | Claude generates apply instructions; developer executes |
| `terraform destroy` generation | Yes | Claude generates destroy runbook; developer executes |
| Security Group rule generation | Yes | Claude validates rules against this policy before generating |
| EC2 instance configuration | Yes | Claude enforces instance type and region constraints automatically |
| Tagging configuration | Yes | Claude includes required tags automatically in all Terraform output |

Remember: Claude generates Terraform files and instructions. The developer runs all
Terraform commands. Claude never executes `terraform init`, `plan`, `apply`, or
`destroy`.

---

## AWS Regions

### Allowed Regions

| Region | Code | Notes |
|---|---|---|
| US East (N. Virginia) | `us-east-1` | **Default** — lowest latency to Docker Hub, most service availability |
| US West (Oregon) | `us-west-2` | Allowed alternative |
| EU (Ireland) | `eu-west-1` | Allowed for EU-based development |

### Prohibited Regions

All other AWS regions are prohibited for this project to:
- Control costs (some regions have higher pricing)
- Reduce operational complexity
- Ensure consistent availability zone naming in Terraform templates

If a different region is required, update this policy and `.claude/config.yml`.

### Region Validation

Claude validates the `AWS_REGION` / `TF_VAR_region` value against the allowed list
before generating Terraform. If an unsupported region is detected, Claude reports
it and substitutes `us-east-1` automatically, logging the substitution.

---

## EC2 Instance Types

### Allowed Instance Types

| Instance Type | vCPU | RAM | Use Case | Notes |
|---|---|---|---|---|
| `t2.micro` | 1 | 1 GB | **Only allowed type for this project** | Free tier eligible; sufficient for dev/demo |

### Why t2.micro Only

This project is a development and demonstration CI dashboard, not a production
system with SLA requirements. Using t2.micro:
- Stays within AWS Free Tier limits (750 hours/month for 12 months)
- Caps accidental cost exposure
- Forces the architecture to remain lightweight

If the application requires more resources, the right solution is to optimise the
application (JVM heap tuning, connection pooling) before scaling up instance size.

### Prohibited Instance Types

All instance families and sizes other than `t2.micro` are prohibited, including:
- `t3.micro`, `t3.small`, `t3.medium` (even though they are cost-similar)
- `t2.small`, `t2.medium`
- Any `m5`, `c5`, `r5`, or compute-optimised family

> Note: CLAUDE.md lists `t3.micro / t3.small` as a general tech stack description.
> This policy is more specific: **only `t2.micro` is permitted** for actual
> provisioning. If requirements change, update both this policy and CLAUDE.md.

### Instance Type Validation

Claude checks `EC2_INSTANCE_TYPE` from config and enforces `t2.micro`. If any other
value is detected, Claude substitutes `t2.micro`, logs the substitution, and notes
the policy constraint in its output.

---

## Security Group Rules

### Inbound Rules

| Rule | Protocol | Port | Source | Justification |
|---|---|---|---|---|
| SSH access | TCP | 22 | `0.0.0.0/0` (dev) | SSH for deployment; restrict to your IP in production |
| Application HTTP | TCP | 8080 | `0.0.0.0/0` | LightCI Server application port |

> Security note: SSH from `0.0.0.0/0` is permitted for developer convenience in this
> project. Claude logs this as a MEDIUM finding during security audits. To restrict:
> replace `0.0.0.0/0` with your static IP in CIDR notation (e.g., `203.0.113.5/32`).

### Outbound Rules

| Rule | Protocol | Port | Destination | Justification |
|---|---|---|---|---|
| All outbound | All | All | `0.0.0.0/0` | Required for: package installs, Docker pull, AWS API calls, health check HTTP |

Outbound is unrestricted. If egress filtering is required, add specific allow rules
for: Docker Hub (`hub.docker.com`), AWS APIs, and yum/dnf package repositories.

### Terraform Security Group Template

```hcl
resource "aws_security_group" "light_ci_sg" {
  name        = "light-ci-server-sg"
  description = "Security group for LightCI Server — SSH + app port"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # Restrict to your IP for production
  }

  ingress {
    description = "LightCI application port"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}
```

---

## VPC Configuration

### Default VPC

This project uses the **AWS Default VPC** in the selected region.

| Setting | Value | Notes |
|---|---|---|
| VPC | Default VPC | No custom VPC provisioning required |
| Subnet | Default public subnet (any AZ) | Use `data.aws_subnet_ids` to select automatically |
| Public IP | Auto-assign enabled | Required for SSH access and application reachability |
| Internet Gateway | Default IGW (pre-exists) | No additional IGW provisioning |

### Terraform VPC Data Sources

```hcl
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}
```

### Why Default VPC

- Eliminates Terraform complexity for a dev/demo project
- All resources are reachable without NAT gateways or route table configuration
- Free tier usage is unaffected by VPC choice
- If network isolation is required in future, migrate to a custom VPC with private
  subnets + NAT gateway (update this policy when that decision is made)

---

## Tagging Requirements

All AWS resources provisioned by Terraform must include these tags:

| Tag Key | Required | Example Value | Description |
|---|---|---|---|
| `Project` | Yes | `light-ci-server` | Project identifier |
| `Environment` | Yes | `dev` / `prod` | Deployment environment |
| `ManagedBy` | Yes | `terraform` | Provisioning tool |
| `Owner` | Yes | Value from `var.owner` | Developer or team email |
| `CostCenter` | No | `personal` | For billing allocation |
| `CreatedAt` | No | ISO 8601 date | When resource was first created |

### Terraform Locals for Tags

```hcl
locals {
  common_tags = {
    Project     = "light-ci-server"
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
  }
}
```

Apply to all resources:
```hcl
resource "aws_instance" "light_ci" {
  # ... instance config ...
  tags = merge(local.common_tags, {
    Name = "light-ci-server-${var.environment}"
  })
}
```

---

## Terraform State Management

### State Backend

| Setting | Value |
|---|---|
| Backend type | Remote S3 backend (required for any persistent deployment) |
| S3 bucket naming | `<your-username>-tfstate-light-ci-server` |
| S3 key | `light-ci-server/terraform.tfstate` |
| Region | Same as `AWS_REGION` |
| Encryption | Server-side encryption enabled (`encrypt = true`) |
| State locking | DynamoDB table required (`terraform-state-lock`) |

### Required Backend Configuration

```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "your-username-tfstate-light-ci-server"
    key            = "light-ci-server/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}
```

### State File Rules

1. `terraform.tfstate` and `terraform.tfstate.backup` must never be committed to git.
2. `.gitignore` must contain `**/*.tfstate` and `**/*.tfstate.backup`.
3. The S3 bucket must have versioning enabled (allows state rollback).
4. The DynamoDB table must have `LockID` as the partition key (string).
5. If using local state for ephemeral dev testing, delete the state file before
   committing. Never leave local state files in the repository.

### Creating the State Infrastructure (One-Time Setup)

```bash
# Developer runs these commands once; Claude generates this runbook:
aws s3api create-bucket \
  --bucket your-username-tfstate-light-ci-server \
  --region us-east-1

aws s3api put-bucket-versioning \
  --bucket your-username-tfstate-light-ci-server \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket your-username-tfstate-light-ci-server \
  --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws dynamodb create-table \
  --table-name terraform-state-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

---

## Cost Limits and Controls

### Target Monthly Cost (AWS)

| Resource | Quantity | Monthly Cost Estimate |
|---|---|---|
| EC2 t2.micro | 1 instance, 24/7 | $0 (Free Tier) / ~$8.35 (post-free-tier) |
| EBS gp3 8GB root volume | 1 | $0 (Free Tier) / ~$0.64 |
| Elastic IP (attached) | 1 | $0 (while attached to running instance) |
| Data transfer out | < 1 GB/month | $0.09/GB (negligible for this use case) |
| S3 state bucket | Minimal | < $0.10/month |
| DynamoDB state lock | On-demand | < $0.10/month |
| **Total** | | **$0 (Free Tier) / ~$9/month** |

### Cost Controls

1. **Instance type enforcement**: Only `t2.micro` is allowed (enforced by this policy).
2. **Elastic IP release**: When the EC2 instance is not running, the EIP must be
   released to avoid the $0.005/hour idle EIP charge.
3. **No persistent storage beyond root EBS**: No RDS, EFS, or additional EBS volumes.
4. **No load balancers**: ALB/NLB are prohibited for this project — direct EC2 access only.
5. **No NAT Gateways**: Default VPC + public subnet eliminates NAT Gateway charges (~$32/month).

### AWS Billing Alert

Claude recommends setting up a billing alert. To set one up:
```bash
aws cloudwatch put-metric-alarm \
  --alarm-name "LightCI-Monthly-Cost-Alert" \
  --alarm-description "Alert when estimated charges exceed $10" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 86400 \
  --threshold 10 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=Currency,Value=USD \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT_ID:billing-alerts
```

---

## AMI Selection

Use Amazon Linux 2023 (AL2023) for EC2 instances.

```hcl
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
```

Rationale:
- Maintained and patched by Amazon
- Pre-installed with AWS CLI v2 and SSM agent
- Compatible with Docker CE installation via `dnf`
- Optimised for EC2 hypervisor (faster boot, Nitro support)

---

## Key Pair Management

| Rule | Details |
|---|---|
| Key pair location | Managed in AWS; private key stored locally only |
| Key pair name | Set via `EC2_KEY_PAIR_NAME` / `TF_VAR_key_pair_name` in config |
| Private key file | Stored at `SSH_KEY_PATH` (local path only, never committed) |
| Key permissions | `chmod 400 <keyfile.pem>` required before use |
| Key rotation | Generate a new key pair and update EC2 before deleting the old one |

---

## Terraform Variable Conventions

| Variable | Type | Description |
|---|---|---|
| `var.region` | string | AWS region (validated against allowed list) |
| `var.instance_type` | string | EC2 instance type (enforced as `t2.micro`) |
| `var.key_pair_name` | string | AWS key pair name |
| `var.environment` | string | `dev` or `prod` |
| `var.owner` | string | Developer email for tagging |
| `var.app_port` | number | Application port (default: `8080`) |
| `var.docker_image` | string | Full Docker image name including tag |

Supply values via `.claude/config.yml` → `TF_VAR_*` environment variables.
Never add `default` values for secrets or credentials in `variables.tf`.

---

## Related Policies

- `.claude/policies/deployment-policy.md` — Container and EC2 deployment rules
- `.claude/security/secret-detection.md` — Credential scanning
- `.claude/security/docker-scan.md` — Docker image security
- `.claude/agents/AWSInfraAgent.md` — Agent that implements this policy
