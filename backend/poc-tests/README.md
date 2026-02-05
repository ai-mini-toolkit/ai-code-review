# AI Code Review - PoC 测试套件

本目录包含 AI Code Review 项目的所有技术验证 (Proof of Concept, PoC) 测试。这些测试旨在验证关键技术选型是否满足项目需求。

## 概述

| PoC 项目 | 目标 | 状态 | 优先级 |
|---------|------|------|--------|
| JavaParser 性能 | 验证 Java 代码解析性能 | ✅ Ready | P0 |
| AWS CodeCommit 集成 | 验证 CodeCommit API 集成 | ✅ Ready | P0 |
| Redis 队列并发 | 验证任务队列高并发性能 | ✅ Ready | P0 |

## 目录结构

```
backend/poc-tests/
├── README.md                           # 本文件
├── run-all-pocs.sh                     # Linux/Mac 一键运行脚本
├── run-all-pocs.bat                    # Windows 一键运行脚本
│
├── javaparser-performance/             # PoC 1: JavaParser 性能测试
│   ├── pom.xml
│   ├── README.md
│   └── src/main/java/com/aicr/poc/
│       ├── JavaParserPerformanceTest.java
│       ├── TestCodeGenerator.java
│       └── PerformanceMetrics.java
│
├── aws-codecommit/                     # PoC 2: AWS CodeCommit 集成测试
│   ├── pom.xml
│   ├── README.md
│   └── src/main/java/com/aicr/poc/
│       ├── AwsCodeCommitIntegrationTest.java
│       ├── DifferenceRetriever.java
│       └── MockDataGenerator.java
│
└── redis-queue/                        # PoC 3: Redis 队列并发测试
    ├── pom.xml
    ├── README.md
    └── src/main/java/com/aicr/poc/
        ├── RedisQueuePerformanceTest.java
        ├── TaskProducer.java
        ├── TaskConsumer.java
        ├── MockReviewTask.java
        └── PerformanceMonitor.java
```

## 快速开始

### 前置要求

#### 所有测试通用

- **Java**: 17 或更高版本
- **Maven**: 3.6 或更高版本

#### 特定测试要求

- **AWS CodeCommit 测试**: AWS 账户和配置（可选，支持 Demo 模式）
- **Redis 测试**: Redis 服务器或 Docker

### 一键运行所有测试

#### Linux / macOS

```bash
cd backend/poc-tests

# 运行基础测试（JavaParser + Redis）
./run-all-pocs.sh

# 运行所有测试（包括 AWS CodeCommit）
./run-all-pocs.sh --all

# 跳过特定测试
./run-all-pocs.sh --skip-redis

# 查看帮助
./run-all-pocs.sh --help
```

#### Windows

```cmd
cd backend\poc-tests

REM 运行基础测试（JavaParser + Redis）
run-all-pocs.bat

REM 运行所有测试（包括 AWS CodeCommit）
run-all-pocs.bat --all

REM 查看帮助
run-all-pocs.bat --help
```

### 单独运行测试

每个 PoC 项目都可以独立运行。详细说明请查看各自的 README.md:

- [JavaParser 性能测试](./javaparser-performance/README.md)
- [AWS CodeCommit 集成测试](./aws-codecommit/README.md)
- [Redis 队列并发测试](./redis-queue/README.md)

## 测试结果

### 输出位置

测试结果会保存到:

```
_bmad-output/poc-test-results/
├── test-summary-YYYYMMDD_HHMMSS.txt
├── javaparser-report-YYYYMMDD_HHMMSS.json
├── codecommit-report-YYYYMMDD_HHMMSS.json
├── redis-report-YYYYMMDD_HHMMSS.json
├── javaparser-output-YYYYMMDD_HHMMSS.log
├── codecommit-output-YYYYMMDD_HHMMSS.log
└── redis-output-YYYYMMDD_HHMMSS.log
```

### 报告格式

每个测试会生成:

1. **JSON 报告**: 结构化的测试结果，包含所有指标
2. **控制台输出**: 详细的测试执行日志
3. **总结文件**: 所有测试的汇总结果

### 执行报告模板

完成测试后，填写执行报告:

```
_bmad-output/implementation-artifacts/poc-execution-report.md
```

这个报告包含:
- 详细的测试结果
- Go/No-Go 决策
- 风险评估
- 下一步行动计划

## 性能阈值

### JavaParser 性能测试

| 文件大小 | 阈值 | 预期性能 |
|---------|------|---------|
| 100 行 | < 100ms | 20-50ms |
| 1000 行 | < 500ms | 150-300ms |
| 5000 行 | < 2000ms | 500-1500ms |
| 内存 | < 500MB | 20-50MB |

### AWS CodeCommit 集成测试

| 场景 | 阈值 | 预期性能 |
|------|------|---------|
| < 10 文件 | < 2s | 0.5-1.5s |
| 10-50 文件 | < 5s | 1-3s |
| 50+ 文件 | < 15s | 3-10s |

### Redis 队列并发测试

| 指标 | 阈值 | 预期性能 |
|------|------|---------|
| 吞吐量 | ≥ 100 tasks/sec | 300-800 tasks/sec |
| P95 延迟 | ≤ 1000ms | 300-900ms |
| P99 延迟 | ≤ 2000ms | 700-1800ms |
| 成功率 | ≥ 99% | 100% |

## Go/No-Go 决策标准

### GO 条件

- ✅ 所有测试通过性能阈值
- ✅ 功能验证完整
- ✅ 无关键错误或失败
- ✅ 识别的风险有明确缓解措施

### GO with Caution 条件

- ⚠️ 大部分测试通过，个别接近阈值
- ⚠️ 有可管理的风险
- ⚠️ 需要在开发中持续监控

### NO-GO 条件

- ❌ 关键测试失败
- ❌ 性能远低于阈值
- ❌ 无法满足基本功能要求
- ❌ 有无法缓解的高风险

## 故障排查

### 常见问题

#### Java 版本问题

```bash
# 检查 Java 版本
java -version

# 应该显示 17 或更高版本
# 如果版本过低，请安装 Java 17+
```

#### Maven 编译失败

```bash
# 清理并重新编译
mvn clean install -DskipTests

# 查看详细错误
mvn clean compile -X
```

#### Redis 连接失败

```bash
# 检查 Redis 是否运行
redis-cli ping
# 应该返回 PONG

# 使用 Docker 启动 Redis
docker run -d -p 6379:6379 redis:latest

# 查看 Redis 日志
docker logs <container-id>
```

#### AWS 认证失败

```bash
# 配置 AWS 凭证
aws configure

# 或设置环境变量
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=us-east-1

# 测试连接
aws codecommit list-repositories
```

### 获取帮助

如果遇到问题:

1. 查看各 PoC 项目的 README.md
2. 检查测试输出日志
3. 查看 Maven 错误信息
4. 验证前置条件是否满足

## 预计执行时间

| 测试 | 预计时间 |
|------|---------|
| JavaParser 性能 | 3-5 分钟 |
| AWS CodeCommit 集成 | 2-5 分钟（实际运行）<br>< 1 分钟（Demo 模式） |
| Redis 队列并发 | 5-10 分钟 |
| **总计** | **10-20 分钟** |

*注: 首次运行需要下载 Maven 依赖，可能需要额外时间*

## 下一步

### 测试通过后

1. **填写执行报告**:
   - 完成 `_bmad-output/implementation-artifacts/poc-execution-report.md`
   - 记录测试结果和决策

2. **技术集成**:
   - 将验证的库添加到主项目 `pom.xml`
   - 开始实现核心服务

3. **架构设计**:
   - 基于 PoC 结果细化架构设计
   - 确定性能优化策略

4. **开发计划**:
   - 更新开发计划和时间表
   - 分配开发任务

### 测试失败后

1. **分析原因**:
   - 是环境问题还是技术限制？
   - 是否可以通过优化解决？

2. **评估替代方案**:
   - 研究替代技术
   - 重新评估架构设计

3. **调整计划**:
   - 更新技术选型
   - 调整项目时间表

## 参考资料

- [JavaParser 官方文档](https://javaparser.org/)
- [AWS CodeCommit API 参考](https://docs.aws.amazon.com/codecommit/latest/APIReference/)
- [Spring Data Redis 文档](https://spring.io/projects/spring-data-redis)
- [Redis 官方文档](https://redis.io/documentation)

## 更新日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2025-02-05 | v1.0 | 初始版本，包含三个 PoC 测试 |

---

**维护者**: AI Code Review Team
**最后更新**: 2025-02-05
