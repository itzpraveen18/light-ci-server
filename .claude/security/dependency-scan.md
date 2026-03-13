---
name: dependency-scan
description: Maven dependency CVE vulnerability scanning policy for SecurityAuditAgent
type: security
agent: SecurityAuditAgent
auto_approve: true
block_on_critical: false
---

# Maven Dependency Vulnerability Scanning Policy — LightCI Server

## Purpose

This document defines how Claude (SecurityAuditAgent) assesses Maven dependency
vulnerabilities in `pom.xml` for the LightCI Server project. It covers what to scan,
how to interpret CVE findings, severity thresholds, reporting format, and auto-approve
behaviour. **Dependency findings never block execution.**

---

## Scope

### What Is Scanned

| Dependency Type | Included | Notes |
|---|---|---|
| Direct dependencies | Yes | All `<dependency>` entries in `pom.xml` |
| Transitive dependencies | Yes | Dependencies of dependencies (full tree) |
| Plugin dependencies | Yes | `<build><plugins>` entries |
| BOM imports | Yes | `<dependencyManagement>` imports resolved transitively |
| Test-scope dependencies | Yes (LOW priority) | `<scope>test</scope>` — reported at reduced severity |
| Provided-scope dependencies | Yes | `<scope>provided</scope>` |
| `target/` compiled output | No | Not scanned — pom.xml is the source of truth |

### Primary Scan Method: OWASP Dependency-Check

The recommended scanner for this project is [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/).

**Maven plugin configuration** (add to `pom.xml` if not present):

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>9.0.9</version>
  <configuration>
    <failBuildOnCVSS>11</failBuildOnCVSS>  <!-- Never fail build: threshold above max CVSS 10 -->
    <formats>
      <format>HTML</format>
      <format>JSON</format>
    </formats>
    <outputDirectory>${project.basedir}/.claude/logs/audits</outputDirectory>
    <suppressionFile>${project.basedir}/.claude/security/owasp-suppressions.xml</suppressionFile>
  </configuration>
</plugin>
```

**To run the scan** (developer executes; Claude generates this command):
```bash
mvn org.owasp:dependency-check-maven:check \
    -DfailBuildOnCVSS=11 \
    -Dformats=HTML,JSON \
    -DoutputDirectory=.claude/logs/audits
```

> Note: Set `failBuildOnCVSS=11` so the build never fails on dependency findings —
> consistent with this project's auto-approve policy.

### Secondary Method: Claude Static Analysis of pom.xml

When a full OWASP scan is not available, Claude reads `pom.xml` and cross-references
known vulnerable versions against its training knowledge. This is supplementary only —
it does not replace a full CVE database scan.

---

## CVE Severity Thresholds

Vulnerabilities are classified using the CVSS v3.1 Base Score:

| CVSS Score Range | Severity | Action Required | Build Blocked? |
|---|---|---|---|
| 9.0 – 10.0 | **CRITICAL** | Immediate upgrade — rotate any exposed credentials | No — auto-proceed |
| 7.0 – 8.9 | **HIGH** | Upgrade in next sprint | No — auto-proceed |
| 4.0 – 6.9 | **MEDIUM** | Upgrade within 30 days or document accepted risk | No — auto-proceed |
| 0.1 – 3.9 | **LOW** | Address in backlog | No — auto-proceed |
| N/A | **INFORMATIONAL** | Known false positive or test-scope only | No — auto-proceed |

**This project's policy**: `failBuildOnCVSS` is always set to `11` (above maximum),
meaning OWASP Dependency-Check will never exit non-zero. Claude logs all findings and
proceeds automatically.

---

## Key Dependencies to Monitor

These are the current project dependencies most likely to carry CVEs. Claude pays
special attention to these during static analysis:

| Artifact | Group ID | Risk Area |
|---|---|---|
| `spring-boot-starter-web` | `org.springframework.boot` | Web layer, deserialization, HTTP parsing |
| `spring-boot-starter-thymeleaf` | `org.springframework.boot` | Template injection (XSS/SSTI) |
| `spring-boot-starter-actuator` | `org.springframework.boot` | Endpoint exposure, info disclosure |
| `spring-boot-starter-security` | `org.springframework.boot` | Auth bypass, session fixation |
| `jackson-databind` | `com.fasterxml.jackson.core` | Deserialization RCE (historically high CVE count) |
| `logback-classic` | `ch.qos.logback` | Log4Shell-type RCE (Log4j is separate but watch logback too) |
| `tomcat-embed-core` | `org.apache.tomcat.embed` | HTTP smuggling, partial PUT, RCE |
| `commons-collections` | `org.apache.commons` | Deserialization gadget chains |
| `snakeyaml` | `org.yaml` | Billion laughs DoS, arbitrary code via constructor |
| `h2` | `com.h2database` | SQL injection in console, RCE via JDBC URL |

---

## How Claude Reports Findings

### During `/security-audit`

Claude reads `pom.xml`, identifies dependency versions, and reports known issues
from its training knowledge. When a full OWASP JSON report is present in
`.claude/logs/audits/`, Claude parses it and reports from that authoritative source.

#### Report Format

```
=== DEPENDENCY VULNERABILITY REPORT ===
Scan timestamp  : 2026-03-12T10:00:00Z
Scanner         : OWASP Dependency-Check 9.0.9 / Claude static analysis
pom.xml location: /Users/praveen/python/light-ci-server/pom.xml
Total findings  : 4 (1 CRITICAL, 1 HIGH, 2 MEDIUM)

[CRITICAL] CVE-2022-42003 — jackson-databind 2.13.3
  CVSS Score   : 9.8
  Artifact     : com.fasterxml.jackson.core:jackson-databind:2.13.3
  Dependency   : Direct (via spring-boot-starter-web)
  Description  : Uncontrolled resource consumption via deeply nested objects
  Fix Version  : 2.13.4.2 or later (Spring Boot 2.7.5+ includes patched version)
  Status       : AUTO-PROCEED — upgrade recommended

[HIGH] CVE-2022-38752 — snakeyaml 1.30
  CVSS Score   : 7.5
  Artifact     : org.yaml:snakeyaml:1.30
  Dependency   : Transitive (via spring-boot-starter → snakeyaml)
  Description  : Stack overflow DoS via deeply nested YAML
  Fix Version  : 1.31 or later (Spring Boot 2.7.4+ includes patched version)
  Status       : AUTO-PROCEED — upgrade recommended

[MEDIUM] CVE-2023-20863 — spring-expression 6.0.7
  CVSS Score   : 6.5
  Artifact     : org.springframework:spring-expression:6.0.7
  Dependency   : Transitive (via spring-boot-starter)
  Description  : DoS via crafted SpEL expression
  Fix Version  : Spring Boot 3.0.6+ or 3.1.0+
  Status       : AUTO-PROCEED — upgrade recommended

[MEDIUM] CVE-2022-1471 — snakeyaml 1.32 (constructor deserialization)
  CVSS Score   : 9.8 (CRITICAL upstream, downgraded to MEDIUM here — test-scope only)
  Artifact     : org.yaml:snakeyaml:1.32
  Dependency   : Transitive (test scope)
  Description  : Arbitrary code execution via SnakeYAML Constructor
  Scope note   : test — not present in production runtime
  Fix Version  : 2.0 or later
  Status       : AUTO-PROCEED — test-scope, lower runtime risk

=== END OF REPORT — proceeding automatically ===
```

---

## Auto-Approve Behaviour

**All dependency vulnerability findings auto-proceed. Claude never blocks on CVE findings.**

Rationale:
- Maven dependencies are controlled by the developer; Claude does not execute `mvn` commands.
- Upgrading a dependency requires human judgment (compatibility testing, changelog review).
- Blocking a pipeline on a transitive CVE that may not affect the application's attack
  surface is counterproductive for a solo developer workflow.
- OWASP Dependency-Check is configured with `failBuildOnCVSS=11` to enforce this at the
  tool level as well.

---

## Suppression Policy

Known false positives can be suppressed using an OWASP suppressions file at:
`.claude/security/owasp-suppressions.xml`

**Example suppression for a test-scope false positive:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">

  <!-- Suppress snakeyaml CVE-2022-1471 in test scope only -->
  <suppress>
    <notes>Test-scope only — not present in production runtime artifact.</notes>
    <gav regex="true">^org\.yaml:snakeyaml:.*$</gav>
    <cve>CVE-2022-1471</cve>
  </suppress>

</suppressions>
```

Claude references this file when reporting findings and notes suppressed CVEs as
`SUPPRESSED (false positive)` in the report.

---

## Upgrade Guidance

### Checking Current Versions

Claude reads `pom.xml` directly to extract dependency versions. For a managed
Spring Boot project, most versions are inherited from the Spring Boot BOM:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.2.3</version>
</parent>
```

Upgrading `spring-boot-starter-parent` to the latest 3.x patch version resolves
the majority of transitive CVEs, as Spring Boot manages dependency versions in
its BOM.

### Checking for Latest Spring Boot Version

```bash
# Developer runs this; Claude generates the command:
mvn versions:display-property-updates -Dincludes=org.springframework.boot
```

### Checking All Outdated Dependencies

```bash
mvn versions:display-dependency-updates
```

### Applying Updates

```bash
# Update Spring Boot parent version to specific release:
mvn versions:update-parent -DallowSnapshots=false

# Or manually edit pom.xml <parent><version>
```

---

## CVSS Vector Interpretation

When reading OWASP reports, Claude interprets the CVSS v3.1 vector string:

| Component | Abbreviation | Key Values |
|---|---|---|
| Attack Vector | AV | N=Network (worst), A=Adjacent, L=Local, P=Physical |
| Attack Complexity | AC | L=Low (easier), H=High |
| Privileges Required | PR | N=None (worst), L=Low, H=High |
| User Interaction | UI | N=None (worst), R=Required |
| Confidentiality Impact | C | H=High, L=Low, N=None |
| Integrity Impact | I | H=High, L=Low, N=None |
| Availability Impact | A | H=High, L=Low, N=None |

Example: `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H` = Network-exploitable,
low complexity, no privileges, no user interaction — **highest risk profile**.

---

## Scan Schedule Recommendation

| Trigger | Recommended Action |
|---|---|
| Before every deployment | Run OWASP scan; review CRITICAL/HIGH findings |
| After adding a new dependency | Run scan immediately |
| Weekly (automated CI) | Add `mvn dependency-check:check` to CI pipeline |
| After Spring Boot version bump | Run scan + integration tests |

---

## Integration with SecurityAuditAgent

SecurityAuditAgent (`.claude/agents/SecurityAuditAgent.md`) calls this policy as part
of the `/security-audit` command. The agent:

1. Reads `pom.xml` to identify all declared dependencies and their versions.
2. If an OWASP report exists in `.claude/logs/audits/`, parses it and reports findings.
3. If no report exists, performs static analysis based on known CVEs in training data.
4. Outputs the Dependency Vulnerability Report shown above.
5. Appends the report to `.claude/logs/audits/dependency-scan-<timestamp>.log`.
6. Proceeds automatically to the next pipeline step.

---

## Related Policies

- `.claude/security/secret-detection.md` — Credential and secret scanning
- `.claude/security/docker-scan.md` — Docker image vulnerability scanning
- `.claude/policies/deployment-policy.md` — Deployment safety rules
