# Story 1.6: 实现 AI 模型配置管理后端 API

**Status:** done

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 系统管理员,
I want to 通过 API 管理 AI 模型配置（提供商、模型参数、API 密钥）,
So that 我可以配置多个 AI 提供商用于代码审查。
```

**业务价值:**
此故事实现了 AI 代码审查系统的第二个核心业务实体 - **AI 模型配置管理**。这是 AI 审查引擎的基础：
1. **多提供商支持** - 允许配置 OpenAI、Anthropic Claude 及自定义 OpenAPI 兼容的 AI 提供商
2. **模型降级策略** - 通过优先级和备用模型配置，确保审查服务高可用
3. **分类审查** - 不同模型可配置不同类别（通用、安全、性能），支持六维度审查
4. **安全密钥管理** - API 密钥使用 AES-256-GCM 加密存储，确保凭证安全
5. **连接测试** - 提供测试连接端点，验证 AI 提供商 API 可用性

此故事完全复用 Story 1.5 建立的 CRUD 模式，同时扩展支持连接测试功能。

**Story ID:** 1.6
**Priority:** HIGH - 阻塞 Story 1.7（Prompt 模板 API）和 Epic 4（AI 智能审查引擎）
**Complexity:** Medium
**Dependencies:**
- Story 1.3 (PostgreSQL & JPA 已配置完成) ✅
- Story 1.4 (Redis & Caching 已配置完成) ✅
- Story 1.5 (项目配置 API 已完成 - 建立了 CRUD 模式) ✅

---

## ✅ Acceptance Criteria (验收标准)

**Given** 项目配置 API 已实现（Story 1.5 完成）
**When** 实现 AI 模型配置管理 API
**Then** 以下验收标准必须全部满足：

### AC 1: 数据库模式（Database Schema）
- [x] 创建 `ai_model_config` 表
- [x] 字段：`id` BIGSERIAL PRIMARY KEY
- [x] 字段：`name` VARCHAR(255) NOT NULL UNIQUE（模型配置名称）
- [x] 字段：`provider` VARCHAR(50) NOT NULL（openai/anthropic/custom）
- [x] 字段：`model_name` VARCHAR(100) NOT NULL（模型标识，如 gpt-4、claude-opus）
- [x] 字段：`api_key` VARCHAR(500) NOT NULL（AES-256-GCM 加密存储）
- [x] 字段：`api_endpoint` VARCHAR(500)（API 端点 URL，如 https://api.openai.com/v1）
- [x] 字段：`temperature` DECIMAL(3,2) DEFAULT 0.3（0.0-2.0）
- [x] 字段：`max_tokens` INT DEFAULT 4000
- [x] 字段：`timeout_seconds` INT DEFAULT 30
- [x] 字段：`enabled` BOOLEAN NOT NULL DEFAULT TRUE
- [x] 字段：`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [x] 字段：`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- [x] 索引：`idx_ai_model_config_name` ON name
- [x] 索引：`idx_ai_model_config_provider` ON provider
- [x] 索引：`idx_ai_model_config_enabled` ON enabled
- [x] Flyway 迁移脚本：`V3__create_ai_model_config_table.sql`

### AC 2: JPA Entity 实现
- [x] 创建 `AiModelConfig.java` 实体类（`com.aicodereview.repository.entity`）
- [x] 使用 `@Entity` 和 `@Table(name = "ai_model_config")` 注解
- [x] 所有字段包含 `@Column` 注解（name 映射 snake_case）
- [x] `@CreatedDate` 和 `@LastModifiedDate`（JPA Auditing 已由 Story 1.5 启用）
- [x] `@EntityListeners(AuditingEntityListener.class)`
- [x] Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [x] api_key 字段使用 `@Convert` 进行 AES 加密/解密（复用 Story 1.5 的加密模式）

### AC 3: JPA Repository 实现
- [x] 创建 `AiModelConfigRepository.java` 接口（`com.aicodereview.repository`）
- [x] 继承 `JpaRepository<AiModelConfig, Long>`
- [x] 自定义查询：`Optional<AiModelConfig> findByName(String name)`
- [x] 自定义查询：`List<AiModelConfig> findByEnabled(Boolean enabled)`
- [x] 自定义查询：`List<AiModelConfig> findByProvider(String provider)`

### AC 4: Service 层实现
- [x] 创建 `AiModelConfigService.java` 接口（`com.aicodereview.service`）
- [x] 创建 `AiModelConfigServiceImpl.java` 实现类
- [x] 方法：`AiModelConfigDTO createAiModel(CreateAiModelRequest request)`
- [x] 方法：`List<AiModelConfigDTO> listAiModels(Boolean enabled, String provider)`
- [x] 方法：`AiModelConfigDTO getAiModelById(Long id)`
- [x] 方法：`AiModelConfigDTO updateAiModel(Long id, UpdateAiModelRequest request)`
- [x] 方法：`void deleteAiModel(Long id)`
- [x] 方法：`TestConnectionResponse testConnection(Long id)`
- [x] `@Cacheable(value = "ai-models", key = "#p0")` 缓存 getById
- [x] `@CacheEvict(value = "ai-models", key = "#p0")` 清除缓存（更新、删除时）
- [x] 名称唯一性检查（DuplicateResourceException）
- [x] 不存在时抛出 ResourceNotFoundException

### AC 5: Controller 层实现
- [x] 创建 `AiModelController.java`（`com.aicodereview.api.controller`）
- [x] 使用 `@RestController` 和 `@RequestMapping("/api/v1/ai-models")`
- [x] POST `/api/v1/ai-models` → 201 Created
- [x] GET `/api/v1/ai-models` → 200 OK（支持 enabled、provider 查询参数）
- [x] GET `/api/v1/ai-models/{id}` → 200 OK
- [x] PUT `/api/v1/ai-models/{id}` → 200 OK
- [x] DELETE `/api/v1/ai-models/{id}` → 200 OK
- [x] POST `/api/v1/ai-models/{id}/test` → 200 OK（测试连接）
- [x] 所有响应使用 `ApiResponse<T>` 统一格式
- [x] 使用 `@Valid` 进行请求验证

### AC 6: DTO 类实现
- [x] 创建 `AiModelConfigDTO.java`（`com.aicodereview.common.dto.aimodel`）
- [x] 创建 `CreateAiModelRequest.java`（验证注解）
- [x] 创建 `UpdateAiModelRequest.java`（所有字段可选）
- [x] 创建 `TestConnectionResponse.java`（连接测试结果）
- [x] api_key 不在 DTO 中暴露明文（仅返回 `apiKeyConfigured` boolean）

### AC 7: API Key 加密存储
- [x] api_key 使用 AES-256-GCM 加密存储（复用 EncryptionUtil）
- [x] 创建 `ApiKeyEncryptionConverter.java`（复用 WebhookSecretConverter 模式）
- [x] 加密密钥使用与 WebhookSecretConverter 相同的配置（`app.encryption.key`）

### AC 8: Redis 缓存配置
- [x] 模型配置缓存到 Redis（cacheName="ai-models"）
- [x] 缓存 TTL：10 分钟（从 RedisCacheManager 继承）
- [x] 更新或删除时自动清除缓存

### AC 9: API 响应格式
- [x] 使用已有的 `ApiResponse<T>` 和 `ErrorCode`
- [x] 成功/错误响应格式与 Story 1.5 一致

### AC 10: 测试连接端点
- [x] POST `/api/v1/ai-models/{id}/test` 测试 AI 提供商连接
- [x] 加载模型配置，验证必要字段（api_key、api_endpoint 不为空）
- [x] 使用 Java HttpClient 向 api_endpoint 发起简单 HEAD/GET 请求验证可达性
- [x] 返回 `TestConnectionResponse`：success（boolean）、message、responseTimeMs
- [x] 超时/网络错误返回 success=false 和错误描述
- [x] 注意：完整的 AI API 调用测试将在 Epic 4 实现

### AC 11: 集成测试
- [x] 创建 `AiModelControllerIntegrationTest.java`
- [x] 测试用例：POST 创建模型配置 → 201
- [x] 测试用例：GET 列出模型配置 → 200
- [x] 测试用例：GET 按 provider 过滤 → 200
- [x] 测试用例：GET 获取详情 → 200
- [x] 测试用例：PUT 更新配置 → 200
- [x] 测试用例：DELETE 删除配置 → 200
- [x] 测试用例：POST 重复名称 → 409 Conflict
- [x] 测试用例：GET 不存在 ID → 404 Not Found
- [x] 测试用例：api_key 不在 GET 响应中明文返回
- [x] 测试用例：POST 缺失必填字段 → 422 Validation Error
- [x] 测试用例：POST test 连接测试端点
- [x] 测试用例：验证 Redis 缓存生效
- [x] 所有测试通过

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 创建 Flyway 数据库迁移脚本 (AC: #1)
- [x] 创建 `backend/ai-code-review-repository/src/main/resources/db/migration/V3__create_ai_model_config_table.sql`
- [x] 定义 `ai_model_config` 表结构
- [x] 创建索引（idx_ai_model_config_name, idx_ai_model_config_provider, idx_ai_model_config_enabled）
- [x] 添加表和列注释
- [x] 启动应用验证迁移执行成功

### Task 2: 创建 API Key 加密转换器 (AC: #7)
- [x] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/ApiKeyEncryptionConverter.java`
- [x] 复用 `WebhookSecretConverter` 的完整模式（static volatile key、@Value 注入、DEFAULT_KEY 回退）
- [x] 使用相同的 `app.encryption.key` 配置项
- [x] 与 WebhookSecretConverter 共用加密密钥

### Task 3: 实现 JPA Entity 和 Repository (AC: #2, #3)
- [x] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/AiModelConfig.java`
- [x] 实现 JPA Entity，包含所有字段（参照 Project.java 模式）
- [x] api_key 字段使用 `@Convert(converter = ApiKeyEncryptionConverter.class)`
- [x] `@Builder.Default` 用于 enabled=true、temperature=0.3、maxTokens=4000、timeoutSeconds=30
- [x] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/AiModelConfigRepository.java`
- [x] 定义自定义查询（findByName, findByEnabled, findByProvider）

### Task 4: 实现 DTO 类和验证 (AC: #6)
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/AiModelConfigDTO.java`
  - 包含：id, name, provider, modelName, apiEndpoint, temperature, maxTokens, timeoutSeconds, enabled, apiKeyConfigured（boolean）, createdAt, updatedAt
  - **不包含** api_key 明文
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/CreateAiModelRequest.java`
  - `@NotBlank`: name, provider, modelName, apiKey
  - `@Pattern(regexp = "^(openai|anthropic|custom)$")`: provider
  - `@Size(max = 255)`: name
  - `@Size(max = 100)`: modelName
  - 可选字段：apiEndpoint, temperature, maxTokens, timeoutSeconds, enabled
  - temperature 验证：`@DecimalMin("0.0") @DecimalMax("2.0")`
  - maxTokens 验证：`@Min(1) @Max(128000)`
  - timeoutSeconds 验证：`@Min(1) @Max(300)`
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/UpdateAiModelRequest.java`
  - 所有字段可选，仅 `@Size`、`@Pattern`、`@DecimalMin/@DecimalMax`、`@Min/@Max` 约束
- [x] 创建 `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/TestConnectionResponse.java`
  - 字段：success（boolean）、message（String）、responseTimeMs（Long）

### Task 5: 实现 Service 层 (AC: #4, #8)
- [x] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/AiModelConfigService.java`（接口）
- [x] 创建 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/AiModelConfigServiceImpl.java`（实现）
- [x] 实现 CRUD 方法：create, list（支持 enabled + provider 过滤）, getById, update, delete
- [x] 实现 testConnection 方法：
  1. 加载模型配置
  2. 验证 apiEndpoint 不为空
  3. 使用 `java.net.http.HttpClient` 向 apiEndpoint 发送 HEAD 请求
  4. 记录响应时间和连接状态
  5. 返回 TestConnectionResponse
- [x] Entity ↔ DTO 转换（private toDTO 方法）
- [x] `@Cacheable(value = "ai-models", key = "#p0")` 缓存 getById
- [x] `@CacheEvict(value = "ai-models", key = "#p0")` 在 update 和 delete
- [x] 名称唯一性检查（DuplicateResourceException）
- [x] 日志记录（@Slf4j）

### Task 6: 实现 Controller 层 (AC: #5)
- [x] 创建 `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/AiModelController.java`
- [x] 6 个 REST 端点：POST, GET list, GET by id, PUT, DELETE, POST test
- [x] `@RequestMapping("/api/v1/ai-models")`
- [x] 所有响应包装为 `ApiResponse<T>`
- [x] 请求体使用 `@Valid` 验证
- [x] GET list 支持可选查询参数：`enabled`、`provider`
- [x] Test 端点：`@PostMapping("/{id}/test")`

### Task 7: 编写集成测试 (AC: #11)
- [x] 创建 `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/AiModelControllerIntegrationTest.java`
- [x] 使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- [x] `@BeforeAll` 清理：`aiModelConfigRepository.deleteAll()`
- [x] 12+ 测试用例覆盖所有 CRUD + 边界条件 + 缓存
- [x] 运行全部测试验证通过

### Task 8: 运行完整测试套件
- [x] `cd backend && mvn test` 确保无回归
- [x] 更新 ErrorCodeTest 计数断言（如有新增 ErrorCode）→ 无需更新，未新增 ErrorCode
- [x] 验证所有 56+ 现有测试 + 新测试全部通过 → 68 tests passed (25 common + 14 repository + 29 API)

---

## 💻 Dev Notes (开发注意事项)

### 架构约束

**模块职责（严格遵守 Story 1.1 建立的规则）:**
- `ai-code-review-common`: DTO、异常类、工具类 → **AiModelConfigDTO, CreateAiModelRequest, UpdateAiModelRequest, TestConnectionResponse**
- `ai-code-review-repository`: 数据层 → **AiModelConfig Entity, AiModelConfigRepository, ApiKeyEncryptionConverter**
- `ai-code-review-service`: 业务逻辑 → **AiModelConfigService, AiModelConfigServiceImpl**
- `ai-code-review-api`: REST 控制器 → **AiModelController**

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
ApiResponse.error(ErrorCode.NOT_FOUND, "AI model config not found")
```

**2. 错误码（ErrorCode.java 已存在，7 个枚举值）:**
- `ERR_404` NOT_FOUND → 模型配置不存在
- `ERR_409` CONFLICT → 名称重复
- `ERR_422` VALIDATION_ERROR → 请求验证失败
- `ERR_500` INTERNAL_SERVER_ERROR → 内部错误
- 无需新增 ErrorCode，现有枚举已覆盖所有场景

**3. 异常类（已存在，直接复用）:**
```java
throw new ResourceNotFoundException("AiModelConfig", id);
throw new DuplicateResourceException("AiModelConfig", "name", request.getName());
```

**4. GlobalExceptionHandler（已存在，无需修改）:**
- ResourceNotFoundException → 404
- DuplicateResourceException → 409
- MethodArgumentNotValidException → 422

**5. 加密工具（EncryptionUtil.java + Converter 模式已存在）:**
```java
// ApiKeyEncryptionConverter 应完全复制 WebhookSecretConverter 模式
@Slf4j
@Component
@Converter
public class ApiKeyEncryptionConverter implements AttributeConverter<String, String> {
    private static volatile String encryptionKey;
    private static final String DEFAULT_KEY = "default-dev-key-32chars-warning!";
    // ... 与 WebhookSecretConverter 相同的实现
}
```

**6. SpEL 缓存 key 必须使用 `#p0`（-parameters 未启用）:**
```java
@Cacheable(value = "ai-models", key = "#p0")   // ✅ 正确
@CacheEvict(value = "ai-models", key = "#p0")  // ✅ 正确
// @Cacheable(value = "ai-models", key = "#id") // ❌ 错误！运行时会失败
```

**7. @PathVariable 和 @RequestParam 必须显式指定 value:**
```java
@PathVariable("id") Long id                           // ✅ 必须
@RequestParam(value = "enabled", required = false)     // ✅ 必须
// @PathVariable Long id                               // ❌ 运行时会失败
```

**8. Flyway 迁移版本号:**
- V1: init_schema（Story 1.3）
- V2: create_project_table（Story 1.5）
- **V3: create_ai_model_config_table（本 Story）**

### 技术栈版本

- Spring Boot: 3.2.2
- Java: 17
- PostgreSQL: 18-alpine（Docker）
- Redis: 7-alpine（Docker）
- Flyway: 9.22.3（Spring Boot 管理）
- Hibernate: 6.4.1（Spring Boot 管理）
- Lombok: Spring Boot 管理版本
- Jackson: Spring Boot 管理版本（含 jackson-datatype-jsr310）

### Previous Story 学习（Story 1.5 关键教训）

**必须遵守：**
1. **ErrorCodeTest 计数** - 如果新增 ErrorCode 枚举值，必须更新 `ErrorCodeTest.shouldHaveAllDefinedErrorCodes` 断言的计数（当前为 7）
2. **Redis Instant 序列化** - RedisConfig 已注册 `JavaTimeModule`，新的 DTO 使用 `Instant` 类型无需额外配置
3. **测试数据清理** - 集成测试必须用 `@BeforeAll` + `repository.deleteAll()` 清理数据，`@Transactional` 在 RANDOM_PORT 下不回滚
4. **WebhookSecretConverter 模式** - 使用 static volatile 字段 + @Value setter 注入，因为 Hibernate 可能通过 `new` 实例化 converter
5. **@Pattern URL 验证** - repoUrl/apiEndpoint 使用 `@Pattern(regexp = "^https?://.+")` 验证

**已解决的问题（无需重复排查）：**
- Jackson `java.time.Instant` 序列化 → JavaTimeModule 已注册
- -parameters 编译器标志 → 使用 `#p0` 和显式 `@PathVariable("id")`
- 测试数据持久化 → `@BeforeAll` cleanup
- 加密密钥默认值对齐 → `DEFAULT_KEY` 在 Converter 和 application-dev.yml 一致

### Test Connection 实现说明

**当前范围（Story 1.6）：**
- 验证配置完整性（api_key 和 api_endpoint 不为空）
- 向 api_endpoint 发送 HTTP HEAD 请求检测可达性
- 记录响应时间
- 返回连接状态和诊断信息

**不在当前范围（Epic 4 实现）：**
- 使用实际 AI API 发送 prompt 测试
- 验证 API key 权限和配额
- 测试具体模型可用性

**实现建议：**
```java
public TestConnectionResponse testConnection(Long id) {
    AiModelConfig config = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AiModelConfig", id));

    if (config.getApiEndpoint() == null || config.getApiEndpoint().isEmpty()) {
        return new TestConnectionResponse(false, "API endpoint not configured", null);
    }

    long startTime = System.currentTimeMillis();
    try {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiEndpoint()))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        long elapsed = System.currentTimeMillis() - startTime;

        boolean success = response.statusCode() < 500;
        return new TestConnectionResponse(success,
                "HTTP " + response.statusCode() + " - " + (success ? "Reachable" : "Server error"),
                elapsed);
    } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - startTime;
        return new TestConnectionResponse(false, "Connection failed: " + e.getMessage(), elapsed);
    }
}
```

### Project Structure Notes

**本次新增文件列表:**
```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   └── dto/aimodel/
│       ├── AiModelConfigDTO.java           (DTO)
│       ├── CreateAiModelRequest.java       (Request DTO)
│       ├── UpdateAiModelRequest.java       (Request DTO)
│       └── TestConnectionResponse.java     (Response DTO)
├── ai-code-review-repository/src/main/
│   ├── java/com/aicodereview/repository/
│   │   ├── entity/
│   │   │   └── AiModelConfig.java          (JPA Entity)
│   │   ├── converter/
│   │   │   └── ApiKeyEncryptionConverter.java (AttributeConverter)
│   │   └── AiModelConfigRepository.java    (JPA Repository)
│   └── resources/db/migration/
│       └── V3__create_ai_model_config_table.sql (Flyway)
├── ai-code-review-service/src/main/java/com/aicodereview/service/
│   ├── AiModelConfigService.java           (接口)
│   └── impl/
│       └── AiModelConfigServiceImpl.java   (实现)
└── ai-code-review-api/src/
    ├── main/java/com/aicodereview/api/controller/
    │   └── AiModelController.java           (REST Controller)
    └── test/java/com/aicodereview/api/controller/
        └── AiModelControllerIntegrationTest.java (集成测试)
```

**不修改已有文件** - 所有 ErrorCode、GlobalExceptionHandler、RedisConfig、EncryptionUtil 已在 Story 1.5 中配置完成，无需任何修改。

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-1.md#Story 1.6] - AI 模型配置 API 需求定义
- [Source: _bmad-output/planning-artifacts/prd.md#Section 1.8.1] - AI 模型配置特性和数据库模式
- [Source: _bmad-output/planning-artifacts/architecture.md#Decision 3.4] - AI Provider 抽象和降级策略
- [Source: _bmad-output/planning-artifacts/architecture.md#Decision 2.3] - AES-256-GCM 加密策略
- [Source: _bmad-output/implementation-artifacts/1-5-implement-project-config-api.md] - Story 1.5 CRUD 模式和教训
- [Source: backend/ai-code-review-repository/src/main/java/.../entity/Project.java] - Entity 模式参考
- [Source: backend/ai-code-review-repository/src/main/java/.../converter/WebhookSecretConverter.java] - Converter 模式参考
- [Source: backend/ai-code-review-service/src/main/java/.../impl/ProjectServiceImpl.java] - Service 实现模式参考
- [Source: backend/ai-code-review-api/src/main/java/.../controller/ProjectController.java] - Controller 模式参考
- [Source: backend/ai-code-review-api/src/test/java/.../ProjectControllerIntegrationTest.java] - 集成测试模式参考

---

## 🏗️ Implementation Strategy (实现策略)

### 实现顺序（推荐）

1. **Task 1**: Flyway 迁移 → 确保数据库表就绪
2. **Task 2**: ApiKeyEncryptionConverter → 加密转换器
3. **Task 3**: Entity + Repository → 数据层
4. **Task 4**: DTO 类 → 数据传输对象
5. **Task 5**: Service 层 → 业务逻辑 + testConnection
6. **Task 6**: Controller 层 → 6 个 API 端点
7. **Task 7**: 集成测试 → 12+ 测试用例
8. **Task 8**: 完整测试套件 → 回归测试

### 测试策略

- **集成测试**: AiModelControllerIntegrationTest（完整 API 流程 + 缓存 + 加密）
- **回归测试**: 运行所有现有 56 个测试确保无破坏

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- No errors encountered during implementation. All 68 tests passed on first run.
- Code review fixes applied: All 68 tests passed after fixes.

### Completion Notes List

1. **Zero errors**: All code compiled and tests passed on first attempt
2. **Pattern reuse**: Successfully replicated Story 1.5 CRUD pattern for AI Model Config domain
3. **New patterns**: Extended with dual query parameter filtering (enabled + provider), testConnection endpoint using Java HttpClient, BigDecimal temperature handling
4. **Test count**: 68 total (was 56 in Story 1.5, +12 new AI model integration tests)
5. **No existing files modified**: All ErrorCode, GlobalExceptionHandler, RedisConfig, EncryptionUtil remain unchanged
6. **Encryption**: ApiKeyEncryptionConverter follows exact same pattern as WebhookSecretConverter

### Code Review Fixes Applied (Adversarial Review)

| ID | Severity | Issue | Fix Applied |
|----|----------|-------|-------------|
| H1 | HIGH | `testConnection` holds DB connection during HTTP call | Changed to `@Transactional(propagation = NOT_SUPPORTED)` |
| M1 | MEDIUM | `listAiModels` ignores combined provider+enabled filter | Added `findByProviderAndEnabled` repository method + service logic |
| M2 | MEDIUM | `testConnection` creates new HttpClient per call | Extracted to static `HTTP_CLIENT` field |
| M3 | MEDIUM | Redundant `idx_ai_model_config_name` index | Removed from V3 migration (UNIQUE already creates index) |
| M4 | MEDIUM | No `@Size` on `apiKey` in CreateAiModelRequest | Added `@Size(max = 200)` |
| M5 | MEDIUM | SSRF risk in `testConnection` | Added `isBlockedEndpoint()` private IP/localhost check |
| L1 | LOW | Broad `catch(Exception)` in testConnection | Split into HttpTimeoutException, InterruptedException, IOException |
| L2 | LOW | No DB CHECK constraint on provider | Added `CHECK (provider IN ('openai','anthropic','custom'))` to V3 |

### File List

**New Files Created (10 files):**
1. `backend/ai-code-review-repository/src/main/resources/db/migration/V3__create_ai_model_config_table.sql`
2. `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/converter/ApiKeyEncryptionConverter.java`
3. `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/AiModelConfig.java`
4. `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/AiModelConfigRepository.java`
5. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/AiModelConfigDTO.java`
6. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/CreateAiModelRequest.java`
7. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/UpdateAiModelRequest.java`
8. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/aimodel/TestConnectionResponse.java`
9. `backend/ai-code-review-service/src/main/java/com/aicodereview/service/AiModelConfigService.java`
10. `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/AiModelConfigServiceImpl.java`
11. `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/AiModelController.java`
12. `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/AiModelControllerIntegrationTest.java`

**No existing files modified.**
