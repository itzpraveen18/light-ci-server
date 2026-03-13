---
name: infra-plan
description: Generate Terraform HCL files to provision AWS EC2 infrastructure via AWSInfraAgent
type: command
agent: AWSInfraAgent
auto_approve: true
config_source: .claude/config.yml
---

# Command: /infra-plan

## Description

Triggers **AWSInfraAgent** to generate complete Terraform files for provisioning
the LightCI Server EC2 infrastructure on AWS. Claude produces `terraform/main.tf`,
`terraform/variables.tf`, and `terraform/outputs.tf` as file contents for the
developer to review. Claude never runs `terraform apply` or any AWS CLI mutation.

---

## Agent

**AWSInfraAgent** (`.claude/agents/AWSInfraAgent.md`)
**Skill**: `.skills/terraform-ec2.md`
**MCP Servers**: `terraform` (schema lookup), `aws-docs` (documentation), `aws-core` (guidance)

---

## Prerequisites

Before invoking `/infra-plan`, ensure:
- Docker image has been pushed (run `/docker-build` first).
- AWS CLI is configured: `aws sts get-caller-identity` returns your account info.
- Environment variables are set: `AWS_REGION`, `EC2_KEY_PAIR_NAME`, `EC2_INSTANCE_TYPE`.
- Terraform >= 1.5.0 is installed: `terraform -version`.

---

## What Claude Generates

When you invoke `/infra-plan`, Claude will:

1. Read existing `terraform/` files to understand current state.
2. Generate updated or new Terraform files for EC2 provisioning.
3. Show a human-readable summary of what `terraform apply` will create.
4. List the estimated AWS resources and their approximate costs.
5. Provide the exact commands to run (developer executes these, not Claude).

---

## Resources That Will Be Created

```
Plan: 2 to add, 0 to change, 0 to destroy.

  + aws_security_group.lightci_sg
      name                     = "lightci-server-sg"
      vpc_id                   = (from data.aws_vpc.default)
      ingress[0]:  TCP 22   from 0.0.0.0/0  (SSH)
      ingress[1]:  TCP 8080 from 0.0.0.0/0  (Application)
      egress[0]:   All      to   0.0.0.0/0  (Outbound)

  + aws_instance.lightci_server
      ami                      = (latest Amazon Linux 2023)
      instance_type            = t3.micro
      key_name                 = (from var.key_pair_name)
      subnet_id                = (default subnet)
      vpc_security_group_ids   = [aws_security_group.lightci_sg.id]
      root_block_device:
        volume_size            = 20 GB (gp3, encrypted)
        delete_on_termination  = true
      user_data                = <Docker installation script>
      tags:
        Name        = lightci-server
        Project     = LightCI
        Environment = dev
        ManagedBy   = Terraform
```

---

## Estimated AWS Costs

| Resource | Type | Free Tier | On-Demand (us-east-1) |
|---|---|---|---|
| EC2 Instance | t3.micro | 750 hrs/month (12 months) | ~$0.0104/hr (~$7.50/month) |
| EBS gp3 Volume | 20 GB | 30 GB/month (12 months) | ~$1.60/month |
| Data Transfer | Outbound | 1 GB/month free | $0.09/GB after |
| **Total (on-demand)** | | | **~$9.10/month** |

---

## Commands to Run (Developer Executes)

```bash
# Navigate to terraform directory
cd terraform/

# Step 1: Initialize Terraform (downloads AWS provider)
terraform init

# Step 2: Validate configuration
terraform validate

# Step 3: Review plan (SAFE — read-only, no changes)
terraform plan \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
  -var="instance_type=${EC2_INSTANCE_TYPE}" \
  -var="environment=dev"

# Step 4: Apply (CREATES REAL RESOURCES — review plan first!)
# terraform apply \
#   -var="region=${AWS_REGION}" \
#   -var="key_pair_name=${EC2_KEY_PAIR_NAME}" \
#   -var="instance_type=${EC2_INSTANCE_TYPE}" \
#   -var="environment=dev"

# Step 5: Get outputs after apply
# terraform output instance_public_ip
# terraform output app_url
# terraform output ssh_command
```

---

## Generated Files Summary

### terraform/main.tf
Contains:
- Terraform version and AWS provider constraints
- `data.aws_vpc.default` — uses your account's default VPC
- `data.aws_subnet.default` — uses the first default subnet
- `data.aws_ami.amazon_linux_2023` — auto-selects latest AL2023 AMI
- `aws_security_group.lightci_sg` — SSH (22) + App (8080) + all egress
- `aws_instance.lightci_server` — EC2 with Docker user_data bootstrap

### terraform/variables.tf
Contains:
- `var.region` (default: us-east-1)
- `var.aws_profile` (default: default)
- `var.instance_type` (default: t3.micro)
- `var.key_pair_name` (required)
- `var.app_port` (default: 8080)
- `var.environment` (default: dev)

### terraform/outputs.tf
Contains:
- `output.instance_public_ip` — set as `EC2_PUBLIC_IP` for deploy step
- `output.app_url` — full URL to the application
- `output.ssh_command` — ready-to-use SSH command
- `output.instance_id` — for AWS console navigation

---

## Post-Apply Steps

After running `terraform apply`, collect the outputs:

```bash
export EC2_PUBLIC_IP=$(terraform output -raw instance_public_ip)
echo "EC2_PUBLIC_IP=${EC2_PUBLIC_IP}" >> ../.env

echo "Application will be at: http://${EC2_PUBLIC_IP}:8080"
echo "Wait 2-3 minutes for instance to boot and Docker to install."
```

Then proceed to run the deploy script:

```bash
cd ..
bash scripts/deploy.sh
```

---

## Destroying Infrastructure

When done, destroy all resources to avoid charges:

```bash
cd terraform/
terraform destroy \
  -var="region=${AWS_REGION}" \
  -var="key_pair_name=${EC2_KEY_PAIR_NAME}"
```

This command removes the EC2 instance and security group. The EBS volume is
deleted because `delete_on_termination = true`.

---

## Security Notes

1. The security group allows SSH from `0.0.0.0/0`. For production, restrict to your IP:
   ```hcl
   cidr_blocks = ["YOUR_IP/32"]
   ```
2. Never commit `terraform.tfstate` — it contains the public IP and resource IDs.
3. For teams, configure an S3 backend for shared state:
   ```hcl
   terraform {
     backend "s3" {
       bucket         = "your-terraform-state-bucket"
       key            = "lightci/terraform.tfstate"
       region         = "us-east-1"
       encrypt        = true
       dynamodb_table = "terraform-lock"
     }
   }
   ```
