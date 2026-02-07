# Epic 10: 性能测试与优化

**Epic 描述**: 对系统进行全面的性能测试，验证 NFR 性能要求，识别性能瓶颈并优化，确保系统可在生产环境稳定运行。

**业务价值**: 确保系统满足性能 SLA，提升用户体验，降低资源成本。

**前置条件**: Epic 1-8 核心功能已完成

**Story 列表**:
- Story 10.1: 性能测试基准数据集准备
- Story 10.2: 单次审查性能测试
- Story 10.3: 并发任务性能测试
- Story 10.4: Webhook 响应时间测试
- Story 10.5: 数据库查询性能优化
- Story 10.6: API 响应时间优化
- Story 10.7: 前端加载性能优化

---

### Story 10.1: 性能测试基准数据集准备

**用户故事**:
作为性能工程师，
我想要准备标准化的性能测试数据集，
以便可重复、可对比地进行性能测试。

**验收标准**:

**Given** 系统功能已完成
**When** 准备测试数据
**Then** 创建代码规模测试集：

**Dataset 1: 小型变更（100 行）**
- 文件数量: 1-2
- 变更类型: 单个方法修改
- 编程语言: Java
- 审查维度: 全部 6 个
- 预期审查时间: < 10s

**Dataset 2: 中型变更（500 行）**
- 文件数量: 5-8
- 变更类型: 功能模块开发
- 编程语言: Java
- 预期审查时间: < 20s

**Dataset 3: 大型变更（1000 行）**
- 文件数量: 10-15
- 变更类型: 大型重构
- 预期审查时间: < 30s（NFR 要求）

**Dataset 4: 超大变更（5000 行）**
- 文件数量: 30-50
- 变更类型: 整个模块重写
- 预期审查时间: < 60s（降级策略）

**And** 创建复杂度测试集：
- **简单代码**: CRUD 操作，直线逻辑，无复杂调用
- **中等复杂**: Service 层业务逻辑，3-5 层调用深度
- **高复杂**: 复杂算法，10+ 层调用深度，设计模式

**And** 创建问题密度测试集：
- **无问题代码**: 高质量代码，预期 0-2 个 Info 级别建议
- **少量问题**: 5-10 个 Warning
- **大量问题**: 20+ 个问题（包括 Critical/High）

**And** 数据集存储在 Git 仓库：
```
performance-test-data/
├── small/
│   ├── 100-lines-crud.diff
│   ├── 100-lines-service.diff
│   └── expected-issues.json
├── medium/
│   └── ...
├── large/
│   └── ...
└── xlarge/
    └── ...
```

**And** 创建数据加载脚本自动导入

---

### Story 10.2: 单次审查性能测试

**用户故事**:
作为性能工程师，
我想要测试单次审查的端到端性能，
以便验证 NFR 要求"单次审查 < 30s per 100 lines"。

**验收标准**:

**Given** 性能测试数据集已准备（Story 10.1）
**When** 执行单次审查性能测试
**Then** 使用 JMeter 或 Gatling 创建测试脚本：

**Test Scenario 1: 100 行代码审查**
```scala
scenario("Single Review - 100 lines")
  .exec(http("Trigger Webhook")
    .post("/api/v1/webhooks/github")
    .body(StringBody(webhook100Lines))
    .header("X-Hub-Signature-256", signature)
    .check(status.is(202))
  )
  .pause(1)
  .exec(http("Wait for Completion")
    .get("/api/v1/tasks/${taskId}")
    .check(jsonPath("$.status").is("COMPLETED"))
    .check(responseTimeInMillis.lte(10000))  // < 10s
  )
```

**Performance Targets (P95)**:
| Code Size | Total Time | Database | AI API | Call Graph | Pass? |
|-----------|------------|----------|--------|------------|-------|
| 100 lines | < 10s | < 500ms | < 8s | < 1s | ✅ Target |
| 500 lines | < 20s | < 1s | < 15s | < 3s | ✅ Target |
| 1000 lines | < 30s | < 1.5s | < 23s | < 5s | ✅ NFR Met |
| 5000 lines | < 60s | < 3s | < 50s | < 7s (or skip) | ⚠️ Degraded |

**And** 测量各阶段耗时占比：
```
Total Review Time (100 lines): 10s
├── Webhook Processing: 0.5s (5%)
├── Diff Retrieval: 1s (10%)
├── Code Parsing: 0.5s (5%)
├── Call Graph Analysis: 1s (10%)
├── AI Review (6 dimensions): 6s (60%)
│   ├── Security: 1s
│   ├── Performance: 1s
│   ├── Maintainability: 1s
│   ├── Correctness: 1s
│   ├── Style: 1s
│   └── Best Practices: 1s
└── Report Generation: 1s (10%)
```

**Key Insights**:
- AI API 调用占 60% 时间（主要瓶颈）
- 六维度并行执行（3 并发）可将 AI 时间从 18s 降至 6s

**And** 测试降级策略性能：
- 模拟主 AI 模型超时
- 验证切换到备用模型后总时间 < 40s

**And** 生成性能报告：
- 响应时间分布图（p50, p95, p99）
- 各阶段耗时饼图
- 瓶颈识别和优化建议

---

### Story 10.3: 并发任务性能测试

**用户故事**:
作为性能工程师，
我想要测试系统的并发处理能力，
以便验证 NFR 要求"并发任务 ≥ 10"。

**验收标准**:

**Given** 单次审查性能测试已完成（Story 10.2）
**When** 执行并发性能测试
**Then** 使用 JMeter 创建并发测试计划：

**Test Scenario 1: 10 并发任务（NFR 要求）**
```xml
<ThreadGroup>
  <numThreads>10</numThreads>
  <rampUpPeriod>5</rampUpPeriod>  <!-- 5秒内启动10个线程 -->
  <loopCount>1</loopCount>
</ThreadGroup>
```

**Performance Targets**:
| Concurrency | Queue Depth | Throughput | Avg Response Time | p95 Response Time | Error Rate |
|-------------|-------------|------------|-------------------|-------------------|------------|
| 10 tasks | < 10 | ≥ 10 tasks/min | < 35s | < 40s | < 1% |
| 50 tasks | < 50 | ≥ 10 tasks/min | < 60s | < 120s | < 5% |
| 100 tasks | < 100 | ≥ 10 tasks/min | < 180s | < 300s | < 10% |

**And** 测试 Redis 队列性能：
- 监控队列深度变化（每秒采样）
- 监控入队/出队延迟（p95 < 100ms）
- 验证无任务丢失（入队数 = 出队数 + 队列中数）

**And** 测试 Worker 线程池利用率：
- 10 个 Worker 线程应保持 90%+ 利用率
- 无线程饥饿或死锁

**And** 测试数据库连接池：
- 连接池大小: 20（HikariCP 默认）
- 监控连接获取等待时间（< 50ms）
- 验证无连接泄漏

**And** 测试 AI API 限流保护：
- 配置 Semaphore（max 3 concurrent per dimension）
- 验证并发 AI 调用不超过 18（6 dimensions × 3）
- 验证无 429 Rate Limit 错误

**And** 负载测试场景：
```
Phase 1: Ramp-up (0-5 min)
- 从 0 增加到 50 并发任务

Phase 2: Steady State (5-15 min)
- 保持 50 并发任务

Phase 3: Peak Load (15-20 min)
- 增加到 100 并发任务

Phase 4: Cool Down (20-25 min)
- 降至 10 并发任务
```

**And** 生成性能报告：
- 吞吐量时间序列图
- 响应时间百分位数图
- 队列深度热力图
- 资源使用率（CPU, Memory, Disk I/O）

---

### Story 10.4: Webhook 响应时间测试

**用户故事**:
作为性能工程师，
我想要测试 Webhook 端点的响应时间，
以便验证 NFR 要求"Webhook 响应 < 1s"。

**验收标准**:

**Given** 并发任务测试已完成（Story 10.3）
**When** 测试 Webhook 响应时间
**Then** 创建专项测试：

**Test Scenario 1: Webhook 端点响应时间（1000 请求）**
```bash
# 使用 Apache Bench
ab -n 1000 -c 10 -p webhook-payload.json \
   -T application/json \
   -H "X-Hub-Signature-256: sha256=..." \
   http://localhost:8080/api/v1/webhooks/github
```

**Performance Targets (NFR)**:
| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| p50 Response Time | < 500ms | TBD | - |
| p95 Response Time | < 1000ms | TBD | - |
| p99 Response Time | < 1500ms | TBD | - |
| Throughput | ≥ 50 req/s | TBD | - |
| Error Rate | < 1% | TBD | - |

**And** 测试各阶段耗时：
```
Webhook Request Processing (target < 1s):
├── Signature Verification: < 50ms
├── Payload Parsing: < 100ms
├── Task Creation: < 200ms
├── Queue Enqueue: < 50ms
└── Response Generation: < 50ms
```

**And** 优化建议（如不达标）：
1. **Signature Verification 优化**:
   - 缓存 Webhook Secret（避免每次从数据库读取）
   - 使用高效的 HMAC 库（Apache Commons Codec）

2. **Database Write 优化**:
   - 使用批量插入（Batch Insert）
   - 异步写入（先返回 202，后台持久化）

3. **Queue Enqueue 优化**:
   - Redis 管道（Pipeline）批量操作
   - 减少网络往返次数

**And** 压力测试：
- 测试 1000 req/s 高并发（模拟 DDoS）
- 验证系统不崩溃，返回 429 限流响应

---

### Story 10.5: 数据库查询性能优化

**用户故事**:
作为性能工程师，
我想要优化数据库查询性能，
以便确保 API 响应时间符合 NFR。

**验收标准**:

**Given** Webhook 响应时间测试已完成（Story 10.4）
**When** 优化数据库查询
**Then** 使用 pg_stat_statements 分析慢查询：

**Slow Query Identification**:
```sql
-- 查找平均执行时间 > 100ms 的查询
SELECT
  query,
  calls,
  mean_exec_time,
  max_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC
LIMIT 20;
```

**Common Slow Queries to Optimize**:

**Query 1: 审查历史列表（无索引）**
```sql
-- BEFORE (slow: ~500ms for 10k records)
SELECT * FROM review_tasks
WHERE project_id = ?
ORDER BY created_at DESC
LIMIT 20;

-- AFTER: Add index
CREATE INDEX idx_review_tasks_project_created
ON review_tasks(project_id, created_at DESC);

-- Result: 500ms → 50ms (10x improvement)
```

**Query 2: 问题统计聚合（全表扫描）**
```sql
-- BEFORE (slow: ~1s for 100k issues)
SELECT
  severity,
  COUNT(*) as count
FROM review_issues
WHERE task_id = ?
GROUP BY severity;

-- AFTER: Add covering index
CREATE INDEX idx_review_issues_task_severity
ON review_issues(task_id, severity);

-- Result: 1s → 100ms (10x improvement)
```

**Query 3: 项目配置查询（JOIN 优化）**
```sql
-- BEFORE: Multiple queries (N+1)
SELECT * FROM projects WHERE id = ?;
SELECT * FROM ai_model_configs WHERE project_id = ?;
SELECT * FROM threshold_configs WHERE project_id = ?;

-- AFTER: Single query with JOIN
SELECT p.*, amc.*, tc.*
FROM projects p
LEFT JOIN ai_model_configs amc ON p.id = amc.project_id
LEFT JOIN threshold_configs tc ON p.id = tc.project_id
WHERE p.id = ?;

-- Result: 3 queries (150ms) → 1 query (50ms)
```

**And** 创建所有必要的索引：
```sql
-- review_tasks 表索引
CREATE INDEX idx_review_tasks_status ON review_tasks(status);
CREATE INDEX idx_review_tasks_commit_sha ON review_tasks(commit_sha);

-- review_results 表索引
CREATE INDEX idx_review_results_task ON review_results(task_id);

-- review_issues 表索引
CREATE INDEX idx_review_issues_result ON review_issues(result_id);
CREATE INDEX idx_review_issues_severity ON review_issues(severity);

-- projects 表索引
CREATE INDEX idx_projects_enabled ON projects(enabled);
```

**And** 配置数据库连接池优化：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**And** 使用 EXPLAIN ANALYZE 验证查询计划：
```sql
EXPLAIN ANALYZE
SELECT * FROM review_tasks
WHERE project_id = 1
ORDER BY created_at DESC
LIMIT 20;

-- Expected: Index Scan on idx_review_tasks_project_created
-- Avoid: Seq Scan (full table scan)
```

**And** 性能测试验证：
- 所有 API 列表查询 p95 < 1s ✅
- 详情查询 p95 < 500ms ✅
- 复杂聚合查询 p95 < 2s ✅

---

### Story 10.6: API 响应时间优化

**用户故事**:
作为性能工程师，
我想要优化 REST API 响应时间，
以便提升用户体验。

**验收标准**:

**Given** 数据库查询已优化（Story 10.5）
**When** 优化 API 响应时间
**Then** 实现缓存策略：

**Cache Strategy 1: Redis 缓存项目配置**
```java
@Service
public class ProjectService {

    @Cacheable(value = "projects", key = "#id", unless = "#result == null")
    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    @CacheEvict(value = "projects", key = "#project.id")
    public Project updateProject(Project project) {
        return projectRepository.save(project);
    }
}
```

**Cache Configuration**:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 minutes
      cache-null-values: false
```

**Expected Improvement**:
- Without cache: 50ms (database query)
- With cache: 5ms (Redis GET)
- 10x speedup for frequently accessed projects

**Cache Strategy 2: ETag for Conditional Requests**
```java
@GetMapping("/api/v1/tasks/{id}")
public ResponseEntity<ReviewTask> getTask(@PathVariable Long id,
                                          @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    ReviewTask task = taskService.getById(id);
    String etag = generateETag(task);

    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    return ResponseEntity.ok()
        .eTag(etag)
        .body(task);
}
```

**Expected Improvement**:
- 304 Not Modified response: < 10ms (no payload transfer)
- 200 OK with data: ~100ms
- Saves bandwidth for unchanged resources

**And** 实现 API 分页优化：
```java
// BEFORE: 加载所有数据到内存
List<ReviewTask> tasks = repository.findAll();  // 10k records = 50MB

// AFTER: 分页查询
Page<ReviewTask> tasks = repository.findAll(PageRequest.of(page, size));  // 20 records = 100KB
```

**And** 实现 GZIP 压缩：
```yaml
server:
  compression:
    enabled: true
    min-response-size: 1024  # 1KB
    mime-types: application/json,application/xml,text/html,text/plain
```

**Expected Improvement**:
- JSON response size: 100KB → 20KB (5x reduction)
- Transfer time (1 Mbps): 800ms → 160ms

**And** 异步 API 优化（长时间操作）：
```java
// BEFORE: 同步审查（阻塞 30s）
@PostMapping("/api/v1/reviews/start")
public ReviewResult startReview(@RequestBody ReviewRequest request) {
    return reviewService.conductReview(request);  // 30s blocking
}

// AFTER: 异步审查（立即返回）
@PostMapping("/api/v1/reviews/start")
public ResponseEntity<ReviewTaskResponse> startReview(@RequestBody ReviewRequest request) {
    ReviewTask task = reviewService.createTask(request);  // 100ms
    reviewService.conductReviewAsync(task);  // 异步执行

    return ResponseEntity.accepted()
        .body(new ReviewTaskResponse(task.getId(), "PENDING"));
}
```

**And** 性能测试验证：
- API 列表查询 p95: < 1s ✅（NFR）
- API 详情查询 p95: < 500ms ✅（NFR）
- 缓存命中率: > 80%

---

### Story 10.7: 前端加载性能优化

**用户故事**:
作为前端工程师，
我想要优化前端加载和渲染性能，
以便提升用户体验。

**验收标准**:

**Given** API 响应时间已优化（Story 10.6）
**When** 优化前端性能
**Then** 使用 Lighthouse 测量基准性能：

**Baseline Metrics (Before Optimization)**:
| Metric | Target | Baseline | Pass? |
|--------|--------|----------|-------|
| First Contentful Paint (FCP) | < 1.8s | TBD | - |
| Largest Contentful Paint (LCP) | < 2.5s | TBD | - |
| Time to Interactive (TTI) | < 3.8s | TBD | - |
| Cumulative Layout Shift (CLS) | < 0.1 | TBD | - |
| Total Blocking Time (TBT) | < 200ms | TBD | - |

**Optimization 1: Code Splitting and Lazy Loading**
```typescript
// BEFORE: 全部组件打包到 main.js (2MB)
import ReviewList from './views/ReviewList.vue';
import ReviewDetail from './views/ReviewDetail.vue';

// AFTER: 路由级别代码分割
const routes = [
  {
    path: '/reviews',
    component: () => import('./views/ReviewList.vue')  // reviews.js (200KB)
  },
  {
    path: '/reviews/:id',
    component: () => import('./views/ReviewDetail.vue')  // review-detail.js (300KB)
  }
];
```

**Expected Improvement**:
- Initial bundle size: 2MB → 500KB (4x reduction)
- First load time: 3s → 1s

**Optimization 2: Virtual Scrolling for Long Lists**
```vue
<!-- BEFORE: 渲染 1000 个问题（卡顿） -->
<div v-for="issue in issues" :key="issue.id">
  <IssueCard :issue="issue" />
</div>

<!-- AFTER: 虚拟滚动（仅渲染可见区域） -->
<virtual-scroller
  :items="issues"
  :item-height="100"
  :buffer="200"
>
  <template #default="{ item }">
    <IssueCard :issue="item" />
  </template>
</virtual-scroller>
```

**Expected Improvement**:
- 1000 issues rendering time: 5s → 0.2s (25x improvement)
- Memory usage: 500MB → 50MB

**Optimization 3: Image and Asset Optimization**
```typescript
// 使用 Vite 图片优化
import logo from './assets/logo.png?width=200&format=webp';

// 懒加载图片
<img src="..." loading="lazy" />
```

**Optimization 4: API Request Batching**
```typescript
// BEFORE: 串行请求（3 × 200ms = 600ms）
const project = await api.getProject(id);
const aiConfig = await api.getAIConfig(id);
const thresholds = await api.getThresholds(id);

// AFTER: 并行请求（max 200ms）
const [project, aiConfig, thresholds] = await Promise.all([
  api.getProject(id),
  api.getAIConfig(id),
  api.getThresholds(id)
]);
```

**Optimization 5: Caching and Service Worker**
```typescript
// 配置 Vite PWA 插件
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/api\.example\.com\/api\/v1\/projects/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'api-cache',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 300  // 5 minutes
              }
            }
          }
        ]
      }
    })
  ]
});
```

**And** 性能测试验证（Lighthouse）：
- Performance Score: > 90 ✅
- Accessibility Score: > 95 ✅
- Best Practices Score: > 90 ✅
- SEO Score: > 90 ✅

**And** 真实用户监控（RUM）：
- 集成 Google Analytics 4 或 Sentry Performance
- 监控 Core Web Vitals（FCP, LCP, CLS, FID, TTFB）
- 设置性能预算告警

---

### Story 1.9: 系统监控与告警

**用户故事**:
作为 DevOps 工程师，
我想要建立完整的监控和告警系统，
以便及时发现和处理生产问题。

**验收标准**:

**Given** 系统已部署到生产环境
**When** 配置监控与告警
**Then** 集成 Prometheus + Grafana：

**Prometheus Configuration**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'ai-code-review-backend'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'redis'
    static_configs:
      - targets: ['localhost:9121']

  - job_name: 'postgres'
    static_configs:
      - targets: ['localhost:9187']
```

**Spring Boot Actuator Configuration**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

**And** 定义关键监控指标：

**Application Metrics**:
- `http_server_requests_seconds`: HTTP 请求响应时间（p50, p95, p99）
- `review_dimension_latency_seconds`: 各维度审查耗时
- `review_total_latency_seconds`: 总审查时间
- `review_failure_rate`: 审查失败率
- `ai_degradation_rate`: AI 降级率
- `task_queue_depth`: Redis 队列深度
- `worker_thread_utilization`: Worker 线程利用率

**Infrastructure Metrics**:
- `jvm_memory_used_bytes`: JVM 内存使用
- `jvm_gc_pause_seconds`: GC 暂停时间
- `system_cpu_usage`: CPU 使用率
- `redis_memory_used_bytes`: Redis 内存使用
- `postgres_connections_active`: 数据库活跃连接数

**And** 创建 Grafana Dashboard：

**Dashboard 1: 系统概览**
- 总审查数（今日/本周/本月）
- 审查成功率（实时）
- API 请求速率（req/s）
- 系统资源使用（CPU, Memory, Disk）

**Dashboard 2: 审查性能**
- 审查时间分布图（p50, p95, p99）
- 各维度审查耗时对比
- 队列深度时间序列
- Worker 线程利用率热力图

**Dashboard 3: 错误监控**
- 错误率趋势图
- Top 10 错误类型
- AI API 失败率
- 降级事件时间线

**And** 配置 Alertmanager 告警规则：

```yaml
groups:
  - name: ai-code-review-alerts
    rules:
      # 审查失败率过高
      - alert: HighReviewFailureRate
        expr: rate(review_failure_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "审查失败率超过 5%"
          description: "过去 5 分钟内审查失败率为 {{ $value }}%"

      # AI 降级率过高
      - alert: HighAIDegradationRate
        expr: rate(ai_degradation_total[5m]) > 0.10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "AI 降级率超过 10%"

      # 队列积压
      - alert: HighQueueDepth
        expr: task_queue_depth > 50
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "任务队列深度超过 50"

      # API 响应时间过长
      - alert: SlowAPIResponse
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "API p95 响应时间超过 1 秒"

      # 数据库连接池耗尽
      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "数据库连接池使用率超过 90%"

      # JVM 内存不足
      - alert: HighJVMMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM 内存使用率超过 85%"
```

**And** 集成通知渠道：
- Slack Webhook（告警发送到 #alerts 频道）
- Email（发送给 on-call 工程师）
- PagerDuty（严重告警自动创建 incident）

**And** 配置日志聚合（ELK Stack）：
```yaml
# Filebeat configuration
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/ai-code-review/*.log
    fields:
      app: ai-code-review
    multiline:
      pattern: '^\d{4}-\d{2}-\d{2}'
      negate: true
      match: after

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "ai-code-review-%{+yyyy.MM.dd}"
```

**And** 创建 Kibana Dashboard：
- 错误日志搜索和过滤
- 审查流程日志追踪（按 taskId 关联）
- Slow query 日志分析

**And** 健康检查端点：
```java
@Component
public class SystemHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        // Check Redis
        try {
            redisTemplate.opsForValue().get("health-check");
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "Connection failed")
                .build();
        }

        // Check Database
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .build();
        }

        // Check Queue Depth
        Long queueDepth = redisTemplate.opsForZSet().size("review-queue");
        if (queueDepth > 100) {
            return Health.down()
                .withDetail("queue", "Queue depth too high: " + queueDepth)
                .build();
        }

        return Health.up()
            .withDetail("queue_depth", queueDepth)
            .build();
    }
}
```

**And** 验收测试：
- 触发高失败率场景，验证告警发送（< 5 分钟）
- 模拟数据库故障，验证健康检查失败
- 验证 Grafana Dashboard 数据实时更新

---

**Story 1.8 补充: HTTPS 配置细节**

在 Story 1.8（Docker Compose 环境配置）的验收标准中，补充以下内容：

**And** 配置 HTTPS 支持（生产环境）：

**方案 1: Nginx Reverse Proxy + Let's Encrypt**

创建 `docker-compose.prod.yml`:
```yaml
version: '3.8'
services:
  nginx:
    image: nginx:1.25
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
      - /etc/letsencrypt:/etc/letsencrypt
    depends_on:
      - backend

  certbot:
    image: certbot/certbot
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt
      - /var/www/certbot:/var/www/certbot
    command: certonly --webroot --webroot-path=/var/www/certbot --email admin@example.com --agree-tos --no-eff-email -d api.example.com
```

**Nginx Configuration** (`nginx/nginx.conf`):
```nginx
server {
    listen 80;
    server_name api.example.com;

    # Redirect HTTP to HTTPS
    location / {
        return 301 https://$host$request_uri;
    }

    # Let's Encrypt verification
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    # SSL Certificate
    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;

    # SSL Configuration (Modern)
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;

    # Security Headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Proxy to Backend
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Frontend Static Files
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

**方案 2: 自签名证书（开发/测试环境）**

生成自签名证书脚本 (`generate-ssl-cert.sh`):
```bash
#!/bin/bash

mkdir -p nginx/ssl
cd nginx/ssl

# Generate private key
openssl genrsa -out server.key 2048

# Generate certificate signing request
openssl req -new -key server.key -out server.csr \
  -subj "/C=CN/ST=Beijing/L=Beijing/O=Example/OU=IT/CN=localhost"

# Generate self-signed certificate (valid for 365 days)
openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt

echo "SSL certificate generated in nginx/ssl/"
```

**Spring Boot HTTPS Configuration** (备选方案，直接使用 Spring Boot):
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: tomcat
```

**生成 PKCS12 Keystore**:
```bash
keytool -genkeypair -alias tomcat \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 365 \
  -storepass changeit
```

**And** 更新 API 文档说明 HTTPS 端点：
- 开发环境: `http://localhost:8080/api/v1/*`
- 生产环境: `https://api.example.com/api/v1/*`

**And** 配置 Webhook URL 验证：
- 确保 Git 平台 Webhook 配置使用 HTTPS URL
- 验证 SSL 证书有效性

**And** 自动续期 Let's Encrypt 证书：
```bash
# Cron job (每月1日执行)
0 0 1 * * docker-compose -f docker-compose.prod.yml run certbot renew && docker-compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

---

