package com.aicr.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * JavaParser Performance Test
 * Tests parsing performance on files of varying sizes (100, 500, 1000, 5000 lines)
 * Measures: parse time, memory usage, AST traversal, dependency analysis
 */
public class JavaParserPerformanceTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 5;

    // Go/No-Go thresholds
    private static final long MAX_PARSE_TIME_100_LINES_MS = 100;    // 100ms for 100 lines
    private static final long MAX_PARSE_TIME_1000_LINES_MS = 500;   // 500ms for 1000 lines
    private static final long MAX_PARSE_TIME_5000_LINES_MS = 2000;  // 2s for 5000 lines
    private static final double MAX_MEMORY_MB = 500.0;              // 500MB max memory

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("JavaParser Performance Test - PoC Validation");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            // Step 1: Generate test files if they don't exist
            generateTestFiles();

            // Step 2: Run performance tests
            List<PerformanceMetrics> allMetrics = new ArrayList<>();
            int[] testSizes = {100, 500, 1000, 5000};

            for (int size : testSizes) {
                System.out.println("\n" + "-".repeat(80));
                System.out.println("Testing file size: " + size + " lines");
                System.out.println("-".repeat(80));

                File testFile = new File("src/test/resources/sample-" + size + "-lines.java");
                PerformanceMetrics metrics = runPerformanceTest(testFile, size);
                allMetrics.add(metrics);

                printMetricsSummary(metrics);
            }

            // Step 3: Generate JSON report
            saveJsonReport(allMetrics);

            // Step 4: Evaluate Go/No-Go decision
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Go/No-Go Decision Analysis");
            System.out.println("=".repeat(80));
            evaluateGoNoGo(allMetrics);

        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Generate test files if they don't exist
     */
    private static void generateTestFiles() throws IOException {
        System.out.println("Checking for test files...");
        File resourceDir = new File("src/test/resources");
        resourceDir.mkdirs();

        int[] sizes = {100, 500, 1000, 5000};
        boolean needsGeneration = false;

        for (int size : sizes) {
            File file = new File(resourceDir, "sample-" + size + "-lines.java");
            if (!file.exists()) {
                needsGeneration = true;
                break;
            }
        }

        if (needsGeneration) {
            System.out.println("Generating test files...");
            TestCodeGenerator.main(new String[]{});
        } else {
            System.out.println("Test files already exist.");
        }
        System.out.println();
    }

    /**
     * Run performance test on a single file
     */
    private static PerformanceMetrics runPerformanceTest(File testFile, int expectedLines) throws IOException {
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setTestName("JavaParser-" + expectedLines + "-lines");
        metrics.setTimestamp(System.currentTimeMillis());
        metrics.setIterations(TEST_ITERATIONS);

        // Read file
        String code = Files.readString(testFile.toPath());
        int actualLines = code.split("\n").length;
        long fileSize = testFile.length();

        metrics.setLineCount(actualLines);
        metrics.setFileSize(String.format("%.2f KB", fileSize / 1024.0));

        try {
            JavaParser parser = new JavaParser();

            // Warmup
            System.out.println("Warming up (" + WARMUP_ITERATIONS + " iterations)...");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                parser.parse(code);
            }

            // Performance test iterations
            System.out.println("Running performance test (" + TEST_ITERATIONS + " iterations)...");
            List<Long> parseTimes = new ArrayList<>();
            List<Double> memoryUsages = new ArrayList<>();

            CompilationUnit lastCu = null;

            for (int i = 0; i < TEST_ITERATIONS; i++) {
                // Memory before
                Runtime runtime = Runtime.getRuntime();
                runtime.gc();
                long memBefore = runtime.totalMemory() - runtime.freeMemory();

                // Parse
                long startTime = System.nanoTime();
                ParseResult<CompilationUnit> result = parser.parse(code);
                long endTime = System.nanoTime();

                // Memory after
                runtime.gc();
                long memAfter = runtime.totalMemory() - runtime.freeMemory();

                long parseTime = (endTime - startTime) / 1_000_000; // Convert to ms
                double memUsed = (memAfter - memBefore) / (1024.0 * 1024.0); // Convert to MB

                parseTimes.add(parseTime);
                memoryUsages.add(memUsed);

                if (result.isSuccessful() && result.getResult().isPresent()) {
                    lastCu = result.getResult().get();
                }

                System.out.println("  Iteration " + (i + 1) + ": " + parseTime + "ms, Memory: " + String.format("%.2f MB", memUsed));
            }

            // Calculate statistics
            metrics.setParseTimeMs(parseTimes.stream().mapToLong(Long::longValue).sum() / parseTimes.size());
            metrics.setAvgParseTimeMs(parseTimes.stream().mapToLong(Long::longValue).average().orElse(0));
            metrics.setMinParseTimeMs(parseTimes.stream().mapToLong(Long::longValue).min().orElse(0));
            metrics.setMaxParseTimeMs(parseTimes.stream().mapToLong(Long::longValue).max().orElse(0));
            metrics.setMemoryUsedMb(memoryUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            metrics.calculateThroughput();

            // Analyze AST
            if (lastCu != null) {
                System.out.println("\nAnalyzing AST structure...");
                analyzeAST(lastCu, metrics);
            }

            metrics.setSuccess(true);

        } catch (Exception e) {
            metrics.setSuccess(false);
            metrics.setErrorMessage(e.getMessage());
            System.err.println("Error during test: " + e.getMessage());
        }

        return metrics;
    }

    /**
     * Analyze AST to extract metrics
     */
    private static void analyzeAST(CompilationUnit cu, PerformanceMetrics metrics) {
        // Count classes
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        metrics.setClassCount(classes.size());

        // Count methods
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        metrics.setMethodCount(methods.size());

        // Analyze dependencies (simplified)
        Set<String> dependencies = new HashSet<>();
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            dependencies.add(type.getNameAsString());
        });
        metrics.setDependencyCount(dependencies.size());

        // Detect circular dependencies (simplified - just count potential cycles)
        Map<String, Set<String>> methodCallGraph = new HashMap<>();
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            Set<String> calls = new HashSet<>();
            method.findAll(MethodCallExpr.class).forEach(call -> {
                calls.add(call.getNameAsString());
            });
            methodCallGraph.put(methodName, calls);
        });

        // Simple cycle detection: count methods that call methods that call them back
        int potentialCycles = 0;
        for (Map.Entry<String, Set<String>> entry : methodCallGraph.entrySet()) {
            String method = entry.getKey();
            Set<String> calls = entry.getValue();
            for (String calledMethod : calls) {
                if (methodCallGraph.containsKey(calledMethod) &&
                        methodCallGraph.get(calledMethod).contains(method)) {
                    potentialCycles++;
                }
            }
        }
        metrics.setCircularDependencies(potentialCycles / 2); // Divide by 2 to avoid double counting

        System.out.println("  Classes: " + metrics.getClassCount());
        System.out.println("  Methods: " + metrics.getMethodCount());
        System.out.println("  Dependencies: " + metrics.getDependencyCount());
        System.out.println("  Potential circular dependencies: " + metrics.getCircularDependencies());
    }

    /**
     * Print metrics summary
     */
    private static void printMetricsSummary(PerformanceMetrics metrics) {
        System.out.println("\n--- Test Results Summary ---");
        System.out.println("Test: " + metrics.getTestName());
        System.out.println("File Size: " + metrics.getFileSize());
        System.out.println("Line Count: " + metrics.getLineCount());
        System.out.println("Avg Parse Time: " + String.format("%.2f ms", metrics.getAvgParseTimeMs()));
        System.out.println("Min/Max Parse Time: " + metrics.getMinParseTimeMs() + "/" + metrics.getMaxParseTimeMs() + " ms");
        System.out.println("Memory Used: " + String.format("%.2f MB", metrics.getMemoryUsedMb()));
        System.out.println("Throughput: " + String.format("%.0f lines/sec", metrics.getThroughputLinesPerSec()));
        System.out.println("Success: " + metrics.isSuccess());
    }

    /**
     * Save JSON report
     */
    private static void saveJsonReport(List<PerformanceMetrics> allMetrics) throws IOException {
        File reportFile = new File("target/javaparser-performance-report.json");
        reportFile.getParentFile().mkdirs();

        Map<String, Object> report = new HashMap<>();
        report.put("test_suite", "JavaParser Performance PoC");
        report.put("timestamp", System.currentTimeMillis());
        report.put("results", allMetrics);

        objectMapper.writeValue(reportFile, report);
        System.out.println("\nJSON report saved to: " + reportFile.getAbsolutePath());
    }

    /**
     * Evaluate Go/No-Go decision
     */
    private static void evaluateGoNoGo(List<PerformanceMetrics> allMetrics) {
        boolean allPassed = true;
        List<String> failures = new ArrayList<>();

        for (PerformanceMetrics metrics : allMetrics) {
            if (!metrics.isSuccess()) {
                allPassed = false;
                failures.add(metrics.getTestName() + ": Test failed - " + metrics.getErrorMessage());
                continue;
            }

            // Check thresholds based on line count
            long threshold = getThreshold(metrics.getLineCount());
            if (metrics.getAvgParseTimeMs() > threshold) {
                allPassed = false;
                failures.add(String.format("%s: Parse time %.2fms exceeds threshold %dms",
                        metrics.getTestName(), metrics.getAvgParseTimeMs(), threshold));
            }

            if (metrics.getMemoryUsedMb() > MAX_MEMORY_MB) {
                allPassed = false;
                failures.add(String.format("%s: Memory usage %.2fMB exceeds threshold %.2fMB",
                        metrics.getTestName(), metrics.getMemoryUsedMb(), MAX_MEMORY_MB));
            }
        }

        System.out.println("\nThresholds:");
        System.out.println("  100 lines: < " + MAX_PARSE_TIME_100_LINES_MS + "ms");
        System.out.println("  1000 lines: < " + MAX_PARSE_TIME_1000_LINES_MS + "ms");
        System.out.println("  5000 lines: < " + MAX_PARSE_TIME_5000_LINES_MS + "ms");
        System.out.println("  Memory: < " + MAX_MEMORY_MB + "MB");
        System.out.println();

        if (allPassed) {
            System.out.println("Result: GO");
            System.out.println("All tests passed. JavaParser meets performance requirements.");
        } else {
            System.out.println("Result: NO-GO");
            System.out.println("Some tests failed:");
            failures.forEach(f -> System.out.println("  - " + f));
        }

        System.out.println("\nRecommendation:");
        if (allPassed) {
            System.out.println("JavaParser is suitable for the AI Code Review project.");
            System.out.println("Proceed with integration into the main codebase.");
        } else {
            System.out.println("Review failures and consider:");
            System.out.println("  1. Adjusting thresholds if they're too strict");
            System.out.println("  2. Optimizing parsing approach");
            System.out.println("  3. Exploring alternative parsing libraries");
        }
    }

    /**
     * Get threshold based on line count
     */
    private static long getThreshold(int lineCount) {
        if (lineCount <= 100) return MAX_PARSE_TIME_100_LINES_MS;
        if (lineCount <= 1000) return MAX_PARSE_TIME_1000_LINES_MS;
        return MAX_PARSE_TIME_5000_LINES_MS;
    }
}
