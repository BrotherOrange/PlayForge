FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci --no-audit --no-fund --legacy-peer-deps

COPY . .

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN cd frontend && npm run build
RUN rm -rf playforge-start/src/main/resources/static \
    && mkdir -p playforge-start/src/main/resources/static \
    && cp -a frontend/build/. playforge-start/src/main/resources/static/
RUN ./mvnw clean package -pl playforge-start -am -DskipTests -B

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu

LABEL maintainer="PlayForge Team"

WORKDIR /app

RUN mkdir -p /app/logs

COPY --from=builder /workspace/playforge-start/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 \
    --add-opens=java.base/sun.misc=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    -Dlangchain4j.http.clientBuilderFactory=dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory"

ENTRYPOINT ["/app/docker-entrypoint.sh"]
