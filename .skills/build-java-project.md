# Skill: build-java-project

## Purpose
Build the Java application using Maven and produce an executable fat JAR.

## Prerequisites
- Java 17 installed (`java -version`)
- Maven 3.9+ installed (`mvn -version`)
- `pom.xml` present at project root

## Steps

### 1. Navigate to project root
```bash
cd light-ci-server
```

### 2. Download dependencies (optional — speeds up subsequent builds)
```bash
mvn dependency:go-offline
```

### 3. Build the fat JAR
```bash
mvn clean package -DskipTests
```

### 4. Run tests only (optional)
```bash
mvn test
```

### 5. Build with tests
```bash
mvn clean package
```

## Output Artifact
```
target/light-ci-server.jar
```

## Verify the JAR
```bash
java -jar target/light-ci-server.jar
# App starts at http://localhost:8080
```

## Notes
- `-DskipTests` skips unit tests for faster CI builds
- The `maven-shade-plugin` in `pom.xml` produces a self-contained fat JAR
- The JAR includes all dependencies and the embedded Tomcat server
