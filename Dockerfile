# syntax=docker/dockerfile:1

# ============================================================
# Runtime-only image (build is done locally via deploy/package.sh)
# ============================================================
FROM eclipse-temurin:25-jre

LABEL maintainer="PlayForge Team"

WORKDIR /app

# Create log directory
RUN mkdir -p /app/logs

# Copy the pre-built fat JAR (built by package.sh)
COPY playforge-start/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 \
    --add-opens=java.base/sun.misc=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    -Dlangchain4j.http.clientBuilderFactory=dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=prod"]
