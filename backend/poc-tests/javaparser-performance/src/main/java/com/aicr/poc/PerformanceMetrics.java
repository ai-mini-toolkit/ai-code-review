package com.aicr.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance metrics data structure for test results
 */
public class PerformanceMetrics {

    @JsonProperty("test_name")
    private String testName;

    @JsonProperty("file_size")
    private String fileSize;

    @JsonProperty("line_count")
    private int lineCount;

    @JsonProperty("parse_time_ms")
    private long parseTimeMs;

    @JsonProperty("memory_used_mb")
    private double memoryUsedMb;

    @JsonProperty("method_count")
    private int methodCount;

    @JsonProperty("class_count")
    private int classCount;

    @JsonProperty("dependency_count")
    private int dependencyCount;

    @JsonProperty("circular_dependencies")
    private int circularDependencies;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("iterations")
    private int iterations;

    @JsonProperty("avg_parse_time_ms")
    private double avgParseTimeMs;

    @JsonProperty("min_parse_time_ms")
    private long minParseTimeMs;

    @JsonProperty("max_parse_time_ms")
    private long maxParseTimeMs;

    @JsonProperty("throughput_lines_per_sec")
    private double throughputLinesPerSec;

    // Getters and setters

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public long getParseTimeMs() {
        return parseTimeMs;
    }

    public void setParseTimeMs(long parseTimeMs) {
        this.parseTimeMs = parseTimeMs;
    }

    public double getMemoryUsedMb() {
        return memoryUsedMb;
    }

    public void setMemoryUsedMb(double memoryUsedMb) {
        this.memoryUsedMb = memoryUsedMb;
    }

    public int getMethodCount() {
        return methodCount;
    }

    public void setMethodCount(int methodCount) {
        this.methodCount = methodCount;
    }

    public int getClassCount() {
        return classCount;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int getDependencyCount() {
        return dependencyCount;
    }

    public void setDependencyCount(int dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public int getCircularDependencies() {
        return circularDependencies;
    }

    public void setCircularDependencies(int circularDependencies) {
        this.circularDependencies = circularDependencies;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public double getAvgParseTimeMs() {
        return avgParseTimeMs;
    }

    public void setAvgParseTimeMs(double avgParseTimeMs) {
        this.avgParseTimeMs = avgParseTimeMs;
    }

    public long getMinParseTimeMs() {
        return minParseTimeMs;
    }

    public void setMinParseTimeMs(long minParseTimeMs) {
        this.minParseTimeMs = minParseTimeMs;
    }

    public long getMaxParseTimeMs() {
        return maxParseTimeMs;
    }

    public void setMaxParseTimeMs(long maxParseTimeMs) {
        this.maxParseTimeMs = maxParseTimeMs;
    }

    public double getThroughputLinesPerSec() {
        return throughputLinesPerSec;
    }

    public void setThroughputLinesPerSec(double throughputLinesPerSec) {
        this.throughputLinesPerSec = throughputLinesPerSec;
    }

    /**
     * Calculate throughput based on line count and parse time
     */
    public void calculateThroughput() {
        if (parseTimeMs > 0 && lineCount > 0) {
            this.throughputLinesPerSec = (lineCount * 1000.0) / parseTimeMs;
        }
    }
}
