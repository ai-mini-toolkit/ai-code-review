# Story 10.1: 性能测试基准数据集准备

Status: done

## Story

作为性能工程师，
我想要准备标准化的性能测试数据集，
以便可重复、可对比地进行性能测试。

## Acceptance Criteria

1. **代码规模测试集（4 个级别）**：创建 4 个 `.diff` 文件，覆盖不同代码变更规模：
   - `small/100-lines.diff` — 1-2 文件、100 行、单方法修改、Java
   - `medium/500-lines.diff` — 5-8 文件、500 行、功能模块开发
   - `large/1000-lines.diff` — 10-15 文件、1000 行、大型重构
   - `xlarge/5000-lines.diff` — 30-50 文件、5000 行、模块重写

2. **预期结果文件**：每个 diff 配套 `expected-issues.json`，定义预期检出的 issue 数量范围和 severity 分布，用于后续性能测试的断言基准。

3. **数据集目录结构**：在仓库中创建标准化目录：
   ```
   backend/ai-code-review-api/src/test/resources/performance-test-data/
   ├── small/
   │   ├── 100-lines.diff
   │   └── expected-issues.json
   ├── medium/
   │   ├── 500-lines.diff
   │   └── expected-issues.json
   ├── large/
   │   ├── 1000-lines.diff
   │   └── expected-issues.json
   └── xlarge/
       ├── 5000-lines.diff
       └── expected-issues.json
   ```

4. **数据加载工具类**：创建 `PerformanceTestDataLoader` 工具类，可从资源目录加载指定级别的 diff 和预期结果，供 Story 10.2-10.4 的性能测试使用。

## Tasks / Subtasks

- [x] Task 1: 创建 diff 测试数据文件 (AC: #1)
  - [x] 1.1 `small/100-lines.diff` — 98 行, 3KB, CRUD service refactoring (2 files)
  - [x] 1.2 `medium/500-lines.diff` — 522 行, 17KB, service layer changes (6 files)
  - [x] 1.3 `large/1000-lines.diff` — 1044 行, 34KB, multi-file refactoring (12 files)
  - [x] 1.4 `xlarge/5000-lines.diff` — 5160 行, 166KB, module rewrite (40 files)

- [x] Task 2: 创建预期结果文件 (AC: #2)
  - [x] 2.1 每个 level 配套 `expected-issues.json`（含 minIssues/maxIssues/severity 分布/性能目标）

- [x] Task 3: 创建 PerformanceTestDataLoader (AC: #4)
  - [x] 3.1 `ClassPathResource` 加载 diff 文件 + JSON
  - [x] 3.2 Java `record Dataset` 封装加载结果
  - [x] 3.3 `loadDataset("small"|"medium"|"large"|"xlarge")` 方法

- [x] Task 4: 验证 (AC: #1-4)
  - [x] 4.1 全部 4 个级别文件存在且 JSON 格式有效
  - [x] 4.2 全量 E2E 回归 49/49 通过（无回归）

## Dev Notes

### 关键实现提示

**1. Diff 文件格式**

使用标准 unified diff 格式：
```diff
diff --git a/src/main/java/Example.java b/src/main/java/Example.java
--- a/src/main/java/Example.java
+++ b/src/main/java/Example.java
@@ -1,10 +1,15 @@
 public class Example {
+    // Added method
+    public void newMethod() {
+        // ...
+    }
 }
```

**2. expected-issues.json 格式**

```json
{
  "minIssues": 0,
  "maxIssues": 5,
  "expectedSeverityDistribution": {
    "CRITICAL": 0,
    "HIGH": {"min": 0, "max": 2},
    "MEDIUM": {"min": 1, "max": 3},
    "LOW": {"min": 0, "max": 5}
  },
  "performanceTarget": {
    "maxReviewTimeMs": 10000
  }
}
```

**3. PerformanceTestDataLoader 位置**

```
backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/PerformanceTestDataLoader.java
```

使用 Spring 的 `ClassPathResource` 加载测试资源文件。

**4. 数据集不需要真实 AI 审查结果**

这些 diff 文件用于性能测试（测量审查耗时），不需要 AI 真正分析代码质量。MockAIProvider 或 ConfigurableMockAIProvider 会返回固定结果。性能测试关注的是**管道吞吐量**而非 AI 准确性。

### References

- [Source: backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/] — E2E 测试 support 目录
- [Source: backend/ai-code-review-api/src/test/resources/] — 测试资源目录

## Dev Agent Record

### Agent Model Used

claude-opus-4-6

### Debug Log References

N/A

### Completion Notes List

1. **Diff 文件使用标准 unified diff 格式**：small (手工编写，真实 CRUD 重构)，medium/large/xlarge (脚本生成，多文件 Service 类)。
2. **PerformanceTestDataLoader 使用 Java record**：`Dataset` record 封装 level、diff、expectedIssues、性能目标。ClassPathResource 加载资源。
3. **预期结果范围化**：expected-issues.json 使用 min/max 范围而非精确值，因为 AI 审查结果不确定性高。

### File List

- `backend/ai-code-review-api/src/test/resources/performance-test-data/small/100-lines.diff` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/small/expected-issues.json` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/medium/500-lines.diff` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/medium/expected-issues.json` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/large/1000-lines.diff` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/large/expected-issues.json` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/xlarge/5000-lines.diff` — NEW
- `backend/ai-code-review-api/src/test/resources/performance-test-data/xlarge/expected-issues.json` — NEW
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/e2e/support/PerformanceTestDataLoader.java` — NEW

## Change Log

- 2026-02-23: 实现完成，4 个级别性能测试数据集 + PerformanceTestDataLoader 工具类创建
