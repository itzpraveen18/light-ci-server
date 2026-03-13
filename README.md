# ⚡ LightCI Server

A lightweight, Jenkins-inspired CI dashboard built with **Java 17 + Spring Boot**.
Designed for learning — minimal dependencies, clean code, fully runnable in minutes.

---

## Quick Start

```bash
cd light-ci-server
mvn spring-boot:run
```

Open your browser: **http://localhost:8080**

---

## Features

| Feature | Details |
|---------|---------|
| Web Dashboard | Job list + one-click Run button |
| Async Execution | Jobs run in background threads; HTTP responds immediately |
| Live Status | Dashboard auto-refreshes every 3s while jobs are running |
| Log Viewer | Colour-coded terminal-style log for each execution |
| Job History | Last 20 executions with status, duration, and log link |
| Log Files | Each execution writes a `.log` file under `logs/` |
| YAML Config | Add/edit jobs in `src/main/resources/jobs.yml` — no recompile needed |

---

## Project Structure

```
light-ci-server/
├── pom.xml
├── README.md
└── src/main/java/com/example/lightci/
    ├── LightCiApplication.java       ← Spring Boot entry point (@EnableAsync)
    ├── controller/
    │   ├── JobController.java        ← GET / | POST /jobs/{id}/run | GET /executions/{id}
    │   └── LogFormatter.java         ← Thymeleaf helper — colours log output
    ├── service/
    │   └── JobService.java           ← Loads jobs.yml, runs jobs @Async, stores history
    ├── model/
    │   ├── Job.java                  ← Job definition (id, name, commands)
    │   ├── JobExecution.java         ← Single execution record (status, logs, timing)
    │   └── ExecutionStatus.java      ← Enum: PENDING | RUNNING | SUCCESS | FAILED
    └── executor/
        └── ShellExecutor.java        ← Forks /bin/sh processes, streams stdout/stderr
```

---

## How It Works

```
Browser → GET /
            ↓
     JobController.dashboard()
            ↓
     JobService.getJobs()          → reads jobs.yml once at startup
     JobService.getRecentExecutions() → reads in-memory history list
            ↓
     Thymeleaf renders dashboard.html

Browser → POST /jobs/hello-world/run
            ↓
     JobController.runJob("hello-world")
            ↓
     JobService.triggerJob()  @Async → returns immediately
            ↓         ↓
     HTTP redirects    Background thread runs ShellExecutor
     to dashboard      → forks /bin/sh for each command
                        → streams stdout/stderr to log file + memory
                        → sets ExecutionStatus to SUCCESS or FAILED
```

---

## Adding a Custom Job

Edit `src/main/resources/jobs.yml`:

```yaml
jobs:
  - id: my-job
    name: My Custom Job
    description: Does something useful
    commands:
      - echo "Step 1"
      - ls -la
      - echo "Done"
```

Restart the server — your job appears on the dashboard immediately.

---

## Build Commands

```bash
# Run in development mode (hot-reload)
mvn spring-boot:run

# Build an executable JAR
mvn clean package
java -jar target/light-ci-server.jar

# Run tests only
mvn test
```

---

## Log Files

Each job execution writes a log file to the `logs/` directory:
```
logs/
└── a3f1b2c4-xxxx-xxxx-xxxx-xxxxxxxxxxxx.log
```

---

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 17 | Language |
| Spring Boot 3.2 | Web framework + async execution |
| Thymeleaf | Server-side HTML templates |
| Jackson YAML | Parse jobs.yml |
| Bootstrap 5 | Dashboard UI |
| Bootstrap Icons | Icon set |
| JetBrains Mono | Terminal font in log viewer |
