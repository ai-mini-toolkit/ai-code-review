# Redis Queue High-Concurrency Performance Test Plan

**Document Version**: 1.0
**Date**: 2026-02-05
**Project**: ai-code-review
**Purpose**: Validate Redis queue performance under high-concurrency scenarios

---

## 1. Executive Summary

This document defines a comprehensive performance testing plan for Redis-based task queue implementation. The architecture decision chose Redis over RabbitMQ for simplicity, but this introduces risk around high-concurrency scenarios. This PoC validates that Redis can meet NFR requirements for task queue latency and throughput.

### Test Objectives

1. Measure Redis queue performance under various concurrency levels (10, 50, 100, 200 concurrent tasks)
2. Validate NFR requirement: "Task processing latency < 5s (enqueue to execution start)"
3. Validate NFR requirement: "Concurrent tasks ≥ 10 simultaneous"
4. Identify queue bottlenecks and failure modes
5. Establish degradation strategy if Redis cannot meet performance targets

---

## 2. Redis Queue Architecture

### 2.1 Queue Implementation Strategy

**Data Structure**: **Redis Sorted Sets** (for priority queue)

**Why Sorted Sets**:
- Native priority support (score = priority + timestamp)
- Atomic operations (ZADD, ZPOPMIN)
- O(log N) enqueue/dequeue complexity
- Built-in duplicate prevention

**Queue Operations**:

**Enqueue** (Producer):
```java
// Priority calculation: Higher priority = lower score (pop first)
// Priority: HIGH = 1, NORMAL = 2
// Score = (priority * 1e13) + timestamp_millis
long score = (priority == TaskPriority.HIGH ? 1 : 2) * 10_000_000_000_000L
             + System.currentTimeMillis();

redisTemplate.opsForZSet().add("review-queue", taskId, score);
```

**Dequeue** (Consumer):
```java
// Atomically pop lowest score (highest priority, earliest timestamp)
Set<TypedTuple<String>> tasks = redisTemplate.opsForZSet()
    .popMin("review-queue", 1);

if (tasks != null && !tasks.isEmpty()) {
    String taskId = tasks.iterator().next().getValue();
    return taskId;
}
return null;
```

**Queue Depth Monitoring**:
```java
Long queueDepth = redisTemplate.opsForZSet().size("review-queue");
```

### 2.2 Worker Pool Architecture

**Pattern**: **Fixed Thread Pool** with blocking dequeue

**Configuration**:
- **Thread Pool Size**: 10 (configurable, matches NFR concurrent tasks requirement)
- **Polling Strategy**: Blocking pop with 1-second timeout
- **Graceful Shutdown**: Wait for in-flight tasks to complete

**Worker Implementation**:
```java
@Service
public class ReviewWorkerPool {

    private final ExecutorService executorService;
    private final RedisQueueService queueService;
    private final ReviewService reviewService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String taskId = queueService.dequeue(1000);  // 1s timeout
                        if (taskId != null) {
                            reviewService.executeReview(taskId);
                        }
                    } catch (Exception e) {
                        log.error("Worker error", e);
                    }
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);
    }
}
```

---

## 3. Test Scenarios

### Scenario 1: Low Concurrency (10 concurrent tasks)

**Objective**: Validate baseline performance meets NFR requirements

**Test Configuration**:
- **Producer Rate**: 10 tasks/second (burst of 10, then pause)
- **Task Duration**: Simulated 5-second processing time per task
- **Total Tasks**: 100
- **Expected Behavior**: All workers busy, queue depth = 0 (immediate consumption)

**Performance Targets**:
- **Enqueue Latency**: < 10ms (p95)
- **Dequeue Latency**: < 50ms (p95)
- **Queue-to-Execution Latency**: < 100ms (p95) ✅ **Meets NFR (<5s)**
- **Throughput**: 10 tasks/second (matches worker pool size)
- **Queue Depth**: Average < 5, Max < 20

---

### Scenario 2: Medium Concurrency (50 concurrent tasks)

**Objective**: Test queue backlog handling under moderate load

**Test Configuration**:
- **Producer Rate**: 50 tasks/second (burst of 50, then pause)
- **Task Duration**: Simulated 5-second processing time per task
- **Total Tasks**: 500
- **Expected Behavior**: Queue backlog builds up, workers consume at max rate (10/s)

**Performance Targets**:
- **Enqueue Latency**: < 20ms (p95)
- **Dequeue Latency**: < 100ms (p95)
- **Queue-to-Execution Latency**:
  - First 10 tasks: < 500ms ✅
  - Remaining tasks: < 5s (average wait time for queue position)
- **Throughput**: 10 tasks/second (sustained)
- **Queue Depth**: Average = 40, Max = 50 (during burst)

**Key Metric**: Time to drain queue after burst
- **Target**: < 30 seconds (50 tasks / 10 workers * 5s task duration = 25s)

---

### Scenario 3: High Concurrency (100 concurrent tasks)

**Objective**: Test system behavior under heavy load

**Test Configuration**:
- **Producer Rate**: 100 tasks/second (burst of 100, then pause)
- **Task Duration**: Simulated 5-second processing time per task
- **Total Tasks**: 1000
- **Expected Behavior**: Large queue backlog, sustained consumption at 10 tasks/s

**Performance Targets**:
- **Enqueue Latency**: < 50ms (p95)
- **Dequeue Latency**: < 200ms (p95)
- **Queue-to-Execution Latency**:
  - First 10 tasks: < 1s ✅
  - Tasks 11-100: < 60s (average queue wait)
- **Throughput**: 10 tasks/second (sustained)
- **Queue Depth**: Average = 90, Max = 100

**Key Metric**: Time to drain queue
- **Target**: < 60 seconds (100 tasks / 10 workers * 5s = 50s)

---

### Scenario 4: Extreme Concurrency (200 concurrent tasks)

**Objective**: Test system limits and failure modes

**Test Configuration**:
- **Producer Rate**: 200 tasks/second (burst of 200)
- **Task Duration**: Simulated 5-second processing time per task
- **Total Tasks**: 2000
- **Expected Behavior**: Very large queue backlog, potential memory pressure

**Performance Targets**:
- **Enqueue Latency**: < 100ms (p95) - May degrade under pressure
- **Dequeue Latency**: < 500ms (p95)
- **Queue-to-Execution Latency**:
  - First 10 tasks: < 2s
  - Tasks 11-200: < 120s (average queue wait)
- **Throughput**: 10 tasks/second (sustained)
- **Queue Depth**: Average = 190, Max = 200

**Failure Mode Testing**:
- **Redis Memory**: Should not exceed 100 MB (200 tasks * 500 KB metadata)
- **No OOM**: Redis should not run out of memory
- **No Data Loss**: All 2000 tasks should be processed eventually

**Key Metric**: Time to drain queue
- **Target**: < 120 seconds (200 tasks / 10 workers * 5s = 100s)

---

## 4. Performance Metrics

### Primary Metrics

| Metric | Description | Measurement Method |
|--------|-------------|-------------------|
| **Enqueue Latency** | Time from `enqueue()` call to Redis ZADD completion | StopWatch around redisTemplate.opsForZSet().add() |
| **Dequeue Latency** | Time from `dequeue()` call to Redis ZPOPMIN completion | StopWatch around redisTemplate.opsForZSet().popMin() |
| **Queue-to-Execution Latency** | Time from task enqueued to worker starts processing | Timestamp in task metadata, calculate delta |
| **Throughput** | Tasks processed per second | Count completed tasks / total elapsed time |
| **Queue Depth** | Number of tasks in queue at any moment | Redis ZCARD command, sample every 500ms |
| **Error Rate** | Percentage of failed enqueue/dequeue operations | Count errors / total operations |

### Secondary Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| **Redis Memory Usage** | Memory consumed by queue data structure | < 100 MB for 200 tasks |
| **Redis CPU Usage** | CPU utilization of Redis process | < 50% |
| **Worker Thread Utilization** | Percentage of time workers are busy | > 95% (indicates efficient queue draining) |
| **Task Execution Time** | Actual time to process a single task | ~5s (simulated, should be stable) |
| **GC Pauses** | Java GC pauses in worker JVM | < 100ms (p99) |
| **Network Errors** | Redis connection errors, timeouts | 0 (100% reliability) |

---

## 5. Acceptance Criteria

### NFR Alignment

**NFR 1.3**: "Task processing latency < 5s (enqueue to execution start)"

| Scenario | First 10 Tasks Latency | Acceptance |
|----------|------------------------|------------|
| Scenario 1 (10 tasks) | < 100ms | ✅ **Pass** |
| Scenario 2 (50 tasks) | < 500ms | ✅ **Pass** |
| Scenario 3 (100 tasks) | < 1s | ✅ **Pass** |
| Scenario 4 (200 tasks) | < 2s | ✅ **Pass** |

**NFR 1.4**: "Concurrent tasks ≥ 10 simultaneous"

- Worker pool size = 10 ✅
- Throughput sustained at 10 tasks/second ✅

### Pass/Fail Criteria

| Criterion | Pass Condition | Fail Condition |
|-----------|----------------|----------------|
| **Latency (First 10 Tasks)** | < 2s in all scenarios | > 5s in any scenario |
| **Throughput** | ≥ 10 tasks/second sustained | < 10 tasks/second |
| **Error Rate** | < 1% | ≥ 5% |
| **Queue Drain Time** | Within 2x of calculated target | > 3x of calculated target |
| **Memory Usage** | < 100 MB for 200 tasks | > 500 MB or OOM |
| **Stability** | 10 consecutive runs with < 15% variance | Standard deviation > 30% |

---

## 6. Test Environment

### Hardware Requirements

- **CPU**: 4 cores, 2.5 GHz minimum
- **RAM**: 8 GB minimum
- **Redis**: 6.2 or later, standalone mode (single-node for PoC)
- **Network**: Localhost (Redis and application on same machine)

### Software Requirements

- **JDK**: OpenJDK 17
- **Spring Boot**: 3.x with spring-boot-starter-data-redis
- **Redis Client**: Lettuce (default in Spring Boot)
- **Testing Framework**: JUnit 5 + TestContainers (for Redis)
- **Profiling Tools**: JMH, Micrometer, Redis CLI (INFO command)

### Redis Configuration

**redis.conf**:
```
# Memory
maxmemory 2gb
maxmemory-policy allkeys-lru

# Persistence (disable for PoC to maximize performance)
save ""
appendonly no

# Performance
tcp-backlog 511
timeout 0
tcp-keepalive 300
```

**Spring Boot Configuration**:
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 10000  # 10s connection timeout
    lettuce:
      pool:
        max-active: 20  # Connection pool size
        max-idle: 10
        min-idle: 5
      shutdown-timeout: 2000ms
```

---

## 7. Test Implementation

### Test Code Structure

**Producer** (Enqueues tasks):
```java
@Component
public class TaskProducer {

    @Autowired
    private RedisQueueService queueService;

    public void produceTaskBurst(int count, TaskPriority priority) {
        List<Long> enqueueTimes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String taskId = UUID.randomUUID().toString();
            long startTime = System.nanoTime();

            queueService.enqueue(taskId, priority);

            long enqueueTime = System.nanoTime() - startTime;
            enqueueTimes.add(enqueueTime);
        }

        // Calculate statistics
        LongSummaryStatistics stats = enqueueTimes.stream()
            .mapToLong(Long::longValue)
            .summaryStatistics();

        log.info("Enqueue latency (ms): avg={}, max={}, p95={}",
            stats.getAverage() / 1_000_000,
            stats.getMax() / 1_000_000,
            calculateP95(enqueueTimes) / 1_000_000
        );
    }
}
```

**Consumer** (Processes tasks):
```java
@Component
public class TaskConsumer {

    @Autowired
    private RedisQueueService queueService;

    private final Map<String, Long> enqueueTimestamps = new ConcurrentHashMap<>();
    private final List<Long> queueLatencies = new CopyOnWriteArrayList<>();

    public void consumeTask() {
        long dequeueStartTime = System.nanoTime();
        String taskId = queueService.dequeue(1000);  // 1s timeout
        long dequeueTime = System.nanoTime() - dequeueStartTime;

        if (taskId != null) {
            // Simulate task processing
            simulateReviewTask(5000);  // 5 seconds

            // Calculate queue latency
            Long enqueueTime = enqueueTimestamps.remove(taskId);
            if (enqueueTime != null) {
                long queueLatency = System.currentTimeMillis() - enqueueTime;
                queueLatencies.add(queueLatency);
            }
        }
    }

    private void simulateReviewTask(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void printStatistics() {
        LongSummaryStatistics stats = queueLatencies.stream()
            .mapToLong(Long::longValue)
            .summaryStatistics();

        log.info("Queue-to-execution latency (ms): avg={}, max={}, p95={}",
            stats.getAverage(),
            stats.getMax(),
            calculateP95(queueLatencies)
        );
    }
}
```

**Performance Test**:
```java
@SpringBootTest
public class RedisQueuePerformanceTest {

    @Autowired
    private TaskProducer producer;

    @Autowired
    private TaskConsumer consumer;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void testScenario1_LowConcurrency() throws Exception {
        // Setup: Start 10 worker threads
        ExecutorService workers = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            workers.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    consumer.consumeTask();
                }
            });
        }

        // Test: Produce 100 tasks in burst
        long startTime = System.currentTimeMillis();
        producer.produceTaskBurst(100, TaskPriority.NORMAL);

        // Wait for queue to drain
        while (getQueueDepth() > 0) {
            Thread.sleep(100);
        }
        long totalTime = System.currentTimeMillis() - startTime;

        // Assertions
        assertThat(totalTime).isLessThan(60_000);  // < 60 seconds
        consumer.printStatistics();

        // Cleanup
        workers.shutdownNow();
    }

    @Test
    public void testScenario3_HighConcurrency() throws Exception {
        // Similar structure, produce 1000 tasks
        // Measure queue depth over time
        // Assert latency targets
    }

    private long getQueueDepth() {
        return redisTemplate.opsForZSet().size("review-queue");
    }
}
```

### Measurement Methodology

**Latency Measurement**:
1. **Enqueue**: `System.nanoTime()` before and after `ZADD` operation
2. **Dequeue**: `System.nanoTime()` before and after `ZPOPMIN` operation
3. **Queue-to-Execution**: Store timestamp in task metadata, calculate delta when worker picks up task

**Throughput Measurement**:
1. Count total tasks completed
2. Measure elapsed time from first enqueue to last task completion
3. Calculate: throughput = total tasks / elapsed time

**Queue Depth Monitoring**:
1. Background thread samples `ZCARD review-queue` every 500ms
2. Record time-series data: `[(timestamp, queue_depth), ...]`
3. Calculate: average, max, min queue depth

**Error Rate Measurement**:
1. Count exceptions during enqueue/dequeue operations
2. Calculate: error rate = errors / total operations

---

## 8. Degradation Strategies

### Strategy 1: Increase Worker Pool Size

**Trigger Condition**: Queue depth consistently > 50 for > 60 seconds

**Implementation**:
- Increase worker pool size from 10 to 20 (configurable)
- Scale horizontally: Deploy multiple application instances with shared Redis

**Trade-offs**:
- Pros: Higher throughput, faster queue draining
- Cons: Higher resource usage (CPU, memory)

**Configuration**:
```yaml
review:
  worker-pool-size: ${WORKER_POOL_SIZE:10}  # Default 10, override with env var
```

---

### Strategy 2: Priority Dropping (Quality Degradation)

**Trigger Condition**: Queue depth > 100 for > 5 minutes

**Implementation**:
- Automatically downgrade low-priority tasks (Push events) to "lightweight review mode"
- Lightweight mode: Skip call graph analysis, use faster AI model (GPT-3.5 instead of GPT-4)

**Trade-offs**:
- Pros: Reduce task processing time from 30s to 10s, clear backlog faster
- Cons: Lower review quality for Push events

**Example**:
```java
if (queueDepth > 100 && task.getPriority() == TaskPriority.NORMAL) {
    task.setReviewMode(ReviewMode.LIGHTWEIGHT);
    log.warn("Queue backlog high, downgrading task {} to lightweight mode", task.getId());
}
```

---

### Strategy 3: Migrate to RabbitMQ (Architectural Change)

**Trigger Condition**: Redis queue consistently fails to meet latency targets (p95 > 5s for first 10 tasks)

**Implementation**:
- Replace Redis Sorted Sets with RabbitMQ priority queue
- RabbitMQ provides better concurrency handling, persistence, and monitoring

**Trade-offs**:
- Pros: Battle-tested for high-concurrency, better observability
- Cons: Additional infrastructure dependency, more operational complexity

**Migration Path**:
```java
// Abstract queue interface allows swapping implementations
public interface TaskQueue {
    void enqueue(String taskId, TaskPriority priority);
    String dequeue(long timeoutMs);
    long getQueueDepth();
}

// Redis implementation
@Component
@ConditionalOnProperty(name = "queue.type", havingValue = "redis", matchIfMissing = true)
public class RedisTaskQueue implements TaskQueue { ... }

// RabbitMQ implementation
@Component
@ConditionalOnProperty(name = "queue.type", havingValue = "rabbitmq")
public class RabbitMQTaskQueue implements TaskQueue { ... }
```

---

## 9. Risk Assessment

### High Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Redis memory exhaustion** | 30% | High | Monitor memory, implement max queue depth limit (300 tasks) |
| **Queue latency > 5s under load** | 40% | High | Implement Strategy 1 (scale workers) or Strategy 3 (RabbitMQ) |
| **Redis single point of failure** | 25% | High | Document Redis Sentinel/Cluster setup for Phase 2 |
| **Task loss on Redis restart** | 20% | Medium | Enable AOF persistence (trade-off: 10-15% performance degradation) |

### Medium Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Network latency (Redis <-> App)** | 20% | Medium | Deploy Redis and app on same host/datacenter |
| **Priority inversion** | 15% | Medium | Use separate queues for HIGH and NORMAL priority |
| **Thundering herd on queue empty** | 10% | Low | Implement exponential backoff in polling loop |

---

## 10. Success Criteria Summary

### Must-Have (P0)

- ✅ Scenario 1 (10 tasks): Queue-to-execution latency < 100ms for first 10 tasks
- ✅ Scenario 2 (50 tasks): Queue-to-execution latency < 500ms for first 10 tasks
- ✅ Throughput ≥ 10 tasks/second sustained
- ✅ Error rate < 1%
- ✅ No Redis memory exhaustion (< 100 MB for 200 tasks)

### Should-Have (P1)

- ✅ Scenario 3 (100 tasks): Queue-to-execution latency < 1s for first 10 tasks
- ✅ Queue drain time within 2x of calculated target
- ✅ Performance variance < 15% across 10 runs

### Nice-to-Have (P2)

- 🔲 Scenario 4 (200 tasks): All targets met (stretch goal)
- 🔲 Redis Cluster setup for high availability (Phase 2)
- 🔲 Dynamic worker pool scaling based on queue depth

---

## 11. Deliverables

### Test Code
- [ ] TaskProducer.java (enqueue tasks)
- [ ] TaskConsumer.java (dequeue and process tasks)
- [ ] RedisQueuePerformanceTest.java (JUnit test suite)
- [ ] QueueMetricsCollector.java (queue depth monitoring)

### Test Reports
- [ ] Performance benchmark report (latency, throughput, queue depth over time)
- [ ] Redis memory usage report
- [ ] Stress test report (Scenario 4 failure modes)

### Decision Document
- [ ] Go/No-Go decision for Redis vs RabbitMQ
- [ ] Worker pool size recommendation
- [ ] Degradation strategy implementation plan

---

## 12. Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| **Setup** | 0.5 day | Setup Redis, Spring Boot test harness |
| **Test Implementation** | 1 day | Write producer, consumer, metrics collector |
| **Test Execution** | 0.5 day | Run all 4 scenarios, collect data |
| **Analysis** | 0.5 day | Analyze results, identify bottlenecks |
| **Decision** | 0.5 day | Make architectural decision, document findings |
| **Total** | **3 days** | |

---

## 13. Appendix

### A. Expected Performance Baselines

**Redis Sorted Sets Performance** (based on Redis 6.2 benchmarks):
- ZADD: ~50,000 ops/second (single-threaded)
- ZPOPMIN: ~45,000 ops/second (single-threaded)
- ZCARD: ~100,000 ops/second (O(1) operation)

**Network Overhead** (localhost):
- RTT: ~0.1ms
- Negligible for PoC, but significant for remote Redis (1-5ms)

**Expected Bottleneck**: Worker thread pool size (10 threads), not Redis performance

### B. Alternative Queue Implementations Comparison

| Feature | Redis Sorted Sets | RabbitMQ Priority Queue | AWS SQS |
|---------|------------------|------------------------|---------|
| **Enqueue Latency** | < 1ms | 1-5ms | 10-50ms |
| **Dequeue Latency** | < 1ms | 1-5ms | 10-50ms |
| **Throughput** | 50k ops/s | 10k msgs/s | Unlimited (with delays) |
| **Priority Support** | Native (score) | Native (x-max-priority) | No (separate queues) |
| **Persistence** | Optional (AOF) | Durable by default | Durable by default |
| **Operational Complexity** | Low | Medium | Low (managed) |
| **Cost** | Free (self-hosted) | Free (self-hosted) | $0.40 per million requests |

**Recommendation**: Start with Redis for simplicity, preserve RabbitMQ migration path.

### C. Redis Monitoring Commands

```bash
# Real-time stats
redis-cli --stat

# Memory usage
redis-cli INFO MEMORY

# Queue depth
redis-cli ZCARD review-queue

# Slowlog (operations > 10ms)
redis-cli SLOWLOG GET 10
```

---

**Document Prepared By**: Backend Performance Engineer
**Review Status**: Draft
**Next Review Date**: After test execution (Day 3)
