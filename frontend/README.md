# PlayForge Frontend

React + TypeScript single-page application for PlayForge.

## Tech Stack

- **React 19** with TypeScript 5 (strict mode)
- **Ant Design 6** — UI component library
- **React Router 7** — Client-side routing with protected routes
- **Axios** — HTTP client with JWT interceptors and auto token refresh
- **react-markdown** + **remark-gfm** — Real-time Markdown rendering for AI chat
- **Create React App 5** — Build toolchain

## Project Structure

```
src/
├── api/                    # API client and endpoint modules
│   ├── client.ts           # Axios instance with auth interceptors
│   ├── auth.ts             # Login, register, logout
│   ├── user.ts             # Profile CRUD
│   ├── oss.ts              # OSS upload policy
│   └── chat.ts             # Agent threads and messages
├── components/             # Reusable components
│   ├── AppLayout.tsx       # Main layout with header and user dropdown
│   ├── AvatarUpload.tsx    # OSS-backed avatar uploader
│   ├── ProtectedRoute.tsx  # Auth guard
│   └── TeamPanel.tsx       # Agent/conversation sidebar panel
├── constants/
│   └── agentTypes.ts       # Agent type definitions
├── hooks/
│   └── useAgentWebSocket.ts # WebSocket hook for streaming AI chat
├── pages/                  # Page-level components
│   ├── LoginPage.tsx
│   ├── RegisterPage.tsx
│   ├── HomePage.tsx        # Dashboard / landing
│   ├── ChatPage.tsx        # AI agent chat interface
│   └── ProfilePage.tsx     # User profile editing
├── types/
│   └── api.ts              # Shared API response/request type definitions
├── utils/
│   └── token.ts            # localStorage token management
├── App.tsx                 # Root router
└── index.tsx               # Entry point
```

## Getting Started

```bash
npm install
npm start
```

Dev server runs on `http://localhost:3000` and proxies `/api` requests to `http://localhost:8080`.

## Scripts

| Command | Description |
|---------|-------------|
| `npm start` | Start development server |
| `npm run build` | Production build |
| `npm test` | Run tests |
| `npx tsc --noEmit` | TypeScript type check |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REACT_APP_WS_BASE_URL` | WebSocket base URL | Derived from `window.location` |
| `REACT_APP_API_BASE_URL` | API base URL | `/api` (proxied in dev) |

## WebSocket

The chat page connects to the backend via WebSocket for streaming AI responses.

- **Endpoint:** `/ws/agent-chat?threadId=<id>`
- **Auth:** `Sec-WebSocket-Protocol: bearer,<jwt>`

**Client messages:**

| Type | Format |
|------|--------|
| Send message | `{"type": "message", "content": "..."}` |
| Cancel response | `{"type": "cancel"}` |

**Server events:**

| Type | Description |
|------|-------------|
| `token` | Streamed token fragment: `{"type": "token", "content": "..."}` |
| `done` | Stream complete: `{"type": "done"}` |
| `error` | Error occurred: `{"type": "error", "content": "..."}` |

## API Proxy

Configured in `package.json`:

```json
"proxy": "http://localhost:8080"
```

All requests to `/api/*` are forwarded to the backend during development.
