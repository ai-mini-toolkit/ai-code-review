# Epic 1: 项目基础设施与配置管理

**用户价值**：开发团队可以初始化项目基础设施、配置 AI 模型和管理系统设置，为代码审查系统提供完整的运行环境。

**用户成果**：
- 完成 Spring Boot 多模块和 Vue-Vben-Admin 项目初始化
- 配置数据库、Redis 和基础设施组件
- 创建项目配置、AI 模型配置和 Prompt 模板的管理功能
- 建立完整的配置管理 Web 界面

**覆盖的功能需求**：FR 1.8（配置管理）
**覆盖的非功能需求**：NFR 5（可维护性）
**覆盖的附加需求**：启动模板、技术栈、命名约定、部署要求

---

## Stories

### Story 1.1: 从启动模板初始化 Spring Boot 多模块项目

**用户故事**：
作为开发团队，
我想要从 Spring Boot 启动模板创建多模块 Maven 项目，
以便建立后端服务的基础结构。

**验收标准**：

**Given** 项目根目录为空（backend/ 目录）
**When** 执行项目初始化
**Then** 创建以下 Maven 模块结构：
- ai-code-review-api（REST API 层）
- ai-code-review-service（业务逻辑层）
- ai-code-review-repository（数据访问层）
- ai-code-review-integration（外部集成层）
- ai-code-review-worker（异步任务工作器）
- ai-code-review-common（共享工具）

**And** 每个模块包含标准目录结构：src/main/java、src/main/resources、src/test/java
**And** 父 POM 配置 Java 17、Spring Boot 3.x、依赖管理
**And** 包名遵循 com.aicodereview.* 约定
**And** 包含 application.yml 配置文件模板
**And** 项目可成功编译（mvn clean install）

---

### Story 1.2: 从 Vue-Vben-Admin 模板初始化前端项目

**用户故事**：
作为前端开发者，
我想要从 Vue-Vben-Admin 模板创建前端项目，
以便建立现代化的管理界面基础。

**验收标准**：

**Given** 项目根目录为空（frontend/ 目录）
**When** 执行 Vue-Vben-Admin 初始化
**Then** 创建以下目录结构：
- src/views/（页面组件）
- src/components/（可复用组件）
- src/api/（API 客户端）
- src/stores/（Pinia 状态管理）
- src/router/（Vue Router 配置）
- src/utils/（工具函数）
- src/types/（TypeScript 类型）

**And** 配置文件包括 vite.config.ts、tsconfig.json、package.json
**And** 安装依赖：Vue 3、Vite、Element Plus、Pinia、Vue Router
**And** 项目可成功启动开发服务器（npm run dev）
**And** 构建成功（npm run build）

---

### Story 1.3: 配置 PostgreSQL 数据库连接与 JPA

**用户故事**：
作为后端开发者，
我想要配置 PostgreSQL 数据库连接和 Spring Data JPA，
以便后续模块可以进行数据持久化。

**验收标准**：

**Given** Spring Boot 项目已初始化
**When** 配置数据库连接
**Then** 添加 PostgreSQL JDBC 驱动依赖
**And** 配置 application.yml 包含：
- spring.datasource.url（PostgreSQL 连接字符串）
- spring.datasource.username/password
- spring.jpa.hibernate.ddl-auto=validate
- spring.jpa.properties.hibernate.dialect=PostgreSQLDialect

**And** 配置 HikariCP 连接池参数（最大连接数、超时等）
**And** 创建 JPA 配置类，启用 @EnableJpaRepositories
**And** 数据库连接健康检查成功（Spring Boot Actuator /health）
**And** 配置环境变量支持（开发、测试、生产环境）

---

### Story 1.4: 配置 Redis 连接与缓存

**用户故事**：
作为后端开发者，
我想要配置 Redis 连接和缓存支持，
以便后续模块可以使用 Redis 进行缓存和队列管理。

**验收标准**：

**Given** Spring Boot 项目已配置数据库
**When** 配置 Redis 连接
**Then** 添加 Spring Data Redis 和 Lettuce 依赖
**And** 配置 application.yml 包含：
- spring.redis.host、port
- spring.redis.password（如有）
- spring.redis.lettuce.pool 连接池配置

**And** 创建 RedisTemplate 和 StringRedisTemplate Bean
**And** 配置 Redis 缓存管理器（RedisCacheManager）
**And** Redis 连接健康检查成功
**And** 编写单元测试验证 Redis 读写操作
**And** 支持环境变量配置（开发、测试、生产）

---

### Story 1.5: 实现项目配置管理后端 API

**用户故事**：
作为系统管理员，
我想要通过 API 管理项目配置（Git 仓库、Webhook 密钥、审查开关），
以便集成 Git 平台和控制审查行为。

**验收标准**：

**Given** 数据库和 Redis 已配置
**When** 实现项目配置 API
**Then** 创建 `project` 表（如不存在）：
- id（主键）、name、description、enabled
- git_platform（GitHub/GitLab/CodeCommit）、repo_url
- webhook_secret（加密存储）、created_at、updated_at

**And** 实现 ProjectController REST API：
- POST /api/v1/projects（创建项目）
- GET /api/v1/projects（列出项目）
- GET /api/v1/projects/{id}（获取项目详情）
- PUT /api/v1/projects/{id}（更新项目）
- DELETE /api/v1/projects/{id}（删除项目）

**And** 实现 ProjectService 业务逻辑层
**And** 实现 ProjectRepository JPA 仓库
**And** webhook_secret 字段使用 AES 加密存储
**And** 项目配置缓存到 Redis（TTL 10 分钟）
**And** API 响应遵循标准格式（success、data、error、timestamp）
**And** 编写集成测试覆盖所有 CRUD 操作

---

### Story 1.6: 实现 AI 模型配置管理后端 API

**用户故事**：
作为系统管理员，
我想要通过 API 管理 AI 模型配置（提供商、模型参数、API 密钥），
以便配置多个 AI 提供商用于代码审查。

**验收标准**：

**Given** 项目配置 API 已实现
**When** 实现 AI 模型配置 API
**Then** 创建 `ai_model_config` 表：
- id、name、provider（OpenAI/Anthropic/CustomOpenAPI）
- model_name、api_key（加密）、api_endpoint
- timeout_seconds、max_tokens、temperature
- enabled、created_at、updated_at

**And** 实现 AIModelController REST API：
- POST /api/v1/ai-models（创建模型配置）
- GET /api/v1/ai-models（列出模型）
- GET /api/v1/ai-models/{id}（获取详情）
- PUT /api/v1/ai-models/{id}（更新配置）
- DELETE /api/v1/ai-models/{id}（删除配置）
- POST /api/v1/ai-models/{id}/test（测试连接）

**And** 实现 AIModelService 和 AIModelRepository
**And** api_key 字段使用 AES 加密存储
**And** 模型配置缓存到 Redis（TTL 10 分钟）
**And** 测试连接功能验证 API 可用性
**And** 编写集成测试覆盖所有操作

---

### Story 1.7: 实现 Prompt 模板管理后端 API

**用户故事**：
作为系统管理员，
我想要通过 API 管理 Prompt 模板（六维度审查 Prompt），
以便定制 AI 审查的提示词和输出格式。

**验收标准**：

**Given** AI 模型配置 API 已实现
**When** 实现 Prompt 模板 API
**Then** 创建 `prompt_template` 表：
- id、name、category（security/performance/maintainability/correctness/style/best_practices）
- template_content（Mustache/Velocity 格式）、version
- enabled、created_at、updated_at

**And** 实现 PromptTemplateController REST API：
- POST /api/v1/prompt-templates（创建模板）
- GET /api/v1/prompt-templates（列出模板）
- GET /api/v1/prompt-templates/{id}（获取详情）
- PUT /api/v1/prompt-templates/{id}（更新模板）
- DELETE /api/v1/prompt-templates/{id}（删除模板）
- POST /api/v1/prompt-templates/{id}/preview（预览渲染）

**And** 实现 PromptTemplateService 和 PromptTemplateRepository
**And** 模板内容验证（Mustache/Velocity 语法检查）
**And** 预览功能使用示例数据渲染模板
**And** 模板缓存到 Redis（TTL 10 分钟）
**And** 编写集成测试覆盖所有操作

---

### Story 1.8: 配置 Docker Compose 开发环境

**用户故事**：
作为开发者，
我想要使用 Docker Compose 启动完整的开发环境，
以便快速开始本地开发和测试。

**验收标准**：

**Given** 项目后端和前端已初始化
**When** 配置 Docker Compose
**Then** 创建 docker-compose.yml 文件包含服务：
- postgres（PostgreSQL 18.1，持久化卷）
- redis（Redis 8.0，持久化卷）
- backend（Spring Boot 应用，依赖 postgres 和 redis）
- frontend（Vue 开发服务器，依赖 backend）

**And** 配置健康检查（所有服务）
**And** 配置网络（backend 和 frontend 在同一网络）
**And** 配置环境变量文件（.env.example）
**And** 编写 README 说明启动步骤
**And** docker-compose up 可成功启动所有服务
**And** 前端可访问 http://localhost:5173
**And** 后端可访问 http://localhost:8080
**And** 数据库和 Redis 可正常连接

---
