# syntax=docker/dockerfile:1.6
#
# Multi-stage build for the AM Ministries church-backend Spring Boot app.
# Target: AWS ECR + ECS Fargate on ap-northeast-2 (Seoul).
#
# Build locally:
#   docker build --platform linux/amd64 -t church-backend:local .
#
# Push to ECR:
#   aws ecr get-login-password --region ap-northeast-2 | \
#     docker login --username AWS --password-stdin <acct>.dkr.ecr.ap-northeast-2.amazonaws.com
#   docker tag church-backend:local <acct>.dkr.ecr.ap-northeast-2.amazonaws.com/church-backend:latest
#   docker push <acct>.dkr.ecr.ap-northeast-2.amazonaws.com/church-backend:latest
#
# Required env at runtime (set in ECS task definition):
#   DB_URL, DB_USERNAME, DB_PASSWORD   — RDS MySQL 8 connection
#   JWT_SECRET                         — base64 or ≥32-byte string
#   CORS_ALLOWED_ORIGINS               — your Vercel frontend origin (comma-sep)
#   UPLOADS_DIR=/data/uploads          — mount an EFS volume here for persistence
#   PRIVATE_UPLOADS_DIR=/data/private  — separate volume for meeting audio
#   MSC_AI_ENABLED, GROQ_API_KEY, ANTHROPIC_API_KEY — only if AI pipeline is on

# ---------------- Build stage ----------------
# Pin to a Temurin tag that ships Java 21 (project targets release 21).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install the maven-wrapper's companion curl + bash so mvnw can bootstrap.
RUN apk add --no-cache bash curl

# Copy build descriptor first so Docker caches the dependency layer across
# source-only edits. Downloads most of Maven's deps on cache miss (~2-3 min).
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Copy source and build the fat jar. -DskipTests because tests need a MySQL —
# CI should run the test suite separately against a service container.
COPY src src
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------------- Runtime stage ----------------
# JRE-only image to keep the production container slim (~180MB vs ~450MB for JDK).
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user — ECS/Fargate best practice. UID 10001 is unprivileged on Linux.
RUN addgroup -S church && adduser -S -G church -u 10001 church

# wget for the HEALTHCHECK below; curl would work too — alpine ships wget already.
# tzdata so LocalDateTime renders in KST where the service layer logs it.
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

# Writable directories for file uploads. ECS task definition should mount an EFS
# volume at /data so writes survive container restarts (default ephemeral volume
# is wiped on redeploy, which would lose every uploaded bulletin PDF / sermon audio).
RUN mkdir -p /data/uploads /data/private && chown -R church:church /data

COPY --from=build --chown=church:church /app/target/*.jar app.jar

USER church

# Spring profile — overridden by env var if a different profile is wanted.
ENV SPRING_PROFILES_ACTIVE=prod
# JVM tuned for Fargate's cgroup-aware memory limit. 75% headroom leaves room
# for native allocations without triggering container OOMKill.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
# Default upload roots inside the EFS mount; override via ECS task env.
ENV UPLOADS_DIR=/data/uploads
ENV PRIVATE_UPLOADS_DIR=/data/private

EXPOSE 8080

# Hits the Actuator health endpoint. ECS can also use its native container
# health check but a Dockerfile-level one keeps `docker run` behavior correct.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
