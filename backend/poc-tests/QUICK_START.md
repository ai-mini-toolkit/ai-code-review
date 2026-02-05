# PoC 测试快速开始指南

## 最快方式：一键运行所有测试

### Linux / macOS

```bash
cd backend/poc-tests
chmod +x run-all-pocs.sh
./run-all-pocs.sh
```

### Windows

```cmd
cd backend\poc-tests
run-all-pocs.bat
```

---

## 单独运行测试

### 1. JavaParser 性能测试 (3-5 分钟)

```bash
cd backend/poc-tests/javaparser-performance
mvn clean compile exec:java
```

**无需任何外部服务**，直接运行即可。

---

### 2. AWS CodeCommit 集成测试

#### Demo 模式（推荐，无需 AWS）

```bash
cd backend/poc-tests/aws-codecommit
mvn clean compile exec:java
```

显示设置说明和测试结构。

#### 实际测试模式（需要 AWS）

```bash
export TEST_REPOSITORY=your-repo
export TEST_BEFORE_COMMIT=commit-id-1
export TEST_AFTER_COMMIT=commit-id-2
cd backend/poc-tests/aws-codecommit
mvn clean compile exec:java
```

---

### 3. Redis 队列并发测试 (5-10 分钟)

```bash
# 启动 Redis
docker run -d -p 6379:6379 redis:latest

# 运行测试
cd backend/poc-tests/redis-queue
mvn clean compile exec:java
```

---

## 查看结果

测试结果保存在:

```
_bmad-output/poc-test-results/
├── test-summary-[timestamp].txt          # 总结
├── javaparser-report-[timestamp].json    # JavaParser 详细报告
├── codecommit-report-[timestamp].json    # CodeCommit 详细报告
└── redis-report-[timestamp].json         # Redis 详细报告
```

---

## 填写执行报告

完成测试后，填写:

```
_bmad-output/implementation-artifacts/poc-execution-report.md
```

---

## 需要帮助？

- 查看 [完整文档](./README.md)
- 查看各 PoC 的 README:
  - [JavaParser](./javaparser-performance/README.md)
  - [AWS CodeCommit](./aws-codecommit/README.md)
  - [Redis Queue](./redis-queue/README.md)

---

## 前置要求检查

```bash
# Java 版本（需要 17+）
java -version

# Maven 版本（需要 3.6+）
mvn -version

# Redis（仅 Redis 测试需要）
redis-cli ping
# 或
docker --version
```

---

**预计总时间**: 10-20 分钟（首次运行需下载依赖）
