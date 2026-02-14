# Epics and Stories: ai-code-review

**项目名称**: AI 智能代码审查系统
**生成日期**: 2026-02-05
**版本**: 1.0
**工作流**: BMAD Method - Create Epics and Stories

---

## 1. 需求摘要

### 1.1 功能需求 (Functional Requirements)

从 PRD 文档提取的所有功能需求：

- **FR 1.1: Webhook 接收与验证**
  - 支持 GitHub、GitLab、AWS CodeCommit 三个平台
  - Webhook 签名验证（HMAC-SHA256、Secret Token、AWS SigV4）
  - Payload 解析和事件类型识别（Push、PR/MR 创建/更新）
  - 安全性：签名验证必须在 payload 解析之前，防止注入攻击

- **FR 1.2: 任务管理**
  - 任务创建和优先级分配（PR/MR 高优先级，Push 普通优先级）
  - 任务队列管理（Redis Sorted Sets 实现优先级队列）
  - 任务状态跟踪（PENDING, RUNNING, COMPLETED, FAILED）
  - 重试机制（3 次重试，指数退避：1s、2s、4s）
  - 任务超时处理（单个审查任务 5 分钟超时）

- **FR 1.3: 代码获取与AI审查上下文组装**
  - Diff 元数据提取（变更文件、变更类型、统计信息）
  - 完整文件内容获取（通过 Git API）
  - 编程语言检测（基于文件扩展名）
  - AI 优化的上下文组装（原始 Diff + 文件内容 + 元数据）
  - 上下文窗口管理（大 Diff/文件截断策略，适配 AI token 限制）
  - 利用 AI 原生代码理解能力，无需自建解析器或调用链路分析

- **FR 1.4: AI 智能审查**
  - 六维度分析：
    1. 安全性问题（注入漏洞、认证/授权缺陷、敏感信息泄露）
    2. 性能问题（N+1 查询、循环低效、资源泄漏）
    3. 可维护性问题（代码重复、命名不当、过度复杂）
    4. 正确性问题（逻辑错误、边界条件、空指针）
    5. 代码风格问题（格式不一致、注释缺失）
    6. 最佳实践违反（设计模式误用、反模式）
  - AI 提供商抽象（策略模式 + 工厂模式）
  - 多 AI 提供商支持（OpenAI、Anthropic Claude、自定义 OpenAPI）
  - 降级策略（主模型 → 备用模型 → 错误）
  - 结构化输出（JSON 格式：severity, category, line, message, suggestion）

- **FR 1.5: 审查报告生成**
  - 结构化报告（问题列表 + 严重性 + 修复建议）
  - 调用链路图生成（Mermaid/PlantUML/D3.js JSON）
  - 问题统计（按严重性、类别分类）
  - 持久化存储（PostgreSQL JSONB 列）
  - 报告可视化支持（前端渲染图表和代码片段）

- **FR 1.6: 阈值拦截与 PR/MR 状态更新**
  - 阈值配置（按项目、严重性级别配置）
  - 阈值验证（Critical > N, High > M, 总问题数 > X）
  - PR/MR 状态更新（通过 Git 平台 API）
  - Check/Status 更新（GitHub Check Runs、GitLab Commit Status）
  - 拦截逻辑（超阈值时阻止合并，添加失败标签）

- **FR 1.7: 通知系统**
  - 邮件通知（审查完成通知 + 超阈值警告）
  - Git 平台评论（PR/MR Comment 显示审查摘要）
  - IM 集成（钉钉、Slack、飞书 Webhook）
  - 通知模板管理（支持 Mustache/Velocity 模板）
  - 通知规则配置（按项目、事件类型配置）

- **FR 1.8: 配置管理**
  - 项目配置（启用/禁用审查、关联 Git 仓库、Webhook 密钥）
  - AI 模型配置（提供商选择、模型参数、API 密钥）
  - Prompt 模板管理（六维度审查 Prompt 定制）
  - 阈值配置（按项目定制阈值规则）
  - 通知配置（邮件、IM、Git 评论开关）

- **FR 1.9: Web 管理界面**
  - 项目管理（创建、编辑、删除项目）
  - 审查历史查看（审查任务列表、详情、问题列表）
  - AI 模型配置界面（模型增删改查、API 密钥管理）
  - Prompt 模板编辑器（模板编辑、预览、版本管理）
  - 审查报告可视化（问题列表、调用链路图、统计图表）
  - 响应式布局（基于 Vue-Vben-Admin）

### 1.2 非功能需求 (Non-Functional Requirements)

从 PRD 文档提取的所有非功能需求：

- **NFR 1: 性能要求**
  - 审查速度：100 行代码 < 30 秒
  - Webhook 响应时间：< 500ms（仅验证 + 创建任务）
  - API 响应时间：列表查询 < 1s，详情查询 < 500ms
  - 并发处理：支持至少 10 个并发审查任务

- **NFR 2: 可靠性要求**
  - 系统可用性：99% uptime
  - 任务重试机制：3 次重试，指数退避
  - 错误恢复：任务失败后自动重试，超过阈值后标记失败并通知
  - 数据持久化：所有审查结果持久化存储

- **NFR 3: 安全性要求**
  - Webhook 签名验证：所有 Webhook 必须通过签名验证
  - HTTPS 传输：所有 API 通信使用 HTTPS
  - 敏感信息加密：API 密钥、Webhook 密钥加密存储
  - 认证授权：JWT 认证 + Spring Security 6
  - 防御时序攻击：签名比较使用常量时间算法

- **NFR 4: 可扩展性要求**
  - 水平扩展：API 层和 Worker 层支持多实例部署
  - 数据库扩展：PostgreSQL 读副本支持
  - 缓存策略：Redis 缓存项目配置、AI 模型配置
  - 存储扩展：支持 S3 兼容存储（用于 Diff 内容）

- **NFR 5: 可维护性要求**
  - 日志记录：结构化日志 + 关联 ID（SLF4J + Logback）
  - 监控指标：Micrometer + Prometheus（审查时间、队列长度、错误率）
  - 统一异常处理：全局异常处理器 + 标准错误响应格式
  - 代码规范：强制命名约定（见架构文档）

### 1.3 附加需求 (Additional Requirements)

从架构文档和产品简报提取的技术和上下文需求：

- **技术栈要求**
  - 后端：Java 17+、Spring Boot 3.x、Spring Data JPA、PostgreSQL 18.1、Redis 8.0
  - 前端：Vue 3、Vite、Vue-Vben-Admin、Element Plus、Pinia
  - 基础设施：Docker、Docker Compose
  - 构建工具：Maven（后端）、npm/pnpm（前端）

- **启动模板要求** ⚠️ **重要：影响 Epic 1 Story 1**
  - 后端：Spring Boot 多模块 Maven 项目结构
    - 模块：api、service、repository、integration、worker、common
    - 包名：com.aicodereview.*
    - 配置：application.yml + 环境配置文件
  - 前端：Vue-Vben-Admin 初始化
    - 目录结构：views/、components/、api/、stores/、router/、utils/、types/
    - 配置：vite.config.ts、tsconfig.json
    - 状态管理：Pinia
    - 路由：Vue Router

- **架构模式要求**
  - 后端分层架构：Controller → Service → Repository → Integration
  - AI 提供商抽象：策略模式 + 工厂模式
  - Webhook 验证：责任链模式
  - 异步处理：消息队列（Redis Queue）+ Worker 池
  - 错误处理：全局异常处理器 + 统一错误响应

- **命名约定要求（强制）**
  - 数据库：snake_case（表、列、索引）
  - Java：PascalCase（类）、camelCase（方法）、UPPER_SNAKE_CASE（常量）
  - REST API：/api/v1/{resources}、复数资源、camelCase 查询参数
  - Vue/TypeScript：PascalCase（组件）、camelCase（函数）

- **集成要求**
  - Git 平台集成：GitHub API、GitLab API、AWS CodeCommit API
  - AI 提供商集成：OpenAI API、Anthropic Claude API、自定义 OpenAPI
  - 通知集成：SMTP（邮件）、钉钉 Webhook、Slack Webhook、飞书 Webhook

- **数据流要求**
  - 核心流程：Webhook → 验证 → 创建任务(PENDING) → 入队 → Worker 获取(RUNNING) → AI 分析 → 存储结果(COMPLETED/FAILED) → 通知 → 更新 PR/MR 状态
  - 数据持久化：所有任务、审查结果、配置信息持久化到 PostgreSQL
  - 缓存策略：项目配置、AI 模型配置、Webhook 密钥缓存到 Redis

- **部署要求**
  - 容器化：Docker + Docker Compose
  - 环境隔离：开发、测试、生产环境独立配置
  - 日志聚合：统一日志输出格式，支持日志聚合工具
  - 健康检查：Spring Boot Actuator 健康检查端点

---

## 2. 需求覆盖映射

### 2.1 Epic 与需求追溯表

| Epic ID | Epic 名称 | 覆盖的功能需求 | 覆盖的非功能需求 | 覆盖的附加需求 |
|---------|-----------|----------------|------------------|----------------|
| Epic 1 | 项目基础设施与配置管理 | FR 1.8 | NFR 5 | 启动模板、技术栈、命名约定、部署要求 |
| Epic 2 | Webhook 集成与任务队列 | FR 1.1, FR 1.2 | NFR 1, NFR 2, NFR 3 | 架构模式（责任链、消息队列）、集成要求（Git 平台） |
| Epic 3 | 代码解析与上下文提取 | FR 1.3 | NFR 1 | 集成要求（Git API）、数据流要求 |
| Epic 4 | AI 智能审查引擎 | FR 1.4 | NFR 1, NFR 2, NFR 3 | 架构模式（策略模式）、集成要求（AI 提供商） |
| Epic 5 | 审查报告与结果存储 | FR 1.5 | NFR 2, NFR 5 | 数据流要求、数据持久化要求 |
| Epic 6 | 质量阈值与 PR/MR 拦截 | FR 1.6 | NFR 2 | 集成要求（Git 平台 API）、数据流要求 |
| Epic 7 | 多渠道通知系统 | FR 1.7 | NFR 2 | 集成要求（SMTP、IM Webhooks）、数据流要求 |
| Epic 8 | Web 管理界面 | FR 1.9 | NFR 1, NFR 4 | 技术栈（Vue 3、Vben-Admin）、命名约定 |

### 2.2 功能需求覆盖映射

| 需求编号 | 需求名称 | 对应 Epic | 简要说明 |
|---------|---------|----------|---------|
| FR 1.1 | Webhook 接收与验证 | Epic 2 | GitHub、GitLab、AWS CodeCommit 集成 |
| FR 1.2 | 任务管理 | Epic 2 | 任务创建、队列、重试机制 |
| FR 1.3 | 代码解析与上下文提取 | Epic 3 | Diff 解析、文件获取、调用链路分析 |
| FR 1.4 | AI 智能审查 | Epic 4 | 六维度分析、多 AI 提供商支持 |
| FR 1.5 | 审查报告生成 | Epic 5 | 结构化报告、可视化、持久化 |
| FR 1.6 | 阈值拦截与状态更新 | Epic 6 | 阈值验证、PR/MR 拦截 |
| FR 1.7 | 通知系统 | Epic 7 | 邮件、Git 评论、IM 通知 |
| FR 1.8 | 配置管理 | Epic 1 | 项目配置、AI 模型配置、模板管理 |
| FR 1.9 | Web 管理界面 | Epic 8 | 项目管理、审查历史、配置界面 |

---

## 3. Epic 列表

### Epic 1: 项目基础设施与配置管理

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

### Epic 2: Webhook 集成与任务队列

**用户价值**：Git 平台（GitHub、GitLab、AWS CodeCommit）可以通过 Webhook 触发代码审查任务，系统能够安全地接收、验证并可靠地排队处理这些任务。

**用户成果**：
- 接收并验证来自三个 Git 平台的 Webhook 请求
- 创建审查任务并分配优先级（PR/MR 高优先级，Push 普通优先级）
- 通过 Redis 优先级队列管理任务
- 实现任务重试机制和超时处理

**覆盖的功能需求**：FR 1.1（Webhook 接收与验证）、FR 1.2（任务管理）
**覆盖的非功能需求**：NFR 1（性能）、NFR 2（可靠性）、NFR 3（安全性）
**覆盖的附加需求**：架构模式（责任链、消息队列）、集成要求（Git 平台）

---

### Epic 3: 代码获取与AI审查上下文组装

**用户价值**：系统能够获取 Git 代码变更和完整文件内容，然后组装 AI 友好的审查上下文。利用 AI 的原生代码理解能力（Unified Diff 格式、代码结构、调用关系），无需自建复杂的代码解析器。

**用户成果**：
- 提取 Diff 元数据（变更文件、变更类型、统计信息）
- 通过 Git 平台 API 获取完整文件内容
- 组装 AI 优化的审查上下文（含上下文窗口管理）
- 基于文件扩展名自动检测编程语言

**覆盖的功能需求**：FR 1.3（代码获取与AI审查上下文组装）
**覆盖的非功能需求**：NFR 1（性能）
**覆盖的附加需求**：集成要求（Git API）、数据流要求

---

### Epic 4: AI 智能审查引擎

> **Epic 3 回顾决策（2026-02-14）**：从5个Story重构为4个Story。
> - 采用单次API调用覆盖六维度（非并发6次），大幅降低成本和复杂度
> - 合并原Story 4.2 (OpenAI) + 4.4 (Custom OpenAPI) 为统一的OpenAI兼容提供商
> - 使用原生 java.net.http.HttpClient（复用Story 3.2模式，不引入SDK）
> - 移除并发执行框架（CompletableFuture/Semaphore/ExecutorService）

**用户价值**：系统使用多种 AI 提供商（OpenAI兼容、Anthropic）对代码进行六维度智能审查，识别安全漏洞、性能问题、可维护性问题等。

**用户成果**：
- 实现 AI 提供商抽象层（策略模式 + 工厂模式）
- 集成 OpenAI 兼容提供商（官方 OpenAI + 自定义 endpoint）和 Anthropic Claude 提供商
- 单次 API 调用执行六维度代码分析（安全性、性能、可维护性、正确性、代码风格、最佳实践）
- 实现模型降级策略（主模型 → 备用模型 → 失败）
- 输出结构化审查结果（JSON 格式：severity, category, line, message, suggestion）

**覆盖的功能需求**：FR 1.4（AI 智能审查）
**覆盖的非功能需求**：NFR 1（性能）、NFR 2（可靠性）、NFR 3（安全性）
**覆盖的附加需求**：架构模式（策略模式）、集成要求（AI 提供商）

---

### Epic 5: 审查报告与结果存储

**用户价值**：开发者可以查看结构化的审查报告、可视化的调用链路图和问题统计，系统持久化存储所有审查结果供后续查询。

**用户成果**：
- 生成结构化审查报告（问题列表 + 严重性 + 修复建议）
- 生成调用链路图（Mermaid/PlantUML/D3.js JSON 格式）
- 统计问题分布（按严重性、类别分类）
- 持久化存储到 PostgreSQL（使用 JSONB 列）
- 提供报告查询 API

**覆盖的功能需求**：FR 1.5（审查报告生成）
**覆盖的非功能需求**：NFR 2（可靠性）、NFR 5（可维护性）
**覆盖的附加需求**：数据流要求、数据持久化要求

---

### Epic 6: 质量阈值与 PR/MR 拦截

**用户价值**：团队可以配置代码质量阈值，系统自动拦截不符合质量标准的 PR/MR，强制执行代码质量门禁。

**用户成果**：
- 配置项目级质量阈值（Critical > N, High > M, 总问题数 > X）
- 执行阈值验证逻辑
- 更新 PR/MR 状态（GitHub Check Runs、GitLab Commit Status）
- 超阈值时阻止合并并添加失败标签
- 提供阈值通过/失败详情

**覆盖的功能需求**：FR 1.6（阈值拦截与 PR/MR 状态更新）
**覆盖的非功能需求**：NFR 2（可靠性）
**覆盖的附加需求**：集成要求（Git 平台 API）、数据流要求

---

### Epic 7: 多渠道通知系统

**用户价值**：开发者通过邮件、Git 平台评论和 IM（钉钉、Slack、飞书）接收审查完成通知和超阈值警告，及时了解代码质量状况。

**用户成果**：
- 发送邮件通知（审查完成 + 超阈值警告）
- 在 PR/MR 中添加审查摘要评论
- 发送 IM 通知（钉钉、Slack、飞书 Webhook）
- 管理通知模板（Mustache/Velocity）
- 配置通知规则（按项目、事件类型）

**覆盖的功能需求**：FR 1.7（通知系统）
**覆盖的非功能需求**：NFR 2（可靠性）
**覆盖的附加需求**：集成要求（SMTP、IM Webhooks）、数据流要求

---

### Epic 8: Web 管理界面

**用户价值**：团队通过现代化的 Web 界面管理项目、查看审查历史、配置 AI 模型和模板，可视化审查报告和调用链路图。

**用户成果**：
- 实现项目管理界面（创建、编辑、删除、列表）
- 实现审查历史查看（任务列表、详情、问题列表）
- 实现 AI 模型配置界面（模型管理、API 密钥管理）
- 实现 Prompt 模板编辑器（编辑、预览、版本管理）
- 实现审查报告可视化（问题列表、调用链路图、统计图表）
- 基于 Vue-Vben-Admin 的响应式布局

**覆盖的功能需求**：FR 1.9（Web 管理界面）
**覆盖的非功能需求**：NFR 1（性能）、NFR 4（可扩展性）
**覆盖的附加需求**：技术栈（Vue 3、Vben-Admin）、命名约定

---

## 4. 详细 Stories

### Epic 1: 项目基础设施与配置管理 - Stories

#### Story 1.1: 从启动模板初始化 Spring Boot 多模块项目

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

#### Story 1.2: 从 Vue-Vben-Admin 模板初始化前端项目

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

#### Story 1.3: 配置 PostgreSQL 数据库连接与 JPA

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

#### Story 1.4: 配置 Redis 连接与缓存

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

#### Story 1.5: 实现项目配置管理后端 API

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

#### Story 1.6: 实现 AI 模型配置管理后端 API

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

#### Story 1.7: 实现 Prompt 模板管理后端 API

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

#### Story 1.8: 配置 Docker Compose 开发环境

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

### Epic 2: Webhook 集成与任务队列 - Stories

#### Story 2.1: 实现 Webhook 验证抽象层（责任链模式）

**用户故事**：
作为系统架构师，
我想要实现 Webhook 签名验证的抽象层，
以便支持多个 Git 平台的不同验证机制。

**验收标准**：

**Given** 项目基础设施已完成
**When** 实现 Webhook 验证抽象
**Then** 创建 WebhookVerifier 接口：
```java
interface WebhookVerifier {
    boolean verify(String payload, String signature, String secret);
    String getPlatform();
}
```

**And** 创建 WebhookVerificationChain 责任链管理器
**And** 实现常量时间字符串比较工具（防御时序攻击）
**And** 编写单元测试验证责任链模式
**And** 文档说明如何添加新的验证器实现

---

#### Story 2.2: 实现 GitHub Webhook 签名验证

**用户故事**：
作为系统，
我想要验证来自 GitHub 的 Webhook 签名，
以便确保请求的真实性和完整性。

**验收标准**：

**Given** Webhook 验证抽象层已实现
**When** 实现 GitHub 验证器
**Then** 创建 GitHubWebhookVerifier 实现 WebhookVerifier
**And** 使用 HMAC-SHA256 算法验证签名
**And** 从 X-Hub-Signature-256 header 提取签名
**And** 使用 webhook_secret 作为 HMAC 密钥
**And** 比较计算的签名与请求签名（常量时间）
**And** 编写单元测试使用 GitHub 示例数据
**And** 签名不匹配时抛出 WebhookVerificationException

---

#### Story 2.3: 实现 GitLab 和 AWS CodeCommit Webhook 验证

**用户故事**：
作为系统，
我想要验证来自 GitLab 和 AWS CodeCommit 的 Webhook 签名，
以便支持多个 Git 平台。

**验收标准**：

**Given** GitHub 验证器已实现
**When** 实现 GitLab 和 CodeCommit 验证器
**Then** 创建 GitLabWebhookVerifier：
- 从 X-Gitlab-Token header 提取 Secret Token
- 使用简单字符串比较验证（常量时间）

**And** 创建 AWSCodeCommitWebhookVerifier：
- 实现 AWS Signature Version 4 验证
- 从 Authorization header 解析签名
- 验证签名和时间戳

**And** 注册所有验证器到责任链
**And** 编写单元测试覆盖所有平台
**And** 文档说明各平台的签名机制

---

#### Story 2.4: 实现 Webhook 接收控制器

**用户故事**：
作为 Git 平台，
我想要通过 Webhook 发送 Push、PR/MR 事件到系统，
以便触发代码审查任务。

**验收标准**：

**Given** Webhook 验证器已实现
**When** 实现 Webhook 控制器
**Then** 创建 WebhookController：
- POST /api/v1/webhooks/github
- POST /api/v1/webhooks/gitlab
- POST /api/v1/webhooks/codecommit

**And** 签名验证在 payload 解析之前执行
**And** 解析事件类型（Push、Pull Request、Merge Request）
**And** 提取关键信息：
- 仓库 URL、分支、提交哈希
- PR/MR 编号、标题、描述
- 作者信息、文件变更列表

**And** 根据项目配置判断是否启用审查
**And** Webhook 响应时间 < 500ms（NFR 1）
**And** 返回 202 Accepted（异步处理）
**And** 签名验证失败返回 401 Unauthorized
**And** 编写集成测试模拟各平台 Webhook

---

#### Story 2.5: 实现审查任务创建与持久化

**用户故事**：
作为系统，
我想要创建审查任务并持久化到数据库，
以便跟踪任务状态和历史记录。

**验收标准**：

**Given** Webhook 控制器已实现
**When** 创建审查任务
**Then** 创建 `review_task` 表：
- id、project_id（外键）、task_type（PUSH/PR/MR）
- repo_url、branch、commit_hash、pr_number
- author、status（PENDING/RUNNING/COMPLETED/FAILED）
- priority（HIGH/NORMAL）、retry_count、max_retries
- created_at、started_at、completed_at、updated_at

**And** 实现 ReviewTaskService.createTask() 方法
**And** 实现 ReviewTaskRepository JPA 仓库
**And** PR/MR 任务优先级设为 HIGH，Push 任务设为 NORMAL
**And** 任务初始状态为 PENDING
**And** max_retries 默认为 3
**And** 创建任务后立即入队（调用队列服务）
**And** 编写单元测试验证任务创建逻辑

---

#### Story 2.6: 实现 Redis 优先级队列管理

**用户故事**：
作为系统，
我想要使用 Redis 实现优先级队列，
以便高优先级任务（PR/MR）优先处理。

**验收标准**：

**Given** Redis 已配置
**When** 实现队列管理服务
**Then** 创建 QueueService：
- enqueue(taskId, priority)：入队任务
- dequeue()：出队任务（按优先级）
- requeueWithDelay(taskId, delaySeconds)：延迟重新入队

**And** 使用 Redis Sorted Set 实现优先级队列：
- Key: review:queue
- Score: priority (HIGH=100, NORMAL=50) + timestamp
- Value: task_id

**And** 使用 Redis String 记录任务处理状态：
- Key: review:task:{task_id}:lock
- Value: worker_id
- TTL: 5 分钟（任务超时）

**And** dequeue() 使用分布式锁防止重复处理
**And** 编写单元测试验证队列操作
**And** 编写集成测试验证多实例并发场景

---

#### Story 2.7: 实现任务重试机制

**用户故事**：
作为系统，
我想要实现任务失败重试机制，
以便提高系统可靠性和审查成功率。

**验收标准**：

**Given** 队列服务已实现
**When** 任务处理失败
**Then** 判断失败类型：
- AI API 限流错误：指数退避重试
- 网络错误：立即重试
- 验证错误：不重试，标记失败

**And** 重试延迟计算：2^retry_count 秒（1s、2s、4s）
**And** 添加随机抖动（0-500ms）防止雷鸣群效应
**And** 更新任务 retry_count 字段
**And** retry_count >= max_retries 时标记任务 FAILED
**And** 重试任务重新入队（使用 requeueWithDelay）
**And** 记录每次重试的错误信息（日志）
**And** 编写单元测试模拟各种失败场景

---

### Epic 3: 代码获取与AI审查上下文组装 - Stories

#### Story 3.1: Diff 元数据提取与变更分析

**用户故事**：
作为系统，
我想要从原始 Diff 中提取基本元数据（变更文件、类型、统计），
以便了解变更范围，同时保留原始 Diff 供 AI 直接阅读。

**设计理念**：AI 模型原生理解 Unified Diff 格式，无需工程化解析 hunk 内容。
本 Story 只提取结构化元数据，原始 Diff 内容直接传给 AI。

**验收标准**：

**Given** 审查任务包含原始 Diff 内容
**When** 提取 Diff 元数据
**Then** 创建 DiffMetadataExtractor 类：
- extractMetadata(String rawDiff): DiffMetadata
- DiffMetadata 包含：List<FileDiffInfo>、DiffStatistics

**And** FileDiffInfo 包含：
- oldPath: String（变更前路径）
- newPath: String（变更后路径）
- changeType: ChangeType（ADD / MODIFY / DELETE / RENAME）
- language: Language（基于文件扩展名检测）
- isBinary: boolean

**And** DiffStatistics 包含：
- totalFilesChanged: int
- totalLinesAdded: int
- totalLinesDeleted: int

**And** 解析 diff header 提取文件路径：
- `--- a/file` / `+++ b/file` 提取 oldPath / newPath
- `new file mode` → ADD
- `deleted file mode` → DELETE
- `rename from/to` → RENAME
- 其他 → MODIFY

**And** 统计变更行数：
- 以 `+` 开头（非 `+++`）→ 添加行
- 以 `-` 开头（非 `---`）→ 删除行

**And** 基于文件扩展名检测编程语言：
- .java → JAVA, .py → PYTHON, .js → JAVASCRIPT, .ts → TYPESCRIPT
- .go → GO, .rs → RUST, .rb → RUBY, .php → PHP, .kt → KOTLIN
- .c/.cpp/.h → C/CPP, .cs → CSHARP, .swift → SWIFT
- .yml/.yaml → YAML, .json → JSON, .xml → XML, .md → MARKDOWN
- .sql → SQL, .sh → SHELL, .dockerfile/Dockerfile → DOCKERFILE
- 无法识别 → UNKNOWN

**And** 处理二进制文件标识（Binary files ... differ）
**And** 不解析 hunk 内容（@@ 行仅用于行数统计，不构建 Hunk 对象）
**And** Language 枚举定义在 common 模块
**And** ChangeType 枚举定义在 common 模块
**And** 编写单元测试：
- 测试各种 changeType 识别（ADD/MODIFY/DELETE/RENAME）
- 测试行数统计准确性
- 测试语言检测覆盖所有支持扩展名
- 测试二进制文件处理
- 测试边界情况（空 Diff、仅重命名无内容变更）
- 测试真实 Git Diff 样本

---

#### Story 3.2: Git 平台 API 客户端

**用户故事**：
作为系统，
我想要通过 Git 平台 API 获取完整文件内容和 Diff 内容，
以便为 AI 审查提供完整上下文。

**验收标准**：

**Given** 项目已配置 Git 平台信息和访问令牌
**When** 需要获取文件内容或 Diff
**Then** 创建 GitPlatformClient 接口：
- getFileContent(repoUrl, commitHash, filePath): String
- getDiff(repoUrl, commitHash): String（获取 commit 的完整 diff）
- getDiff(repoUrl, baseBranch, headBranch): String（获取 PR/MR 的 diff）
- getPlatform(): GitPlatform

**And** 实现 GitHubApiClient：
- 文件内容: GET /repos/{owner}/{repo}/contents/{path}?ref={sha}，解码 Base64
- Commit diff: GET /repos/{owner}/{repo}/commits/{sha}，Accept: application/vnd.github.diff
- PR diff: GET /repos/{owner}/{repo}/pulls/{number}，Accept: application/vnd.github.diff
- 认证: Bearer token（从 Project 配置或全局配置获取）

**And** 实现 GitLabApiClient：
- 文件内容: GET /api/v4/projects/{id}/repository/files/{path}/raw?ref={sha}
- Commit diff: GET /api/v4/projects/{id}/repository/commits/{sha}/diff
- MR diff: GET /api/v4/projects/{id}/merge_requests/{iid}/changes
- 认证: Private-Token header

**And** AWSCodeCommitClient 作为 Stub（TODO 标注）：
- 与 Story 2.3 webhook 验证保持一致的策略
- 接口方法抛出 UnsupportedOperationException("AWS CodeCommit support planned for future release")

**And** 使用工厂模式 GitPlatformClientFactory 根据 GitPlatform 选择客户端
**And** API 访问令牌配置：
- 扩展 Project 实体添加 accessToken 字段（加密存储）
- 或使用全局配置 application.yml（初期方案，Story 中选择一种实现）

**And** HTTP 超时配置（连接超时 5s，读取超时 10s）
**And** 瞬态失败重试（HTTP 429/5xx，最多 2 次重试，指数退避）
**And** 缓存文件内容到 Redis（TTL 5 分钟）：
- key 格式: `git:file:{platform}:{repoUrl}:{commitHash}:{filePath}`
- Diff 不缓存（每次获取最新）

**And** 编写单元测试使用 MockRestServiceServer 或 WireMock：
- 测试各平台 API 调用路径和参数
- 测试 Base64 解码（GitHub）
- 测试认证 header 正确设置
- 测试超时和重试行为
- 测试 Redis 缓存命中/未命中
- 测试错误处理（404 文件不存在、403 无权限、500 服务器错误）

---

#### Story 3.3: AI 审查上下文组装服务

**用户故事**：
作为系统，
我想要将 Diff 元数据、原始 Diff、完整文件内容组装成 AI 友好的 CodeContext，
以便 Epic 4 的 AI 审查引擎可以直接使用。

**设计理念**：本 Story 是 Epic 3 的编排层，协调前两个 Story 的能力，
组装出 AI 可直接消费的上下文。重点是上下文窗口管理——确保不超过 AI token 限制。

**验收标准**：

**Given** DiffMetadataExtractor 和 GitPlatformClient 已实现
**When** 为审查任务组装上下文
**Then** 创建 ReviewContextAssembler 服务：
- assembleContext(ReviewTask task): CodeContext

**And** 编排流程：
1. 通过 GitPlatformClient 获取原始 Diff（如果 task 中没有）
2. 调用 DiffMetadataExtractor 提取元数据
3. 对每个变更文件，通过 GitPlatformClient 获取完整文件内容
4. 组装 CodeContext 对象

**And** CodeContext 数据模型（定义在 common 模块）：
- rawDiff: String（原始 Unified Diff，AI 直接阅读）
- files: List<FileInfo>（文件元数据：path, changeType, language）
- fileContents: Map<String, String>（filePath → 完整文件内容）
- statistics: DiffStatistics（变更统计）
- taskMeta: TaskMetadata（PR title, description, author, branch — 从 ReviewTask 提取）

**And** 上下文窗口管理（防止超过 AI token 限制）：
- 可配置 maxContextTokens（默认 100,000 tokens，粗略估算 1 token ≈ 4 字符）
- 截断策略（按优先级顺序保留）：
  1. rawDiff（最重要，优先保留完整）
  2. 变更文件的完整内容（按变更行数降序）
  3. 未变更文件内容（如果还有空间）
- 如果 rawDiff 本身超过限制，截断尾部并添加 `[TRUNCATED: diff too large, showing first N files]`
- 单个文件超过 maxFileTokens（默认 10,000 tokens）时截断并标注

**And** 配置项（application.yml）：
```yaml
review:
  context:
    max-context-tokens: 100000
    max-file-tokens: 10000
    max-files: 50
```

**And** Flyway 数据库迁移：
- 添加 `code_context` JSONB 列到 `review_task` 表
- 迁移版本号按现有序列递增

**And** 组装完成后将 CodeContext 序列化为 JSON 存储到 review_task.code_context
**And** 处理异常情况：
- Git API 获取失败（某个文件）→ 跳过该文件，在 CodeContext 中标注
- 所有文件获取失败 → 仅使用 rawDiff（降级模式）
- 空 Diff → 返回空 CodeContext 并标注

**And** 编写单元测试：
- 测试完整编排流程（mock GitPlatformClient 和 DiffMetadataExtractor）
- 测试上下文截断逻辑（超大 Diff、超多文件）
- 测试降级场景（部分文件获取失败）
- 测试 CodeContext JSON 序列化/反序列化

**And** 编写集成测试：
- 测试 ReviewTask + CodeContext 的数据库存储和查询
- 测试 JSONB 列的正确存储

---

### Epic 4: AI 智能审查引擎 - Stories

> **重构说明（Epic 3 回顾决策 2026-02-14）**：
> - 原5个Story重构为4个Story
> - 原Story 4.2 (OpenAI) + 4.4 (Custom OpenAPI) 合并为新 Story 4.2（OpenAI兼容提供商）
> - 原Story 4.5 简化为新 Story 4.4（移除并发框架，采用单次API调用架构）
> - 技术栈：原生 java.net.http.HttpClient（复用Story 3.2模式），不引入AI SDK

#### Story 4.1: AI 提供商抽象层

**用户故事**：
作为系统架构师，
我想要实现 AI 提供商的抽象层，
以便支持多个 AI 提供商和灵活的路由策略。

**验收标准**：

**Given** 代码上下文提取服务已实现（Epic 3 CodeContext）
**When** 设计 AI 提供商抽象
**Then** 创建 AIProvider 接口：
```java
interface AIProvider {
    ReviewResult analyze(CodeContext context, PromptTemplate template);
    boolean isAvailable();
    String getProviderId();
    int getMaxTokens();
}
```

**And** 创建 AIProviderFactory 工厂类（复用 GitPlatformClientFactory 模式）：
- 通过 `List<AIProvider>` 构造器注入自动发现所有 `@Component` 实现
- `getProvider(String providerId): AIProvider`
- `getDefaultProvider(): AIProvider`（从配置获取默认提供商ID）

**And** 创建 ReviewResult 数据模型：
- `List<ReviewIssue> issues` — 审查发现的问题列表
- `ReviewMetadata metadata` — 模型信息、耗时、token 数、降级事件
- ReviewIssue 包含：`severity`（CRITICAL/HIGH/MEDIUM/LOW/INFO）、`category`（security/performance/maintainability/correctness/style/best_practices）、`filePath`、`line`（nullable）、`message`、`suggestion`

**And** 创建相关异常类：
- `AIProviderException`（基类）
- `RateLimitException`（429）
- `AIAuthenticationException`（401）
- `AITimeoutException`（超时）

**And** 编写单元测试验证工厂模式和数据模型

---

#### Story 4.2: OpenAI 兼容提供商（含自定义 Endpoint）

> **合并说明**：本Story合并了原Story 4.2 (OpenAI) 和原Story 4.4 (Custom OpenAPI)。
> 两者使用相同的 OpenAI Chat Completions API 格式，仅 baseUrl 不同。

**用户故事**：
作为系统，
我想要集成 OpenAI 兼容的 API 进行代码审查，
以便使用 GPT 模型或任何 OpenAI 兼容的私有部署模型分析代码。

**验收标准**：

**Given** AI 提供商抽象层已实现
**When** 实现 OpenAI 兼容提供商
**Then** 创建 OpenAICompatibleProvider 实现 AIProvider
**And** 使用原生 `java.net.http.HttpClient`（复用 Story 3.2 的 GitClientConfig HttpClient Bean）
**And** 支持可配置的 baseUrl：
- 官方 OpenAI：`https://api.openai.com`（默认）
- 自定义 endpoint：任意 OpenAI 兼容的 API 地址（如私有部署、Azure OpenAI等）

**And** 从 AIModelConfig 加载配置：
- API Key（`Authorization: Bearer {key}`）
- Base URL（默认 `https://api.openai.com`）
- Model Name（如 `gpt-4`、`gpt-4o`）
- Max Tokens、Temperature、Timeout

**And** 构建 Chat Completions 请求：
- `POST {baseUrl}/v1/chat/completions`
- Request Body：`messages`（system + user）、`model`、`max_tokens`、`temperature`
- System Message：六维度审查指令（从 PromptTemplate 渲染）
- User Message：CodeContext 内容（rawDiff + 文件列表 + 统计 + taskMeta）
- Response Format：要求 JSON 输出（`response_format: { type: "json_object" }`）

**And** 解析 JSON 响应：
- 提取 `choices[0].message.content`
- 解析内容为结构化 ReviewIssue 列表
- 提取 `usage.prompt_tokens`、`usage.completion_tokens` 到 metadata

**And** 处理 API 错误：
- 429 Rate Limit → 抛出 `RateLimitException`
- 401 Unauthorized → 抛出 `AIAuthenticationException`
- 408/504 Timeout → 抛出 `AITimeoutException`
- 500/502/503 Server Error → 抛出 `AIProviderException`
- 网络超时 `HttpTimeoutException` → 包装为 `AITimeoutException`

**And** 实现重试逻辑（复用 Story 3.2 模式）：
- 429/5xx：最多2次重试，指数退避（1s, 2s）
- 401/403/404：不重试
- 网络超时：不自动重试（交给上层降级策略）

**And** 记录 API 调用指标（耗时、token 数、模型名称）
**And** 编写单元测试（Mock HttpClient 响应）：
- 测试请求 URL 和 Header 构建（官方 + 自定义 endpoint）
- 测试成功响应解析（JSON → ReviewResult）
- 测试各种错误码处理
- 测试重试逻辑
- 测试空 token / 无效配置处理

---

#### Story 4.3: Anthropic Claude 提供商

**用户故事**：
作为系统，
我想要集成 Anthropic Claude API 进行代码审查，
以便使用 Claude 模型分析代码。

**验收标准**：

**Given** OpenAI 兼容提供商已实现
**When** 实现 Anthropic 提供商
**Then** 创建 AnthropicProvider 实现 AIProvider
**And** 使用原生 `java.net.http.HttpClient`
**And** 从 AIModelConfig 加载配置：
- API Key（`x-api-key: {key}` header）
- Model Name（如 `claude-sonnet-4-5-20250929`）
- Max Tokens、Temperature、Timeout
- Anthropic API Version（`anthropic-version: 2023-06-01`）

**And** 构建 Messages API 请求：
- `POST https://api.anthropic.com/v1/messages`
- Headers：`x-api-key`、`anthropic-version`、`content-type: application/json`
- Request Body：`model`、`max_tokens`、`system`（审查指令）、`messages`（user message）
- User Message：CodeContext 内容

**And** 解析 JSON 响应：
- 提取 `content[0].text`
- 解析内容为结构化 ReviewIssue 列表
- 提取 `usage.input_tokens`、`usage.output_tokens` 到 metadata

**And** 处理 API 错误（与 OpenAI 提供商相同的异常层次）
**And** 实现重试逻辑（同 OpenAI 提供商模式）
**And** 记录 API 调用指标
**And** 编写单元测试（Mock HttpClient 响应）：
- 测试 Anthropic 特有的 Header 格式（x-api-key vs Authorization: Bearer）
- 测试 Messages API 请求/响应格式
- 测试错误处理和重试

---

#### Story 4.4: 审查编排与 Prompt 管理与降级策略

> **简化说明**：原Story 4.5采用6维度并发执行（CompletableFuture + Semaphore）。
> 基于 Epic 3 回顾决策，改为**单次 API 调用覆盖全部六维度**。
> AI 模型完全有能力在一次调用中完成多维度分析，大幅降低成本和复杂度。

**用户故事**：
作为系统，
我想要编排代码审查流程、管理 Prompt 渲染并实现降级策略，
以便可靠地完成代码审查。

**验收标准**：

**Given** 所有 AI 提供商已实现
**When** 执行代码审查
**Then** 创建 ReviewOrchestrator 服务：
- `review(ReviewTask task): ReviewResult`
- 六维度：security、performance、maintainability、correctness、style、best_practices

**And** 实现单次调用审查流程：
1. 通过 `ReviewContextAssembler.assembleContext(task)` 获取 CodeContext
2. 从项目配置加载 AI 模型选择（主模型 + 备用模型）
3. 从 PromptTemplate 加载审查模板
4. 渲染 Prompt（注入 CodeContext 内容：rawDiff、files、statistics、taskMeta）
5. 调用 AIProvider.analyze(codeContext, renderedTemplate)
6. 解析响应为 ReviewResult（包含六维度的全部 issue）
7. 持久化结果

**And** 实现 Prompt 渲染：
- 简单字符串替换：`{{rawDiff}}`、`{{files}}`、`{{statistics}}`、`{{taskMeta}}`
- Prompt 模板要求 AI 输出结构化 JSON，包含 `issues` 数组
- 每个 issue 必须包含 `category`（六维度之一）、`severity`、`message`、`suggestion`

**And** 实现模型降级策略（简化版，无并发）：

**Level 0: 主 AI 模型（如 GPT-4 / Claude Sonnet 4.5）**
- 429 Rate Limit → 指数退避重试（1s, 2s, 4s，最多 3 次）
- 503 Service Unavailable → 指数退避重试（1s, 2s，最多 2 次）
- Timeout → 重试 1 次
- 401 Authentication Failed → 不重试，标记失败，记录错误
- 所有重试失败 → 降级到 Level 1

**Level 1: 备用 AI 模型（如 GPT-4o-mini / Claude Haiku）**
- 相同的重试策略
- 所有重试失败 → 降级到 Level 2

**Level 2: 完全失败**
- 标记任务为 FAILED
- 记录详细错误日志（包含所有降级尝试记录）
- ReviewResult 标记为失败，包含降级事件链

**And** 记录降级事件到 ReviewMetadata：
- 使用的模型（主模型 or 备用模型）
- 降级原因和次数
- 每次 API 调用耗时和 token 数

**And** 性能验收标准：
- 100 行代码：单次 API 调用 < 15 秒
- 500 行代码：单次 API 调用 < 30 秒
- 1000 行代码：单次 API 调用 < 45 秒

**And** 监控指标（Micrometer）：
- `review.total.latency` — 总审查时间
- `review.model.used` — 使用的模型（标签）
- `ai.degradation.count` — 降级次数
- `review.success.rate` — 审查成功率

**And** 编写单元测试（Mock AIProvider）：
- 测试正常场景（单次调用成功）
- 测试主模型 429 → 重试成功
- 测试主模型全部失败 → 降级到备用模型成功
- 测试所有模型失败 → FAILED 状态
- 测试 401 认证错误 → 不重试直接失败
- 测试 Prompt 渲染（模板变量替换正确性）
- 测试 ReviewResult 结构完整性

---

### Epic 5: 审查报告与结果存储 - Stories

#### Story 5.1: 实现审查结果持久化存储

**用户故事**：
作为系统，
我想要持久化审查结果到数据库，
以便后续查询和展示。

**验收标准**：

**Given** AI 审查引擎已实现
**When** 审查完成
**Then** 创建 `review_result` 表：
- id、task_id（外键，关联 review_task）
- issues（JSONB，存储问题列表）
- call_graph（JSONB，存储调用链路图）
- statistics（JSONB，问题统计）
- review_duration_ms、total_tokens
- created_at

**And** issues JSONB 结构：
```json
[
  {
    "severity": "CRITICAL",
    "category": "security",
    "line": 42,
    "file": "UserService.java",
    "message": "SQL injection vulnerability",
    "suggestion": "Use PreparedStatement instead"
  }
]
```

**And** statistics JSONB 结构：
```json
{
  "total": 15,
  "by_severity": {"CRITICAL": 2, "HIGH": 5, "MEDIUM": 8},
  "by_category": {"security": 3, "performance": 4, "maintainability": 8}
}
```

**And** 实现 ReviewResultRepository JPA 仓库
**And** 实现 ReviewResultService.saveResult() 方法
**And** 更新 review_task 状态为 COMPLETED
**And** 编写单元测试验证存储逻辑

---

#### Story 5.2: 实现审查报告生成服务

**用户故事**：
作为开发者，
我想要生成结构化的审查报告，
以便清晰地了解代码问题和改进建议。

**验收标准**：

**Given** 审查结果已持久化
**When** 生成审查报告
**Then** 创建 ReportGenerator 服务：
- generateReport(ReviewResult result): Report
- Report 包含：
  - summary（总问题数、严重性分布）
  - issuesByFile（按文件分组的问题列表）
  - issuesByCategory（按类别分组）
  - callGraph（Mermaid 格式调用链路图）
  - recommendations（修复建议列表）

**And** 按严重性排序问题（CRITICAL → HIGH → MEDIUM → LOW）
**And** 生成 Markdown 格式报告
**And** 生成 HTML 格式报告（用于邮件）
**And** 生成 JSON 格式报告（用于 API）
**And** 编写单元测试验证报告生成逻辑

---

#### Story 5.3: 实现调用链路图可视化格式转换

**用户故事**：
作为前端开发者，
我想要将调用链路图转换为可视化格式，
以便在 Web 界面展示。

**验收标准**：

**Given** 调用链路分析已完成
**When** 需要可视化调用图
**Then** 创建 CallGraphRenderer 服务：
- toMermaid(CallGraph graph): String
- toPlantUML(CallGraph graph): String
- toD3Json(CallGraph graph): String（D3.js 格式）

**And** Mermaid 格式：
```
graph TD
  A[UserService.createUser] --> B[UserRepository.save]
  A --> C[EmailService.sendWelcomeEmail]
```

**And** PlantUML 格式：
```
@startuml
UserService.createUser -> UserRepository.save
UserService.createUser -> EmailService.sendWelcomeEmail
@enduml
```

**And** D3.js JSON 格式：
```json
{
  "nodes": [{"id": "UserService.createUser"}, ...],
  "links": [{"source": 0, "target": 1}, ...]
}
```

**And** 编写单元测试验证所有格式转换

---

#### Story 5.4: 实现审查结果查询 API

**用户故事**：
作为前端开发者，
我想要通过 API 查询审查结果，
以便在 Web 界面展示。

**验收标准**：

**Given** 审查结果已存储
**When** 查询审查结果
**Then** 创建 ReviewResultController REST API：
- GET /api/v1/reviews/{taskId}/result（获取审查结果）
- GET /api/v1/reviews/{taskId}/report（获取报告，支持 format=markdown|html|json）
- GET /api/v1/reviews/{taskId}/call-graph（获取调用图，支持 format=mermaid|plantuml|d3）
- GET /api/v1/reviews（列出审查历史，支持分页、过滤、排序）

**And** 列表 API 支持过滤：
- projectId（按项目）
- status（按状态）
- dateRange（按日期范围）

**And** 列表 API 支持排序：
- created_at DESC（默认）
- total_issues DESC

**And** API 响应时间 < 500ms（NFR 1）
**And** 编写集成测试覆盖所有端点

---

### Epic 6: 质量阈值与 PR/MR 拦截 - Stories

#### Story 6.1: 实现阈值配置管理

**用户故事**：
作为系统管理员，
我想要为项目配置质量阈值，
以便定义代码合并的质量门禁。

**验收标准**：

**Given** 审查结果查询 API 已实现
**When** 配置质量阈值
**Then** 在 `project` 表添加 `thresholds` JSONB 列：
```json
{
  "enabled": true,
  "rules": [
    {"severity": "CRITICAL", "max_count": 0},
    {"severity": "HIGH", "max_count": 3},
    {"total_issues": 20}
  ],
  "action": "BLOCK_MERGE"
}
```

**And** 更新 ProjectController API：
- PUT /api/v1/projects/{id}/thresholds（更新阈值配置）
- GET /api/v1/projects/{id}/thresholds（获取阈值配置）

**And** 阈值配置验证（max_count >= 0）
**And** 阈值配置缓存到 Redis
**And** 编写单元测试验证配置逻辑

---

#### Story 6.2: 实现阈值验证引擎

**用户故事**：
作为系统，
我想要根据阈值配置验证审查结果，
以便判断是否应该拦截 PR/MR。

**验收标准**：

**Given** 阈值配置已实现
**When** 审查完成后验证阈值
**Then** 创建 ThresholdValidator 服务：
- validate(ReviewResult result, ThresholdConfig config): ValidationResult
- ValidationResult 包含：passed、violations、action

**And** 验证逻辑：
- 检查 CRITICAL 问题数 <= max_count
- 检查 HIGH 问题数 <= max_count
- 检查总问题数 <= total_issues

**And** 任何规则违反时 passed = false
**And** 记录所有违反的规则到 violations
**And** 根据配置返回 action（BLOCK_MERGE/WARN_ONLY）
**And** 编写单元测试覆盖各种场景

---

#### Story 6.3: 实现 GitHub Check Runs 状态更新

**用户故事**：
作为系统，
我想要更新 GitHub PR 的 Check Run 状态，
以便在 PR 页面显示审查结果。

**验收标准**：

**Given** 阈值验证已实现
**When** 审查完成且项目为 GitHub
**Then** 创建 GitHubCheckRunService：
- createCheckRun(taskId, repoUrl, commitHash): CheckRun
- updateCheckRun(checkRunId, conclusion, output): void

**And** 使用 GitHub API：
- POST /repos/{owner}/{repo}/check-runs
- PATCH /repos/{owner}/{repo}/check-runs/{check_run_id}

**And** Check Run 参数：
- name: "AI Code Review"
- status: "in_progress" → "completed"
- conclusion: "success" / "failure" / "neutral"
- output: title, summary, text（审查摘要）

**And** 阈值通过时 conclusion = "success"
**And** 阈值失败时 conclusion = "failure"
**And** output 包含问题统计和严重问题列表
**And** 编写单元测试使用 Mock GitHub API

---

#### Story 6.4: 实现 GitLab Commit Status 和 AWS CodeCommit 状态更新

**用户故事**：
作为系统，
我想要更新 GitLab MR 和 AWS CodeCommit PR 的状态，
以便在各平台显示审查结果。

**验收标准**：

**Given** GitHub Check Runs 已实现
**When** 审查完成且项目为 GitLab 或 CodeCommit
**Then** 创建 GitLabCommitStatusService：
- updateCommitStatus(projectId, commitSha, state, description): void
- 使用 GitLab API：
  - POST /api/v4/projects/{id}/statuses/{sha}
  - state: "success" / "failed" / "pending"

**And** 创建 AWSCodeCommitStatusService：
- updatePullRequestApprovalState(prId, revisionId, approvalState): void
- 使用 AWS SDK：
  - codecommit.updatePullRequestApprovalState()

**And** 阈值通过时设置成功状态
**And** 阈值失败时设置失败状态
**And** 状态描述包含问题统计
**And** 编写单元测试使用 Mock API

---

### Epic 7: 多渠道通知系统 - Stories

#### Story 7.1: 实现邮件通知服务

**用户故事**：
作为开发者，
我想要通过邮件接收审查完成通知，
以便及时了解代码审查结果。

**验收标准**：

**Given** 审查结果和阈值验证已完成
**When** 发送邮件通知
**Then** 创建 `notification_config` 表：
- id、project_id（外键）
- email_enabled、email_recipients（逗号分隔）
- smtp_host、smtp_port、smtp_username、smtp_password（加密）
- email_template_success、email_template_failure

**And** 创建 EmailNotificationService：
- sendReviewCompleteEmail(task, result, validation): void
- sendThresholdViolationEmail(task, result, violations): void

**And** 集成 Spring Mail（JavaMailSender）
**And** 使用 Thymeleaf 渲染邮件模板
**And** 邮件内容包含：
- 项目名称、分支、提交哈希
- 审查摘要（总问题数、严重性分布）
- 阈值验证结果
- 审查详情链接

**And** HTML 格式邮件（带样式）
**And** SMTP 连接失败时记录错误但不阻塞
**And** 编写单元测试使用 Mock SMTP

---

#### Story 7.2: 实现 Git 平台评论通知

**用户故事**：
作为开发者，
我想要在 PR/MR 中看到审查摘要评论，
以便在代码审查界面直接查看结果。

**验收标准**：

**Given** 邮件通知已实现
**When** 审查完成且任务类型为 PR/MR
**Then** 创建 GitCommentService：
- postReviewComment(task, result, validation): void

**And** 实现 GitHub 评论：
- POST /repos/{owner}/{repo}/issues/{number}/comments
- 评论格式：Markdown 表格 + 问题列表

**And** 实现 GitLab 评论：
- POST /api/v4/projects/{id}/merge_requests/{iid}/notes

**And** 实现 AWS CodeCommit 评论：
- codecommit.postCommentForPullRequest()

**And** 评论内容包含：
- 🤖 AI Code Review 标识
- 审查摘要表格（问题统计）
- 严重问题列表（TOP 5）
- 完整报告链接
- 阈值验证结果（✅ 通过 / ❌ 失败）

**And** 编写单元测试使用 Mock API

---

#### Story 7.3: 实现 IM Webhook 通知（钉钉、Slack、飞书）

**用户故事**：
作为团队，
我想要通过 IM（钉钉、Slack、飞书）接收审查警告，
以便团队及时知晓代码质量问题。

**验收标准**：

**Given** Git 平台评论已实现
**When** 审查超阈值时发送 IM 通知
**Then** 在 `notification_config` 表添加字段：
- dingtalk_enabled、dingtalk_webhook_url、dingtalk_secret
- slack_enabled、slack_webhook_url
- lark_enabled、lark_webhook_url

**And** 创建 IMNotificationService：
- sendDingTalkNotification(task, result, violations): void
- sendSlackNotification(task, result, violations): void
- sendLarkNotification(task, result, violations): void

**And** 钉钉通知使用 Markdown 格式：
```json
{
  "msgtype": "markdown",
  "markdown": {
    "title": "代码审查警告",
    "text": "### 项目: xxx\n- Critical: 2\n- High: 5\n..."
  }
}
```

**And** Slack 通知使用 Blocks 格式
**And** 飞书通知使用富文本格式
**And** 仅超阈值时发送 IM 通知（避免打扰）
**And** Webhook 调用失败时记录错误但不阻塞
**And** 编写单元测试使用 Mock Webhook

---

#### Story 7.4: 实现通知模板管理

**用户故事**：
作为系统管理员，
我想要管理通知模板，
以便定制通知内容和格式。

**验收标准**：

**Given** 所有通知渠道已实现
**When** 管理通知模板
**Then** 创建 `notification_template` 表：
- id、name、channel（EMAIL/GIT_COMMENT/DINGTALK/SLACK/LARK）
- template_content（Mustache/Velocity 格式）
- variables（JSONB，可用变量说明）
- enabled、created_at、updated_at

**And** 实现 NotificationTemplateController REST API：
- POST /api/v1/notification-templates（创建模板）
- GET /api/v1/notification-templates（列出模板）
- PUT /api/v1/notification-templates/{id}（更新模板）
- POST /api/v1/notification-templates/{id}/preview（预览渲染）

**And** 模板变量包括：
- project_name、branch、commit_hash
- total_issues、critical_count、high_count
- threshold_passed、violations
- review_url

**And** 模板渲染使用 Mustache 引擎
**And** 模板缓存到 Redis
**And** 编写单元测试验证模板渲染

---

### Epic 8: Web 管理界面 - Stories

#### Story 8.1: 实现项目管理界面

**用户故事**：
作为系统管理员，
我想要通过 Web 界面管理项目，
以便可视化地配置和查看项目列表。

**验收标准**：

**Given** 项目配置后端 API 已实现（Epic 1）
**When** 访问项目管理界面
**Then** 创建 Vue 组件：
- src/views/project/ProjectList.vue（项目列表）
- src/views/project/ProjectDetail.vue（项目详情）
- src/views/project/ProjectForm.vue（创建/编辑表单）

**And** ProjectList 功能：
- 表格展示项目（名称、Git 平台、仓库 URL、状态）
- 搜索过滤（按名称、平台）
- 操作按钮（编辑、删除、启用/禁用）
- 新建项目按钮

**And** ProjectForm 功能：
- 表单字段：名称、描述、Git 平台、仓库 URL、Webhook 密钥
- 表单验证（必填字段、URL 格式）
- 提交到后端 API

**And** ProjectDetail 功能：
- 展示项目完整信息
- Webhook URL 显示和复制
- 配置标签页（阈值、通知）

**And** 基于 Element Plus 组件库
**And** 响应式布局（移动端适配）

---

#### Story 8.2: 实现审查历史查看界面

**用户故事**：
作为开发者，
我想要查看项目的审查历史，
以便了解代码质量趋势。

**验收标准**：

**Given** 审查结果查询 API 已实现（Epic 5）
**When** 访问审查历史界面
**Then** 创建 Vue 组件：
- src/views/review/ReviewHistory.vue（审查历史列表）
- src/views/review/ReviewDetail.vue（审查详情）

**And** ReviewHistory 功能：
- 表格展示审查任务（时间、分支、提交、状态、问题数）
- 过滤：按项目、状态、日期范围
- 排序：按时间、问题数
- 分页（每页 20 条）
- 状态徽章（PENDING/RUNNING/COMPLETED/FAILED）

**And** ReviewDetail 功能（详细 UX 交互规范见 ux-design-specification.md）：

**审查摘要卡片**:
- 显示总问题数、严重性分布（饼图）
- 显示审查耗时、代码行数、文件数
- 阈值验证结果：✅ 通过（绿色） / ❌ 失败（红色）
- 快速筛选按钮：仅显示 Critical、仅显示 High、显示全部

**问题列表（核心 UX 目标：3 秒内定位关键问题）**:
- 默认按严重性排序（Critical → High → Medium → Low → Info）
- 问题卡片布局（而非表格）：
  ```
  [🔴 Critical] Security - SQL Injection              Line 42 ▶
  在 UserService.java 的查询中检测到 SQL 注入漏洞...
  [展开详情] [查看代码] [标记已处理]
  ```

- 虚拟滚动优化（性能要求）：
  - 仅渲染可见区域的问题（使用 vue-virtual-scroller）
  - 目标: 1000 个问题渲染时间 < 3 秒
  - 懒加载代码片段（点击"查看代码"时才加载）

- 智能分组和筛选：
  - 按文件分组：`UserService.java (5 issues) ▶`
  - 按类别分组：`Security (10 issues) ▶`
  - 按严重性分组：`Critical (3 issues) ▶`
  - 搜索框：实时过滤（按关键词、文件名、行号）

- 交互细节：
  - 点击问题卡片 → 展开详细说明和修复建议
  - 点击行号 → 跳转到代码片段（高亮显示问题行）
  - 点击"标记已处理" → 问题置灰，从"待处理"计数中移除
  - 支持键盘导航：J/K 上下切换问题，Enter 展开/收起

**代码片段展示（核心 UX 目标：理解问题上下文）**:
- 语法高亮（使用 Prism.js 或 Highlight.js）
- 显示上下文（问题行 ± 5 行）
- 问题行高亮标记（红色背景 + 波浪下划线）
- 行号显示（点击复制行号）
- 复制代码按钮
- 差异视图：Before（旧代码）vs After（新代码）

**调用链路图展示**:
- Mermaid 图表渲染（mermaid.js）
- 或使用 D3.js 交互式图表（可拖拽、缩放）
- 节点点击 → 显示方法详情（参数、返回值）
- 高亮变更节点（红色边框）
- 图表导出功能（PNG / SVG）

**统计图表可视化**:
- 饼图：按严重性分布（ECharts）
- 柱状图：按类别分布（Security、Performance、Maintainability 等）
- 趋势图：历史审查统计（最近 30 天问题数趋势）
- 热力图：问题分布热点文件

**加载状态和错误处理（核心 UX 原则：信息透明）**:
- 骨架屏加载（Skeleton Loading）：
  ```
  ████████████ 85% ████████░░
  正在加载审查详情...（预计还需 2 秒）
  ```

- 部分加载策略：
  1. 优先加载摘要卡片（Critical 问题数）
  2. 然后加载问题列表（前 20 个）
  3. 最后加载调用链路图（可选，异步加载）

- 错误处理：
  - 审查失败 → 显示错误原因和重试按钮
  - 部分维度失败 → 显示警告"仅显示 4/6 维度结果"
  - 调用图生成失败 → 显示占位消息"调用图不可用"

**性能优化**:
- 问题列表分页加载（每页 50 个，滚动触发加载下一页）
- 代码片段懒加载（默认不加载，点击时才请求）
- 图表按需渲染（切换到"统计"Tab 时才渲染）
- API 响应缓存（5 分钟 TTL）

**响应式设计**:
- 桌面端（> 1024px）：三栏布局（侧边栏 + 问题列表 + 详情）
- 平板端（768-1024px）：两栏布局（问题列表 + 详情）
- 移动端（< 768px）：单栏布局（列表视图 → 点击 → 详情视图）

**可访问性（A11y）**:
- 支持键盘导航（Tab, Enter, Escape）
- ARIA 标签完整（role, aria-label, aria-describedby）
- 高对比度模式支持
- 屏幕阅读器友好

**And** 使用 Vue Router 路由：
- `/reviews` → ReviewHistory.vue
- `/reviews/:id` → ReviewDetail.vue
- `/reviews/:id/issues/:issueId` → 问题详情（深度链接）

**And** 使用 Pinia 状态管理：
```typescript
// stores/review.ts
export const useReviewStore = defineStore('review', {
  state: () => ({
    currentReview: null,
    issues: [],
    filters: { severity: 'all', category: 'all' },
    loading: false,
    error: null
  }),
  actions: {
    async fetchReviewDetail(id: string) {
      this.loading = true;
      try {
        this.currentReview = await api.getReviewDetail(id);
        this.issues = this.currentReview.issues;
      } catch (error) {
        this.error = error.message;
      } finally {
        this.loading = false;
      }
    },
    filterIssues(filters) {
      this.filters = filters;
      // Apply filters to issues
    }
  }
});
```

**And** 用户测试验收标准：
- ✅ 用户能在 3 秒内看到首屏关键问题（Critical 级别）
- ✅ 用户能在 10 秒内理解问题本质（查看代码上下文）
- ✅ 用户能在 30 秒内知道如何修复（查看修复建议）
- ✅ 1000 个问题的页面加载时间 < 3 秒（虚拟滚动）
- ✅ 用户满意度 ≥ 4.5/5（用户反馈调研）

---

#### Story 8.3: 实现 AI 模型配置界面

**用户故事**：
作为系统管理员，
我想要通过 Web 界面管理 AI 模型配置，
以便可视化地添加和配置 AI 提供商。

**验收标准**：

**Given** AI 模型配置后端 API 已实现（Epic 1）
**When** 访问 AI 模型配置界面
**Then** 创建 Vue 组件：
- src/views/model/AIModelList.vue（模型列表）
- src/views/model/AIModelForm.vue（创建/编辑表单）

**And** AIModelList 功能：
- 表格展示模型（名称、提供商、模型名称、状态）
- 操作按钮（编辑、删除、测试连接）
- 新建模型按钮
- 状态开关（启用/禁用）

**And** AIModelForm 功能：
- 表单字段：
  - 名称、提供商（下拉选择：OpenAI/Anthropic/CustomOpenAPI）
  - 模型名称、API Endpoint（自定义提供商）
  - API Key（密码输入）
  - 参数：Timeout、Max Tokens、Temperature
- 表单验证
- 测试连接按钮（调用测试 API）

**And** 测试连接显示结果（成功/失败 + 响应时间）
**And** API Key 显示为密文（***）

---

#### Story 8.4: 实现 Prompt 模板编辑器

**用户故事**：
作为系统管理员，
我想要通过 Web 界面编辑 Prompt 模板，
以便定制 AI 审查的提示词。

**验收标准**：

**Given** Prompt 模板后端 API 已实现（Epic 1）
**When** 访问 Prompt 模板编辑器
**Then** 创建 Vue 组件：
- src/views/template/TemplateList.vue（模板列表）
- src/views/template/TemplateEditor.vue（模板编辑器）

**And** TemplateList 功能：
- 表格展示模板（名称、类别、版本、状态）
- 按类别过滤（六维度）
- 操作按钮（编辑、删除、复制）

**And** TemplateEditor 功能：
- 代码编辑器（Monaco Editor 或 CodeMirror）
- 语法高亮（Mustache）
- 模板变量提示（自动补全）
- 实时预览（右侧分栏）
- 保存和版本管理
- 恢复到默认模板按钮

**And** 预览功能：
- 使用示例数据渲染模板
- 显示渲染后的 Prompt 内容

**And** 模板变量文档说明

---

#### Story 8.5: 实现审查报告可视化组件

**用户故事**：
作为开发者，
我想要在 Web 界面看到可视化的审查报告，
以便更直观地理解代码问题。

**验收标准**：

**Given** 审查详情界面已实现（Story 8.2）
**When** 查看审查详情
**Then** 创建可视化组件：
- src/components/review/IssueList.vue（问题列表）
- src/components/review/CodeSnippet.vue（代码片段）
- src/components/chart/CallGraphChart.vue（调用链路图）
- src/components/chart/StatisticsChart.vue（统计图表）

**And** IssueList 功能：
- 问题卡片布局
- 严重性徽章（Critical/High/Medium/Low）
- 代码行号链接（点击跳转到代码）
- 修复建议展开/收起

**And** CodeSnippet 功能：
- 代码语法高亮（Prism.js 或 Highlight.js）
- 行号显示
- 问题行高亮标记
- 复制代码按钮

**And** CallGraphChart 功能：
- Mermaid 图表渲染（mermaid.js）
- 或 D3.js 交互式图表
- 节点点击查看详情
- 图表缩放和拖拽

**And** StatisticsChart 功能：
- 饼图（按严重性分布）- ECharts
- 柱状图（按类别分布）- ECharts
- 趋势图（历史审查统计）

---

#### Story 8.6: 实现用户认证与授权（JWT）

**用户故事**：
作为系统管理员，
我想要实现用户认证和授权，
以便保护 Web 界面和 API。

**验收标准**：

**Given** Web 界面已实现
**When** 实现认证授权
**Then** 创建 `user` 表：
- id、username、password_hash、email
- role（ADMIN/USER）、enabled、created_at

**And** 后端实现 Spring Security 6 配置：
- JWT Token 生成和验证
- 登录端点：POST /api/v1/auth/login
- 注册端点：POST /api/v1/auth/register（仅管理员）
- 刷新端点：POST /api/v1/auth/refresh

**And** 前端实现认证逻辑：
- 登录页面（src/views/auth/Login.vue）
- Token 存储（localStorage）
- Axios 拦截器（添加 Authorization header）
- 路由守卫（未登录跳转登录页）

**And** API 权限控制：
- ADMIN 角色：所有操作
- USER 角色：仅查看（不能删除、修改配置）

**And** Token 过期时间：24 小时
**And** Refresh Token 有效期：7 天
**And** 编写集成测试验证认证流程

---

## Epic 9: 端到端测试与集成验证

**Epic 描述**: 构建完整的端到端测试套件，验证从 Webhook 触发到审查报告生成的全流程，确保系统各模块集成正确、流程可靠。

**业务价值**: 提升系统质量，减少生产环境故障，提供回归测试保障。

**前置条件**: Epic 1-8 核心功能已完成

**Story 列表**:
- Story 9.1: E2E 测试框架搭建
- Story 9.2: Webhook 到审查流程 E2E 测试
- Story 9.3: 多平台集成 E2E 测试
- Story 9.4: AI 审查质量验证测试
- Story 9.5: Web 界面 E2E 测试
- Story 9.6: CI/CD 集成与回归测试
- Story 9.7: 错误场景与边界测试

---

#### Story 9.1: E2E 测试框架搭建

**用户故事**:
作为 QA 工程师，
我想要建立完整的 E2E 测试框架，
以便自动化验证系统端到端功能。

**验收标准**:

**Given** 系统后端和前端已部署
**When** 搭建测试框架
**Then** 后端 E2E 测试框架：
- 使用 Spring Boot Test + TestContainers
- 启动真实 PostgreSQL 和 Redis 容器
- 配置测试数据初始化脚本

**And** 前端 E2E 测试框架：
- 使用 Playwright 或 Cypress
- 配置测试浏览器（Chromium, Firefox）
- 实现 Page Object Model 模式

**And** 创建测试工具类：
- MockWebhookServer（模拟 GitHub/GitLab/CodeCommit webhook）
- MockAIProvider（模拟 AI API 响应，避免真实 API 费用）
- TestDataFactory（生成测试数据：项目、配置、审查结果）
- AssertionHelper（自定义断言：数据库状态、队列状态）

**And** Docker Compose 测试环境：
```yaml
version: '3.8'
services:
  postgres-test:
    image: postgres:16
  redis-test:
    image: redis:7
  app-test:
    build: .
    environment:
      - SPRING_PROFILES_ACTIVE=test
```

**And** 编写示例测试用例验证框架可用

**技术要点**:
- TestContainers 自动启动/停止容器
- 测试数据库独立于开发环境
- 每个测试类使用独立数据库 Schema（隔离）

---

#### Story 9.2: Webhook 到审查流程 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试从 Webhook 触发到审查完成的全流程，
以便验证核心业务流程正确性。

**验收标准**:

**Given** E2E 测试框架已搭建（Story 9.1）
**When** 执行核心流程测试
**Then** 创建测试用例：

**Test Case 1: GitHub Push 事件 → 审查完成**
```java
@Test
public void testGitHubPushToReviewComplete() {
    // 1. 创建项目配置
    Project project = createProject("test-repo", GitPlatform.GITHUB);

    // 2. 发送 GitHub Push webhook
    mockGitHubWebhook.sendPushEvent(project, "main", "abc123");

    // 3. 验证任务创建
    await().atMost(5, SECONDS).until(() ->
        taskRepository.findByCommitSha("abc123").isPresent()
    );

    ReviewTask task = taskRepository.findByCommitSha("abc123").get();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

    // 4. 等待任务处理（Worker 消费队列）
    await().atMost(60, SECONDS).until(() ->
        task.getStatus() == TaskStatus.COMPLETED
    );

    // 5. 验证审查结果
    ReviewResult result = resultRepository.findByTaskId(task.getId()).get();
    assertThat(result.getDimensions()).hasSize(6);
    assertThat(result.getTotalIssues()).isGreaterThan(0);

    // 6. 验证通知已发送
    verify(emailService, times(1)).sendReviewCompleteEmail(any());
}
```

**Test Case 2: GitLab MR 事件 → 审查 → 阈值拦截**
```java
@Test
public void testGitLabMRWithThresholdViolation() {
    // 1. 创建项目配置（设置严格阈值：Critical > 0 即拦截）
    Project project = createProject("test-repo", GitPlatform.GITLAB);
    project.setThresholdConfig(new ThresholdConfig(0, 0, 10));

    // 2. 发送 GitLab MR webhook（包含严重问题的代码）
    mockGitLabWebhook.sendMREvent(project, "feature-branch", codeWithSQLInjection);

    // 3. 等待审查完成
    await().atMost(60, SECONDS).until(() ->
        reviewIsComplete(taskId)
    );

    // 4. 验证阈值验证失败
    ThresholdValidation validation = validationRepository.findByTaskId(taskId).get();
    assertThat(validation.isPassed()).isFalse();
    assertThat(validation.getViolations()).contains("Critical: 1 > 0");

    // 5. 验证 GitLab MR 状态更新为失败
    verify(gitLabClient, times(1))
        .updateMRStatus(anyString(), eq(CommitStatus.FAILED), contains("阈值拦截"));
}
```

**Test Case 3: AWS CodeCommit PR → 完整流程**
- 测试 AWS SigV4 签名验证
- 测试 GetDifferences API 调用
- 验证审查完成

**And** 所有测试在隔离环境运行（使用 Mock AI 响应）
**And** 测试覆盖高优先级（PR/MR）和普通优先级（Push）
**And** 每个测试独立可重复运行

---

#### Story 9.3: 多平台集成 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试所有 Git 平台集成的兼容性，
以便确保多平台支持无回归。

**验收标准**:

**Given** Webhook 流程测试已完成（Story 9.2）
**When** 测试多平台集成
**Then** 创建参数化测试：

```java
@ParameterizedTest
@EnumSource(GitPlatform.class)
public void testWebhookVerificationForAllPlatforms(GitPlatform platform) {
    // 测试每个平台的签名验证
    WebhookPayload payload = generateWebhookPayload(platform);

    ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
        "/api/v1/webhooks/" + platform.name().toLowerCase(),
        payload,
        WebhookResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().isSuccess()).isTrue();
}
```

**And** 测试每个平台的特定功能：
- GitHub: Check Runs API 状态更新
- GitLab: Commit Status API 更新
- AWS CodeCommit: Pull Request Comments

**And** 测试差异获取一致性：
- 验证所有平台返回统一的 DiffResult 格式
- 测试 Add/Modify/Delete 三种变更类型

**And** 测试通知集成：
- GitHub: PR Comment 格式正确
- GitLab: MR Note 格式正确
- AWS CodeCommit: PR Comment 使用 SDK API

**And** 边界测试：
- 测试无效签名（返回 401）
- 测试不支持的事件类型（忽略）
- 测试大型 Payload（> 1MB）

---

#### Story 9.4: AI 审查质量验证测试

**用户故事**:
作为 AI 工程师，
我想要验证 AI 审查的准确性和一致性，
以便确保审查质量达标。

**验收标准**:

**Given** 多平台集成测试已完成（Story 9.3）
**When** 验证 AI 审查质量
**Then** 创建已知漏洞测试集：

**Test Dataset 1: 安全漏洞代码（10 个样本）**
```java
// 样本 1: SQL 注入
String query = "SELECT * FROM users WHERE id = " + userId;

// 样本 2: XSS 漏洞
response.getWriter().write("<div>" + userInput + "</div>");

// 样本 3: 硬编码密钥
String apiKey = "sk-1234567890abcdef";

// ... 7 个其他漏洞
```

**Expected Results**:
- 所有 10 个漏洞被检测到（Recall = 100%）
- Severity 正确标记为 "error"
- Category 正确分类（sql-injection, xss, sensitive-data）

**Test Dataset 2: 性能问题代码（10 个样本）**
- N+1 查询、O(n²) 算法、资源泄漏等
- Expected Recall ≥ 90%

**Test Dataset 3: 正常代码（无问题）**
- 验证误报率 ≤ 15%

**And** 实现自动化评分系统：
```java
@Test
public void testAIReviewPrecisionAndRecall() {
    List<TestCase> testCases = loadKnownVulnerabilities();

    int truePositives = 0;
    int falsePositives = 0;
    int falseNegatives = 0;

    for (TestCase testCase : testCases) {
        ReviewResult result = conductReview(testCase.getCode());
        List<Issue> detected = result.getIssues();

        // 比对检测结果与预期结果
        truePositives += countTruePositives(detected, testCase.getExpected());
        falsePositives += countFalsePositives(detected, testCase.getExpected());
        falseNegatives += countFalseNegatives(detected, testCase.getExpected());
    }

    double precision = (double) truePositives / (truePositives + falsePositives);
    double recall = (double) truePositives / (truePositives + falseNegatives);

    assertThat(precision).isGreaterThanOrEqualTo(0.85);  // ≥ 85%
    assertThat(recall).isGreaterThanOrEqualTo(0.90);     // ≥ 90%
}
```

**And** 测试 Prompt 模板质量：
- 测试每个维度的 Prompt 模板
- 验证输出 JSON 格式可解析（100%）

**And** 测试降级策略：
- 模拟主 AI 模型失败（返回 429）
- 验证自动切换到备用模型
- 验证最终返回有效结果

---

#### Story 9.5: Web 界面 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试 Web 界面的完整用户流程，
以便确保 UI 功能正常、交互流畅。

**验收标准**:

**Given** AI 审查质量测试已完成（Story 9.4）
**When** 测试 Web 界面
**Then** 使用 Playwright 创建测试：

**Test Case 1: 用户登录流程**
```typescript
test('user login and navigate to dashboard', async ({ page }) => {
  await page.goto('http://localhost:3000/login');

  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'password123');
  await page.click('button[type="submit"]');

  await expect(page).toHaveURL('/dashboard');
  await expect(page.locator('h1')).toContainText('Dashboard');
});
```

**Test Case 2: 创建项目流程**
```typescript
test('create new project', async ({ page }) => {
  await loginAs('admin', page);
  await page.click('button:has-text("新建项目")');

  await page.fill('input[name="name"]', 'Test Project');
  await page.selectOption('select[name="gitPlatform"]', 'GITHUB');
  await page.fill('input[name="repoUrl"]', 'https://github.com/user/repo');
  await page.click('button:has-text("保存")');

  await expect(page.locator('.message')).toContainText('项目创建成功');
});
```

**Test Case 3: 查看审查详情**
```typescript
test('view review details', async ({ page }) => {
  await loginAs('admin', page);
  await page.goto('/reviews');

  await page.click('tr:first-child a:has-text("查看详情")');

  await expect(page.locator('h2')).toContainText('审查详情');
  await expect(page.locator('.issue-card')).toHaveCount(5);
  await expect(page.locator('.call-graph-chart')).toBeVisible();
});
```

**Test Case 4: 配置 AI 模型**
- 测试添加 OpenAI 配置
- 测试 API 密钥加密存储
- 测试连接测试功能

**Test Case 5: 审查报告可视化**
- 测试问题列表加载
- 测试代码高亮显示
- 测试调用链路图渲染
- 测试统计图表交互

**And** 测试响应式布局（桌面、平板、手机）
**And** 测试深色/浅色主题切换
**And** 测试键盘导航（可访问性）

---

#### Story 9.6: CI/CD 集成与回归测试

**用户故事**:
作为 DevOps 工程师，
我想要将 E2E 测试集成到 CI/CD 流程，
以便每次代码提交自动运行回归测试。

**验收标准**:

**Given** Web 界面测试已完成（Story 9.5）
**When** 集成 CI/CD
**Then** 创建 GitHub Actions 工作流：

```yaml
name: E2E Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  e2e-backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
      redis:
        image: redis:7

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Run Backend E2E Tests
        run: mvn test -Pintegration-test

      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        with:
          name: backend-test-reports
          path: target/surefire-reports

  e2e-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3

      - name: Install Playwright
        run: pnpm install && pnpm exec playwright install

      - name: Run Frontend E2E Tests
        run: pnpm run test:e2e

      - name: Upload Screenshots
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-screenshots
          path: test-results
```

**And** 配置测试报告：
- JUnit XML 格式（后端）
- Playwright HTML 报告（前端）
- 测试覆盖率报告（JaCoCo + Istanbul）

**And** 失败通知机制：
- CI 失败时发送 Slack 通知
- 包含失败测试用例列表和日志链接

**And** 性能监控：
- 记录测试执行时间趋势
- 测试套件总时间 < 10 分钟（目标）

---

#### Story 9.7: 错误场景与边界测试

**用户故事**:
作为 QA 工程师，
我想要测试系统的错误处理和边界条件，
以便确保系统在异常情况下也能稳定运行。

**验收标准**:

**Given** CI/CD 集成已完成（Story 9.6）
**When** 测试错误场景
**Then** 创建错误场景测试：

**Test Case 1: 数据库连接失败**
```java
@Test
public void testDatabaseConnectionFailure() {
    // 停止数据库容器
    postgresContainer.stop();

    // 尝试创建项目
    ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
        "/api/v1/projects",
        projectRequest,
        ApiResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getError()).contains("Database connection failed");

    // 恢复数据库
    postgresContainer.start();
}
```

**Test Case 2: Redis 队列不可用**
- 模拟 Redis 宕机
- 验证任务创建失败并返回友好错误
- 验证系统不崩溃

**Test Case 3: AI API 限流**
- 模拟 AI API 返回 429 错误
- 验证重试机制触发（指数退避）
- 验证最终切换到备用模型

**Test Case 4: 超大代码变更（> 5000 行）**
- 测试系统能否处理大型 diff
- 验证性能降级策略（跳过调用图分析）

**Test Case 5: 恶意 Webhook Payload**
- 测试无效签名（返回 401）
- 测试超大 Payload（> 10MB，返回 413）
- 测试 SQL 注入尝试（参数化查询防御）

**Test Case 6: 并发冲突**
- 测试 100 个并发 Webhook 请求
- 验证队列深度不超过限制
- 验证无重复任务创建（分布式锁）

**And** 边界值测试：
- 空字符串、null 值、空数组
- 超长字符串（> 10000 字符）
- 特殊字符（SQL 注入、XSS）

**And** 性能边界测试：
- 1000 个项目配置（系统不卡顿）
- 10000 个历史审查记录（列表查询 < 1s）

**技术要点**:
- 使用 Chaos Engineering 工具（Toxiproxy）模拟网络故障
- 使用 WireMock 模拟外部 API 错误响应

---

## Epic 10: 性能测试与优化

**Epic 描述**: 对系统进行全面的性能测试，验证 NFR 性能要求，识别性能瓶颈并优化，确保系统可在生产环境稳定运行。

**业务价值**: 确保系统满足性能 SLA，提升用户体验，降低资源成本。

**前置条件**: Epic 1-8 核心功能已完成

**Story 列表**:
- Story 10.1: 性能测试基准数据集准备
- Story 10.2: 单次审查性能测试
- Story 10.3: 并发任务性能测试
- Story 10.4: Webhook 响应时间测试
- Story 10.5: 数据库查询性能优化
- Story 10.6: API 响应时间优化
- Story 10.7: 前端加载性能优化

---

#### Story 10.1: 性能测试基准数据集准备

**用户故事**:
作为性能工程师，
我想要准备标准化的性能测试数据集，
以便可重复、可对比地进行性能测试。

**验收标准**:

**Given** 系统功能已完成
**When** 准备测试数据
**Then** 创建代码规模测试集：

**Dataset 1: 小型变更（100 行）**
- 文件数量: 1-2
- 变更类型: 单个方法修改
- 编程语言: Java
- 审查维度: 全部 6 个
- 预期审查时间: < 10s

**Dataset 2: 中型变更（500 行）**
- 文件数量: 5-8
- 变更类型: 功能模块开发
- 编程语言: Java
- 预期审查时间: < 20s

**Dataset 3: 大型变更（1000 行）**
- 文件数量: 10-15
- 变更类型: 大型重构
- 预期审查时间: < 30s（NFR 要求）

**Dataset 4: 超大变更（5000 行）**
- 文件数量: 30-50
- 变更类型: 整个模块重写
- 预期审查时间: < 60s（降级策略）

**And** 创建复杂度测试集：
- **简单代码**: CRUD 操作，直线逻辑，无复杂调用
- **中等复杂**: Service 层业务逻辑，3-5 层调用深度
- **高复杂**: 复杂算法，10+ 层调用深度，设计模式

**And** 创建问题密度测试集：
- **无问题代码**: 高质量代码，预期 0-2 个 Info 级别建议
- **少量问题**: 5-10 个 Warning
- **大量问题**: 20+ 个问题（包括 Critical/High）

**And** 数据集存储在 Git 仓库：
```
performance-test-data/
├── small/
│   ├── 100-lines-crud.diff
│   ├── 100-lines-service.diff
│   └── expected-issues.json
├── medium/
│   └── ...
├── large/
│   └── ...
└── xlarge/
    └── ...
```

**And** 创建数据加载脚本自动导入

---

#### Story 10.2: 单次审查性能测试

**用户故事**:
作为性能工程师，
我想要测试单次审查的端到端性能，
以便验证 NFR 要求"单次审查 < 30s per 100 lines"。

**验收标准**:

**Given** 性能测试数据集已准备（Story 10.1）
**When** 执行单次审查性能测试
**Then** 使用 JMeter 或 Gatling 创建测试脚本：

**Test Scenario 1: 100 行代码审查**
```scala
scenario("Single Review - 100 lines")
  .exec(http("Trigger Webhook")
    .post("/api/v1/webhooks/github")
    .body(StringBody(webhook100Lines))
    .header("X-Hub-Signature-256", signature)
    .check(status.is(202))
  )
  .pause(1)
  .exec(http("Wait for Completion")
    .get("/api/v1/tasks/${taskId}")
    .check(jsonPath("$.status").is("COMPLETED"))
    .check(responseTimeInMillis.lte(10000))  // < 10s
  )
```

**Performance Targets (P95)**:
| Code Size | Total Time | Database | AI API | Call Graph | Pass? |
|-----------|------------|----------|--------|------------|-------|
| 100 lines | < 10s | < 500ms | < 8s | < 1s | ✅ Target |
| 500 lines | < 20s | < 1s | < 15s | < 3s | ✅ Target |
| 1000 lines | < 30s | < 1.5s | < 23s | < 5s | ✅ NFR Met |
| 5000 lines | < 60s | < 3s | < 50s | < 7s (or skip) | ⚠️ Degraded |

**And** 测量各阶段耗时占比：
```
Total Review Time (100 lines): 10s
├── Webhook Processing: 0.5s (5%)
├── Diff Retrieval: 1s (10%)
├── Code Parsing: 0.5s (5%)
├── Call Graph Analysis: 1s (10%)
├── AI Review (6 dimensions): 6s (60%)
│   ├── Security: 1s
│   ├── Performance: 1s
│   ├── Maintainability: 1s
│   ├── Correctness: 1s
│   ├── Style: 1s
│   └── Best Practices: 1s
└── Report Generation: 1s (10%)
```

**Key Insights**:
- AI API 调用占 60% 时间（主要瓶颈）
- 六维度并行执行（3 并发）可将 AI 时间从 18s 降至 6s

**And** 测试降级策略性能：
- 模拟主 AI 模型超时
- 验证切换到备用模型后总时间 < 40s

**And** 生成性能报告：
- 响应时间分布图（p50, p95, p99）
- 各阶段耗时饼图
- 瓶颈识别和优化建议

---

#### Story 10.3: 并发任务性能测试

**用户故事**:
作为性能工程师，
我想要测试系统的并发处理能力，
以便验证 NFR 要求"并发任务 ≥ 10"。

**验收标准**:

**Given** 单次审查性能测试已完成（Story 10.2）
**When** 执行并发性能测试
**Then** 使用 JMeter 创建并发测试计划：

**Test Scenario 1: 10 并发任务（NFR 要求）**
```xml
<ThreadGroup>
  <numThreads>10</numThreads>
  <rampUpPeriod>5</rampUpPeriod>  <!-- 5秒内启动10个线程 -->
  <loopCount>1</loopCount>
</ThreadGroup>
```

**Performance Targets**:
| Concurrency | Queue Depth | Throughput | Avg Response Time | p95 Response Time | Error Rate |
|-------------|-------------|------------|-------------------|-------------------|------------|
| 10 tasks | < 10 | ≥ 10 tasks/min | < 35s | < 40s | < 1% |
| 50 tasks | < 50 | ≥ 10 tasks/min | < 60s | < 120s | < 5% |
| 100 tasks | < 100 | ≥ 10 tasks/min | < 180s | < 300s | < 10% |

**And** 测试 Redis 队列性能：
- 监控队列深度变化（每秒采样）
- 监控入队/出队延迟（p95 < 100ms）
- 验证无任务丢失（入队数 = 出队数 + 队列中数）

**And** 测试 Worker 线程池利用率：
- 10 个 Worker 线程应保持 90%+ 利用率
- 无线程饥饿或死锁

**And** 测试数据库连接池：
- 连接池大小: 20（HikariCP 默认）
- 监控连接获取等待时间（< 50ms）
- 验证无连接泄漏

**And** 测试 AI API 限流保护：
- 配置 Semaphore（max 3 concurrent per dimension）
- 验证并发 AI 调用不超过 18（6 dimensions × 3）
- 验证无 429 Rate Limit 错误

**And** 负载测试场景：
```
Phase 1: Ramp-up (0-5 min)
- 从 0 增加到 50 并发任务

Phase 2: Steady State (5-15 min)
- 保持 50 并发任务

Phase 3: Peak Load (15-20 min)
- 增加到 100 并发任务

Phase 4: Cool Down (20-25 min)
- 降至 10 并发任务
```

**And** 生成性能报告：
- 吞吐量时间序列图
- 响应时间百分位数图
- 队列深度热力图
- 资源使用率（CPU, Memory, Disk I/O）

---

#### Story 10.4: Webhook 响应时间测试

**用户故事**:
作为性能工程师，
我想要测试 Webhook 端点的响应时间，
以便验证 NFR 要求"Webhook 响应 < 1s"。

**验收标准**:

**Given** 并发任务测试已完成（Story 10.3）
**When** 测试 Webhook 响应时间
**Then** 创建专项测试：

**Test Scenario 1: Webhook 端点响应时间（1000 请求）**
```bash
# 使用 Apache Bench
ab -n 1000 -c 10 -p webhook-payload.json \
   -T application/json \
   -H "X-Hub-Signature-256: sha256=..." \
   http://localhost:8080/api/v1/webhooks/github
```

**Performance Targets (NFR)**:
| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| p50 Response Time | < 500ms | TBD | - |
| p95 Response Time | < 1000ms | TBD | - |
| p99 Response Time | < 1500ms | TBD | - |
| Throughput | ≥ 50 req/s | TBD | - |
| Error Rate | < 1% | TBD | - |

**And** 测试各阶段耗时：
```
Webhook Request Processing (target < 1s):
├── Signature Verification: < 50ms
├── Payload Parsing: < 100ms
├── Task Creation: < 200ms
├── Queue Enqueue: < 50ms
└── Response Generation: < 50ms
```

**And** 优化建议（如不达标）：
1. **Signature Verification 优化**:
   - 缓存 Webhook Secret（避免每次从数据库读取）
   - 使用高效的 HMAC 库（Apache Commons Codec）

2. **Database Write 优化**:
   - 使用批量插入（Batch Insert）
   - 异步写入（先返回 202，后台持久化）

3. **Queue Enqueue 优化**:
   - Redis 管道（Pipeline）批量操作
   - 减少网络往返次数

**And** 压力测试：
- 测试 1000 req/s 高并发（模拟 DDoS）
- 验证系统不崩溃，返回 429 限流响应

---

#### Story 10.5: 数据库查询性能优化

**用户故事**:
作为性能工程师，
我想要优化数据库查询性能，
以便确保 API 响应时间符合 NFR。

**验收标准**:

**Given** Webhook 响应时间测试已完成（Story 10.4）
**When** 优化数据库查询
**Then** 使用 pg_stat_statements 分析慢查询：

**Slow Query Identification**:
```sql
-- 查找平均执行时间 > 100ms 的查询
SELECT
  query,
  calls,
  mean_exec_time,
  max_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC
LIMIT 20;
```

**Common Slow Queries to Optimize**:

**Query 1: 审查历史列表（无索引）**
```sql
-- BEFORE (slow: ~500ms for 10k records)
SELECT * FROM review_tasks
WHERE project_id = ?
ORDER BY created_at DESC
LIMIT 20;

-- AFTER: Add index
CREATE INDEX idx_review_tasks_project_created
ON review_tasks(project_id, created_at DESC);

-- Result: 500ms → 50ms (10x improvement)
```

**Query 2: 问题统计聚合（全表扫描）**
```sql
-- BEFORE (slow: ~1s for 100k issues)
SELECT
  severity,
  COUNT(*) as count
FROM review_issues
WHERE task_id = ?
GROUP BY severity;

-- AFTER: Add covering index
CREATE INDEX idx_review_issues_task_severity
ON review_issues(task_id, severity);

-- Result: 1s → 100ms (10x improvement)
```

**Query 3: 项目配置查询（JOIN 优化）**
```sql
-- BEFORE: Multiple queries (N+1)
SELECT * FROM projects WHERE id = ?;
SELECT * FROM ai_model_configs WHERE project_id = ?;
SELECT * FROM threshold_configs WHERE project_id = ?;

-- AFTER: Single query with JOIN
SELECT p.*, amc.*, tc.*
FROM projects p
LEFT JOIN ai_model_configs amc ON p.id = amc.project_id
LEFT JOIN threshold_configs tc ON p.id = tc.project_id
WHERE p.id = ?;

-- Result: 3 queries (150ms) → 1 query (50ms)
```

**And** 创建所有必要的索引：
```sql
-- review_tasks 表索引
CREATE INDEX idx_review_tasks_status ON review_tasks(status);
CREATE INDEX idx_review_tasks_commit_sha ON review_tasks(commit_sha);

-- review_results 表索引
CREATE INDEX idx_review_results_task ON review_results(task_id);

-- review_issues 表索引
CREATE INDEX idx_review_issues_result ON review_issues(result_id);
CREATE INDEX idx_review_issues_severity ON review_issues(severity);

-- projects 表索引
CREATE INDEX idx_projects_enabled ON projects(enabled);
```

**And** 配置数据库连接池优化：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**And** 使用 EXPLAIN ANALYZE 验证查询计划：
```sql
EXPLAIN ANALYZE
SELECT * FROM review_tasks
WHERE project_id = 1
ORDER BY created_at DESC
LIMIT 20;

-- Expected: Index Scan on idx_review_tasks_project_created
-- Avoid: Seq Scan (full table scan)
```

**And** 性能测试验证：
- 所有 API 列表查询 p95 < 1s ✅
- 详情查询 p95 < 500ms ✅
- 复杂聚合查询 p95 < 2s ✅

---

#### Story 10.6: API 响应时间优化

**用户故事**:
作为性能工程师，
我想要优化 REST API 响应时间，
以便提升用户体验。

**验收标准**:

**Given** 数据库查询已优化（Story 10.5）
**When** 优化 API 响应时间
**Then** 实现缓存策略：

**Cache Strategy 1: Redis 缓存项目配置**
```java
@Service
public class ProjectService {

    @Cacheable(value = "projects", key = "#id", unless = "#result == null")
    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    @CacheEvict(value = "projects", key = "#project.id")
    public Project updateProject(Project project) {
        return projectRepository.save(project);
    }
}
```

**Cache Configuration**:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 minutes
      cache-null-values: false
```

**Expected Improvement**:
- Without cache: 50ms (database query)
- With cache: 5ms (Redis GET)
- 10x speedup for frequently accessed projects

**Cache Strategy 2: ETag for Conditional Requests**
```java
@GetMapping("/api/v1/tasks/{id}")
public ResponseEntity<ReviewTask> getTask(@PathVariable Long id,
                                          @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    ReviewTask task = taskService.getById(id);
    String etag = generateETag(task);

    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    return ResponseEntity.ok()
        .eTag(etag)
        .body(task);
}
```

**Expected Improvement**:
- 304 Not Modified response: < 10ms (no payload transfer)
- 200 OK with data: ~100ms
- Saves bandwidth for unchanged resources

**And** 实现 API 分页优化：
```java
// BEFORE: 加载所有数据到内存
List<ReviewTask> tasks = repository.findAll();  // 10k records = 50MB

// AFTER: 分页查询
Page<ReviewTask> tasks = repository.findAll(PageRequest.of(page, size));  // 20 records = 100KB
```

**And** 实现 GZIP 压缩：
```yaml
server:
  compression:
    enabled: true
    min-response-size: 1024  # 1KB
    mime-types: application/json,application/xml,text/html,text/plain
```

**Expected Improvement**:
- JSON response size: 100KB → 20KB (5x reduction)
- Transfer time (1 Mbps): 800ms → 160ms

**And** 异步 API 优化（长时间操作）：
```java
// BEFORE: 同步审查（阻塞 30s）
@PostMapping("/api/v1/reviews/start")
public ReviewResult startReview(@RequestBody ReviewRequest request) {
    return reviewService.conductReview(request);  // 30s blocking
}

// AFTER: 异步审查（立即返回）
@PostMapping("/api/v1/reviews/start")
public ResponseEntity<ReviewTaskResponse> startReview(@RequestBody ReviewRequest request) {
    ReviewTask task = reviewService.createTask(request);  // 100ms
    reviewService.conductReviewAsync(task);  // 异步执行

    return ResponseEntity.accepted()
        .body(new ReviewTaskResponse(task.getId(), "PENDING"));
}
```

**And** 性能测试验证：
- API 列表查询 p95: < 1s ✅（NFR）
- API 详情查询 p95: < 500ms ✅（NFR）
- 缓存命中率: > 80%

---

#### Story 10.7: 前端加载性能优化

**用户故事**:
作为前端工程师，
我想要优化前端加载和渲染性能，
以便提升用户体验。

**验收标准**:

**Given** API 响应时间已优化（Story 10.6）
**When** 优化前端性能
**Then** 使用 Lighthouse 测量基准性能：

**Baseline Metrics (Before Optimization)**:
| Metric | Target | Baseline | Pass? |
|--------|--------|----------|-------|
| First Contentful Paint (FCP) | < 1.8s | TBD | - |
| Largest Contentful Paint (LCP) | < 2.5s | TBD | - |
| Time to Interactive (TTI) | < 3.8s | TBD | - |
| Cumulative Layout Shift (CLS) | < 0.1 | TBD | - |
| Total Blocking Time (TBT) | < 200ms | TBD | - |

**Optimization 1: Code Splitting and Lazy Loading**
```typescript
// BEFORE: 全部组件打包到 main.js (2MB)
import ReviewList from './views/ReviewList.vue';
import ReviewDetail from './views/ReviewDetail.vue';

// AFTER: 路由级别代码分割
const routes = [
  {
    path: '/reviews',
    component: () => import('./views/ReviewList.vue')  // reviews.js (200KB)
  },
  {
    path: '/reviews/:id',
    component: () => import('./views/ReviewDetail.vue')  // review-detail.js (300KB)
  }
];
```

**Expected Improvement**:
- Initial bundle size: 2MB → 500KB (4x reduction)
- First load time: 3s → 1s

**Optimization 2: Virtual Scrolling for Long Lists**
```vue
<!-- BEFORE: 渲染 1000 个问题（卡顿） -->
<div v-for="issue in issues" :key="issue.id">
  <IssueCard :issue="issue" />
</div>

<!-- AFTER: 虚拟滚动（仅渲染可见区域） -->
<virtual-scroller
  :items="issues"
  :item-height="100"
  :buffer="200"
>
  <template #default="{ item }">
    <IssueCard :issue="item" />
  </template>
</virtual-scroller>
```

**Expected Improvement**:
- 1000 issues rendering time: 5s → 0.2s (25x improvement)
- Memory usage: 500MB → 50MB

**Optimization 3: Image and Asset Optimization**
```typescript
// 使用 Vite 图片优化
import logo from './assets/logo.png?width=200&format=webp';

// 懒加载图片
<img src="..." loading="lazy" />
```

**Optimization 4: API Request Batching**
```typescript
// BEFORE: 串行请求（3 × 200ms = 600ms）
const project = await api.getProject(id);
const aiConfig = await api.getAIConfig(id);
const thresholds = await api.getThresholds(id);

// AFTER: 并行请求（max 200ms）
const [project, aiConfig, thresholds] = await Promise.all([
  api.getProject(id),
  api.getAIConfig(id),
  api.getThresholds(id)
]);
```

**Optimization 5: Caching and Service Worker**
```typescript
// 配置 Vite PWA 插件
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/api\.example\.com\/api\/v1\/projects/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'api-cache',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 300  // 5 minutes
              }
            }
          }
        ]
      }
    })
  ]
});
```

**And** 性能测试验证（Lighthouse）：
- Performance Score: > 90 ✅
- Accessibility Score: > 95 ✅
- Best Practices Score: > 90 ✅
- SEO Score: > 90 ✅

**And** 真实用户监控（RUM）：
- 集成 Google Analytics 4 或 Sentry Performance
- 监控 Core Web Vitals（FCP, LCP, CLS, FID, TTFB）
- 设置性能预算告警

---

#### Story 1.9: 系统监控与告警

**用户故事**:
作为 DevOps 工程师，
我想要建立完整的监控和告警系统，
以便及时发现和处理生产问题。

**验收标准**:

**Given** 系统已部署到生产环境
**When** 配置监控与告警
**Then** 集成 Prometheus + Grafana：

**Prometheus Configuration**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'ai-code-review-backend'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'redis'
    static_configs:
      - targets: ['localhost:9121']

  - job_name: 'postgres'
    static_configs:
      - targets: ['localhost:9187']
```

**Spring Boot Actuator Configuration**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

**And** 定义关键监控指标：

**Application Metrics**:
- `http_server_requests_seconds`: HTTP 请求响应时间（p50, p95, p99）
- `review_dimension_latency_seconds`: 各维度审查耗时
- `review_total_latency_seconds`: 总审查时间
- `review_failure_rate`: 审查失败率
- `ai_degradation_rate`: AI 降级率
- `task_queue_depth`: Redis 队列深度
- `worker_thread_utilization`: Worker 线程利用率

**Infrastructure Metrics**:
- `jvm_memory_used_bytes`: JVM 内存使用
- `jvm_gc_pause_seconds`: GC 暂停时间
- `system_cpu_usage`: CPU 使用率
- `redis_memory_used_bytes`: Redis 内存使用
- `postgres_connections_active`: 数据库活跃连接数

**And** 创建 Grafana Dashboard：

**Dashboard 1: 系统概览**
- 总审查数（今日/本周/本月）
- 审查成功率（实时）
- API 请求速率（req/s）
- 系统资源使用（CPU, Memory, Disk）

**Dashboard 2: 审查性能**
- 审查时间分布图（p50, p95, p99）
- 各维度审查耗时对比
- 队列深度时间序列
- Worker 线程利用率热力图

**Dashboard 3: 错误监控**
- 错误率趋势图
- Top 10 错误类型
- AI API 失败率
- 降级事件时间线

**And** 配置 Alertmanager 告警规则：

```yaml
groups:
  - name: ai-code-review-alerts
    rules:
      # 审查失败率过高
      - alert: HighReviewFailureRate
        expr: rate(review_failure_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "审查失败率超过 5%"
          description: "过去 5 分钟内审查失败率为 {{ $value }}%"

      # AI 降级率过高
      - alert: HighAIDegradationRate
        expr: rate(ai_degradation_total[5m]) > 0.10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "AI 降级率超过 10%"

      # 队列积压
      - alert: HighQueueDepth
        expr: task_queue_depth > 50
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "任务队列深度超过 50"

      # API 响应时间过长
      - alert: SlowAPIResponse
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "API p95 响应时间超过 1 秒"

      # 数据库连接池耗尽
      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "数据库连接池使用率超过 90%"

      # JVM 内存不足
      - alert: HighJVMMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM 内存使用率超过 85%"
```

**And** 集成通知渠道：
- Slack Webhook（告警发送到 #alerts 频道）
- Email（发送给 on-call 工程师）
- PagerDuty（严重告警自动创建 incident）

**And** 配置日志聚合（ELK Stack）：
```yaml
# Filebeat configuration
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/ai-code-review/*.log
    fields:
      app: ai-code-review
    multiline:
      pattern: '^\d{4}-\d{2}-\d{2}'
      negate: true
      match: after

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "ai-code-review-%{+yyyy.MM.dd}"
```

**And** 创建 Kibana Dashboard：
- 错误日志搜索和过滤
- 审查流程日志追踪（按 taskId 关联）
- Slow query 日志分析

**And** 健康检查端点：
```java
@Component
public class SystemHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        // Check Redis
        try {
            redisTemplate.opsForValue().get("health-check");
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "Connection failed")
                .build();
        }

        // Check Database
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .build();
        }

        // Check Queue Depth
        Long queueDepth = redisTemplate.opsForZSet().size("review-queue");
        if (queueDepth > 100) {
            return Health.down()
                .withDetail("queue", "Queue depth too high: " + queueDepth)
                .build();
        }

        return Health.up()
            .withDetail("queue_depth", queueDepth)
            .build();
    }
}
```

**And** 验收测试：
- 触发高失败率场景，验证告警发送（< 5 分钟）
- 模拟数据库故障，验证健康检查失败
- 验证 Grafana Dashboard 数据实时更新

---

**Story 1.8 补充: HTTPS 配置细节**

在 Story 1.8（Docker Compose 环境配置）的验收标准中，补充以下内容：

**And** 配置 HTTPS 支持（生产环境）：

**方案 1: Nginx Reverse Proxy + Let's Encrypt**

创建 `docker-compose.prod.yml`:
```yaml
version: '3.8'
services:
  nginx:
    image: nginx:1.25
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
      - /etc/letsencrypt:/etc/letsencrypt
    depends_on:
      - backend

  certbot:
    image: certbot/certbot
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt
      - /var/www/certbot:/var/www/certbot
    command: certonly --webroot --webroot-path=/var/www/certbot --email admin@example.com --agree-tos --no-eff-email -d api.example.com
```

**Nginx Configuration** (`nginx/nginx.conf`):
```nginx
server {
    listen 80;
    server_name api.example.com;

    # Redirect HTTP to HTTPS
    location / {
        return 301 https://$host$request_uri;
    }

    # Let's Encrypt verification
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    # SSL Certificate
    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;

    # SSL Configuration (Modern)
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;

    # Security Headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Proxy to Backend
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Frontend Static Files
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

**方案 2: 自签名证书（开发/测试环境）**

生成自签名证书脚本 (`generate-ssl-cert.sh`):
```bash
#!/bin/bash

mkdir -p nginx/ssl
cd nginx/ssl

# Generate private key
openssl genrsa -out server.key 2048

# Generate certificate signing request
openssl req -new -key server.key -out server.csr \
  -subj "/C=CN/ST=Beijing/L=Beijing/O=Example/OU=IT/CN=localhost"

# Generate self-signed certificate (valid for 365 days)
openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt

echo "SSL certificate generated in nginx/ssl/"
```

**Spring Boot HTTPS Configuration** (备选方案，直接使用 Spring Boot):
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: tomcat
```

**生成 PKCS12 Keystore**:
```bash
keytool -genkeypair -alias tomcat \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 365 \
  -storepass changeit
```

**And** 更新 API 文档说明 HTTPS 端点：
- 开发环境: `http://localhost:8080/api/v1/*`
- 生产环境: `https://api.example.com/api/v1/*`

**And** 配置 Webhook URL 验证：
- 确保 Git 平台 Webhook 配置使用 HTTPS URL
- 验证 SSL 证书有效性

**And** 自动续期 Let's Encrypt 证书：
```bash
# Cron job (每月1日执行)
0 0 1 * * docker-compose -f docker-compose.prod.yml run certbot renew && docker-compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

---

## 5. 附录

### 4.1 参考文档

- **PRD 文档**: `_bmad-output/implementation-artifacts/02-PRD.md`
- **架构文档**: `_bmad-output/planning-artifacts/architecture.md`
- **产品简报**: `_bmad-output/planning-artifacts/01-product-brief.md`

### 4.2 关键术语

- **Webhook**: Git 平台在代码提交或 PR/MR 事件时向外部系统发送的 HTTP 回调
- **Review Task**: 代码审查任务，包含代码差异、元数据和审查配置
- **AI Provider**: AI 服务提供商（OpenAI、Anthropic 等）
- **Threshold**: 代码质量阈值，超过时触发拦截
- **Epic**: 大型用户故事，代表重要业务价值，包含多个 Story
- **Story**: 可独立交付的功能单元，描述用户价值和验收标准

### 4.3 更新历史

| 日期 | 版本 | 变更描述 | 作者 |
|------|------|----------|------|
| 2026-02-05 | 1.0 | 初始需求提取完成（Step 1） | BMAD Method |
| 2026-02-05 | 2.0 | Epic 列表设计完成（Step 2） | BMAD Method |
| 2026-02-05 | 3.0 | 43 个详细 Stories 创建完成（Step 3） | BMAD Method |
| 2026-02-05 | 4.0 | 最终验证完成（Step 4） - 文档就绪 | BMAD Method |
| 2026-02-05 | 5.0 | **P0 行动项完成** - 新增 Epic 9/10，补充 Story 1.9/1.8，细化 Story 4.5/3.4/8.2 | Implementation Readiness |

---

**文档状态**: ✅ **完成并增强** - 所有需求已覆盖，关键差距已补充，Stories 就绪用于开发
**最终统计**:
- **10 个 Epic**（新增 Epic 9: 端到端测试，Epic 10: 性能测试）
- **57 个 Story**（原 43 个 + Epic 9 的 7 个 + Epic 10 的 7 个）
- **9 个 FR | 5 个 NFR**
- **100% 需求覆盖率**
- **关键增强**:
  - ✅ 补充 AWS CodeCommit API 详细规范（architecture.md）
  - ✅ 补充 AI 降级策略决策树（architecture.md）
  - ✅ 补充六维度审查并发策略（architecture.md）
  - ✅ 新增默认 Prompt 模板设计（ai-prompt-templates.md）
  - ✅ 新增性能测试计划（poc-javaparser-performance.md, poc-redis-queue-performance.md, poc-aws-codecommit-integration.md）
  - ✅ 细化高风险 Story（4.5, 3.4, 8.2）的验收标准和性能目标
  - ✅ 新增系统监控与告警 Story（1.9）
  - ✅ 补充 HTTPS 配置详细规范（Story 1.8）
