# Skill: generate-terraform-ec2

## Purpose
Provision an EC2 instance on AWS using Terraform to host the LightCI container.

## Prerequisites
- Terraform installed (`terraform -version`)
- AWS CLI configured (`aws configure`)
- Terraform files exist at `terraform/`

## Files
```
terraform/
├── main.tf        ← EC2 instance + Security Group
├── variables.tf   ← Configurable inputs
└── outputs.tf     ← EC2 public IP output
```

## Steps

### 1. Navigate to Terraform directory
```bash
cd terraform
```

### 2. Initialise Terraform
```bash
terraform init
```

### 3. Preview the plan (safe — no changes made)
```bash
terraform plan
```

### 4. Apply the plan (creates AWS resources)
```bash
terraform apply
# Type 'yes' when prompted
```

### 5. Get the EC2 Public IP
```bash
terraform output ec2_public_ip
```

### 6. Destroy infrastructure (when done)
```bash
terraform destroy
# Type 'yes' when prompted
```

## Variables

Edit `terraform/variables.tf` or pass overrides:

```bash
terraform apply \
  -var="aws_region=us-east-1" \
  -var="key_pair_name=my-key"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `us-east-1` | AWS region |
| `instance_type` | `t2.micro` | EC2 instance type (free tier) |
| `key_pair_name` | — | Your existing EC2 key pair name |
| `app_port` | `8080` | Application port |

## Notes
- Uses **Default VPC** and **default subnet** — no VPC creation needed
- Security Group opens port 22 (SSH) and 8080 (app) from `0.0.0.0/0`
- `t2.micro` is free tier eligible
- AMI used: Amazon Linux 2023 (latest for region)
