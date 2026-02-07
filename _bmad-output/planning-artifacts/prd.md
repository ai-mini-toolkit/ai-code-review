# 产品需求文档 (PRD) - AI Code Review

## 文档信息
| 项目 | 内容 |
|------|------|
| 项目名称 | ai-code-review |
| 版本 | v1.1 |
| 创建日期 | 2026-02-04 |
| 更新日期 | 2026-02-04 |
| 状态 | Draft |

## 版本历史
| 版本 | 日期 | 更新内容 |
|------|------|---------|
| v1.1 | 2026-02-04 | 新增 AWS CodeCommit 支持、AI 模型直接集成、OpenAPI 配置 |
| v1.0 | 2026-02-04 | 初始版本 |

---

## 1. 功能需求

### 1.1 Webhook 接收

#### 1.1.1 GitHub Webhook
- 支持事件类型：
  - `push` - 代码推送
  - `pull_request` - PR 创建/更新/同步
- 验证 Webhook 签名（HMAC-SHA256）
- 解析 Payload 提取：
  - 仓库信息（owner, repo, branch）
  - 提交信息（commit hash, author, message）
  - 代码变更（diff/patch）

#### 1.1.2 GitLab Webhook
- 支持事件类型：
  - `Push Events` - 代码推送
  - `Merge Request Events` - MR 创建/更新
- 验证 Secret Token
- 解析 Payload（同 GitHub）

#### 1.1.3 AWS CodeCommit Webhook
- 支持事件类型：
  - `ReferenceTriggers` - 推送触发
  - 支持 `BranchName` 和 `Tags` 过滤
- 验证 AWS Signature Version 4
- 解析 Event Payload 提取：
  - 仓库信息（repositoryName, region）
  - 提交信息（commitId, author, message）
  - 代码变更（通过 CodeCommit API 获取 diff）
- 需要 AWS 访问凭证配置：
  - `AWS_ACCESS_KEY_ID`
  - `AWS_SECRET_ACCESS_KEY`
  - `AWS_REGION`

#### 1.1.4 Webhook 注册接口
```
POST /api/webhook/register
```
请求体：
```json
{
  "platform": "github|gitlab|aws-codecommit",
  "repoUrl": "https://github.com/owner/repo",
  "webhookUrl": "https://your-domain/api/webhook/github",
  "secret": "webhook-secret",
  "events": ["push", "pull_request"],
  "awsRegion": "us-east-1",
  "awsAccessKeyId": "AKIAIOSFODNN7EXAMPLE",
  "awsSecretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}
```

### 1.2 任务管理

#### 1.2.1 任务创建
- 接收 Webhook 后自动创建任务
- 任务信息：
  - 任务 ID（UUID）
  - 项目 ID
  - 触发类型（push/pr）
  - 提交 hash
  - 代码变更内容
  - 创建时间
  - 状态（等待/执行中/完成/失败）

#### 1.2.2 任务队列
- 异步队列处理任务
- 支持任务优先级：
  - High：PR/MR 任务
  - Normal：Push 任务
- 支持并发控制（最大并发数配置）

#### 1.2.3 任务状态更新
- 任务执行过程中实时更新状态
- 任务失败时记录错误信息
- 支持任务重试（可配置重试次数）

### 1.3 代码解析

#### 1.3.1 Diff 解析
- 解析 Git diff/patch 格式
- 提取变更文件列表：
  - 新增文件
  - 修改文件
  - 删除文件
- 提取变更代码片段：
  - 删除的行
  - 新增的行

#### 1.3.2 语言识别
- 根据文件扩展名识别编程语言
- 支持的语言列表（扩展）：
  - Java, Python, JavaScript, TypeScript, Go, Rust, C/C++, PHP, Ruby, etc.

#### 1.3.3 路径过滤
- 根据配置过滤文件路径
- 支持正则表达式匹配
- 配置示例：
  ```json
  {
    "includePatterns": ["src/**/*.java", "api/**/*.ts"],
    "excludePatterns": ["**/test/**", "**/*Test.java"]
  }
  ```

### 1.4 AI 智能审查

#### 1.4.0 AI 模型集成
- **直接集成 AI 模型**：系统内置 AI 审查引擎，无需依赖外部 AI API
- **支持 OpenAPI 配置**：可通过 OpenAPI 规范配置自定义 AI 模型接入
- **内置模型支持**：
  - OpenAI GPT-4 / GPT-3.5
  - Anthropic Claude 3.5 Sonnet / Opus
  - 其他兼容 OpenAI API 的模型（如 Azure OpenAI、本地 LLM）
- **模型配置**：
  ```json
  {
    "provider": "openai|anthropic|custom",
    "apiKey": "sk-...",
    "baseURL": "https://api.openai.com/v1",
    "model": "gpt-4",
    "temperature": 0.3,
    "maxTokens": 4000,
    "timeout": 30000
  }
  ```
- **模型路由策略**：
  - 按审查维度分配不同模型（安全审查用更谨慎的模型）
  - 支持负载均衡多 API Key
  - 支持降级策略（主模型失败时切换备用模型）

#### 1.4.1 代码质量分析
- 命名规范检查：
  - 类名：PascalCase
  - 方法名：camelCase
  - 常量：UPPER_SNAKE_CASE
  - 变量：camelCase
- 代码结构分析：
  - 方法长度（默认限制：50 行）
  - 类长度（默认限制：500 行）
  - 圈复杂度（默认限制：10）
  - 重复代码检测

#### 1.4.2 安全问题检测
- SQL 注入风险：
  - 字符串拼接 SQL
  - 缺少参数化查询
- XSS 风险：
  - 直接输出用户输入
  - 缺少 HTML 转义
- 敏感信息泄漏：
  - 硬编码密码/API Key
  - 敏感数据日志输出
- 权限问题：
  - 未授权访问
  - 权限绕过

#### 1.4.3 性能问题分析
- 算法效率：
  - 嵌套循环检测
  - O(n²) 及以上复杂度警告
- 资源使用：
  - 未关闭的资源（连接、流）
  - 内存泄漏风险
  - N+1 查询问题
- 缓存问题：
  - 可添加缓存的场景
  - 缓存穿透风险

#### 1.4.4 代码风格检查
- 导入顺序
- 空行使用
- 括号位置
- 缩进一致性
- 命名长度（过短/过长）
- 注释规范

#### 1.4.5 Bug 预测
- 空指针风险
- 数组越界风险
- 类型转换风险
- 并发问题（竞态条件）
- 资源竞争

#### 1.4.6 调用链路分析
- 依赖关系分析：
  - 类与类之间的调用
  - 模块与模块之间的依赖
- 循环依赖检测：
  - 类级别循环依赖
  - 模块级别循环依赖
- 调用深度分析：
  - 警告过深调用链（默认限制：10 层）

### 1.5 审查报告

#### 1.5.1 报告结构
```json
{
  "taskId": "uuid",
  "projectId": "project-id",
  "triggerType": "pr",
  "commitHash": "abc123",
  "status": "completed",
  "summary": {
    "totalIssues": 15,
    "errorCount": 3,
    "warningCount": 8,
    "infoCount": 4,
    "score": 75
  },
  "issues": [
    {
      "id": "issue-1",
      "severity": "error",
      "category": "security",
      "rule": "sql-injection",
      "file": "src/main/java/dao/UserDao.java",
      "line": 42,
      "codeSnippet": "String sql = \"SELECT * FROM users WHERE id = \" + userId;",
      "message": "SQL injection risk: use parameterized query",
      "suggestion": "Use PreparedStatement with parameters"
    }
  ],
  "callGraph": {
    "nodes": ["UserDao", "UserService", "UserController"],
    "edges": [
      {"from": "UserController", "to": "UserService"},
      {"from": "UserService", "to": "UserDao"}
    ],
    "circularDependencies": []
  },
  "createdAt": "2026-02-04T17:00:00Z",
  "completedAt": "2026-02-04T17:02:00Z"
}
```

#### 1.5.2 问题分级
| 级别 | 描述 | 处理方式 |
|------|------|---------|
| Error | 严重问题，必须修复 | 强制拦截，阻止合并 |
| Warning | 警告问题，建议修复 | 仅报告，人工决定 |
| Info | 参考性建议 | 可选修复 |

#### 1.5.3 调用链路可视化
- 支持导出为：
  - Mermaid 图表
  - PlantUML
  - SVG 图像
- 高亮显示：
  - 循环依赖（红色）
  - 过深调用链（黄色）

### 1.6 阈值拦截

#### 1.6.1 阈值配置
```json
{
  "thresholds": {
    "errorCount": {
      "enabled": true,
      "value": 0,
      "action": "block"
    },
    "score": {
      "enabled": true,
      "value": 60,
      "action": "block"
    },
    "maxSeverity": {
      "enabled": true,
      "value": "error",
      "action": "block"
    }
  }
}
```

#### 1.6.2 拦截策略
| 条件 | Action |
|------|--------|
| Error 数量 > 0 | 拦截 |
| 评分 < 60 | 拦截 |
| 严重级别为 Error | 拦截 |
| 仅 Warning/Info | 不拦截 |

#### 1.6.3 状态回写
- 对于 PR/MR，自动写入评论：
  ```
  ❌ Code Review Failed

  Score: 45/100
  Errors: 3, Warnings: 8, Info: 4

  Please fix the errors before merging.
  [View Full Report](https://your-domain/report/xxx)
  ```
- 通过时：
  ```
  ✅ Code Review Passed

  Score: 85/100
  No errors found.
  ```

### 1.7 通知系统

#### 1.7.1 邮件通知
- 触发条件：
  - 审查完成（无论通过/失败）
  - 阈值拦截触发
- 邮件内容：
  - 审查摘要（评分、问题数量）
  - 问题列表（高亮 Error 级别）
  - 报告链接

#### 1.7.2 Git 平台评论
- GitHub PR Comment
- GitLab MR Comment
- 支持 @ 提及开发者

#### 1.7.3 通知配置
```json
{
  "notifications": {
    "email": {
      "enabled": true,
      "recipients": ["dev@company.com"]
    },
    "gitComment": {
      "enabled": true
    },
    "dingTalk": {
      "enabled": false,
      "webhookUrl": "https://oapi.dingtalk.com/robot/..."
    }
  }
}
```

### 1.8 配置管理

#### 1.8.1 AI 模型配置
- 模型列表管理：
  - 添加/编辑/删除 AI 模型配置
  - 支持多模型同时配置
  - 支持模型优先级设置
- 模型分类：
  - **通用模型**：用于常规代码审查
  - **安全专用模型**：用于安全漏洞检测（更严格）
  - **性能专用模型**：用于性能分析
  - **备用模型**：主模型失败时切换
- OpenAPI 配置：
  ```json
  {
    "id": "model-1",
    "name": "OpenAI GPT-4",
    "provider": "openai",
    "openapiSpec": {
      "openapi": "3.0.0",
      "servers": [
        {"url": "https://api.openai.com/v1"}
      ],
      "paths": {
        "/chat/completions": {
          "post": {
            "operationId": "chat",
            "requestBody": {
              "required": true,
              "content": {
                "application/json": {
                  "schema": {
                    "type": "object",
                    "properties": {
                      "model": {"type": "string"},
                      "messages": {"type": "array"},
                      "temperature": {"type": "number"}
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    "apiKey": "${OPENAI_API_KEY}",
    "model": "gpt-4",
    "category": ["general", "security", "performance"],
    "priority": 1,
    "enabled": true
  }
  ```
- 密钥管理：
  - 支持环境变量引用（`${VAR_NAME}`）
  - 支持密钥加密存储
  - 支持密钥轮换

#### 1.8.2 项目配置
- 基础信息：
  - 项目名称
  - 仓库 URL
  - 平台类型（GitHub/GitLab）
  - Webhook Secret
- 审查规则：
  - 触发事件（push/pr）
  - 路径过滤规则
  - 阈值配置
- 通知配置：
  - 邮件收件人
  - 通知方式开关

#### 1.8.3 审查模板
- 预置模板：
  - Java 标准模板
  - Python 标准模板
  - TypeScript 标准模板
- 自定义模板：
  - 支持自定义规则提示语
  - 支持自定义严重级别

#### 1.8.4 多项目管理
- 项目列表页面
- 项目配置独立
- 支持批量操作（启用/禁用）

### 1.9 Web 界面

#### 1.9.1 项目管理
- 项目列表：
  - 展示所有项目
  - 显示项目状态（启用/禁用）
  - 显示最近审查时间
- 项目详情：
  - 基本信息
  - 规则配置
  - 通知配置
- 创建/编辑项目：
  - 表单验证
  - Webhook 配置指引

#### 1.9.2 审查历史
- 审查记录列表：
  - 按时间倒序
  - 支持筛选（项目、状态、时间范围）
- 审查详情：
  - 代码变更查看
  - 问题列表
  - 调用链路图
  - 评分详情

#### 1.9.3 调用链路可视化
- 交互式图表：
  - 缩放/拖拽
  - 点击节点高亮相关链路
- 导出功能：
  - PNG/SVG 导出
  - Mermaid 代码导出

#### 1.9.4 规则模板管理
- 模板列表
- 模板创建/编辑
- 模板复制
- 模板删除

---

## 2. 非功能性需求

### 2.1 性能要求
| 指标 | 要求 |
|------|------|
| 单次审查响应时间 | < 30s（100 行代码） |
| 任务处理延迟 | < 5s（入队到开始执行） |
| 并发任务数 | ≥ 10 |
| Webhook 响应时间 | < 1s |

### 2.2 可靠性要求
| 指标 | 要求 |
|------|------|
| 系统可用性 | ≥ 99% |
| 任务失败重试 | 3 次 |
| 数据持久化 | 100% |

### 2.3 安全性要求
- Webhook 签名验证
- HTTPS 加密传输
- 敏感信息加密存储
- 访问控制（基础认证）

### 2.4 可扩展性要求
- 支持水平扩展（多实例）
- 支持新的编程语言
- 支持新的 Git 平台
- 支持新的通知渠道

---

## 3. 数据模型

### 3.1 Project (项目)
```sql
CREATE TABLE project (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  platform VARCHAR(50) NOT NULL,  -- github/gitlab/aws-codecommit
  repo_url VARCHAR(500) NOT NULL,
  webhook_secret VARCHAR(255),
  aws_region VARCHAR(50),          -- AWS CodeCommit 区域
  aws_access_key_id VARCHAR(255),   -- 加密存储
  aws_secret_access_key TEXT,      -- 加密存储
  trigger_events JSON,  -- ["push", "pull_request"]
  include_patterns JSON,  -- ["src/**/*.java"]
  exclude_patterns JSON,  -- ["**/test/**"]
  thresholds JSON,
  notifications JSON,
  status VARCHAR(20) DEFAULT 'active',  -- active/disabled
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 ReviewTask (审查任务)
```sql
CREATE TABLE review_task (
  id VARCHAR(36) PRIMARY KEY,
  project_id VARCHAR(36) NOT NULL,
  trigger_type VARCHAR(20) NOT NULL,  -- push/pr
  commit_hash VARCHAR(100),
  branch_name VARCHAR(255),
  pr_mr_id VARCHAR(100),  -- PR/MR 编号
  pr_mr_url VARCHAR(500),
  diff_content TEXT,
  status VARCHAR(20) DEFAULT 'pending',  -- pending/running/completed/failed
  error_message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES project(id)
);
```

### 3.3 ReviewResult (审查结果)
```sql
CREATE TABLE review_result (
  id VARCHAR(36) PRIMARY KEY,
  task_id VARCHAR(36) NOT NULL,
  total_issues INT DEFAULT 0,
  error_count INT DEFAULT 0,
  warning_count INT DEFAULT 0,
  info_count INT DEFAULT 0,
  score INT DEFAULT 0,
  blocked BOOLEAN DEFAULT FALSE,  -- 是否拦截
  issues JSON,
  call_graph JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (task_id) REFERENCES review_task(id)
);
```

### 3.4 ReviewIssue (审查问题)
```sql
CREATE TABLE review_issue (
  id VARCHAR(36) PRIMARY KEY,
  result_id VARCHAR(36) NOT NULL,
  severity VARCHAR(20) NOT NULL,  -- error/warning/info
  category VARCHAR(50) NOT NULL,  -- security/performance/quality/style/bug
  rule VARCHAR(100) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  line_number INT,
  code_snippet TEXT,
  message TEXT,
  suggestion TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (result_id) REFERENCES review_result(id)
);
```

### 3.5 Template (审查模板)
```sql
CREATE TABLE template (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  language VARCHAR(50) NOT NULL,  -- java/python/typescript
  rules JSON NOT NULL,
  is_system BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.6 AIModelConfig (AI 模型配置)
```sql
CREATE TABLE ai_model_config (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  provider VARCHAR(50) NOT NULL,  -- openai/anthropic/custom
  openapi_spec JSON,  -- OpenAPI 规范
  api_key_encrypted TEXT,  -- 加密的 API Key
  base_url VARCHAR(500),
  model VARCHAR(100) NOT NULL,
  temperature DECIMAL(3,2) DEFAULT 0.3,
  max_tokens INT DEFAULT 4000,
  timeout_ms INT DEFAULT 30000,
  category JSON,  -- ["general", "security", "performance"]
  priority INT DEFAULT 0,  -- 优先级，数字越小优先级越高
  enabled BOOLEAN DEFAULT TRUE,
  is_fallback BOOLEAN DEFAULT FALSE,  -- 是否为备用模型
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
```sql
CREATE TABLE template (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  language VARCHAR(50) NOT NULL,  -- java/python/typescript
  rules JSON NOT NULL,
  is_system BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 4. API 接口定义

### 4.1 Webhook 接口
```
POST /api/webhook/github
POST /api/webhook/gitlab
POST /api/webhook/aws-codecommit
```
响应：
```json
{
  "code": 200,
  "message": "Task created",
  "data": {
    "taskId": "uuid"
  }
}
```

### 4.2 项目管理
```
GET    /api/projects
POST   /api/projects
GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}
```

### 4.3 审查任务
```
GET /api/tasks?projectId={id}&status={status}
GET /api/tasks/{id}
```

### 4.4 审查结果
```
GET /api/results/{taskId}
GET /api/results/{taskId}/issues
GET /api/results/{taskId}/call-graph
```

### 4.5 规则模板
```
GET    /api/templates
POST   /api/templates
GET    /api/templates/{id}
PUT    /api/templates/{id}
DELETE /api/templates/{id}
```

### 4.6 系统配置
```
GET /api/config
PUT /api/config
```

### 4.7 AI 模型配置
```
GET    /api/ai-models
POST   /api/ai-models
GET    /api/ai-models/{id}
PUT    /api/ai-models/{id}
DELETE /api/ai-models/{id}
POST   /api/ai-models/{id}/test      -- 测试模型连接
POST   /api/ai-models/set-primary    -- 设置主模型
```

---

## 5. 验收标准

### 5.1 Webhook 功能
- [ ] 能正确接收 GitHub Webhook (Push + PR)
- [ ] 能正确接收 GitLab Webhook (Push + MR)
- [ ] 能正确接收 AWS CodeCommit Webhook (ReferenceTriggers)
- [ ] Webhook 签名验证正常（GitHub HMAC-SHA256, GitLab Token, AWS SigV4）
- [ ] Payload 解析正确
- [ ] AWS CodeCommit 凭证配置正常
- [ ] 通过 AWS API 获取 diff 内容正常

### 5.2 任务管理
- [ ] 任务自动创建并进入队列
- [ ] 任务状态正确更新
- [ ] 任务失败时正确重试
- [ ] 并发任务正常处理

### 5.3 AI 审查
- [ ] 代码质量分析准确
- [ ] 安全问题检测有效
- [ ] 性能问题识别准确
- [ ] 代码风格检查正常
- [ ] Bug 预测功能正常
- [ ] 调用链路分析正确

### 5.4 阈值拦截
- [ ] Error 触发时正确拦截
- [ ] 评分低于阈值时正确拦截
- [ ] PR/MR 状态正确回写
- [ ] 阻止合并（通过 API/评论）

### 5.5 通知系统
- [ ] 邮件通知发送成功
- [ ] Git 平台评论发布成功
- [ ] 通知内容完整准确

### 5.6 Web 界面
- [ ] 项目管理功能完整
- [ ] 审查历史查看正常
- [ ] 调用链路可视化正常
- [ ] 规则配置保存生效

### 5.7 AI 模型配置
- [ ] AI 模型配置保存生效
- [ ] 支持多模型同时配置
- [ ] 模型路由策略正确执行
- [ ] 模型降级/切换正常
- [ ] API Key 加密存储
- [ ] OpenAPI 规范导入/导出正常
- [ ] 模型连接测试正常

### 5.8 性能指标
- [ ] 单次审查 < 30s
- [ ] Webhook 响应 < 1s
- [ ] 支持并发 ≥ 10 个任务

---

## 6. 用户故事

### US-001: Webhook 自动触发
> 作为开发者，我希望提交代码或创建 PR 后自动触发代码审查，这样我不需要手动触发。

**验收标准**：Push/PR 后自动创建审查任务

### US-002: 查看审查报告
> 作为开发者，我希望查看详细的审查报告，包括问题列表和修复建议，这样我可以快速定位问题。

**验收标准**：报告包含问题详情、代码位置、修复建议

### US-003: 调用链路可视化
> 作为开发者，我希望查看代码调用链路图，这样我可以理解代码的依赖关系。

**验收标准**：调用链路图可交互、可导出

### US-004: 阈值拦截
> 作为项目管理者，我希望不通过审查的代码不能合并，这样可以保证代码质量。

**验收标准**：Error 级别问题触发时阻止合并

### US-005: 配置审查规则
> 作为项目管理者，我希望配置不同的审查规则，这样可以适配不同项目的需求。

**验收标准**：规则配置保存生效

---

## 7. 技术债与风险

### 7.1 技术债
- [ ] 调用链路分析深度有限，可能无法处理复杂项目
- [ ] AWS CodeCommit API 调用可能产生额外成本
- [ ] OpenAPI 规范兼容性验证需要持续维护

### 7.2 风险
| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| AI API 限流 | 高 | 中 | 使用多个 API Key + 队列控制 |
| 大文件处理超时 | 中 | 高 | 分块处理 + 超时控制 |
| Webhook 重复提交 | 低 | 中 | 去重机制 |
| AWS 凭证泄漏 | 高 | 低 | 加密存储 + 定期轮换 |
| AI 模型不可用 | 中 | 中 | 多模型配置 + 降级策略 |
| OpenAPI 规范不兼容 | 低 | 低 | 提供验证工具 + 示例模板 |

---

## 8. 里程碑

| 里程碑 | 预计时间 | 交付物 |
|--------|---------|--------|
| M1: AI 模型集成 | Week 1 | AI 审查引擎、OpenAPI 配置支持、模型路由 |
| M2: 核心审查功能 | Week 2 | 代码解析、多维审查、报告生成 |
| M3: Webhook 集成 | Week 3 | GitHub/GitLab/AWS CodeCommit Webhook、任务队列 |
| M4: Web 界面 | Week 4-5 | 项目管理、审查历史、AI 模型配置 |
| M5: 通知与拦截 | Week 6 | 邮件通知、PR 状态回写、阈值拦截 |
| M6: 测试与优化 | Week 7-8 | 端到端测试、性能优化、安全加固 |
