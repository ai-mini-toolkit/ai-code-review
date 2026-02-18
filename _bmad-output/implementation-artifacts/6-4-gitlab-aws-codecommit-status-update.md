# Story 6.4: 实现 GitLab Commit Status 和 AWS CodeCommit 状态更新

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a 系统,
I want 在审查完成后更新 GitLab MR 的 Commit Status 和 AWS CodeCommit PR 的审批状态,
so that 审查结果显示在各平台的 MR/PR 页面，阈值失败时提醒或阻止合并。

## Acceptance Criteria (BDD)

### AC1: 创建 GitLabCommitStatusService 接口和实现
**Given** Story 6.3 已实现 GitHub Check Runs 状态更新
**When** 审查完成且项目平台为 GitLab
**Then** 创建 `GitLabCommitStatusService`：
- `updateCommitStatus(repoUrl, commitHash, statistics, thresholdResult)` → `CommitStatusResponseDTO`
- 放置在 integration 模块（与 GitLabApiClient 同级）
- 使用现有 `HttpClient` bean 和 `PRIVATE-TOKEN` 认证模式

### AC2: GitLab Commit Status API 调用
**Given** GitLab Access Token 已配置（`${git.platform.gitlab.token}`）
**When** 调用 updateCommitStatus
**Then** 发送 POST 请求到 `{baseUrl}/api/v4/projects/{projectId}/statuses/{sha}`：
- Headers: `Content-Type: application/json`, `PRIVATE-TOKEN: {token}`
- Body 包含: state, name="AI Code Review", description（审查摘要）, target_url（可选）
- `{projectId}` 使用 URL-encoded 的 namespace/project 路径
- `{baseUrl}` 可配置（`${git.platform.gitlab.base-url:https://gitlab.com}`）

### AC3: State 映射逻辑
**Given** 阈值验证结果
**When** 确定 Commit Status state
**Then** 映射规则：
- `thresholdResult.passed = true` → state = `"success"`
- `thresholdResult.passed = false` AND `action = "BLOCK_MERGE"` → state = `"failed"`
- `thresholdResult.passed = false` AND `action = "WARN_ONLY"` → state = `"success"` (配合 description 提供警告信息)

### AC4: Commit Status description 内容
**Given** 审查统计信息和阈值结果
**When** 构建 description
**Then** 描述格式：
- 通过时: "AI Code Review passed — {total} issues found"
- 失败时: "AI Code Review failed — {violations} threshold violations"
- 注意: GitLab description 最大 255 字符，超过需截断

### AC5: 创建 AWSCodeCommitStatusService 存根实现
**Given** AWSCodeCommitClient 当前为存根实现
**When** 审查完成且项目平台为 AWS CodeCommit
**Then** 创建 `AWSCodeCommitStatusService` 存根：
- 方法签名与 GitLabCommitStatusService 对称
- 实现仅记录 WARN 日志: "AWS CodeCommit status update not yet implemented"
- 返回 null（与 GitHubCheckRunService token 为空时的行为一致）

### AC6: 扩展 ReviewResultServiceImpl 平台状态更新
**Given** saveResult() 流程中 step 9 仅处理 GitHub
**When** 审查完成且项目平台为 GitLab 或 AWS CodeCommit
**Then** 扩展 step 9 的平台检测逻辑：
- 使用 `GitPlatform.fromRepoUrl()` 或 `project.getGitPlatform()` 判断平台
- GitHub → 调用 `GitHubCheckRunService`（已有）
- GitLab → 调用 `GitLabCommitStatusService`
- AWS_CODECOMMIT → 调用 `AWSCodeCommitStatusService`（存根）
- 每个平台调用均包裹在独立 try-catch 中，失败不阻塞返回

### AC7: 错误处理与降级
**Given** GitLab API 调用可能失败
**When** API 返回错误或超时
**Then** 使用 try-catch 捕获异常
**And** 记录 WARN 级别日志（包含 repoUrl、platform、错误详情）
**And** 不影响审查结果的保存和返回

### AC8: 单元测试
**Given** 实现完成
**When** 运行测试
**Then** 验证：
- GitLabCommitStatusService: state 映射（success/failed）、description 构建、API 请求体、错误处理、空 token 跳过
- AWSCodeCommitStatusService: 存根行为（返回 null、记录日志）
- ReviewResultServiceImpl 扩展: GitLab 平台调用 mock、AWS 平台调用 mock、平台检测逻辑

## Tasks / Subtasks

- [x] Task 1: 创建 GitLab Commit Status 相关 DTO (AC: #1, #4)
  - [x] 1.1 创建 `CommitStatusResponseDTO`（id, state, description）在 common 模块 `dto/checkrun/` 包
- [x] Task 2: 创建 GitLabCommitStatusService 接口和实现 (AC: #1, #2, #3, #4, #7)
  - [x] 2.1 在 integration 模块创建 `GitLabCommitStatusService` 接口
  - [x] 2.2 实现 `GitLabCommitStatusServiceImpl`（HTTP 调用、state 映射、description 构建）
  - [x] 2.3 复用 `GitLabApiClient` 的 `parseProjectPath()` 模式（或提取为 GitLabUrlUtils）
  - [x] 2.4 实现 description 内容构建（255 字符限制截断）
- [x] Task 3: 创建 AWSCodeCommitStatusService 存根 (AC: #5)
  - [x] 3.1 在 integration 模块创建 `AWSCodeCommitStatusService` 接口
  - [x] 3.2 实现 `AWSCodeCommitStatusServiceImpl` 存根（WARN 日志 + 返回 null）
- [x] Task 4: 扩展 ReviewResultServiceImpl 平台状态更新 (AC: #6)
  - [x] 4.1 注入 GitLabCommitStatusService 和 AWSCodeCommitStatusService
  - [x] 4.2 重构 step 9 为多平台 switch/if-else 逻辑
  - [x] 4.3 每个平台调用独立 try-catch
- [x] Task 5: 编写单元测试 (AC: #8)
  - [x] 5.1 GitLabCommitStatusServiceImpl 单元测试（state 映射、description 构建、API 调用、错误处理）
  - [x] 5.2 AWSCodeCommitStatusServiceImpl 单元测试（存根行为验证）
  - [x] 5.3 ReviewResultServiceImpl 扩展测试（GitLab/AWS 平台 mock 测试）

## Dev Notes

### 架构模式与约束

- **模块放置**：
  - DTO → `ai-code-review-common` 模块 `dto/checkrun/` 包（复用 Story 6.3 已有的包路径）
  - Service 接口+实现 → `ai-code-review-integration` 模块 `git/` 包（与 GitLabApiClient、GitHubCheckRunService 同级）
  - 集成逻辑 → `ai-code-review-service` 模块 ReviewResultServiceImpl（扩展 step 9）
- **不创建新 Controller**：此 Story 为后端集成，无新 API 端点
- **不创建新 Flyway 迁移**：不需要数据库变更
- **不引入新依赖**：GitLab 使用现有 `java.net.http.HttpClient`，AWS 为存根不需要 AWS SDK

### 关键技术细节

1. **-parameters 编译器标志未启用**（关键！）：
   - **必须** `@Value("${git.platform.gitlab.token:}")` 带显式 value
   - **必须** `@Value("${git.platform.gitlab.base-url:https://gitlab.com}")` 带显式 value
   - 构造器注入不受影响

2. **GitLab 认证模式（与 GitHub 不同！）**：
   ```java
   // GitLabApiClient 已有的模式（PRIVATE-TOKEN，不是 Bearer）
   // 参考: GitLabApiClient.java:176-178
   if (accessToken != null && !accessToken.isEmpty()) {
       builder.header("PRIVATE-TOKEN", accessToken);
   }
   ```
   **关键差异**: GitHub 用 `Authorization: Bearer {token}`，GitLab 用 `PRIVATE-TOKEN: {token}`

3. **GitLab Base URL 可配置（支持自托管）**：
   ```java
   // 参考: GitLabApiClient.java:38
   @Value("${git.platform.gitlab.base-url:https://gitlab.com}") String baseUrl
   // 末尾斜杠处理: baseUrl.endsWith("/") ? baseUrl.substring(0, ...) : baseUrl
   ```

4. **GitLab Commit Status API 请求**：
   ```
   POST {baseUrl}/api/v4/projects/{projectId}/statuses/{sha}
   Headers:
     Content-Type: application/json
     PRIVATE-TOKEN: {token}
   Body:
     {
       "state": "success|failed|pending",
       "name": "AI Code Review",
       "description": "AI Code Review passed — 3 issues found",
       "target_url": ""
     }
   ```
   **注意**: `{projectId}` 是 URL-encoded 的项目路径（如 `owner%2Frepo`），与 GitLabApiClient.parseProjectPath() 一致

5. **GitLab State 映射（与 GitHub conclusion 不同！）**：
   ```java
   String mapState(ThresholdValidationResultDTO thresholdResult) {
       if (thresholdResult.isPassed()) {
           return "success";
       }
       if ("BLOCK_MERGE".equals(thresholdResult.getAction())) {
           return "failed";  // GitLab 用 "failed"，GitHub 用 "failure"
       }
       return "success";  // WARN_ONLY 对应 success + 警告 description
   }
   ```
   **关键差异**: GitLab 无 "neutral" state。WARN_ONLY 时使用 `state=success` + description 包含警告信息

6. **GitLab description 限制**：
   - 最大 255 字符（远小于 GitHub summary 的 65535）
   - 需要简洁的单行描述，不支持 Markdown 富文本
   - 截断模式: `description.substring(0, 252) + "..."`

7. **GitLab parseProjectPath() 已存在但为包级方法**：
   ```java
   // GitLabApiClient.java:88-110 — parseProjectPath() 是包级可见
   // 返回 URL-encoded 的项目路径
   // 选项A: 直接调用（同包可见）
   // 选项B: 提取为 GitLabUrlUtils 共享工具类（与 Story 6.3 的 GitHubUrlUtils 对称）
   ```
   **推荐**: 提取为 `GitLabUrlUtils.parseProjectPath()` 与 `GitHubUrlUtils.parseOwnerRepo()` 对称

8. **ReviewResultServiceImpl step 9 扩展伪代码**：
   ```java
   // 现有 step 9 (仅 GitHub):
   if ("GitHub".equalsIgnoreCase(task.getProject().getGitPlatform())
           && Boolean.TRUE.equals(reviewResult.isSuccess())) {
       try { gitHubCheckRunService.createCompletedCheckRun(...); }
       catch (Exception e) { log.warn(...); }
   }

   // 扩展为多平台:
   if (Boolean.TRUE.equals(reviewResult.isSuccess())) {
       String platform = task.getProject().getGitPlatform();
       if ("GitHub".equalsIgnoreCase(platform)) {
           try { gitHubCheckRunService.createCompletedCheckRun(...); }
           catch (Exception e) { log.warn("GitHub Check Run failed: {}", e.getMessage()); }
       } else if ("GitLab".equalsIgnoreCase(platform)) {
           try { gitLabCommitStatusService.updateCommitStatus(...); }
           catch (Exception e) { log.warn("GitLab Commit Status failed: {}", e.getMessage()); }
       } else if ("AWS_CODECOMMIT".equalsIgnoreCase(platform)) {
           try { awsCodeCommitStatusService.updateCommitStatus(...); }
           catch (Exception e) { log.warn("AWS CodeCommit status failed: {}", e.getMessage()); }
       }
   }
   ```

9. **GitLab Commit Status API 响应**：
   ```json
   {
     "id": 1234,
     "sha": "abc123",
     "status": "success",
     "name": "AI Code Review",
     "description": "AI Code Review passed — 3 issues found"
   }
   ```

10. **AWS CodeCommit 存根模式（参考 AWSCodeCommitClient）**：
    ```java
    // AWSCodeCommitClient 的存根模式:
    // - 所有方法抛出 UnsupportedOperationException
    // - 仅 getPlatform() 返回有效值
    //
    // AWSCodeCommitStatusService 存根采用更温和的方式:
    // - 记录 WARN 日志 + 返回 null（与 GitHubCheckRunService token 为空时行为一致）
    // - 不抛出异常（避免触发 ReviewResultServiceImpl 的 catch 块）
    ```

### 已建立的代码模式参考

| 模式 | 参考文件 | 关键点 |
|------|---------|--------|
| GitHub Status Service | `GitHubCheckRunService.java` / `GitHubCheckRunServiceImpl.java` | 方法签名模板、结论映射、输出构建 |
| GitLab HTTP 调用 | `GitLabApiClient.java` | PRIVATE-TOKEN 认证、base-url 配置、parseProjectPath() |
| GitHub URL 工具 | `GitHubUrlUtils.java` | 共享工具类模式（Story 6.3 code review 提取） |
| AWS 存根 | `AWSCodeCommitClient.java` | 存根实现模式 |
| DTO builder | `CheckRunResponseDTO.java` | @Data @Builder @NoArgsConstructor @AllArgsConstructor |
| 降级集成 | `ReviewResultServiceImpl.java:119-128` | try-catch + WARN 日志模式 |
| HttpClient bean | `GitClientConfig.java` | 共享 HttpClient 构造器注入 |

### 之前 Story 的 Code Review 教训

1. **Content-Type header**: HTTP POST 请求必须带 `Content-Type: application/json`（Story 6.3 M1 发现）
2. **Summary/Description 截断**: 外部 API 有长度限制，必须截断处理（Story 6.3 M2）
3. **URL 解析共享工具类**: 避免重复代码，提取为 `*UrlUtils` 工具类（Story 6.3 M3）
4. **InterruptedException 恢复中断标志**: `Thread.currentThread().interrupt()` 后才 return（Story 6.3 L1）
5. **`Boolean.TRUE.equals()`** 避免 NPE
6. **空 token 检查**: 在调用前检查 token 非空非 blank，跳过并记录 WARN

### 依赖的已实现类（直接使用，无需修改）

| 类 | 模块 | 用途 |
|---|------|------|
| `GitClientConfig` | integration/config | HttpClient bean |
| `GitLabApiClient` | integration/git | PRIVATE-TOKEN 认证模式参考、parseProjectPath() 逻辑参考 |
| `AWSCodeCommitClient` | integration/git | 存根实现模式参考 |
| `GitHubCheckRunService` / `Impl` | integration/git | Service 接口+实现模式参考 |
| `GitHubUrlUtils` | integration/git | URL 工具类模式参考 |
| `GitPlatform` | common/enums | 平台枚举、fromRepoUrl() 检测 |
| `CheckRunOutputDTO` / `CheckRunResponseDTO` | common/dto/checkrun | DTO 模式参考 |
| `ReviewStatisticsDTO` | common/dto/result | 统计信息 |
| `ThresholdValidationResultDTO` | common/dto/threshold | 阈值验证结果 |
| `ReviewResultServiceImpl` | service/impl | saveResult() step 9 集成点 |

### Project Structure Notes

**新增文件清单**（按模块）：

```
backend/ai-code-review-common/src/main/java/com/aicodereview/common/
  └── dto/
      └── checkrun/
          └── CommitStatusResponseDTO.java          ← 新增

backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/
  └── git/
      ├── GitLabCommitStatusService.java            ← 新增（接口）
      ├── GitLabCommitStatusServiceImpl.java        ← 新增
      ├── GitLabUrlUtils.java                       ← 新增（共享工具类）
      ├── AWSCodeCommitStatusService.java           ← 新增（接口）
      └── AWSCodeCommitStatusServiceImpl.java       ← 新增（存根）

backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/
  └── git/
      ├── GitLabCommitStatusServiceImplTest.java    ← 新增
      ├── GitLabUrlUtilsTest.java                   ← 新增（可选，若提取工具类）
      └── AWSCodeCommitStatusServiceImplTest.java   ← 新增
```

**修改文件清单**：
```
backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImpl.java                  ← 修改（扩展 step 9 多平台）

backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/
  └── ReviewResultServiceImplTest.java              ← 修改（添加 GitLab/AWS mock 测试）

backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/
  └── git/
      └── GitLabApiClient.java                      ← 修改（parseProjectPath 委托给 GitLabUrlUtils）
```

### 集成点

- **上游**：Story 6.3（GitHubCheckRunService → 同模式扩展到 GitLab/AWS）
- **上游**：Story 3.2（GitLabApiClient → HTTP 调用模式 + parseProjectPath()）
- **下游**：无（Epic 6 最后一个 Story）

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-6.md#Story 6.4]
- [Source: _bmad-output/implementation-artifacts/6-3-github-check-runs-status-update.md — Story 6.3 完整实现参考]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabApiClient.java — GitLab HTTP 调用+认证模式]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/AWSCodeCommitClient.java — AWS 存根模式]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubCheckRunServiceImpl.java — Service 实现参考]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubUrlUtils.java — URL 工具类模式]
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/config/GitClientConfig.java — HttpClient bean]
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java — saveResult() step 9 集成点]
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/GitPlatform.java — 平台枚举]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

无构建错误，所有测试一次通过。

### Completion Notes List

- `CommitStatusResponseDTO` 放入 `dto/checkrun/` 包与 `CheckRunResponseDTO` 并列
- `GitLabCommitStatusServiceImpl` 遵循 `GitHubCheckRunServiceImpl` 的相同模式：构造器注入 HttpClient + token + baseUrl
- GitLab state 映射: passed→success, BLOCK_MERGE→failed, WARN_ONLY→success（无 neutral 概念）
- 提取 `GitLabUrlUtils.parseProjectPath()` 共享工具类，`GitLabApiClient.parseProjectPath()` 委托调用
- `AWSCodeCommitStatusServiceImpl` 为温和存根（WARN 日志 + 返回 null），不抛异常
- `ReviewResultServiceImpl` step 9 从 GitHub-only 扩展为 if/else 多平台逻辑，每个平台独立 try-catch
- GitLab description 限制 255 字符，与 GitHub summary 65535 字符截然不同

### Test Count Summary

| 模块 | 测试数 | 变化 |
|------|--------|------|
| common | 130 | 不变 |
| repository | 14 | 不变 |
| integration | 216 | +23（GitLabCommitStatusServiceImplTest 21 + AWSCodeCommitStatusServiceImplTest 2） |
| service | 228 | +5（MultiPlatformStatusIntegration 5个测试） |
| api | 99 | 不变 |
| **总计** | **687** | **+28** |

### File List

**新增文件（7个）**:
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/checkrun/CommitStatusResponseDTO.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabCommitStatusService.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabCommitStatusServiceImpl.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabUrlUtils.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/AWSCodeCommitStatusService.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/AWSCodeCommitStatusServiceImpl.java`
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/GitLabCommitStatusServiceImplTest.java`
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/AWSCodeCommitStatusServiceImplTest.java`

**修改文件（3个）**:
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabApiClient.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewResultServiceImpl.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewResultServiceImplTest.java`

### Change Log

- 2026-02-17: Story 6.4 实现完成，所有 AC 满足，684 测试全部通过
- 2026-02-18: Code review 修复 — buildRequestBody 可见性提升+测试、描述截断测试修正、parseResponse 错误路径测试、重复 violationCount 提取、@Transactional HTTP 调用标记为技术债务
