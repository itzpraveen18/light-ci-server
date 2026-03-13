# Skill: terraform-ec2

## Overview

**Skill ID**: `terraform-ec2`
**Used by**: AWSInfraAgent
**Purpose**: Generates complete Terraform HCL files to provision an EC2 instance
for the LightCI Server on AWS.

This skill defines the procedure AWSInfraAgent follows to generate `main.tf`,
`variables.tf`, and `outputs.tf`. It covers VPC/subnet data sources, security
group configuration, EC2 instance configuration, and the Docker bootstrap user_data.

Claude (via AWSInfraAgent) generates the files — the developer runs Terraform.

---

## Prerequisites

| Requirement | Description |
|---|---|
| Terraform CLI >= 1.5 | Installed on the developer's machine |
| AWS CLI configured | `aws sts get-caller-identity` must succeed |
| AWS account access | Permissions: `ec2:*`, `iam:PassRole` (for Terraform) |
| Existing key pair | EC2 key pair must exist in the target region |
| `AWS_REGION` set | Environment variable |
| `EC2_KEY_PAIR_NAME` set | Environment variable |

---

## Inputs

| Input | Variable | Default | Description |
|---|---|---|---|
| AWS Region | `var.region` | `us-east-1` | Region for all resources |
| AWS Profile | `var.aws_profile` | `default` | AWS CLI named profile |
| Instance Type | `var.instance_type` | `t3.micro` | EC2 instance type |
| Key Pair | `var.key_pair_name` | required | AWS key pair name |
| App Port | `var.app_port` | `8080` | Port to open in security group |
| Environment | `var.environment` | `dev` | Env label for resource tags |

---

## Outputs

| Output | Terraform Name | Description |
|---|---|---|
| EC2 public IP | `instance_public_ip` | Use as `EC2_PUBLIC_IP` |
| EC2 instance ID | `instance_id` | For AWS console |
| Application URL | `app_url` | `http://<ip>:8080` |
| Health URL | `health_url` | `http://<ip>:8080/actuator/health` |
| SSH command | `ssh_command` | Ready-to-run SSH command |
| Security group ID | `security_group_id` | For additional rules |

---

## Resources Generated

### Data Sources (read-only, no AWS resources created)

```hcl
data "aws_vpc" "default"          # Default VPC in the region
data "aws_subnet" "default"       # Default subnet in AZ "a"
data "aws_ami" "amazon_linux_2023" # Latest Amazon Linux 2023 x86_64
```

### Resources Created

```hcl
resource "aws_security_group" "lightci_sg"   # Security group with SSH + app port
resource "aws_instance" "lightci_server"      # EC2 instance with Docker user_data
```

---

## Step-by-Step Procedure

### Phase 1: Determine AMI (never hardcoded)

AWSInfraAgent always uses a `data "aws_ami"` source to auto-select the latest
Amazon Linux 2023 AMI. This avoids the common mistake of hardcoding an AMI ID
that becomes outdated or is not available in the target region.

```hcl
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
```

### Phase 2: Generate main.tf

Include in order:
1. `terraform {}` block with version constraints
2. `provider "aws"` with default_tags
3. Data sources (VPC, subnet, AMI)
4. `aws_security_group` with ingress rules for SSH + app port + all egress
5. `aws_instance` with Docker user_data bootstrap

### Phase 3: Generate variables.tf

One `variable` block per input, with:
- `description` — clear human-readable description
- `type` — explicit type (`string`, `number`, `bool`)
- `default` — sensible default where applicable

### Phase 4: Generate outputs.tf

One `output` block per key value, with `description`.

### Phase 5: Document the plan

AWSInfraAgent produces a summary:
- List of resources that will be created
- Estimated AWS cost
- Commands to run (developer executes these)

---

## User Data Script (Docker Bootstrap)

The EC2 instance `user_data` installs Docker on first boot:

```bash
#!/bin/bash
set -e
exec > /var/log/user-data.log 2>&1

echo "=== LightCI Bootstrap $(date) ==="
dnf update -y
dnf install -y docker curl wget jq
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user
touch /tmp/bootstrap-complete
echo "Bootstrap complete: $(date)"
```

This runs once when the EC2 instance first boots. If the bootstrap fails, check
`/var/log/user-data.log` on the instance.

---

## Complete Procedure — Commands to Run

```bash
cd terraform/

# 1. Initialize (downloads AWS provider plugin)
terraform init

# 2. Validate syntax (no AWS API calls)
terraform validate

# 3. Plan (READ ONLY — shows what will be created)
terraform plan \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
  -var="instance_type=${EC2_INSTANCE_TYPE:-t3.micro}" \
  -var="environment=dev"

# 4. Apply (CREATES REAL RESOURCES — review plan first)
terraform apply \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
  -var="instance_type=${EC2_INSTANCE_TYPE:-t3.micro}" \
  -var="environment=dev"

# 5. Get outputs
terraform output instance_public_ip
terraform output app_url
terraform output ssh_command
```

---

## Security Group Rules Summary

| Direction | Protocol | Port | CIDR | Purpose |
|---|---|---|---|---|
| Inbound | TCP | 22 | 0.0.0.0/0 | SSH admin access |
| Inbound | TCP | 8080 | 0.0.0.0/0 | LightCI web UI |
| Outbound | All | All | 0.0.0.0/0 | Docker pull, dnf, AWS APIs |

**Production note**: Restrict SSH to `YOUR_IP/32`. This skill generates a comment
flagging the `0.0.0.0/0` SSH rule as a hardening opportunity.

---

## How to Verify Success

```bash
# After terraform apply:

# 1. Confirm instance is running
aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=LightCI" \
  --query 'Reservations[*].Instances[*].[InstanceId,State.Name,PublicIpAddress]' \
  --region "${AWS_REGION}" \
  --output table

# 2. Test SSH connectivity (after ~2 min boot time)
EC2_IP=$(terraform output -raw instance_public_ip)
ssh -i "${SSH_KEY_PATH}" -o ConnectTimeout=10 ec2-user@${EC2_IP} "echo connected"

# 3. Verify Docker is installed on EC2
ssh -i "${SSH_KEY_PATH}" ec2-user@${EC2_IP} "docker --version"

# 4. Check bootstrap log
ssh -i "${SSH_KEY_PATH}" ec2-user@${EC2_IP} "cat /var/log/user-data.log"
```

---

## Common Errors and Fixes

### `Error: No default VPC for this user`
**Cause**: The default VPC was deleted in this region.
**Fix**: Create a new default VPC:
```bash
aws ec2 create-default-vpc --region "${AWS_REGION}"
```
Or update `main.tf` to use a specific VPC resource.

### `Error: InvalidKeyPair.NotFound`
**Cause**: The key pair named in `var.key_pair_name` doesn't exist in this region.
**Fix**:
```bash
aws ec2 create-key-pair \
  --key-name "${EC2_KEY_PAIR_NAME}" \
  --query 'KeyMaterial' \
  --output text \
  --region "${AWS_REGION}" > ~/keys/${EC2_KEY_PAIR_NAME}.pem
chmod 400 ~/keys/${EC2_KEY_PAIR_NAME}.pem
```

### `Error: UnauthorizedOperation`
**Cause**: IAM user/role lacks `ec2:RunInstances` or other required permissions.
**Fix**: Attach `AmazonEC2FullAccess` policy (or a scoped equivalent) to the IAM principal.

### `Error: VcpuLimitExceeded`
**Cause**: AWS account vCPU limit reached for the instance family.
**Fix**: Request a quota increase in the AWS Service Quotas console.

### Terraform state conflict (resources exist but state is gone)
**Fix**: Import the existing resource:
```bash
terraform import aws_instance.lightci_server i-0123456789abcdef0
```

---

## Teardown

When done, always destroy to avoid charges:

```bash
cd terraform/
terraform destroy \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}"
```

Verify destruction:
```bash
aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=LightCI" "Name=instance-state-name,Values=running" \
  --query 'Reservations[*].Instances[*].InstanceId' \
  --region "${AWS_REGION}"
# Expected: empty list []
```
