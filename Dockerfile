# ── Stage 1: Build fat JAR ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime with Eclipse Temurin (better multi-platform support) ─────
FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

COPY --from=builder /app/target/light-ci-server.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
