# Story 1.1: 从启动模板初始化 Spring Boot 多模块项目

**Status:** review

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 开发团队,
I want to 从 Spring Boot 启动模板创建一个多模块 Maven 项目,
So that 我可以为后端服务建立基础结构。
```

**业务价值:**
此故事建立了整个 AI 代码审查系统的基础多模块 Maven 结构。它是 Epic 1 的第一个故事，是所有后续后端开发故事（1.2、1.3、1.4 等）的前提条件。

**Story ID:** 1.1
**Priority:** CRITICAL - 必须首先完成
**Complexity:** Medium

---

## ✅ Acceptance Criteria (验收标准)

**Given** 项目根目录的 backend/ 目录基本为空
**When** 执行项目初始化
**Then** 创建以下 Maven 模块结构：

1. **ai-code-review-api** - REST API 层
2. **ai-code-review-service** - 业务逻辑层
3. **ai-code-review-repository** - 数据访问层
4. **ai-code-review-integration** - 外部集成层
5. **ai-code-review-worker** - 异步任务工作器
6. **ai-code-review-common** - 共享工具

**And** 每个模块包含标准目录结构：
- `src/main/java`
- `src/main/resources`
- `src/test/java`

**And** 父 POM 配置：
- Java 17 或更高版本
- Spring Boot 3.x
- 依赖管理

**And** 包名遵循约定：`com.aicodereview.*`

**And** 包含 `application.yml` 配置文件模板

**And** 项目成功编译（`mvn clean install`）

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 创建多模块 Maven 项目结构 (AC: 所有模块)
- [x] 创建父 POM (`backend/pom.xml`) 配置 `packaging: pom`
- [x] 创建 6 个子模块目录
- [x] 为每个模块创建 `pom.xml`
- [x] 配置模块间依赖关系

### Task 2: 配置父 POM 依赖管理 (AC: 父 POM 配置)
- [x] 设置 Java 17
- [x] 配置 Spring Boot 3.x
- [x] 添加 `<dependencyManagement>` 部分
- [x] 配置 Spring Cloud 版本（如需要）
- [x] 添加通用插件配置

### Task 3: 初始化每个模块的包结构 (AC: 包名约定)
- [x] **api 模块**: `com.aicodereview.api.{controller,dto,exception,config}`
- [x] **service 模块**: `com.aicodereview.service.{service,domain,strategy}`
- [x] **repository 模块**: `com.aicodereview.repository.{entity,repository,mapper}`
- [x] **integration 模块**: `com.aicodereview.integration.{webhook,git,ai,notification}`
- [x] **worker 模块**: `com.aicodereview.worker.{consumer,processor,analyzer}`
- [x] **common 模块**: `com.aicodereview.common.{config,util,constant}`

### Task 4: 配置应用程序配置文件 (AC: application.yml)
- [x] 在 api 模块创建 `application.yml`
- [x] 创建 `application-dev.yml`
- [x] 创建 `application-prod.yml`
- [x] 配置基本服务器端口和应用名称
- [x] 添加占位符用于后续数据库/Redis 配置

### Task 5: 添加基础类和配置 (AC: 项目编译成功)
- [x] 在 api 模块创建 Spring Boot 主应用类
- [x] 创建标准化的 `ApiResponse<T>` 类
- [x] 添加全局异常处理器基础结构
- [x] 创建基础常量类

### Task 6: 验证构建 (AC: mvn clean install)
- [x] 执行 `mvn clean install`
- [x] 验证所有模块编译成功
- [x] 确认无错误和警告
- [x] 验证 JAR 文件生成

---

## 💻 Dev Notes (开发注意事项)

### 关键架构约束

**模块依赖规则（严格）:**
```
api → service, common
service → repository, integration, common
repository → common
integration → common
worker → service, common
common → NO dependencies on other modules
```

**禁止循环依赖！** 这对于独立模块测试至关重要。

### 技术栈规范

| 组件 | 版本/规范 |
|------|----------|
| Java | 17+ |
| Spring Boot | 3.x (最新稳定版) |
| Maven | 3.8+ |
| Package Base | `com.aicodereview` |
| Build Tool | Maven |

### 目录结构标准

**完整的后端多模块目录结构:**

```
backend/
├── pom.xml                                 # 父 POM (packaging: pom)
├── ai-code-review-api/                     # REST API 层
│   ├── src/main/java/com/aicodereview/api/
│   │   ├── controller/                     # REST Controllers
│   │   ├── dto/                            # Request/Response DTOs
│   │   ├── exception/                      # Global Exception Handlers
│   │   └── config/                         # API-specific Config
│   ├── src/main/resources/
│   │   ├── application.yml                 # 主配置
│   │   ├── application-dev.yml             # Dev profile
│   │   └── application-prod.yml            # Prod profile
│   └── pom.xml
├── ai-code-review-service/                 # 业务逻辑层
│   ├── src/main/java/com/aicodereview/service/
│   │   ├── service/                        # Business Services
│   │   ├── domain/                         # Domain Models
│   │   └── strategy/                       # Strategy Pattern Implementations
│   └── pom.xml
├── ai-code-review-repository/              # 数据访问层
│   ├── src/main/java/com/aicodereview/repository/
│   │   ├── entity/                         # JPA Entities
│   │   ├── repository/                     # Spring Data Repositories
│   │   └── mapper/                         # Entity-Domain Mappers
│   ├── src/main/resources/db/migration/    # Flyway Migrations
│   └── pom.xml
├── ai-code-review-integration/             # 外部集成
│   ├── src/main/java/com/aicodereview/integration/
│   │   ├── webhook/                        # Webhook Verifiers
│   │   ├── git/                            # Git Operations (JGit)
│   │   ├── ai/                             # AI Provider Clients
│   │   └── notification/                   # Notification Clients
│   └── pom.xml
├── ai-code-review-worker/                  # 异步任务工作器
│   ├── src/main/java/com/aicodereview/worker/
│   │   ├── consumer/                       # Queue Consumers
│   │   ├── processor/                      # Task Processors
│   │   └── analyzer/                       # Code Analyzers
│   └── pom.xml
└── ai-code-review-common/                  # 共享工具
    ├── src/main/java/com/aicodereview/common/
    │   ├── config/                         # Shared Config Classes
    │   ├── util/                           # Utility Classes
    │   └── constant/                       # Constants
    └── pom.xml
```

### 命名约定（必须遵守）

**类命名规范:**
- Controllers: `*Controller` (例：`ProjectController`)
- Services: `*Service` (例：`ReviewService`)
- Repositories: `*Repository` (例：`ProjectRepository`)
- Entities: 无后缀 (例：`Project`, `ReviewTask`)
- DTOs: `*Request`, `*Response`, `*DTO`
- Exceptions: `*Exception`
- Interfaces: **无 `I` 前缀** (例：`AIProvider`，不是 `IAIProvider`)

### 必需的基础类

**1. ApiResponse<T> (MANDATORY - 标准化响应格式)**

```java
package com.aicodereview.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object details) {
        ErrorDetail error = new ErrorDetail(code.getCode(), message, details);
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
```

**所有 Controllers 必须返回 `ApiResponse<T>`**

**2. Spring Boot 主应用类**

```java
package com.aicodereview.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aicodereview")
public class AiCodeReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCodeReviewApplication.class, args);
    }
}
```

### Maven 配置关键点

**父 POM 策略:**
- 父 POM 管理所有依赖版本
- 仅 `ai-code-review-api` 声明 `spring-boot-starter-parent`
- 其他模块依赖特定的 Spring 依赖
- 防止库模块中的依赖膨胀

**Spring Boot Maven 插件:**
- 仅在 `api` 模块中（生成可执行 JAR）
- 其他模块生成标准库 JAR

### 配置文件模板

**application.yml (基础版本):**
```yaml
spring:
  application:
    name: ai-code-review
  profiles:
    active: dev

server:
  port: 8080

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
```

---

## 🔍 架构合规性

### 来源文档引用
- **架构文档**: `_bmad-output/planning-artifacts/architecture.md`
  - 技术栈规范（第 2 节）
  - 模块结构设计（第 3.1 节）
  - 包命名约定（第 3.2 节）
  - 依赖管理策略（第 3.3 节）

- **Epic 文档**: `_bmad-output/planning-artifacts/epics.md`
  - Epic 1: 项目基础设施与配置管理
  - Story 1.1: 完整需求和验收标准

### 关键架构决策

1. **六模块分层架构** - 关注点分离，未来微服务迁移路径
2. **严格的依赖规则** - 防止循环依赖
3. **标准化 API 响应** - 统一错误处理和响应格式
4. **配置文件分层** - 支持多环境部署

---

## 🧪 测试要求

### 单元测试
- 验证项目结构创建正确
- 测试模块依赖配置

### 集成测试
- **构建验证**: `mvn clean install` 成功
- **启动验证**: Spring Boot 应用成功启动
- **模块加载**: 所有模块正确扫描和加载

### 测试框架
- JUnit 5
- Spring Boot Test
- AssertJ（断言）

---

## 📚 References (参考资源)

### 内部文档
- [Architecture Document](../_bmad-output/planning-artifacts/architecture.md#技术栈)
- [Epic 1 Requirements](../_bmad-output/planning-artifacts/epics.md#epic-1)

### 外部资源
- [Spring Boot Multi-Module Projects](https://spring.io/guides/gs/multi-module/)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Spring Boot 3.x Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)

---

## 🚀 Implementation Strategy (实现策略)

### 推荐方法

**方法 1: 手动创建（推荐用于学习）**
1. 手动创建目录结构
2. 手动编写 POM 文件
3. 创建包结构和基础类
4. 逐步验证每个模块

**方法 2: Spring Initializr + 重构**
1. 使用 Spring Initializr 生成单模块项目
2. 重构为多模块结构
3. 配置模块依赖
4. 验证构建

**方法 3: Maven Archetype（最快）**
1. 使用 Maven archetype 生成多模块项目
2. 根据架构规范调整
3. 添加自定义配置

### 当前项目状态

**现有 backend/ 目录:**
- 存在 `poc-tests/` 子目录
- 基本为空，准备初始化

**Git 状态:**
- 最近提交: "Initial commit: Project setup with BMAD Method" (62ffea7)
- 清洁状态，可以开始开发

---

## 🎯 Definition of Done (完成定义)

- [ ] 所有 6 个 Maven 模块已创建并配置
- [ ] 包结构遵循 `com.aicodereview.*` 约定
- [ ] 父 POM 正确配置依赖管理
- [ ] 模块间依赖关系正确设置
- [ ] `ApiResponse<T>` 类已创建
- [ ] Spring Boot 主应用类已创建
- [ ] 配置文件（application.yml + profiles）已创建
- [ ] `mvn clean install` 成功执行
- [ ] Spring Boot 应用可以启动
- [ ] 代码已提交到 Git
- [ ] 无编译错误或警告

---

## 💡 Dev Agent Tips (开发 Agent 提示)

### 常见陷阱（必须避免）

❌ **不要做:**
- 创建 `I` 前缀的接口（如 `IService`）
- 在 common 模块中依赖其他模块
- 使用 `spring-boot-starter-parent` 在多个模块
- 跳过包命名约定
- 忘记配置 `scanBasePackages`

✅ **必须做:**
- 遵循严格的模块依赖规则
- 使用标准化的 `ApiResponse<T>`
- 创建完整的目录结构
- 配置多环境支持
- 验证构建成功

### 效率提示

1. **先创建骨架，后填充细节** - 先建立所有模块和目录
2. **使用 IDE 的 Maven 支持** - IntelliJ IDEA 或 Eclipse 可以自动导入模块
3. **增量验证** - 每创建一个模块就验证编译
4. **复制粘贴模板** - 使用 POM 模板加速配置

---

## 📝 Dev Agent Record (开发记录)

### Agent Model Used
Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Implementation Plan
遵循架构规范创建六模块 Maven 项目结构：
1. 创建父 POM 并配置依赖管理
2. 创建所有 6 个子模块的 POM 文件
3. 建立完整的包结构目录
4. 实现核心基础类（ApiResponse, ErrorDetail, ErrorCode）
5. 配置 Spring Boot 主应用类和全局异常处理
6. 创建多环境配置文件
7. 编写单元测试和集成测试
8. 验证构建和应用启动

### Debug Log References
- Maven build logs: C:\Users\songh\.claude\projects\...\tool-results\toolu_01KVuAGD6PCf94dHW52Q1sGX.txt
- Spring Boot startup logs: Task bd6b632 output

### Completion Notes List
✅ **成功完成所有 6 个任务和 24 个子任务**

1. **多模块项目结构** - 成功创建父 POM 和 6 个子模块，严格遵循依赖规则
2. **依赖管理配置** - Java 17, Spring Boot 3.2.2, Lombok 1.18.30, Maven Surefire 2.22.2
3. **包结构初始化** - 所有模块的包结构遵循 `com.aicodereview.*` 约定
4. **配置文件** - application.yml + dev/prod profiles，包含 Actuator 端点配置
5. **基础类实现** - ApiResponse<T>, ErrorDetail, ErrorCode, AppConstants, GlobalExceptionHandler
6. **构建验证** - `mvn clean install` BUILD SUCCESS，所有测试通过（4 tests run）
7. **应用启动** - Spring Boot 成功启动在端口 8080，耗时 2.59 秒
8. **DataSource 排除** - 临时排除 DataSourceAutoConfiguration 直到 Story 1.3

**关键技术决策:**
- 使用父 POM 的 dependencyManagement 统一管理版本
- 仅在 api 模块启用 Spring Boot Maven 插件生成可执行 JAR
- 严格遵守模块依赖规则防止循环依赖
- 标准化 API 响应格式 ApiResponse<T> 用于所有 Controllers

### File List
**创建的文件:**
- backend/pom.xml (父 POM)
- backend/ai-code-review-common/pom.xml
- backend/ai-code-review-repository/pom.xml
- backend/ai-code-review-integration/pom.xml
- backend/ai-code-review-service/pom.xml
- backend/ai-code-review-worker/pom.xml
- backend/ai-code-review-api/pom.xml
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/ApiResponse.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/ErrorDetail.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/ErrorCode.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/constant/AppConstants.java
- backend/ai-code-review-api/src/main/java/com/aicodereview/api/AiCodeReviewApplication.java
- backend/ai-code-review-api/src/main/java/com/aicodereview/api/exception/GlobalExceptionHandler.java
- backend/ai-code-review-api/src/main/resources/application.yml
- backend/ai-code-review-api/src/main/resources/application-dev.yml
- backend/ai-code-review-api/src/main/resources/application-prod.yml
- backend/ai-code-review-common/src/test/java/com/aicodereview/common/dto/ApiResponseTest.java
- backend/ai-code-review-api/src/test/java/com/aicodereview/api/AiCodeReviewApplicationTests.java

**创建的目录结构:**
- 所有 6 个模块的完整 src/main/java, src/main/resources, src/test/java 目录
- 按照架构规范的包结构（controller, dto, exception, config, service, domain, etc.）

---

**Story Created:** 2026-02-05
**Ready for Development:** ✅ YES
**Next Story:** 1.2 - 从 Vue-Vben-Admin 模板初始化前端项目
