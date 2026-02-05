# PoC 测试索引

快速导航到所有 PoC 测试相关文档和代码。

## 📚 主要文档

| 文档 | 描述 | 链接 |
|------|------|------|
| **快速开始** | 最快运行方式 | [QUICK_START.md](./QUICK_START.md) |
| **完整指南** | 详细使用说明 | [README.md](./README.md) |
| **实施完成报告** | 所有文件清单和摘要 | [../../POC_IMPLEMENTATION_COMPLETE.md](../../POC_IMPLEMENTATION_COMPLETE.md) |

## 🧪 PoC 项目

### 1. JavaParser 性能测试

- **目录**: [javaparser-performance/](./javaparser-performance/)
- **README**: [javaparser-performance/README.md](./javaparser-performance/README.md)
- **运行**: `cd javaparser-performance && mvn clean compile exec:java`
- **时间**: 3-5 分钟

### 2. AWS CodeCommit 集成测试

- **目录**: [aws-codecommit/](./aws-codecommit/)
- **README**: [aws-codecommit/README.md](./aws-codecommit/README.md)
- **运行**: `cd aws-codecommit && mvn clean compile exec:java`
- **时间**: 1-5 分钟（Demo < 1 分钟）

### 3. Redis 队列并发测试

- **目录**: [redis-queue/](./redis-queue/)
- **README**: [redis-queue/README.md](./redis-queue/README.md)
- **运行**: `docker run -d -p 6379:6379 redis:latest && cd redis-queue && mvn clean compile exec:java`
- **时间**: 5-10 分钟

## 🚀 执行脚本

| 脚本 | 平台 | 命令 |
|------|------|------|
| [run-all-pocs.sh](./run-all-pocs.sh) | Linux/Mac | `./run-all-pocs.sh` |
| [run-all-pocs.bat](./run-all-pocs.bat) | Windows | `run-all-pocs.bat` |

## 📊 报告和模板

**位置**: `_bmad-output/implementation-artifacts/`

| 文档 | 描述 |
|------|------|
| [poc-execution-report.md](../../_bmad-output/implementation-artifacts/poc-execution-report.md) | 执行报告模板（需填写） |
| [poc-execution-summary.md](../../_bmad-output/implementation-artifacts/poc-execution-summary.md) | 实施方案摘要 |

## 📁 项目结构

```
backend/poc-tests/
├── INDEX.md                    # 本文档
├── QUICK_START.md              # 快速开始
├── README.md                   # 完整指南
├── run-all-pocs.sh             # Linux/Mac 脚本
├── run-all-pocs.bat            # Windows 脚本
│
├── javaparser-performance/     # PoC 1
│   ├── README.md
│   ├── pom.xml
│   └── src/main/java/com/aicr/poc/
│
├── aws-codecommit/             # PoC 2
│   ├── README.md
│   ├── pom.xml
│   └── src/main/java/com/aicr/poc/
│
└── redis-queue/                # PoC 3
    ├── README.md
    ├── pom.xml
    └── src/main/java/com/aicr/poc/
```

## 🎯 快速命令

### 一键运行所有测试

```bash
# Linux/Mac
cd backend/poc-tests && ./run-all-pocs.sh

# Windows
cd backend\poc-tests && run-all-pocs.bat
```

### 单独运行测试

```bash
# JavaParser
cd backend/poc-tests/javaparser-performance && mvn clean compile exec:java

# AWS CodeCommit (Demo)
cd backend/poc-tests/aws-codecommit && mvn clean compile exec:java

# Redis (需先启动 Redis)
docker run -d -p 6379:6379 redis:latest
cd backend/poc-tests/redis-queue && mvn clean compile exec:java
```

## 📈 预期时间

| 测试 | 时间 |
|------|------|
| JavaParser | 3-5 分钟 |
| AWS CodeCommit | 1-5 分钟 |
| Redis Queue | 5-10 分钟 |
| **全部** | **10-20 分钟** |

## ✅ 前置要求

- Java 17+
- Maven 3.6+
- Docker (可选，用于 Redis)
- AWS 凭证 (可选，用于 CodeCommit 实际测试)

---

**最后更新**: 2025-02-05
