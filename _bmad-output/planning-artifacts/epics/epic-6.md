# Epic 6: 质量阈值与 PR/MR 拦截

**用户价值**：团队可以配置代码质量阈值，系统自动拦截不符合质量标准的 PR/MR，强制执行代码质量门禁。

**用户成果**：
- 配置项目级质量阈值（Critical > N, High > M, 总问题数 > X）
- 执行阈值验证逻辑
- 更新 PR/MR 状态（GitHub Check Runs、GitLab Commit Status）
- 超阈值时阻止合并并添加失败标签
- 提供阈值通过/失败详情

**覆盖的功能需求**：FR 1.6（阈值拦截与 PR/MR 状态更新）
**覆盖的非功能需求**：NFR 2（可靠性）
**覆盖的附加需求**：集成要求（Git 平台 API）、数据流要求

---

## Stories


### Story 6.1: 实现阈值配置管理

**用户故事**：
作为系统管理员，
我想要为项目配置质量阈值，
以便定义代码合并的质量门禁。

**验收标准**：

**Given** 审查结果查询 API 已实现
**When** 配置质量阈值
**Then** 在 `project` 表添加 `thresholds` JSONB 列：
```json
{
  "enabled": true,
  "rules": [
    {"severity": "CRITICAL", "max_count": 0},
    {"severity": "HIGH", "max_count": 3},
    {"total_issues": 20}
  ],
  "action": "BLOCK_MERGE"
}
```

**And** 更新 ProjectController API：
- PUT /api/v1/projects/{id}/thresholds（更新阈值配置）
- GET /api/v1/projects/{id}/thresholds（获取阈值配置）

**And** 阈值配置验证（max_count >= 0）
**And** 阈值配置缓存到 Redis
**And** 编写单元测试验证配置逻辑

---

### Story 6.2: 实现阈值验证引擎

**用户故事**：
作为系统，
我想要根据阈值配置验证审查结果，
以便判断是否应该拦截 PR/MR。

**验收标准**：

**Given** 阈值配置已实现
**When** 审查完成后验证阈值
**Then** 创建 ThresholdValidator 服务：
- validate(ReviewResult result, ThresholdConfig config): ValidationResult
- ValidationResult 包含：passed、violations、action

**And** 验证逻辑：
- 检查 CRITICAL 问题数 <= max_count
- 检查 HIGH 问题数 <= max_count
- 检查总问题数 <= total_issues

**And** 任何规则违反时 passed = false
**And** 记录所有违反的规则到 violations
**And** 根据配置返回 action（BLOCK_MERGE/WARN_ONLY）
**And** 编写单元测试覆盖各种场景

---

### Story 6.3: 实现 GitHub Check Runs 状态更新

**用户故事**：
作为系统，
我想要更新 GitHub PR 的 Check Run 状态，
以便在 PR 页面显示审查结果。

**验收标准**：

**Given** 阈值验证已实现
**When** 审查完成且项目为 GitHub
**Then** 创建 GitHubCheckRunService：
- createCheckRun(taskId, repoUrl, commitHash): CheckRun
- updateCheckRun(checkRunId, conclusion, output): void

**And** 使用 GitHub API：
- POST /repos/{owner}/{repo}/check-runs
- PATCH /repos/{owner}/{repo}/check-runs/{check_run_id}

**And** Check Run 参数：
- name: "AI Code Review"
- status: "in_progress" → "completed"
- conclusion: "success" / "failure" / "neutral"
- output: title, summary, text（审查摘要）

**And** 阈值通过时 conclusion = "success"
**And** 阈值失败时 conclusion = "failure"
**And** output 包含问题统计和严重问题列表
**And** 编写单元测试使用 Mock GitHub API

---

### Story 6.4: 实现 GitLab Commit Status 和 AWS CodeCommit 状态更新

**用户故事**：
作为系统，
我想要更新 GitLab MR 和 AWS CodeCommit PR 的状态，
以便在各平台显示审查结果。

**验收标准**：

**Given** GitHub Check Runs 已实现
**When** 审查完成且项目为 GitLab 或 CodeCommit
**Then** 创建 GitLabCommitStatusService：
- updateCommitStatus(projectId, commitSha, state, description): void
- 使用 GitLab API：
  - POST /api/v4/projects/{id}/statuses/{sha}
  - state: "success" / "failed" / "pending"

**And** 创建 AWSCodeCommitStatusService：
- updatePullRequestApprovalState(prId, revisionId, approvalState): void
- 使用 AWS SDK：
  - codecommit.updatePullRequestApprovalState()

**And** 阈值通过时设置成功状态
**And** 阈值失败时设置失败状态
**And** 状态描述包含问题统计
**And** 编写单元测试使用 Mock API

---

