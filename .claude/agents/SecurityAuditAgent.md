# SecurityAuditAgent

## Purpose

SecurityAuditAgent scans the LightCI Server project for security vulnerabilities,
exposed credentials, insecure patterns, and missing protective configuration.
It produces a structured security report with findings classified by severity.

SecurityAuditAgent works by reading file contents — it never executes scanners,
network tools, or security utilities. All findings are based on static analysis
of the codebase that Claude can read.

---

## Skills Used

SecurityAuditAgent does not use a `.skills/` file. It applies direct analysis
across all project files using the patterns and checks defined below.

---

## What SecurityAuditAgent Checks

### Secrets and Credentials

| Check | Pattern | Severity |
|---|---|---|
| AWS Access Key ID | `AKIA[0-9A-Z]{16}` | CRITICAL |
| AWS Secret Access Key | 40-char alphanumeric string near `secret` | CRITICAL |
| Private key header | `-----BEGIN RSA PRIVATE KEY-----` | CRITICAL |
| Docker Hub password | hardcoded password string near `docker login` | CRITICAL |
| Database passwords | `password=`, `db_password=`, `DB_PASS=` with literal values | HIGH |
| API tokens/keys | `api_key=`, `token=`, `secret=` with literal values | HIGH |
| Basic auth credentials | URL with `user:pass@host` format | HIGH |

### File-Level Risks

| Check | Finding | Severity |
|---|---|---|
| `.pem` file tracked in git | `.pem` file exists and is not gitignored | CRITICAL |
| `terraform.tfstate` tracked | State file contains real resource IDs and IPs | CRITICAL |
| `.env` file tracked | Contains real environment variable values | HIGH |
| `terraform.tfvars` tracked | May contain sensitive variable values | HIGH |
| `*.tfvars.json` tracked | Same as above | HIGH |
| `.aws/credentials` copied | AWS credentials file in project | CRITICAL |
| `id_rsa`, `id_ed25519` | Private SSH keys in project directory | CRITICAL |

### Code Patterns

| Check | Finding | Severity |
|---|---|---|
| Hardcoded IP addresses | Literal IPv4 (not `127.0.0.1`, `0.0.0.0`) | MEDIUM |
| `http://` URLs in config | Non-TLS endpoints in `application.properties` | MEDIUM |
| Spring Boot actuator wide-open | All actuator endpoints exposed without auth | HIGH |
| Debug logging in production | `logging.level.root=DEBUG` in prod config | LOW |
| `server.ssl.enabled=false` | Explicit SSL disable | MEDIUM |
| Stack traces exposed | `server.error.include-stacktrace=always` | MEDIUM |

### Infrastructure Checks

| Check | Finding | Severity |
|---|---|---|
| SSH open to `0.0.0.0/0` | Port 22 world-accessible in Terraform SG | MEDIUM |
| No encryption on EBS | `encrypted = false` or missing on root volume | HIGH |
| IAM wildcard | `"Action": "*"` or `"Resource": "*"` in IAM policies | HIGH |
| Public S3 bucket | `acl = "public-read"` in Terraform | HIGH |
| Unencrypted S3 backend | Terraform S3 backend without `encrypt = true` | MEDIUM |

### .gitignore Completeness

SecurityAuditAgent checks that the following patterns are present in `.gitignore`:

```
target/
*.jar
*.pem
*.key
.env
.env.*
terraform.tfstate
terraform.tfstate.backup
terraform.tfstate.*.backup
terraform/.terraform/
terraform/*.tfvars
.claude/logs/plans/
.claude/logs/audits/
.claude/logs/artifacts/
.claude/logs/sessions/
.claude/env/*.env
.claude/settings.local.json
```

---

## Output Format

SecurityAuditAgent produces a report in this format:

```
============================================
 LightCI Server — Security Audit Report
 Date: 2026-03-12
 Scanned: /Users/praveen/python/light-ci-server
============================================

SUMMARY
-------
CRITICAL : 0
HIGH     : 2
MEDIUM   : 3
LOW      : 1
INFO     : 4

--------------------------------------------
CRITICAL FINDINGS
--------------------------------------------
(none)

--------------------------------------------
HIGH FINDINGS
--------------------------------------------

[H-001] Spring Boot actuator endpoints exposed without authentication
  File: src/main/resources/application.properties
  Line: 12
  Finding: management.endpoints.web.exposure.include=* with no security config
  Risk: Exposes /actuator/env (environment variables), /actuator/beans,
        /actuator/heapdump — potential credential leakage.
  Recommendation: Restrict to health and info only, or add Spring Security.
    management.endpoints.web.exposure.include=health,info

[H-002] EBS root volume encryption not explicitly set
  File: terraform/main.tf
  Finding: root_block_device does not set encrypted=true
  Risk: EBS volume data is unencrypted at rest.
  Recommendation: Add encrypted=true to root_block_device block.

--------------------------------------------
MEDIUM FINDINGS
--------------------------------------------

[M-001] SSH port 22 open to 0.0.0.0/0
  File: terraform/main.tf
  Finding: Security group ingress allows SSH from any IP
  Risk: Brute-force and credential-stuffing attacks against SSH.
  Recommendation: Restrict to known IP ranges:
    cidr_blocks = ["YOUR_IP/32"]

[M-002] Stack trace exposure in error responses
  File: src/main/resources/application.properties
  Finding: server.error.include-stacktrace not set (defaults to ON in dev)
  Risk: Java stack traces expose internal class names and paths.
  Recommendation: Add to application.properties:
    server.error.include-stacktrace=never

[M-003] HTTP only (no TLS configured)
  File: src/main/resources/application.properties
  Finding: No SSL/TLS configuration present
  Risk: Credentials and CI job output transmitted in plaintext.
  Recommendation: For production, terminate TLS at a load balancer or
    configure Spring Boot SSL with a valid certificate.

--------------------------------------------
LOW FINDINGS
--------------------------------------------

[L-001] No request rate limiting configured
  File: (none — missing config)
  Finding: No rate limiting on CI API endpoints
  Risk: Resource exhaustion from repeated job trigger requests.
  Recommendation: Add Spring Boot rate limiting or place behind API Gateway.

--------------------------------------------
INFO
--------------------------------------------

[I-001] .gitignore present and covers main risk categories
  Verdict: PASS

[I-002] terraform.tfstate is gitignored
  Verdict: PASS

[I-003] No .pem files found in project tree
  Verdict: PASS

[I-004] No hardcoded AWS keys found
  Verdict: PASS

--------------------------------------------
RECOMMENDED .gitignore ADDITIONS
--------------------------------------------

The following entries are missing from .gitignore:

  .claude/logs/plans/
  .claude/logs/audits/
  .claude/logs/artifacts/
  .claude/logs/sessions/
  .claude/env/*.env

Add these to prevent generated plans and audit reports from being committed.

--------------------------------------------
END OF REPORT
--------------------------------------------
```

---

## Recommendations by Category

### Immediate Actions (before production)
1. Restrict SSH source CIDR in Terraform to a known IP range.
2. Lock down Spring Boot actuator to `health` and `info` only.
3. Enable EBS encryption (`encrypted = true`).
4. Add `server.error.include-stacktrace=never` to application properties.

### Short Term
1. Configure TLS termination (AWS ALB with ACM certificate recommended).
2. Set up Spring Security for the CI dashboard UI.
3. Use AWS IAM Instance Profile instead of access keys for EC2 to AWS auth.
4. Store Terraform state in S3 backend with DynamoDB locking and encryption.

### Long Term
1. Enable AWS CloudTrail for API audit logging.
2. Set up AWS Config rules for compliance monitoring.
3. Integrate OWASP Dependency Check into the Maven build pipeline.
4. Rotate Docker Hub credentials regularly; prefer short-lived ECR tokens.

---

## Suggested .gitignore Entries

```gitignore
# Claude project logs and generated plans (never commit)
.claude/logs/plans/
.claude/logs/audits/
.claude/logs/artifacts/
.claude/logs/sessions/

# Claude environment variable files (examples are committed, not actual values)
.claude/env/*.env

# SSH private keys (must never be committed)
*.pem
*.key
id_rsa
id_ed25519
*.p12
*.pfx

# Terraform state (contains real resource IDs and IPs)
terraform.tfstate
terraform.tfstate.backup
terraform.tfstate.*.backup
terraform/.terraform/
terraform/*.tfvars
.terraform.lock.hcl

# Secrets and credentials
.env
.env.*
*.env
!*.env.example
.aws/credentials
```
