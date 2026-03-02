# SolonClaw Dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="SolonClaw Team"
LABEL description="SolonClaw AI Agent Service"
LABEL version="1.0.0"

# Install curl for health check
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S solonclaw && adduser -S solonclaw -G solonclaw

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/target/solonclaw-*-jar-with-dependencies.jar /app/solonclaw.jar

# Create workspace directory
RUN mkdir -p /app/workspace && chown -R solonclaw:solonclaw /app

# Switch to non-root user
USER solonclaw

# Expose port
EXPOSE 41234

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:41234/health/live || exit 1

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"

# Default environment
ENV SOLON_ENV=prod

# Start command
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar solonclaw.jar"]