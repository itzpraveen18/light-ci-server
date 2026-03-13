Push the Docker image to Docker Hub registry.

Steps:
1. Ensure the image `${DOCKER_USERNAME}/light-ci-server:latest` exists locally
2. Push to Docker Hub:

```bash
docker push ${DOCKER_USERNAME}/light-ci-server:latest
```

3. Confirm the push succeeded and print the image digest.

Note: If not logged in, prompt the user to run `docker login -u ${DOCKER_USERNAME}` first.
