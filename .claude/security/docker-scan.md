---
name: docker-scan
description: Docker image layer and base image vulnerability scanning policy for SecurityAuditAgent
type: security
agent: SecurityAuditAgent
auto_approve: true
block_on_critical: false
---

# Docker Image Vulnerability Scanning Policy — LightCI Server

## Purpose

This document defines how Claude (SecurityAuditAgent and DockerAgent) evaluates Docker
images for the LightCI Server project. It covers base image selection, vulnerability
scanning with Trivy and Docker Scout, runtime security checks (root user, exposed
secrets, port exposure), and reporting. **All Docker scan findings auto-proceed —
Claude never blocks on Docker vulnerabilities.**

---

## Scope

Claude evaluates the following during a Docker security scan:

| Target | What Is Checked |
|---|---|
| `Dockerfile` in repo root | Base image, USER directive, COPY of sensitive files, exposed ports, ARG/ENV secrets, layer count |
| `.claude/templates/Dockerfile.template` | Same checks as above |
| Built image (if report provided) | CVE findings from Trivy or Docker Scout JSON output |
| Docker Compose files (if present) | Port bindings, volume mounts, privileged mode, environment variable injection |

---

## Base Image Selection Policy

### Approved Base Images

| Image | Tag Pattern | Use Case | Notes |
|---|---|---|---|
| `eclipse-temurin` | `17-jre-alpine` | JRE runtime (preferred) | Alpine-based, minimal attack surface |
| `eclipse-temurin` | `17-jdk-alpine` | Build stage only | Use only in multi-stage build stage 1 |
| `gcr.io/distroless/java17-debian12` | `latest` or digest | Production runtime (hardened) | No shell, no package manager |
| `amazoncorretto` | `17-alpine3.19` | Alternative JRE | AWS-optimised, Alpine-based |

### Prohibited Base Images

| Image | Reason |
|---|---|
| `ubuntu`, `debian`, `centos` (full) | Large attack surface, hundreds of CVEs in base layer |
| `openjdk` (official) | Deprecated on Docker Hub; use eclipse-temurin instead |
| `java` (official) | Deprecated; removed from Docker Hub |
| Any image without a pinned version tag | `latest` tag is mutable — image changes silently |
| Images from unverified registries | Use only Docker Hub Official/Verified Publisher images |

### Recommended Multi-Stage Dockerfile Pattern

```dockerfile
# Stage 1 — Build (uses JDK)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests --no-transfer-progress

# Stage 2 — Runtime (uses JRE only)
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the fat JAR — no source code, no Maven cache
COPY --from=build /build/target/light-ci-server.jar app.jar

# Drop to non-root user
USER appuser

# Expose only the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

## Vulnerability Scanning

### Trivy (Recommended)

[Trivy](https://github.com/aquasecurity/trivy) is the primary scanner for this project.
It scans OS packages and application dependencies within the image layer.

**Developer runs; Claude generates this command:**

```bash
# Scan the locally built image
trivy image \
  --severity CRITICAL,HIGH,MEDIUM,LOW \
  --format json \
  --output .claude/logs/audits/trivy-$(date +%Y%m%dT%H%M%S).json \
  your-dockerhub-username/light-ci-server:latest

# Human-readable summary
trivy image \
  --severity CRITICAL,HIGH \
  --format table \
  your-dockerhub-username/light-ci-server:latest
```

**Exit code policy**: Always use `--exit-code 0` (or omit it) — do not fail the build
on Trivy findings. Consistent with this project's auto-approve policy.

```bash
trivy image --exit-code 0 your-dockerhub-username/light-ci-server:latest
```

### Docker Scout (Alternative)

[Docker Scout](https://docs.docker.com/scout/) is integrated into Docker Desktop and
the Docker CLI.

```bash
# Quickview
docker scout quickview your-dockerhub-username/light-ci-server:latest

# Full CVE report
docker scout cves \
  --format json \
  --output .claude/logs/audits/scout-$(date +%Y%m%dT%H%M%S).json \
  your-dockerhub-username/light-ci-server:latest

# Compare to previous tag for regressions
docker scout compare \
  --to your-dockerhub-username/light-ci-server:previous \
  your-dockerhub-username/light-ci-server:latest
```

### Grype (Secondary Alternative)

```bash
grype your-dockerhub-username/light-ci-server:latest \
  --output json \
  --file .claude/logs/audits/grype-$(date +%Y%m%dT%H%M%S).json
```

---

## Dockerfile Static Analysis Checks

Claude performs these checks by reading the `Dockerfile` directly — no execution required:

### Check 1 — Root User

**Finding**: Container runs as root (no `USER` directive, or `USER root`).

| Field | Value |
|---|---|
| Severity | HIGH |
| Pattern | Absence of `USER` directive after the final `FROM`, or explicit `USER root` |
| Impact | If the application is compromised, attacker has root inside the container |
| Fix | Add `RUN adduser -S appuser && addgroup -S appgroup` and `USER appuser` before `ENTRYPOINT` |

### Check 2 — Secrets in ENV or ARG

**Finding**: Sensitive values hardcoded in `ENV` or `ARG` directives.

| Field | Value |
|---|---|
| Severity | CRITICAL |
| Pattern | `ENV.*PASSWORD`, `ENV.*SECRET`, `ENV.*KEY`, `ENV.*TOKEN`, `ARG.*PASSWORD` etc. with non-empty default |
| Impact | Secret is baked into image layer; visible via `docker history` and `docker inspect` |
| Fix | Use Docker secrets (`--secret`), runtime environment injection, or AWS Secrets Manager at startup |

Example of a **non-compliant** Dockerfile line:
```dockerfile
ENV DB_PASSWORD=mypassword123  # CRITICAL — baked into image layer
```

Compliant pattern (pass at runtime):
```bash
docker run -e DB_PASSWORD="$(aws secretsmanager get-secret-value ...)" ...
```

### Check 3 — COPY of Sensitive Files

**Finding**: `COPY` or `ADD` instruction copies credential files into the image.

| Field | Value |
|---|---|
| Severity | CRITICAL |
| Pattern | `COPY *.pem`, `COPY .env`, `COPY credentials`, `ADD id_rsa`, etc. |
| Impact | Sensitive file is permanently in image layer even if deleted in a subsequent `RUN rm` |
| Fix | Use `.dockerignore` to exclude sensitive files; never COPY them |

Required `.dockerignore` entries for this project:
```
.env
*.pem
*.key
id_rsa
id_ed25519
.aws/
terraform/*.tfstate
terraform/*.tfvars
.claude/config.yml
target/
```

### Check 4 — Exposed Unnecessary Ports

**Finding**: `EXPOSE` directive exposes ports beyond what the application uses.

| Field | Value |
|---|---|
| Severity | MEDIUM |
| Expected | `EXPOSE 8080` only |
| Prohibited | `EXPOSE 22` (SSH in container), `EXPOSE 8443` (unless TLS is configured), `EXPOSE 5005` (Java debug port) |
| Fix | Remove unnecessary `EXPOSE` directives; never expose debug ports in production images |

### Check 5 — Outdated Base Image

**Finding**: Base image uses a tag that maps to an outdated digest.

| Field | Value |
|---|---|
| Severity | HIGH |
| Detection | Claude compares the declared tag against known LTS releases in training data |
| Example | `FROM eclipse-temurin:11-jre-alpine` — Java 11 is past end-of-life for some support tiers |
| Fix | Upgrade to `eclipse-temurin:17-jre-alpine` (LTS) or `eclipse-temurin:21-jre-alpine` (next LTS) |

### Check 6 — Mutable `latest` Tag

**Finding**: Base image pinned to `latest` tag.

| Field | Value |
|---|---|
| Severity | MEDIUM |
| Pattern | `FROM eclipse-temurin:latest`, `FROM amazoncorretto:latest` |
| Impact | Unpredictable builds; a silent base image update could introduce vulnerabilities |
| Fix | Pin to a specific version tag: `FROM eclipse-temurin:17.0.10_7-jre-alpine` |

### Check 7 — `ADD` Instead of `COPY` for Local Files

**Finding**: `ADD` used for local file copy (instead of `COPY`).

| Field | Value |
|---|---|
| Severity | LOW |
| Pattern | `ADD <localfile> <dest>` where source is not a URL or tar archive |
| Impact | `ADD` with URL can download content from the internet at build time — supply chain risk |
| Fix | Use `COPY` for local files; reserve `ADD` only when tar auto-extraction is needed |

### Check 8 — Secrets Leaked via `RUN` Layer

**Finding**: Sensitive values passed as arguments to `RUN` commands.

| Field | Value |
|---|---|
| Severity | CRITICAL |
| Pattern | `RUN curl -H "Authorization: Bearer <token>"`, `RUN mvn ... -Dtoken=<value>` |
| Impact | The command string is visible in image history via `docker history --no-trunc` |
| Fix | Use BuildKit secrets: `RUN --mount=type=secret,id=my_secret cat /run/secrets/my_secret` |

---

## Scan Report Format

Claude outputs Docker scan findings in this format:

```
=== DOCKER IMAGE SCAN REPORT ===
Scan timestamp  : 2026-03-12T10:00:00Z
Scanner         : Trivy 0.49.1 / Claude Dockerfile static analysis
Image           : yourusername/light-ci-server:1.0.0
Base image      : eclipse-temurin:17-jre-alpine
Total CVEs      : 6 (0 CRITICAL, 2 HIGH, 3 MEDIUM, 1 LOW)
Dockerfile issues: 1 HIGH (root user), 0 CRITICAL

--- CVE Findings ---

[HIGH] CVE-2024-21626 — runc 1.1.11
  CVSS Score  : 8.6
  Component   : OS package — runc
  Layer       : Base image (eclipse-temurin:17-jre-alpine)
  Description : Container escape via /proc/self/fd working directory manipulation
  Fix Version : runc >= 1.1.12; update base image to latest alpine patch
  Status      : AUTO-PROCEED

[HIGH] CVE-2024-24786 — golang.org/x/net 0.18.0
  CVSS Score  : 7.5
  Component   : OS package (indirect via alpine tooling)
  Layer       : Base image
  Description : Infinite loop in protobuf unmarshaling
  Fix Version : golang.org/x/net >= 0.21.0
  Status      : AUTO-PROCEED

--- Dockerfile Static Analysis Findings ---

[HIGH] Docker-Check-1 — Container runs as root
  Location    : Dockerfile, no USER directive found after final FROM
  Impact      : Process runs as UID 0 inside the container
  Fix         : Add non-root user creation and USER directive (see template above)
  Status      : AUTO-PROCEED

=== END OF REPORT — proceeding automatically ===
```

---

## Auto-Approve Behaviour

**All Docker scan findings at all severity levels auto-proceed. Claude never blocks
or pauses based on Docker vulnerability findings.**

This applies to:
- CVEs in base image OS packages
- CVEs in application layer packages
- Dockerfile static analysis findings (including CRITICAL)
- Outdated base image warnings
- Root user warnings

Rationale: Docker image hardening requires deliberate engineering trade-offs (e.g.,
switching to distroless may break health check tooling). These changes require the
developer's review and testing. Blocking the pipeline on image scan findings in a
local dev workflow adds friction without adding safety when the developer is the
sole operator reviewing all outputs.

---

## Remediation Guidance

### Upgrading the Base Image

```bash
# Check latest alpine-based Eclipse Temurin tags:
# https://hub.docker.com/_/eclipse-temurin/tags?name=17-jre-alpine

# Update Dockerfile:
FROM eclipse-temurin:17.0.10_7-jre-alpine
```

After updating, rebuild and re-scan:
```bash
docker build -t light-ci-server:test .
trivy image light-ci-server:test
```

### Removing Root User Risk

Add to `Dockerfile` before the final `ENTRYPOINT`:

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

Verify at runtime:
```bash
docker run --rm --entrypoint whoami light-ci-server:latest
# Expected output: appuser
```

### Removing Secrets from Image Layers

Use Docker BuildKit secrets for build-time secrets:

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-alpine AS build
RUN --mount=type=secret,id=maven_settings,dst=/root/.m2/settings.xml \
    mvn clean package -DskipTests
```

Build with:
```bash
docker build \
  --secret id=maven_settings,src=~/.m2/settings.xml \
  -t light-ci-server:latest .
```

### Pinning to Image Digest (Most Secure)

For production, pin to the immutable image digest instead of a mutable tag:

```dockerfile
FROM eclipse-temurin@sha256:abc123...def456
```

Retrieve the digest:
```bash
docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:17-jre-alpine
```

---

## Integration with Agents

| Agent | Usage |
|---|---|
| `SecurityAuditAgent` | Reads Dockerfile for static analysis checks during `/security-audit` |
| `DockerAgent` | Generates Dockerfiles following the base image and USER directives in this policy |

Reports are appended to `.claude/logs/audits/docker-scan-<timestamp>.log`.

---

## Related Policies

- `.claude/security/secret-detection.md` — Credential and secret scanning
- `.claude/security/dependency-scan.md` — Maven CVE scanning
- `.claude/policies/deployment-policy.md` — Container runtime configuration
- `.claude/policies/infra-policy.md` — Infrastructure and Security Group rules
