# AI 智能代码审查系统

AI Code Review System - 基于 AI 的智能代码审查工具

## 项目结构

```
ai-code-review/
├── frontend/           # Vue + Vue-Vben-Admin 前端
├── backend/            # Spring Boot 后端（多模块 Maven）
├── docker-compose.yml  # 完整服务编排（含监控）
└── monitoring/         # Prometheus + Grafana 配置
```

## 技术栈

**后端：** Java 17 + Spring Boot 3.2.2 + JPA + Flyway + Redis
**前端：** Vue 3 + Vue-Vben-Admin 5.6 + Element Plus（`apps/web-ele`）
**基础设施：** PostgreSQL 18 + Redis 7 + Docker Compose
**监控：** Prometheus + Grafana

---

## 快速启动

### 前置条件

- Docker & Docker Compose
- Java 17（推荐 Amazon Corretto 17）
- Maven 3.8+
- Node.js 22+ & pnpm 10+（前端本地开发）

### 方式一：仅启动基础设施（本地开发推荐）

启动 PostgreSQL 和 Redis，后端和前端在本地运行：

```bash
# 1. 启动 PostgreSQL + Redis
docker run -d --name aicodereview-postgres \
  -e POSTGRES_DB=aicodereview_dev \
  -e POSTGRES_USER=aicodereview \
  -e POSTGRES_PASSWORD=dev_password_123 \
  -p 5432:5432 postgres:16-alpine

docker run -d --name aicodereview-redis \
  -p 6379:6379 redis:7-alpine

# 2. 指定 Java 17（Homebrew Java 25 与 Lombok 不兼容）
export JAVA_HOME=/path/to/java17
# macOS Corretto 示例：
# export JAVA_HOME=/Users/$USER/Library/Java/JavaVirtualMachines/corretto-17.0.9/Contents/Home

# 3. 构建并启动后端（dev profile）
cd backend
mvn package -DskipTests
JAVA_HOME=$JAVA_HOME java -jar ai-code-review-api/target/ai-code-review-api-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev

# 4. 启动前端（新终端，从项目根目录执行）
cd frontend
pnpm install          # 首次运行需安装依赖（约 2~3 分钟）
pnpm dev:ele          # 启动 Element Plus 管理界面
```

各服务地址：
- 前端：http://localhost:5173
- 后端 API：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health

### 方式二：Docker Compose 全量启动

一键启动所有服务（后端需先构建镜像）：

```bash
docker compose up -d
```

各服务地址：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:5173 | Vue + Element Plus 管理界面 |
| 后端 API | http://localhost:8080 | Spring Boot REST API |
| Grafana | http://localhost:3000 | 监控仪表盘（admin/admin） |
| Prometheus | http://localhost:9090 | 指标采集 |
| PostgreSQL | localhost:5432 | 数据库（aicodereview/dev_password_123） |
| Redis | localhost:6379 | 缓存 + 任务队列 |

### 关键环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `AI_OPENAI_API_KEY` | （空） | OpenAI API 密钥 |
| `AI_ANTHROPIC_API_KEY` | （空） | Anthropic API 密钥 |
| `GIT_GITHUB_TOKEN` | （空） | GitHub Personal Access Token |
| `GIT_GITLAB_TOKEN` | （空） | GitLab Personal Access Token |
| `WEBHOOK_SECRET_GITHUB` | `test-github-secret` | GitHub Webhook 密钥 |
| `WEBHOOK_SECRET_GITLAB` | `test-gitlab-token` | GitLab Webhook token |
| `JWT_SECRET` | 内置默认值 | JWT 签名密钥（生产环境必须修改） |
| `MAIL_SMTP_HOST` | （空） | 邮件服务器（可选） |

本地开发可直接 export 环境变量，或修改 `backend/ai-code-review-api/src/main/resources/application-dev.yml`。

### 停止服务

```bash
# 停止所有容器
docker compose down

# 停止并清除数据卷（重置数据库）
docker compose down -v
```

---

## 开发

使用 BMAD Method 进行开发。

详见各子目录的 README。
