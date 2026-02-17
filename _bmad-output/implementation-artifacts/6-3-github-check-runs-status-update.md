# Story 6.3: 实现 GitHub Check Runs 状态更新

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 系统,
I want 在审查完成后更新 GitHub PR 的 Check Run 状态,
so that 审查结果直接显示在 PR 页面，阈值失败时自动阻止合并。

## Acceptance Criteria (BDD)

### AC1: 创建 GitHubCheckRunService 接口和实现
**Given** Story 6.2 已实现阈值验证引擎
**When** 审查完成且项目平台为 GitHub
**Then** 创建 `GitHubCheckRunService`：
- `createCompletedCheckRun(ReviewTask task, ReviewStatisticsDTO statistics, ThresholdValidationResultDTO thresholdResult)` → `CheckRunResponseDTO`
- 放置在 integration 模块（与 GitHubApiClient 同级）

### AC2: GitHub Check Runs API 调用
**Given** GitHub API Token 已配置（`${git.platform.github.token}`）
**When** 调用 createCompletedCheckRun
**Then** 发送 POST 请求到 `https://api.github.com/repos/{owner}/{repo}/check-runs`：
- Headers: `Accept: application/vnd.github+json`, `Authorization: Bearer {token}`
- Body 包含: name, head_sha, status="completed", conclusion, completed_at, output
- 使用现有 `HttpClient` bean（来自 `GitClientConfig`）
- 复用 `parseOwnerRepo()` 逻辑提取 owner/repo

### AC3: Conclusion 映射逻辑
**Given** 阈值验证结果
**When** 确定 Check Run conclusion
**Then** 映射规则：
- `thresholdResult.passed = true` → conclusion = `"success"`
- `thresholdResult.passed = false` AND `action = "BLOCK_MERGE"` → conclusion = `"failure"`
- `thresholdResult.passed = false` AND `action = "WARN_ONLY"` → conclusion = `"neutral"`
- 阈值未启用（thresholdResult 为 passed=true）→ conclusion = `"success"`

### AC4: Check Run Output 内容
**Given** 审查统计信息和阈值结果
**When** 构建 Check Run output
**Then** output 结构包含：
- `title`: "AI Code Review — {passed|failed}"
- `summary`: Markdown 格式摘要（总问题数、各严重级别统计、违规列表）
- `text`: 详细审查信息（可选，超过限制时截断）

### AC5: 集成到 ReviewResultServiceImpl
**Given** saveResult() 流程中 task 状态更新为 COMPLETED 之后
**When** 项目 gitPlatform = "GitHub" 且审查成功（success=true）
**Then** 异步或同步调用 GitHubCheckRunService
**And** Check Run 创建失败不应阻塞 saveResult 返回（降级处理）
**And** 记录 WARN 日志但不抛出异常

### AC6: 错误处理与降级
**Given** GitHub API 调用可能失败
**When** API 返回错误或超时
**Then** 使用 try-catch 捕获异常
**And** 记录 WARN 级别日志（包含 taskId、repoUrl、错误详情）
**And** 不影响审查结果的保存和返回

### AC7: 单元测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- conclusion 映射逻辑（success/failure/neutral）
- output 内容构建（title、summary）
- API 请求体构建正确性
- 错误处理（API 失败不传播异常）
- ReviewResultServiceImpl 集成（mock GitHubCheckRunService）

## Tasks / Subtasks

- [x] Task 1: 创建 Check Run 相关 DTO (AC: #1, #4)
  - [x] 1.1 创建 `CheckRunOutputDTO`（title, summary, text）在 common 模块
  - [x] 1.2 创建 `CheckRunResponseDTO`（id, htmlUrl, conclusion）在 common 模块
- [x] Task 2: 创建 GitHubCheckRunService 接口和实现 (AC: #1, #2, #3, #4)
  - [x] 2.1 在 integration 模块创建 `GitHubCheckRunService` 接口
  - [x] 2.2 实现 `GitHubCheckRunServiceImpl`（HTTP 调用、conclusion 映射、output 构建）
  - [x] 2.3 复用 `GitHubApiClient` 的 `parseOwnerRepo()` 模式和 HttpClient bean
  - [x] 2.4 实现 output 内容 Markdown 格式化（统计摘要、违规详情）
- [x] Task 3: 集成到 ReviewResultServiceImpl (AC: #5, #6)
  - [x] 3.1 注入 GitHubCheckRunService（构造器注入）
  - [x] 3.2 在 saveResult() 的 task status 更新后调用 Check Run 创建
  - [x] 3.3 实现降级处理：try-catch 包裹，失败时仅记录 WARN 日志
  - [x] 3.4 条件检查：仅 gitPlatform="GitHub" 且 success=true 时调用
- [x] Task 4: 编写单元测试 (AC: #7)
  - [x] 4.1 GitHubCheckRunServiceImpl 单元测试（14个测试：conclusion 映射、output 构建、API 调用、错误处理）
  - [x] 4.2 ReviewResultServiceImpl 集成测试（4个测试：mock GitHubCheckRunService、平台检查、降级处理）

## Dev Notes

### 架构模式与约束

- **模块放置**：
  - DTO → `ai-code-review-common` 模块 `dto/checkrun/` 包
  - Service 接口+实现 → `ai-code-review-integration` 模块 `git/` 包（与 GitHubApiClient 同级）
  - 集成逻辑 → `ai-code-review-service` 模块 ReviewResultServiceImpl
- **不创建新 Controller**：此 Story 为后端集成，无新 API 端点
- **不创建新 Flyway 迁移**：不需要数据库变更
- **不引入新依赖**：使用现有 `java.net.http.HttpClient`

### 关键技术细节

1. **-parameters 编译器标志未启用**（关键！）：
   - **必须** `@Cacheable(key = "#p0")` 用位置参数
   - 构造器注入不受影响

2. **现有 GitHubApiClient HTTP 模式（必须复用！）**：
   ```java
   // GitHubApiClient 已有的模式（在 integration/git/GitHubApiClient.java）
   @Value("${git.platform.github.token:}")
   private String accessToken;

   private final HttpClient httpClient;  // 从 GitClientConfig bean 注入

   // 请求构建模式:
   HttpRequest request = HttpRequest.newBuilder()
       .uri(URI.create(url))
       .header("Accept", "application/vnd.github+json")
       .header("Authorization", "Bearer " + accessToken)
       .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
       .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
       .build();

   HttpResponse<String> response = httpClient.send(
       request, HttpResponse.BodyHandlers.ofString());
   ```

3. **owner/repo 解析（已有方法，可参考但需要在 Check Run Service 中独立实现或提取到工具类）**：
   ```java
   // GitHubApiClient.parseOwnerRepo() 是 private 方法
   // 模式: https://github.com/{owner}/{repo}.git → String[]{owner, repo}
   // 需要在 GitHubCheckRunServiceImpl 中实现相同逻辑
   ```

4. **Check Run API 请求体**：
   ```json
   {
     "name": "AI Code Review",
     "head_sha": "commitHash",
     "status": "completed",
     "conclusion": "success|failure|neutral",
     "completed_at": "2026-02-17T12:00:00Z",
     "output": {
       "title": "AI Code Review — Passed",
       "summary": "### Review Summary\n- Total Issues: 5\n- CRITICAL: 0\n...",
       "text": ""
     }
   }
   ```
   **注意**: 使用单次 POST 创建已完成的 Check Run（不需要先创建再更新）。

5. **Conclusion 映射伪代码**：
   ```java
   private String mapConclusion(ThresholdValidationResultDTO thresholdResult) {
       if (thresholdResult.isPassed()) {
           return "success";
       }
       if ("BLOCK_MERGE".equals(thresholdResult.getAction())) {
           return "failure";
       }
       return "neutral";  // WARN_ONLY
   }
   ```

6. **Output Summary 格式（Markdown）**：
   ```markdown
   ### Review Summary
   **Total Issues:** 5

   | Severity | Count |
   |----------|-------|
   | CRITICAL | 1 |
   | HIGH | 2 |
   | MEDIUM | 1 |
   | LOW | 1 |

   ### Threshold Violations
   - CRITICAL <= 0 (actual: 1)
   - HIGH <= 3 (actual: 2) ✅

   **Action:** BLOCK_MERGE
   ```

7. **降级集成到 ReviewResultServiceImpl.saveResult()**：
   ```java
   // 在 step 8 (task status → COMPLETED) 之后:
   // 9. Platform status update (non-blocking)
   try {
       if ("GitHub".equalsIgnoreCase(task.getProject().getGitPlatform())
               && Boolean.TRUE.equals(reviewResult.isSuccess())) {
           gitHubCheckRunService.createCompletedCheckRun(task, statistics, thresholdResult);
       }
   } catch (Exception e) {
       log.warn("Failed to create GitHub Check Run for task {}: {}", taskId, e.getMessage());
   }
   ```
   **关键**: 使用 `equalsIgnoreCase` 因为 `gitPlatform` 值来自 webhook 控制器，可能大小写不一致。

8. **GitHubCheckRunService 不应该是 GitPlatformClient 的一部分**：
   - `GitPlatformClient` 接口专注于代码获取（getFileContent, getDiff）
   - Check Run 是独立功能，创建新的 Service 接口
   - 不修改现有的 `GitPlatformClient` 接口

9. **Token 配置**：
   - 使用与 `GitHubApiClient` 相同的 `@Value("${git.platform.github.token:}")` 注入
   - 全局 token（不是 per-project token）
   - 如果 token 为空，应跳过 Check Run 创建并记录 WARN

10. **GitHub API 限制**：
    - Check Runs API 需要 `checks:write` 权限
    - API 版本头：`X-GitHub-Api-Version: 2022-11-28`（可选但推荐）
    - output.summary 最大 65535 字符
    - output.text 最大 65535 字符

### 已建立的代码模式参考

| 模式 | 参考文件 | 关键点 |
|------|---------|--------|
| HTTP 调用 | `GitHubApiClient.java` | HttpClient, Bearer token, retry |
| Service 注入 | `ReviewResultServiceImpl.java` | 构造器注入, @Transactional |
| JSON 序列化 | `ThresholdMapper.java` | 静态 ObjectMapper |
| DTO builder | `ThresholdValidationResultDTO.java` | @Data @Builder @NoArgsConstructor @AllArgsConstructor |
| 平台检测 | `GitPlatform.java` | enum, fromRepoUrl() |
| 平台工厂 | `GitPlatformClientFactory.java` | Map<GitPlatform, Client> |
| 降级处理 | `ReviewOrchestrator.java` | try-catch + WARN 日志 |

### 之前 Story 的 Code Review 教训

1. **`-parameters` 未启用**：所有注解必须带显式 value 属性
2. **`Boolean.TRUE.equals()`** 避免 NPE
3. **`@Builder.Default`** 防止集合字段为 null
4. **Mapper 使用静态方法**，不放 Service 的 private 方法里
5. **降级处理**：外部 API 调用必须有 try-catch，不能影响核心流程
6. **空 token 检查**：在调用前检查 token 非空非 blank

### 依赖的已实现类（直接使用，无需修改）

| 类 | 模块 | 用途 |
|---|------|------|
| `GitClientConfig` | integration/config | HttpClient bean |
| `GitHubApiClient` | integration/git | 参考 HTTP 调用模式 |
| `GitPlatform` | common/enums | 平台枚举 |
| `ReviewTask` | repository/entity | commitHash, repoUrl, project 字段 |
| `Project` | repository/entity | gitPlatform 字段 |
| `ReviewStatisticsDTO` | common/dto/result | 统计信息 |
| `ThresholdValidationResultDTO` | common/dto/threshold | 阈值验证结果 |
| `ThresholdViolationDTO` | common/dto/threshold | 违规详情 |
| `ReviewResultServiceImpl` | service/impl | saveResult() 集成点 |

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── checkrun/
          ├── CheckRunOutputDTO.java          ← 新增
          └── CheckRunResponseDTO.java        ← 新增

backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/
  └── git/
      ├── GitHubCheckRunService.java          ← 新增（接口）
      └── GitHubCheckRunServiceImpl.java      ← 新增

backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/
  └── git/
      └── GitHubCheckRunServiceImplTest.java  ← 新增
```

**修改文件清单**：
```
backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImpl.java            ← 修改（添加 Check Run 集成）

backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImplTest.java        ← 修改（添加 Check Run mock 测试）
```

### 集成点

- **上游**：Story 6.2（ThresholdValidationResultDTO → conclusion 映射）
- **上游**：Story 3.2（GitHubApiClient → HTTP 调用模式参考）
- **下游**：Story 6.4（GitLab/AWS CodeCommit 将遵循相同模式）

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-6.md#Story 6.3]
- [Source: _bmad-output/implementation-artifacts/6-2-threshold-validation-engine.md — Story 6.2 实现]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubApiClient.java — HTTP 调用模式]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/config/GitClientConfig.java — HttpClient bean]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java — saveResult() 集成点]
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java — 任务字段]
- [Source: _bmad-output/planning-artifacts/architecture.md#FR 1.6 — 阈值拦截与 PR/MR 状态更新]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

无构建错误，所有测试一次通过。

### Completion Notes List

- 使用构造器注入代替 `@Autowired(required=false)`，保持与项目现有模式一致
- `createCompletedCheckRun` 方法签名使用 `(repoUrl, commitHash, statistics, thresholdResult)` 而非 `(ReviewTask task, ...)` 以避免 integration 模块依赖 repository 模块
- `parseOwnerRepo()` 返回 `"owner/repo"` 字符串而非 `String[]`，简化 API URL 构建
- Check Run output 中仅显示 count > 0 的严重级别（过滤零计数行）

### Test Count Summary

| 模块 | 测试数 | 变化 |
|------|--------|------|
| common | 43 | 不变 |
| repository | 14 | 不变 |
| integration | 193 | +16（GitHubCheckRunServiceImplTest 16个测试） |
| service | 223 | +4（GitHubCheckRunIntegration 嵌套类） |
| api | 71 | 不变 |
| **总计** | **544+** | **+20** |

### File List

**新增文件（6个）**:
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/checkrun/CheckRunOutputDTO.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/checkrun/CheckRunResponseDTO.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubCheckRunService.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubCheckRunServiceImpl.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubUrlUtils.java`
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/GitHubCheckRunServiceImplTest.java`

**修改文件（2个）**:
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java`

### Change Log

- 2026-02-17: Story 6.3 实现完成，所有 AC 满足，544+ 测试全部通过
- 2026-02-17: Code review 修复 — 添加 Content-Type 头、summary 截断、提取 GitHubUrlUtils 共享工具类、InterruptedException 测试
