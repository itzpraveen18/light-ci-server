---
name: secret-detection
description: Secret detection patterns (S-001 to S-018) and auto-proceed remediation policy
type: security
agent: SecurityAuditAgent
auto_approve: true
block_on_critical: false
---

# Secret Detection Policy — LightCI Server

## Purpose

This document defines how Claude (SecurityAuditAgent) scans source files, configuration,
scripts, Terraform, and Dockerfiles for accidentally committed secrets, credentials,
private keys, and sensitive configuration. Findings are always reported; **they never
block execution**.

---

## Scope

Claude scans the following file types and locations during every `/security-audit` run:

| Location | What Is Scanned |
|---|---|
| `src/` | All Java source files for hardcoded strings |
| `terraform/` | All `.tf` files for embedded credentials or IPs |
| `Dockerfile` | Build args, ENV statements, COPY of sensitive files |
| `.claude/env/` | Only `.example` files — real `.env` files should never exist here |
| `pom.xml` | Plugin configs, repository credentials |
| `scripts/` | Shell scripts for exported secrets |
| `.github/workflows/` | CI workflow YAML for exposed secrets |
| Root directory | `.env`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.tfstate` files |

Claude does **not** scan `target/`, `.git/`, or binary files.

---

## Pattern Table

The following patterns are checked by Claude during a static read of each file.
Regex patterns are written in a form Claude applies conceptually during analysis
(not via shell execution).

| ID | Finding Type | Regex Pattern (conceptual) | Severity | Affects |
|----|---|---|---|---|
| S-001 | AWS Access Key ID | `AKIA[0-9A-Z]{16}` | CRITICAL | Any file |
| S-002 | AWS Secret Access Key | `(?i)aws.{0,20}secret.{0,20}['\"][0-9a-zA-Z/+]{40}['\"]` | CRITICAL | Any file |
| S-003 | AWS Session Token | `(?i)aws.{0,20}session.token.{0,20}['\"][A-Za-z0-9/+=]{100,}` | CRITICAL | Any file |
| S-004 | RSA / EC Private Key | `-----BEGIN (RSA\|EC\|OPENSSH) PRIVATE KEY-----` | CRITICAL | Any file |
| S-005 | PEM Certificate with Key | `-----BEGIN CERTIFICATE-----` combined with private key material | HIGH | Any file |
| S-006 | Terraform state file present | Filename matches `*.tfstate` or `*.tfstate.backup` | CRITICAL | Repo root, terraform/ |
| S-007 | `.env` file with real values | File named `.env` (not `.env.example`) present and non-empty | HIGH | Repo root |
| S-008 | Hardcoded IP address (public) | `\b((?!10\.|172\.(1[6-9]\|2\d\|3[01])\.|192\.168\.)\d{1,3}\.){3}\d{1,3}\b` | MEDIUM | .tf, .sh, .java, .yml |
| S-009 | Hardcoded password in code | `(?i)(password\|passwd\|pwd)\s*[=:]\s*['\"][^'\"]{4,}['\"]` | HIGH | .java, .properties, .yml |
| S-010 | Docker Hub credentials in script | `(?i)docker login.{0,80}(-p\|--password)\s+\S+` | HIGH | .sh, Dockerfile, .yml |
| S-011 | Generic API key / token | `(?i)(api.?key\|api.?token\|access.?token)\s*[=:]\s*['\"][a-zA-Z0-9_\-]{16,}['\"]` | HIGH | Any file |
| S-012 | GitHub Personal Access Token | `ghp_[a-zA-Z0-9]{36}` | CRITICAL | Any file |
| S-013 | SSH private key file present | Filename matches `*.pem` or `id_rsa` or `id_ed25519` | CRITICAL | Repo root, scripts/ |
| S-014 | Java keystore / truststore | Filename matches `*.jks`, `*.p12`, `*.keystore` | HIGH | Any location |
| S-015 | Terraform variable with sensitive default | `variable.*default.*=.*"[^"]{8,}"` inside a `sensitive = true` block | MEDIUM | terraform/*.tf |
| S-016 | Spring datasource password | `spring.datasource.password\s*=\s*.+` with a non-placeholder value | HIGH | application.properties |
| S-017 | Slack / webhook URL | `https://hooks\.slack\.com/services/[A-Z0-9/]+` | MEDIUM | Any file |
| S-018 | Base64-encoded potential secret | Long base64 string (>40 chars) assigned to a variable named `*secret*`, `*key*`, `*token*`, `*pass*` | MEDIUM | Any file |

---

## Severity Definitions

| Severity | Definition | Example |
|---|---|---|
| **CRITICAL** | Direct credential exposure; exploitable immediately if repo is public or leaked. Must be rotated immediately. | AWS key, private key file, tfstate file, GitHub PAT |
| **HIGH** | Strong indicator of hardcoded secret; exploitable with minimal effort. Should be rotated and moved to secrets manager. | Hardcoded password, Docker Hub password in script, API token |
| **MEDIUM** | Possible information disclosure; context-dependent risk. Review and consider moving to config or vault. | Hardcoded public IP, Slack webhook URL, Terraform sensitive default |
| **LOW** | Informational. Low exploitability on its own but contributes to attack surface. | Internal hostname in comment, debug log with partial credential |

---

## Auto-Approve Behaviour

**ALL findings at ALL severity levels — including CRITICAL — auto-proceed.**

Claude logs every finding in the audit report with its ID, severity, file path, line
number (if available), and the matched snippet (truncated to avoid echoing real secrets).

Claude **never** blocks, pauses, or requires confirmation based on secret-detection
findings — at any severity level. This applies to CRITICAL findings as well. Claude
reports every finding, documents it in the audit log, and immediately proceeds to
the next step without waiting for developer confirmation.

The rationale is that this project's developer is the sole operator. Blocking on
findings in a local dev workflow creates friction without adding safety, since the
developer must review the audit log regardless.

---

## Audit Report Format

Claude outputs findings in this format during `/security-audit`:

```
=== SECRET DETECTION REPORT ===
Scan timestamp : 2026-03-12T10:00:00Z
Files scanned  : 47
Findings total : 3

[CRITICAL] S-001 — AWS Access Key ID
  File    : src/main/resources/application.properties
  Line    : 42
  Snippet : AKIA******************** (truncated)
  Status  : AUTO-PROCEED — rotate this key immediately

[HIGH] S-009 — Hardcoded password in code
  File    : src/main/java/com/example/lightci/config/DataConfig.java
  Line    : 18
  Snippet : password = "myP***" (truncated)
  Status  : AUTO-PROCEED — move to environment variable or AWS Secrets Manager

[MEDIUM] S-008 — Hardcoded public IP address
  File    : terraform/main.tf
  Line    : 31
  Snippet : 54.23.***.*** (truncated)
  Status  : AUTO-PROCEED — parameterise as Terraform variable

=== END OF REPORT — proceeding automatically ===
```

---

## Remediation Steps by Finding Type

### S-001 / S-002 / S-003 — AWS Credentials

1. **Immediately rotate** the key in AWS IAM Console → Security credentials → Access keys.
2. Revoke the old key.
3. Remove the hardcoded value from the file.
4. Store credentials using one of:
   - `~/.aws/credentials` (for local dev, never commit this file)
   - AWS IAM Instance Profile (for EC2 — preferred)
   - AWS Secrets Manager + retrieval at runtime
5. Add `**/.aws/credentials`, `.env`, `application-prod.properties` to `.gitignore`.
6. Audit git history: `git log -S "AKIA" --all` — if key appears in history, treat the
   entire history as compromised and rotate all credentials.

### S-004 / S-013 — Private Keys / PEM Files

1. Remove the file from the working tree immediately.
2. Add `*.pem`, `*.key`, `id_rsa`, `id_ed25519` to `.gitignore`.
3. If the file was ever committed: run `git filter-repo --path <file> --invert-paths`
   to purge from history, then force-push (after team coordination).
4. Generate a new key pair; the exposed private key must be considered compromised.
5. For EC2 SSH keys: delete the old key pair in AWS console, create a new one, and
   re-provision the instance or update the `authorized_keys` file.

### S-005 / S-014 — Certificates / Keystores

1. Remove binary keystore files from the repository.
2. Store keystores in a secrets manager (AWS Secrets Manager, HashiCorp Vault) or an
   encrypted S3 bucket with access restricted to the deployment IAM role.
3. Mount keystores at runtime via environment injection or EC2 instance profile.
4. Rotate the certificate if the keystore's password was also exposed.

### S-006 — Terraform State File

1. Remove `*.tfstate` and `*.tfstate.backup` from the repository.
2. Add `**/*.tfstate`, `**/*.tfstate.backup` to `.gitignore`.
3. Migrate state to a remote backend immediately:
   ```hcl
   terraform {
     backend "s3" {
       bucket         = "your-tfstate-bucket"
       key            = "light-ci-server/terraform.tfstate"
       region         = "us-east-1"
       encrypt        = true
       dynamodb_table = "terraform-state-lock"
     }
   }
   ```
4. Tfstate often contains sensitive outputs (passwords, IPs, keys). If exposed, rotate
   all values referenced in the state outputs.

### S-007 — `.env` File Committed

1. Remove `.env` from the repository.
2. Add `.env` to `.gitignore` immediately.
3. Purge from git history: `git filter-repo --path .env --invert-paths`.
4. Rotate every credential that was in the `.env` file.
5. Use `.env.example` (no real values) as the committed template.

### S-008 — Hardcoded Public IP

1. Replace the hardcoded IP with a Terraform output reference or an environment variable:
   ```hcl
   variable "ec2_public_ip" {
     description = "Public IP of the EC2 instance"
     type        = string
   }
   ```
2. In shell scripts: `EC2_PUBLIC_IP="${EC2_PUBLIC_IP:?EC2_PUBLIC_IP must be set}"`.
3. Document the IP in `.claude/config.yml` under `ec2.public_ip` (never commit real values).

### S-009 / S-016 — Hardcoded Password / Spring Datasource Password

1. Replace with an environment variable reference:
   ```properties
   spring.datasource.password=${DB_PASSWORD}
   ```
2. Inject `DB_PASSWORD` at runtime via EC2 user-data, AWS Secrets Manager, or
   Parameter Store.
3. Rotate the database password immediately.

### S-010 — Docker Hub Credentials in Script

1. Remove inline `-p <password>` from `docker login` commands.
2. Use `~/.docker/config.json` (populated by `docker login` interactively) or
   a CI secrets mechanism.
3. For automated pipelines: use Docker Hub access tokens (not your account password)
   stored as GitHub Secrets or AWS Secrets Manager entries.
   ```bash
   echo "$DOCKER_TOKEN" | docker login -u "$DOCKER_USERNAME" --password-stdin
   ```

### S-011 / S-012 — API Key / GitHub PAT

1. Revoke the token in the respective service (GitHub → Settings → Developer settings).
2. Generate a new token with minimum required scopes.
3. Store the new token in:
   - GitHub Secrets (for GitHub Actions)
   - AWS Secrets Manager (for runtime access)
   - Local `~/.env` or shell profile (never committed)
4. Reference via environment variable only:
   ```java
   String token = System.getenv("GITHUB_TOKEN");
   ```

### S-015 — Terraform Sensitive Variable with Default

1. Remove the `default` value from `sensitive = true` variables.
2. Supply values via `TF_VAR_*` environment variables or a `terraform.tfvars` file
   that is gitignored.
3. Example of compliant variable declaration:
   ```hcl
   variable "db_password" {
     description = "RDS master password"
     type        = string
     sensitive   = true
     # No default — must be supplied at plan/apply time
   }
   ```

### S-017 — Slack / Webhook URL

1. Treat webhook URLs as credentials — rotate them in Slack (App settings → Incoming Webhooks).
2. Store in environment variables or AWS Parameter Store:
   ```bash
   SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:?must be set}"
   ```

### S-018 — Base64-Encoded Potential Secret

1. Decode and inspect the value: `echo "<value>" | base64 -d`.
2. If it contains a credential, follow the remediation steps for the underlying secret type.
3. Base64 encoding provides no security — treat encoded secrets identically to plaintext ones.

---

## Integration with SecurityAuditAgent

The `SecurityAuditAgent` (defined in `.claude/agents/SecurityAuditAgent.md`) executes
this policy during every `/security-audit` invocation. The agent:

1. Reads all in-scope files listed in the Scope section above.
2. Applies each pattern from the Pattern Table conceptually during analysis.
3. Outputs the Audit Report Format shown above.
4. Appends the report to `.claude/logs/audits/secret-detection-<timestamp>.log`.
5. Proceeds to the next pipeline step automatically regardless of findings.

---

## Related Policies

- `.claude/security/dependency-scan.md` — Maven CVE scanning
- `.claude/security/docker-scan.md` — Docker image vulnerability scanning
- `.claude/policies/deployment-policy.md` — Deployment safety rules
- `.claude/policies/infra-policy.md` — Infrastructure constraints
