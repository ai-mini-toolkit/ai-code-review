# Story 5.3: 实现审查结果查询 API

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 前端开发者和 API 调用方，
I want 通过 REST API 查询审查结果和结构化报告，
so that 可以在 Web 界面展示审查历史、查看详细报告，并支持多种输出格式（JSON/Markdown/HTML）。

## Acceptance Criteria (BDD)

### AC1: 获取单个审查结果
**Given** 审查结果已通过 Story 5.1 持久化到数据库
**When** 调用 `GET /api/v1/reviews/{taskId}/result`
**Then** 返回 `ApiResponse<ReviewResultDTO>`，HTTP 200
**And** 包含完整的 issues 列表、statistics、metadata 字段

### AC2: 获取结构化报告（JSON 格式）
**Given** 审查结果已持久化
**When** 调用 `GET /api/v1/reviews/{taskId}/report` 或 `GET /api/v1/reviews/{taskId}/report?format=json`
**Then** 返回 `ApiResponse<ReviewReportDTO>`，HTTP 200，Content-Type: application/json
**And** 报告包含 issuesByFile、issuesBySeverity、issuesByCategory 分组数据

### AC3: 获取 Markdown/HTML 格式报告
**Given** 审查结果已持久化
**When** 调用 `GET /api/v1/reviews/{taskId}/report?format=markdown`
**Then** 返回纯文本 Markdown，HTTP 200，Content-Type: text/plain
**When** 调用 `GET /api/v1/reviews/{taskId}/report?format=html`
**Then** 返回自包含 HTML，HTTP 200，Content-Type: text/html

### AC4: 分页查询审查历史
**Given** 数据库中存在多个审查结果
**When** 调用 `GET /api/v1/reviews?page=0&size=20&sort=createdAt,desc`
**Then** 返回 `ApiResponse<Page<ReviewSummaryDTO>>`，HTTP 200
**And** `ReviewSummaryDTO` 包含轻量级字段：resultId, taskId, projectName, branch, author, success, totalIssues, createdAt
**And** 分页信息包含 totalElements, totalPages, number, size

### AC5: 按条件过滤审查历史
**Given** 数据库中存在多个审查结果
**When** 调用 `GET /api/v1/reviews?projectId=1`
**Then** 只返回指定项目的审查结果
**When** 调用 `GET /api/v1/reviews?success=true`
**Then** 只返回成功的审查结果
**When** 同时使用 `?projectId=1&success=false`
**Then** 返回交集结果

### AC6: 错误处理
**Given** 传入的 taskId 不存在
**When** 调用 `GET /api/v1/reviews/{taskId}/result` 或 `/report`
**Then** 返回 `ApiResponse.error(ERR_404, ...)`，HTTP 404
**Given** format 参数值无效
**When** 调用 `GET /api/v1/reviews/{taskId}/report?format=pdf`
**Then** 返回 `ApiResponse.error(ERR_400, ...)`，HTTP 400

### AC7: 集成测试
**Given** 实现完成
**When** 运行集成测试
**Then** 验证：
- GET /result 返回完整审查结果
- GET /report?format=json 返回结构化报告
- GET /report?format=markdown 返回 Markdown 文本
- GET /report?format=html 返回 HTML 文本
- GET /reviews 返回分页列表
- GET /reviews?projectId=X 过滤正确
- GET /reviews?success=true 过滤正确
- 不存在的 taskId 返回 404
- 无效 format 返回 400

## Tasks / Subtasks

- [x] Task 1: 创建 ReviewSummaryDTO (AC: #4)
  - [x] 1.1 在 common 模块 `dto/result/` 包创建 `ReviewSummaryDTO.java`
  - [x] 1.2 轻量级字段：resultId, taskId, projectName, branch, author, success, errorMessage, totalIssues, createdAt
- [x] Task 2: 扩展 Repository 和 Service 层查询能力 (AC: #4, #5)
  - [x] 2.1 在 ReviewResultRepository 添加分页查询方法（使用 @EntityGraph 避免 N+1）
  - [x] 2.2 在 ReviewResultService 接口添加 `Page<ReviewSummaryDTO> listResults(Long projectId, Boolean success, Pageable pageable)` 方法
  - [x] 2.3 在 ReviewResultServiceImpl 实现分页查询逻辑
  - [x] 2.4 在 ReviewResultMapper 添加 `toSummaryDTO()` 静态方法
- [x] Task 3: 创建 ReviewResultController (AC: #1, #2, #3, #4, #5, #6)
  - [x] 3.1 在 api 模块创建 `ReviewResultController.java`
  - [x] 3.2 实现 `GET /{taskId}/result` 端点
  - [x] 3.3 实现 `GET /{taskId}/report` 端点（支持 format 参数）
  - [x] 3.4 实现 `GET /` 列表端点（支持分页和过滤）
  - [x] 3.5 format 参数校验（json/markdown/html，无效值返回 400）
- [x] Task 4: 编写 Service 层单元测试 (AC: #7)
  - [x] 4.1 测试 listResults 分页查询逻辑
  - [x] 4.2 测试 toSummaryDTO 映射
- [x] Task 5: 编写 API 集成测试 (AC: #7)
  - [x] 5.1 创建 `ReviewResultControllerIntegrationTest.java`
  - [x] 5.2 @BeforeAll 设置测试数据（项目 + 任务 + 审查结果）
  - [x] 5.3 测试 GET /result 成功和 404
  - [x] 5.4 测试 GET /report 三种格式
  - [x] 5.5 测试 GET / 分页列表
  - [x] 5.6 测试过滤（projectId、success）
  - [x] 5.7 测试无效 format 返回 400

## Dev Notes

### 架构模式与约束

- **模块放置**：遵循已建立的分层架构：
  - DTO → `ai-code-review-common` 模块 `dto/result/` 包
  - Controller → `ai-code-review-api` 模块 `controller/` 包
  - Service 修改 → `ai-code-review-service` 模块
  - Repository 修改 → `ai-code-review-repository` 模块
- **不需要新 Entity/Migration**：复用 Story 5.1 的 ReviewResultEntity 和 review_result 表
- **不引入新依赖**：Spring Data JPA 的 `Pageable`/`Page` 已在框架中

### 关键技术细节

1. **-parameters 编译器标志未启用**（关键！）：
   - **必须** `@PathVariable("taskId")` 带显式值
   - **必须** `@RequestParam(value = "format", ...)` 带显式值
   - **必须** `@RequestParam(value = "projectId", ...)` 带显式值
   - **必须** `@Param("projectId")` 带显式值

2. **Controller 注解模式**（从现有 Controller 提取）：
   ```java
   @Slf4j
   @RestController
   @RequestMapping("/api/v1/reviews")
   @RequiredArgsConstructor
   public class ReviewResultController {
       private final ReviewResultService reviewResultService;
       private final ReviewReportService reviewReportService;
   }
   ```
   **注意**：现有 Controller 使用 `@RequiredArgsConstructor`（Lombok），但现有 Service 使用显式构造函数。Controller 保持 Lombok 风格。

3. **ApiResponse 包装规则**：
   - JSON 响应：`ResponseEntity.ok(ApiResponse.success(data))`
   - Markdown/HTML 响应：直接返回 `String` body + 手动设置 Content-Type（不用 ApiResponse 包装）
   - 错误响应由 `GlobalExceptionHandler` 自动处理

4. **report 端点的 format 参数设计**：
   ```java
   @GetMapping("/{taskId}/report")
   public ResponseEntity<?> getReport(
           @PathVariable("taskId") Long taskId,
           @RequestParam(value = "format", defaultValue = "json") String format) {

       ReviewReportDTO report = reviewReportService.generateReport(taskId);

       switch (format.toLowerCase()) {
           case "markdown":
               return ResponseEntity.ok()
                       .contentType(MediaType.TEXT_PLAIN)
                       .body(reviewReportService.renderMarkdown(report));
           case "html":
               return ResponseEntity.ok()
                       .contentType(MediaType.TEXT_HTML)
                       .body(reviewReportService.renderHtml(report));
           case "json":
               return ResponseEntity.ok(ApiResponse.success(report));
           default:
               throw new IllegalArgumentException(
                       "Unsupported format: " + format + ". Supported: json, markdown, html");
       }
   }
   ```
   **注意**：`IllegalArgumentException` 会被 `GlobalExceptionHandler` 捕获并返回 400 Bad Request。

5. **分页查询设计**：
   ```java
   @GetMapping
   public ResponseEntity<ApiResponse<Page<ReviewSummaryDTO>>> listReviews(
           @RequestParam(value = "projectId", required = false) Long projectId,
           @RequestParam(value = "success", required = false) Boolean success,
           Pageable pageable) {
       Page<ReviewSummaryDTO> results = reviewResultService.listResults(projectId, success, pageable);
       return ResponseEntity.ok(ApiResponse.success(results));
   }
   ```
   Spring Boot 自动解析 `page`, `size`, `sort` 参数到 `Pageable` 对象。

6. **Repository 分页查询 — 避免 N+1**：
   ```java
   // 使用 @EntityGraph 代替 JOIN FETCH（JOIN FETCH 与 Pageable 不兼容）
   @EntityGraph(attributePaths = {"reviewTask", "reviewTask.project"})
   Page<ReviewResultEntity> findAll(Pageable pageable);

   @EntityGraph(attributePaths = {"reviewTask", "reviewTask.project"})
   Page<ReviewResultEntity> findByReviewTaskProjectId(@Param("projectId") Long projectId, Pageable pageable);

   @EntityGraph(attributePaths = {"reviewTask", "reviewTask.project"})
   @Query("SELECT r FROM ReviewResultEntity r WHERE r.success = :success")
   Page<ReviewResultEntity> findPageBySuccess(@Param("success") Boolean success, Pageable pageable);

   @EntityGraph(attributePaths = {"reviewTask", "reviewTask.project"})
   @Query("SELECT r FROM ReviewResultEntity r WHERE r.reviewTask.project.id = :projectId AND r.success = :success")
   Page<ReviewResultEntity> findByProjectIdAndSuccess(@Param("projectId") Long projectId, @Param("success") Boolean success, Pageable pageable);
   ```
   **关键**：`@EntityGraph` 和 `Page<>` 兼容，`JOIN FETCH` 和 `Page<>` 不兼容（会导致内存分页警告）。

7. **ReviewSummaryDTO 映射**：
   ```java
   // ReviewResultMapper.toSummaryDTO()
   public static ReviewSummaryDTO toSummaryDTO(ReviewResultEntity entity) {
       ReviewStatisticsDTO stats = deserializeStatistics(entity.getStatistics());
       return ReviewSummaryDTO.builder()
               .resultId(entity.getId())
               .taskId(entity.getReviewTask().getId())
               .projectName(entity.getReviewTask().getProject().getName())
               .branch(entity.getReviewTask().getBranch())
               .author(entity.getReviewTask().getAuthor())
               .success(entity.getSuccess())
               .errorMessage(entity.getErrorMessage())
               .totalIssues(stats != null ? stats.getTotal() : 0)
               .createdAt(entity.getCreatedAt())
               .build();
   }
   ```

8. **集成测试数据准备**：
   测试需要在数据库中创建完整的调用链：Project → ReviewTask (RUNNING) → ReviewResult。

   ```java
   @BeforeAll
   static void setupTestData(
           @Autowired ProjectRepository projectRepository,
           @Autowired ReviewTaskRepository reviewTaskRepository,
           @Autowired ReviewResultService reviewResultService) {
       // 清理
       // 注意：ON DELETE CASCADE 会清理 review_task 和 review_result
       projectRepository.deleteAll();

       // 创建项目
       Project project = projectRepository.save(Project.builder()...);

       // 创建 RUNNING 状态的任务
       ReviewTask task = ReviewTask.builder()
               .project(project)
               .status(TaskStatus.RUNNING)
               ...
               .build();
       reviewTaskRepository.save(task);

       // 通过 service 保存结果（会自动更新 task 状态为 COMPLETED）
       reviewResultService.saveResult(taskId, reviewResult);
   }
   ```
   **关键**：使用 `@BeforeAll static` + `@Autowired` 参数注入进行一次性数据准备。

9. **HTTP 方法与状态码**（遵循已建立模式）：
   | 端点 | 方法 | 成功状态码 |
   |------|------|-----------|
   | /{taskId}/result | GET | 200 |
   | /{taskId}/report | GET | 200 |
   | / (列表) | GET | 200 |

### 依赖的已实现类（直接使用，无需修改）

| 类 | 模块 | 用途 |
|---|------|------|
| `ReviewResultService.getResultByTaskId()` | service | 获取单个审查结果 |
| `ReviewReportService.generateReport()` | service | 生成结构化报告 |
| `ReviewReportService.renderMarkdown()` | service | 渲染 Markdown |
| `ReviewReportService.renderHtml()` | service | 渲染 HTML |
| `ReviewResultDTO` | common/dto/result | 审查结果响应 |
| `ReviewReportDTO` | common/dto/result | 结构化报告响应 |
| `ApiResponse` | common/dto | 标准响应包装 |
| `GlobalExceptionHandler` | api/exception | 异常处理（404, 400 等） |
| `ResourceNotFoundException` | common/exception | 404 异常 |

### 需要修改的已有文件

| 文件 | 模块 | 修改内容 |
|------|------|---------|
| `ReviewResultRepository.java` | repository | 添加 4 个分页查询方法（@EntityGraph） |
| `ReviewResultService.java` | service | 添加 `listResults()` 方法签名 |
| `ReviewResultServiceImpl.java` | service | 实现 `listResults()` 方法 |
| `ReviewResultMapper.java` | service/mapper | 添加 `toSummaryDTO()` 静态方法 |

### Story 5.1 和 5.2 的 Code Review 教训

以下问题应避免重复：
1. **`-parameters` 未启用**：所有 `@PathVariable`、`@RequestParam`、`@Param` 必须带显式 value 属性
2. **`Boolean.TRUE.equals()`**：使用 `Boolean` 包装类时，不要直接用 `?` 三元运算符，用 `Boolean.TRUE.equals()`
3. **使用 LinkedHashMap** 保持 Map key 的稳定顺序
4. **toDTO/toSummaryDTO 方法放在 Mapper 中**，不放在 Service 的 private 方法里
5. **Markdown 表格内容需转义 `|` 字符**（Story 5.2 code review 发现并修复）
6. **DateTimeFormatter 的 literal 文本需要用单引号包裹**（如 `'UTC'`）
7. **排序测试的输入数据不应已经是期望顺序**（否则测试无效）

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── result/
          └── ReviewSummaryDTO.java              ← 新增

backend/ai-code-review-api/src/main/java/com/aicodereview/api/
  └── controller/
      └── ReviewResultController.java            ← 新增
```

**修改文件清单**：
```
backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/
  └── ReviewResultRepository.java                ← 修改（添加分页查询）

backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  ├── ReviewResultService.java                    ← 修改（添加 listResults 方法）
  └── impl/
      └── ReviewResultServiceImpl.java            ← 修改（实现 listResults）

backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/
  └── ReviewResultMapper.java                     ← 修改（添加 toSummaryDTO）
```

**测试文件**：
```
backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/
  └── ReviewResultControllerIntegrationTest.java  ← 新增

backend/ai-code-review-service/src/test/java/com/aicodereview/service/
  └── impl/
      └── ReviewResultServiceImplTest.java        ← 修改（添加 listResults 测试）

backend/ai-code-review-service/src/test/java/com/aicodereview/service/mapper/
  └── ReviewResultMapperTest.java                 ← 修改（添加 toSummaryDTO 测试）
```

### 集成点

- **上游**：`ReviewResultService`（Story 5.1）→ 获取持久化的审查结果
- **上游**：`ReviewReportService`（Story 5.2）→ 生成结构化报告和渲染
- **下游**：Epic 6（质量阈值 API）→ 查询审查结果用于阈值判断
- **下游**：Epic 7（通知系统）→ 查询报告数据生成通知
- **下游**：Epic 8（Web 界面）→ 前端调用查询 API 展示数据

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 5 Stories - Story 5.4（原编号，已重编号为 5.3）]
- [Source: _bmad-output/planning-artifacts/architecture.md#API 层 - ReviewResultController]
- [Source: _bmad-output/implementation-artifacts/5-1-review-result-persistence-storage.md — Story 5.1 完整实现]
- [Source: _bmad-output/implementation-artifacts/5-2-review-report-generation-service.md — Story 5.2 完整实现]
- [Source: backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/ProjectController.java — Controller 模式参考]
- [Source: backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/ProjectControllerIntegrationTest.java — 集成测试模式参考]
- [Source: backend/ai-code-review-api/src/main/java/com/aicodereview/api/exception/GlobalExceptionHandler.java — 异常处理]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewResultService.java — 现有 Service 接口]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewReportService.java — 报告 Service 接口]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewResultRepository.java — 现有 Repository]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewResultEntity.java — 结果实体]
- [Source: _bmad-output/implementation-artifacts/epic-4-retro-2026-02-15.md — CallGraph 移除决策（call-graph 端点已移除）]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- PostgreSQL JSONB type mismatch: `column "issues" is of type jsonb but expression is of type character varying` — fixed by adding `?stringtype=unspecified` to all JDBC URLs (application-dev.yml, application-docker.yml, repository test application-dev.yml)
- Surefire 2.22.2 `-Dtest` filter does not discover nested test classes — ran full module tests without filter
- ReviewTaskIntegrationTest has 4 pre-existing failures (webhook auth "cannot retry due to server authentication, in streaming mode") — unrelated to Story 5.3

### Completion Notes List

- All 599 tests pass across 5 modules (130 common + 14 repository + 177 integration + 181 service + 97 API), except 4 pre-existing ReviewTaskIntegrationTest failures
- 22 new tests: 5 listResults unit tests + 2 toSummaryDTO unit tests + 15 integration tests
- ReviewResultController with 3 endpoints: GET /result, GET /report (json/markdown/html), GET / (paginated list with filtering)
- Repository uses @EntityGraph (not JOIN FETCH) for paginated queries to avoid memory paging warnings
- Service listResults() dispatches to 4 repository methods based on filter combination
- JSONB fix: Added `?stringtype=unspecified` to PostgreSQL JDBC URLs in all profiles

### Senior Developer Review (AI)

**Reviewer**: Claude Opus 4.6 (adversarial review)
**Date**: 2026-02-16
**Outcome**: Approved (after fixes)

**Issues Found**: 0 Critical, 2 Medium, 5 Low
**Issues Fixed**: 2 Medium

**Fixes Applied**:
1. **M1** (ReviewResultRepository): Replaced `@Override findAll(Pageable)` with `findAllWithAssociations(Pageable)` to preserve JPA default fetch strategy for other callers. Updated caller in `ReviewResultServiceImpl` and unit test.
2. **M2** (Integration test): Added sort order validation to `shouldListAllReviews()` — now asserts `firstCreatedAt >= lastCreatedAt` for `sort=createdAt,desc`.

**Remaining LOW items** (accepted as-is):
- L1: `stats != null` dead branch in `toSummaryDTO()` — harmless
- L2: Unnecessary `@Param` on derived query method — no functional impact
- L3: Test helper reuses taskId as projectId — unit test only
- L4: `int totalIssues` vs wrapper type inconsistency — functionally correct
- L5: No case-insensitive format test — code handles it, low risk

### Change Log

| Date | Change | Description |
|------|--------|-------------|
| 2026-02-16 | ADD | ReviewSummaryDTO — lightweight DTO for paginated listing |
| 2026-02-16 | ADD | ReviewResultController — REST controller with 3 endpoints |
| 2026-02-16 | ADD | ReviewResultControllerIntegrationTest — 15 integration tests |
| 2026-02-16 | MOD | ReviewResultRepository — 4 @EntityGraph paginated query methods |
| 2026-02-16 | MOD | ReviewResultService — listResults() method signature |
| 2026-02-16 | MOD | ReviewResultServiceImpl — listResults() implementation |
| 2026-02-16 | MOD | ReviewResultMapper — toSummaryDTO() static method |
| 2026-02-16 | MOD | ReviewResultServiceImplTest — 5 new listResults tests |
| 2026-02-16 | MOD | ReviewResultMapperTest — 2 new toSummaryDTO tests |
| 2026-02-16 | FIX | application-dev.yml — JDBC URL stringtype=unspecified for JSONB |
| 2026-02-16 | FIX | application-docker.yml — JDBC URL stringtype=unspecified for JSONB |
| 2026-02-16 | FIX | repository test application-dev.yml — JDBC URL stringtype=unspecified |
| 2026-02-16 | FIX | ReviewResultRepository — replaced @Override findAll with findAllWithAssociations (code review M1) |
| 2026-02-16 | FIX | ReviewResultServiceImpl — updated to call findAllWithAssociations (code review M1) |
| 2026-02-16 | FIX | ReviewResultServiceImplTest — updated mock for findAllWithAssociations (code review M1) |
| 2026-02-16 | FIX | ReviewResultControllerIntegrationTest — added sort order validation (code review M2) |

### File List

**New files (3):**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewSummaryDTO.java`
- `backend/ai-code-review-api/src/main/java/com/aicodereview/api/controller/ReviewResultController.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/ReviewResultControllerIntegrationTest.java`

**Modified files (9):**
- `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewResultRepository.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewResultService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewResultMapper.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/mapper/ReviewResultMapperTest.java`
- `backend/ai-code-review-api/src/main/resources/application-dev.yml`
- `backend/ai-code-review-api/src/main/resources/application-docker.yml`
- `backend/ai-code-review-repository/src/test/resources/application-dev.yml`
