package com.aicr.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Performance monitoring and metrics calculation
 */
public class PerformanceMonitor {

    /**
     * Calculate performance metrics from completed tasks
     */
    public static PerformanceMetrics calculateMetrics(
            List<MockReviewTask> tasks,
            long testDurationMs,
            int producedCount,
            int processedCount,
            int errorCount) {

        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setTestDurationMs(testDurationMs);
        metrics.setTotalProduced(producedCount);
        metrics.setTotalProcessed(processedCount);
        metrics.setErrorCount(errorCount);
        metrics.setSuccessRate(producedCount > 0 ? (double) processedCount / producedCount * 100 : 0);

        if (tasks.isEmpty()) {
            return metrics;
        }

        // Calculate latencies (queue wait time)
        List<Long> latencies = new ArrayList<>();
        List<Long> processingTimes = new ArrayList<>();
        List<Long> totalTimes = new ArrayList<>();

        for (MockReviewTask task : tasks) {
            latencies.add(task.getLatencyMs());
            processingTimes.add(task.getProcessingTimeMs());
            totalTimes.add(task.getTotalTimeMs());
        }

        Collections.sort(latencies);
        Collections.sort(processingTimes);
        Collections.sort(totalTimes);

        // Latency statistics
        metrics.setAvgLatencyMs(calculateAverage(latencies));
        metrics.setP50LatencyMs(calculatePercentile(latencies, 50));
        metrics.setP95LatencyMs(calculatePercentile(latencies, 95));
        metrics.setP99LatencyMs(calculatePercentile(latencies, 99));
        metrics.setMinLatencyMs(latencies.get(0));
        metrics.setMaxLatencyMs(latencies.get(latencies.size() - 1));

        // Processing time statistics
        metrics.setAvgProcessingTimeMs(calculateAverage(processingTimes));
        metrics.setMinProcessingTimeMs(processingTimes.get(0));
        metrics.setMaxProcessingTimeMs(processingTimes.get(processingTimes.size() - 1));

        // Throughput
        double throughputPerSec = testDurationMs > 0 ? (double) processedCount / (testDurationMs / 1000.0) : 0;
        metrics.setThroughputPerSec(throughputPerSec);

        return metrics;
    }

    /**
     * Calculate average
     */
    private static double calculateAverage(List<Long> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /**
     * Calculate percentile
     */
    private static long calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(sortedValues.size() * percentile / 100.0) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Performance metrics data structure
     */
    public static class PerformanceMetrics {
        @JsonProperty("test_duration_ms")
        private long testDurationMs;

        @JsonProperty("total_produced")
        private int totalProduced;

        @JsonProperty("total_processed")
        private int totalProcessed;

        @JsonProperty("error_count")
        private int errorCount;

        @JsonProperty("success_rate")
        private double successRate;

        @JsonProperty("throughput_per_sec")
        private double throughputPerSec;

        @JsonProperty("avg_latency_ms")
        private double avgLatencyMs;

        @JsonProperty("p50_latency_ms")
        private long p50LatencyMs;

        @JsonProperty("p95_latency_ms")
        private long p95LatencyMs;

        @JsonProperty("p99_latency_ms")
        private long p99LatencyMs;

        @JsonProperty("min_latency_ms")
        private long minLatencyMs;

        @JsonProperty("max_latency_ms")
        private long maxLatencyMs;

        @JsonProperty("avg_processing_time_ms")
        private double avgProcessingTimeMs;

        @JsonProperty("min_processing_time_ms")
        private long minProcessingTimeMs;

        @JsonProperty("max_processing_time_ms")
        private long maxProcessingTimeMs;

        // Getters and setters
        public long getTestDurationMs() { return testDurationMs; }
        public void setTestDurationMs(long testDurationMs) { this.testDurationMs = testDurationMs; }

        public int getTotalProduced() { return totalProduced; }
        public void setTotalProduced(int totalProduced) { this.totalProduced = totalProduced; }

        public int getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public double getThroughputPerSec() { return throughputPerSec; }
        public void setThroughputPerSec(double throughputPerSec) { this.throughputPerSec = throughputPerSec; }

        public double getAvgLatencyMs() { return avgLatencyMs; }
        public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

        public long getP50LatencyMs() { return p50LatencyMs; }
        public void setP50LatencyMs(long p50LatencyMs) { this.p50LatencyMs = p50LatencyMs; }

        public long getP95LatencyMs() { return p95LatencyMs; }
        public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }

        public long getP99LatencyMs() { return p99LatencyMs; }
        public void setP99LatencyMs(long p99LatencyMs) { this.p99LatencyMs = p99LatencyMs; }

        public long getMinLatencyMs() { return minLatencyMs; }
        public void setMinLatencyMs(long minLatencyMs) { this.minLatencyMs = minLatencyMs; }

        public long getMaxLatencyMs() { return maxLatencyMs; }
        public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }

        public double getAvgProcessingTimeMs() { return avgProcessingTimeMs; }
        public void setAvgProcessingTimeMs(double avgProcessingTimeMs) { this.avgProcessingTimeMs = avgProcessingTimeMs; }

        public long getMinProcessingTimeMs() { return minProcessingTimeMs; }
        public void setMinProcessingTimeMs(long minProcessingTimeMs) { this.minProcessingTimeMs = minProcessingTimeMs; }

        public long getMaxProcessingTimeMs() { return maxProcessingTimeMs; }
        public void setMaxProcessingTimeMs(long maxProcessingTimeMs) { this.maxProcessingTimeMs = maxProcessingTimeMs; }
    }
}
