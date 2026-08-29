# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
# curl backs the /actuator/health container healthcheck in docker-compose.
# Run as a dedicated non-root user (container hardening).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --shell /usr/sbin/nologin appuser
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
# Container-friendly heap sizing (honors cgroup memory limits); override via
# JAVA_TOOL_OPTIONS if explicit -Xmx sizing is ever needed.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
