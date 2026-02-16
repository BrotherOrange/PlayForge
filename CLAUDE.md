# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PlayForge is a full-stack web application with a Spring Boot 3.5 backend (Java 25) and a React 19 frontend (Create React App).

## Build & Run Commands

### Backend (root directory)
- **Build:** `./mvnw clean package` (or `mvn clean package` if Maven is installed globally)
- **Run:** `./mvnw spring-boot:run -pl playforge-start`
- **Run tests:** `./mvnw test`
- **Run a single test class:** `./mvnw test -pl playforge-start -Dtest=PlayForgeApplicationTests`
- **Run a single test method:** `./mvnw test -pl playforge-start -Dtest=PlayForgeApplicationTests#contextLoads`
- **Build a single module:** `./mvnw clean compile -pl common` (replace `common` with module name)
- **Build a module with dependencies:** `./mvnw clean compile -pl playforge-start -am`

### Frontend (`frontend/` directory)
- **Install dependencies:** `npm install`
- **Dev server:** `npm start` (port 3000)
- **Build:** `npm run build`
- **Run tests:** `npm test`
- **Lint:** ESLint is configured via react-app preset; runs automatically during `npm start` and `npm test`

## Architecture

- **Backend:** Spring Boot with Web (REST), WebFlux (reactive), MyBatis (SQL persistence), Spring Data Redis (caching/sessions), and JDBC
- **Frontend:** React 19 SPA bootstrapped with Create React App, using Jest + React Testing Library for tests
- **Java package root:** `com.game.playforge`
- **AI/LLM:** LangChain4J 1.11.0 with OpenAI, Anthropic Claude, and Google Gemini providers
- **Cloud:** Alibaba Cloud ecosystem (OSS for storage, RDS for MySQL, Redis, SAE for deployment)
- **Database:** MySQL with HikariCP connection pool, MyBatis mapper XMLs at `classpath*:mapper/**/*.xml`

## Deployment

- **Docker:** Multi-stage Dockerfile (Node 20-alpine for frontend build, Eclipse Temurin 25 for backend)
- **Deploy scripts in `deploy/`:**
  - `build.sh` — Build Docker image (linux/amd64)
  - `push.sh` — Push to Alibaba Cloud ACR
  - `deploy.sh` — Deploy to Alibaba Cloud SAE
  - `deploy-all.sh` — All-in-one: build + push + deploy
  - `package.sh` — Build fat JAR with frontend assets bundled into Spring Boot static resources
- **Config:** `application.yaml` uses `spring.profiles.active=local` by default; production values come from environment variables

## Maven Multi-Module Structure

The backend is organized as a Maven multi-module project following Domain-Driven Design:

```
PlayForge/
├── pom.xml                          # Parent POM (packaging=pom)
├── common/                          # Shared utilities, constants, enums, exceptions
│   └── src/main/java/com/game/playforge/common/
├── domain/                          # Core domain: entities, value objects, domain services, repository interfaces
│   └── src/main/java/com/game/playforge/domain/
│       ├── model/
│       ├── service/
│       └── repository/
├── infrastructure/                  # Infrastructure: repository implementations, MyBatis, Redis, external integrations
│   └── src/main/java/com/game/playforge/infrastructure/
│       ├── persistence/
│       └── external/
├── application/                     # Application services, use-case orchestration
│   └── src/main/java/com/game/playforge/application/
├── api/                             # Presentation layer: REST controllers, request/response DTOs
│   └── src/main/java/com/game/playforge/api/
├── playforge-start/                   # Spring Boot entry point, configuration, assembly
│   └── src/
│       ├── main/java/com/game/playforge/PlayForgeApplication.java
│       ├── main/resources/application.yaml
│       └── test/java/com/game/playforge/PlayForgeApplicationTests.java
└── frontend/                        # React 19 SPA
```

## Module Dependency Rules

```
common          ← no dependencies (pure utilities)
domain          ← common
infrastructure  ← common, domain
application     ← common, domain
api             ← common, domain, application
playforge-start ← api, application, infrastructure (transitively pulls in all)
```

- **common** → depends on nothing (pure utilities, constants, exceptions)
- **domain** → depends on common only (pure business logic, no framework imports)
- **infrastructure** → implements domain.repository interfaces; depends on domain and common
- **application** → depends on domain and common (use-case orchestration)
- **api** → depends on application, domain, and common (REST controllers)
- **playforge-start** → assembles all modules; contains Spring Boot main class, configuration, and tests
