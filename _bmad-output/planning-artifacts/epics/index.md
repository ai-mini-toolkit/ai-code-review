# Epics and Stories: ai-code-review

**项目名称**: AI 智能代码审查系统
**生成日期**: 2026-02-05
**版本**: 1.0
**工作流**: BMAD Method - Create Epics and Stories

---

## 文档说明

本文档为 Epic 总览，提供需求摘要、需求映射和 Epic 列表。

**详细 Story 内容请查看各 epic-X.md 文件**：
- [Epic 1: 项目基础设施与配置管理](./epic-1.md)
- [Epic 2: Webhook 集成与任务队列](./epic-2.md)
- [Epic 3: 代码解析与上下文提取](./epic-3.md)
- [Epic 4: AI 智能审查引擎](./epic-4.md)
- [Epic 5: 审查报告与结果存储](./epic-5.md)
- [Epic 6: 质量阈值与 PR/MR 拦截](./epic-6.md)
- [Epic 7: 多渠道通知系统](./epic-7.md)
- [Epic 8: Web 管理界面](./epic-8.md)
- [Epic 9: 端到端测试与集成验证](./epic-9.md)
- [Epic 10: 性能测试与优化](./epic-10.md)

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

- **FR 1.3: 代码解析与上下文提取**
  - Diff 内容解析（支持 Unified Diff 格式）
  - 完整文件内容获取（通过 Git API）
  - 编程语言检测（基于文件扩展名和内容特征）
  - 调用链路分析（Phase 1: JavaParser for Java; Phase 2: Tree-sitter for multi-language）
  - 上下文提取（变更的函数、类、模块信息）

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

### Epic 3: 代码解析与上下文提取

**用户价值**：系统能够解析 Git 代码差异、获取完整文件内容、检测编程语言并分析调用链路，为 AI 审查提供丰富的代码上下文。

**用户成果**：
- 解析 Unified Diff 格式的代码差异
- 通过 Git API 获取完整文件内容
- 自动检测编程语言（基于扩展名和内容特征）
- 生成 Java 代码的调用链路图（Phase 1: JavaParser）
- 提取变更的函数、类、模块信息

**覆盖的功能需求**：FR 1.3（代码解析与上下文提取）
**覆盖的非功能需求**：NFR 1（性能）
**覆盖的附加需求**：集成要求（Git API）、数据流要求

---

### Epic 4: AI 智能审查引擎

**用户价值**：系统使用多种 AI 提供商（OpenAI、Anthropic、自定义 OpenAPI）对代码进行六维度智能审查，识别安全漏洞、性能问题、可维护性问题等。

**用户成果**：
- 实现 AI 提供商抽象层（策略模式 + 工厂模式）
- 集成 OpenAI、Anthropic Claude 和自定义 OpenAPI 提供商
- 执行六维度代码分析（安全性、性能、可维护性、正确性、代码风格、最佳实践）
- 实现降级策略（主模型 → 备用模型 → 错误）
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
