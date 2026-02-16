# PlayForge

[English](README.md) | [中文](README_CN.md)

PlayForge 是一个 AI 驱动的游戏设计平台，基于 Spring Boot 和 React 构建。通过 LangChain4J 集成多个大语言模型（OpenAI、Anthropic Claude、Google Gemini），辅助游戏设计工作流。

## 功能特性

- **用户认证** — 注册、登录、登出，JWT 双令牌（access/refresh）+ Redis 会话管理
- **个人资料管理** — 编辑昵称、简介、头像
- **头像上传** — 服务端签名直传阿里云 OSS
- **令牌自动刷新** — 通过 Axios 拦截器透明刷新 access token
- **AI 集成** — 通过 LangChain4J 支持多 LLM 供应商（OpenAI、Anthropic、Gemini）

## 技术栈

| 层级 | 技术 |
|------|-----|
| 后端 | Java 25、Spring Boot 3.5、MyBatis、Spring Data Redis |
| 前端 | React 19、TypeScript 5、Ant Design 6、Axios、React Router 7 |
| AI/LLM | LangChain4J 1.11.0（OpenAI、Anthropic、Gemini） |
| 存储 | MySQL（Flyway 迁移）、Redis、阿里云 OSS |
| 部署 | Docker（多阶段构建）、阿里云 SAE |

## 项目结构

后端采用领域驱动设计（DDD），使用 Maven 多模块组织：

```
PlayForge/
├── common/           # 公共工具类、常量、异常
├── domain/           # 实体、值对象、仓储接口
├── infrastructure/   # 仓储实现、MyBatis、Redis、JWT、OSS
├── application/      # 应用服务、用例编排
├── api/              # REST 控制器、请求/响应 DTO、拦截器
├── playforge-start/  # Spring Boot 启动入口、配置、Flyway 迁移脚本
├── frontend/         # React + TypeScript 单页应用
└── deploy/           # Docker 构建与阿里云部署脚本
```

## 环境要求

- Java 25+
- Node.js 20+
- MySQL 8+
- Redis 7+
- Maven 3.9+（或使用项目自带的 `mvnw` 包装器）

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/BrotherOrange/PlayForge.git
cd PlayForge
```

### 2. 配置环境变量

```bash
cp deploy/env.example .env
# 编辑 .env，填入数据库、Redis、OSS 和 LLM API 密钥
```

主要变量：

| 变量 | 说明 |
|------|-----|
| `MYSQL_HOST` / `MYSQL_PASSWORD` | MySQL 连接信息 |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接信息 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS |
| `OPENAI_API_KEY` | OpenAI API 密钥 |
| `ANTHROPIC_API_KEY` | Anthropic API 密钥 |
| `GEMINI_API_KEY` | Google Gemini API 密钥 |

### 3. 启动后端

```bash
./mvnw spring-boot:run -pl playforge-start
```

Flyway 会在启动时自动执行数据库迁移。

### 4. 启动前端

```bash
cd frontend
npm install
npm start
```

前端开发服务器运行在 `http://localhost:3000`，API 请求会代理到后端的 `8080` 端口。

## 构建与测试

```bash
# 构建所有后端模块
./mvnw clean package

# 运行后端测试
./mvnw test

# 前端类型检查
cd frontend && npx tsc --noEmit

# 构建前端生产版本
cd frontend && npm run build
```

## Docker

```bash
# 构建镜像
docker build -t playforge .

# 运行容器
docker run -p 8080:8080 --env-file .env playforge
```

多阶段 Dockerfile 会先构建前端（TypeScript → JS），将产物打包到 Spring Boot 静态资源中，最终生成基于最小 JRE 的运行时镜像。

## 部署

`deploy/` 目录提供了阿里云 SAE 部署脚本：

```bash
./deploy/deploy-all.sh        # 构建、推送到 ACR、部署到 SAE
./deploy/build.sh              # 仅构建 Docker 镜像
./deploy/push.sh               # 推送镜像到 ACR
./deploy/deploy.sh             # 部署到 SAE
```

完整的环境变量列表请参考 `deploy/env.example`。

## API 接口

| 方法 | 路径 | 说明 | 需认证 |
|------|------|------|--------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录 | 否 |
| POST | `/api/auth/logout` | 用户登出 | 是 |
| POST | `/api/auth/refresh` | 刷新访问令牌 | 否 |
| GET | `/api/user/profile` | 获取当前用户资料 | 是 |
| PUT | `/api/user/profile` | 更新用户资料 | 是 |
| GET | `/api/oss/policy` | 获取 OSS 上传策略 | 是 |

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
