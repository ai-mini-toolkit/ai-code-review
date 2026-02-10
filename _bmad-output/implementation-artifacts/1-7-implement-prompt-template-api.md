# Story 1.7: 实现 Prompt 模板管理后端 API

**Status:** done

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 系统管理员,
I want to 通过 API 管理 Prompt 模板（六维度审查 Prompt），
So that 我可以定制 AI 审查的提示词和输出格式。
```

**业务价值:**
此故事实现了 AI 代码审查系统的第三个核心业务实体 - **Prompt 模板管理**。这是六维度智能审查的基础：
1. **六维度审查模板** - 每个审查维度（security/performance/maintainability/correctness/style/best_practices）可配置独立的 Prompt 模板
2. **模板变量渲染** - 使用 Mustache 模板语法支持动态变量替换，灵活适应不同审查场景
3. **模板预览** - 提供预览端点，使用示例数据渲染模板，方便管理员调试和优化 Prompt
4. **模板版本管理** - 通过 version 字段追踪模板演化，支持模板迭代优化
5. **Redis 缓存** - 模板缓存到 Redis（TTL 10 分钟），减少 AI 审查时的数据库查询

此故事完全复用 Story 1.5/1.6 建立的 CRUD 模式，同时扩展支持模板渲染预览功能。

**Story ID:** 1.7
**Priority:** HIGH - 阻塞 Epic 4（AI 智能审查引擎）的六维度审查编排
**Complexity:** Medium
**Dependencies:**
- Story 1.3 (PostgreSQL & JPA 已配置完成) ✅
- Story 1.4 (Redis & Caching 已配置完成) ✅
- Story 1.5 (项目配置 API 已完成 - 建立了 CRUD 模式) ✅
- Story 1.6 (AI 模型配置 API 已完成 - 巩固了 CRUD + 特殊端点模式) ✅

---

## ✅ Acceptance Criteria (验收标准)

**Given** AI 模型配置 API 已实现（Story 1.6 完成）
**When** 实现 Prompt 模板管理 API
**Then** 以下验收标准必须全部满足：

### AC 1: 数据库模式（Database Schema）
- [x] 创建 `prompt_template` 表
- [x] 字段：`id` BIGSERIAL PRIMARY KEY
- [x] 字段：`name` VARCHAR(255) NOT NULL UNIQUE（模板名称）
- [x] 字段：`category` VARCHAR(50) NOT NULL（审查维度：security/performance/maintainability/correctness/style/best_practices）
- [x] 字段：`template_content` TEXT NOT NULL（Mustache 模板内容）
- [x] 字段：`version` INT NOT NULL DEFAULT 1（模板版本号）
- [x] 字段：`enabled` BOOLEAN NOT NULL DEFAULT TRUE
- [x] 字段：`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [x] 字段：`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [x] CHECK 约束：`category IN ('security','performance','maintainability','correctness','style','best_practices')`
- [x] 索引：`idx_prompt_template_category` ON category
- [x] 索引：`idx_prompt_template_enabled` ON enabled
- [x] Flyway 迁移脚本：`V4__create_prompt_template_table.sql`

### AC 2: JPA Entity 实现
- [x] 创建 `PromptTemplate.java` 实体类（`com.aicodereview.repository.entity`）
- [x] 使用 `@Entity` 和 `@Table(name = "prompt_template")` 注解
- [x] 所有字段包含 `@Column` 注解（name 映射 snake_case）
- [x] `template_content` 使用 `@Column(columnDefinition = "TEXT")` 支持大文本
- [x] `@CreatedDate` 和 `@LastModifiedDate`（JPA Auditing 已由 Story 1.5 启用）
- [x] `@EntityListeners(AuditingEntityListener.class)`
- [x] Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [x] `@Builder.Default` 用于 version=1、enabled=true

### AC 3: JPA Repository 实现
- [x] 创建 `PromptTemplateRepository.java` 接口（`com.aicodereview.repository`）
- [x] 继承 `JpaRepository<PromptTemplate, Long>`
- [x] 自定义查询：`Optional<PromptTemplate> findByName(String name)`
- [x] 自定义查询：`List<PromptTemplate> findByCategory(String category)`
- [x] 自定义查询：`List<PromptTemplate> findByEnabled(Boolean enabled)`
- [x] 自定义查询：`List<PromptTemplate> findByCategoryAndEnabled(String category, Boolean enabled)`

### AC 4: Service 层实现
- [x] 创建 `PromptTemplateService.java` 接口（`com.aicodereview.service`）
- [x] 创建 `PromptTemplateServiceImpl.java` 实现类
- [x] 方法：`PromptTemplateDTO createPromptTemplate(CreatePromptTemplateRequest request)`
- [x] 方法：`List<PromptTemplateDTO> listPromptTemplates(Boolean enabled, String category)`
- [x] 方法：`PromptTemplateDTO getPromptTemplateById(Long id)`
- [x] 方法：`PromptTemplateDTO updatePromptTemplate(Long id, UpdatePromptTemplateRequest request)`
- [x] 方法：`void deletePromptTemplate(Long id)`
- [x] 方法：`PreviewResponse previewTemplate(Long id, Map<String, Object> sampleData)`
- [x] `@Cacheable(value = "prompt-templates", key = "#p0")` 缓存 getById
- [x] `@CacheEvict(value = "prompt-templates", key = "#p0")` 清除缓存（更新、删除时）
- [x] 名称唯一性检查（DuplicateResourceException）
- [x] 不存在时抛出 ResourceNotFoundException
- [x] 模板语法验证（创建和更新时）

### AC 5: Controller 层实现
- [x] 创建 `PromptTemplateController.java`（`com.aicodereview.api.controller`）
- [x] 使用 `@RestController` 和 `@RequestMapping("/api/v1/prompt-templates")`
- [x] POST `/api/v1/prompt-templates` → 201 Created
- [x] GET `/api/v1/prompt-templates` → 200 OK（支持 enabled、category 查询参数）
- [x] GET `/api/v1/prompt-templates/{id}` → 200 OK
- [x] PUT `/api/v1/prompt-templates/{id}` → 200 OK
- [x] DELETE `/api/v1/prompt-templates/{id}` → 200 OK
- [x] POST `/api/v1/prompt-templates/{id}/preview` → 200 OK（模板预览渲染）
- [x] 所有响应使用 `ApiResponse<T>` 统一格式
- [x] 使用 `@Valid` 进行请求验证

### AC 6: DTO 类实现
- [x] 创建 `PromptTemplateDTO.java`（`com.aicodereview.common.dto.prompttemplate`）
- [x] 创建 `CreatePromptTemplateRequest.java`（验证注解）
- [x] 创建 `UpdatePromptTemplateRequest.java`（所有字段可选）
- [x] 创建 `PreviewResponse.java`（预览渲染结果）

### AC 7: Mustache 模板渲染
- [x] 添加 Handlebars.java 依赖到 service 模块
- [x] 创建时验证模板语法（compileInline 不抛异常）
- [x] 更新时如有 templateContent 变更则验证语法
- [x] 语法错误返回 422 Validation Error

### AC 8: 模板预览端点
- [x] POST `/api/v1/prompt-templates/{id}/preview` 接收 JSON body 作为示例数据
- [x] 加载模板内容，使用 Handlebars 渲染
- [x] 返回 `PreviewResponse`：renderedContent（String）、renderTimeMs（Long）
- [x] 渲染失败返回 success=false 和错误描述

### AC 9: Redis 缓存配置
- [x] 模板缓存到 Redis（cacheName="prompt-templates"）
- [x] 缓存 TTL：10 分钟（从 RedisCacheManager 继承）
- [x] 更新或删除时自动清除缓存

### AC 10: 集成测试
- [x] 创建 `PromptTemplateControllerIntegrationTest.java`
- [x] 测试用例：POST 创建模板 → 201
- [x] 测试用例：GET 列出模板 → 200
- [x] 测试用例：GET 按 category 过滤 → 200
- [x] 测试用例：GET 获取详情 → 200
- [x] 测试用例：PUT 更新模板 → 200
- [x] 测试用例：DELETE 删除模板 → 200
- [x] 测试用例：POST 重复名称 → 409 Conflict
- [x] 测试用例：GET 不存在 ID → 404 Not Found
- [x] 测试用例：POST 缺失必填字段 → 422 Validation Error
- [x] 测试用例：POST preview 模板预览渲染
- [x] 测试用例：POST 无效 Mustache 语法 → 422
- [x] 测试用例：验证 Redis 缓存生效
- [x] 所有测试通过

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 添加 Handlebars.java 依赖 (AC: #7)
- [x] 在 `backend/ai-code-review-service/pom.xml` 中添加 `com.github.jknack:handlebars:4.4.0` 依赖
- [x] 运行 `mvn compile` 验证依赖下载成功

### Task 2: 创建 Flyway 数据库迁移脚本 (AC: #1)
- [x] 创建 `backend/ai-code-review-repository/src/main/resources/db/migration/V4__create_prompt_template_table.sql`
- [x] 定义 `prompt_template` 表结构（template_content 使用 TEXT 类型）
- [x] 添加 CHECK 约束验证 category 枚举值
- [x] 创建索引（idx_prompt_template_category, idx_prompt_template_enabled）
- [x] 注意：name 列有 UNIQUE 约束，不需要额外的 name 索引（Story 1.6 code review 教训）
- [x] 添加表和列注释

### Task 3: 实现 JPA Entity 和 Repository (AC: #2, #3)
- [x] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/PromptTemplate.java`
- [x] 实现 JPA Entity，包含所有字段（参照 AiModelConfig.java 模式）
- [x] `template_content` 使用 `@Column(name = "template_content", columnDefinition = "TEXT", nullable = false)`
- [x] `@Builder.Default` 用于 version=1、enabled=true
- [x] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/PromptTemplateRepository.java`
- [x] 定义自定义查询（findByName, findByCategory, findByEnabled, findByCategoryAndEnabled）

### Task 4: 实现 DTO 类和验证 (AC: #6)
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/PromptTemplateDTO.java`
  - 包含：id, name, category, templateContent, version, enabled, createdAt, updatedAt
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/CreatePromptTemplateRequest.java`
  - `@NotBlank`: name, category, templateContent
  - `@Pattern(regexp = "^(security|performance|maintainability|correctness|style|best_practices)$")`: category
  - `@Size(max = 255)`: name
  - `@Size(max = 10000)`: templateContent
  - 可选字段：version, enabled
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/UpdatePromptTemplateRequest.java`
  - 所有字段可选，仅 `@Size`、`@Pattern` 约束
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/PreviewResponse.java`
  - 字段：renderedContent（String）、renderTimeMs（Long）

### Task 5: 实现 Service 层 (AC: #4, #7, #8, #9)
- [x] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/PromptTemplateService.java`（接口）
- [x] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/PromptTemplateServiceImpl.java`（实现）
- [x] 实现 CRUD 方法：create, list（支持 enabled + category 过滤）, getById, update, delete
- [x] 实现 previewTemplate 方法：
  1. 加载模板配置
  2. 使用 Handlebars.compileInline 渲染模板
  3. 记录渲染时间
  4. 返回 PreviewResponse
- [x] 实现 validateTemplateSyntax 私有方法：
  1. 使用 Handlebars.compileInline 编译模板
  2. 编译失败抛出 MethodArgumentNotValidException 或自定义异常返回 422
- [x] Entity ↔ DTO 转换（private toDTO 方法）
- [x] `@Cacheable(value = "prompt-templates", key = "#p0")` 缓存 getById
- [x] `@CacheEvict(value = "prompt-templates", key = "#p0")` 在 update 和 delete
- [x] 名称唯一性检查（DuplicateResourceException）
- [x] 日志记录（@Slf4j）
- [x] previewTemplate 方法使用 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`（Story 1.6 code review 教训：避免在事务中执行模板渲染等非 DB 操作）

### Task 6: 实现 Controller 层 (AC: #5)
- [x] 创建 `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/PromptTemplateController.java`
- [x] 6 个 REST 端点：POST, GET list, GET by id, PUT, DELETE, POST preview
- [x] `@RequestMapping("/api/v1/prompt-templates")`
- [x] 所有响应包装为 `ApiResponse<T>`
- [x] 请求体使用 `@Valid` 验证
- [x] GET list 支持可选查询参数：`enabled`、`category`
- [x] Preview 端点：`@PostMapping("/{id}/preview")` 接收 `@RequestBody Map<String, Object> sampleData`

### Task 7: 编写集成测试 (AC: #10)
- [x] 创建 `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/PromptTemplateControllerIntegrationTest.java`
- [x] 使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- [x] `@BeforeAll` 清理：`promptTemplateRepository.deleteAll()`
- [x] 12+ 测试用例覆盖所有 CRUD + 预览 + 语法验证 + 缓存
- [x] 运行全部测试验证通过

### Task 8: 运行完整测试套件
- [x] `cd backend && mvn test` 确保无回归
- [x] 更新 ErrorCodeTest 计数断言（如有新增 ErrorCode）→ 无需新增
- [x] 验证所有 68+ 现有测试 + 新测试全部通过

---

## 💻 Dev Notes (开发注意事项)

### 架构约束

**模块职责（严格遵守 Story 1.1 建立的规则）:**
- `ai-code-review-common`: DTO、异常类、工具类 → **PromptTemplateDTO, CreatePromptTemplateRequest, UpdatePromptTemplateRequest, PreviewResponse**
- `ai-code-review-repository`: 数据层 → **PromptTemplate Entity, PromptTemplateRepository**
- `ai-code-review-service`: 业务逻辑 → **PromptTemplateService, PromptTemplateServiceImpl**（Handlebars 依赖放在此模块）
- `ai-code-review-api`: REST 控制器 → **PromptTemplateController**

**模块依赖方向（严格遵守，不允许反向依赖）:**
```
api → service → repository → common
                              ↑
api ──────────────────────────┘
```

### 已有代码模式（必须完全复用）

**1. API 响应格式（ApiResponse.java 已存在）:**
```java
// 成功
ApiResponse.success(data)      // 有数据
ApiResponse.success()          // 无数据（DELETE）
// 错误
ApiResponse.error(ErrorCode.NOT_FOUND, "Prompt template not found")
```

**2. 错误码（ErrorCode.java 已存在，7 个枚举值）:**
- `ERR_404` NOT_FOUND → 模板不存在
- `ERR_409` CONFLICT → 名称重复
- `ERR_422` VALIDATION_ERROR → 请求验证失败 / 模板语法错误
- `ERR_500` INTERNAL_SERVER_ERROR → 内部错误
- 无需新增 ErrorCode，现有枚举已覆盖所有场景

**3. 异常类（已存在，直接复用）:**
```java
throw new ResourceNotFoundException("PromptTemplate", id);
throw new DuplicateResourceException("PromptTemplate", "name", request.getName());
```

**4. GlobalExceptionHandler（已存在，无需修改）:**
- ResourceNotFoundException → 404
- DuplicateResourceException → 409
- MethodArgumentNotValidException → 422

**5. SpEL 缓存 key 必须使用 `#p0`（-parameters 未启用）:**
```java
@Cacheable(value = "prompt-templates", key = "#p0")   // ✅ 正确
@CacheEvict(value = "prompt-templates", key = "#p0")  // ✅ 正确
// @Cacheable(value = "prompt-templates", key = "#id") // ❌ 错误！运行时会失败
```

**6. @PathVariable 和 @RequestParam 必须显式指定 value:**
```java
@PathVariable("id") Long id                           // ✅ 必须
@RequestParam(value = "enabled", required = false)     // ✅ 必须
@RequestParam(value = "category", required = false)    // ✅ 必须
```

**7. Flyway 迁移版本号:**
- V1: init_schema（Story 1.3）
- V2: create_project_table（Story 1.5）
- V3: create_ai_model_config_table（Story 1.6）
- **V4: create_prompt_template_table（本 Story）**

**8. 数据库设计教训（Story 1.6 code review）:**
- name 列有 UNIQUE 约束时**不要**创建额外的 name 索引（PostgreSQL 自动为 UNIQUE 创建索引）
- 枚举字段添加 DB 层 CHECK 约束（defense-in-depth）

### Handlebars.java（Mustache 模板引擎）使用说明

**依赖（添加到 ai-code-review-service/pom.xml）:**
```xml
<dependency>
    <groupId>com.github.jknack</groupId>
    <artifactId>handlebars</artifactId>
    <version>4.4.0</version>
</dependency>
```

**核心 API 使用模式:**
```java
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

// 创建引擎（类级别，可重用）
private final Handlebars handlebars = new Handlebars();

// 渲染模板
public String renderTemplate(String templateContent, Map<String, Object> data) throws IOException {
    Template template = handlebars.compileInline(templateContent);
    return template.apply(data);
}

// 验证语法（编译不抛异常 = 语法正确）
public void validateTemplateSyntax(String templateContent) {
    try {
        handlebars.compileInline(templateContent);
    } catch (IOException e) {
        throw new IllegalArgumentException("Invalid Mustache template syntax: " + e.getMessage());
    }
}
```

**Mustache 语法示例（供测试使用）:**
```mustache
Review {{file_name}} for {{category}} issues:
{{#each issues}}
- Line {{line}}: {{description}} (severity: {{severity}})
{{/each}}

Summary: Found {{issue_count}} {{category}} issues in {{file_name}}.
```

### 模板语法验证错误处理

**方案：** 在 Service 层 create/update 时调用 `validateTemplateSyntax`。如果语法无效，使用以下方式返回 422：

```java
// 选项 A：抛出自定义异常，GlobalExceptionHandler 捕获
// 需要在 GlobalExceptionHandler 中添加 IllegalArgumentException → 422 映射

// 选项 B（推荐）：利用已有 VALIDATION_ERROR 错误码
// 在 Service 中抛 IllegalArgumentException，
// 在 GlobalExceptionHandler 中添加处理或直接在 Controller 中 try-catch
```

**推荐方案：** 在 `GlobalExceptionHandler` 中添加 `IllegalArgumentException` 处理（如尚无），返回 422 错误。或者如果已有此处理，直接在 Service 中抛出。需要检查 GlobalExceptionHandler 是否已处理此异常类型。

### 技术栈版本

- Spring Boot: 3.2.2
- Java: 17
- PostgreSQL: 18-alpine（Docker）
- Redis: 7-alpine（Docker）
- Flyway: 9.22.3（Spring Boot 管理）
- Hibernate: 6.4.1（Spring Boot 管理）
- **Handlebars.java: 4.4.0**（新增依赖）
- Lombok: Spring Boot 管理版本
- Jackson: Spring Boot 管理版本（含 jackson-datatype-jsr310）

### Previous Story 学习（Story 1.6 关键教训）

**必须遵守：**
1. **冗余索引** - name 有 UNIQUE 时不要创建额外 name 索引
2. **DB CHECK 约束** - 枚举字段添加 CHECK 约束（category 字段）
3. **@Transactional propagation** - 非 DB 操作（如模板渲染）使用 `Propagation.NOT_SUPPORTED`，避免持有 DB 连接
4. **HttpClient 复用** - 如需 HTTP 调用，使用类级别静态客户端（本 Story 不需要）
5. **@Size 限制** - 所有字符串字段添加 @Size 约束，避免超出 DB 列长度
6. **组合过滤** - list 端点如接受多个查询参数，必须支持组合过滤（findByCategoryAndEnabled）
7. **异常细分** - 避免宽泛的 catch(Exception)，分别处理具体异常类型

**已解决的问题（无需重复排查）：**
- Jackson `java.time.Instant` 序列化 → JavaTimeModule 已注册
- -parameters 编译器标志 → 使用 `#p0` 和显式 `@PathVariable("id")`
- 测试数据持久化 → `@BeforeAll` cleanup
- Redis 序列化配置 → RedisConfig 已完成

### Project Structure Notes

**本次新增文件列表:**
```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   └── dto/prompttemplate/
│       ├── PromptTemplateDTO.java            (DTO)
│       ├── CreatePromptTemplateRequest.java  (Request DTO)
│       ├── UpdatePromptTemplateRequest.java  (Request DTO)
│       └── PreviewResponse.java             (Response DTO)
├── ai-code-review-repository/src/main/
│   ├── java/com/aicodereview/repository/
│   │   ├── entity/
│   │   │   └── PromptTemplate.java           (JPA Entity)
│   │   └── PromptTemplateRepository.java     (JPA Repository)
│   └── resources/db/migration/
│       └── V4__create_prompt_template_table.sql (Flyway)
├── ai-code-review-service/
│   ├── pom.xml                               (修改：添加 handlebars 依赖)
│   └── src/main/java/com/aicodereview/service/
│       ├── PromptTemplateService.java        (接口)
│       └── impl/
│           └── PromptTemplateServiceImpl.java (实现)
└── ai-code-review-api/src/
    ├── main/java/com/aicodereview/api/controller/
    │   └── PromptTemplateController.java      (REST Controller)
    └── test/java/com/aicodereview/api/controller/
        └── PromptTemplateControllerIntegrationTest.java (集成测试)
```

**修改已有文件:**
- `backend/ai-code-review-service/pom.xml` → 添加 handlebars 依赖
- 可能修改 `GlobalExceptionHandler.java` → 如需添加 IllegalArgumentException → 422 映射

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-1.md#Story 1.7] - Prompt 模板 API 需求定义
- [Source: _bmad-output/planning-artifacts/architecture.md#Section 1.5] - Redis 缓存策略（Review Templates TTL 1 小时）
- [Source: _bmad-output/planning-artifacts/architecture.md#Six-Dimension Review] - 六维度审查并发策略
- [Source: _bmad-output/implementation-artifacts/1-6-implement-ai-model-config-api.md] - Story 1.6 CRUD 模式和 code review 教训
- [Source: backend/ai-code-review-repository/src/main/java/.../entity/AiModelConfig.java] - Entity 模式参考
- [Source: backend/ai-code-review-repository/src/main/java/.../AiModelConfigRepository.java] - Repository 模式参考
- [Source: backend/ai-code-review-service/src/main/java/.../impl/AiModelConfigServiceImpl.java] - Service 实现模式参考
- [Source: backend/ai-code-review-api/src/main/java/.../controller/AiModelController.java] - Controller 模式参考
- [Source: backend/ai-code-review-api/src/test/java/.../AiModelControllerIntegrationTest.java] - 集成测试模式参考
- [Source: backend/ai-code-review-repository/src/main/resources/db/migration/V3__create_ai_model_config_table.sql] - 迁移脚本模式参考

---

## 🏗️ Implementation Strategy (实现策略)

### 实现顺序（推荐）

1. **Task 1**: Handlebars 依赖 → 确保模板引擎可用
2. **Task 2**: Flyway 迁移 → 确保数据库表就绪
3. **Task 3**: Entity + Repository → 数据层
4. **Task 4**: DTO 类 → 数据传输对象
5. **Task 5**: Service 层 → 业务逻辑 + 模板渲染 + 语法验证
6. **Task 6**: Controller 层 → 6 个 API 端点
7. **Task 7**: 集成测试 → 12+ 测试用例
8. **Task 8**: 完整测试套件 → 回归测试

### 测试策略

- **集成测试**: PromptTemplateControllerIntegrationTest（完整 API 流程 + 预览 + 语法验证 + 缓存）
- **回归测试**: 运行所有现有 68 个测试确保无破坏

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Initial test run: 1 failure - `shouldReturn422ForInvalidMustacheSyntax` returned 500 instead of 422
- Root cause: Handlebars throws `HandlebarsException` (RuntimeException) for syntax errors, not `IOException`
- Fix: Added `HandlebarsException` catch clause in both `validateTemplateSyntax` and `previewTemplate` methods
- Second test run: All 80 tests pass (25 common + 14 repository + 41 API)

### Completion Notes List

1. Added `TemplateSyntaxException` custom exception + GlobalExceptionHandler mapping to 422 (story originally suggested IllegalArgumentException, but that's already mapped to 400)
2. Handlebars.java `compileInline()` throws `HandlebarsException` (RuntimeException) for template syntax errors, not `IOException` - must catch both
3. Static `Handlebars` instance (`private static final Handlebars HANDLEBARS`) reused across calls - thread-safe per Handlebars docs
4. `previewTemplate` uses `@Transactional(propagation = Propagation.NOT_SUPPORTED)` per Story 1.6 code review lesson
5. All 14 integration test cases passing: CRUD (6) + duplicate name (1) + not found (1) + validation (1) + preview (1) + invalid syntax (1) + cache (1) + enabled filter (1) + combined filter (1)

### Code Review Fixes Applied

| ID | Severity | Description | Fix |
|----|----------|-------------|-----|
| M1 | MEDIUM | Dead variable `elapsed` in previewTemplate catch blocks | Removed unused variable |
| M2 | MEDIUM | Duplicate IOException/HandlebarsException catch blocks | Combined using multi-catch `catch (IOException \| HandlebarsException e)` |
| M3 | MEDIUM | UpdatePromptTemplateRequest allows empty strings for name/templateContent | Added `@Size(min = 1)` to name and templateContent |
| M4 | MEDIUM | Preview endpoint null sampleData risk | Added null check, defaults to `Map.of()` |
| L1 | LOW | Missing combined category+enabled filter test | Added `shouldFilterByCategoryAndEnabled` test |
| L2 | LOW | Missing enabled-only filter test | Added `shouldFilterByEnabled` test |

### File List

**New files (12):**
- `backend/ai-code-review-repository/src/main/resources/db/migration/V4__create_prompt_template_table.sql`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/PromptTemplate.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/PromptTemplateRepository.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/PromptTemplateDTO.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/CreatePromptTemplateRequest.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/UpdatePromptTemplateRequest.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/prompttemplate/PreviewResponse.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/TemplateSyntaxException.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/PromptTemplateService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/PromptTemplateServiceImpl.java`
- `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/PromptTemplateController.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/PromptTemplateControllerIntegrationTest.java`

**Modified files (2):**
- `backend/ai-code-review-service/pom.xml` (added Handlebars.java 4.4.0 dependency)
- `backend/ai-code-review-api/src/main/java/com/aicodereview/api/exception/GlobalExceptionHandler.java` (added TemplateSyntaxException handler → 422)
