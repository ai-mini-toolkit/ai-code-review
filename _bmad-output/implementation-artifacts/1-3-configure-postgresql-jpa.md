# Story 1.3: 配置 PostgreSQL 数据库连接与 JPA

**Status:** ready-for-dev

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 后端开发者,
I want to 配置 PostgreSQL 数据库连接和 Spring Data JPA,
So that 后续模块可以进行数据持久化。
```

**业务价值:**
此故事建立了 AI 代码审查系统的数据持久化基础。PostgreSQL 18.x 提供了强大的 JSONB 支持和高级查询能力，对于后续 Epic 2-7 中的任务管理、代码分析、审查结果存储等功能至关重要。同时，Flyway 数据库迁移机制确保了团队协作中数据库架构的版本控制和一致性。

**Story ID:** 1.3
**Priority:** CRITICAL - Epic 1 的核心基础设施，阻塞 Story 1.5-1.7（API 实现）
**Complexity:** Medium
**Dependencies:**
- Story 1.1 (Spring Boot 多模块项目已初始化)
- Docker 环境（PostgreSQL 容器运行）

---

## ✅ Acceptance Criteria (验收标准)

**Given** Spring Boot 项目已初始化（Story 1.1 完成）
**When** 配置数据库连接
**Then** 以下验收标准必须全部满足：

### AC 1: PostgreSQL JDBC 驱动依赖
- [ ] 在 `ai-code-review-repository/pom.xml` 添加 PostgreSQL JDBC 驱动
- [ ] 使用版本：`org.postgresql:postgresql:42.7.x`（最新稳定版）

### AC 2: Flyway 数据库迁移依赖
- [ ] 添加 `org.flywaydb:flyway-core` 依赖
- [ ] 添加 `org.flywaydb:flyway-database-postgresql` 依赖（Flyway 10 要求）
- [ ] 版本由 Spring Boot 依赖管理自动选择

### AC 3: 数据源配置（application.yml）
- [ ] `spring.datasource.url` 配置 PostgreSQL 连接字符串
- [ ] `spring.datasource.username` 和 `password` 配置（支持环境变量）
- [ ] `spring.datasource.driver-class-name` 指定为 `org.postgresql.Driver`

### AC 4: HikariCP 连接池配置
- [ ] `hikari.maximum-pool-size` 配置最大连接数（推荐：20）
- [ ] `hikari.minimum-idle` 配置最小空闲连接（推荐：5）
- [ ] `hikari.connection-timeout` 配置连接超时（推荐：10000ms）
- [ ] `hikari.idle-timeout` 配置空闲超时（推荐：600000ms）
- [ ] `hikari.max-lifetime` 配置连接最大生命周期（推荐：1800000ms）
- [ ] `hikari.leak-detection-threshold` 配置连接泄漏检测（推荐：60000ms）

### AC 5: JPA/Hibernate 配置
- [ ] `spring.jpa.hibernate.ddl-auto=validate`（重要：由 Flyway 管理 DDL）
- [ ] `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect`
- [ ] `spring.jpa.properties.hibernate.jdbc.batch_size=20`（批处理优化）
- [ ] `spring.jpa.properties.hibernate.order_inserts=true`
- [ ] `spring.jpa.properties.hibernate.order_updates=true`
- [ ] 开发环境：`spring.jpa.show-sql=true`
- [ ] 生产环境：`spring.jpa.show-sql=false`

### AC 6: Flyway 配置
- [ ] `spring.flyway.enabled=true`
- [ ] `spring.flyway.locations=classpath:db/migration`
- [ ] `spring.flyway.baseline-on-migrate=true`
- [ ] `spring.flyway.validate-on-migrate=true`

### AC 7: JPA 配置类
- [ ] 创建 `JpaConfiguration.java` 配置类
- [ ] 添加 `@EnableJpaRepositories(basePackages = "com.aicodereview.repository")`
- [ ] 配置在 `ai-code-review-repository` 模块的 `config` 包中

### AC 8: 移除 DataSourceAutoConfiguration 排除
- [ ] 修改 `AiCodeReviewApplication.java`
- [ ] 移除 `exclude = {DataSourceAutoConfiguration.class}`
- [ ] 应用现在可以自动配置数据源

### AC 9: Docker Compose 配置
- [ ] 创建项目根目录的 `docker-compose.yml`
- [ ] 配置 PostgreSQL 18.x 服务（使用 `postgres:18-alpine` 镜像）
- [ ] 配置数据库名称、用户名、密码
- [ ] 配置端口映射（5432:5432）
- [ ] 配置数据卷持久化
- [ ] 配置健康检查（`pg_isready`）

### AC 10: Flyway 初始迁移脚本
- [ ] 创建 `V1__initial_schema.sql`（或按需拆分）
- [ ] 包含基础表结构（如有需要）
- [ ] 脚本位于 `ai-code-review-repository/src/main/resources/db/migration/`
- [ ] 脚本可被 Flyway 成功执行

### AC 11: 环境变量支持
- [ ] 创建 `.env.example` 文件
- [ ] 包含数据库相关的环境变量示例
- [ ] 配置文件使用 `${VAR_NAME:default}` 语法

### AC 12: 数据库连接健康检查
- [ ] 启动 Spring Boot 应用（`mvn spring-boot:run`）
- [ ] 访问 `/actuator/health` 端点
- [ ] 响应包含 `"status":"UP"`
- [ ] 响应包含数据库连接状态（`"db":{"status":"UP"}`）

### AC 13: 多环境配置
- [ ] `application-dev.yml` 包含开发环境数据库配置
- [ ] `application-prod.yml` 包含生产环境数据库配置（使用环境变量）
- [ ] 测试不同 profile 切换正常工作

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 添加 PostgreSQL 和 Flyway 依赖 (AC: 1, 2)
- [ ] 修改 `backend/ai-code-review-repository/pom.xml`
- [ ] 添加 PostgreSQL JDBC 驱动依赖（runtime scope）
- [ ] 添加 Flyway Core 依赖
- [ ] 添加 Flyway PostgreSQL 数据库支持依赖
- [ ] 验证依赖版本由父 POM 管理

### Task 2: 创建 Docker Compose 开发环境 (AC: 9)
- [ ] 在项目根目录创建 `docker-compose.yml`
- [ ] 配置 PostgreSQL 18.x 服务
  - 镜像：`postgres:18-alpine`
  - 数据库：`aicodereview_dev`
  - 用户：`aicodereview`
  - 密码：通过环境变量配置
- [ ] 配置端口映射：`5432:5432`
- [ ] 配置数据卷：`postgres-data:/var/lib/postgresql/data`
- [ ] 配置健康检查：`pg_isready -U aicodereview`
- [ ] 启动容器：`docker-compose up -d postgres`
- [ ] 验证容器运行：`docker-compose ps`

### Task 3: 配置数据源连接 (AC: 3, 4)
- [ ] 修改 `application-dev.yml`
  - 添加 `spring.datasource.url`（localhost:5432）
  - 添加 `spring.datasource.username`（使用环境变量）
  - 添加 `spring.datasource.password`（使用环境变量）
  - 配置 HikariCP 连接池参数
- [ ] 修改 `application-prod.yml`
  - 使用环境变量配置所有敏感信息
  - 调整连接池大小（生产环境更大）
- [ ] 保持 `application.yml` 基础配置不变

### Task 4: 配置 JPA 和 Hibernate (AC: 5)
- [ ] 修改 `application-dev.yml`
  - 设置 `spring.jpa.hibernate.ddl-auto=validate`
  - 设置 `hibernate.dialect=PostgreSQLDialect`
  - 设置批处理参数（jdbc.batch_size=20）
  - 启用 `show-sql=true`（开发环境）
- [ ] 修改 `application-prod.yml`
  - 同样的 JPA 配置
  - 禁用 `show-sql=false`（生产环境）

### Task 5: 配置 Flyway 数据库迁移 (AC: 6)
- [ ] 修改 `application.yml`（或 application-dev.yml）
  - 启用 Flyway：`spring.flyway.enabled=true`
  - 配置迁移路径：`classpath:db/migration`
  - 启用基线迁移：`baseline-on-migrate=true`
  - 启用迁移验证：`validate-on-migrate=true`

### Task 6: 创建 JPA 配置类 (AC: 7)
- [ ] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/JpaConfiguration.java`
- [ ] 添加 `@Configuration` 注解
- [ ] 添加 `@EnableJpaRepositories(basePackages = "com.aicodereview.repository.repository")`
- [ ] 添加 Javadoc 说明配置目的

### Task 7: 移除 DataSource 自动配置排除 (AC: 8)
- [ ] 修改 `AiCodeReviewApplication.java`
- [ ] 移除 `exclude = {DataSourceAutoConfiguration.class}`
- [ ] 验证应用可以启动（依赖 Docker PostgreSQL 容器）

### Task 8: 创建 Flyway 初始迁移脚本 (AC: 10)
- [ ] 创建 `backend/ai-code-review-repository/src/main/resources/db/migration/V1__initial_schema.sql`
- [ ] 添加初始注释说明迁移目的
- [ ] （可选）创建初始表结构（如需要）
- [ ] 或者创建空迁移脚本（占位符，后续 Story 添加表）

### Task 9: 创建环境变量配置文件 (AC: 11)
- [ ] 在项目根目录创建 `.env.example`
- [ ] 添加数据库相关的环境变量示例：
  - `DB_HOST=localhost`
  - `DB_PORT=5432`
  - `DB_NAME=aicodereview_dev`
  - `DB_USERNAME=aicodereview`
  - `DB_PASSWORD=changeme`
- [ ] 添加注释说明如何使用

### Task 10: 验证数据库连接和健康检查 (AC: 12, 13)
- [ ] 启动 Docker Compose：`docker-compose up -d`
- [ ] 启动 Spring Boot 应用：`mvn spring-boot:run -pl ai-code-review-api`
- [ ] 验证应用启动成功（无错误）
- [ ] 访问 `http://localhost:8080/actuator/health`
- [ ] 验证响应包含数据库健康状态
- [ ] 切换到 prod profile 测试：`mvn spring-boot:run -Dspring-boot.run.profiles=prod`
- [ ] 验证多环境配置正常工作

### Task 11: 编写集成测试（可选但推荐）
- [ ] 创建 `JpaConfigurationTest.java`
- [ ] 使用 `@DataJpaTest` 测试 JPA 配置
- [ ] 验证 Repository 扫描正确
- [ ] 验证数据源连接成功

---

## 💻 Dev Notes (开发注意事项)

### PostgreSQL 版本选择

**架构决策（Decision 1.1）**：
- **数据库版本**：PostgreSQL 18.x（最新稳定：18.1）
- **JDBC 驱动版本**：`org.postgresql:postgresql:42.7.x`（独立于数据库版本）
- **为什么选择 PostgreSQL 18.x**：
  - 6+ 表需要 JSONB 字段（`project.thresholds`, `review_result.issues` 等）
  - GIN 索引支持快速 JSON 查询
  - 丰富的 JSON 操作符（`->`, `->>`, `@>`, `?`）
  - 优秀的查询优化器

**Docker 镜像选择**：
- 推荐：`postgres:18-alpine`（小体积，最新版本）
- 备选：`postgres:18`（标准版本）
- 不推荐：`postgres:15-alpine`（全局规则建议，但架构文档要求 18.x）

### Flyway 配置要点

**重要变更（Flyway 10 + Spring Boot 3.3）**：
- Flyway 10 需要单独的 PostgreSQL 数据库支持依赖
- 必须添加：`org.flywaydb:flyway-database-postgresql`
- 否则会报错：`Unable to find flyway-database-postgresql in classpath`

**迁移脚本命名规范**：
```
V{version}__{description}.sql

示例：
V1__initial_schema.sql
V2__create_project_table.sql
V3__create_review_task_table.sql
V4__add_aws_codecommit_support.sql
```

**版本号规则**：
- 必须以 `V` 开头（大写）
- 版本号用双下划线 `__` 分隔描述
- 版本号可以是 `1`, `1.1`, `1.1.1` 等格式
- 描述使用小写字母和下划线

**迁移脚本位置**：
```
ai-code-review-repository/
└── src/main/resources/
    └── db/migration/
        ├── V1__initial_schema.sql
        ├── V2__create_project_table.sql
        └── V3__create_review_task_table.sql
```

### HikariCP 连接池配置详解

**开发环境推荐配置**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10          # 开发环境较小
      minimum-idle: 2                 # 最小空闲连接
      connection-timeout: 10000       # 10 秒获取连接超时
      idle-timeout: 600000            # 10 分钟空闲超时
      max-lifetime: 1800000           # 30 分钟最大生命周期
      leak-detection-threshold: 60000 # 60 秒连接泄漏检测
      pool-name: AiCodeReviewHikariCP
```

**生产环境推荐配置**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # 生产环境更大
      minimum-idle: 5
      connection-timeout: 10000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      pool-name: AiCodeReviewHikariCP-Prod
```

**参数说明**：
- **maximum-pool-size**: 最大连接数，根据并发需求调整
- **minimum-idle**: 保持的最小空闲连接数，提高响应速度
- **connection-timeout**: 从连接池获取连接的最大等待时间
- **idle-timeout**: 连接空闲多久后关闭（释放资源）
- **max-lifetime**: 连接的最大生命周期（防止长连接问题）
- **leak-detection-threshold**: 连接泄漏检测阈值（调试用）

### JPA/Hibernate 配置策略

**关键决策：ddl-auto=validate + Flyway**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 重要：不使用 create/update/create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.batch_size: 20        # 批量插入/更新优化
        order_inserts: true         # 批量操作排序优化
        order_updates: true
        format_sql: true            # 开发环境格式化 SQL
    show-sql: true                  # 开发环境显示 SQL
```

**为什么不使用 ddl-auto=create/update**：
- ❌ 不可控的表结构变更（危险）
- ❌ 无版本控制（团队协作困难）
- ❌ 无法回滚（出错难以恢复）
- ✅ Flyway 提供：Version Controlled SQL、团队协作友好、可回滚

**开发 vs 生产配置差异**：
| 配置项 | 开发环境 | 生产环境 |
|-------|---------|---------|
| `show-sql` | `true` | `false` |
| `format_sql` | `true` | `false` |
| `use_sql_comments` | `true` | `false` |
| `generate_statistics` | `true` | `false` |
| 连接池大小 | 10 | 20-50 |

### 环境变量配置模式

**推荐的配置模式**（Spring Boot 环境变量注入）：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:aicodereview_dev}
    username: ${DB_USERNAME:aicodereview}
    password: ${DB_PASSWORD:changeme}
```

**语法说明**：
- `${VAR_NAME:default}` - 如果环境变量不存在，使用默认值
- 开发环境：使用默认值（方便快速启动）
- 生产环境：必须设置环境变量（安全）

**.env.example 文件示例**：
```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aicodereview_dev
DB_USERNAME=aicodereview
DB_PASSWORD=changeme

# 生产环境示例
# DB_HOST=prod-db-server.example.com
# DB_NAME=aicodereview_prod
# DB_USERNAME=aicodereview_prod
# DB_PASSWORD=<strong-password-here>
```

**如何使用 .env 文件**：
1. 复制 `.env.example` 为 `.env`
2. 修改 `.env` 中的值
3. 启动应用前加载环境变量：
   ```bash
   # Linux/Mac
   export $(cat .env | xargs)
   mvn spring-boot:run

   # Windows PowerShell
   Get-Content .env | ForEach-Object { $line = $_.Split('='); [Environment]::SetEnvironmentVariable($line[0], $line[1]) }
   mvn spring-boot:run
   ```

### Docker Compose 配置详解

**完整的 docker-compose.yml 模板**：
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:18-alpine
    container_name: aicodereview-postgres
    environment:
      POSTGRES_DB: aicodereview_dev
      POSTGRES_USER: aicodereview
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aicodereview"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - aicodereview-network

volumes:
  postgres-data:
    driver: local

networks:
  aicodereview-network:
    driver: bridge
```

**配置要点**：
- **image**: `postgres:18-alpine` - 小体积镜像
- **container_name**: 便于识别和管理
- **environment**: 数据库初始化参数
- **ports**: 端口映射（主机:容器）
- **volumes**: 数据持久化（数据库重启后数据不丢失）
- **healthcheck**: 健康检查（确保数据库可用）
- **networks**: 自定义网络（后续添加 Redis、backend 服务）

**启动和管理命令**：
```bash
# 启动 PostgreSQL 容器
docker-compose up -d postgres

# 查看容器状态
docker-compose ps

# 查看日志
docker-compose logs -f postgres

# 进入 PostgreSQL 容器
docker-compose exec postgres psql -U aicodereview -d aicodereview_dev

# 停止容器
docker-compose down

# 停止并删除数据卷（危险！会删除所有数据）
docker-compose down -v
```

### JPA 配置类实现

**JpaConfiguration.java 完整示例**：
```java
package com.aicodereview.repository.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA 配置类
 *
 * 功能：
 * - 启用 JPA Repositories 扫描
 * - 启用事务管理
 * - 配置 Repository 基础包路径
 *
 * @author AI Code Review Team
 * @since 1.0
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.aicodereview.repository.repository"
)
@EnableTransactionManagement
public class JpaConfiguration {
    // 配置类主要通过注解工作，通常不需要额外的 Bean 定义
    // Spring Boot 自动配置会处理 EntityManagerFactory 和 TransactionManager
}
```

**为什么需要这个配置类**：
- 显式指定 Repository 扫描路径（避免扫描错误的包）
- 启用事务管理（`@Transactional` 注解生效）
- 提供集中的 JPA 配置入口（未来可添加自定义配置）

### 从 Story 1.1 学到的经验

#### 经验 1: 依赖管理模式
- ✅ 继续使用 BOM import 方式（父 POM 的 `dependencyManagement`）
- ✅ PostgreSQL 驱动使用 `<scope>runtime</scope>`（不需要编译时依赖）
- ✅ Flyway 依赖让 Spring Boot 自动管理版本

#### 经验 2: 配置文件结构
- ✅ `application.yml` - 基础配置（应用名、端口、Actuator）
- ✅ `application-dev.yml` - 开发环境特定配置（show-sql=true）
- ✅ `application-prod.yml` - 生产环境特定配置（环境变量）

#### 经验 3: 模块依赖规则（严格遵守）
```
当前依赖关系：
api → service, common
service → repository, integration, common
repository → common  ← Story 1.3 在这里添加数据库依赖
integration → common
worker → service, common
common → 无依赖（不允许依赖其他模块）
```

**重要**：
- PostgreSQL 驱动添加到 `repository` 模块
- Flyway 依赖添加到 `repository` 模块
- 不要在 `common` 模块添加数据库依赖

#### 经验 4: 配置注释的处理
Story 1.1 中已在 `application-dev.yml` 添加了数据库配置的占位符注释：
```yaml
# Database configuration (will be added in Story 1.3)
# spring:
#   datasource:
#     url: jdbc:postgresql://localhost:5432/ai_code_review
```

Story 1.3 需要：
- 解除这些注释
- 填充完整的数据库配置
- 添加 HikariCP 和 JPA 配置

---

## 🔍 架构合规性

### 来源文档引用

- **架构文档**: `_bmad-output/planning-artifacts/architecture.md`
  - Decision 1.1: Database - PostgreSQL（第 283-320 行）
  - Decision 1.2: Message Queue - Redis Queue（第 321-353 行）
  - Decision 1.3: Data Migration Strategy（第 356-380 行）
  - Decision 1.5: Caching Strategy（第 407-438 行）

- **Epic 文档**: `_bmad-output/planning-artifacts/epics/epic-1.md`
  - Epic 1: 项目基础设施与配置管理
  - Story 1.3: 配置 PostgreSQL 数据库连接与 JPA（第 73-95 行）

- **Story 1.1 文档**: `_bmad-output/implementation-artifacts/1-1-initialize-spring-boot-multi-module-project.md`
  - Maven 依赖管理模式
  - 配置文件结构
  - 模块依赖规则

- **全局规则**: `C:\Users\songh\.claude\projects\...\memory\MEMORY.md`
  - Docker-First Strategy for Infrastructure Services
  - Integration Tests with Docker Compose

- **Web 研究来源**:
  - [PostgreSQL JDBC Driver - pgJDBC](https://jdbc.postgresql.org/)
  - [Flyway PostgreSQL Support](https://documentation.red-gate.com/fd/postgresql-database-277579325.html)
  - [Flyway 10 Spring Boot 3 Compatibility](https://github.com/openrewrite/rewrite-spring/issues/532)
  - [Maven Repository: PostgreSQL Driver](https://mvnrepository.com/artifact/org.postgresql/postgresql)

### 关键架构决策

1. **PostgreSQL 18.x** - 架构文档明确要求（JSONB 支持）
2. **Flyway 数据库迁移** - 版本控制的 DDL 管理
3. **JPA ddl-auto=validate** - 不使用 JPA 自动 DDL
4. **HikariCP 连接池** - Spring Boot 默认，最高性能
5. **多环境配置** - 开发、生产环境隔离
6. **环境变量注入** - 安全的凭证管理
7. **Docker Compose** - 开发环境基础设施

---

## 🧪 测试要求

### 单元测试（可选但推荐）

**JpaConfigurationTest.java**:
```java
package com.aicodereview.repository.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev")
class JpaConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void jpaRepositoriesEnabled() {
        // 验证 JPA Repositories 已启用
        assertThat(context.containsBean("jpaConfiguration")).isTrue();
    }
}
```

### 集成测试

**数据库连接测试**:
```java
package com.aicodereview.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void databaseConnectionSuccessful() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(5)).isTrue();
        }
    }
}
```

### 健康检查验证

**手动测试步骤**:
1. 启动 Docker Compose: `docker-compose up -d`
2. 启动 Spring Boot 应用: `mvn spring-boot:run -pl ai-code-review-api`
3. 访问健康检查端点: `curl http://localhost:8080/actuator/health`
4. 验证响应:
   ```json
   {
     "status": "UP",
     "components": {
       "db": {
         "status": "UP",
         "details": {
           "database": "PostgreSQL",
           "validationQuery": "isValid()"
         }
       }
     }
   }
   ```

### Flyway 迁移验证

**验证步骤**:
1. 确保 `V1__initial_schema.sql` 存在
2. 启动应用（Flyway 自动执行迁移）
3. 检查日志输出：
   ```
   Flyway Community Edition x.x.x by Redgate
   Flyway: Migrating schema "public" to version "1 - initial schema"
   Flyway: Successfully applied 1 migration
   ```
4. 连接数据库验证：
   ```bash
   docker-compose exec postgres psql -U aicodereview -d aicodereview_dev
   \dt  # 列出所有表
   SELECT * FROM flyway_schema_history;  # 查看迁移历史
   ```

---

## 📚 References (参考资源)

### 内部文档
- [Architecture Document - Data Architecture](../_bmad-output/planning-artifacts/architecture.md#data-architecture)
- [Epic 1 Requirements](../_bmad-output/planning-artifacts/epics/epic-1.md)
- [Story 1.1 - Spring Boot Multi-Module Project](1-1-initialize-spring-boot-multi-module-project.md)
- [Global Development Rules](~/.claude/projects/.../memory/MEMORY.md)

### 外部资源 - PostgreSQL
- [PostgreSQL Official Documentation](https://www.postgresql.org/docs/18/)
- [PostgreSQL JDBC Driver (pgJDBC)](https://jdbc.postgresql.org/)
- [PostgreSQL JDBC Driver Downloads](https://jdbc.postgresql.org/download/)
- [Maven Repository: PostgreSQL Driver](https://mvnrepository.com/artifact/org.postgresql/postgresql)

### 外部资源 - Flyway
- [Flyway Official Documentation](https://documentation.red-gate.com/fd)
- [Flyway PostgreSQL Database Support](https://documentation.red-gate.com/fd/postgresql-database-277579325.html)
- [Flyway with Spring Boot Guide](https://bell-sw.com/blog/how-to-use-flyway-with-spring-boot/)
- [Database Migrations with Flyway (Baeldung)](https://www.baeldung.com/database-migrations-with-flyway)

### 外部资源 - Spring Data JPA
- [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization)
- [HikariCP Configuration Guide](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby)

### 外部资源 - Docker
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)

---

## 🚀 Implementation Strategy (实现策略)

### 推荐实现顺序

**Phase 1: Docker 环境准备（优先）**
1. 创建 `docker-compose.yml`
2. 启动 PostgreSQL 容器
3. 验证容器运行和健康检查

**Phase 2: 添加依赖**
1. 修改 `repository/pom.xml`
2. 添加 PostgreSQL JDBC 驱动
3. 添加 Flyway Core 和 PostgreSQL 支持
4. 执行 `mvn clean install` 验证

**Phase 3: 配置数据源和 JPA**
1. 修改 `application-dev.yml`（数据源、HikariCP、JPA）
2. 修改 `application-prod.yml`（环境变量）
3. 创建 `.env.example`

**Phase 4: 配置 Flyway**
1. 在 `application.yml` 添加 Flyway 配置
2. 创建 `V1__initial_schema.sql`（空文件或基础表）

**Phase 5: 创建 JPA 配置类**
1. 创建 `JpaConfiguration.java`
2. 添加 `@EnableJpaRepositories` 注解

**Phase 6: 移除 DataSource 排除**
1. 修改 `AiCodeReviewApplication.java`
2. 移除 `exclude = {DataSourceAutoConfiguration.class}`

**Phase 7: 验证和测试**
1. 启动应用
2. 验证健康检查
3. 验证 Flyway 迁移执行
4. 运行集成测试（可选）

### 当前项目状态

**已完成（来自 Story 1.1）**：
- ✅ 6 个 Maven 模块已创建
- ✅ repository 模块已包含 JPA 依赖
- ✅ application.yml 基础配置完成
- ✅ application-dev.yml 包含数据库配置占位符
- ✅ db/migration/ 目录已创建（空）

**待完成（Story 1.3）**：
- ⏳ 添加 PostgreSQL JDBC 和 Flyway 依赖
- ⏳ 配置数据源连接
- ⏳ 配置 HikariCP 连接池
- ⏳ 配置 JPA/Hibernate
- ⏳ 创建 JPA 配置类
- ⏳ 创建 Docker Compose 配置
- ⏳ 创建 Flyway 初始迁移
- ⏳ 移除 DataSource 自动配置排除

**Git 状态**：
- 当前分支: master
- 最近提交: "Update sprint status: Story 1.1 completed" (86dd69e)
- 准备提交 Story 1.3 的更改

---

## 🎯 Definition of Done (完成定义)

- [ ] PostgreSQL JDBC 驱动依赖已添加到 repository 模块
- [ ] Flyway Core 和 PostgreSQL 支持依赖已添加
- [ ] docker-compose.yml 已创建并配置 PostgreSQL 18.x 服务
- [ ] PostgreSQL 容器可成功启动（`docker-compose up -d`）
- [ ] application-dev.yml 数据源配置完成（包含 HikariCP 参数）
- [ ] application-prod.yml 使用环境变量配置
- [ ] JPA/Hibernate 配置完成（ddl-auto=validate, PostgreSQLDialect）
- [ ] Flyway 配置完成（enabled=true, locations=classpath:db/migration）
- [ ] JpaConfiguration 类已创建（@EnableJpaRepositories）
- [ ] DataSourceAutoConfiguration 排除已移除
- [ ] V1__initial_schema.sql 迁移脚本已创建
- [ ] .env.example 文件已创建
- [ ] Spring Boot 应用成功启动（无错误）
- [ ] /actuator/health 返回数据库状态 UP
- [ ] Flyway 迁移成功执行（日志确认）
- [ ] 代码已提交到 Git（按照 Git 规范）
- [ ] 无编译错误或警告

---

## 💡 Dev Agent Tips (开发 Agent 提示)

### 常见陷阱（必须避免）

❌ **不要做:**
- 使用 `spring.jpa.hibernate.ddl-auto=create` 或 `update`（应该用 `validate`）
- 忘记添加 `flyway-database-postgresql` 依赖（Flyway 10 必需）
- 在 common 模块添加数据库依赖（违反模块依赖规则）
- 将数据库密码硬编码在配置文件中（应该用环境变量）
- 跳过 HikariCP 配置（性能和连接泄漏检测）
- 使用 `postgres:latest` 镜像（应该指定版本号）
- 忘记配置健康检查（Actuator 端点）
- 在生产环境启用 `show-sql=true`（性能影响）

✅ **必须做:**
- 使用 `ddl-auto=validate` + Flyway 管理 DDL
- 添加 Flyway PostgreSQL 数据库支持依赖
- 遵循模块依赖规则（数据库依赖在 repository 模块）
- 使用环境变量注入敏感配置
- 配置完整的 HikariCP 参数（连接池大小、超时等）
- 使用 `postgres:18-alpine` 明确版本号
- 配置 Actuator 健康检查端点
- 开发环境和生产环境分离配置

### 常见问题排查

**问题 1: 应用启动失败 - "Failed to configure a DataSource"**
- **原因**: PostgreSQL 容器未启动或配置错误
- **解决**:
  1. 检查 Docker 容器状态: `docker-compose ps`
  2. 查看容器日志: `docker-compose logs postgres`
  3. 验证数据库配置（url, username, password）

**问题 2: Flyway 报错 - "Unable to find flyway-database-postgresql"**
- **原因**: 缺少 Flyway PostgreSQL 数据库支持依赖
- **解决**:
  1. 添加依赖到 repository/pom.xml:
     ```xml
     <dependency>
         <groupId>org.flywaydb</groupId>
         <artifactId>flyway-database-postgresql</artifactId>
     </dependency>
     ```
  2. 执行 `mvn clean install`

**问题 3: 健康检查失败 - "DB connection failed"**
- **原因**: 数据库连接参数错误或容器未就绪
- **解决**:
  1. 验证数据库 URL、用户名、密码
  2. 检查 PostgreSQL 容器健康状态
  3. 尝试手动连接数据库:
     ```bash
     docker-compose exec postgres psql -U aicodereview -d aicodereview_dev
     ```

**问题 4: Flyway 迁移脚本未执行**
- **原因**: 迁移脚本命名错误或位置错误
- **解决**:
  1. 检查脚本命名格式: `V1__description.sql`（V 大写，双下划线）
  2. 检查脚本位置: `src/main/resources/db/migration/`
  3. 检查 Flyway 配置: `spring.flyway.locations`

**问题 5: 连接池耗尽 - "Connection pool exhausted"**
- **原因**: 连接泄漏或连接池配置过小
- **解决**:
  1. 检查连接泄漏: 查看 `leak-detection-threshold` 日志
  2. 增大连接池: 调整 `maximum-pool-size`
  3. 确保使用 `@Transactional` 或 try-with-resources 正确关闭连接

### 效率提示

1. **先启动 Docker，后启动应用** - 避免数据库连接失败
2. **使用 Docker Compose 健康检查** - 确保数据库就绪后再连接
3. **开发环境启用 show-sql** - 方便调试 SQL 问题
4. **使用 Actuator health 端点** - 快速验证数据库连接状态
5. **Flyway 迁移脚本版本控制** - 提交到 Git，团队共享
6. **使用 .env 文件管理本地配置** - 不提交到 Git（添加到 .gitignore）

### 从 Story 1.1 和 1.2 学到的经验应用

**应用到 Story 1.3**:
- **严格遵循模块依赖规则** - 数据库依赖只在 repository 模块
- **多环境配置** - dev/prod profiles 清晰分离
- **完整的验证步骤** - Docker 启动 + 应用启动 + 健康检查
- **详细的 Dev Notes** - 防止常见错误和陷阱
- **Web 研究支持** - 获取最新的 JDBC 驱动和 Flyway 版本信息
- **Docker-First 策略** - 所有基础设施服务使用 Docker

---

## 📝 Dev Agent Record (开发记录)

### Agent Model Used
_[将在实现时填写]_

### Implementation Plan
_[将在实现时填写]_

### Debug Log References
_[将在实现时填写]_

### Completion Notes List
_[将在实现时填写]_

### File List
_[将在实现时填写]_

---

**Story Created:** 2026-02-05
**Ready for Development:** ✅ YES
**Previous Story:** 1.2 - 从 Vue-Vben-Admin 模板初始化前端项目 (ready-for-dev)
**Next Story:** 1.4 - 配置 Redis 连接与缓存 (Backlog)
**Blocked By:** None（但需要 Docker 环境）
**Blocks:**
- Story 1.5 (实现项目配置管理后端 API) - 需要数据库持久化
- Story 1.6 (实现 AI 模型配置管理后端 API) - 需要数据库持久化
- Story 1.7 (实现 Prompt 模板管理后端 API) - 需要数据库持久化
- Epic 2-7 的所有数据持久化相关 Stories
