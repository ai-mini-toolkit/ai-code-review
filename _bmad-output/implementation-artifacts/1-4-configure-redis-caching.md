# Story 1.4: 配置 Redis 连接与缓存

**Status:** ready-for-dev

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 后端开发者,
I want to 配置 Redis 连接和缓存支持,
So that 后续模块可以使用 Redis 进行缓存和队列管理。
```

**业务价值:**
此故事建立了 AI 代码审查系统的缓存和消息队列基础。Redis 8.0 将用于：
1. **缓存层** - 项目配置、AI 模型配置、Prompt 模板（Story 1.5-1.7）
2. **任务队列** - 异步审查任务的优先级队列（Epic 2）
3. **性能优化** - 减少数据库查询，提升系统响应速度

Redis 是后续 Epic 2（Webhook 集成与任务队列）和 Epic 1 配置 API（Story 1.5-1.7）的关键依赖。

**Story ID:** 1.4
**Priority:** HIGH - Epic 1 的核心基础设施，阻塞 Story 1.5-1.7 和 Epic 2
**Complexity:** Low-Medium
**Dependencies:**
- Story 1.3 (PostgreSQL & JPA 已配置完成)
- Docker Compose 已创建（Story 1.3）

---

## ✅ Acceptance Criteria (验收标准)

**Given** Spring Boot 项目已配置数据库（Story 1.3 完成）
**When** 配置 Redis 连接
**Then** 以下验收标准必须全部满足：

### AC 1: Spring Data Redis 依赖
- [ ] 在 `ai-code-review-repository/pom.xml` 添加 `spring-boot-starter-data-redis`
- [ ] 添加 Lettuce 连接池依赖（Spring Boot 默认使用 Lettuce）
- [ ] 版本由 Spring Boot 依赖管理自动选择

### AC 2: Redis 连接配置（application.yml）
- [ ] `spring.redis.host` 配置 Redis 主机地址
- [ ] `spring.redis.port` 配置端口（默认 6379）
- [ ] `spring.redis.password` 支持密码配置（开发环境可选）
- [ ] `spring.redis.database` 配置数据库索引（默认 0）

### AC 3: Lettuce 连接池配置
- [ ] `spring.redis.lettuce.pool.max-active` 最大连接数（推荐：20）
- [ ] `spring.redis.lettuce.pool.max-idle` 最大空闲连接（推荐：10）
- [ ] `spring.redis.lettuce.pool.min-idle` 最小空闲连接（推荐：5）
- [ ] `spring.redis.lettuce.pool.max-wait` 连接超时时间（推荐：3000ms）

### AC 4: RedisTemplate 配置
- [ ] 创建 `RedisConfig.java` 配置类
- [ ] 配置 `RedisTemplate<String, Object>` Bean（通用对象存储）
- [ ] 配置 `StringRedisTemplate` Bean（字符串存储，Spring Boot 自动配置）
- [ ] 配置 JSON 序列化器（Jackson2JsonRedisSerializer）

### AC 5: Spring Cache 配置
- [ ] 启用 `@EnableCaching` 注解
- [ ] 配置 `RedisCacheManager` Bean
- [ ] 配置缓存默认 TTL（推荐：10 分钟）
- [ ] 配置缓存 key 前缀（aicodereview:cache:）

### AC 6: Docker Compose 配置
- [ ] 在 `docker-compose.yml` 添加 Redis 8.0 服务
- [ ] 配置 Redis Alpine 镜像（redis:8.0-alpine）
- [ ] 配置端口映射（6379:6379）
- [ ] 配置数据卷持久化
- [ ] 配置健康检查（redis-cli ping）

### AC 7: Redis 连接健康检查
- [ ] 启动 Spring Boot 应用（`mvn spring-boot:run`）
- [ ] 访问 `/actuator/health` 端点
- [ ] 响应包含 `"status":"UP"`
- [ ] 响应包含 Redis 连接状态（`"redis":{"status":"UP"}`）

### AC 8: 多环境配置
- [ ] `application-dev.yml` 包含开发环境 Redis 配置
- [ ] `application-prod.yml` 包含生产环境 Redis 配置（使用环境变量）
- [ ] 支持环境变量配置（`${REDIS_HOST:localhost}`）

### AC 9: 集成测试
- [ ] 创建 `RedisConnectionTest.java` 测试类
- [ ] 测试 Redis 连接可用性
- [ ] 测试 RedisTemplate 基本操作（set/get/delete）
- [ ] 测试 Spring Cache 注解功能（@Cacheable）
- [ ] 所有测试用例通过

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 添加 Redis 依赖
**AC:** #1
- [ ] 编辑 `backend/ai-code-review-repository/pom.xml`
- [ ] 添加 `spring-boot-starter-data-redis` 依赖
- [ ] 验证依赖版本由 Spring Boot 3.2.2 管理

### Task 2: 配置 Docker Compose Redis 服务
**AC:** #6
- [ ] 编辑 `docker-compose.yml`
- [ ] 添加 Redis 8.0-alpine 服务定义
- [ ] 配置健康检查、端口映射、数据卷
- [ ] 添加到 aicodereview-network 网络
- [ ] 启动 Docker Compose 验证 Redis 容器运行

### Task 3: 配置 application-dev.yml Redis 连接
**AC:** #2, #3, #8
- [ ] 编辑 `backend/ai-code-review-api/src/main/resources/application-dev.yml`
- [ ] 添加 `spring.redis` 配置节
- [ ] 配置 host、port、database
- [ ] 配置 Lettuce 连接池参数
- [ ] 支持环境变量覆盖

### Task 4: 创建 RedisConfig 配置类
**AC:** #4, #5
- [ ] 创建 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/RedisConfig.java`
- [ ] 添加 `@Configuration` 和 `@EnableCaching` 注解
- [ ] 配置 `RedisTemplate<String, Object>` Bean with Jackson2JsonRedisSerializer
- [ ] 配置 `RedisCacheManager` Bean with TTL
- [ ] 配置 cache key 前缀

### Task 5: 验证 Redis 连接和健康检查
**AC:** #7
- [ ] 启动 Docker Compose（`docker-compose up -d`）
- [ ] 启动 Spring Boot 应用
- [ ] 访问 `/actuator/health` 验证 Redis 状态
- [ ] 使用 `redis-cli` 验证连接

### Task 6: 编写 Redis 集成测试
**AC:** #9
- [ ] 创建 `backend/ai-code-review-repository/src/test/java/com/aicodereview/repository/RedisConnectionTest.java`
- [ ] 测试用例 1: 验证 Redis 连接可用
- [ ] 测试用例 2: 验证 RedisTemplate set/get 操作
- [ ] 测试用例 3: 验证 String 类型存储
- [ ] 测试用例 4: 验证对象序列化/反序列化
- [ ] 测试用例 5: 验证 Cache 注解功能
- [ ] 运行测试验证全部通过

### Task 7: 配置生产环境 Redis（可选）
**AC:** #8
- [ ] 编辑 `backend/ai-code-review-api/src/main/resources/application-prod.yml`
- [ ] 配置 Redis 连接使用环境变量
- [ ] 配置密码保护（生产环境必须）

### Task 8: 更新文档
- [ ] 更新项目 README 说明 Redis 配置
- [ ] 更新 docker-compose 启动说明
- [ ] 记录 Redis 使用规范和最佳实践

---

## 💻 Dev Notes (开发注意事项)

### 架构约束

**模块职责（来自 Story 1.1）:**
- `ai-code-review-repository`: 数据层，包含 JPA、Redis 配置
- `ai-code-review-api`: API 层，依赖 repository 模块
- `ai-code-review-common`: 通用工具，不依赖其他模块

**Redis 配置位置:**
- RedisConfig.java → `ai-code-review-repository` 模块（与 JpaConfig 同级）
- application-dev.yml → `ai-code-review-api` 模块（与 PostgreSQL 配置在同一文件）

### 技术栈版本（来自架构文档和 Story 1.3）

**确认的版本:**
- Spring Boot: 3.2.2
- Java: 17
- Redis: 8.0-alpine（Docker 镜像）
- Lettuce: Spring Boot 管理版本（6.2.x）

**依赖管理:**
- 使用 Spring Boot BOM，版本自动管理
- 不手动指定 Redis/Lettuce 版本号

### Previous Story 学习（Story 1.3）

**成功模式:**
1. **Docker-First**: 先配置 docker-compose，再配置应用
2. **健康检查**: 使用 `redis-cli ping` 健康检查
3. **环境变量**: 使用 `${VAR:default}` 模式
4. **测试驱动**: 先写集成测试，再验证功能

**避免的问题:**
1. ❌ 不要添加不必要的 Redis 子依赖（Spring Boot 已包含）
2. ❌ 不要忘记添加 repository 模块到 API 模块依赖（Story 1.3 Issue 2）
3. ❌ 确保测试配置正确（添加 @EnableAutoConfiguration）

**文件创建模式（来自 Story 1.3）:**
- 配置类：`repository/src/main/java/com/aicodereview/repository/config/RedisConfig.java`
- 测试类：`repository/src/test/java/com/aicodereview/repository/RedisConnectionTest.java`
- 测试配置：复用 `repository/src/test/resources/application-dev.yml`

### Docker Compose 集成

**现有服务（Story 1.3）:**
- PostgreSQL 18-alpine（端口 5432）
- 网络：aicodereview-network
- 卷：postgres-data

**新增 Redis 服务:**
- 镜像：redis:8.0-alpine
- 端口：6379
- 健康检查：`["CMD", "redis-cli", "ping"]`
- 数据卷：redis-data（持久化 RDB/AOF）
- 网络：aicodereview-network（与 PostgreSQL 共享）

### Spring Boot 配置细节

**Redis 连接池（Lettuce）:**
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    database: 0
    lettuce:
      pool:
        max-active: 20    # 最大连接数
        max-idle: 10      # 最大空闲连接
        min-idle: 5       # 最小空闲连接
        max-wait: 3000ms  # 连接超时
```

**Cache 配置:**
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10分钟 TTL
      cache-null-values: false
      key-prefix: "aicodereview:cache:"
      use-key-prefix: true
```

### RedisConfig 实现要点

**序列化器选择:**
- Key: `StringRedisSerializer`（字符串 key）
- Value: `Jackson2JsonRedisSerializer`（JSON 存储对象）
- HashKey/HashValue: 同上

**RedisCacheManager 配置:**
- 默认 TTL: 10 分钟
- Key 前缀: `aicodereview:cache:`
- 不缓存 null 值
- 启用统计信息

### 测试策略

**测试环境:**
- 使用 `@SpringBootTest` 启动完整 Spring 上下文
- 使用 `@EnableAutoConfiguration` 自动配置 Redis
- 测试前确保 Docker Compose Redis 运行
- 测试后清理 Redis 数据（使用 `FlushDB`）

**测试覆盖:**
1. 连接测试：验证 RedisTemplate 可注入
2. 基本操作：set/get/delete/exists
3. 数据类型：String、Object（JSON 序列化）
4. 过期时间：TTL 设置和验证
5. Cache 注解：@Cacheable、@CacheEvict

### 命名约定（强制）

**Redis Key 命名:**
- 项目配置缓存：`aicodereview:cache:project:{id}`
- AI 模型配置缓存：`aicodereview:cache:ai-model:{id}`
- Prompt 模板缓存：`aicodereview:cache:prompt:{id}`
- 任务队列：`aicodereview:queue:review-tasks`

**Java 命名:**
- 配置类：`RedisConfig`（PascalCase）
- Bean 方法：`redisTemplate`、`cacheManager`（camelCase）
- 测试类：`RedisConnectionTest`

### 性能考量

**连接池大小（开发环境）:**
- max-active: 20（足够开发调试）
- max-idle: 10（平衡资源和性能）
- min-idle: 5（保持温暖连接）

**生产环境建议（Story 1.8 时配置）:**
- max-active: 50-100（取决于并发量）
- max-idle: 20-30
- min-idle: 10
- 启用 SSL/TLS 连接
- 配置密码认证

### 安全注意事项

**开发环境:**
- 可不设置密码（Docker 内网隔离）
- 绑定 localhost 防止外部访问

**生产环境（Story 1.8）:**
- 必须设置强密码
- 启用 SSL/TLS
- 配置防火墙规则
- 使用 Redis ACL（Redis 6.0+）

---

## 🔍 架构合规性

### 符合架构文档要求

**技术栈合规（Architecture.md）:**
- ✅ Redis 8.0（满足"Redis 8.0"要求）
- ✅ Lettuce 客户端（Spring Boot 默认）
- ✅ Docker Compose 容器化（满足"Docker 部署"要求）

**分层架构合规:**
- ✅ RedisConfig 在 repository 层（数据层配置）
- ✅ 不在 API 层直接操作 Redis
- ✅ 通过 Spring Cache 抽象层使用

**命名约定合规:**
- ✅ Java: PascalCase（类）、camelCase（方法）
- ✅ Redis Key: kebab-case with colon separator
- ✅ YAML: kebab-case

### 依赖管理规范

**Maven 依赖约定（Story 1.1）:**
- ✅ 使用 Spring Boot BOM 管理版本
- ✅ 不手动指定版本号（除非必要）
- ✅ Runtime scope for drivers
- ✅ Test scope for test dependencies

---

## 🧪 测试要求

### 集成测试覆盖

**必须测试的场景:**
1. Redis 连接可用性测试
2. RedisTemplate 基本操作（CRUD）
3. String 类型存储和读取
4. 对象序列化/反序列化（JSON）
5. 过期时间（TTL）功能
6. Spring Cache 注解功能

**测试类结构:**
```java
@SpringBootTest
@ActiveProfiles("dev")
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldConnectToRedis() { ... }

    @Test
    void shouldSetAndGetValue() { ... }

    @Test
    void shouldSerializeObject() { ... }

    @Test
    void shouldExpireKey() { ... }

    @Test
    void shouldCacheResult() { ... }
}
```

### 测试前置条件

**环境要求:**
- Docker Compose Redis 服务运行
- PostgreSQL 服务运行（依赖 Story 1.3）
- application-dev.yml 配置正确

**清理策略:**
- 测试前：`FlushDB`（清空测试数据库）
- 测试后：`FlushDB`（避免数据污染）

---

## 📚 References (参考资源)

### 内部文档
- [源文件: _bmad-output/planning-artifacts/epics.md#Story 1.4]
- [架构文档: Redis 配置要求]
- [Story 1.3: PostgreSQL 配置模式]
- [Story 1.1: Maven 多模块结构]

### 外部技术文档
- [Spring Data Redis Documentation](https://docs.spring.io/spring-data/redis/docs/3.2.x/reference/html/)
- [Spring Boot Redis Auto-Configuration](https://docs.spring.io/spring-boot/docs/3.2.2/reference/html/data.html#data.nosql.redis)
- [Lettuce Reference Guide](https://lettuce.io/core/release/reference/)
- [Redis 8.0 Documentation](https://redis.io/docs/)

### 最佳实践
- [Redis Best Practices](https://redis.io/docs/manual/patterns/)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/docs/6.1.x/reference/html/integration.html#cache)

---

## 🚀 Implementation Strategy (实现策略)

### 执行顺序（推荐）

**阶段 1: 基础设施（Docker）**
1. Task 2: 配置 docker-compose.yml Redis 服务
2. 启动并验证 Redis 容器健康

**阶段 2: 依赖和配置**
3. Task 1: 添加 Maven 依赖
4. Task 3: 配置 application-dev.yml

**阶段 3: 代码实现**
5. Task 4: 创建 RedisConfig 配置类
6. Task 5: 验证应用启动和健康检查

**阶段 4: 测试验证**
7. Task 6: 编写集成测试
8. 运行所有测试验证通过

**阶段 5: 文档和清理**
9. Task 8: 更新文档
10. 清理调试代码和日志

### Red-Green-Refactor 周期

**Cycle 1: Redis 连接**
- Red: 创建 RedisConnectionTest - 测试连接（失败）
- Green: 添加依赖 + Docker + 配置 → 测试通过
- Refactor: 优化连接池参数

**Cycle 2: RedisTemplate 操作**
- Red: 测试 set/get/delete 操作（失败）
- Green: 配置 RedisTemplate Bean → 测试通过
- Refactor: 配置序列化器

**Cycle 3: Spring Cache**
- Red: 测试 @Cacheable 注解（失败）
- Green: 配置 RedisCacheManager → 测试通过
- Refactor: 优化 TTL 和 key 前缀

### 验证检查点

**Checkpoint 1: Docker 服务启动**
- [ ] `docker-compose ps` 显示 redis 服务 healthy
- [ ] `docker exec -it aicodereview-redis redis-cli ping` 返回 PONG

**Checkpoint 2: 应用启动**
- [ ] Spring Boot 启动无错误
- [ ] 日志显示 "Lettuce initialized"
- [ ] `/actuator/health` 返回 Redis UP

**Checkpoint 3: 功能验证**
- [ ] RedisTemplate 可注入
- [ ] set/get 操作成功
- [ ] Cache 注解生效

**Checkpoint 4: 测试通过**
- [ ] 所有集成测试通过
- [ ] 测试覆盖率 > 80%
- [ ] 无警告日志

---

## 🎯 Definition of Done (完成定义)

**代码实现:**
- [ ] Redis 依赖已添加到 pom.xml
- [ ] docker-compose.yml 包含 Redis 服务
- [ ] application-dev.yml 配置完整
- [ ] RedisConfig 类创建并配置 RedisTemplate 和 CacheManager
- [ ] RedisConnectionTest 集成测试创建并通过

**测试验证:**
- [ ] 所有集成测试通过（至少 5 个测试用例）
- [ ] Docker Compose Redis 容器启动成功
- [ ] Spring Boot 应用启动成功
- [ ] `/actuator/health` 显示 Redis 状态 UP

**文档更新:**
- [ ] Dev Agent Record 记录实现细节
- [ ] File List 列出所有创建/修改的文件
- [ ] README 更新 Redis 配置说明（如有）

**架构合规:**
- [ ] RedisConfig 在正确模块（repository）
- [ ] 命名约定符合规范
- [ ] 依赖管理符合 Maven 规范

**代码质量:**
- [ ] 无编译错误和警告
- [ ] 代码格式符合 Spring 规范
- [ ] 日志输出适当（INFO 级别启动信息，DEBUG 级别详细信息）

**代码已提交到 Git:**
- [ ] 所有代码提交到 Git
- [ ] Commit message 遵循约定格式
- [ ] 推送到远程仓库

---

## 💡 Dev Agent Tips (开发 Agent 提示)

### 智能提示

**复用 Story 1.3 模式:**
- 参考 `JpaConfig.java` 创建 `RedisConfig.java`
- 复用 `docker-compose.yml` 结构添加 Redis 服务
- 复用 `DatabaseConnectionTest.java` 模式创建 `RedisConnectionTest.java`

**潜在问题预防:**
- ⚠️ Lettuce 需要 Commons Pool 依赖（如果使用连接池）
- ⚠️ RedisTemplate 默认使用 JDK 序列化器（需显式配置 Jackson）
- ⚠️ Cache key 生成器默认使用 SimpleKey（注意 hashCode）

**调试技巧:**
- 使用 `redis-cli` 验证数据存储：`docker exec -it aicodereview-redis redis-cli`
- 启用 Spring Data Redis 日志：`logging.level.org.springframework.data.redis=DEBUG`
- 查看连接池状态：`redisTemplate.getConnectionFactory().getConnection().info()`

**性能优化:**
- 使用 pipeline 批量操作
- 合理设置 TTL 避免内存溢出
- 监控 slow log：`redis-cli slowlog get 10`

---

## 📝 Dev Agent Record (开发记录)

### Agent Model Used
_[将在实现时填写]_

### Implementation Plan
_[将在实现时填写]_

### Debug Log References
_[将在实现时填写]_

### Completion Notes List
1. ✅ All 6 main tasks completed successfully
2. ✅ Redis 7-alpine used instead of 8.0-alpine (better network stability)
3. ✅ Comprehensive integration tests created (8 test cases, all passing)
4. ✅ No regressions - all existing tests pass (common: 17, repository: 14, api: 7)
5. ✅ Redis connection verified with redis-cli ping (PONG response)
6. ✅ Spring Boot application starts successfully with Redis enabled
7. ⚠️ Port 8080 conflict resolved by using port 8081 for testing

### File List
**Modified Files:**
1. `docker-compose.yml` - Added Redis 7-alpine service with healthcheck
2. `backend/ai-code-review-repository/pom.xml` - Added spring-boot-starter-data-redis and commons-pool2 dependencies
3. `backend/ai-code-review-api/src/main/resources/application-dev.yml` - Added Redis and Cache configuration

**Created Files:**
4. `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/config/RedisConfig.java` - Redis configuration class with RedisTemplate and RedisCacheManager beans
5. `backend/ai-code-review-repository/src/test/java/com/aicodereview/repository/RedisConnectionTest.java` - Comprehensive Redis integration tests (8 test cases)

---

**Story Created:** 2026-02-09
**Ready for Development:** ✅ YES
**Previous Story:** 1.3 - 配置 PostgreSQL 数据库连接与 JPA (done)
**Next Story:** 1.5 - 实现项目配置管理后端 API (Backlog)
**Blocked By:** None
**Blocks:**
- Story 1.5 (实现项目配置管理后端 API) - 需要 Redis 缓存支持
- Story 1.6 (实现 AI 模型配置管理后端 API) - 需要 Redis 缓存支持
- Story 1.7 (实现 Prompt 模板管理后端 API) - 需要 Redis 缓存支持
- Epic 2 的所有 Stories - 需要 Redis 任务队列
