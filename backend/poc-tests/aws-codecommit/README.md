# AWS CodeCommit Integration PoC

## 目标

验证 AWS CodeCommit GetDifferences API 的集成能力，确保可以稳定获取代码变更并满足性能要求。

## 测试场景

| 场景 | 文件数量 | 性能阈值 | 测试内容 |
|------|---------|---------|---------|
| 小型提交 | < 10 文件 | < 2s | 基本 API 调用和响应解析 |
| 中型提交 | 10-50 文件 | < 5s | 分页处理和数据聚合 |
| 大型提交 | 50+ 文件 | < 15s | 大量数据处理和性能优化 |

## 测试指标

- **API 响应时间**: GetDifferences 调用的端到端时间
- **分页处理**: 多页结果的正确处理
- **错误处理**: 重试机制和异常处理
- **数据完整性**: 文件变更的完整获取
- **限流处理**: Rate limiting 的正确响应

## 前置要求

### AWS 账户和权限

1. AWS 账户（具有 CodeCommit 访问权限）
2. IAM 用户权限:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "codecommit:GetRepository",
           "codecommit:GetDifferences",
           "codecommit:GetCommit"
         ],
         "Resource": "*"
       }
     ]
   }
   ```

### 软件要求

- Java 17+
- Maven 3.6+
- AWS CLI (可选，用于设置)

## 快速开始

### 模式 1: Demo 模式（无需 AWS 凭证）

如果你还没有 AWS 账户或想先了解测试结构:

```bash
cd backend/poc-tests/aws-codecommit
mvn clean compile exec:java
```

这会显示:
- 完整的设置说明
- 测试场景结构
- 预期输出示例
- 配置文件模板

### 模式 2: 实际测试模式

## 步骤 1: 配置 AWS 凭证

**选项 A: 使用 AWS CLI**
```bash
aws configure
# 输入:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (如 us-east-1)
# - Output format (json)
```

**选项 B: 使用环境变量**
```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
```

## 步骤 2: 创建测试仓库

```bash
# 创建 CodeCommit 仓库
aws codecommit create-repository \
  --repository-name ai-code-review-test \
  --repository-description "PoC test repository"

# 克隆仓库
git clone codecommit://ai-code-review-test
cd ai-code-review-test

# 配置 Git 凭证助手
git config --global credential.helper '!aws codecommit credential-helper $@'
git config --global credential.UseHttpPath true
```

## 步骤 3: 创建测试提交

**小型提交 (< 10 文件)**:
```bash
for i in {1..8}; do
  echo "package com.example;
public class TestClass$i {
    public void method() {
        System.out.println(\"Test $i\");
    }
}" > TestClass$i.java
done

git add .
git commit -m "Small commit - 8 files"
git push
SMALL_COMMIT=$(git rev-parse HEAD)
echo "Small commit ID: $SMALL_COMMIT"
```

**中型提交 (10-50 文件)**:
```bash
for i in {9..30}; do
  echo "package com.example;
public class TestClass$i {
    public void method() {
        System.out.println(\"Test $i\");
    }
}" > TestClass$i.java
done

git add .
git commit -m "Medium commit - 22 files"
git push
MEDIUM_COMMIT=$(git rev-parse HEAD)
echo "Medium commit ID: $MEDIUM_COMMIT"
```

**大型提交 (50+ 文件)**:
```bash
for i in {31..100}; do
  echo "package com.example;
public class TestClass$i {
    public void method() {
        System.out.println(\"Test $i\");
    }
}" > TestClass$i.java
done

git add .
git commit -m "Large commit - 70 files"
git push
LARGE_COMMIT=$(git rev-parse HEAD)
echo "Large commit ID: $LARGE_COMMIT"
```

## 步骤 4: 运行测试

使用实际的 commit ID:

```bash
cd backend/poc-tests/aws-codecommit

# 测试小型提交
export TEST_REPOSITORY=ai-code-review-test
export TEST_BEFORE_COMMIT=<PARENT_OF_SMALL_COMMIT>
export TEST_AFTER_COMMIT=<SMALL_COMMIT>
mvn clean compile exec:java

# 测试中型提交
export TEST_BEFORE_COMMIT=<SMALL_COMMIT>
export TEST_AFTER_COMMIT=<MEDIUM_COMMIT>
mvn clean compile exec:java

# 测试大型提交
export TEST_BEFORE_COMMIT=<MEDIUM_COMMIT>
export TEST_AFTER_COMMIT=<LARGE_COMMIT>
mvn clean compile exec:java
```

## 测试输出

### 控制台输出示例

```
================================================================================
AWS CodeCommit Integration Test - PoC Validation
================================================================================

Running in LIVE MODE - Using AWS credentials

Configuration:
  Repository: ai-code-review-test
  Region: us-east-1
  Before: abc123...
  After: def456...

--------------------------------------------------------------------------------
Retrieving differences...
--------------------------------------------------------------------------------
Retrieving differences (attempt 1)...
  Page 1: 8 differences

Analyzing AST structure...
  Classes: 2
  Methods: 8
  Dependencies: 5

Results:
  Total files: 8
  Added: 8
  Modified: 0
  Deleted: 0
  Java files: 8
  Duration: 1234ms
  Pages: 1

Performance:
  Threshold: 2000ms
  Result: PASS

JSON report saved to: target/codecommit-integration-report.json

================================================================================
Go/No-Go Decision
================================================================================

Results: 1/1 tests successful

Decision: GO
AWS CodeCommit integration is ready for production use.
```

### JSON 报告格式

```json
{
  "test_suite": "AWS CodeCommit Integration PoC",
  "timestamp": 1738742400000,
  "results": [
    {
      "scenarioName": "Live test",
      "repositoryName": "ai-code-review-test",
      "success": true,
      "passed": true,
      "durationMs": 1234,
      "thresholdMs": 2000,
      "totalFiles": 8,
      "addedFiles": 8,
      "modifiedFiles": 0,
      "deletedFiles": 0,
      "javaFiles": 8,
      "pageCount": 1
    }
  ]
}
```

## Go/No-Go 标准

### GO 条件（全部满足）

1. **连接成功**:
   - 成功认证到 AWS
   - 可以访问 CodeCommit 仓库
   - API 调用正常响应

2. **功能完整**:
   - 正确获取所有文件差异
   - 分页处理正确（如果需要）
   - 文件类型识别准确

3. **性能达标**:
   - 小型提交 < 2s
   - 中型提交 < 5s
   - 大型提交 < 15s

4. **错误处理**:
   - 重试机制正常工作
   - 限流处理正确
   - 异常情况有明确错误信息

### NO-GO 触发条件

1. 无法连接到 AWS CodeCommit
2. API 调用频繁失败或超时
3. 分页处理错误（数据缺失）
4. 性能超过阈值 200% 以上
5. 无法正确识别 Java 文件

## 预期结果

基于 AWS CodeCommit 的典型性能:

| 场景 | 预期时间 | 预期行为 |
|------|---------|---------|
| 小型 (< 10 文件) | 0.5-1.5s | 单页响应，无分页 |
| 中型 (10-50 文件) | 1-3s | 可能需要 1-2 页 |
| 大型 (50+ 文件) | 3-10s | 需要多页，测试分页逻辑 |

## 故障排查

### 认证失败

```bash
# 检查 AWS CLI 配置
aws configure list

# 测试 AWS 连接
aws codecommit list-repositories

# 检查凭证文件
cat ~/.aws/credentials
```

### Repository not found

```bash
# 列出所有仓库
aws codecommit list-repositories

# 检查仓库详情
aws codecommit get-repository --repository-name ai-code-review-test
```

### Commit ID 无效

```bash
# 获取仓库的最近提交
aws codecommit get-branch \
  --repository-name ai-code-review-test \
  --branch-name main

# 获取提交历史
aws codecommit get-commit-history \
  --repository-name ai-code-review-test
```

### 限流 (429 Too Many Requests)

- AWS CodeCommit 有 API 限流
- 测试代码已包含重试逻辑
- 如频繁触发，增加重试延迟

## 下一步

### 如果测试通过 (GO)

1. 集成到主项目:
   ```xml
   <dependency>
       <groupId>software.amazon.awssdk</groupId>
       <artifactId>codecommit</artifactId>
       <version>2.23.9</version>
   </dependency>
   ```

2. 创建 `CodeCommitService`:
   - 封装 GetDifferences API
   - 实现分页和重试
   - 添加缓存机制

3. 实现 Webhook 处理:
   - 接收 CodeCommit 事件
   - 触发代码审查流程

### 如果测试失败 (NO-GO)

1. **认证问题**: 检查 IAM 权限和凭证
2. **性能问题**: 考虑增加缓存或异步处理
3. **API 限制**: 评估是否需要 API Gateway 或限流策略

## 清理资源

测试完成后清理 AWS 资源:

```bash
# 删除测试仓库
aws codecommit delete-repository \
  --repository-name ai-code-review-test
```

## 参考资料

- [AWS CodeCommit API Reference](https://docs.aws.amazon.com/codecommit/latest/APIReference/Welcome.html)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [CodeCommit GetDifferences](https://docs.aws.amazon.com/codecommit/latest/APIReference/API_GetDifferences.html)
