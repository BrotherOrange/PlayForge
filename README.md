# PlayForge

[English](README.md) | [中文](README_CN.md)

PlayForge is an AI-powered game design platform built with Spring Boot and React. It integrates multiple LLM providers (OpenAI, Anthropic Claude, Google Gemini) via LangChain4J to assist with game design workflows.

## Features

- **User Authentication** — Registration, login, logout with JWT access/refresh tokens and Redis-backed session management
- **Profile Management** — Edit nickname, bio, and avatar
- **Avatar Upload** — Direct upload to Alibaba Cloud OSS with server-signed policies
- **Token Auto-Refresh** — Transparent access token refresh via Axios interceptors
- **AI Integration** — Multi-provider LLM support via LangChain4J (OpenAI, Anthropic, Gemini)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 3.5, MyBatis, Spring Data Redis |
| Frontend | React 19, TypeScript 5, Ant Design 6, Axios, React Router 7 |
| AI/LLM | LangChain4J 1.11.0 (OpenAI, Anthropic, Gemini) |
| Storage | MySQL (Flyway migrations), Redis, Alibaba Cloud OSS |
| Deployment | Docker (multi-stage), Alibaba Cloud SAE |

## Project Structure

The backend follows Domain-Driven Design with a Maven multi-module layout:

```
PlayForge/
├── common/           # Shared utilities, constants, exceptions
├── domain/           # Entities, value objects, repository interfaces
├── infrastructure/   # Repository implementations, MyBatis, Redis, JWT, OSS
├── application/      # Application services, use-case orchestration
├── api/              # REST controllers, request/response DTOs, interceptors
├── playforge-start/  # Spring Boot entry point, configuration, Flyway migrations
├── frontend/         # React + TypeScript SPA
└── deploy/           # Docker build & Alibaba Cloud deployment scripts
```

## Prerequisites

- Java 25+
- Node.js 20+
- MySQL 8+
- Redis 7+
- Maven 3.9+ (or use the included `mvnw` wrapper)

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/BrotherOrange/PlayForge.git
cd PlayForge
```

### 2. Configure environment variables

```bash
cp deploy/env.example .env
# Edit .env with your database, Redis, OSS, and LLM API keys
```

Key variables:

| Variable | Description |
|----------|-------------|
| `MYSQL_HOST` / `MYSQL_PASSWORD` | MySQL connection |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis connection |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | Alibaba Cloud OSS |
| `OPENAI_API_KEY` | OpenAI API key |
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `GEMINI_API_KEY` | Google Gemini API key |

### 3. Run the backend

```bash
./mvnw spring-boot:run -pl playforge-start
```

Flyway will automatically run database migrations on startup.

### 4. Run the frontend

```bash
cd frontend
npm install
npm start
```

The frontend dev server runs on `http://localhost:3000` and proxies API requests to the backend on port `8080`.

## Build & Test

```bash
# Build all backend modules
./mvnw clean package

# Run backend tests
./mvnw test

# Type-check frontend
cd frontend && npx tsc --noEmit

# Build frontend for production
cd frontend && npm run build
```

## Docker

```bash
# Build image
docker build -t playforge .

# Run container
docker run -p 8080:8080 --env-file .env playforge
```

The multi-stage Dockerfile builds the frontend (TypeScript → JS), bundles it into Spring Boot static resources, and produces a minimal JRE-based runtime image.

## Deployment

Deploy scripts are provided in `deploy/` for Alibaba Cloud SAE:

```bash
./deploy/deploy-all.sh        # Build, push to ACR, and deploy to SAE
./deploy/build.sh              # Build Docker image only
./deploy/push.sh               # Push image to ACR
./deploy/deploy.sh             # Deploy to SAE
```

See `deploy/env.example` for the full list of required environment variables.

## API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/auth/register` | Register new user | No |
| POST | `/api/auth/login` | Login | No |
| POST | `/api/auth/logout` | Logout | Yes |
| POST | `/api/auth/refresh` | Refresh access token | No |
| GET | `/api/user/profile` | Get current user profile | Yes |
| PUT | `/api/user/profile` | Update profile | Yes |
| GET | `/api/oss/policy` | Get OSS upload policy | Yes |

## License

This project is licensed under the [Apache License 2.0](LICENSE).
