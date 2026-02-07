# Epic 2: Webhook 集成与任务队列

**用户价值**：Git 平台（GitHub、GitLab、AWS CodeCommit）可以通过 Webhook 触发代码审查任务，系统能够安全地接收、验证并可靠地排队处理这些任务。

**用户成果**：
- 接收并验证来自三个 Git 平台的 Webhook 请求
- 创建审查任务并分配优先级（PR/MR 高优先级，Push 普通优先级）
- 通过 Redis 优先级队列管理任务
- 实现任务重试机制和超时处理

**覆盖的功能需求**：FR 1.1（Webhook 接收与验证）、FR 1.2（任务管理）
**覆盖的非功能需求**：NFR 1（性能）、NFR 2（可靠性）、NFR 3（安全性）
**覆盖的附加需求**：架构模式（责任链、消息队列）、集成要求（Git 平台）

---

## Stories

### Story 2.1: 实现 Webhook 验证抽象层（责任链模式）

**用户故事**：
作为系统架构师，
我想要实现 Webhook 签名验证的抽象层，
以便支持多个 Git 平台的不同验证机制。

**验收标准**：

**Given** 项目基础设施已完成
**When** 实现 Webhook 验证抽象
**Then** 创建 WebhookVerifier 接口：
```java
interface WebhookVerifier {
    boolean verify(String payload, String signature, String secret);
    String getPlatform();
}
```

**And** 创建 WebhookVerificationChain 责任链管理器
**And** 实现常量时间字符串比较工具（防御时序攻击）
**And** 编写单元测试验证责任链模式
**And** 文档说明如何添加新的验证器实现

---

### Story 2.2: 实现 GitHub Webhook 签名验证

**用户故事**：
作为系统，
我想要验证来自 GitHub 的 Webhook 签名，
以便确保请求的真实性和完整性。

**验收标准**：

**Given** Webhook 验证抽象层已实现
**When** 实现 GitHub 验证器
**Then** 创建 GitHubWebhookVerifier 实现 WebhookVerifier
**And** 使用 HMAC-SHA256 算法验证签名
**And** 从 X-Hub-Signature-256 header 提取签名
**And** 使用 webhook_secret 作为 HMAC 密钥
**And** 比较计算的签名与请求签名（常量时间）
**And** 编写单元测试使用 GitHub 示例数据
**And** 签名不匹配时抛出 WebhookVerificationException

---

### Story 2.3: 实现 GitLab 和 AWS CodeCommit Webhook 验证

**用户故事**：
作为系统，
我想要验证来自 GitLab 和 AWS CodeCommit 的 Webhook 签名，
以便支持多个 Git 平台。

**验收标准**：

**Given** GitHub 验证器已实现
**When** 实现 GitLab 和 CodeCommit 验证器
**Then** 创建 GitLabWebhookVerifier：
- 从 X-Gitlab-Token header 提取 Secret Token
- 使用简单字符串比较验证（常量时间）

**And** 创建 AWSCodeCommitWebhookVerifier：
- 实现 AWS Signature Version 4 验证
- 从 Authorization header 解析签名
- 验证签名和时间戳

**And** 注册所有验证器到责任链
**And** 编写单元测试覆盖所有平台
**And** 文档说明各平台的签名机制

---

### Story 2.4: 实现 Webhook 接收控制器

**用户故事**：
作为 Git 平台，
我想要通过 Webhook 发送 Push、PR/MR 事件到系统，
以便触发代码审查任务。

**验收标准**：

**Given** Webhook 验证器已实现
**When** 实现 Webhook 控制器
**Then** 创建 WebhookController：
- POST /api/v1/webhooks/github
- POST /api/v1/webhooks/gitlab
- POST /api/v1/webhooks/codecommit

**And** 签名验证在 payload 解析之前执行
**And** 解析事件类型（Push、Pull Request、Merge Request）
**And** 提取关键信息：
- 仓库 URL、分支、提交哈希
- PR/MR 编号、标题、描述
- 作者信息、文件变更列表

**And** 根据项目配置判断是否启用审查
**And** Webhook 响应时间 < 500ms（NFR 1）
**And** 返回 202 Accepted（异步处理）
**And** 签名验证失败返回 401 Unauthorized
**And** 编写集成测试模拟各平台 Webhook

---

### Story 2.5: 实现审查任务创建与持久化

**用户故事**：
作为系统，
我想要创建审查任务并持久化到数据库，
以便跟踪任务状态和历史记录。

**验收标准**：

**Given** Webhook 控制器已实现
**When** 创建审查任务
**Then** 创建 `review_task` 表：
- id、project_id（外键）、task_type（PUSH/PR/MR）
- repo_url、branch、commit_hash、pr_number
- author、status（PENDING/RUNNING/COMPLETED/FAILED）
- priority（HIGH/NORMAL）、retry_count、max_retries
- created_at、started_at、completed_at、updated_at

**And** 实现 ReviewTaskService.createTask() 方法
**And** 实现 ReviewTaskRepository JPA 仓库
**And** PR/MR 任务优先级设为 HIGH，Push 任务设为 NORMAL
**And** 任务初始状态为 PENDING
**And** max_retries 默认为 3
**And** 创建任务后立即入队（调用队列服务）
**And** 编写单元测试验证任务创建逻辑

---

### Story 2.6: 实现 Redis 优先级队列管理

**用户故事**：
作为系统，
我想要使用 Redis 实现优先级队列，
以便高优先级任务（PR/MR）优先处理。

**验收标准**：

**Given** Redis 已配置
**When** 实现队列管理服务
**Then** 创建 QueueService：
- enqueue(taskId, priority)：入队任务
- dequeue()：出队任务（按优先级）
- requeueWithDelay(taskId, delaySeconds)：延迟重新入队

**And** 使用 Redis Sorted Set 实现优先级队列：
- Key: review:queue
- Score: priority (HIGH=100, NORMAL=50) + timestamp
- Value: task_id

**And** 使用 Redis String 记录任务处理状态：
- Key: review:task:{task_id}:lock
- Value: worker_id
- TTL: 5 分钟（任务超时）

**And** dequeue() 使用分布式锁防止重复处理
**And** 编写单元测试验证队列操作
**And** 编写集成测试验证多实例并发场景

---

### Story 2.7: 实现任务重试机制

**用户故事**：
作为系统，
我想要实现任务失败重试机制，
以便提高系统可靠性和审查成功率。

**验收标准**：

**Given** 队列服务已实现
**When** 任务处理失败
**Then** 判断失败类型：
- AI API 限流错误：指数退避重试
- 网络错误：立即重试
- 验证错误：不重试，标记失败

**And** 重试延迟计算：2^retry_count 秒（1s、2s、4s）
**And** 添加随机抖动（0-500ms）防止雷鸣群效应
**And** 更新任务 retry_count 字段
**And** retry_count >= max_retries 时标记任务 FAILED
**And** 重试任务重新入队（使用 requeueWithDelay）
**And** 记录每次重试的错误信息（日志）
**And** 编写单元测试模拟各种失败场景

---
