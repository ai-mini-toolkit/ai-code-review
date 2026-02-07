# JavaParser Performance Benchmark Test Plan

**Document Version**: 1.0
**Date**: 2026-02-05
**Project**: ai-code-review
**Purpose**: Validate JavaParser performance for call graph analysis in production scenarios

---

## 1. Executive Summary

This document defines a comprehensive performance testing plan for JavaParser-based call graph analysis. The goal is to validate that JavaParser can meet the NFR requirement of "single review < 30s per 100 lines of code" when analyzing Java code complexity ranging from small to large codebases.

### Test Objectives

1. Measure JavaParser parsing and call graph analysis performance across different code scales
2. Identify performance bottlenecks and resource constraints
3. Define acceptance criteria for production deployment
4. Establish degradation strategies if performance targets are not met

---

## 2. Test Scenarios

### Scenario 1: Small Codebase (100 lines)
**Description**: Single Java file with 1-2 classes, 5-8 methods, 2-3 method calls per method

**Test Case Details**:
- **File Count**: 1
- **Total Lines**: 100 lines
- **Classes**: 1-2
- **Methods**: 5-8
- **Method Calls**: 15-20
- **Max Call Depth**: 3 levels
- **Expected Use Case**: Bug fix, small feature addition

**Sample Code Characteristics**:
```java
// Example: Simple service class
class UserService {
    private UserRepository repo;

    public User findUser(Long id) {
        return repo.findById(id);
    }

    public void updateUser(User user) {
        validateUser(user);
        repo.save(user);
        notifyUpdate(user);
    }

    private void validateUser(User user) { ... }
    private void notifyUpdate(User user) { ... }
}
```

---

### Scenario 2: Medium Codebase (500 lines)
**Description**: 3-5 Java files with multiple classes, moderate method complexity

**Test Case Details**:
- **File Count**: 3-5
- **Total Lines**: 500 lines
- **Classes**: 5-8
- **Methods**: 30-50
- **Method Calls**: 80-120
- **Max Call Depth**: 5 levels
- **Expected Use Case**: Feature module development, refactoring

**Sample Code Characteristics**:
- Service layer with business logic
- Repository interfaces with implementations
- DTO and entity classes
- Inter-class method calls

---

### Scenario 3: Large Codebase (1000 lines)
**Description**: 8-12 Java files forming a complete feature module

**Test Case Details**:
- **File Count**: 8-12
- **Total Lines**: 1000 lines
- **Classes**: 15-20
- **Methods**: 80-120
- **Method Calls**: 200-300
- **Max Call Depth**: 7 levels
- **Expected Use Case**: Large feature implementation, multi-class refactoring

**Sample Code Characteristics**:
- Controller → Service → Repository layered architecture
- Multiple service classes with dependencies
- Exception handling and validation logic
- Complex call chains

---

### Scenario 4: Enterprise Codebase (5000 lines)
**Description**: 30-50 Java files representing a microservice or large module

**Test Case Details**:
- **File Count**: 30-50
- **Total Lines**: 5000 lines
- **Classes**: 60-100
- **Methods**: 300-500
- **Method Calls**: 1000-2000
- **Max Call Depth**: 10 levels
- **Expected Use Case**: Large-scale refactoring, architectural changes

**Sample Code Characteristics**:
- Complete microservice with all layers
- Complex dependency injection
- Multiple design patterns (Factory, Strategy, Chain of Responsibility)
- Cross-module dependencies

---

## 3. Performance Metrics

### Primary Metrics

| Metric | Description | Measurement Method |
|--------|-------------|-------------------|
| **Parse Time** | Time to parse Java source code into AST | System.nanoTime() before/after parse |
| **Symbol Resolution Time** | Time to resolve symbols and build symbol table | Measure `SymbolResolver.resolve()` duration |
| **Call Graph Build Time** | Time to traverse AST and construct call graph | Measure custom call graph builder duration |
| **Total Processing Time** | End-to-end time from source code to call graph JSON | Sum of all above + serialization |
| **Memory Usage (Peak)** | Maximum heap memory during processing | Runtime.totalMemory() - Runtime.freeMemory() |
| **Memory Usage (Average)** | Average heap memory during processing | Sample every 100ms and calculate average |
| **CPU Usage** | CPU utilization percentage | OperatingSystemMXBean.getProcessCpuLoad() |
| **Call Graph Accuracy** | Percentage of correctly identified method calls | Manual validation against ground truth |

### Secondary Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| **AST Node Count** | Total AST nodes created | Track for correlation analysis |
| **Symbol Table Size** | Number of symbols in symbol table | Track for memory analysis |
| **Call Graph Node Count** | Number of nodes in call graph | Expected to match method count |
| **Call Graph Edge Count** | Number of edges (method calls) | Expected to match call count |
| **GC Count** | Number of garbage collection cycles | Should be minimal |
| **GC Time** | Total time spent in GC | Should be < 10% of total time |

---

## 4. Acceptance Criteria

### Performance Targets

| Scenario | Code Size | Total Time Target | Memory Target | Call Graph Accuracy |
|----------|-----------|-------------------|---------------|-------------------|
| Scenario 1 | 100 lines | **< 1 second** | < 50 MB | ≥ 95% |
| Scenario 2 | 500 lines | **< 3 seconds** | < 150 MB | ≥ 95% |
| Scenario 3 | 1000 lines | **< 5 seconds** | < 300 MB | ≥ 90% |
| Scenario 4 | 5000 lines | **< 20 seconds** | < 1 GB | ≥ 85% |

### NFR Alignment

**NFR 1 Requirement**: "Single review < 30s per 100 lines of code"

- Scenario 1 (100 lines): 1s → **Meets requirement** (1s < 30s)
- Scenario 2 (500 lines): 3s → **Meets requirement** (0.6s per 100 lines)
- Scenario 3 (1000 lines): 5s → **Meets requirement** (0.5s per 100 lines)
- Scenario 4 (5000 lines): 20s → **Meets requirement** (0.4s per 100 lines)

### Pass/Fail Criteria

| Criterion | Pass Condition | Fail Condition |
|-----------|----------------|----------------|
| **Performance** | All scenarios meet time targets | Any scenario exceeds target by > 50% |
| **Memory** | Peak memory < target for all scenarios | OutOfMemoryError in any scenario |
| **Accuracy** | Call graph accuracy ≥ target | Accuracy < 80% in any scenario |
| **Stability** | 10 consecutive runs with < 10% variance | Standard deviation > 20% |

---

## 5. Test Environment

### Hardware Requirements

- **CPU**: 4 cores, 2.5 GHz minimum
- **RAM**: 8 GB minimum
- **Disk**: SSD for fast I/O
- **OS**: Linux (Ubuntu 22.04) or macOS (for CI/CD consistency)

### Software Requirements

- **JDK**: OpenJDK 17 or later
- **JavaParser Version**: 3.25.x (latest stable)
- **Build Tool**: Maven 3.8+ or Gradle 8+
- **Testing Framework**: JUnit 5 + JMH (Java Microbenchmark Harness)
- **Profiling Tools**: JVisualVM, YourKit Java Profiler (optional)

### Test Data Preparation

**Data Sources**:
1. **Synthetic Test Data**: Generate controlled test cases with known complexity
2. **Real-World Samples**: Extract code samples from open-source Java projects (Spring Boot, Apache Commons)
3. **Ground Truth**: Manually verify call graphs for accuracy validation

**Data Storage**:
```
test-data/
├── scenario-1-small/
│   ├── Sample1.java (100 lines)
│   └── expected-call-graph.json
├── scenario-2-medium/
│   ├── Service.java (200 lines)
│   ├── Repository.java (150 lines)
│   ├── Model.java (150 lines)
│   └── expected-call-graph.json
├── scenario-3-large/
│   └── [8-12 files, 1000 lines total]
└── scenario-4-enterprise/
    └── [30-50 files, 5000 lines total]
```

---

## 6. Test Implementation

### Test Code Structure

```java
@State(Scope.Benchmark)
public class JavaParserPerformanceBenchmark {

    @Param({"100", "500", "1000", "5000"})
    private int codeLines;

    private List<Path> sourceFiles;
    private CallGraphAnalyzer analyzer;

    @Setup
    public void setup() {
        sourceFiles = loadTestFiles(codeLines);
        analyzer = new CallGraphAnalyzer();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 3, time = 1)
    @Measurement(iterations = 10, time = 1)
    @Fork(1)
    public CallGraph benchmarkCallGraphAnalysis() {
        return analyzer.analyze(sourceFiles);
    }

    @TearDown
    public void tearDown() {
        // Log memory usage
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        System.out.println("Peak Memory: " +
            heapUsage.getUsed() / (1024 * 1024) + " MB");
    }
}
```

### Measurement Methodology

**Warm-up Phase**:
- Run 3 warm-up iterations to allow JIT compilation
- Discard warm-up results from final metrics

**Measurement Phase**:
- Run 10 iterations per scenario
- Calculate: mean, median, p95, p99, standard deviation
- Record outliers for investigation

**Memory Profiling**:
- Use `-Xmx2g -XX:+PrintGCDetails -XX:+PrintGCTimeStamps`
- Sample heap usage every 100ms during processing
- Generate heap dump if OutOfMemoryError occurs

**Accuracy Validation**:
- Compare generated call graph with expected call graph (ground truth)
- Calculate precision, recall, F1 score
- Manually inspect mismatches

---

## 7. Degradation Strategies

### Strategy 1: Partial Call Graph (Recommended)

**Trigger Condition**: Processing time > 5s for 1000 lines or memory > 500 MB

**Implementation**:
- Analyze only changed files instead of entire codebase
- Build "local call graph" showing 2-level call depth from changed methods
- Skip symbol resolution for unchanged files

**Trade-offs**:
- Pros: 70-80% performance improvement, still provides value
- Cons: Missing cross-file call chains beyond 2 levels

**Example Output**:
```json
{
  "type": "partial",
  "scope": "changed-files-only",
  "call_graph": { ... },
  "warning": "Call graph limited to 2-level depth from changed methods"
}
```

---

### Strategy 2: Disable Call Graph for Large Changes

**Trigger Condition**: Total lines changed > 2000 or file count > 20

**Implementation**:
- Skip call graph analysis entirely
- Return placeholder message
- Proceed with other 5 review dimensions

**Trade-offs**:
- Pros: No performance impact, no risk of timeout
- Cons: No call graph value for large changes

**Example Output**:
```json
{
  "type": "disabled",
  "reason": "codebase-too-large",
  "message": "Call graph analysis skipped for changes > 2000 lines. Other review dimensions completed."
}
```

---

### Strategy 3: Async Call Graph Generation

**Trigger Condition**: All scenarios (default enhancement for Phase 2)

**Implementation**:
- Return review results immediately without call graph
- Generate call graph asynchronously in background
- Update report when call graph is ready (WebSocket notification)

**Trade-offs**:
- Pros: Fast initial response, no blocking, better UX
- Cons: Requires WebSocket infrastructure, more complex

---

## 8. Risk Assessment

### High Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **OutOfMemoryError in Scenario 4** | 60% | High | Implement Strategy 1 (partial call graph) |
| **Symbol resolution too slow** | 50% | High | Use JavaParser TypeSolver cache, disable unused resolvers |
| **Call graph accuracy < 80%** | 40% | High | Improve AST traversal logic, add test cases |
| **Performance varies by JVM version** | 30% | Medium | Test on OpenJDK 17, 21, GraalVM |

### Medium Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **GC pauses impact latency** | 40% | Medium | Tune JVM flags (-XX:+UseG1GC, -XX:MaxGCPauseMillis=100) |
| **Test data not representative** | 30% | Medium | Use real-world open-source projects as test data |
| **Benchmark variance > 20%** | 25% | Low | Increase measurement iterations to 20 |

---

## 9. Success Criteria Summary

### Must-Have (P0)

- ✅ Scenario 1 (100 lines) < 1s, 95% accuracy
- ✅ Scenario 2 (500 lines) < 3s, 95% accuracy
- ✅ Scenario 3 (1000 lines) < 5s, 90% accuracy
- ✅ No OutOfMemoryError in any scenario with 2GB heap

### Should-Have (P1)

- ✅ Scenario 4 (5000 lines) < 20s, 85% accuracy
- ✅ Performance variance < 10% across 10 runs
- ✅ Degradation strategy implemented and tested

### Nice-to-Have (P2)

- 🔲 Async call graph generation (Phase 2)
- 🔲 Multi-language support with Tree-sitter (Phase 2)
- 🔲 Call graph caching for unchanged files

---

## 10. Deliverables

### Test Code
- [ ] JMH benchmark suite (JavaParserPerformanceBenchmark.java)
- [ ] Test data generator (TestDataGenerator.java)
- [ ] Ground truth validator (CallGraphValidator.java)

### Test Data
- [ ] Scenario 1-4 test files (100, 500, 1000, 5000 lines)
- [ ] Expected call graphs for accuracy validation

### Test Reports
- [ ] Performance benchmark report (JSON + HTML)
- [ ] Memory profiling report (heap dump analysis)
- [ ] Accuracy validation report (precision/recall metrics)

### Decision Document
- [ ] Go/No-Go decision based on test results
- [ ] Degradation strategy implementation plan (if needed)
- [ ] Recommendation for Phase 1 scope (full vs partial call graph)

---

## 11. Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| **Preparation** | 1 day | Setup test environment, prepare test data |
| **Test Implementation** | 1 day | Write JMH benchmarks, implement validators |
| **Test Execution** | 0.5 day | Run all scenarios, collect metrics |
| **Analysis** | 0.5 day | Analyze results, validate accuracy |
| **Decision** | 0.5 day | Document findings, make go/no-go decision |
| **Total** | **3.5 days** | |

---

## 12. Appendix

### A. JavaParser Configuration Options

```java
// Optimize for performance
ParserConfiguration config = new ParserConfiguration()
    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
    .setSymbolResolver(new JavaSymbolSolver(typeSolver))
    .setAttributeComments(false)  // Disable if not needed
    .setLexicalPreservationEnabled(false);  // Disable if not modifying AST

// TypeSolver with cache
CombinedTypeSolver typeSolver = new CombinedTypeSolver();
typeSolver.add(new ReflectionTypeSolver());
typeSolver.add(new JavaParserTypeSolver(sourceRoot));
```

### B. Sample Call Graph JSON Format

```json
{
  "version": "1.0",
  "generated_at": "2026-02-05T10:30:00Z",
  "statistics": {
    "total_classes": 5,
    "total_methods": 30,
    "total_calls": 80
  },
  "nodes": [
    {
      "id": "UserService.findUser",
      "class": "UserService",
      "method": "findUser",
      "signature": "User findUser(Long)",
      "file": "UserService.java",
      "line": 15
    }
  ],
  "edges": [
    {
      "from": "UserService.findUser",
      "to": "UserRepository.findById",
      "call_type": "method_call",
      "line": 16
    }
  ]
}
```

### C. Reference Benchmarks

**Expected Performance (based on JavaParser 3.25.x)**:
- Simple parsing (no symbol resolution): ~1000 lines/second
- With symbol resolution: ~300-500 lines/second
- Call graph construction: ~200-400 lines/second

**Memory Usage**:
- AST memory overhead: ~10-15 KB per class
- Symbol table overhead: ~5-10 KB per class
- Call graph overhead: ~2-5 KB per method

---

**Document Prepared By**: Technical Lead
**Review Status**: Draft
**Next Review Date**: After test execution (Day 4)
