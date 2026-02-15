# Story 5.2: 实现审查报告生成服务

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 开发者（前端和通知系统的调用方），
I want 从持久化的审查结果生成结构化审查报告，
so that 可以清晰展示代码问题分布、按文件/严重性/类别分组的问题列表，并支持 Markdown 和 HTML 格式渲染。

## Acceptance Criteria (BDD)

### AC1: ReviewReportDTO 结构化报告数据
**Given** 审查结果已通过 Story 5.1 持久化到数据库
**When** 调用 `generateReport(Long taskId)`
**Then** 返回 `ReviewReportDTO`，包含以下字段：
- `taskId`（Long，审查任务 ID）
- `projectName`（String，项目名称）
- `branch`（String，分支名）
- `author`（String，提交者）
- `reviewedAt`（Instant，审查完成时间）
- `success`（Boolean，审查是否成功）
- `errorMessage`（String，失败原因，仅 success=false 时非空）
- `summary`（ReviewStatisticsDTO，问题统计汇总）
- `issuesByFile`（Map<String, List<ReviewIssue>>，按文件分组）
- `issuesBySeverity`（Map<String, List<ReviewIssue>>，按严重性分组，key 为 enum name）
- `issuesByCategory`（Map<String, List<ReviewIssue>>，按类别分组，key 为 enum name）
- `metadata`（ReviewMetadata，AI 审查元数据）

### AC2: 问题分组与排序
**Given** 审查结果包含多个 ReviewIssue
**When** 生成报告
**Then** 完成以下分组和排序：
1. `issuesByFile`：按 `filePath` 分组，每组内按严重性降序排列（CRITICAL → INFO）
2. `issuesBySeverity`：按 `severity` 分组，包含所有 IssueSeverity 枚举值（空列表代替缺失的 severity）
3. `issuesByCategory`：按 `category` 分组，包含所有 IssueCategory 枚举值（空列表代替缺失的 category）
4. Map 的 key 使用 enum 的 `name()` 字符串（如 "CRITICAL"、"SECURITY"），与 ReviewStatisticsDTO 保持一致

### AC3: 失败结果报告
**Given** 审查失败（success = false）
**When** 生成报告
**Then** 返回报告对象，其中：
- `success` = false
- `errorMessage` 包含失败原因
- `issuesByFile`、`issuesBySeverity`、`issuesByCategory` 均为空 Map
- `summary` 为零值统计

### AC4: Markdown 格式渲染
**Given** ReviewReportDTO 已生成
**When** 调用 `renderMarkdown(ReviewReportDTO report)`
**Then** 返回 Markdown 字符串，格式如下：
```markdown
# Code Review Report

**Project:** {projectName} | **Branch:** {branch} | **Author:** {author}
**Reviewed:** {reviewedAt} | **Status:** {success ? "Passed" : "Failed"}

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 2     |
| HIGH     | 5     |
| ...      | ...   |

**Total Issues:** 15

## Issues by File

### UserService.java (3 issues)

| # | Severity | Category | Line | Message | Suggestion |
|---|----------|----------|------|---------|------------|
| 1 | CRITICAL | SECURITY | 42   | SQL injection | Use PreparedStatement |

### Controller.java (2 issues)
...

## Review Metadata

- **Provider:** anthropic
- **Model:** claude-sonnet
- **Tokens:** 1000 prompt + 500 completion
- **Duration:** 2000ms
```

### AC5: HTML 格式渲染
**Given** ReviewReportDTO 已生成
**When** 调用 `renderHtml(ReviewReportDTO report)`
**Then** 返回自包含 HTML 字符串（内联 CSS），包含：
- 标题和项目信息头部
- 严重性统计表格
- 按文件分组的问题列表（CRITICAL/HIGH 用红/橙色高亮）
- 元数据脚注
- 无外部 CSS/JS 依赖（适合邮件发送）

### AC6: taskId 不存在处理
**Given** 传入的 taskId 不存在或无对应审查结果
**When** 调用 `generateReport(taskId)`
**Then** 抛出 `ResourceNotFoundException`

### AC7: 单元测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- 成功结果生成完整报告（分组正确、排序正确）
- 失败结果生成空报告（issues 为空、summary 为零）
- taskId 不存在时抛出 ResourceNotFoundException
- Markdown 渲染包含正确的表格和标题结构
- HTML 渲染包含 `<table>` 和内联样式
- 空 issues 列表生成有效报告
- 多文件多 severity 的复杂场景

## Tasks / Subtasks

- [x] Task 1: 创建 ReviewReportDTO (AC: #1)
  - [x] 1.1 在 common 模块 `dto/result/` 包创建 `ReviewReportDTO.java`
  - [x] 1.2 包含所有字段：taskId, projectName, branch, author, reviewedAt, success, errorMessage, summary, issuesByFile, issuesBySeverity, issuesByCategory, metadata
- [x] Task 2: 创建 ReviewReportService 接口 (AC: #1, #4, #5)
  - [x] 2.1 在 service 模块创建 `ReviewReportService.java` 接口
  - [x] 2.2 定义方法：`generateReport(Long taskId)`, `renderMarkdown(ReviewReportDTO)`, `renderHtml(ReviewReportDTO)`
- [x] Task 3: 实现 ReviewReportServiceImpl (AC: #1, #2, #3, #6)
  - [x] 3.1 创建 `ReviewReportServiceImpl.java`
  - [x] 3.2 注入 `ReviewResultService`（获取 ReviewResultDTO）和 `ReviewTaskRepository`（获取任务元数据）
  - [x] 3.3 实现 `generateReport()`：获取结果 → 获取任务信息 → 分组 → 排序 → 组装 DTO
  - [x] 3.4 问题按文件分组（Collectors.groupingBy filePath）
  - [x] 3.5 问题按严重性分组（确保所有 IssueSeverity 枚举值都有 key）
  - [x] 3.6 问题按类别分组（确保所有 IssueCategory 枚举值都有 key）
  - [x] 3.7 每组内按严重性降序排序（使用 IssueSeverity.getScore() 比较）
- [x] Task 4: 实现 Markdown 渲染 (AC: #4)
  - [x] 4.1 实现 `renderMarkdown(ReviewReportDTO)` 方法
  - [x] 4.2 生成标题、项目信息、统计表格、按文件分组的问题表、元数据
  - [x] 4.3 失败报告只显示错误信息
- [x] Task 5: 实现 HTML 渲染 (AC: #5)
  - [x] 5.1 实现 `renderHtml(ReviewReportDTO)` 方法
  - [x] 5.2 使用 StringBuilder 拼接 HTML（不引入模板引擎）
  - [x] 5.3 内联 CSS 样式（适合邮件）
  - [x] 5.4 严重性颜色：CRITICAL=#dc3545, HIGH=#fd7e14, MEDIUM=#ffc107, LOW=#17a2b8, INFO=#6c757d
- [x] Task 6: 编写单元测试 (AC: #7)
  - [x] 6.1 ReviewReportServiceImpl 单元测试（Mock ReviewResultService + ReviewTaskRepository）
  - [x] 6.2 测试成功报告的分组和排序
  - [x] 6.3 测试失败报告的空内容
  - [x] 6.4 测试 Markdown 渲染输出
  - [x] 6.5 测试 HTML 渲染输出
  - [x] 6.6 测试 taskId 不存在的异常

## Dev Notes

### 架构模式与约束

- **模块放置**：遵循 Story 5.1 建立的分层架构：
  - DTO → `ai-code-review-common` 模块 `dto/result/` 包
  - Service 接口 + 实现 → `ai-code-review-service` 模块
- **不需要新的 Entity/Repository/Migration**：本 Story 纯服务层逻辑，复用 Story 5.1 的数据访问层
- **不引入新依赖**：不需要模板引擎（Thymeleaf/FreeMarker），HTML 用 StringBuilder 生成

### 关键技术细节

1. **-parameters 编译器标志未启用**：
   - 如果使用 `@PathVariable`，必须显式指定 `@PathVariable("id")`
   - 如果使用 `@Param`，必须显式指定 `@Param("name")`

2. **依赖 Story 5.1 的类**（均已实现，直接使用）：
   | 类 | 模块 | 用途 |
   |---|------|------|
   | `ReviewResultService.getResultByTaskId()` | service | 获取持久化的审查结果 |
   | `ReviewResultDTO` | common/dto/result | 审查结果数据 |
   | `ReviewStatisticsDTO` | common/dto/result | 统计数据 |
   | `ReviewIssue` | common/dto/review | 问题详情 |
   | `ReviewMetadata` | common/dto/review | 元数据 |
   | `IssueSeverity` | common/enums | 严重性枚举（有 getScore() 方法用于排序） |
   | `IssueCategory` | common/enums | 类别枚举（有 getDisplayName() 方法） |

3. **ReviewTaskRepository 访问**：
   - 需要从 ReviewTask 获取 projectName、branch、author
   - ReviewTask 有 `@ManyToOne(LAZY) Project project`，通过 `task.getProject().getName()` 获取项目名
   - ReviewResultDTO 包含 `taskId`，可以通过 `reviewTaskRepository.findById(taskId)` 获取

4. **IssueSeverity 排序**：使用 `getScore()` 方法（CRITICAL=5, HIGH=4, MEDIUM=3, LOW=2, INFO=1），降序排列

5. **Map 的 key 类型**：使用 `String`（enum.name()），不使用 enum 类型作为 key，因为 JSON 序列化需要字符串 key

6. **HTML 渲染**：
   - 使用 StringBuilder 拼接，不引入模板引擎（YAGNI 原则）
   - 所有 CSS 内联在 `style` 属性中（邮件客户端兼容）
   - 不使用外部图片/字体（邮件安全策略限制）

### 已有 Service 参考模式

```java
// ReviewResultServiceImpl.java 模式（Story 5.1）
@Slf4j
@Service
@Transactional
public class ReviewResultServiceImpl implements ReviewResultService {
    private final ReviewResultRepository reviewResultRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    // constructor injection...
}
```

**注意**：ReviewReportServiceImpl 应该使用 `@Transactional(readOnly = true)` 因为它是纯读操作。

### 已有 Mapper/Utility 参考模式

```java
// ReviewResultMapper.java 模式（Story 5.1）
@Slf4j
public final class ReviewResultMapper {
    private ReviewResultMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    // static methods...
}
```

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── result/
          └── ReviewReportDTO.java              ← 新增

backend/ai-code-review-service/src/main/java/com/aicodereview/service/
  ├── ReviewReportService.java                  ← 新增（接口）
  └── impl/
      └── ReviewReportServiceImpl.java          ← 新增
```

**测试文件**：
```
backend/ai-code-review-service/src/test/java/com/aicodereview/service/
  └── impl/
      └── ReviewReportServiceImplTest.java      ← 新增
```

**不需要修改的已有文件**：
- ReviewResultService.java — 已有 getResultByTaskId()
- ReviewResultDTO.java — 已有所有需要的字段
- ReviewStatisticsDTO.java — 已有 total, bySeverity, byCategory
- ReviewIssue.java — 已有 severity, category, filePath, line, message, suggestion
- ReviewMetadata.java — 已有 providerId, model, promptTokens, completionTokens, durationMs, degradationEvents

### 集成点

- **上游**：`ReviewResultService.getResultByTaskId()` → 获取持久化的审查结果
- **上游**：`ReviewTaskRepository.findById()` → 获取任务元数据（项目名、分支、作者）
- **下游**：Story 5.3（查询 API）将调用 `ReviewReportService.generateReport()` 返回 JSON
- **下游**：Epic 7（通知系统）将调用 `renderMarkdown()` 和 `renderHtml()` 生成通知内容

### Story 5.1 的 Code Review 教训

以下问题在 Story 5.1 代码审查中被发现并修复，Story 5.2 应避免重复：
1. **toDTO 方法应放在 Mapper/Utility 中**，不要放在 Service 的 private 方法里
2. **不要暴露可变的共享对象**（如 ObjectMapper 实例的 getter）
3. **包含所有 enum 值**在 Map 中（空列表而非缺失 key），保持 JSON 输出一致性
4. **使用 LinkedHashMap** 保持 key 的稳定顺序
5. **DTO 应包含 updatedAt 等审计字段**，与现有模式保持一致

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 5 Stories - Story 5.2]
- [Source: _bmad-output/planning-artifacts/architecture.md#服务层 - ReportService]
- [Source: _bmad-output/implementation-artifacts/5-1-review-result-persistence-storage.md — Story 5.1 完整实现]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewResultService.java — 数据源接口]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java — Service 模式]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewResultDTO.java — 输入 DTO]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewStatisticsDTO.java — 统计 DTO]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewIssue.java — 问题 DTO]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/IssueSeverity.java — 严重性枚举（含 getScore()）]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/IssueCategory.java — 类别枚举（含 getDisplayName()）]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java — 任务实体（含 project, branch, author）]
- [Source: _bmad-output/implementation-artifacts/epic-4-retro-2026-02-15.md — CallGraph 移除决策]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- DateTimeFormatter pattern `"yyyy-MM-dd HH:mm:ss UTC"` failed with `Unknown pattern letter: U` — fixed by quoting literal: `"yyyy-MM-dd HH:mm:ss 'UTC'"`
- Surefire 2.22.2 test class filter (`-Dtest="ReviewReportServiceImplTest"`) finds 0 tests with nested classes — ran full module tests instead
- Flyway V8 checksum mismatch after Story 5.1 code review removed redundant index — fixed via `UPDATE flyway_schema_history SET checksum` on dev database

### Completion Notes List

- All 318 tests pass (130 common + 14 repository + 174 service)
- 12 new unit tests: 7 generateReport, 2 renderMarkdown, 3 renderHtml (including XSS escaping)
- No new dependencies added; StringBuilder-based rendering (no template engine)
- HTML uses inline CSS for email compatibility; severity color mapping matches AC5

### Change Log

| Change | File | Description |
|--------|------|-------------|
| ADD | ReviewReportDTO.java | Structured report DTO with grouped issues |
| ADD | ReviewReportService.java | Interface: generateReport, renderMarkdown, renderHtml |
| ADD | ReviewReportServiceImpl.java | Full implementation with grouping, sorting, Markdown/HTML rendering |
| ADD | ReviewReportServiceImplTest.java | 12 unit tests across 3 nested test classes |

### File List

**New files (4):**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/result/ReviewReportDTO.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewReportService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewReportServiceImpl.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewReportServiceImplTest.java`

**Modified files (0):**
- None (pure additive story)
