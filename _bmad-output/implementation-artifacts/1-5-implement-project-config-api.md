# Story 1.5: 实现项目配置管理后端 API

**Status:** review

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 系统管理员,
I want to 通过 API 管理项目配置（Git 仓库、Webhook 密钥、审查开关）,
So that 我可以集成 Git 平台和控制代码审查行为。
```

**业务价值:**
此故事实现了 AI 代码审查系统的第一个核心业务实体 - **项目配置管理**。这是整个系统的基础，因为：
1. **系统入口** - 每个代码审查任务都从一个项目配置开始
2. **多项目支持** - 允许一个系统实例管理多个 Git 仓库
3. **Git 平台集成** - 存储 Webhook 密钥，为 Epic 2（Webhook 集成）提供验证基础
4. **审查控制** - 通过 enabled 开关灵活控制项目的审查功能

这是第一个完整的 CRUD API 实现，将建立后续 Story 1.6（AI 模型配置）和 Story 1.7（Prompt 模板）的实现模式。

**Story ID:** 1.5
**Priority:** HIGH - Epic 1 的核心业务实体，阻塞 Epic 2 和 Epic 8
**Complexity:** Medium
**Dependencies:**
- Story 1.3 (PostgreSQL & JPA 已配置完成)
- Story 1.4 (Redis & Caching 已配置完成)

---

## ✅ Acceptance Criteria (验收标准)

**Given** 数据库和 Redis 已配置（Story 1.3, 1.4 完成）
**When** 实现项目配置管理 API
**Then** 以下验收标准必须全部满足：

### AC 1: 数据库模式（Database Schema）
- [ ] 创建 `project` 表（如不存在）
- [ ] 字段：`id` BIGSERIAL PRIMARY KEY
- [ ] 字段：`name` VARCHAR(255) NOT NULL UNIQUE（项目名称）
- [ ] 字段：`description` TEXT（项目描述）
- [ ] 字段：`enabled` BOOLEAN NOT NULL DEFAULT TRUE（是否启用审查）
- [ ] 字段：`git_platform` VARCHAR(50) NOT NULL（GitHub/GitLab/CodeCommit）
- [ ] 字段：`repo_url` VARCHAR(500) NOT NULL（Git 仓库 URL）
- [ ] 字段：`webhook_secret` VARCHAR(500) NOT NULL（Webhook 密钥，AES 加密）
- [ ] 字段：`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [ ] 字段：`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [ ] 索引：`idx_project_name` ON name
- [ ] 索引：`idx_project_enabled` ON enabled
- [ ] Flyway 迁移脚本：`V2__create_project_table.sql`

### AC 2: JPA Entity 实现
- [ ] 创建 `Project.java` 实体类（`com.aicodereview.repository.entity`）
- [ ] 使用 `@Entity` 和 `@Table(name = "project")` 注解
- [ ] 所有字段包含 `@Column` 注解（name 映射 snake_case）
- [ ] `@CreatedDate` 和 `@LastModifiedDate` 注解（启用 Auditing）
- [ ] Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [ ] webhook_secret 字段使用 `@Convert` 进行 AES 加密/解密
- [ ] 实现 AttributeConverter for webhook_secret encryption

### AC 3: JPA Repository 实现
- [ ] 创建 `ProjectRepository.java` 接口（`com.aicodereview.repository`）
- [ ] 继承 `JpaRepository<Project, Long>`
- [ ] 自定义查询：`Optional<Project> findByName(String name)`
- [ ] 自定义查询：`List<Project> findByEnabled(Boolean enabled)`
- [ ] 使用 Spring Data JPA 方法命名规则

### AC 4: Service 层实现
- [ ] 创建 `ProjectService.java` 接口（`com.aicodereview.service`）
- [ ] 创建 `ProjectServiceImpl.java` 实现类
- [ ] 方法：`ProjectDTO createProject(CreateProjectRequest request)`
- [ ] 方法：`List<ProjectDTO> listProjects(Boolean enabled)`
- [ ] 方法：`ProjectDTO getProjectById(Long id)`
- [ ] 方法：`ProjectDTO updateProject(Long id, UpdateProjectRequest request)`
- [ ] 方法：`void deleteProject(Long id)`
- [ ] 使用 `@Cacheable` 注解缓存项目配置（cacheName="projects", key="#id"）
- [ ] 使用 `@CacheEvict` 注解清除缓存（更新和删除时）
- [ ] 抛出 `ResourceNotFoundException` 当项目不存在

### AC 5: Controller 层实现
- [ ] 创建 `ProjectController.java`（`com.aicodereview.api.controller`）
- [ ] 使用 `@RestController` 和 `@RequestMapping("/api/v1/projects")`
- [ ] POST `/api/v1/projects` - 创建项目
- [ ] GET `/api/v1/projects` - 列出项目（支持 enabled 查询参数）
- [ ] GET `/api/v1/projects/{id}` - 获取项目详情
- [ ] PUT `/api/v1/projects/{id}` - 更新项目
- [ ] DELETE `/api/v1/projects/{id}` - 删除项目
- [ ] 所有响应使用 `ApiResponse<T>` 统一格式
- [ ] 使用 `@Valid` 进行请求验证
- [ ] 使用 `@PreAuthorize` 预留权限控制（暂时开放）

### AC 6: DTO 类实现
- [ ] 创建 `ProjectDTO.java`（`com.aicodereview.common.dto`）
- [ ] 创建 `CreateProjectRequest.java`（验证注解：@NotBlank, @Size, @Pattern）
- [ ] 创建 `UpdateProjectRequest.java`（所有字段可选）
- [ ] 不在 DTO 中暴露 webhook_secret 明文（仅返回是否已配置）
- [ ] 使用 `@JsonProperty` 指定 camelCase JSON 字段名

### AC 7: 密钥加密存储
- [ ] webhook_secret 使用 AES-256-GCM 加密存储
- [ ] 加密密钥从环境变量读取（`ENCRYPTION_KEY`）或配置文件
- [ ] 如果环境变量未设置，使用默认密钥（开发环境警告日志）
- [ ] 实现 `EncryptionUtil.java` 工具类
- [ ] 实现 `WebhookSecretConverter.java` JPA AttributeConverter

### AC 8: Redis 缓存配置
- [ ] 项目配置缓存到 Redis（cacheName="projects"）
- [ ] 缓存 TTL：10 分钟（从 RedisConfig 继承）
- [ ] 缓存 key 格式：`aicodereview:cache:projects::{id}`
- [ ] 更新或删除时自动清除缓存

### AC 9: API 响应格式
- [ ] 成功响应：`{"success": true, "data": {...}, "timestamp": "2026-02-09T10:00:00Z"}`
- [ ] 错误响应：`{"success": false, "error": {"code": "ERR_404", "message": "Project not found"}, "timestamp": "..."}`
- [ ] 使用已有的 `ApiResponse<T>` 和 `ErrorCode` 类

### AC 10: 集成测试
- [ ] 创建 `ProjectIntegrationTest.java`（`com.aicodereview.api`）
- [ ] 使用 `@SpringBootTest` 和 `@ActiveProfiles("dev")`
- [ ] 测试用例：创建项目（POST）
- [ ] 测试用例：列出项目（GET）
- [ ] 测试用例：获取项目详情（GET /{id}）
- [ ] 测试用例：更新项目（PUT /{id}）
- [ ] 测试用例：删除项目（DELETE /{id}）
- [ ] 测试用例：重复名称验证（409 Conflict）
- [ ] 测试用例：项目不存在（404 Not Found）
- [ ] 测试用例：验证加密存储（webhook_secret 不可读）
- [ ] 测试用例：验证 Redis 缓存生效
- [ ] 所有测试用例通过

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 创建 Flyway 数据库迁移脚本
**AC:** #1
- [ ] 创建 `backend/ai-code-review-repository/src/main/resources/db/migration/V2__create_project_table.sql`
- [ ] 定义 `project` 表结构（id, name, description, enabled, git_platform, repo_url, webhook_secret, created_at, updated_at）
- [ ] 创建索引（idx_project_name, idx_project_enabled）
- [ ] 添加表和列注释
- [ ] 启动应用验证迁移执行成功

### Task 2: 实现加密工具类
**AC:** #7
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/util/EncryptionUtil.java`
- [ ] 实现 AES-256-GCM 加密方法 `encrypt(String plainText, String key): String`
- [ ] 实现 AES-256-GCM 解密方法 `decrypt(String cipherText, String key): String`
- [ ] 加密结果使用 Base64 编码存储
- [ ] 从环境变量 `ENCRYPTION_KEY` 读取密钥（提供默认值用于开发环境）
- [ ] 编写 EncryptionUtilTest 单元测试

### Task 3: 实现 JPA Entity 和 Repository
**AC:** #2, #3
- [ ] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/Project.java`
- [ ] 实现 JPA Entity（@Entity, @Table, @Column, Lombok 注解）
- [ ] 启用 JPA Auditing（@EnableJpaAuditing 在 JpaConfig.java，@CreatedDate/@LastModifiedDate）
- [ ] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/WebhookSecretConverter.java`
- [ ] 实现 AttributeConverter<String, String>，使用 EncryptionUtil
- [ ] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ProjectRepository.java`
- [ ] 定义自定义查询方法（findByName, findByEnabled）

### Task 4: 实现 DTO 类和验证
**AC:** #6, #9
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/ProjectDTO.java`
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/CreateProjectRequest.java`
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/UpdateProjectRequest.java`
- [ ] ProjectDTO 包含：id, name, description, enabled, gitPlatform, repoUrl, webhookSecretConfigured（boolean）, createdAt, updatedAt
- [ ] CreateProjectRequest 验证注解：@NotBlank(name), @NotBlank(gitPlatform), @NotBlank(repoUrl), @NotBlank(webhookSecret)
- [ ] UpdateProjectRequest 所有字段可选
- [ ] 添加 `spring-boot-starter-validation` 依赖到 api 模块 pom.xml（如未有）

### Task 5: 添加自定义异常类
**AC:** #4, #9
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/ResourceNotFoundException.java`
- [ ] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/DuplicateResourceException.java`
- [ ] 在 GlobalExceptionHandler 中添加新异常处理器
  - ResourceNotFoundException → HTTP 404
  - DuplicateResourceException → HTTP 409 Conflict
  - MethodArgumentNotValidException → HTTP 422 Validation Error（字段级错误）

### Task 6: 实现 Service 层
**AC:** #4, #8
- [ ] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ProjectService.java`（接口）
- [ ] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ProjectServiceImpl.java`（实现）
- [ ] 实现 CRUD 方法（create, list, getById, update, delete）
- [ ] Entity ↔ DTO 转换逻辑
- [ ] 添加 `@Cacheable("projects")` 到 getById 方法
- [ ] 添加 `@CacheEvict(value = "projects", key = "#p0")` 到 update 和 delete 方法
- [ ] 创建项目时检查名称唯一性（抛出 DuplicateResourceException）
- [ ] 日志记录关键操作（@Slf4j）

### Task 7: 实现 Controller 层
**AC:** #5
- [ ] 创建 `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/ProjectController.java`
- [ ] 实现 5 个 REST 端点（POST, GET list, GET by id, PUT, DELETE）
- [ ] 使用 `@RequestMapping("/api/v1/projects")` 基路径
- [ ] 所有响应包装为 `ApiResponse<T>`
- [ ] 请求体使用 `@Valid` 验证
- [ ] 添加 `@Slf4j` 日志记录

### Task 8: 添加配置（application-dev.yml）
**AC:** #7
- [ ] 在 `application-dev.yml` 添加加密密钥配置
  ```yaml
  app:
    encryption:
      key: ${ENCRYPTION_KEY:default-dev-key-32chars!!}
  ```
- [ ] 确保开发环境使用默认密钥并输出警告日志

### Task 9: 编写集成测试
**AC:** #10
- [ ] 创建 `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/ProjectControllerIntegrationTest.java`
- [ ] 使用 `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` 和 `TestRestTemplate`
- [ ] 测试用例 1: POST 创建项目 → 验证 201 Created
- [ ] 测试用例 2: GET 列出项目 → 验证返回列表
- [ ] 测试用例 3: GET 获取项目详情 → 验证返回数据
- [ ] 测试用例 4: PUT 更新项目 → 验证数据更新
- [ ] 测试用例 5: DELETE 删除项目 → 验证 200 OK
- [ ] 测试用例 6: POST 重复名称 → 验证 409 Conflict
- [ ] 测试用例 7: GET 不存在的 ID → 验证 404 Not Found
- [ ] 测试用例 8: webhook_secret 不在 GET 响应中明文返回
- [ ] 测试用例 9: POST 缺失必填字段 → 验证 422 Validation Error
- [ ] 运行全部测试验证通过

### Task 10: 运行完整测试套件
- [ ] `cd backend && mvn test` 确保无回归
- [ ] 手动验证 API 端点（使用 curl 或 Postman）
- [ ] 验证 Redis 缓存生效（GET 同一项目两次，第二次应从缓存读取）

---

## 💻 Dev Notes (开发注意事项)

### 架构约束

**模块职责（来自 Story 1.1）:**
- `ai-code-review-common`: 通用 DTO、异常类、工具类 → **EncryptionUtil, ProjectDTO, Exceptions**
- `ai-code-review-repository`: 数据层，JPA 实体和仓库 → **Project Entity, ProjectRepository, WebhookSecretConverter**
- `ai-code-review-service`: 业务逻辑层 → **ProjectService, ProjectServiceImpl**
- `ai-code-review-api`: REST API 控制器 → **ProjectController**

**模块依赖方向（严格遵守）:**
```
api → service → repository → common
                              ↑
api ──────────────────────────┘
```

**不允许反向依赖：**
- ❌ common 不能依赖任何其他模块
- ❌ repository 不能依赖 service 或 api
- ❌ service 不能依赖 api

### 已有代码模式（必须遵守）

**1. API 响应格式（来自 ApiResponse.java）:**
```java
// 成功响应
ApiResponse.success(data)
// 错误响应
ApiResponse.error(ErrorCode.NOT_FOUND, "Project not found")
```

**2. 错误码（来自 ErrorCode.java）:**
- `ERR_500`: INTERNAL_SERVER_ERROR
- `ERR_400`: BAD_REQUEST
- `ERR_404`: NOT_FOUND → 项目不存在
- `ERR_422`: VALIDATION_ERROR → 请求验证失败
- **需新增**: `ERR_409` CONFLICT → 名称重复

**3. 全局异常处理（来自 GlobalExceptionHandler.java）:**
- 已有: `Exception` → 500, `IllegalArgumentException` → 400
- 需新增: `ResourceNotFoundException` → 404, `DuplicateResourceException` → 409, `MethodArgumentNotValidException` → 422

**4. API 路径常量（来自 AppConstants.java）:**
```java
API_BASE_PATH = "/api/v1"
```

**5. Flyway 迁移命名（来自 V1__init_schema.sql）:**
- 格式：`V{version}__{description}.sql`
- 已有：V1（init_schema）
- 本次：V2（create_project_table）

### 技术栈版本

**确认的版本:**
- Spring Boot: 3.2.2
- Java: 17
- PostgreSQL: 18.1（Docker）
- Redis: 7-alpine（Docker）
- Flyway: 9.22.3（Spring Boot 管理）
- Hibernate: 6.4.1（Spring Boot 管理）
- Lombok: Spring Boot 管理版本

**依赖管理:**
- 使用 Spring Boot BOM，版本自动管理
- `spring-boot-starter-validation` 可能需要手动添加到 api 模块

### Previous Story 学习（Story 1.3, 1.4）

**成功模式:**
1. **测试配置**: 使用 `@EnableAutoConfiguration` + `@ComponentScan` 内部配置类
2. **Docker-First**: 确保 Docker 服务运行后再测试
3. **环境变量**: 使用 `${VAR:default}` 模式支持多环境
4. **SpEL 表达式**: 缓存 key 使用 `#p0` 而非 `#paramName`（避免编译器 -parameters 标志问题）

**避免的问题:**
1. ❌ 不要在 `@Cacheable` 的 key 表达式中使用参数名（用 `#p0`），除非确认编译器启用了 `-parameters`
2. ❌ 不要忘记在测试类中添加 TestConfig 内部类配置
3. ❌ 不要暴露 webhook_secret 明文到 API 响应
4. ❌ ErrorCode 需要添加 CONFLICT (409) 枚举值

### 关键实现细节

**AES-256-GCM 加密注意事项:**
- GCM 模式提供认证加密（AEAD），推荐用于存储敏感数据
- 每次加密必须使用不同的 IV（Initialization Vector）
- IV 应与密文一起存储（Base64 编码：IV + 密文）
- 加密密钥必须是 32 字节（256 位）

**JPA Auditing 配置:**
- 在 `JpaConfig.java` 中添加 `@EnableJpaAuditing`
- Entity 需要 `@EntityListeners(AuditingEntityListener.class)`
- 字段使用 `@CreatedDate` 和 `@LastModifiedDate`

**Redis 缓存 SpEL 注意:**
```java
// ✅ 正确 - 使用参数索引
@Cacheable(value = "projects", key = "#p0")
public ProjectDTO getProjectById(Long id)

// ✅ 正确 - 使用参数索引
@CacheEvict(value = "projects", key = "#p0")
public void deleteProject(Long id)
```

### Project Structure Notes

**本次新增文件列表:**
```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   ├── dto/project/
│   │   ├── ProjectDTO.java              (DTO)
│   │   ├── CreateProjectRequest.java     (Request DTO)
│   │   └── UpdateProjectRequest.java     (Request DTO)
│   ├── exception/
│   │   ├── ResourceNotFoundException.java
│   │   └── DuplicateResourceException.java
│   └── util/
│       └── EncryptionUtil.java           (AES-256-GCM)
├── ai-code-review-repository/src/main/
│   ├── java/com/aicodereview/repository/
│   │   ├── entity/
│   │   │   └── Project.java             (JPA Entity)
│   │   ├── converter/
│   │   │   └── WebhookSecretConverter.java (AttributeConverter)
│   │   └── ProjectRepository.java        (JPA Repository)
│   └── resources/db/migration/
│       └── V2__create_project_table.sql  (Flyway)
├── ai-code-review-service/src/main/java/com/aicodereview/service/
│   ├── ProjectService.java              (接口)
│   └── impl/
│       └── ProjectServiceImpl.java      (实现)
└── ai-code-review-api/src/
    ├── main/java/com/aicodereview/api/controller/
    │   └── ProjectController.java        (REST Controller)
    └── test/java/com/aicodereview/api/controller/
        └── ProjectControllerIntegrationTest.java (集成测试)
```

**修改的已有文件:**
```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/dto/
│   └── ErrorCode.java                   (添加 CONFLICT 枚举)
├── ai-code-review-api/src/main/java/com/aicodereview/api/exception/
│   └── GlobalExceptionHandler.java       (添加新异常处理器)
├── ai-code-review-api/src/main/resources/
│   └── application-dev.yml              (添加 encryption key 配置)
├── ai-code-review-api/pom.xml           (可能添加 validation 依赖)
└── ai-code-review-repository/src/main/java/com/aicodereview/repository/config/
    └── JpaConfig.java                   (添加 @EnableJpaAuditing)
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.5] - 项目配置 API 需求定义
- [Source: _bmad-output/planning-artifacts/architecture.md#Module Structure] - 模块职责和依赖规则
- [Source: backend/ai-code-review-common/src/main/java/.../dto/ApiResponse.java] - 统一响应格式
- [Source: backend/ai-code-review-common/src/main/java/.../dto/ErrorCode.java] - 错误码枚举
- [Source: backend/ai-code-review-api/src/main/java/.../exception/GlobalExceptionHandler.java] - 全局异常处理
- [Source: backend/ai-code-review-repository/src/main/java/.../config/RedisConfig.java] - Redis 缓存配置
- [Source: backend/ai-code-review-repository/src/main/java/.../config/JpaConfig.java] - JPA 配置
- [Source: backend/ai-code-review-repository/src/main/resources/db/migration/V1__init_schema.sql] - Flyway 迁移模式

---

## 🏗️ Implementation Strategy (实现策略)

### 实现顺序（推荐）

1. **Task 1**: Flyway 迁移 → 确保数据库表就绪
2. **Task 2**: 加密工具类 → 基础工具优先
3. **Task 3**: Entity + Repository → 数据层
4. **Task 4**: DTO 类 → 数据传输对象
5. **Task 5**: 异常类 + 异常处理器 → 错误处理
6. **Task 6**: Service 层 → 业务逻辑
7. **Task 7**: Controller 层 → API 端点
8. **Task 8**: 配置更新 → 环境配置
9. **Task 9**: 集成测试 → 验证所有功能
10. **Task 10**: 完整测试套件 → 回归测试

### 测试策略

- **单元测试**: EncryptionUtil（加密/解密正确性）
- **集成测试**: ProjectControllerIntegrationTest（完整 API 流程）
- **回归测试**: 运行所有现有测试确保无破坏

---

## Dev Agent Record

### Agent Model Used
Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Debug Log References
- **ErrorCodeTest fix**: Added CONFLICT enum value (7 total) but forgot to update `shouldHaveAllDefinedErrorCodes` test → fixed assertion from 6 to 7
- **PathVariable/RequestParam resolution**: Spring couldn't resolve parameter names without `-parameters` compiler flag → added explicit `value` attributes: `@PathVariable("id")`, `@RequestParam(value = "enabled", required = false)`
- **Redis Instant serialization**: `java.time.Instant` not supported by default Jackson ObjectMapper in RedisConfig → registered `JavaTimeModule` and added `jackson-datatype-jsr310` dependency to repository module
- **Test data persistence**: Integration tests used real dev database, data persisted between runs causing 409 CONFLICT → added `@BeforeAll` with `repository.deleteAll()` cleanup

### Completion Notes List
- All 10 tasks completed successfully
- 55 total tests pass (25 common + 14 repository + 16 API), 0 failures
- Full CRUD REST API for project configuration management operational
- AES-256-GCM encryption for webhook secrets with per-encryption random IVs
- Redis caching with `@Cacheable`/`@CacheEvict` and proper Java 8 time support
- Bean validation with field-level error reporting (422)
- Unique name constraint with conflict detection (409)
- webhook_secret never exposed in API responses (only `webhookSecretConfigured` boolean)

### File List
**New files created:**
- `backend/ai-code-review-repository/src/main/resources/db/migration/V2__create_project_table.sql`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/util/EncryptionUtil.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/util/EncryptionUtilTest.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/ResourceNotFoundException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/DuplicateResourceException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/ProjectDTO.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/CreateProjectRequest.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/project/UpdateProjectRequest.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/Project.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/WebhookSecretConverter.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ProjectRepository.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ProjectService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ProjectServiceImpl.java`
- `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/ProjectController.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/ProjectControllerIntegrationTest.java`

**Modified files:**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/ErrorCode.java` (added CONFLICT)
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/dto/ErrorCodeTest.java` (updated count to 7)
- `backend/ai-code-review-common/pom.xml` (added jakarta.validation-api)
- `backend/ai-code-review-repository/pom.xml` (added jackson-datatype-jsr310)
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/JpaConfig.java` (added @EnableJpaAuditing)
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/RedisConfig.java` (added JavaTimeModule)
- `backend/ai-code-review-api/pom.xml` (added spring-boot-starter-validation)
- `backend/ai-code-review-api/src/main/java/com/aicodereview/api/exception/GlobalExceptionHandler.java` (added 3 handlers)
- `backend/ai-code-review-api/src/main/resources/application-dev.yml` (added encryption key config)

---

**Story Created:** 2026-02-09
**Ready for Development:** ✅ YES
**Previous Story:** 1.4 - 配置 Redis 连接与缓存 (done)
**Next Story:** 1.6 - 实现 AI 模型配置管理后端 API (Backlog)
**Blocked By:** None (Story 1.3, 1.4 已完成)
**Blocks:**
- Story 1.6 (AI 模型配置 API) - 建立 CRUD 模式
- Story 1.7 (Prompt 模板 API) - 建立 CRUD 模式
- Epic 2 (Webhook 集成) - 需要项目配置中的 webhook_secret
- Epic 8 (Web 管理界面) - 需要项目配置 API
