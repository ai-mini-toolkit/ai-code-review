# Story 9.1: E2E 测试框架搭建

Status: done

## Story

作为 QA 工程师，
我想要建立完整的 E2E 测试框架，
以便自动化验证系统端到端功能。

## Acceptance Criteria

1. **后端 E2E 测试框架（TestContainers）**：在 `ai-code-review-api` 模块配置 Spring Boot Test + TestContainers，启动真实 PostgreSQL（16+）和 Redis（7+）容器，配置测试数据初始化脚本，创建 `application-e2e.yml` 测试 Profile。

2. **前端 E2E 测试框架（Playwright）**：在 `frontend/apps/web-ele` 中配置 Playwright，支持 Chromium 浏览器，实现 Page Object Model（LoginPage、DashboardPage 基础类）。

3. **测试工具类创建**：
   - `MockWebhookServer` — 模拟 GitHub/GitLab/AWS CodeCommit Webhook 请求
   - `MockAIProvider` — 模拟 AI API 响应（避免真实 API 费用），使用 `@MockBean`
   - `TestDataFactory` — 生成测试数据（项目、AI 配置、审查结果）
   - `AssertionHelper` — 自定义断言（数据库状态、队列状态）

4. **Docker Compose 测试环境**：创建 `docker-compose.test.yml`，配置独立的 `postgres-test`（postgres:16）和 `redis-test`（redis:7）服务，app-test 使用 `SPRING_PROFILES_ACTIVE=e2e`。

5. **示例测试用例**：编写 `E2EFrameworkSetupTest.java`（后端）和 `framework-setup.spec.ts`（前端），验证框架可用，测试通过。

## Tasks / Subtasks

- [x] Task 1: 配置 TestContainers Maven 依赖 (AC: #1)
  - [x] 1.1 在父 `pom.xml` dependencyManagement 中添加 `testcontainers-bom` (升级至 1.20.6 以兼容 Docker Desktop 4.34+，在 Spring Boot BOM 前导入)
  - [x] 1.2 在 `ai-code-review-api/pom.xml` 添加测试依赖：`spring-boot-testcontainers`、`testcontainers:junit-jupiter`、`testcontainers:postgresql`、`org.apache.httpcomponents.client5:httpclient5`
  - [x] 1.3 创建 `src/test/resources/application-e2e.yml`（覆盖 datasource/redis 使用 TestContainers 动态端口）

- [x] Task 2: 创建后端 E2E 基础设施类 (AC: #1, #3)
  - [x] 2.1 创建 `AbstractE2ETest.java`（基类，包含 `@SpringBootTest`、`@Testcontainers`、PostgreSQL 和 Redis 容器配置，`@ActiveProfiles("e2e")`）
  - [x] 2.2 创建 `MockWebhookServer.java`（发送 GitHub/GitLab/CodeCommit Webhook，计算 HMAC-SHA256 签名）
  - [x] 2.3 创建 `MockAIProvider.java`（`@TestConfiguration` + `@MockBean`，返回预定义 AI 审查结果）
  - [x] 2.4 创建 `TestDataFactory.java`（创建 Project、AIModelConfig、ReviewTask 测试数据）
  - [x] 2.5 创建 `AssertionHelper.java`（封装数据库/Redis 队列状态断言）

- [x] Task 3: 创建 Docker Compose 测试环境 (AC: #4)
  - [x] 3.1 在项目根目录创建 `docker-compose.test.yml`

- [x] Task 4: 配置前端 Playwright 测试框架 (AC: #2)
  - [x] 4.1 在 `frontend/apps/web-ele/package.json` 添加 `@playwright/test` 依赖
  - [x] 4.2 创建 `frontend/apps/web-ele/playwright.config.ts`（baseURL: http://localhost:5173）
  - [x] 4.3 创建 `frontend/apps/web-ele/e2e/pages/LoginPage.ts`（Page Object Model）
  - [x] 4.4 创建 `frontend/apps/web-ele/e2e/pages/DashboardPage.ts`（Page Object Model）

- [x] Task 5: 编写示例测试用例验证框架 (AC: #5)
  - [x] 5.1 创建 `E2EFrameworkSetupTest.java`（验证 TestContainers 启动、DB/Redis 连接、MockWebhookServer 可用、TestDataFactory 可用）
  - [x] 5.2 创建 `frontend/apps/web-ele/e2e/framework-setup.spec.ts`（Playwright 框架验证：页面加载、登录页面元素可见）

- [x] Task 6: 验证与文档 (AC: #1-5)
  - [x] 6.1 运行后端 E2E 测试：10/10 通过（TestContainers PostgreSQL 16 + Redis 7，jsonb 类型问题通过 @JdbcTypeCode(SqlTypes.JSON) 修复）
  - [x] 6.2 运行前端 E2E 测试：6/6 通过（Playwright Chromium）
  - [x] 6.3 确认所有测试通过

## Dev Notes

### 关键架构信息

**当前测试现状（重要！不要破坏已有测试）：**
- 现有集成测试使用 `@ActiveProfiles("dev")`，依赖真实运行的 Docker 服务（`docker-compose up`）
- 位置：`backend/ai-code-review-api/src/test/java/com/aicodereview/api/`
- 文件：`AiCodeReviewApplicationTests.java`、`QueueIntegrationTest.java`、`RetryIntegrationTest.java`、`ReviewTaskIntegrationTest.java`
- 新 E2E 测试使用 `@ActiveProfiles("e2e")` + TestContainers，**独立于已有的 `dev` profile 测试**，两者可并行存在

**TestContainers 关键依赖（Spring Boot 3.2.2 管理版本，无需指定版本）：**
```xml
<!-- 在父 pom.xml dependencyManagement 中 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>${testcontainers.version}</version> <!-- Spring Boot 3.2.2 管理 = 1.19.3 -->
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- 在 ai-code-review-api/pom.xml 中 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<!-- Redis 使用 GenericContainer，无专用 module -->
```

**注意**：TestContainers 1.19.x 已包含在 `spring-boot-dependencies` BOM 中，父 pom.xml 无需显式指定版本。

**application-e2e.yml 配置模式：**
```yaml
# 使用 Spring Boot 3.2 TestContainers 自动配置
# 连接 URL 由 @DynamicPropertySource 或 @ServiceConnection 动态注入
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///testdb  # TestContainers JDBC URL 模式
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    clean-disabled: false  # E2E 测试允许 clean（每次重建）
  jpa:
    hibernate:
      ddl-auto: validate
  data:
    redis:
      host: ${redis.host:localhost}
      port: ${redis.port:6379}
```

**AbstractE2ETest.java 结构模式（参考已有的 QueueIntegrationTest.java）：**
```java
package com.aicodereview.api.e2e;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

**MockWebhookServer 设计**（复用已有 `ReviewTaskIntegrationTest.java` 中的签名计算逻辑）：
- `sendGitHubPushEvent(String repoUrl, String branch, String commitSha)` — 发送含 HMAC-SHA256 签名的 POST 到 `/api/webhook/github`
- `sendGitLabMREvent(String repoUrl, String branch, int mrIid)` — 发送含 X-Gitlab-Token 的 POST 到 `/api/webhook/gitlab`
- `sendCodeCommitPREvent(String repoUrl, String branch)` — 发送到 `/api/webhook/codecommit`
- HMAC 签名逻辑已在 `ReviewTaskIntegrationTest.java:calculateGitHubSignature()` 中实现，直接复用

**TestDataFactory 需要创建的实体类型（参考已有 Repository）：**
- `Project` — 来自 `com.aicodereview.repository.entity.Project`，已有 `ProjectRepository`
- `ReviewTask` — 来自 `com.aicodereview.repository.entity.ReviewTask`，已有 `ReviewTaskRepository`
- 测试 webhook secret 参考：`test-github-secret`（与 `ReviewTaskIntegrationTest.java` 保持一致）

**Awaitility 依赖**（异步等待，已在 epics 中的示例代码中使用）：
```xml
<!-- spring-boot-starter-test 已包含 awaitility，无需额外添加 -->
```
`spring-boot-starter-test` 从 3.x 起已自动包含 `awaitility`，直接使用 `Awaitility.await()` 即可。

### 前端 E2E 配置

**Playwright 已在项目中使用**（`frontend/playground/playwright.config.ts`），参考该配置为 web-ele 创建：
- 输出目录：`node_modules/.e2e/test-results/`
- 测试目录：`./e2e/`（相对于 web-ele 根目录）
- baseURL：`http://localhost:5173`（web-ele 开发服务器端口）
- 浏览器：Chromium（初期仅配置一种浏览器）

**pnpm 工作区注意**：在 `web-ele/package.json` 中添加依赖需使用 `pnpm add -D @playwright/test`，在 `frontend/` 目录执行。

**web-ele 当前登录信息**（来自 `AuthControllerIntegrationTest.java` 模式，参考 `auth-login.spec.ts`）：
- 登录页路由：`/auth/login`（基于 Vben Admin 路由规范）
- 测试用户：`admin` / `123456`（或参考 `application-dev.yml` 中的初始用户配置）

**Page Object Model 位置**：
```
frontend/apps/web-ele/
├── e2e/
│   ├── pages/
│   │   ├── LoginPage.ts
│   │   └── DashboardPage.ts
│   └── framework-setup.spec.ts
└── playwright.config.ts
```

### 文件结构规划（需要创建的文件）

**后端新增文件：**
```
backend/ai-code-review-api/src/test/java/com/aicodereview/api/
├── e2e/
│   ├── AbstractE2ETest.java              # TestContainers 基类
│   ├── E2EFrameworkSetupTest.java        # 框架验证测试
│   └── support/
│       ├── MockWebhookServer.java        # Webhook 模拟器
│       ├── MockAIProvider.java           # AI 响应模拟
│       ├── TestDataFactory.java          # 测试数据工厂
│       └── AssertionHelper.java          # 自定义断言
backend/ai-code-review-api/src/test/resources/
└── application-e2e.yml                   # E2E 测试配置
```

**修改的后端文件：**
- `backend/pom.xml` — dependencyManagement 添加 testcontainers-bom（可选，Spring Boot BOM 已管理版本）
- `backend/ai-code-review-api/pom.xml` — test scope 添加 spring-boot-testcontainers、testcontainers junit-jupiter、postgresql

**前端新增文件：**
```
frontend/apps/web-ele/
├── playwright.config.ts
└── e2e/
    ├── pages/
    │   ├── LoginPage.ts
    │   └── DashboardPage.ts
    └── framework-setup.spec.ts
```

**修改的前端文件：**
- `frontend/apps/web-ele/package.json` — 添加 `@playwright/test` devDependency

**新增的根目录文件：**
- `docker-compose.test.yml` — 独立测试环境

### 注意事项与风险

1. **TestContainers 需要 Docker 运行时**：执行 E2E 测试前需确保 Docker 已启动（开发者本地和 CI 环境均需）
2. **端口冲突**：TestContainers 使用随机端口，`@DynamicPropertySource` 注入，不与 `dev` profile 的固定端口冲突
3. **flyway clean-disabled=false**：E2E profile 中建议允许 `flyway:clean`，以便每次测试从干净状态开始；**生产和 dev profile 保持 `clean-disabled=true`**
4. **Awaitility 已包含**：`spring-boot-starter-test` 已包含 awaitility，无需额外添加依赖
5. **`@MockBean` 用于 MockAIProvider**：使用 Spring 的 `@MockBean` 注解在测试时替换真实 AI 客户端，避免真实 API 调用
6. **现有集成测试不受影响**：已有的 `QueueIntegrationTest`、`ReviewTaskIntegrationTest` 等使用 `@ActiveProfiles("dev")`，与新的 `e2e` profile 完全独立

### 架构合规要求

- 测试包名：`com.aicodereview.api.e2e`（遵循架构文档 [Source: architecture.md#Test Organization]）
- 集成测试命名后缀：`Test`（E2E 测试）或 `IntegrationTest`（现有命名规范）
- 测试资源位置：`src/test/resources/application-e2e.yml`
- 使用 Apache HttpClient `HttpComponentsClientHttpRequestFactory`（参考已有测试，避免 401 响应处理问题）

### References

- [Source: architecture.md#Testing Infrastructure] — JUnit 5, Spring Boot Test, TestContainers for database integration tests
- [Source: architecture.md#Test Organization] — `src/test/java/com/aicodereview/{module}/`，Integration tests suffix with `IntegrationTest`
- [Source: epics.md#Story 9.1] — 完整验收标准和 Docker Compose 示例
- [Source: backend/ai-code-review-api/src/test/java/com/aicodereview/api/ReviewTaskIntegrationTest.java] — HMAC-SHA256 签名模式复用
- [Source: backend/ai-code-review-api/src/test/java/com/aicodereview/api/QueueIntegrationTest.java] — 现有集成测试模式（`@SpringBootTest(webEnvironment=RANDOM_PORT)`）
- [Source: frontend/playground/playwright.config.ts] — Playwright 配置参考
- [Source: frontend/playground/__tests__/e2e/auth-login.spec.ts] — E2E 测试写法参考
- [Source: backend/ai-code-review-api/pom.xml] — 当前 API 模块依赖（Apache HttpClient 已存在）
- TestContainers 官方文档: https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/
- Playwright 官方文档: https://playwright.dev/docs/intro

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- TestContainers 1.20.6 升级是必须的：Docker Desktop 4.34+ 不支持 API < 1.44，需在 Spring Boot BOM 前导入 testcontainers-bom 1.20.6
- 需要创建 `~/.docker-java.properties`（`api.version=1.44`）和 `~/.testcontainers.properties`（`tc.host=unix:///...docker.sock`）才能让 TestContainers 连接 Docker Desktop
- PostgreSQL 16 不支持 varchar→jsonb 隐式转换：所有 jsonb String 字段需加 `@JdbcTypeCode(SqlTypes.JSON)`（Hibernate 6 注解）
- Maven 使用 Java 25（Homebrew），Lombok 1.18.30 不兼容 Java 25；需用 `JAVA_HOME=corretto-17` 执行 mvn 命令
- repository 模块需在 pom.xml 中配置 `annotationProcessorPaths` 才能正确处理 Lombok @Slf4j

### File List

**后端新增文件：**
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/AbstractE2ETest.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/E2EFrameworkSetupTest.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/MockWebhookServer.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/MockAIProvider.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/TestDataFactory.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/AssertionHelper.java`
- `backend/ai-code-review-api/src/test/resources/application-e2e.yml`

**后端修改文件：**
- `backend/pom.xml`
- `backend/ai-code-review-api/pom.xml`
- `backend/ai-code-review-repository/pom.xml`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/Project.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewResultEntity.java`
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/NotificationTemplate.java`

**前端新增文件：**
- `frontend/apps/web-ele/playwright.config.ts`
- `frontend/apps/web-ele/e2e/pages/LoginPage.ts`
- `frontend/apps/web-ele/e2e/pages/DashboardPage.ts`
- `frontend/apps/web-ele/e2e/framework-setup.spec.ts`

**前端修改文件：**
- `frontend/apps/web-ele/package.json`
- `frontend/pnpm-lock.yaml`

**根目录新增文件：**
- `docker-compose.test.yml`

### Senior Developer Review (AI)

**Reviewer:** claude-sonnet-4-6
**Review Date:** 2026-02-22
**Outcome:** APPROVED — all HIGH and MEDIUM issues fixed before merge

#### Issues Found and Fixed

| Severity | ID | Location | Issue | Resolution |
|---|---|---|---|---|
| HIGH | H1 | `E2EFrameworkSetupTest.java:redisPingSucceeds()` | Redis connection leak — `getConnection().ping()` never returned connection to pool | Changed to `execute((RedisCallback<String>) RedisConnection::ping)` |
| HIGH | H2 | `E2EFrameworkSetupTest.java:setUp()` | `@BeforeEach` cleanup skipped `review_result` deleteAll(), violating FK constraint ordering | Added `reviewResultRepository.deleteAll()` first; order now: result → task → project |
| MEDIUM | M1 | `playwright.config.ts:webServer` | CI environment would get `reuseExistingServer: undefined` causing Playwright to start a second server | Changed to always use `reuseExistingServer: true` |
| MEDIUM | M2 | `E2EFrameworkSetupTest.java:coreTablesExist()` | `notification_template` table missing from expected tables list — AC #5 gap | Added `notification_template` to `expectedTables` list |
| MEDIUM | M3 | `AbstractE2ETest.java` | `MockAIProvider.MockAIConfiguration` only imported in `E2EFrameworkSetupTest`; future E2E tests would make real AI API calls | Added `@Import(MockAIProvider.MockAIConfiguration.class)` to `AbstractE2ETest` base class |
| LOW | L1 | `AbstractE2ETest.java` | Missing `@SuppressWarnings("resource")` Javadoc explaining suppression rationale | Documented in `@SuppressWarnings` comment |
| LOW | L2 | `docker-compose.test.yml` | `app-test` healthcheck uses `wget` but image may not have it | Noted; acceptable for optional manual-smoke-test service |
| LOW | L3 | `MockWebhookServer` | No content-type header on GitLab webhook requests | Noted; not critical for framework validation scope |

#### Verification

All 10/10 backend E2E tests pass. All 6/6 Playwright framework tests pass.
