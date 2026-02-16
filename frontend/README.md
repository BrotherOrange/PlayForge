# PlayForge Frontend

React + TypeScript single-page application for PlayForge.

## Tech Stack

- **React 19** with TypeScript 5 (strict mode)
- **Ant Design 6** — UI component library
- **React Router 7** — Client-side routing with protected routes
- **Axios** — HTTP client with JWT interceptors and auto token refresh
- **Create React App 5** — Build toolchain

## Project Structure

```
src/
├── api/                # API client and endpoint modules
│   ├── client.ts       # Axios instance with auth interceptors
│   ├── auth.ts         # Login, register, logout
│   ├── user.ts         # Profile CRUD
│   └── oss.ts          # OSS upload policy
├── components/         # Reusable components
│   ├── AppLayout.tsx   # Main layout with header and user dropdown
│   ├── AvatarUpload.tsx # OSS-backed avatar uploader
│   └── ProtectedRoute.tsx # Auth guard
├── pages/              # Page-level components
│   ├── LoginPage.tsx
│   ├── RegisterPage.tsx
│   └── HomePage.tsx    # Profile editing
├── types/
│   └── api.ts          # Shared API response/request type definitions
├── utils/
│   └── token.ts        # localStorage token management
├── App.tsx             # Root router
└── index.tsx           # Entry point
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

## API Proxy

Configured in `package.json`:

```json
"proxy": "http://localhost:8080"
```

All requests to `/api/*` are forwarded to the backend during development.
