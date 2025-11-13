# Multi-stage Dockerfile for YouTube Webhook Ingestion Service
# Built with Spring Boot 4.0.0 and Java 25

# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and configuration files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Extract JAR layers for better caching
RUN mkdir -p build/dependency && \
    cd build/dependency && \
    java -Djarmode=tools -jar ../libs/*.jar extract --layers --destination .

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy extracted layers from builder stage
COPY --from=builder --chown=spring:spring /app/build/dependency/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/build/dependency/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/build/dependency/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/build/dependency/application/ ./

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Entry point using Spring Boot layered JAR
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
