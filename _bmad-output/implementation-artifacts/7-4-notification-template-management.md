# Story 7.4: 实现通知模板管理

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 系统管理员 (System Administrator),
I want 管理通知模板（创建、查询、更新、预览渲染）,
so that 定制通知内容和格式，满足不同团队的通知需求。

## Acceptance Criteria

### AC1: 数据库 — 创建 notification_template 表
- 创建 Flyway 迁移 `V16__create_notification_template_table.sql`
- 表结构：
  - `id BIGSERIAL PRIMARY KEY`
  - `name VARCHAR(255) NOT NULL UNIQUE`
  - `channel VARCHAR(50) NOT NULL` — CHECK 约束：`(EMAIL, GIT_COMMENT, DINGTALK, SLACK, LARK)`
  - `template_content TEXT NOT NULL` — Mustache/Handlebars 格式模板内容
  - `variables JSONB` — 可用变量说明（JSON 格式，可为 null）
  - `enabled BOOLEAN NOT NULL DEFAULT TRUE`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- 为 `channel` 和 `enabled` 字段创建索引
- 为表和列添加 COMMENT

### AC2: 实体类与 Repository
- 在 `ai-code-review-repository` 模块创建 `NotificationTemplate` 实体：
  - 包路径：`com.aicodereview.repository.entity`
  - 标准 JPA 注解：`@Entity`, `@Table(name="notification_template")`, `@EntityListeners(AuditingEntityListener.class)`
  - `channel` 字段使用 `VARCHAR(50)` 存储，用 String 类型（不用枚举，保持灵活性）
  - `variables` 字段使用 `@Column(columnDefinition = "jsonb")` + `String` 类型存储 JSON
  - `@Builder.Default` 用于 `enabled = true`
  - `@CreatedDate` + `@LastModifiedDate` 用于 `createdAt` / `updatedAt`
- 创建 `NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long>`：
  - `Optional<NotificationTemplate> findByName(String name)`
  - `List<NotificationTemplate> findByChannel(String channel)`
  - `List<NotificationTemplate> findByEnabled(Boolean enabled)`
  - `List<NotificationTemplate> findByChannelAndEnabled(String channel, Boolean enabled)`

### AC3: DTO 设计（在 common 模块）
- 包路径：`com.aicodereview.common.dto.notificationtemplate`
- `CreateNotificationTemplateRequest`：
  - `name`: `@NotBlank`, `@Size(max=255)`
  - `channel`: `@NotBlank`, `@Pattern(regexp="^(EMAIL|GIT_COMMENT|DINGTALK|SLACK|LARK)$")`
  - `templateContent`: `@NotBlank`, `@Size(max=50000)`
  - `variables`: `String`（JSONB 原始字符串，可为 null）
  - `enabled`: `Boolean`（可为 null，默认 true）
- `UpdateNotificationTemplateRequest`：
  - 同上，但所有字段均为可选（无 `@NotBlank`，只有 `@Size` 等约束）
- `NotificationTemplateDTO`（响应 DTO）：
  - `id`, `name`, `channel`, `templateContent`, `variables`, `enabled`, `createdAt`, `updatedAt`

### AC4: NotificationTemplateService 接口与实现
- 在 `ai-code-review-service` 模块创建 `NotificationTemplateService` 接口（package: `com.aicodereview.service`）：
  - `NotificationTemplateDTO createTemplate(CreateNotificationTemplateRequest request)`
  - `List<NotificationTemplateDTO> listTemplates(String channel, Boolean enabled)`
  - `NotificationTemplateDTO getTemplateById(Long id)`
  - `NotificationTemplateDTO updateTemplate(Long id, UpdateNotificationTemplateRequest request)`
  - `void deleteTemplate(Long id)`
  - `PreviewRenderResponse previewTemplate(Long id, Map<String, Object> variables)`
- 创建 `NotificationTemplateServiceImpl`：
  - `@Service`, `@RequiredArgsConstructor`, `@Transactional`, `@Slf4j`
  - 使用已有的 `Handlebars` 实例（与 `PromptTemplateServiceImpl` 相同方式：`private static final Handlebars HANDLEBARS = new Handlebars()`）
  - `createTemplate()`: 检查 name 唯一性 → 验证模板语法 → 保存 → 返回 DTO
  - `listTemplates()`: 支持 channel / enabled / 组合过滤（四种查询路径，与 PromptTemplateServiceImpl 相同模式）
  - `getTemplateById()`: `@Transactional(readOnly = true)` + `@Cacheable(value = "notification-templates", key = "#p0")`
  - `updateTemplate()`: `@CacheEvict(value = "notification-templates", key = "#p0")` + name 唯一性校验 + 语法校验
  - `deleteTemplate()`: `@CacheEvict(value = "notification-templates", key = "#p0")`
  - `previewTemplate()`: `@Transactional(propagation = Propagation.NOT_SUPPORTED)` — 使用 Handlebars 渲染，返回 `PreviewRenderResponse`（包含 `renderedContent`, `renderTimeMs`）
  - 异常处理：
    - 名称重复 → `DuplicateResourceException("NotificationTemplate", "name", request.getName())`
    - ID 不存在 → `ResourceNotFoundException("NotificationTemplate", id)`
    - 模板语法错误 → `TemplateSyntaxException`

### AC5: REST API Controller
- 在 `ai-code-review-api` 模块创建 `NotificationTemplateController`（package: `com.aicodereview.api.controller`）：
  - `@RestController`, `@RequestMapping("/api/v1/notification-templates")`, `@RequiredArgsConstructor`, `@Slf4j`
  - `POST /api/v1/notification-templates`（需 `@PreAuthorize("hasRole('ADMIN')")`）— 创建模板，返回 HTTP 201
  - `GET /api/v1/notification-templates`（无需认证）— 查询列表，支持 `?channel=EMAIL&enabled=true`
  - `GET /api/v1/notification-templates/{id}`（无需认证）— 按 ID 查询
  - `PUT /api/v1/notification-templates/{id}`（需 `@PreAuthorize("hasRole('ADMIN')")`）— 更新模板
  - `DELETE /api/v1/notification-templates/{id}`（需 `@PreAuthorize("hasRole('ADMIN')")`）— 删除，返回 HTTP 200 ApiResponse.success()
  - `POST /api/v1/notification-templates/{id}/preview`（需 `@PreAuthorize("hasRole('ADMIN')")`）— 预览渲染，接收 `@RequestBody Map<String, Object> variables`
  - 所有响应使用 `ApiResponse<T>` 包装（与 PromptTemplateController 完全一致）

### AC6: 新增 `PreviewRenderResponse` DTO（如不存在）
- 检查 `com.aicodereview.common.dto.prompttemplate.PreviewResponse` 是否可复用
- 如 `PreviewResponse` 已存在（Story 1.7 中创建），则直接复用，无需新建
- `PreviewRenderResponse`（如需新建）：`renderedContent: String`, `renderTimeMs: long`

### AC7: 通知模板变量说明
- `variables` 字段存储 JSON 说明文档（非运行时绑定变量），例如：
  ```json
  {
    "project_name": "项目名称",
    "branch": "分支名",
    "commit_hash": "提交哈希",
    "total_issues": "问题总数",
    "critical_count": "严重问题数",
    "high_count": "高危问题数",
    "threshold_passed": "是否通过阈值（true/false）",
    "violations": "阈值违规列表（JSON 数组）",
    "review_url": "审查详情链接"
  }
  ```
- `previewTemplate()` 接收用户提供的 `Map<String, Object> variables` 运行时参数进行渲染

### AC8: 单元测试
- `NotificationTemplateServiceImplTest`（在 service 模块 test 目录）：
  - 创建成功
  - 创建时 name 重复 → 抛出 DuplicateResourceException
  - 创建时模板语法无效 → 抛出 TemplateSyntaxException
  - 查询列表（无过滤、按 channel、按 enabled、按 channel+enabled）
  - 按 ID 查询成功
  - 按 ID 查询不存在 → ResourceNotFoundException
  - 更新成功
  - 更新时 name 重复 → DuplicateResourceException
  - 删除成功
  - 删除不存在 → ResourceNotFoundException
  - 预览渲染成功（Handlebars 变量替换）
  - 预览渲染语法错误 → TemplateSyntaxException

## Tasks / Subtasks

- [x] Task 1: 数据库迁移 (AC: #1)
  - [x] 1.1 创建 `V16__create_notification_template_table.sql`

- [x] Task 2: 实体类与 Repository (AC: #2)
  - [x] 2.1 创建 `NotificationTemplate` JPA 实体类
  - [x] 2.2 创建 `NotificationTemplateRepository` 接口

- [x] Task 3: DTO 类 (AC: #3, #6)
  - [x] 3.1 创建 `CreateNotificationTemplateRequest`
  - [x] 3.2 创建 `UpdateNotificationTemplateRequest`
  - [x] 3.3 创建 `NotificationTemplateDTO`
  - [x] 3.4 确认 `PreviewResponse` 可复用（已存在于 dto/prompttemplate/，直接复用）

- [x] Task 4: Service 层 (AC: #4)
  - [x] 4.1 创建 `NotificationTemplateService` 接口
  - [x] 4.2 创建 `NotificationTemplateServiceImpl`（含 Redis 缓存注解）

- [x] Task 5: REST API Controller (AC: #5)
  - [x] 5.1 创建 `NotificationTemplateController`（6 个端点）

- [x] Task 6: 单元测试 (AC: #8)
  - [x] 6.1 创建 `NotificationTemplateServiceImplTest`（覆盖 AC8 所有场景，21 个测试全部通过）

- [x] Task 7: 回归测试
  - [x] 7.1 运行 common 模块测试（130 pass）
  - [x] 7.2 运行 repository 模块测试（编译通过）
  - [x] 7.3 运行 service 模块全部测试（314 pass，含 21 个新测试）
  - [x] 7.4 运行全量回归测试（common 130 + integration 261 + service 314 = 705 pass，0 失败）

## Dev Notes

### 架构模式（极其重要 — 沿用已有模式，不要重新发明）

**核心发现：Story 7.4 是 Story 1.7（`PromptTemplate` 管理）的通知领域镜像！**

开发者必须参考并完全沿用以下已实现代码的模式：

| 对比维度 | PromptTemplate（参考）| NotificationTemplate（本 Story）|
|----------|----------------------|--------------------------------|
| 实体类 | `PromptTemplate.java` | `NotificationTemplate.java` |
| Repository | `PromptTemplateRepository` | `NotificationTemplateRepository` |
| Service 接口 | `PromptTemplateService` | `NotificationTemplateService` |
| Service 实现 | `PromptTemplateServiceImpl` | `NotificationTemplateServiceImpl` |
| Controller | `PromptTemplateController` | `NotificationTemplateController` |
| DTO 包 | `dto.prompttemplate.*` | `dto.notificationtemplate.*` |
| Flyway 迁移 | `V4__create_prompt_template_table.sql` | `V16__create_notification_template_table.sql` |
| 缓存名称 | `"prompt-templates"` | `"notification-templates"` |
| 模板引擎 | `Handlebars 4.4.0` | 相同（已在 service pom 中） |

**关键区别**（NotificationTemplate vs PromptTemplate）：
- PromptTemplate 有 `category` 字段（prompt 分类），NotificationTemplate 有 `channel` 字段（通知渠道）
- PromptTemplate 无 `variables` 字段，NotificationTemplate 有 `variables JSONB`（变量说明文档）
- PromptTemplate 有 `version` 字段，NotificationTemplate **无 version**
- Channel 合法值：`EMAIL | GIT_COMMENT | DINGTALK | SLACK | LARK`（不是枚举，用 String + CHECK 约束）

### 文件位置（精确路径）

```
# 数据库迁移
backend/ai-code-review-repository/src/main/resources/db/migration/
  V16__create_notification_template_table.sql

# Repository 实体类
backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/
  NotificationTemplate.java

# Repository 接口
backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/
  NotificationTemplateRepository.java

# Common DTOs
backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notificationtemplate/
  CreateNotificationTemplateRequest.java
  UpdateNotificationTemplateRequest.java
  NotificationTemplateDTO.java

# Service 接口
backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  NotificationTemplateService.java

# Service 实现
backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/
  NotificationTemplateServiceImpl.java

# API Controller
backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/
  NotificationTemplateController.java

# 单元测试
backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/
  NotificationTemplateServiceImplTest.java
```

### 关键技术约束

1. **Handlebars 版本**：`com.github.jknack:handlebars:4.4.0`（已在 `ai-code-review-service/pom.xml` 第 35-40 行声明，无需新增依赖）

2. **Redis 缓存配置**：Redis 缓存已配置（`RedisConfig.java`），TTL 10 分钟，使用 Spring 的 `@Cacheable`/`@CacheEvict`。缓存名称用 `"notification-templates"` 以避免与已有 `"prompt-templates"` 冲突。

3. **JSONB 存储**：`variables` 字段使用 PostgreSQL `jsonb` 类型，在 JPA 实体中用 `@Column(columnDefinition = "jsonb")` + `String` 存储，不需要额外序列化（由调用方提供 JSON 字符串）。

4. **`@Transactional(readOnly = true)`**：`listTemplates()` 和 `getTemplateById()` 方法必须加此注解（防止 LazyInitializationException，Story 7.2/7.3 教训）。

5. **`-parameters` 编译参数**：与 PromptTemplateServiceImpl 一样，`@Value` 注解无需额外配置（本 Story 无配置注入）。

6. **`PreviewResponse` 复用**：`PreviewResponse` 类已在 `com.aicodereview.common.dto.prompttemplate.PreviewResponse` 中实现（Story 1.7），**直接复用**，不要重复创建。只需在 `NotificationTemplateService.previewTemplate()` 返回 `PreviewResponse` 即可。

7. **`TemplateSyntaxException` 复用**：`com.aicodereview.common.exception.TemplateSyntaxException` 已存在，直接使用。

8. **Maven 编译环境**：
   - ⚠️ **必须使用 Java 17**：Maven 默认使用 Homebrew Java 25，Lombok 不兼容 Java 25
   - 所有 Maven 命令必须加前缀：`JAVA_HOME=/Users/ethan/Library/Java/JavaVirtualMachines/corretto-17.0.9/Contents/Home mvn ...`
   - 正确命令示例：`JAVA_HOME=/Users/ethan/Library/Java/JavaVirtualMachines/corretto-17.0.9/Contents/Home mvn -f /Users/ethan/Desktop/PRD/ai-code-review/backend/pom.xml -pl ai-code-review-service test`

9. **`channel` 字段设计决策**：使用 `String` 而非枚举类型存储 channel，配合 DB CHECK 约束 + Controller 层 `@Pattern` 验证，避免未来新增渠道时需要修改枚举。

### 现有代码参考片段

**实体类模板（参考 NotificationConfigEntity）**：
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "notification_template")
@EntityListeners(AuditingEntityListener.class)
public class NotificationTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "template_content", columnDefinition = "TEXT", nullable = false)
    private String templateContent;

    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

**Handlebars 使用模式（直接从 PromptTemplateServiceImpl 复制）**：
```java
private static final Handlebars HANDLEBARS = new Handlebars();

private void validateTemplateSyntax(String templateContent) {
    try {
        HANDLEBARS.compileInline(templateContent);
    } catch (IOException | HandlebarsException e) {
        throw new TemplateSyntaxException("Invalid Mustache template syntax: " + e.getMessage(), e);
    }
}

// previewTemplate() 中：
Template compiled = HANDLEBARS.compileInline(template.getTemplateContent());
String rendered = compiled.apply(variables != null ? variables : Map.of());
```

**Redis 缓存注解（直接从 PromptTemplateServiceImpl 复制）**：
```java
@Transactional(readOnly = true)
@Cacheable(value = "notification-templates", key = "#p0")
public NotificationTemplateDTO getTemplateById(Long id) { ... }

@CacheEvict(value = "notification-templates", key = "#p0")
public NotificationTemplateDTO updateTemplate(Long id, ...) { ... }

@CacheEvict(value = "notification-templates", key = "#p0")
public void deleteTemplate(Long id) { ... }
```

**previewTemplate 中使用 `Propagation.NOT_SUPPORTED`（防止事务 + IO 操作冲突）**：
```java
@Override
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public PreviewResponse previewTemplate(Long id, Map<String, Object> variables) { ... }
```

**Controller 模板（从 PromptTemplateController 复制）**：
```java
@Slf4j @RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {
    private final NotificationTemplateService notificationTemplateService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationTemplateDTO>> createTemplate(
            @Valid @RequestBody CreateNotificationTemplateRequest request) { ... }
    // ... 其余端点
}
```

### SQL 迁移模板

```sql
-- V16__create_notification_template_table.sql
CREATE TABLE IF NOT EXISTS notification_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    channel VARCHAR(50) NOT NULL CONSTRAINT chk_notification_template_channel
        CHECK (channel IN ('EMAIL', 'GIT_COMMENT', 'DINGTALK', 'SLACK', 'LARK')),
    template_content TEXT NOT NULL,
    variables JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_template_channel ON notification_template(channel);
CREATE INDEX idx_notification_template_enabled ON notification_template(enabled);
```

### 前序 Story 教训（必须遵守）

来自 Story 7.3 Code Review 发现的规范，本 Story 必须从一开始就遵守：

- **M1（文件记录）**：修改/创建的所有文件必须完整记录到 File List
- **M2（日志安全）**：API 响应体记录到日志时截断至 200 字符
- **M3（事务注解）**：`listTemplates()` 和 `getTemplateById()` 必须加 `@Transactional(readOnly = true)`
- **M4（枚举排序）**：如有排序场景，使用枚举的 `getScore()` 或 `values()` 而非 `ordinal()` 或 `Map.entrySet()`
- **M5（入口防御）**：`createTemplate()` 和 `updateTemplate()` 检查传入 ID 存在性
- **H1（Null 防御）**：所有方法对关键参数做 null 防护（`variables` 可为 null）
- **H2（字段 Null 防护）**：`toDTO()` 中对可能为 null 的字段用 null 安全处理

### Project Structure Notes

- **新建模块分布**：
  - `ai-code-review-repository`：`NotificationTemplate.java` + `NotificationTemplateRepository.java` + `V16` 迁移
  - `ai-code-review-common`：`dto/notificationtemplate/` 下 3 个 DTO 类（`PreviewResponse` 直接复用 `dto/prompttemplate/`）
  - `ai-code-review-service`：`NotificationTemplateService` 接口 + `NotificationTemplateServiceImpl` + `NotificationTemplateServiceImplTest`
  - `ai-code-review-api`：`NotificationTemplateController`

- **无需新增依赖**：
  - Handlebars 4.4.0 已在 service pom.xml 声明（第 35-40 行）
  - Redis/Spring Cache 已配置（`RedisConfig.java`）
  - Jakarta Validation 已在所有模块可用
  - 无需修改任何 pom.xml

- **不需要修改的现有文件**：
  - `ReviewResultServiceImpl.java` — IM 通知已在 Story 7.3 集成
  - `NotificationConfigEntity.java` — 已完整（Story 7.3）
  - 任何现有 Service 或 Repository

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-7.md#Story 7.4] — Epic 原始需求
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/PromptTemplateServiceImpl.java] — **关键参考：完整实现模式（Handlebars + Redis 缓存）**
- [Source: backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/PromptTemplateController.java] — **关键参考：Controller 实现模式**
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/] — DTO 模式参考
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/PromptTemplate.java] — 实体类模式参考
- [Source: backend/ai-code-review-repository/src/main/resources/db/migration/V4__create_prompt_template_table.sql] — Flyway 迁移 SQL 模式参考
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/RedisConfig.java] — Redis 缓存配置（TTL 10 分钟）
- [Source: backend/ai-code-review-service/pom.xml#L35-40] — Handlebars 依赖声明
- [Source: _bmad-output/implementation-artifacts/7-3-im-webhook-notifications.md#Dev Notes] — 前序 Story 教训

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Maven 编译环境：必须使用 JAVA_HOME=corretto-17.0.9，Homebrew Java 25 与 Lombok 1.18.30 不兼容
- 初次运行 service 测试时，需先 `mvn install` common 和 repository 模块以确保依赖已在本地仓库
- 705 tests pass (common 130 + integration 261 + service 314)，其中新增 21 个 NotificationTemplate 测试
- Code review 修复后：711 tests pass (common 130 + integration 261 + service 320)，service 新增 6 个测试

### Completion Notes List

- AC1: V16__create_notification_template_table.sql 创建成功，含 notification_template 表、CHECK 约束（channel IN EMAIL/GIT_COMMENT/DINGTALK/SLACK/LARK）、channel 和 enabled 索引、COMMENT
- AC2: NotificationTemplate 实体类（含 variables JSONB 字段，@Builder.Default enabled=true）+ NotificationTemplateRepository（4 个查询方法）
- AC3: 3 个 DTO 类创建（CreateNotificationTemplateRequest、UpdateNotificationTemplateRequest、NotificationTemplateDTO）；PreviewResponse 直接复用 dto/prompttemplate/PreviewResponse
- AC4: NotificationTemplateService 接口 + NotificationTemplateServiceImpl（Handlebars 4.4.0 模板渲染、Redis @Cacheable/@CacheEvict、@Transactional(readOnly=true) 防 LazyInit）
- AC5: NotificationTemplateController（6 个端点：POST/GET/GET{id}/PUT/DELETE/POST{id}/preview，ADMIN 鉴权，ApiResponse 包装）
- AC6: 直接复用 PreviewResponse（renderedContent, renderTimeMs）
- AC7: variables 字段作为 JSONB String 存储变量说明文档
- AC8: NotificationTemplateServiceImplTest — 21 个测试覆盖全部 AC8 场景（创建/重名/语法错误/列表4路径/查询/更新/删除/预览/null变量/DTO null字段）

### Change Log

- 2026-02-20: Story 7.4 完成实现，TDD 开发（RED→GREEN），705 tests pass，状态更新为 review
- 2026-02-20: Code review 完成，修复 4 个 Important 问题，新增 6 个测试，711 tests pass，状态更新为 done
  - I1: listTemplates() per-branch log.debug() 已存在（确认）
  - I2: 新增 validateVariablesJson() — variables 字段 JSON 格式验证，防止无效 JSON 触发 DB 500 错误
  - I3: NotificationTemplateServiceImpl 和 Controller 新增 class-level Javadoc（含安全模型说明）
  - I4: previewTemplate 端点 @RequestBody 改为 required=false（允许无 body 调用预览）
  - I5: 新增 6 个测试（enabled=false 创建、无效 variables JSON、空列表返回、全 null 更新、同名更新、空 Map 预览）

### File List

- backend/ai-code-review-repository/src/main/resources/db/migration/V16__create_notification_template_table.sql
- backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/NotificationTemplate.java
- backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/NotificationTemplateRepository.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notificationtemplate/CreateNotificationTemplateRequest.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notificationtemplate/UpdateNotificationTemplateRequest.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/notificationtemplate/NotificationTemplateDTO.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/NotificationTemplateService.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/NotificationTemplateServiceImpl.java
- backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/NotificationTemplateController.java
- backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/NotificationTemplateServiceImplTest.java

