# .claude/mcp — MCP Server Configuration

MCP (Model Context Protocol) servers extend Claude with access to external
documentation, tooling, and APIs. In this project, all MCP servers are
**read-only and advisory only**. Claude uses them to look up documentation
and validate generated code — never to create, modify, or delete resources.

---

## Configured MCP Servers

### 1. terraform

**Purpose**: Terraform provider schema lookup, resource argument validation,
module version queries, and HCL syntax reference.

**Transport**: `stdio`

**Command**:
```bash
docker run -i --rm ghcr.io/hashicorp/terraform-mcp-server:latest
```

**Prerequisites**:
- Docker Desktop or Docker Engine must be running.
- The `ghcr.io/hashicorp/terraform-mcp-server` image is pulled automatically.

**What Claude uses it for**:
- Looking up the current argument schema for `aws_instance`, `aws_security_group`,
  `aws_vpc`, etc. before generating Terraform code.
- Checking required/optional attributes and their types.
- Verifying provider version constraints (`~> 5.0`).
- Looking up Terraform functions (e.g. `base64encode`, `jsonencode`).

**What it does NOT do**:
- It does not connect to your AWS account.
- It does not read your Terraform state.
- It does not run `terraform plan` or `terraform apply`.

---

### 2. aws-docs

**Purpose**: Official AWS documentation search — EC2 user guides, IAM reference,
service quotas, SDK examples.

**Transport**: `stdio`

**Command**:
```bash
uvx awslabs.aws-documentation-mcp-server@latest
```

**Prerequisites**:
- `uv` or `uvx` installed: `pip install uv` or `brew install uv`
- Internet access (fetches docs from `docs.aws.amazon.com`)

**What Claude uses it for**:
- Verifying EC2 instance type specifications and pricing tiers.
- Looking up the correct AMI filter patterns for Amazon Linux 2023.
- Checking IAM policy syntax and available actions.
- Finding ECR authentication steps.
- Verifying actuator health endpoint behavior for ELB health checks.

**What it does NOT do**:
- It does not connect to your AWS account.
- It does not make any AWS API calls.
- It only searches and returns documentation text.

---

### 3. aws-core

**Purpose**: AWS service guidance, best practices, region/AZ availability checks,
and service capability metadata.

**Transport**: `stdio`

**Command**:
```bash
uvx awslabs.core-mcp-server@latest
```

**Prerequisites**:
- `uv` or `uvx` installed.
- Internet access.

**What Claude uses it for**:
- Checking whether a specific EC2 instance type is available in a given region.
- Verifying the latest recommended AMI IDs for Amazon Linux.
- Getting AWS Well-Architected Framework guidance for the project.
- Checking service limits relevant to the deployment (e.g. EC2 running instance limit).

**What it does NOT do**:
- It does not connect to your AWS account or use your credentials.
- It does not create or read any AWS resources.
- It is purely an information/guidance tool.

---

## How to Configure MCP Servers

Add the following to your Claude project's `.mcp.json` (or equivalent config file):

```json
{
  "mcpServers": {
    "terraform": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "ghcr.io/hashicorp/terraform-mcp-server:latest"
      ]
    },
    "aws-docs": {
      "type": "stdio",
      "command": "uvx",
      "args": ["awslabs.aws-documentation-mcp-server@latest"],
      "env": {
        "FASTMCP_LOG_LEVEL": "ERROR"
      }
    },
    "aws-core": {
      "type": "stdio",
      "command": "uvx",
      "args": ["awslabs.core-mcp-server@latest"],
      "env": {
        "FASTMCP_LOG_LEVEL": "ERROR"
      }
    }
  }
}
```

---

## Verifying MCP Servers Are Connected

```bash
# List configured and connected MCP servers
claude mcp list

# Expected output (when all servers are running):
# terraform    stdio  docker run ...  connected
# aws-docs     stdio  uvx awslabs...  connected
# aws-core     stdio  uvx awslabs...  connected
```

If a server shows `disconnected`, check:
1. The prerequisite tool is installed (Docker, uvx).
2. The Docker daemon is running (for the terraform server).
3. Internet access is available (for aws-docs and aws-core).

---

## MCP Usage Policy

All MCP servers in this project follow these rules:

1. **Read-only**: MCP servers only retrieve documentation and metadata. They never
   create, update, or delete any resources.

2. **Advisory only**: Information from MCP servers is used to improve the quality
   of generated Terraform files and scripts. Developers always review generated
   content before applying it.

3. **No credentials passed**: No AWS credentials, Docker Hub tokens, or SSH keys
   are ever passed to MCP server processes.

4. **No state access**: The terraform MCP server does not have access to
   `terraform.tfstate` or `.terraform/` directories.

---

## Disabling MCP Servers

If MCP servers are unavailable (no Docker, no internet), Claude will still function
normally. It will generate Terraform and scripts based on its training knowledge
without live documentation lookup. In this mode:

- Warn that provider schemas are based on training data, not live lookup.
- Verify generated Terraform syntax with `terraform validate` before applying.
- Double-check AMI filters against the AWS Console before running `terraform apply`.
