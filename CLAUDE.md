# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PlayForge is an AI-powered game design platform. Spring Boot 3.5 (Java 25) backend with React 19 (TypeScript) frontend. Integrates OpenAI, Anthropic Claude, and Google Gemini via LangChain4J 1.11.0 for streaming AI chat.

## Build & Run Commands

### Backend (root directory)
- **Build all:** `./mvnw clean package`
- **Run:** `./mvnw spring-boot:run -pl playforge-start`
- **Run tests:** `./mvnw test`
- **Single test class:** `./mvnw test -pl playforge-start -Dtest=PlayForgeApplicationTests`
- **Single test method:** `./mvnw test -pl playforge-start -Dtest=PlayForgeApplicationTests#contextLoads`
- **Compile one module:** `./mvnw clean compile -pl common`
- **Compile with dependencies:** `./mvnw clean compile -pl playforge-start -am`

### Frontend (`frontend/` directory)
- **Install:** `npm install`
- **Dev server:** `npm start` (port 3000, proxies `/api` to backend 8080)
- **Build:** `npm run build`
- **Type check:** `npx tsc --noEmit`
- **Tests:** `npm test`

### Deployment
- **Full deploy:** `bash deploy/deploy-all.sh` (build Docker image → push to Alibaba Cloud ACR → deploy to SAE)
- **Build only:** `bash deploy/build.sh`

## Maven Multi-Module Structure (DDD)

```
common/           ← no deps (constants, exceptions, ResultCode, ApiResult)
domain/           ← common (entities, repository interfaces — no framework imports)
infrastructure/   ← common, domain (MyBatis impls, Redis, JWT, OSS, LangChain4J providers)
application/      ← common, domain (service orchestration, AgentFactory, chat streaming)
api/              ← common, domain, application (controllers, DTOs, interceptors, WebSocket)
playforge-start/  ← all modules (Spring Boot main, config, Flyway migrations)
frontend/         ← React SPA (CRA, Ant Design, Axios, React Router)
```

**Java package root:** `com.game.playforge`

## Key Architecture Patterns

### Error Handling
`BusinessException(ResultCode)` is the standard way to signal business errors. `ResultCode` enum bundles code, message, and HTTP status. `GlobalExceptionHandler` (`@RestControllerAdvice`) catches all exceptions and returns `ApiResult<Void>`.

Code ranges: `0=success | 10xx=auth | 20xx=user | 30xx=oss | 40xx=client | 50xx=agent | 9xxx=system`

### Auth Flow
- **JWT**: HMAC-SHA256 access tokens (short-lived) + UUID refresh tokens in Redis
- **AuthInterceptor**: Validates Bearer token, stores `userId` in request attribute (`AuthConstants.CURRENT_USER_ID`)
- **Public endpoints** (excluded from auth): `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`
- **TraceIdInterceptor**: Generates UUID traceId in MDC for all `/api/**` requests
- **Frontend**: Axios interceptor auto-refreshes expired access tokens (code 1003) with queuing to avoid thundering herd

### ID Generation
Snowflake IDs via MyBatis-Plus `ASSIGN_ID`. All `Long` IDs are serialized as `String` in JSON responses (`JacksonConfig` with `ToStringSerializer`) because Snowflake IDs exceed JavaScript's `Number.MAX_SAFE_INTEGER`. Frontend types use `string` for all ID fields.

### Agent / AI Chat System
1. **AgentDefinition** — per-user model config (provider, model, system prompt, tools, skills)
2. **AgentThread** — conversation session bound to an agent
3. **AgentFactory** — builds LangChain4J `AiService` instances with tools, memory, and optional system prompt
4. **AgentChatAppService.chatStream()** — returns `Flux<String>` for streaming responses
5. **WebSocket** at `/ws/agent-chat?threadId=<id>` with `Sec-WebSocket-Protocol: bearer,<jwt>`:
   - Client sends `{"type": "message", "content": "..."}` or `{"type": "cancel"}`
   - Server streams `{"type": "token", "content": "..."}` → `{"type": "done"}` or `{"type": "error"}`
6. **Admin-only**: Agent creation, deletion, and chat require `user.isAdmin = true`

### LangChain4J Provider Lifecycle
1. `LangChain4jBeanFilter` (BeanFactoryPostProcessor) removes bean definitions for providers with missing API keys — prevents startup failure
2. `ModelProviderRegistry` registers available `ChatModel`/`StreamingChatModel` pairs at `@PostConstruct`
3. `ToolRegistry` (SmartInitializingSingleton) scans for `@Tool`-annotated methods after all singletons are instantiated
4. `SystemPromptResolver` resolves prompts from inline text or `classpath:prompts/<ref>` files, appends skill fragments, caches results
5. `RedisChatMemoryStore` persists LangChain4J chat memory in Redis with TTL

### Database
- **MySQL** with Flyway migrations at `playforge-start/src/main/resources/db/migration/`
- Naming: `V<n>__<description>.sql` (e.g., `V1__create_user_table.sql`)
- Config: `spring.flyway.ignore-migration-patterns: "*:missing"`
- **MyBatis-Plus** with mapper XMLs at `classpath*:mapper/**/*.xml`
- Soft deletes use `is_active` / `is_deleted` boolean columns

### Frontend
- **Routing** (App.tsx): `ProtectedRoute` wraps authenticated routes under `AppLayout`; public routes: `/login`, `/register`
- **API client** (`api/client.ts`): Axios with baseURL `/api`, auto Bearer token, 401 refresh interceptor
- **WebSocket hook** (`hooks/useAgentWebSocket.ts`): Uses env-configurable WebSocket base URL with protocol normalization (`http -> ws`, `https -> wss`)
- **Chat page**: Sidebar with agent/conversation list, real-time Markdown rendering (`react-markdown` + `remark-gfm`)
- **Outlet context**: `AppLayout` passes `{ user, setUser }` to child routes via React Router's `useOutletContext`

### Infrastructure
- **Redis**: Lettuce client for refresh tokens, chat memory, and caching
- **OSS**: Alibaba Cloud OSS for avatar uploads with server-signed policies
- **Docker**: Multi-stage build (Node 20-alpine → frontend, Eclipse Temurin 25 → backend)
- **Config**: `application.yaml` with `spring.profiles.active=local`; production values from environment variables
