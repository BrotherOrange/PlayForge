# syntax=docker/dockerfile:1

# ============================================================
# Stage 1: Build frontend
# ============================================================
FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm install --no-audit --no-fund --legacy-peer-deps
COPY frontend/ ./
RUN npm run build

# ============================================================
# Stage 2: Build backend (with frontend static assets)
# ============================================================
FROM eclipse-temurin:25-jdk AS backend-build

WORKDIR /app

# Copy Maven wrapper and parent POM first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY domain/pom.xml domain/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY application/pom.xml application/pom.xml
COPY api/pom.xml api/pom.xml
COPY playforge-start/pom.xml playforge-start/pom.xml

# Download dependencies (cached via BuildKit mount)
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw dependency:go-offline -B || true

# Copy source code
COPY common/ common/
COPY domain/ domain/
COPY infrastructure/ infrastructure/
COPY application/ application/
COPY api/ api/
COPY playforge-start/ playforge-start/

# Copy frontend build output into Spring Boot static resources
COPY --from=frontend-build /app/frontend/build/ playforge-start/src/main/resources/static/

# Build the fat JAR (reuses cached ~/.m2 across builds)
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 3: Runtime
# ============================================================
FROM eclipse-temurin:25-jre AS runtime

LABEL maintainer="PlayForge Team"

WORKDIR /app

# Create log directory
RUN mkdir -p /app/logs

# Copy the executable JAR
COPY --from=backend-build /app/playforge-start/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 \
    --add-opens=java.base/sun.misc=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=prod"]
