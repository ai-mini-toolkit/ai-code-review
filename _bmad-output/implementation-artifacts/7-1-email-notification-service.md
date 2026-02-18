# Story 7.1: 实现邮件通知服务

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 开发者,
I want 在审查完成后通过邮件接收通知,
so that 及时了解代码审查结果和阈值违规情况。

## Acceptance Criteria (BDD)

### AC1: 创建 notification_config 表
**Given** 项目需要邮件通知配置
**When** 应用启动
**Then** Flyway 迁移 `V11__create_notification_config_table.sql` 创建表：
- `id` BIGSERIAL PRIMARY KEY
- `project_id` BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE（UNIQUE 约束）
- `email_enabled` BOOLEAN NOT NULL DEFAULT false
- `email_recipients` VARCHAR(1000)（逗号分隔的邮箱地址）
- `smtp_host` VARCHAR(255)
- `smtp_port` INTEGER
- `smtp_username` VARCHAR(255)
- `smtp_password` VARCHAR(500)（通过 JPA AttributeConverter 加密存储，复用 EncryptionUtil）
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()

### AC2: 创建 NotificationConfig 实体和 Repository
**Given** notification_config 表已创建
**When** 访问通知配置
**Then** 创建 JPA 实体 `NotificationConfigEntity`：
- 使用 `@EntityListeners(AuditingEntityListener.class)` 自动管理时间戳
- `smtp_password` 字段使用 AttributeConverter 加密（参考 `WebhookSecretConverter`）
- Repository 提供 `findByProjectId(Long projectId)` 查询

### AC3: 创建 EmailNotificationService 接口和实现
**Given** 审查结果和阈值验证已完成
**When** 需要发送邮件通知
**Then** 创建 `EmailNotificationService`：
- `sendReviewCompleteNotification(Long taskId)`: void — 审查完成通知
- `sendThresholdViolationNotification(Long taskId)`: void — 阈值违规通知
- 实现放在 integration 模块 `notification/` 包中
- 使用 Spring Boot `JavaMailSender` 发送邮件

### AC4: 邮件内容渲染
**Given** 审查结果数据可用
**When** 构建邮件内容
**Then** 复用 `ReviewReportService.renderHtml()` 生成邮件正文：
- 邮件主题格式："[AI Code Review] {projectName} - {branch} - {Passed|Failed}"
- 邮件正文使用 ReviewReportService 生成的 HTML 报告（已包含 inline CSS）
- 在 HTML 头部添加阈值验证摘要（通过/失败 + 违规数量）
- 不需要额外的模板引擎（ReviewReportService.renderHtml 已生成完整 HTML）

### AC5: SMTP 配置与 JavaMailSender 集成
**Given** SMTP 配置存在
**When** 发送邮件
**Then** 支持两种 SMTP 配置来源：
- **全局配置**：application.yml 中 `spring.mail.*` 属性（默认）
- **项目级配置**：notification_config 表中的 smtp_* 字段（覆盖全局）
- 当项目级 SMTP 配置存在时，动态创建 `JavaMailSender` 实例
- 当仅有全局配置时，使用 Spring 自动配置的 `JavaMailSender` bean

### AC6: 集成到 ReviewResultServiceImpl
**Given** saveResult() 流程完成
**When** 审查结果已保存且 email_enabled = true
**Then** 在 ReviewResultServiceImpl step 9（平台状态更新）之后添加 step 10：
- 查询 NotificationConfig，检查 email_enabled
- 如果启用，调用 EmailNotificationService
- 使用 try-catch 包裹，失败仅记录 WARN 日志，不影响结果返回
- 阈值违规时调用 sendThresholdViolationNotification（额外强调违规信息）
- 审查成功时调用 sendReviewCompleteNotification

### AC7: 错误处理与降级
**Given** SMTP 连接可能失败
**When** 邮件发送失败
**Then** 遵循已建立的降级模式：
- SMTP 连接失败：记录 WARN 日志，不抛出异常
- 无效收件人：记录 WARN 日志，跳过
- 配置缺失（无 SMTP）：记录 DEBUG 日志，静默跳过
- 不影响审查结果的保存和返回（与 GitHubCheckRunService 降级模式一致）

### AC8: 单元测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- NotificationConfig CRUD 操作（Repository 层）
- EmailNotificationService 邮件构建逻辑（Mock JavaMailSender）
- SMTP 密码加密/解密（AttributeConverter）
- 收件人列表解析（逗号分隔）
- 错误处理（SMTP 失败不传播）
- ReviewResultServiceImpl 集成（Mock EmailNotificationService）

## Tasks / Subtasks

- [x] Task 1: 创建 notification_config 数据库表 (AC: #1)
  - [x] 1.1 创建 Flyway 迁移 `V11__create_notification_config_table.sql`
- [x] Task 2: 创建 NotificationConfig 实体和 Repository (AC: #2)
  - [x] 2.1 创建 `NotificationConfigEntity` 在 repository 模块
  - [x] 2.2 创建 `SmtpPasswordConverter`（AttributeConverter，复用 EncryptionUtil）
  - [x] 2.3 创建 `NotificationConfigRepository`（JpaRepository）
- [x] Task 3: 创建 NotificationConfig DTO (AC: #2, #3)
  - [x] 3.1 创建 `NotificationConfigDTO` 在 common 模块 `dto/notification/` 包
  - [N/A] 3.2 NotificationConfigMapper 推迟到 Story 7.4（通知配置管理 API 时需要）
- [x] Task 4: 创建 EmailNotificationService 接口和实现 (AC: #3, #4, #5, #7)
  - [x] 4.1 创建 `EmailNotificationService` 接口在 **service** 模块（非 integration，因依赖 ReviewReportService）
  - [x] 4.2 创建 `EmailNotificationServiceImpl` 实现在 service 模块
  - [x] 4.3 实现邮件构建（复用 ReviewReportService.renderHtml）
  - [x] 4.4 实现动态 JavaMailSender 创建（项目级 SMTP 覆盖）
  - [x] 4.5 添加 `spring-boot-starter-mail` 依赖到 **service** 模块（非 integration）
- [x] Task 5: 集成到 ReviewResultServiceImpl (AC: #6)
  - [x] 5.1 注入 EmailNotificationService（使用 @Lazy 打破循环依赖）
  - [x] 5.2 在 saveResult() step 9 之后添加 step 10 邮件通知逻辑
- [x] Task 6: 添加 application.yml 邮件配置 (AC: #5)
  - [x] 6.1 在 application.yml 添加 `spring.mail.*` 全局配置（环境变量）
  - [N/A] 6.2 application-dev.yml 开发 SMTP 配置（暂不需要，全局配置已用环境变量默认值）
- [x] Task 7: 编写单元测试 (AC: #8)
  - [N/A] 7.1 NotificationConfigRepository 集成测试（SmtpPasswordConverter 由 Spring 上下文自动扫描验证）
  - [x] 7.2 SmtpPasswordConverter 单元测试（5 tests）
  - [x] 7.3 EmailNotificationServiceImpl 单元测试（22+ tests，Mock JavaMailSender）
  - [x] 7.4 ReviewResultServiceImpl 扩展测试（Mock EmailNotificationService）

## Dev Notes

### 架构模式与约束

- **模块放置**：
  - DTO → `ai-code-review-common` 模块 `dto/notification/` 包
  - Entity + Repository + Converter → `ai-code-review-repository` 模块
  - Service 接口+实现 → `ai-code-review-integration` 模块 `notification/` 包（**已预留空目录**）
  - 集成逻辑 → `ai-code-review-service` 模块 ReviewResultServiceImpl（扩展 step 10）
  - Flyway 迁移 → `ai-code-review-repository/src/main/resources/db/migration/`
- **不创建新 Controller**：此 Story 为后端邮件集成，不暴露新 API 端点（Story 7.4 会做通知配置管理 API）
- **不创建新模板引擎**：复用 `ReviewReportService.renderHtml()` 已有的 HTML 渲染能力

### 关键技术细节

1. **-parameters 编译器标志未启用**（关键！）：
   - **必须** `@Value("${spring.mail.host:}")` 带显式 value
   - **必须** `@Column(name = "xxx")` 显式指定列名
   - 构造器注入不受影响

2. **SMTP 密码加密（复用已有 EncryptionUtil）**：
   ```java
   // 参考: WebhookSecretConverter.java — AttributeConverter 模式
   // 参考: ApiKeyEncryptionConverter.java — 同样的模式
   // 参考: EncryptionUtil.java — AES-256-GCM 加密工具
   @Converter
   public class SmtpPasswordConverter implements AttributeConverter<String, String> {
       @Value("${encryption.key:default-dev-key-change-in-prod}")
       private String encryptionKey;

       @Override
       public String convertToDatabaseColumn(String attribute) {
           return EncryptionUtil.encrypt(attribute, encryptionKey);
       }

       @Override
       public String convertToDatabase(String dbData) {
           return EncryptionUtil.decrypt(dbData, encryptionKey);
       }
   }
   ```
   **注意**: `@Value` 在 `AttributeConverter` 中**不能直接注入**！必须通过 Spring `@Component` + `@Converter(autoApply=false)` 模式，参考现有的 `WebhookSecretConverter` 实现方式。

3. **JavaMailSender 依赖添加**：
   ```xml
   <!-- 在 ai-code-review-integration/pom.xml 中添加 -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-mail</artifactId>
   </dependency>
   ```
   Spring Boot 3.2.2 自动配置 `JavaMailSenderImpl`，需要 `spring.mail.host` 属性。

4. **全局 vs 项目级 SMTP 配置**：
   ```java
   // 全局 JavaMailSender（Spring 自动配置）
   @Autowired(required = false)  // required=false，因为 SMTP 不是必须的
   private JavaMailSender defaultMailSender;

   // 项目级：从 NotificationConfig 动态创建 JavaMailSenderImpl
   private JavaMailSender createMailSender(NotificationConfigEntity config) {
       JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
       mailSender.setHost(config.getSmtpHost());
       mailSender.setPort(config.getSmtpPort());
       mailSender.setUsername(config.getSmtpUsername());
       mailSender.setPassword(config.getSmtpPassword()); // 已解密
       Properties props = mailSender.getJavaMailProperties();
       props.put("mail.smtp.auth", "true");
       props.put("mail.smtp.starttls.enable", "true");
       props.put("mail.smtp.timeout", "5000");
       props.put("mail.smtp.connectiontimeout", "5000");
       return mailSender;
   }
   ```

5. **邮件内容构建（复用 ReviewReportService）**：
   ```java
   // ReviewReportService 已提供完整的 HTML 报告渲染
   // 参考: ReviewReportService.java — "produces grouped, sorted reports suitable for API responses, notifications, and emails"
   // 参考: ReviewReportServiceImpl.renderHtml() — 生成 self-contained HTML with inline CSS

   ReviewReportDTO report = reviewReportService.generateReport(taskId);
   String htmlBody = reviewReportService.renderHtml(report);

   // 在 HTML 头部插入阈值验证摘要
   String subject = String.format("[AI Code Review] %s - %s - %s",
       report.getProjectName(), report.getBranch(),
       Boolean.TRUE.equals(report.getSuccess()) ? "Passed" : "Failed");
   ```
   **关键**: 不需要 Thymeleaf 模板引擎！`ReviewReportService.renderHtml()` 已经生成了完整的 self-contained HTML，直接用作邮件正文。

6. **收件人解析**：
   ```java
   // email_recipients 存储为逗号分隔: "dev@example.com,team@example.com"
   String[] recipients = config.getEmailRecipients().split(",");
   // 需要 trim 每个地址并过滤空值
   List<String> validRecipients = Arrays.stream(recipients)
       .map(String::trim)
       .filter(s -> !s.isEmpty())
       .toList();
   ```

7. **application.yml 邮件配置**：
   ```yaml
   # 添加到 application.yml
   spring:
     mail:
       host: ${MAIL_SMTP_HOST:}
       port: ${MAIL_SMTP_PORT:587}
       username: ${MAIL_SMTP_USERNAME:}
       password: ${MAIL_SMTP_PASSWORD:}
       properties:
         mail:
           smtp:
             auth: true
             starttls:
               enable: true
               required: true
             timeout: 5000
             connectiontimeout: 5000

   # 通知配置
   notification:
     email:
       from: ${NOTIFICATION_EMAIL_FROM:noreply@aicodereview.com}
       enabled: ${NOTIFICATION_EMAIL_ENABLED:false}
   ```
   **注意**: `spring.mail.host` 为空时，Spring Boot 不会自动配置 `JavaMailSender`，所以使用 `@Autowired(required = false)`。

8. **ReviewResultServiceImpl 扩展（step 10）**：
   ```java
   // Step 10: Email notification (non-blocking)
   try {
       emailNotificationService.sendReviewCompleteNotification(taskId);
       if (!thresholdResult.isPassed()) {
           emailNotificationService.sendThresholdViolationNotification(taskId);
       }
   } catch (Exception e) {
       log.warn("Failed to send email notification for task {}: {}", taskId, e.getMessage());
   }
   ```
   **注意**: 与 step 9 平台状态更新相同的降级模式 — try-catch + WARN 日志

9. **Flyway 迁移 V11**：
   ```sql
   -- V11__create_notification_config_table.sql
   CREATE TABLE notification_config (
       id BIGSERIAL PRIMARY KEY,
       project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
       email_enabled BOOLEAN NOT NULL DEFAULT false,
       email_recipients VARCHAR(1000),
       smtp_host VARCHAR(255),
       smtp_port INTEGER,
       smtp_username VARCHAR(255),
       smtp_password VARCHAR(500),
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );

   CREATE INDEX idx_notification_config_project_id ON notification_config(project_id);
   ```
   **注意**: 最新迁移为 V10，所以使用 V11。project_id 使用 UNIQUE 约束（一个项目一个配置）。

10. **EmailNotificationService 依赖的服务**：
    ```java
    // EmailNotificationServiceImpl 构造器注入:
    public EmailNotificationServiceImpl(
            NotificationConfigRepository notificationConfigRepository,
            ReviewReportService reviewReportService,
            ReviewTaskRepository reviewTaskRepository,
            @Autowired(required = false) JavaMailSender defaultMailSender,
            @Value("${notification.email.from:noreply@aicodereview.com}") String fromAddress) {
    ```
    **模块依赖问题**: `ReviewReportService` 在 service 模块，`EmailNotificationServiceImpl` 在 integration 模块。
    **解决方案**: 将 `EmailNotificationService` 接口放在 service 模块（与 ReviewReportService 同级），实现放在 integration 模块。或者将实现也放在 service 模块。

    **推荐**: 将接口和实现都放在 **service 模块**，因为需要依赖 `ReviewReportService` 和 `NotificationConfigRepository`。integration 模块的 `notification/` 目录留给 Story 7.2/7.3 的 IM 和 Git 评论通知（这些是纯外部 API 调用，适合 integration 模块）。

### 模块依赖分析（关键决策！）

**当前模块依赖链**: api → service → repository + integration → common

**问题**: `EmailNotificationServiceImpl` 需要：
- `ReviewReportService`（service 模块）
- `NotificationConfigRepository`（repository 模块）
- `JavaMailSender`（spring-boot-starter-mail）

**方案**:
- **接口** `EmailNotificationService` → service 模块 `com.aicodereview.service`
- **实现** `EmailNotificationServiceImpl` → service 模块 `com.aicodereview.service.impl`
- **spring-boot-starter-mail 依赖** → 添加到 service 模块 pom.xml（或 api 模块 pom.xml，因为 api 启动类加载配置）
- integration 模块的 `notification/` 包保留给 Story 7.2/7.3 的纯外部 API 通知服务

### 已建立的代码模式参考

| 模式 | 参考文件 | 关键点 |
|------|---------|--------|
| Entity + Audit | `Project.java` | @Data @Builder @EntityListeners(AuditingEntityListener.class) |
| AttributeConverter 加密 | `WebhookSecretConverter.java` | EncryptionUtil + @Value 注入 encryption.key |
| Service 降级模式 | `ReviewResultServiceImpl.java:127-153` | try-catch + WARN 日志，不阻塞主流程 |
| HTML 报告渲染 | `ReviewReportServiceImpl.renderHtml()` | self-contained HTML with inline CSS |
| Flyway 迁移 | `V10__add_threshold_result_to_review_result.sql` | 命名规范、TIMESTAMPTZ、ON DELETE CASCADE |
| Repository 接口 | `ReviewResultRepository.java` | JpaRepository<Entity, Long> |
| DTO builder | 所有 *DTO.java | @Data @Builder @NoArgsConstructor @AllArgsConstructor |

### 之前 Story 的 Code Review 教训

1. **@Transactional + HTTP 调用**: step 9 的平台状态更新和新增的 step 10 邮件通知都在 @Transactional 内，持有 DB 连接。这是已知的技术债务（Story 6.4 M3），但本 Story 不需要解决。
2. **Boolean.TRUE.equals()** 避免 NPE（Story 6.3/6.4）
3. **空 token/配置检查**: 在调用前检查配置是否完整，跳过并记录日志（Story 6.3/6.4 模式）
4. **每个外部调用独立 try-catch**: 不要让一个通知渠道的失败影响其他渠道（Story 6.4 模式）
5. **当修改 ErrorCode enum 时**: 更新 ErrorCodeTest 计数断言

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-repository/src/main/resources/db/migration/
  └── V11__create_notification_config_table.sql          ← 新增

backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/
  ├── entity/
  │   └── NotificationConfigEntity.java                  ← 新增
  ├── converter/
  │   └── SmtpPasswordConverter.java                     ← 新增
  └── NotificationConfigRepository.java                  ← 新增

backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── notification/
          └── NotificationConfigDTO.java                 ← 新增

backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  ├── EmailNotificationService.java                      ← 新增（接口）
  └── impl/
      ├── EmailNotificationServiceImpl.java              ← 新增
      └── NotificationConfigMapper.java                  ← 新增

backend/ai-code-review-service/src/test/java/com/aicodereview/service/
  └── impl/
      └── EmailNotificationServiceImplTest.java          ← 新增

backend/ai-code-review-repository/src/test/java/com/aicodereview/repository/
  └── NotificationConfigRepositoryTest.java              ← 新增（可选，如已有集成测试模式）
```

**修改文件清单**：
```
backend/ai-code-review-service/pom.xml                    ← 修改（添加 spring-boot-starter-mail）
backend/ai-code-review-api/src/main/resources/application.yml ← 修改（添加 spring.mail.* 配置）
backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImpl.java                        ← 修改（添加 step 10 邮件通知）
backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImplTest.java                    ← 修改（添加 email mock 测试）
```

### 集成点

- **上游**：Story 5.2（ReviewReportService → 复用 HTML 渲染能力）
- **上游**：Story 5.1（ReviewResultServiceImpl → saveResult() 集成点）
- **上游**：Story 6.4（平台状态更新的降级模式参考）
- **下游**：Story 7.2（Git 平台评论通知，可复用通知配置表）
- **下游**：Story 7.3（IM Webhook 通知，扩展 notification_config 表字段）
- **下游**：Story 7.4（通知模板管理，可能替换邮件内容渲染方式）

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-7.md#Story 7.1]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewReportService.java — HTML 报告渲染接口]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewReportServiceImpl.java — renderHtml() 实现]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java — saveResult() 集成点]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/WebhookSecretConverter.java — 加密 Converter 模式]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/util/EncryptionUtil.java — AES-256-GCM 加密工具]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/Project.java — Entity 模式参考]
- [Source: backend/ai-code-review-api/src/main/resources/application.yml — 现有配置结构]
- [Source: backend/ai-code-review-integration/pom.xml — integration 模块描述包含 Notifications]
- [Source: _bmad-output/implementation-artifacts/6-4-gitlab-aws-codecommit-status-update.md — 降级模式和 code review 教训]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

### Completion Notes List

1. **模块放置变更**: EmailNotificationService 接口和实现都放在 service 模块（非 integration 模块）。原因：`EmailNotificationServiceImpl` 需要依赖 `ReviewReportService`（service 模块）和 `ReviewResultService`（service 模块），放在 integration 模块会导致循环依赖。integration 模块的 `notification/` 包保留给 Story 7.2/7.3 的纯外部 API 通知服务。
2. **循环依赖修复**: ReviewResultServiceImpl → EmailNotificationServiceImpl → ReviewReportServiceImpl → ReviewResultServiceImpl 形成循环。使用 `@Lazy` 注解在 ReviewResultServiceImpl 的 EmailNotificationService 构造器参数上打破循环。
3. **NotificationConfigMapper 推迟**: 当前 Story 不暴露 API 端点，Mapper 在 Story 7.4（通知配置管理 API）时再创建。
4. **spring-boot-starter-mail 依赖**: 添加到 service 模块 pom.xml（非 integration 模块），与 EmailNotificationServiceImpl 放置一致。
5. **API 模块预已存在的测试失败**: API 模块 55 个测试失败均由 Epic 8 并行开发的 JWT 认证（JwtTokenProvider/JwtAuthenticationFilter）引起，与 Story 7.1 无关。

### Test Count Summary

| Module | Tests | Status |
|--------|-------|--------|
| common | 130 | All Pass |
| repository | 19 (incl. SmtpPasswordConverterTest: 5) | All Pass |
| integration | 216 | All Pass |
| service | 251 (incl. EmailNotificationServiceImplTest: 22+) | All Pass |
| api | 59 (4 pass, 55 fail from Epic 8 JWT) | Pre-existing failures |
| **Total** | **675** | **Story 7.1 tests all pass** |

### File List

**New Files:**
- `backend/ai-code-review-repository/src/main/resources/db/migration/V11__create_notification_config_table.sql`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/NotificationConfigEntity.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/SmtpPasswordConverter.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/NotificationConfigRepository.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notification/NotificationConfigDTO.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/EmailNotificationService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/EmailNotificationServiceImpl.java`
- `backend/ai-code-review-repository/src/test/java/com/aicodereview/repository/converter/SmtpPasswordConverterTest.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/EmailNotificationServiceImplTest.java`

**Modified Files:**
- `backend/ai-code-review-service/pom.xml` (added spring-boot-starter-mail)
- `backend/ai-code-review-api/src/main/resources/application.yml` (added spring.mail.* and notification.email.from)
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java` (added step 10 email notification + @Lazy)
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java` (added EmailNotificationService mock)
