# Chronological Order of Arguments — Full Deploy Workflow

All arguments are read from `.claude/config.yml`.
This file shows exactly when each argument is consumed, in workflow order.

---

## Phase 1 — Pre-Build Validation  (`pre-build.sh`)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 1 | Project root | `project.root` | `/Users/praveen/python/light-ci-server` |
| 2 | Java version | `project.java_version` | `17` |
| 3 | Maven artifact | `project.artifact` | `target/light-ci-server.jar` |

---

## Phase 2 — Maven Build  (`/build` → JavaBuildAgent)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 4 | Maven goals | `maven.goals` | `clean package` |
| 5 | Skip tests | `maven.skip_tests` | `true` |
| 6 | Maven profiles | `maven.profiles` | `` (blank = default) |
| 7 | Extra Maven args | `maven.extra_args` | `-q` |
| 8 | Auto-approve build | `auto_approve.maven_build` | `true` |

---

## Phase 3 — Post-Build Validation  (`post-build.sh`)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 9 | Artifact path | `project.artifact` | `target/light-ci-server.jar` |

---

## Phase 4 — Docker Build + Tag  (`/docker-build` → DockerAgent)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 10 | Docker username | `docker.username` | `praveenaws7` |
| 11 | Image name | `docker.image` | `light-ci-server` |
| 12 | Image tag | `docker.tag` | `latest` |
| 13 | Dockerfile path | `docker.dockerfile` | `Dockerfile` |
| 14 | App port | `project.app_port` | `8080` |
| 15 | Auto-approve build | `auto_approve.docker_build` | `true` |

---

## Phase 5 — Docker Push  (`/docker-build` push step → DockerAgent)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 16 | Registry type | `docker.registry` | `dockerhub` |
| 17 | Docker username | `docker.username` | `praveenaws7` |
| 18 | Image name | `docker.image` | `light-ci-server` |
| 19 | Image tag | `docker.tag` | `latest` |
| 20 | ECR account ID | `docker.ecr.account_id` | *(only if registry: ecr)* |
| 21 | ECR region | `docker.ecr.region` | *(only if registry: ecr)* |
| 22 | Auto-approve push | `auto_approve.docker_push` | `true` |

---

## Phase 6 — Terraform Infrastructure Plan  (`/infra-plan` → AWSInfraAgent)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 23 | Terraform working dir | `terraform.working_dir` | `terraform` |
| 24 | AWS region | `aws.region` | `us-east-1` |
| 25 | AWS profile | `aws.profile` | `default` |
| 26 | EC2 instance type | `terraform.instance_type` | `t2.micro` |
| 27 | EC2 key pair name | `terraform.key_pair_name` | `light-ci-server-key` |
| 28 | App port | `terraform.app_port` | `8080` |
| 29 | SSH port | `terraform.ssh_port` | `22` |
| 30 | Auto-approve plan | `auto_approve.terraform_plan` | `true` |
| 31 | Auto-approve apply | `auto_approve.terraform_apply` | `true` |

---

## Phase 7 — Pre-Deploy Validation  (`pre-deploy.sh`)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 32 | Docker image ref | `docker.username` + `docker.image` + `docker.tag` | `praveenaws7/light-ci-server:latest` |
| 33 | AWS profile | `aws.profile` | `default` |
| 34 | Terraform dir | `terraform.working_dir` | `terraform` |
| 35 | SSH key path | `ec2.ssh_key_path` | `~/.ssh/light-ci-server-key.pem` |

---

## Phase 8 — EC2 Deploy  (`/deploy` → DeploymentAgent)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 36 | EC2 public IP | `ec2.public_ip` | *(filled after terraform apply)* |
| 37 | SSH user | `ec2.ssh_user` | `ec2-user` |
| 38 | SSH key path | `ec2.ssh_key_path` | `~/.ssh/light-ci-server-key.pem` |
| 39 | Docker image | `docker.username`/`docker.image`:`docker.tag` | `praveenaws7/light-ci-server:latest` |
| 40 | Container name | `docker.container_name` | `lightci` |
| 41 | App port | `project.app_port` | `8080` |
| 42 | Auto-approve deploy | `auto_approve.ec2_deploy` | `true` |

---

## Phase 9 — Post-Deploy Validation  (`post-deploy.sh`)

| # | Argument | Config Key | Example Value |
|---|----------|------------|---------------|
| 43 | EC2 public IP | `ec2.public_ip` | *(from terraform output)* |
| 44 | App port | `project.app_port` | `8080` |
| 45 | SSH user | `ec2.ssh_user` | `ec2-user` |
| 46 | SSH key path | `ec2.ssh_key_path` | `~/.ssh/light-ci-server-key.pem` |

---

## Security Audit  (`/security-audit` → SecurityAuditAgent)

Runs first in the workflow (before Phase 1) and can also run standalone.

| # | Argument | Config Key | Behaviour |
|---|----------|------------|-----------|
| — | Block on CRITICAL | `auto_approve.block_on_critical_security` | `true` = always pauses |
| — | Auto-proceed on HIGH/MEDIUM/LOW | `auto_approve.security_warnings` | `true` = no pause |

---

## Quick Reference — All 46 Arguments in Order

```
 1. project.root
 2. project.java_version
 3. project.artifact
 4. maven.goals
 5. maven.skip_tests
 6. maven.profiles
 7. maven.extra_args
 8. auto_approve.maven_build
 9. project.artifact               ← post-build re-check
10. docker.username
11. docker.image
12. docker.tag
13. docker.dockerfile
14. project.app_port
15. auto_approve.docker_build
16. docker.registry
17. docker.username                ← push step
18. docker.image
19. docker.tag
20. docker.ecr.account_id          ← ECR only
21. docker.ecr.region              ← ECR only
22. auto_approve.docker_push
23. terraform.working_dir
24. aws.region
25. aws.profile
26. terraform.instance_type
27. terraform.key_pair_name
28. terraform.app_port
29. terraform.ssh_port
30. auto_approve.terraform_plan
31. auto_approve.terraform_apply
32. docker image ref               ← pre-deploy check
33. aws.profile                    ← pre-deploy check
34. terraform.working_dir          ← pre-deploy check
35. ec2.ssh_key_path               ← pre-deploy check
36. ec2.public_ip
37. ec2.ssh_user
38. ec2.ssh_key_path
39. docker image ref               ← deploy step
40. docker.container_name
41. project.app_port
42. auto_approve.ec2_deploy
43. ec2.public_ip                  ← post-deploy check
44. project.app_port               ← post-deploy check
45. ec2.ssh_user                   ← post-deploy check
46. ec2.ssh_key_path               ← post-deploy check
```
