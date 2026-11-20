# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies in their own layer so source changes rebuild quickly.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# ---- Runtime ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home skygo
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER skygo
# Heap is sized from the container memory limit set in docker-compose.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
