Build and tag the Docker image for LightCI Server.

Steps:
1. Make sure Colima (Docker) is running — run `colima start` if not
2. Build the image from the project root:

```bash
cd /Users/praveen/python/light-ci-server
docker build -t light-ci-server:latest .
docker tag light-ci-server:latest ${DOCKER_USERNAME}/light-ci-server:latest
```

3. Confirm the image was created and show its size with `docker images | grep light-ci-server`
