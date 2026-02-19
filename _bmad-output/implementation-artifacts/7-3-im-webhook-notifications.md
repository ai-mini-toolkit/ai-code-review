# Story 7.3: 实现 IM Webhook 通知（钉钉、Slack、飞书）

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 团队 (Team),
I want 通过 IM（钉钉、Slack、飞书）接收审查超阈值警告,
so that 团队能及时知晓代码质量问题，快速响应严重缺陷。

## Acceptance Criteria

### AC1: 数据库扩展 — notification_config 表添加 IM 配置字段
- 创建 Flyway 迁移 `V15__add_im_fields_to_notification_config.sql`
- 在 `notification_config` 表添加以下列：
  - `dingtalk_enabled BOOLEAN NOT NULL DEFAULT false`
  - `dingtalk_webhook_url VARCHAR(500)`
  - `dingtalk_secret VARCHAR(500)` — 加密存储（复用 `SmtpPasswordConverter`）
  - `slack_enabled BOOLEAN NOT NULL DEFAULT false`
  - `slack_webhook_url VARCHAR(500)`
  - `lark_enabled BOOLEAN NOT NULL DEFAULT false`
  - `lark_webhook_url VARCHAR(500)`
- 更新 `NotificationConfigEntity` 添加对应字段（每个 enabled 字段使用 `@Builder.Default`）
- 更新 `NotificationConfigDTO` 添加对应字段（排除 `dingtalkSecret`，同 `smtpPassword` 安全策略）

### AC2: 钉钉 Webhook 通知服务
- 在 integration 模块创建 `DingTalkWebhookService` 接口：
  - `sendNotification(String webhookUrl, String secret, String markdownTitle, String markdownText): boolean`
- 创建 `DingTalkWebhookServiceImpl`：
  - 使用已有的 `HttpClient` bean
  - 请求格式：`{"msgtype": "markdown", "markdown": {"title": "...", "text": "..."}}`
  - 当 `secret` 不为空时，附加签名参数：
    - `timestamp` = 当前毫秒时间戳
    - `sign` = URL-encode(Base64(HMAC-SHA256(`timestamp + "\n" + secret`)))
    - 将 `&timestamp={timestamp}&sign={sign}` 追加到 webhook URL
  - 返回 true 表示发送成功（HTTP 200 + errcode=0），false 表示失败
  - 网络异常记录 WARN 日志并返回 false

### AC3: Slack Webhook 通知服务
- 在 integration 模块创建 `SlackWebhookService` 接口：
  - `sendNotification(String webhookUrl, String markdownText): boolean`
- 创建 `SlackWebhookServiceImpl`：
  - 使用已有的 `HttpClient` bean
  - 请求格式：Slack Incoming Webhook（`{"text": "..."}`，支持 Slack mrkdwn 格式）
  - 返回 true 表示发送成功（HTTP 200），false 表示失败
  - 网络异常记录 WARN 日志并返回 false

### AC4: 飞书 Webhook 通知服务
- 在 integration 模块创建 `LarkWebhookService` 接口：
  - `sendNotification(String webhookUrl, String title, String content): boolean`
- 创建 `LarkWebhookServiceImpl`：
  - 使用已有的 `HttpClient` bean
  - 请求格式：飞书自定义机器人 Webhook（`{"msg_type": "interactive", "card": {...}}`）
  - 使用 Interactive Card（交互式卡片）格式，比纯文本更美观
  - 返回 true 表示发送成功（HTTP 200 + code=0），false 表示失败
  - 网络异常记录 WARN 日志并返回 false

### AC5: IM 通知编排服务
- 在 service 模块创建 `IMNotificationService` 接口：
  - `sendThresholdViolationNotifications(Long taskId): void`
- 创建 `IMNotificationServiceImpl`：
  1. 通过 `reviewTaskRepository.findById()` 查找任务
  2. 通过 `notificationConfigRepository.findByProjectId()` 查找配置
  3. 调用 `reviewReportService.generateReport(taskId)` 生成报告
  4. 调用 `reviewResultService.getResultByTaskId(taskId)` 获取阈值结果
  5. 构建通知内容（`buildNotificationContent()`）
  6. 分别检查 dingtalk_enabled、slack_enabled、lark_enabled，逐一发送
  7. 所有异常捕获并记录 WARN 日志，不阻塞主流程
- 添加 `@Transactional(readOnly = true)` 注解（Story 7.2 code review 经验）

### AC6: 通知内容格式
- 构建精简的阈值违规通知内容，包含：
  - 标题：`[AI Code Review] 审查超阈值警告 — {project_name}`
  - 项目信息：项目名、分支、提交者
  - 阈值违规摘要：违规规则列表（rule、actual、threshold）
  - 问题统计：严重性分布表
  - 建议动作：`action` 字段值（如 BLOCK_MERGE）
- 为每个平台适配格式：
  - 钉钉：Markdown 格式（支持 `### 标题`、`> 引用`、`- 列表`）
  - Slack：mrkdwn 格式（支持 `*bold*`、`_italic_`、` ``` code ``` `）
  - 飞书：Interactive Card JSON 格式

### AC7: 集成到 ReviewResultServiceImpl
- 在 `saveResult()` 方法中添加新步骤（step 12，在 git comment 通知之后）
- **仅当阈值验证失败时**调用 `imNotificationService.sendThresholdViolationNotifications(taskId)`
- 使用 try-catch 包裹，失败时记录 WARN 日志
- 使用 `@Lazy` 注解注入 `IMNotificationService`

### AC8: 单元测试
- `DingTalkWebhookServiceImplTest`：
  - 发送成功（mock HttpClient，HTTP 200 + errcode=0）
  - 带签名发送（验证 URL 追加 timestamp/sign 参数）
  - HTTP 错误码处理
  - 网络异常处理
  - 钉钉返回非零 errcode 处理
- `SlackWebhookServiceImplTest`：
  - 发送成功（mock HttpClient，HTTP 200）
  - HTTP 错误码处理
  - 网络异常处理
- `LarkWebhookServiceImplTest`：
  - 发送成功（mock HttpClient，HTTP 200 + code=0）
  - HTTP 错误码处理
  - 网络异常处理
- `IMNotificationServiceImplTest`：
  - 阈值失败时发送钉钉通知
  - 阈值失败时发送 Slack 通知
  - 阈值失败时发送飞书通知
  - 多平台同时启用时全部发送
  - 任务不存在时跳过
  - 无通知配置时跳过
  - 无 IM 平台启用时跳过
  - 单个平台异常不影响其他平台
  - buildNotificationContent 内容验证
- `ReviewResultServiceImplTest`（扩展）：
  - 阈值失败时调用 sendThresholdViolationNotifications
  - 阈值通过时不调用 IM 通知
  - IM 通知异常不传播

## Tasks / Subtasks

- [x] Task 1: 数据库扩展 (AC: #1)
  - [x] 1.1 创建 Flyway 迁移 `V15__add_im_fields_to_notification_config.sql`
  - [x] 1.2 更新 `NotificationConfigEntity` 添加 7 个 IM 字段
  - [x] 1.3 更新 `NotificationConfigDTO` 添加 IM 字段（排除 secret）

- [x] Task 2: 钉钉 Webhook 通知服务 (AC: #2)
  - [x] 2.1 创建 `DingTalkWebhookService` 接口
  - [x] 2.2 创建 `DingTalkWebhookServiceImpl`（含签名计算）
  - [x] 2.3 创建 `DingTalkWebhookServiceImplTest`

- [x] Task 3: Slack Webhook 通知服务 (AC: #3)
  - [x] 3.1 创建 `SlackWebhookService` 接口
  - [x] 3.2 创建 `SlackWebhookServiceImpl`
  - [x] 3.3 创建 `SlackWebhookServiceImplTest`

- [x] Task 4: 飞书 Webhook 通知服务 (AC: #4)
  - [x] 4.1 创建 `LarkWebhookService` 接口
  - [x] 4.2 创建 `LarkWebhookServiceImpl`
  - [x] 4.3 创建 `LarkWebhookServiceImplTest`

- [x] Task 5: IM 通知编排服务 (AC: #5, #6)
  - [x] 5.1 创建 `IMNotificationService` 接口
  - [x] 5.2 创建 `IMNotificationServiceImpl`（含 `buildNotificationContent()` 方法）
  - [x] 5.3 创建 `IMNotificationServiceImplTest`

- [x] Task 6: 集成到 ReviewResultServiceImpl (AC: #7)
  - [x] 6.1 注入 `IMNotificationService`（使用 `@Lazy`）
  - [x] 6.2 在 saveResult() 添加 step 12（仅阈值失败时调用）
  - [x] 6.3 扩展 `ReviewResultServiceImplTest` 添加 IM 通知集成测试

- [x] Task 7: 回归测试 (AC: #8)
  - [x] 7.1 运行 integration 模块全部测试
  - [x] 7.2 运行 service 模块全部测试
  - [x] 7.3 运行全量回归测试

## Dev Notes

### 架构模式

**模块分布（沿用 Story 7.1/7.2 既定模式）**：
- **integration 模块**：`DingTalkWebhookService`、`SlackWebhookService`、`LarkWebhookService` — HTTP Webhook 调用层
- **service 模块**：`IMNotificationService` — 业务编排层（查配置 → 生成内容 → 分发平台）
- **repository 模块**：`NotificationConfigEntity` 更新 + Flyway 迁移
- **common 模块**：`NotificationConfigDTO` 更新

**复用已有组件**：
- `HttpClient` bean（已在 integration 模块配置）
- `SmtpPasswordConverter` — 复用加密/解密逻辑（用于 `dingtalk_secret` 字段）
- `ReviewReportService.generateReport()` — 生成报告 DTO
- `ReviewResultService.getResultByTaskId()` — 获取阈值结果
- `NotificationConfigRepository.findByProjectId()` — 查询通知配置
- `ReviewTaskRepository.findById()` — 查询任务详情

### Webhook API 参考

**钉钉自定义机器人** ([Source: open.dingtalk.com]):
```
POST https://oapi.dingtalk.com/robot/send?access_token={token}

# 带签名时：
POST https://oapi.dingtalk.com/robot/send?access_token={token}&timestamp={ts}&sign={sign}
签名计算: sign = URL-encode(Base64(HMAC-SHA256(timestamp + "\n" + secret, secret)))

Body: {"msgtype": "markdown", "markdown": {"title": "...", "text": "..."}}
Response: {"errcode": 0, "errmsg": "ok"}
```

**Slack Incoming Webhook** ([Source: api.slack.com]):
```
POST https://hooks.slack.com/services/T.../B.../xxx

Body: {"text": "Markdown formatted text with *bold*, _italic_, etc."}
Response: HTTP 200 "ok"
```
注意：Slack Incoming Webhook 不需要认证头，URL 本身包含 token。使用 mrkdwn 格式而非标准 Markdown。

**飞书自定义机器人** ([Source: open.feishu.cn]):
```
POST https://open.feishu.cn/open-apis/bot/v2/hook/{hook_id}

Body: {"msg_type": "interactive", "card": {"header": {...}, "elements": [...]}}
Response: {"code": 0, "msg": "success"}
```

### 触发条件（关键差异）

**与 Email/Comment 通知的关键区别**：
- **Email**（Story 7.1）：阈值失败 → violation 通知；阈值通过 → complete 通知（互斥发送）
- **Git Comment**（Story 7.2）：始终发送（无论阈值结果），由 `postReviewComment` 内部决定
- **IM Webhook**（本 Story）：**仅当阈值验证失败时**发送（Epic 明确要求 "仅超阈值时发送 IM 通知，避免打扰"）

实现要点：
```java
// ReviewResultServiceImpl.saveResult() — step 12
// 仅阈值失败时发送 IM 通知（区别于 email 和 comment）
if (!thresholdResult.isPassed()) {
    try {
        imNotificationService.sendThresholdViolationNotifications(taskId);
    } catch (Exception e) {
        log.warn("Failed to send IM notification for task {}: {}", taskId, e.getMessage());
    }
}
```

### 钉钉签名计算

```java
// 签名计算示例
long timestamp = System.currentTimeMillis();
String stringToSign = timestamp + "\n" + secret;
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
String signedUrl = webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
```

### 通知内容模板

**钉钉 Markdown 示例**：
```markdown
### ⚠️ AI Code Review 超阈值警告

**项目**: Test Project
**分支**: feature/login
**提交者**: dev@test.com

---

#### 阈值违规

- **CRITICAL <= 0** — 实际: 2, 限制: 0
- **HIGH <= 3** — 实际: 5, 限制: 3

#### 问题统计

| 严重性 | 数量 |
|--------|------|
| CRITICAL | 2 |
| HIGH | 5 |
| MEDIUM | 3 |

**建议动作**: BLOCK_MERGE
```

**Slack mrkdwn 示例**：
```
:warning: *AI Code Review Threshold Violation*

*Project:* Test Project
*Branch:* feature/login
*Author:* dev@test.com

*Violations:*
• `CRITICAL <= 0` — actual: 2, limit: 0
• `HIGH <= 3` — actual: 5, limit: 3

*Issue Summary:* CRITICAL: 2, HIGH: 5, MEDIUM: 3
*Action:* BLOCK_MERGE
```

### 关键技术约束

1. **`-parameters` 未启用**：所有 `@Value` 注解无需担心（Webhook service 无配置注入，URL 来自数据库）
2. **循环依赖防护**：`IMNotificationService` 注入 `ReviewReportService` + `ReviewResultService`。在 `ReviewResultServiceImpl` 中使用 `@Lazy` 注入 `IMNotificationService`。
3. **非阻塞模式**：与 Email/Comment 通知一致，所有 Webhook 调用异常仅记录 WARN 日志
4. **`@Transactional(readOnly = true)`**：Story 7.2 code review 发现 `GitCommentNotificationServiceImpl` 需要此注解以防止 `LazyInitializationException`，本 story 直接应用
5. **Secret 加密**：DingTalk secret 使用 `SmtpPasswordConverter` 加密存储（JPA `@Convert`），与 SMTP 密码一致
6. **多平台独立发送**：一个平台失败不影响其他平台。在 `IMNotificationServiceImpl` 中对每个平台独立 try-catch

### 前序 Story 教训（来自 Story 7.1 / 7.2 Code Review）

- **M1（文件记录）**：修改的文件必须全部记录到 File List（Story 7.2 漏记了 ReviewReportServiceImpl.java）
- **M2（日志安全）**：API 响应体记录到日志时截断至 200 字符（防止敏感信息泄露）
- **M3（事务注解）**：Service 层访问 Lazy-loaded JPA 实体时需 `@Transactional(readOnly = true)`
- **M4（枚举排序）**：使用 `IssueSeverity.getScore()` 而非 `ordinal()` 排序
- **M5（入口防御）**：Public 接口方法对关键参数做 null 防护
- **IssueCategory 枚举值**：SECURITY, PERFORMANCE, MAINTAINABILITY, CORRECTNESS, STYLE, BEST_PRACTICES（不是 BUG/CODE_SMELL）

### Project Structure Notes

- **新文件路径与既有模块结构对齐**：
  - integration: `com.aicodereview.integration.im.*` — 新建 `im` 子包（区别于 `git` 包）
  - service: `com.aicodereview.service.*` + `com.aicodereview.service.impl.*` — 与 EmailNotificationService 同包
  - repository migration: `db/migration/V15__*.sql` — 按顺序递增

- **依赖关系**：
  - `service` 模块依赖 `integration` 模块（已有）
  - `service` 模块依赖 `repository` 模块（已有）
  - 无需新增 Maven 依赖项（`HttpClient` 已配置，`javax.crypto` 为 JDK 标准库）

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-7.md#Story 7.3] — Epic 原始需求
- [Source: _bmad-output/implementation-artifacts/7-1-email-notification-service.md] — Email 通知模式参考
- [Source: _bmad-output/implementation-artifacts/7-2-git-platform-comment-notification.md] — Git 评论通知模式参考
- [Source: backend/ai-code-review-service/src/main/java/.../ReviewResultServiceImpl.java] — 编排集成点（step 12）
- [Source: backend/ai-code-review-repository/src/main/java/.../NotificationConfigEntity.java] — 实体扩展点
- [Source: backend/ai-code-review-repository/src/main/java/.../converter/SmtpPasswordConverter.java] — Secret 加密复用
- [Source: _bmad-output/planning-artifacts/architecture.md#Module Architecture] — 模块架构约束

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- 环境注意：Maven 默认 JDK 为 Java 25（Homebrew），需使用 JAVA_HOME=/Users/ethan/Library/Java/JavaVirtualMachines/corretto-17.0.9/Contents/Home 指定 JDK 17 编译
- RedisConnectionTest 和 DatabaseConnectionTest 需要运行中的 Redis/PostgreSQL 服务，在本地开发环境中预期失败

### Completion Notes List

- 所有实现在前序会话中完成，本次会话验证了全部代码编译通过且测试通过
- AC1: V15 迁移创建成功，NotificationConfigEntity 添加 7 个 IM 字段（dingtalk_enabled/webhook_url/secret, slack_enabled/webhook_url, lark_enabled/webhook_url），NotificationConfigDTO 排除 dingtalkSecret
- AC2: DingTalkWebhookServiceImpl 实现 HMAC-SHA256 签名计算，支持带签名和不带签名两种模式，11 个测试全部通过
- AC3: SlackWebhookServiceImpl 实现 Slack Incoming Webhook（mrkdwn 格式），6 个测试全部通过
- AC4: LarkWebhookServiceImpl 实现飞书 Interactive Card 格式，7 个测试全部通过
- AC5/AC6: IMNotificationServiceImpl 编排服务，支持多平台独立发送（每个平台独立 try-catch），11 个测试全部通过
- AC7: ReviewResultServiceImpl.saveResult() step 12 集成，仅阈值失败时触发 IM 通知，@Lazy 注入避免循环依赖，33 个测试全部通过
- AC8: 全量回归测试 682 项通过（common 130 + integration 261 + service 291），0 失败

### Change Log

- 2026-02-19: 验证全部实现并标记任务完成，状态更新为 review
- 2026-02-19: Code Review 修复 — H1（thresholdResult null 防护）、H2（report 字段 null 防护 + 重构 buildXxxContent 参数）、H3（package-private 方法加注释）、M1（SlackWebhookServiceImpl 添加 Blocks 格式差异说明）、M2（issue summary 排序改用 IssueSeverity.values()，L2（记录 webhook 发送返回值）；新增 2 个测试；293 tests 全部通过，状态更新为 done

### File List

- backend/ai-code-review-repository/src/main/resources/db/migration/V15__add_im_fields_to_notification_config.sql
- backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/NotificationConfigEntity.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notification/NotificationConfigDTO.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/DingTalkWebhookService.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/DingTalkWebhookServiceImpl.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/SlackWebhookService.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/SlackWebhookServiceImpl.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/LarkWebhookService.java
- backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/im/LarkWebhookServiceImpl.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/IMNotificationService.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/IMNotificationServiceImpl.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java
- backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/im/DingTalkWebhookServiceImplTest.java
- backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/im/SlackWebhookServiceImplTest.java
- backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/im/LarkWebhookServiceImplTest.java
- backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/IMNotificationServiceImplTest.java
- backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java
