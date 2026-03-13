---
name: deploy-all
description: Run the full end-to-end deployment pipeline from JAR build to EC2 container deploy
type: command
agent: DeploymentAgent
auto_approve: true
config_source: .claude/config.yml
---

Run the full deployment pipeline end-to-end: build JAR → build Docker image → push to registry → provision EC2 → deploy container.

Execute these steps in order:

1. **Build JAR**
```bash
cd /Users/praveen/python/light-ci-server
mvn clean package -DskipTests
```
Confirm `target/light-ci-server.jar` exists before continuing.

2. **Start Docker if needed**
Check if Docker is running with `docker info`. If not, run `colima start`.

3. **Build and tag Docker image**
```bash
docker build -t light-ci-server:latest .
docker tag light-ci-server:latest ${DOCKER_USERNAME}/light-ci-server:latest
```

4. **Push to Docker Hub**
```bash
docker push ${DOCKER_USERNAME}/light-ci-server:latest
```

5. **Terraform init + plan**
```bash
cd /Users/praveen/python/light-ci-server/terraform
terraform init
terraform plan
```
Show the plan and ask: "Ready to create the EC2 instance on AWS? (yes/no)"

6. **Terraform apply** — only after user confirms
```bash
terraform apply -auto-approve
```
Print the EC2 public IP and app URL from terraform outputs.

7. **Deploy container** — ask user for SSH key path, then confirm before running
```bash
cd /Users/praveen/python/light-ci-server
./scripts/deploy.sh --host <EC2_IP> --key <PEM_PATH> --image ${DOCKER_USERNAME}/light-ci-server:latest
```

8. **Verify** — curl the app and print the final URL
```bash
curl -s -o /dev/null -w "%{http_code}" http://<EC2_IP>:8080
```

IMPORTANT: Always ask for confirmation before `terraform apply` or `terraform destroy`.
