# Epic 9: 端到端测试与集成验证

**Epic 描述**: 构建完整的端到端测试套件，验证从 Webhook 触发到审查报告生成的全流程，确保系统各模块集成正确、流程可靠。

**业务价值**: 提升系统质量，减少生产环境故障，提供回归测试保障。

**前置条件**: Epic 1-8 核心功能已完成

**Story 列表**:
- Story 9.1: E2E 测试框架搭建
- Story 9.2: Webhook 到审查流程 E2E 测试
- Story 9.3: 多平台集成 E2E 测试
- Story 9.4: AI 审查质量验证测试
- Story 9.5: Web 界面 E2E 测试
- Story 9.6: CI/CD 集成与回归测试
- Story 9.7: 错误场景与边界测试

---

### Story 9.1: E2E 测试框架搭建

**用户故事**:
作为 QA 工程师，
我想要建立完整的 E2E 测试框架，
以便自动化验证系统端到端功能。

**验收标准**:

**Given** 系统后端和前端已部署
**When** 搭建测试框架
**Then** 后端 E2E 测试框架：
- 使用 Spring Boot Test + TestContainers
- 启动真实 PostgreSQL 和 Redis 容器
- 配置测试数据初始化脚本

**And** 前端 E2E 测试框架：
- 使用 Playwright 或 Cypress
- 配置测试浏览器（Chromium, Firefox）
- 实现 Page Object Model 模式

**And** 创建测试工具类：
- MockWebhookServer（模拟 GitHub/GitLab/CodeCommit webhook）
- MockAIProvider（模拟 AI API 响应，避免真实 API 费用）
- TestDataFactory（生成测试数据：项目、配置、审查结果）
- AssertionHelper（自定义断言：数据库状态、队列状态）

**And** Docker Compose 测试环境：
```yaml
version: '3.8'
services:
  postgres-test:
    image: postgres:16
  redis-test:
    image: redis:7
  app-test:
    build: .
    environment:
      - SPRING_PROFILES_ACTIVE=test
```

**And** 编写示例测试用例验证框架可用

**技术要点**:
- TestContainers 自动启动/停止容器
- 测试数据库独立于开发环境
- 每个测试类使用独立数据库 Schema（隔离）

---

### Story 9.2: Webhook 到审查流程 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试从 Webhook 触发到审查完成的全流程，
以便验证核心业务流程正确性。

**验收标准**:

**Given** E2E 测试框架已搭建（Story 9.1）
**When** 执行核心流程测试
**Then** 创建测试用例：

**Test Case 1: GitHub Push 事件 → 审查完成**
```java
@Test
public void testGitHubPushToReviewComplete() {
    // 1. 创建项目配置
    Project project = createProject("test-repo", GitPlatform.GITHUB);

    // 2. 发送 GitHub Push webhook
    mockGitHubWebhook.sendPushEvent(project, "main", "abc123");

    // 3. 验证任务创建
    await().atMost(5, SECONDS).until(() ->
        taskRepository.findByCommitSha("abc123").isPresent()
    );

    ReviewTask task = taskRepository.findByCommitSha("abc123").get();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

    // 4. 等待任务处理（Worker 消费队列）
    await().atMost(60, SECONDS).until(() ->
        task.getStatus() == TaskStatus.COMPLETED
    );

    // 5. 验证审查结果
    ReviewResult result = resultRepository.findByTaskId(task.getId()).get();
    assertThat(result.getDimensions()).hasSize(6);
    assertThat(result.getTotalIssues()).isGreaterThan(0);

    // 6. 验证通知已发送
    verify(emailService, times(1)).sendReviewCompleteEmail(any());
}
```

**Test Case 2: GitLab MR 事件 → 审查 → 阈值拦截**
```java
@Test
public void testGitLabMRWithThresholdViolation() {
    // 1. 创建项目配置（设置严格阈值：Critical > 0 即拦截）
    Project project = createProject("test-repo", GitPlatform.GITLAB);
    project.setThresholdConfig(new ThresholdConfig(0, 0, 10));

    // 2. 发送 GitLab MR webhook（包含严重问题的代码）
    mockGitLabWebhook.sendMREvent(project, "feature-branch", codeWithSQLInjection);

    // 3. 等待审查完成
    await().atMost(60, SECONDS).until(() ->
        reviewIsComplete(taskId)
    );

    // 4. 验证阈值验证失败
    ThresholdValidation validation = validationRepository.findByTaskId(taskId).get();
    assertThat(validation.isPassed()).isFalse();
    assertThat(validation.getViolations()).contains("Critical: 1 > 0");

    // 5. 验证 GitLab MR 状态更新为失败
    verify(gitLabClient, times(1))
        .updateMRStatus(anyString(), eq(CommitStatus.FAILED), contains("阈值拦截"));
}
```

**Test Case 3: AWS CodeCommit PR → 完整流程**
- 测试 AWS SigV4 签名验证
- 测试 GetDifferences API 调用
- 验证审查完成

**And** 所有测试在隔离环境运行（使用 Mock AI 响应）
**And** 测试覆盖高优先级（PR/MR）和普通优先级（Push）
**And** 每个测试独立可重复运行

---

### Story 9.3: 多平台集成 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试所有 Git 平台集成的兼容性，
以便确保多平台支持无回归。

**验收标准**:

**Given** Webhook 流程测试已完成（Story 9.2）
**When** 测试多平台集成
**Then** 创建参数化测试：

```java
@ParameterizedTest
@EnumSource(GitPlatform.class)
public void testWebhookVerificationForAllPlatforms(GitPlatform platform) {
    // 测试每个平台的签名验证
    WebhookPayload payload = generateWebhookPayload(platform);

    ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
        "/api/v1/webhooks/" + platform.name().toLowerCase(),
        payload,
        WebhookResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().isSuccess()).isTrue();
}
```

**And** 测试每个平台的特定功能：
- GitHub: Check Runs API 状态更新
- GitLab: Commit Status API 更新
- AWS CodeCommit: Pull Request Comments

**And** 测试差异获取一致性：
- 验证所有平台返回统一的 DiffResult 格式
- 测试 Add/Modify/Delete 三种变更类型

**And** 测试通知集成：
- GitHub: PR Comment 格式正确
- GitLab: MR Note 格式正确
- AWS CodeCommit: PR Comment 使用 SDK API

**And** 边界测试：
- 测试无效签名（返回 401）
- 测试不支持的事件类型（忽略）
- 测试大型 Payload（> 1MB）

---

### Story 9.4: AI 审查质量验证测试

**用户故事**:
作为 AI 工程师，
我想要验证 AI 审查的准确性和一致性，
以便确保审查质量达标。

**验收标准**:

**Given** 多平台集成测试已完成（Story 9.3）
**When** 验证 AI 审查质量
**Then** 创建已知漏洞测试集：

**Test Dataset 1: 安全漏洞代码（10 个样本）**
```java
// 样本 1: SQL 注入
String query = "SELECT * FROM users WHERE id = " + userId;

// 样本 2: XSS 漏洞
response.getWriter().write("<div>" + userInput + "</div>");

// 样本 3: 硬编码密钥
String apiKey = "sk-1234567890abcdef";

// ... 7 个其他漏洞
```

**Expected Results**:
- 所有 10 个漏洞被检测到（Recall = 100%）
- Severity 正确标记为 "error"
- Category 正确分类（sql-injection, xss, sensitive-data）

**Test Dataset 2: 性能问题代码（10 个样本）**
- N+1 查询、O(n²) 算法、资源泄漏等
- Expected Recall ≥ 90%

**Test Dataset 3: 正常代码（无问题）**
- 验证误报率 ≤ 15%

**And** 实现自动化评分系统：
```java
@Test
public void testAIReviewPrecisionAndRecall() {
    List<TestCase> testCases = loadKnownVulnerabilities();

    int truePositives = 0;
    int falsePositives = 0;
    int falseNegatives = 0;

    for (TestCase testCase : testCases) {
        ReviewResult result = conductReview(testCase.getCode());
        List<Issue> detected = result.getIssues();

        // 比对检测结果与预期结果
        truePositives += countTruePositives(detected, testCase.getExpected());
        falsePositives += countFalsePositives(detected, testCase.getExpected());
        falseNegatives += countFalseNegatives(detected, testCase.getExpected());
    }

    double precision = (double) truePositives / (truePositives + falsePositives);
    double recall = (double) truePositives / (truePositives + falseNegatives);

    assertThat(precision).isGreaterThanOrEqualTo(0.85);  // ≥ 85%
    assertThat(recall).isGreaterThanOrEqualTo(0.90);     // ≥ 90%
}
```

**And** 测试 Prompt 模板质量：
- 测试每个维度的 Prompt 模板
- 验证输出 JSON 格式可解析（100%）

**And** 测试降级策略：
- 模拟主 AI 模型失败（返回 429）
- 验证自动切换到备用模型
- 验证最终返回有效结果

---

### Story 9.5: Web 界面 E2E 测试

**用户故事**:
作为 QA 工程师，
我想要测试 Web 界面的完整用户流程，
以便确保 UI 功能正常、交互流畅。

**验收标准**:

**Given** AI 审查质量测试已完成（Story 9.4）
**When** 测试 Web 界面
**Then** 使用 Playwright 创建测试：

**Test Case 1: 用户登录流程**
```typescript
test('user login and navigate to dashboard', async ({ page }) => {
  await page.goto('http://localhost:3000/login');

  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'password123');
  await page.click('button[type="submit"]');

  await expect(page).toHaveURL('/dashboard');
  await expect(page.locator('h1')).toContainText('Dashboard');
});
```

**Test Case 2: 创建项目流程**
```typescript
test('create new project', async ({ page }) => {
  await loginAs('admin', page);
  await page.click('button:has-text("新建项目")');

  await page.fill('input[name="name"]', 'Test Project');
  await page.selectOption('select[name="gitPlatform"]', 'GITHUB');
  await page.fill('input[name="repoUrl"]', 'https://github.com/user/repo');
  await page.click('button:has-text("保存")');

  await expect(page.locator('.message')).toContainText('项目创建成功');
});
```

**Test Case 3: 查看审查详情**
```typescript
test('view review details', async ({ page }) => {
  await loginAs('admin', page);
  await page.goto('/reviews');

  await page.click('tr:first-child a:has-text("查看详情")');

  await expect(page.locator('h2')).toContainText('审查详情');
  await expect(page.locator('.issue-card')).toHaveCount(5);
  await expect(page.locator('.call-graph-chart')).toBeVisible();
});
```

**Test Case 4: 配置 AI 模型**
- 测试添加 OpenAI 配置
- 测试 API 密钥加密存储
- 测试连接测试功能

**Test Case 5: 审查报告可视化**
- 测试问题列表加载
- 测试代码高亮显示
- 测试调用链路图渲染
- 测试统计图表交互

**And** 测试响应式布局（桌面、平板、手机）
**And** 测试深色/浅色主题切换
**And** 测试键盘导航（可访问性）

---

### Story 9.6: CI/CD 集成与回归测试

**用户故事**:
作为 DevOps 工程师，
我想要将 E2E 测试集成到 CI/CD 流程，
以便每次代码提交自动运行回归测试。

**验收标准**:

**Given** Web 界面测试已完成（Story 9.5）
**When** 集成 CI/CD
**Then** 创建 GitHub Actions 工作流：

```yaml
name: E2E Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  e2e-backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
      redis:
        image: redis:7

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Run Backend E2E Tests
        run: mvn test -Pintegration-test

      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        with:
          name: backend-test-reports
          path: target/surefire-reports

  e2e-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3

      - name: Install Playwright
        run: pnpm install && pnpm exec playwright install

      - name: Run Frontend E2E Tests
        run: pnpm run test:e2e

      - name: Upload Screenshots
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-screenshots
          path: test-results
```

**And** 配置测试报告：
- JUnit XML 格式（后端）
- Playwright HTML 报告（前端）
- 测试覆盖率报告（JaCoCo + Istanbul）

**And** 失败通知机制：
- CI 失败时发送 Slack 通知
- 包含失败测试用例列表和日志链接

**And** 性能监控：
- 记录测试执行时间趋势
- 测试套件总时间 < 10 分钟（目标）

---

### Story 9.7: 错误场景与边界测试

**用户故事**:
作为 QA 工程师，
我想要测试系统的错误处理和边界条件，
以便确保系统在异常情况下也能稳定运行。

**验收标准**:

**Given** CI/CD 集成已完成（Story 9.6）
**When** 测试错误场景
**Then** 创建错误场景测试：

**Test Case 1: 数据库连接失败**
```java
@Test
public void testDatabaseConnectionFailure() {
    // 停止数据库容器
    postgresContainer.stop();

    // 尝试创建项目
    ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
        "/api/v1/projects",
        projectRequest,
        ApiResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getError()).contains("Database connection failed");

    // 恢复数据库
    postgresContainer.start();
}
```

**Test Case 2: Redis 队列不可用**
- 模拟 Redis 宕机
- 验证任务创建失败并返回友好错误
- 验证系统不崩溃

**Test Case 3: AI API 限流**
- 模拟 AI API 返回 429 错误
- 验证重试机制触发（指数退避）
- 验证最终切换到备用模型

**Test Case 4: 超大代码变更（> 5000 行）**
- 测试系统能否处理大型 diff
- 验证性能降级策略（跳过调用图分析）

**Test Case 5: 恶意 Webhook Payload**
- 测试无效签名（返回 401）
- 测试超大 Payload（> 10MB，返回 413）
- 测试 SQL 注入尝试（参数化查询防御）

**Test Case 6: 并发冲突**
- 测试 100 个并发 Webhook 请求
- 验证队列深度不超过限制
- 验证无重复任务创建（分布式锁）

**And** 边界值测试：
- 空字符串、null 值、空数组
- 超长字符串（> 10000 字符）
- 特殊字符（SQL 注入、XSS）

**And** 性能边界测试：
- 1000 个项目配置（系统不卡顿）
- 10000 个历史审查记录（列表查询 < 1s）

**技术要点**:
- 使用 Chaos Engineering 工具（Toxiproxy）模拟网络故障
- 使用 WireMock 模拟外部 API 错误响应

---

