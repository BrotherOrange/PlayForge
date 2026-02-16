# PlayForge

[English](README.md) | [中文](README_CN.md)

PlayForge 是一个 AI 驱动的游戏设计平台，基于 Spring Boot 和 React 构建。通过 LangChain4J 集成多个大语言模型（OpenAI、Anthropic Claude、Google Gemini），辅助游戏设计工作流。

## 技术栈

| 层级 | 技术 |
|------|-----|
| 后端 | Java 25、Spring Boot 3.5、MyBatis、Spring Data Redis |
| 前端 | React 19、Create React App |
| AI/LLM | LangChain4J 1.11.0（OpenAI、Anthropic、Gemini） |
| 存储 | MySQL、Redis、阿里云 OSS |
| 部署 | Docker、阿里云 SAE |

## 项目结构

后端采用领域驱动设计（DDD），使用 Maven 多模块组织：

```
PlayForge/
├── common/           # 公共工具类、常量、异常
├── domain/           # 实体、值对象、仓储接口
├── infrastructure/   # 仓储实现、MyBatis、Redis、外部集成
├── application/      # 应用服务、用例编排
├── api/              # REST 控制器、请求/响应 DTO
├── playforge-start/  # Spring Boot 启动入口、配置
└── frontend/         # React 单页应用
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
git clone https://github.com/your-org/PlayForge.git
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

### 4. 启动前端

```bash
cd frontend
npm install
npm start
```

前端开发服务器运行在 `http://localhost:3000`，API 请求会代理到后端的 `8080` 端口。

## 构建与测试

```bash
# 构建所有模块
./mvnw clean package

# 运行后端测试
./mvnw test

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

多阶段 Dockerfile 会先构建前端，将产物打包到 Spring Boot 静态资源中，最终生成基于最小 JRE 的运行时镜像。

## 部署

`deploy/` 目录提供了阿里云 SAE 部署脚本：

```bash
./deploy/deploy-all.sh   # 构建、推送到 ACR、部署到 SAE
```

完整的环境变量列表请参考 `deploy/env.example`。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
