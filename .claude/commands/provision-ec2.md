---
name: provision-ec2
description: Provision AWS EC2 infrastructure using Terraform
type: command
agent: AWSInfraAgent
auto_approve: true
config_source: .claude/config.yml
---

Provision AWS EC2 infrastructure using Terraform.

Steps:
1. Navigate to the terraform directory and initialise:

```bash
cd /Users/praveen/python/light-ci-server/terraform
terraform init
```

2. Run plan and show the user what will be created:

```bash
terraform plan
```

3. Show the plan output and ask the user: "Ready to apply and create the EC2 instance on AWS?"

4. Only if the user confirms — run:

```bash
terraform apply -auto-approve
```

5. After apply succeeds, print:
   - EC2 Public IP (`terraform output ec2_public_ip`)
   - App URL (`terraform output app_url`)
   - SSH command (`terraform output ssh_command`)

IMPORTANT: Always ask for confirmation before `terraform apply` or `terraform destroy`.
