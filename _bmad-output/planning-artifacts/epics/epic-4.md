# Epic 4: AI 智能审查引擎

**用户价值**：系统使用多种 AI 提供商（OpenAI、Anthropic、自定义 OpenAPI）对代码进行六维度智能审查，识别安全漏洞、性能问题、可维护性问题等。

**用户成果**：
- 实现 AI 提供商抽象层（策略模式 + 工厂模式）
- 集成 OpenAI、Anthropic Claude 和自定义 OpenAPI 提供商
- 执行六维度代码分析（安全性、性能、可维护性、正确性、代码风格、最佳实践）
- 实现降级策略（主模型 → 备用模型 → 错误）
- 输出结构化审查结果（JSON 格式：severity, category, line, message, suggestion）

**覆盖的功能需求**：FR 1.4（AI 智能审查）
**覆盖的非功能需求**：NFR 1（性能）、NFR 2（可靠性）、NFR 3（安全性）
**覆盖的附加需求**：架构模式（策略模式）、集成要求（AI 提供商）

---

## Stories


### Story 4.1: 实现 AI 提供商抽象层（策略模式）

**用户故事**：
作为系统架构师，
我想要实现 AI 提供商的抽象层，
以便支持多个 AI 提供商和灵活的路由策略。

**验收标准**：

**Given** 代码上下文提取服务已实现
**When** 设计 AI 提供商抽象
**Then** 创建 AIProvider 接口：
```java
interface AIProvider {
    ReviewResult analyze(CodeContext context, PromptTemplate template);
    boolean isAvailable();
    String getProviderId();
    int getMaxTokens();
}
```

**And** 创建 AIProviderFactory 工厂类：
- getProvider(String providerId): AIProvider
- registerProvider(AIProvider provider)

**And** 创建 ReviewResult 类：
- List<Issue> issues
- Map<String, Object> metadata（模型、耗时、token 数）
- Issue 包含：severity、category、line、message、suggestion

**And** 编写单元测试验证工厂模式
**And** 文档说明如何添加新提供商

---

### Story 4.2: 实现 OpenAI 提供商集成

**用户故事**：
作为系统，
我想要集成 OpenAI API 进行代码审查，
以便使用 GPT 模型分析代码。

**验收标准**：

**Given** AI 提供商抽象层已实现
**When** 实现 OpenAI 提供商
**Then** 创建 OpenAIProvider 实现 AIProvider
**And** 集成 OpenAI Java SDK 或 HTTP 客户端
**And** 从 AIModelConfig 加载配置：
- API Key、Model Name（如 gpt-4）
- Max Tokens、Temperature、Timeout

**And** 构建 ChatCompletion 请求：
- System Prompt：审查维度说明
- User Prompt：代码上下文 + 变更 Diff
- 使用 Prompt Template 渲染

**And** 解析 JSON 响应提取问题列表
**And** 映射到 ReviewResult 格式
**And** 处理 API 错误：
- 限流（429）：抛出 RateLimitException
- 超时：抛出 TimeoutException
- 认证错误（401）：抛出 AuthenticationException

**And** 记录 API 调用指标（耗时、token 数）
**And** 编写单元测试使用 Mock API 响应

---

### Story 4.3: 实现 Anthropic Claude 提供商集成

**用户故事**：
作为系统，
我想要集成 Anthropic Claude API 进行代码审查，
以便使用 Claude 模型分析代码。

**验收标准**：

**Given** OpenAI 提供商已实现
**When** 实现 Anthropic 提供商
**Then** 创建 AnthropicProvider 实现 AIProvider
**And** 集成 Anthropic Java SDK 或 HTTP 客户端
**And** 从 AIModelConfig 加载配置：
- API Key、Model Name（如 claude-sonnet-4）
- Max Tokens、Temperature、Timeout

**And** 构建 Messages API 请求：
- System Prompt：审查维度说明
- User Message：代码上下文 + 变更 Diff

**And** 解析 JSON 响应提取问题列表
**And** 映射到 ReviewResult 格式
**And** 处理 API 错误（同 OpenAI）
**And** 记录 API 调用指标
**And** 编写单元测试使用 Mock API 响应

---

### Story 4.4: 实现自定义 OpenAPI 提供商集成

**用户故事**：
作为系统管理员，
我想要集成符合 OpenAPI 标准的自定义 AI 提供商，
以便支持私有部署或其他兼容模型。

**验收标准**：

**Given** OpenAI 和 Anthropic 提供商已实现
**When** 实现自定义 OpenAPI 提供商
**Then** 创建 CustomOpenAPIProvider 实现 AIProvider
**And** 从 AIModelConfig 加载配置：
- API Endpoint（自定义 URL）
- API Key、Model Name
- OpenAPI Spec 路径（可选，用于验证）

**And** 支持 OpenAI 兼容的 API 格式：
- POST {endpoint}/v1/chat/completions
- Request Body：messages、model、max_tokens、temperature

**And** 解析 JSON 响应（OpenAI 格式）
**And** 映射到 ReviewResult 格式
**And** 验证 API Spec（如提供）
**And** 处理 API 错误
**And** 编写单元测试使用本地 Mock 服务器

---

### Story 4.5: 实现六维度审查编排与降级策略

**用户故事**：
作为系统，
我想要编排六维度审查流程并实现降级策略，
以便可靠地完成代码审查。

**验收标准**：

**Given** 所有 AI 提供商已实现
**When** 执行代码审查
**Then** 创建 ReviewOrchestrator 服务：
- review(ReviewTask task): ReviewResult
- 六维度：security、performance、maintainability、correctness、style、best_practices

**And** 从项目配置加载审查维度配置
**And** 为每个维度：
- 选择 AI 模型（从 AI 模型配置）
- 加载 Prompt 模板（从模板管理）
- 调用 AI Provider 分析
- 聚合结果到 ReviewResult

**And** 实现六维度并发执行策略（详细规范见 architecture.md）：
- 使用 Semaphore 限制最大并发数 = 3（避免 AI API 限流）
- 使用 ExecutorService 固定线程池（6 个线程，每个维度一个）
- 使用 CompletableFuture 并行执行所有维度
- 单个维度超时时间: 45 秒
- 总审查超时时间: 60 秒
- 任意维度失败不阻塞其他维度（错误隔离）

**And** 实现完整降级策略（详细规范见 architecture.md）：

**Level 0: 主 AI 模型（GPT-4 / Claude Opus）**
- 429 Rate Limit → 指数退避重试（1s, 2s, 4s, 8s，最多 4 次）
- 503 Service Unavailable → 指数退避重试（1s, 2s, 4s，最多 3 次）
- Timeout (> 30s) → 立即重试 1 次
- 401 Authentication Failed → 不重试，立即告警管理员，标记失败
- 所有重试失败 → 降级到 Level 1

**Level 1: 备用 AI 模型（GPT-3.5 / Claude Sonnet）**
- 相同的重试策略
- 所有重试失败 → 降级到 Level 2

**Level 2: 简化审查（快速模型 + 3 维度）**
- 仅审查 Security、Bugs、Performance 三个维度
- 使用 GPT-3.5 Turbo（最快模型）
- 失败 → 降级到 Level 3

**Level 3: 静态分析（无 AI）**
- 使用规则引擎进行基础检查（null pointer, SQL injection patterns）
- 返回结果并附加警告："AI 审查不可用，仅显示静态分析结果"
- 失败 → Level 4

**Level 4: 完全失败**
- 标记任务为 FAILED
- 记录详细错误日志
- 发送告警通知管理员

**And** 实现错误分类和处理：
```java
if (exception instanceof RateLimitException) {
    // 触发指数退避重试（@Retryable 自动处理）
    log.warn("Rate limit hit for task {}", task.getId());
} else if (exception instanceof AuthenticationException) {
    // 立即失败，发送告警
    alertService.sendCriticalAlert("AI Authentication Failed", exception);
    return ReviewResult.failed(task.getId(), "Authentication error");
} else if (exception instanceof QuotaExceededException) {
    // 切换到备用模型（触发 Level 1 降级）
    log.error("AI quota exceeded, switching to secondary model");
} else if (exception instanceof TimeoutException) {
    // 单次重试后降级
    log.warn("AI request timeout for task {}", task.getId());
}
```

**And** 记录每个维度的审查耗时和降级事件
**And** 性能验收标准（详细见 architecture.md）：
- 100 行代码：总审查时间 < 10 秒（6 维度并行）
- 500 行代码：总审查时间 < 20 秒
- 1000 行代码：总审查时间 < 30 秒（NFR 要求）
- 并发控制：同时运行的 AI 请求 ≤ 18（6 维度 × 3 并发）

**And** 监控指标（Micrometer）：
- `review.dimension.latency` - 每个维度的审查耗时
- `review.total.latency` - 总审查时间
- `review.concurrency.available` - 可用 Semaphore 许可数
- `review.dimension.failure_rate` - 维度失败率
- `ai.degradation.rate` - AI 降级率（Level 1+）

**And** 编写集成测试模拟各种场景：
- 测试正常场景（所有维度成功）
- 测试单个维度失败（其他维度继续）
- 测试主模型 429 错误（触发重试和降级）
- 测试主模型 401 错误（立即失败并告警）
- 测试超时场景（60 秒后返回部分结果）
- 测试并发控制（验证最多 3 个并发 AI 调用）

**And** 测试并发审查多个维度（验证性能提升）：
- 串行执行 6 维度：预期 ~180s（6 × 30s）
- 并行执行 6 维度：预期 ~30s（max of 6 parallel calls）
- 验证实际并行加速比 ≥ 5x

---

