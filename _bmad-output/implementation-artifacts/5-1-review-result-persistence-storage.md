# Story 5.1: 实现审查结果持久化存储

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 系统（ReviewOrchestrator 调用方），
I want 将 AI 审查结果持久化到 PostgreSQL 数据库，
so that 后续可以查询、展示审查报告和统计信息。

## Acceptance Criteria (BDD)

### AC1: review_result 表结构
**Given** AI 审查引擎（Epic 4）已实现并返回 ReviewResult DTO
**When** 审查完成（成功或失败）
**Then** 创建 `review_result` 表（Flyway V8 迁移），包含以下字段：
- `id`（BIGSERIAL PRIMARY KEY）
- `task_id`（BIGINT NOT NULL UNIQUE，外键关联 review_task.id，ON DELETE CASCADE）
- `issues`（JSONB NOT NULL DEFAULT '[]'::jsonb，问题列表）
- `statistics`（JSONB NOT NULL DEFAULT '{}'::jsonb，问题统计）
- `metadata`（JSONB NOT NULL DEFAULT '{}'::jsonb，审查元数据：provider、model、tokens、durationMs、degradationEvents）
- `success`（BOOLEAN NOT NULL DEFAULT FALSE）
- `error_message`（TEXT，仅失败时非空）
- `created_at`（TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP）
- `updated_at`（TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP）

**And** 创建索引：
- `idx_review_result_task_id` ON review_result(task_id)
- `idx_review_result_created_at` ON review_result(created_at DESC)

### AC2: issues JSONB 结构对齐 ReviewIssue DTO
**Given** ReviewResult 包含 List\<ReviewIssue\>
**When** 序列化到 issues 列
**Then** JSON 结构如下（字段名严格对齐 ReviewIssue DTO）：
```json
[
  {
    "severity": "CRITICAL",
    "category": "SECURITY",
    "filePath": "UserService.java",
    "line": 42,
    "message": "SQL injection vulnerability",
    "suggestion": "Use PreparedStatement instead"
  }
]
```

### AC3: statistics JSONB 结构
**Given** ReviewResult 包含问题列表
**When** 持久化时自动计算统计
**Then** statistics 结构：
```json
{
  "total": 15,
  "bySeverity": {"CRITICAL": 2, "HIGH": 5, "MEDIUM": 8, "LOW": 0, "INFO": 0},
  "byCategory": {"SECURITY": 3, "PERFORMANCE": 4, "MAINTAINABILITY": 8, "CORRECTNESS": 0, "STYLE": 0, "BEST_PRACTICES": 0}
}
```

### AC4: ReviewResultRepository JPA 仓库
**Given** ReviewResultEntity 已定义
**When** 需要数据访问
**Then** 实现 ReviewResultRepository extends JpaRepository\<ReviewResultEntity, Long\>
**And** 提供查询方法：
- `findByReviewTaskId(Long taskId)` — 按任务ID查询（1:1关系）
- `findBySuccess(Boolean success)` — 按成功/失败过滤

### AC5: ReviewResultService.saveResult() 方法
**Given** ReviewOrchestrator 返回 ReviewResult
**When** 调用 `saveResult(Long taskId, ReviewResult reviewResult)`
**Then** 完成以下操作：
1. 验证 taskId 存在（否则抛 ResourceNotFoundException）
2. 将 issues 列表序列化为 JSON
3. 从 issues 计算 statistics（按 severity、category 分组计数）
4. 将 metadata 序列化为 JSON
5. 构建 ReviewResultEntity 并持久化
6. 返回 ReviewResultDTO

### AC6: 更新 review_task 状态
**Given** 审查结果已保存
**When** saveResult() 成功
**Then** 更新 review_task.status = COMPLETED，review_task.completed_at = Instant.now()
**And** 在同一事务中完成（@Transactional）

### AC7: 失败结果持久化
**Given** AI 审查失败（ReviewResult.success = false）
**When** saveResult() 被调用
**Then** success = false，error_message 包含失败原因
**And** issues = []（空数组），statistics = 空统计
**And** review_task.status 仍更新为 COMPLETED（审查流程完成，只是结果为失败）

### AC8: 单元测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- 成功结果持久化（issues、statistics、metadata 正确序列化）
- 失败结果持久化（error_message 存储，issues 为空）
- taskId 不存在时抛出 ResourceNotFoundException
- JSON 序列化/反序列化往返一致
- statistics 计算正确（按 severity/category 分组）
- review_task 状态更新为 COMPLETED

## Tasks / Subtasks

- [x] Task 1: 创建 Flyway 迁移 V8 (AC: #1)
  - [x] 1.1 创建 `V8__create_review_result_table.sql`
  - [x] 1.2 定义表结构（id, task_id, issues, statistics, metadata, success, error_message, timestamps）
  - [x] 1.3 添加索引（task_id, created_at DESC）
  - [x] 1.4 添加外键约束（task_id → review_task.id ON DELETE CASCADE）
- [x] Task 2: 创建 ReviewResultEntity (AC: #1, #2)
  - [x] 2.1 在 repository 模块创建 `ReviewResultEntity.java`
  - [x] 2.2 使用 @OneToOne 关联 ReviewTask（LAZY fetch）
  - [x] 2.3 JSONB 列使用 String 类型存储（手动序列化）
  - [x] 2.4 审计字段：@CreatedDate, @LastModifiedDate
- [x] Task 3: 创建 ReviewResultRepository (AC: #4)
  - [x] 3.1 创建 `ReviewResultRepository.java` extends JpaRepository
  - [x] 3.2 实现 `findByReviewTaskId()` 方法（JOIN FETCH 避免 N+1）
  - [x] 3.3 实现 `findBySuccess()` 方法
- [x] Task 4: 创建 ReviewResultDTO 和 ReviewStatisticsDTO (AC: #3)
  - [x] 4.1 在 common 模块创建 `ReviewResultDTO.java`
  - [x] 4.2 创建 `ReviewStatisticsDTO.java`（total, bySeverity, byCategory）
- [x] Task 5: 创建 ReviewResultMapper (AC: #5)
  - [x] 5.1 在 service 模块创建 `ReviewResultMapper.java`
  - [x] 5.2 实现 toDTO()：Entity → DTO（含 JSON 反序列化）
  - [x] 5.3 实现 calculateStatistics()：从 issues 列表计算统计
- [x] Task 6: 实现 ReviewResultService (AC: #5, #6, #7)
  - [x] 6.1 创建 `ReviewResultService.java` 接口
  - [x] 6.2 创建 `ReviewResultServiceImpl.java` 实现
  - [x] 6.3 实现 saveResult()：序列化 + 持久化 + 状态更新
  - [x] 6.4 实现 getResultByTaskId()：查询结果
  - [x] 6.5 @Transactional 事务保证（saveResult 中同时保存 result 和更新 task 状态）
- [x] Task 7: 编写测试 (AC: #8)
  - [x] 7.1 ReviewResultServiceImpl 单元测试（Mock Repository, 5 tests）
  - [x] 7.2 ReviewResultMapper 单元测试（JSON 序列化/反序列化, 11 tests）
  - [x] 7.3 ReviewResultRepository 集成测试 — 跳过（需要 Docker 环境启动 PostgreSQL，留给 E2E 测试阶段）
  - [x] 7.4 统计计算测试（多种 severity/category 组合，含在 MapperTest 中）

## Dev Notes

### 架构模式与约束

- **模块放置**：遵循已建立的分层架构：
  - Entity + Repository → `ai-code-review-repository` 模块
  - Service + Mapper → `ai-code-review-service` 模块
  - DTO → `ai-code-review-common` 模块
- **命名注意**：Entity 类名用 `ReviewResultEntity`（避免与 common 中已有的 `ReviewResult` DTO 冲突）
- **JSONB 策略**：使用 String 类型存储 JSONB 列，通过 Jackson ObjectMapper 手动序列化/反序列化（不使用 JPA AttributeConverter）
- **外键关系**：ReviewResultEntity 与 ReviewTask 是 **@OneToOne**（一个 task 只有一个 result），不是 @ManyToOne

### 关键技术细节

1. **-parameters 编译器标志未启用**：
   - 使用 `@Param("taskId")` 显式命名 JPQL 参数
   - 使用 `@PathVariable("id")` 显式绑定路径变量

2. **Flyway 迁移版本**：当前最新迁移为 V7，新迁移使用 **V8**

3. **Instant 时间戳**：所有时间字段使用 `java.time.Instant`，数据库列用 `TIMESTAMPTZ`（与 V6 迁移一致）

4. **Jackson ObjectMapper 配置**：
   - 必须注册 `JavaTimeModule` 以正确序列化 Instant 类型
   - 使用 `@JsonProperty` 确保 JSON 字段名与 DTO 字段名一致

5. **统计计算**：在 Service 层计算（不在数据库层），遍历 issues 列表按 severity 和 category 分组计数

### 已有 DTO 复用（Epic 4 产物）

以下 DTO 已在 common 模块实现，Story 5.1 **直接复用**，无需修改：

| DTO | 路径 | 字段 |
|-----|------|------|
| `ReviewResult` | common/dto/review/ReviewResult.java | issues, metadata, success, errorMessage |
| `ReviewIssue` | common/dto/review/ReviewIssue.java | severity, category, filePath, line, message, suggestion |
| `ReviewMetadata` | common/dto/review/ReviewMetadata.java | providerId, model, promptTokens, completionTokens, durationMs, degradationEvents |
| `IssueSeverity` | common/enums/IssueSeverity.java | CRITICAL(5), HIGH(4), MEDIUM(3), LOW(2), INFO(1) |
| `IssueCategory` | common/enums/IssueCategory.java | SECURITY, PERFORMANCE, MAINTAINABILITY, CORRECTNESS, STYLE, BEST_PRACTICES |

### 已有 Entity 参考模式（ReviewTask）

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "review_task")
@EntityListeners(AuditingEntityListener.class)
public class ReviewTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_review_task_project"))
    private Project project;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    // ...
}
```

### 已有 Mapper 参考模式（ReviewTaskMapper）

```java
@Component @Slf4j
public class ReviewTaskMapper {
    public ReviewTaskDTO toDTO(ReviewTask entity) {
        return ReviewTaskDTO.builder()
            .id(entity.getId())
            .projectId(entity.getProject().getId())
            // ...
            .build();
    }
}
```

### 已有 Service 参考模式

```java
@Service @Slf4j @Transactional
public class ReviewTaskServiceImpl implements ReviewTaskService {
    private final ReviewTaskRepository repository;
    private final ReviewTaskMapper mapper;
    // constructor injection...
}
```

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── result/
          ├── ReviewResultDTO.java         ← 新增
          └── ReviewStatisticsDTO.java     ← 新增

backend/ai-code-review-repository/
  ├── src/main/java/com/aicodereview/repository/
  │   ├── entity/
  │   │   └── ReviewResultEntity.java      ← 新增
  │   └── ReviewResultRepository.java      ← 新增
  └── src/main/resources/db/migration/
      └── V8__create_review_result_table.sql  ← 新增

backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  ├── ReviewResultService.java             ← 新增（接口）
  ├── impl/
  │   └── ReviewResultServiceImpl.java     ← 新增
  └── mapper/
      └── ReviewResultMapper.java          ← 新增
```

**测试文件**：
```
backend/ai-code-review-service/src/test/java/com/aicodereview/service/
  ├── impl/
  │   └── ReviewResultServiceImplTest.java     ← 单元测试 (7 tests)
  └── mapper/
      └── ReviewResultMapperTest.java          ← 单元测试 (11 tests)
```

### 集成点

- **上游**：`ReviewOrchestrator.review(task)` 返回 `ReviewResult` → 调用 `ReviewResultService.saveResult(taskId, result)`
- **下游**：Story 5.2（报告生成）和 Story 5.3（查询 API）将依赖本 Story 的 Entity/Repository/Service

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 5 Stories - Story 5.1]
- [Source: _bmad-output/planning-artifacts/architecture.md#数据库设计 - JSONB 列]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java — Entity 模式]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewTaskRepository.java — Repository 模式]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewTaskMapper.java — Mapper 模式]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewResult.java — 输入 DTO]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewIssue.java — Issue DTO]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewMetadata.java — Metadata DTO]
- [Source: backend/ai-code-review-repository/src/main/resources/db/migration/V5__create_review_task_table.sql — 迁移模式]
- [Source: _bmad-output/implementation-artifacts/epic-4-retro-2026-02-15.md — CallGraph 移除决策]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Full regression: 306 tests pass (130 common + 14 repository + 162 service), 0 failures
- API module: 4 pre-existing errors in ReviewTaskIntegrationTest (HttpRetryException, unrelated to Story 5.1)

### Completion Notes List

- Implemented Flyway V8 migration with review_result table (JSONB columns for issues/statistics/metadata)
- Created ReviewResultEntity with @OneToOne to ReviewTask, String-based JSONB storage
- Created ReviewResultRepository with JOIN FETCH queries to prevent N+1
- Created ReviewResultDTO and ReviewStatisticsDTO in common module's dto/result package
- Created ReviewResultMapper with full JSON round-trip serialization, statistics calculation, and toDTO conversion
- Created ReviewResultService interface and ReviewResultServiceImpl with @Transactional saveResult()
- saveResult() handles both success and failure cases, updates task status to COMPLETED
- saveResult() validates task status (must be RUNNING) and checks for duplicate results
- 18 new unit tests (7 service + 11 mapper), all passing
- Repository integration test deferred (requires Docker PostgreSQL, covered by E2E in Epic 9)

### Change Log

- 2026-02-15: Story 5.1 implementation complete — review result persistence with JSONB storage
- 2026-02-15: Code review fixes — removed redundant index, added duplicate/status validation, moved toDTO to mapper, added updatedAt to DTO, removed exposed ObjectMapper, unified Map types

### File List

#### New Files
- backend/ai-code-review-repository/src/main/resources/db/migration/V8__create_review_result_table.sql
- backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewResultEntity.java
- backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewResultRepository.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewResultDTO.java
- backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewStatisticsDTO.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewResultService.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java
- backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewResultMapper.java
- backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java
- backend/ai-code-review-service/src/test/java/com/aicodereview/service/mapper/ReviewResultMapperTest.java
