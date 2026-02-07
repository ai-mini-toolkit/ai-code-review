# Epic 7: 多渠道通知系统

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

## Stories


### Story 7.1: 实现邮件通知服务

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

### Story 7.2: 实现 Git 平台评论通知

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

### Story 7.3: 实现 IM Webhook 通知（钉钉、Slack、飞书）

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

### Story 7.4: 实现通知模板管理

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

