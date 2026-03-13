---
name: security-audit
description: Run comprehensive static security analysis across all project files via SecurityAuditAgent
type: command
agent: SecurityAuditAgent
auto_approve: true
config_source: .claude/config.yml
---

# Command: /security-audit

## Description

Triggers **SecurityAuditAgent** to perform a comprehensive static security analysis
of the LightCI Server project. Claude reads all project files and produces a
structured security report with findings classified by severity. Claude never
executes any security scanning tools — all analysis is static file review.

---

## Agent

**SecurityAuditAgent** (`.claude/agents/SecurityAuditAgent.md`)

---

## What Claude Scans

When you invoke `/security-audit`, Claude reads and analyses:

| Category | Files / Patterns |
|---|---|
| Source code | `src/**/*.java`, `src/**/*.properties`, `src/**/*.yml` |
| Build config | `pom.xml`, `Dockerfile` |
| Infrastructure | `terraform/*.tf`, `terraform/*.tfvars` |
| Scripts | `scripts/*.sh`, `.claude/hooks/*.sh` |
| Git config | `.gitignore`, `.git/config` |
| Environment | `.env`, `.env.*`, `**/*.env` |
| Terraform state | `terraform.tfstate`, `terraform.tfstate.backup` |
| Credentials | `**/*.pem`, `**/*.key`, `**/*.p12`, `**/*.jks` |

---

## Severity Levels

| Level | Meaning | Action Required |
|---|---|---|
| CRITICAL | Credential exposed, private key committed, or immediate data breach risk | Fix before any commit or push |
| HIGH | Misconfiguration enabling privilege escalation or credential theft | Fix before deploying to any environment |
| MEDIUM | Security best practice violation that increases attack surface | Fix before production deployment |
| LOW | Minor hardening improvement | Address in next sprint |
| INFO | Configuration is correct — noting for visibility | No action needed |

---

## Checks Performed

### Secrets Detection
- AWS Access Key ID pattern: `AKIA[0-9A-Z]{16}`
- AWS Secret Access Key: 40-char alphanumeric adjacent to `secret` keyword
- Private key headers: `-----BEGIN RSA PRIVATE KEY-----`, `-----BEGIN OPENSSH PRIVATE KEY-----`
- Hardcoded passwords in config files
- Docker Hub tokens or passwords in scripts
- Database connection strings with embedded credentials

### File Risk Analysis
- `.pem` files present in project tree and not gitignored
- `terraform.tfstate` tracked by git
- `.env` files with real values tracked by git
- `terraform.tfvars` tracked by git
- AWS credentials file copied into project
- Private SSH keys (`id_rsa`, `id_ed25519`) in project directory

### Spring Boot Security
- Actuator endpoints exposed: `management.endpoints.web.exposure.include=*`
- Stack traces in responses: `server.error.include-stacktrace=always`
- Debug logging in production profiles
- HTTP only (no TLS configuration)
- Missing `server.error.include-message` restriction

### Infrastructure Security
- SSH port 22 open to `0.0.0.0/0` in Terraform security group
- EBS volume without `encrypted = true`
- IAM wildcard actions or resources
- EC2 metadata service v1 enabled (IMDSv2 not enforced)
- Unencrypted S3 backend configuration

### .gitignore Completeness
Checks all critical patterns are gitignored:
- `*.pem`, `*.key`
- `.env`, `.env.*`
- `terraform.tfstate*`
- `terraform/.terraform/`
- `terraform/*.tfvars`
- `.claude/logs/plans/`, `.claude/logs/audits/`
- `.claude/logs/artifacts/`, `.claude/logs/sessions/`
- `.claude/env/*.env`
- `.claude/settings.local.json`

---

## Sample Output

```
============================================
 LightCI Server — Security Audit Report
 Date: 2026-03-12T00:00:00Z
 Project: /Users/praveen/python/light-ci-server
============================================

SUMMARY
-------
CRITICAL : 0
HIGH     : 1
MEDIUM   : 2
LOW      : 1
INFO     : 5

--------------------------------------------
HIGH FINDINGS
--------------------------------------------

[H-001] Spring Boot actuator wide-open
  File:    src/main/resources/application.properties
  Pattern: management.endpoints.web.exposure.include=*
  Risk:    Exposes /actuator/env (environment variables), /actuator/heapdump,
           /actuator/beans, and /actuator/loggers — potential credential leakage
           and heap dump extraction.
  Fix:     Change to:
             management.endpoints.web.exposure.include=health,info
           Or add Spring Security to require authentication for all actuator endpoints.

--------------------------------------------
MEDIUM FINDINGS
--------------------------------------------

[M-001] SSH open to the world
  File:    terraform/main.tf (ingress block, port 22)
  Pattern: cidr_blocks = ["0.0.0.0/0"] on port 22
  Risk:    Exposes EC2 instance to automated SSH brute-force scanning.
  Fix:     Restrict to your public IP:
             cidr_blocks = ["YOUR_PUBLIC_IP/32"]
           Or use AWS Systems Manager Session Manager (no SSH needed).

[M-002] No EBS encryption configured
  File:    terraform/main.tf (root_block_device block)
  Pattern: encrypted attribute missing or not set to true
  Risk:    Root volume data unencrypted at rest. If a snapshot is shared
           accidentally, data would be readable.
  Fix:     Add to root_block_device:
             encrypted = true

--------------------------------------------
LOW FINDINGS
--------------------------------------------

[L-001] Application runs over HTTP only
  Finding: No SSL/TLS configured in application.properties or Terraform.
  Risk:    CI job logs, build outputs, and session tokens transmitted in
           plaintext between browser and EC2.
  Fix:     Use an AWS Application Load Balancer with ACM certificate, or
           configure Let's Encrypt on the EC2 instance.

--------------------------------------------
INFO
--------------------------------------------

[I-001] terraform.tfstate is gitignored — PASS
[I-002] .pem files are gitignored — PASS
[I-003] .env files are gitignored — PASS
[I-004] No AWS keys found in source files — PASS
[I-005] No private keys found in project tree — PASS

--------------------------------------------
RECOMMENDED .gitignore ADDITIONS
--------------------------------------------

Add these entries to .gitignore:

  .claude/logs/plans/
  .claude/logs/audits/
  .claude/logs/artifacts/
  .claude/logs/sessions/
  .claude/env/*.env

--------------------------------------------
FIXES SUMMARY
--------------------------------------------

Priority 1 (before production):
  1. Restrict actuator: management.endpoints.web.exposure.include=health,info
  2. Restrict SSH CIDR in terraform/main.tf to your IP

Priority 2 (next sprint):
  1. Enable EBS encryption: add encrypted=true in root_block_device
  2. Configure TLS via ALB + ACM

Priority 3 (long term):
  1. Add Spring Security for UI authentication
  2. Migrate from Docker Hub credentials to AWS ECR + IAM instance profile
  3. Enable Terraform S3 backend with encryption and DynamoDB locking

--------------------------------------------
END OF REPORT
Saved to: .claude/logs/audits/2026-03-12-00-00-audit.md
--------------------------------------------
```

---

## Saving the Audit Report

Claude will suggest saving the report to `.claude/logs/audits/`:

```
File: .claude/logs/audits/2026-03-12-00-00-audit.md
```

This file is gitignored. Do not commit audit reports — they may reveal
vulnerability details that should stay private.

---

## Running This Command Regularly

It is recommended to run `/security-audit` at these points:
1. Before every `git push` to main/master
2. After any change to `terraform/*.tf` files
3. After adding new dependencies to `pom.xml`
4. After any change to `src/main/resources/*.properties`
5. Before any production deployment

Install the pre-commit hook to catch the most critical issues automatically:

```bash
cp .claude/hooks/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```
