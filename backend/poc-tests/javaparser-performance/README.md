# JavaParser Performance PoC

## 目标

验证 JavaParser 库是否满足 AI Code Review 项目的性能要求，测试不同规模文件的解析性能。

## 测试场景

| 场景 | 文件大小 | 性能阈值 | 测试内容 |
|------|---------|---------|---------|
| 小型文件 | 100 行 | < 100ms | 基础解析和 AST 遍历 |
| 中型文件 | 500 行 | < 300ms | 方法提取和依赖分析 |
| 大型文件 | 1000 行 | < 500ms | 完整分析流程 |
| 超大文件 | 5000 行 | < 2000ms | 压力测试和内存监控 |

## 测试指标

- **解析时间**: 从代码字符串到 AST 的时间
- **内存使用**: 解析过程的内存消耗
- **吞吐量**: 每秒可处理的代码行数
- **AST 分析**: 类、方法、依赖关系提取
- **循环依赖检测**: 简化版的循环调用检测

## 前置要求

- Java 17 或更高版本
- Maven 3.6+

## 如何运行

### 1. 一键运行完整测试

```bash
cd backend/poc-tests/javaparser-performance
mvn clean compile exec:java
```

### 2. 分步执行

**生成测试文件**:
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.aicr.poc.TestCodeGenerator"
```

**运行性能测试**:
```bash
mvn exec:java -Dexec.mainClass="com.aicr.poc.JavaParserPerformanceTest"
```

### 3. 构建可执行 JAR

```bash
mvn clean package
java -jar target/javaparser-performance-1.0-SNAPSHOT.jar
```

## 测试输出

### 控制台输出

测试会输出详细的性能指标:
- 每次迭代的解析时间和内存使用
- 平均/最小/最大解析时间
- AST 结构分析（类、方法、依赖数量）
- Go/No-Go 决策结果

### JSON 报告

测试结果会保存到 `target/javaparser-performance-report.json`:

```json
{
  "test_suite": "JavaParser Performance PoC",
  "timestamp": 1738742400000,
  "results": [
    {
      "test_name": "JavaParser-100-lines",
      "file_size": "2.5 KB",
      "line_count": 102,
      "avg_parse_time_ms": 45.2,
      "memory_used_mb": 2.3,
      "method_count": 8,
      "class_count": 2,
      "success": true
    }
  ]
}
```

## Go/No-Go 标准

### GO 条件（全部满足）

1. **性能达标**:
   - 100 行文件: < 100ms
   - 1000 行文件: < 500ms
   - 5000 行文件: < 2000ms

2. **内存可控**:
   - 单次解析内存 < 500MB

3. **功能完整**:
   - 成功解析所有测试文件
   - 正确提取类和方法信息
   - 能够分析依赖关系

### NO-GO 触发条件（任一发生）

1. 任何测试用例解析失败
2. 性能超过阈值 50% 以上
3. 内存使用超过 500MB
4. 无法提取基本代码结构信息

## 预期结果

基于 JavaParser 3.25.8 的性能表现，预期结果如下:

| 文件大小 | 预期解析时间 | 预期内存 | 预期吞吐量 |
|---------|------------|---------|-----------|
| 100 行 | 20-50ms | 1-3MB | 2000-5000 行/秒 |
| 500 行 | 80-150ms | 3-8MB | 3000-6000 行/秒 |
| 1000 行 | 150-300ms | 5-15MB | 3000-7000 行/秒 |
| 5000 行 | 500-1500ms | 20-50MB | 3000-10000 行/秒 |

## 结果解读

### 性能优秀 (GO)

- 所有测试用例通过
- 解析时间远低于阈值
- 内存使用稳定可控
- 可以进入下一阶段开发

### 性能可接受 (GO with caution)

- 大部分测试通过
- 个别场景接近阈值但未超过
- 需要在实际开发中持续监控
- 建议进行优化

### 性能不达标 (NO-GO)

- 多个测试用例失败或超时
- 内存使用过高
- 需要考虑替代方案:
  - 使用其他解析库 (Eclipse JDT, Spoon)
  - 优化解析策略（增量解析、缓存）
  - 调整架构设计

## 故障排查

### Maven 依赖问题

```bash
mvn dependency:tree
mvn dependency:resolve
```

### 内存不足

增加 JVM 堆内存:
```bash
export MAVEN_OPTS="-Xmx2g"
mvn exec:java
```

### 测试文件未生成

手动运行生成器:
```bash
mvn exec:java -Dexec.mainClass="com.aicr.poc.TestCodeGenerator"
```

## 下一步

### 如果测试通过 (GO)

1. 将 JavaParser 集成到主项目 `pom.xml`
2. 创建代码分析服务 (`CodeAnalysisService`)
3. 实现以下功能:
   - 解析 Java 文件并提取方法
   - 分析调用链路
   - 检测代码异味和潜在问题
4. 编写单元测试和集成测试

### 如果测试失败 (NO-GO)

1. 分析失败原因（性能/内存/功能）
2. 评估是否可以通过优化解决
3. 如无法优化，考虑替代方案:
   - **Eclipse JDT**: 更强大但复杂度更高
   - **Spoon**: 专注于代码分析和转换
   - **混合方案**: 使用正则表达式预处理 + JavaParser

## 参考资料

- [JavaParser 官方文档](https://javaparser.org/)
- [JavaParser GitHub](https://github.com/javaparser/javaparser)
- [AST 遍历指南](https://github.com/javaparser/javaparser/wiki/Manual)
