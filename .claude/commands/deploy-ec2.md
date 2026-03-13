Deploy the Docker container to the EC2 instance.

Steps:
1. Get the EC2 public IP from Terraform output:

```bash
cd /Users/praveen/python/light-ci-server/terraform
terraform output ec2_public_ip
```

2. Ask the user for the path to their SSH key (.pem file) if not already known.

3. Ask the user to confirm: "Ready to SSH into EC2 and deploy the container?"

4. Only if confirmed — run the deploy script:

```bash
cd /Users/praveen/python/light-ci-server
./scripts/deploy.sh \
  --host <EC2_PUBLIC_IP> \
  --key <PATH_TO_PEM> \
  --image ${DOCKER_USERNAME}/light-ci-server:latest
```

5. After deploy, verify the app is live:

```bash
curl -s -o /dev/null -w "%{http_code}" http://<EC2_PUBLIC_IP>:8080
```

6. Print the final URL: `http://<EC2_PUBLIC_IP>:8080`
