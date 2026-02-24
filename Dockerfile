# Stage 1: Build
FROM gradle:8-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/claude-memory-mcp-standalone.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
