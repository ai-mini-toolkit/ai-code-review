# Redis Queue Concurrency PoC

## 目标

验证 Redis 作为任务队列在高并发场景下的性能表现，确保可以支持 AI Code Review 项目的审查任务分发需求。

## 测试场景

| 场景 | 并发数 | 任务数 | 测试目标 |
|------|--------|--------|---------|
| 低并发 | 10 workers | 500 | 基础功能验证 |
| 中并发 | 50 workers | 2500 | 生产环境模拟 |
| 高并发 | 100 workers | 5000 | 峰值流量测试 |
| 极限并发 | 200 workers | 10000 | 压力测试 |

## 测试指标

- **吞吐量**: 每秒处理的任务数 (tasks/sec)
- **延迟**:
  - 平均延迟
  - P50/P95/P99 延迟
- **成功率**: 成功处理的任务比例
- **错误率**: 失败任务数量
- **资源使用**: Redis 内存和 CPU

## 性能阈值

| 指标 | 阈值 | 说明 |
|------|------|------|
| 吞吐量 | ≥ 100 tasks/sec | 最小处理能力 |
| P95 延迟 | ≤ 1000ms | 95% 任务在 1 秒内开始处理 |
| P99 延迟 | ≤ 2000ms | 99% 任务在 2 秒内开始处理 |
| 成功率 | ≥ 99% | 几乎无失败 |

## 前置要求

### Redis 服务器

**选项 1: Docker (推荐)**
```bash
docker run -d \
  --name redis-poc \
  -p 6379:6379 \
  redis:latest
```

**选项 2: 本地安装**

MacOS:
```bash
brew install redis
redis-server
```

Ubuntu:
```bash
sudo apt-get install redis-server
sudo systemctl start redis
```

Windows:
```bash
# 使用 WSL 或下载 Redis for Windows
# https://github.com/microsoftarchive/redis/releases
```

### Java 环境

- Java 17+
- Maven 3.6+

## 如何运行

### 1. 启动 Redis

```bash
# 使用 Docker
docker run -d -p 6379:6379 redis:latest

# 验证 Redis 运行
redis-cli ping
# 应该返回: PONG
```

### 2. 运行测试

**使用默认配置 (localhost:6379)**:
```bash
cd backend/poc-tests/redis-queue
mvn clean compile exec:java
```

**使用自定义 Redis 配置**:
```bash
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379
mvn clean compile exec:java
```

### 3. 使用 Spring Boot

```bash
mvn clean package
java -jar target/redis-queue-concurrency-1.0-SNAPSHOT.jar
```

## 测试输出

### 控制台输出示例

```
================================================================================
Redis Queue Performance Test - PoC Validation
================================================================================

Redis Configuration:
  Host: localhost
  Port: 6379

Redis connection successful!

--------------------------------------------------------------------------------
Testing concurrency level: 10
--------------------------------------------------------------------------------
Starting 10 consumers...
Starting 5 producers...
Produced 500 tasks
Waiting for consumers to process all tasks...
..........

--- Test Results ---
Concurrency: 10
Duration: 5234ms
Produced: 500
Processed: 500
Errors: 0
Success Rate: 100.00%

Performance:
  Throughput: 95.53 tasks/sec
  Avg Latency: 123.45ms
  P50 Latency: 98ms
  P95 Latency: 456ms
  P99 Latency: 789ms

Result: PASS

--------------------------------------------------------------------------------
Testing concurrency level: 50
--------------------------------------------------------------------------------
[继续测试其他并发级别...]

================================================================================
Go/No-Go Decision
================================================================================

Thresholds:
  Min Throughput: 100.0 tasks/sec
  Max P95 Latency: 1000ms
  Max P99 Latency: 2000ms
  Min Success Rate: 99.0%

Results: 4/4 tests passed

Decision: GO
Redis queue meets all performance requirements.

Recommendation:
  - Proceed with Redis-based task queue
  - Recommended concurrency: 50-100 workers
  - Implement dead letter queue for failed tasks
```

### JSON 报告

测试结果保存在 `target/redis-queue-report.json`:

```json
{
  "test_suite": "Redis Queue Concurrency PoC",
  "timestamp": 1738742400000,
  "results": [
    {
      "concurrency": 10,
      "durationMs": 5234,
      "passed": true,
      "metrics": {
        "total_produced": 500,
        "total_processed": 500,
        "error_count": 0,
        "success_rate": 100.0,
        "throughput_per_sec": 95.53,
        "avg_latency_ms": 123.45,
        "p50_latency_ms": 98,
        "p95_latency_ms": 456,
        "p99_latency_ms": 789
      }
    }
  ]
}
```

## Go/No-Go 标准

### GO 条件（全部满足）

1. **功能正确**:
   - 所有任务都被正确处理
   - 无数据丢失
   - 无重复处理

2. **性能达标**:
   - 吞吐量 ≥ 100 tasks/sec
   - P95 延迟 ≤ 1000ms
   - P99 延迟 ≤ 2000ms

3. **稳定性好**:
   - 成功率 ≥ 99%
   - 错误率 < 1%
   - 无内存泄漏

4. **可扩展性**:
   - 支持至少 100 并发 workers
   - 性能随并发线性增长

### NO-GO 触发条件

1. 任何并发级别下吞吐量 < 50 tasks/sec
2. P99 延迟 > 3000ms
3. 成功率 < 95%
4. 频繁出现连接错误或超时
5. Redis 内存溢出

## 预期结果

基于 Redis 的典型性能（单机）:

| 并发级别 | 预期吞吐量 | 预期 P95 延迟 | 预期 P99 延迟 |
|---------|-----------|--------------|--------------|
| 10 | 80-120 tasks/sec | 200-500ms | 500-1000ms |
| 50 | 300-500 tasks/sec | 300-700ms | 700-1500ms |
| 100 | 500-800 tasks/sec | 400-900ms | 900-1800ms |
| 200 | 700-1000 tasks/sec | 500-1000ms | 1000-2000ms |

## 架构说明

### 生产者-消费者模型

```
┌─────────────┐    LPUSH     ┌───────────────┐    BRPOP    ┌──────────────┐
│  Producers  │─────────────>│ Redis Queue   │────────────>│  Consumers   │
│  (N/2个)    │              │  (List)       │             │  (N个)       │
└─────────────┘              └───────────────┘             └──────────────┘
                                                                   │
                                                                   v
                                                            ┌──────────────┐
                                                            │ Process Task │
                                                            │  (50-200ms)  │
                                                            └──────────────┘
```

### 关键实现

1. **队列操作**:
   - `LPUSH`: 生产者添加任务到队列头
   - `BRPOP`: 消费者从队列尾阻塞获取任务

2. **连接池**:
   - Lettuce 客户端
   - 连接池大小: 并发数 + 10

3. **任务处理**:
   - 每个任务模拟 50-200ms 处理时间
   - 包含 6 种审查维度

## 监控 Redis

### 实时监控

```bash
# 连接到 Redis CLI
redis-cli

# 查看队列长度
LLEN code-review-tasks

# 监控所有命令
MONITOR

# 查看统计信息
INFO stats

# 查看内存使用
INFO memory
```

### 性能监控命令

```bash
# 查看慢查询
SLOWLOG GET 10

# 查看客户端连接
CLIENT LIST

# 查看服务器信息
INFO server
```

## 故障排查

### Redis 连接失败

```bash
# 检查 Redis 是否运行
redis-cli ping

# 检查端口是否开放
netstat -an | grep 6379

# Docker 查看日志
docker logs redis-poc
```

### 性能不达标

1. **增加 Redis 内存**:
   ```bash
   docker run -d -p 6379:6379 \
     --memory=2g \
     redis:latest redis-server --maxmemory 2gb
   ```

2. **调整连接池**:
   - 增加 Lettuce 连接池大小
   - 调整超时时间

3. **优化任务处理**:
   - 减少任务处理时间
   - 使用批量操作

### 内存问题

```bash
# 查看内存使用
redis-cli INFO memory

# 清理所有数据
redis-cli FLUSHALL

# 设置内存上限
redis-cli CONFIG SET maxmemory 512mb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

## 生产环境建议

### 如果测试通过 (GO)

1. **Redis 配置优化**:
   ```conf
   # redis.conf
   maxmemory 2gb
   maxmemory-policy allkeys-lru
   save 900 1
   save 300 10
   ```

2. **高可用部署**:
   - Redis Sentinel (主从复制)
   - Redis Cluster (分片)
   - 云服务: AWS ElastiCache, Azure Redis Cache

3. **实现功能**:
   - 死信队列 (Dead Letter Queue)
   - 任务重试机制
   - 任务优先级
   - 监控和告警

4. **代码集成**:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-redis</artifactId>
   </dependency>
   ```

### 如果测试失败 (NO-GO)

1. **性能优化**:
   - 升级到 Redis Cluster
   - 使用 Redis Streams 替代 List
   - 增加服务器资源

2. **替代方案**:
   - **RabbitMQ**: 更丰富的消息模式
   - **Apache Kafka**: 更高吞吐量，适合大规模
   - **AWS SQS**: 托管服务，简化运维

3. **混合方案**:
   - Redis 用于缓存和状态管理
   - 消息队列使用专用 MQ

## 清理资源

```bash
# 停止并删除 Docker 容器
docker stop redis-poc
docker rm redis-poc

# 清理 Maven 构建
mvn clean
```

## 参考资料

- [Redis Documentation](https://redis.io/documentation)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Lettuce Documentation](https://lettuce.io/core/release/reference/)
- [Redis as a Message Queue](https://redis.io/topics/streams-intro)

## 下一步

成功通过测试后:

1. 集成到主项目
2. 实现 `TaskQueueService`
3. 添加监控和告警
4. 编写单元测试和集成测试
5. 部署到生产环境
