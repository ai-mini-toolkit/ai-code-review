package com.aicr.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis Queue Performance Test
 * Tests producer-consumer pattern with varying concurrency levels
 */
public class RedisQueuePerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuePerformanceTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String QUEUE_NAME = "code-review-tasks";

    // Test configurations
    private static final int[] CONCURRENCY_LEVELS = {10, 50, 100, 200};
    private static final int TASKS_PER_PRODUCER = 100;
    private static final int TEST_DURATION_SECONDS = 30;

    // Performance thresholds
    private static final double MIN_THROUGHPUT_PER_SEC = 100.0;    // 100 tasks/sec
    private static final long MAX_P95_LATENCY_MS = 1000;           // 1 second
    private static final long MAX_P99_LATENCY_MS = 2000;           // 2 seconds
    private static final double MIN_SUCCESS_RATE = 99.0;           // 99%

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Redis Queue Performance Test - PoC Validation");
        System.out.println("=".repeat(80));
        System.out.println();

        // Check Redis connection
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        String redisPort = System.getenv().getOrDefault("REDIS_PORT", "6379");

        System.out.println("Redis Configuration:");
        System.out.println("  Host: " + redisHost);
        System.out.println("  Port: " + redisPort);
        System.out.println();

        RedisTemplate<String, String> redisTemplate = null;
        LettuceConnectionFactory connectionFactory = null;

        try {
            // Initialize Redis connection
            connectionFactory = createConnectionFactory(redisHost, Integer.parseInt(redisPort));
            redisTemplate = createRedisTemplate(connectionFactory);

            // Test Redis connection
            if (!testRedisConnection(redisTemplate)) {
                System.err.println("Failed to connect to Redis");
                System.err.println("\nTo run this test, ensure Redis is running:");
                System.err.println("  docker run -d -p 6379:6379 redis:latest");
                System.err.println("  # or");
                System.err.println("  redis-server");
                System.exit(1);
            }

            System.out.println("Redis connection successful!\n");

            // Run tests for different concurrency levels
            List<TestResult> allResults = new ArrayList<>();

            for (int concurrency : CONCURRENCY_LEVELS) {
                System.out.println("-".repeat(80));
                System.out.println("Testing concurrency level: " + concurrency);
                System.out.println("-".repeat(80));

                TestResult result = runConcurrencyTest(redisTemplate, concurrency);
                allResults.add(result);

                printTestSummary(result);

                // Clean up queue between tests
                clearQueue(redisTemplate);
                Thread.sleep(2000);
            }

            // Save report
            saveReport(allResults);

            // Evaluate Go/No-Go
            evaluateGoNoGo(allResults);

        } catch (Exception e) {
            logger.error("Test failed", e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    /**
     * Create Redis connection factory
     */
    private static LettuceConnectionFactory createConnectionFactory(String host, int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Create Redis template
     */
    private static RedisTemplate<String, String> createRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Test Redis connection
     */
    private static boolean testRedisConnection(RedisTemplate<String, String> redisTemplate) {
        try {
            redisTemplate.opsForValue().set("test-key", "test-value", 10, TimeUnit.SECONDS);
            String value = redisTemplate.opsForValue().get("test-key");
            return "test-value".equals(value);
        } catch (Exception e) {
            logger.error("Redis connection test failed", e);
            return false;
        }
    }

    /**
     * Run concurrency test
     */
    private static TestResult runConcurrencyTest(RedisTemplate<String, String> redisTemplate, int concurrency) {
        TestResult result = new TestResult();
        result.setConcurrency(concurrency);
        result.setStartTime(System.currentTimeMillis());

        // Shared counters
        AtomicInteger producedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<MockReviewTask> completedTasks = new CopyOnWriteArrayList<>();

        // Create thread pools
        ExecutorService producerPool = Executors.newFixedThreadPool(concurrency / 2);
        ExecutorService consumerPool = Executors.newFixedThreadPool(concurrency);

        List<TaskConsumer> consumers = new ArrayList<>();

        try {
            System.out.println("Starting " + concurrency + " consumers...");

            // Start consumers
            for (int i = 0; i < concurrency; i++) {
                TaskConsumer consumer = new TaskConsumer(
                        redisTemplate,
                        QUEUE_NAME,
                        processedCount,
                        errorCount,
                        completedTasks
                );
                consumers.add(consumer);
                consumerPool.submit(consumer);
            }

            Thread.sleep(1000); // Let consumers start

            System.out.println("Starting " + (concurrency / 2) + " producers...");

            // Start producers
            for (int i = 0; i < concurrency / 2; i++) {
                TaskProducer producer = new TaskProducer(
                        redisTemplate,
                        QUEUE_NAME,
                        TASKS_PER_PRODUCER,
                        producedCount,
                        errorCount
                );
                producerPool.submit(producer);
            }

            // Wait for producers to finish
            producerPool.shutdown();
            producerPool.awaitTermination(TEST_DURATION_SECONDS, TimeUnit.SECONDS);

            System.out.println("Produced " + producedCount.get() + " tasks");
            System.out.println("Waiting for consumers to process all tasks...");

            // Wait for all tasks to be processed (with timeout)
            long waitStart = System.currentTimeMillis();
            while (processedCount.get() < producedCount.get() &&
                    System.currentTimeMillis() - waitStart < TEST_DURATION_SECONDS * 1000) {
                Thread.sleep(500);
                System.out.print(".");
            }
            System.out.println();

            // Stop consumers
            for (TaskConsumer consumer : consumers) {
                consumer.stop();
            }
            consumerPool.shutdownNow();
            consumerPool.awaitTermination(5, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Test interrupted", e);
        }

        result.setEndTime(System.currentTimeMillis());
        result.setDurationMs(result.getEndTime() - result.getStartTime());

        // Calculate metrics
        PerformanceMonitor.PerformanceMetrics metrics = PerformanceMonitor.calculateMetrics(
                completedTasks,
                result.getDurationMs(),
                producedCount.get(),
                processedCount.get(),
                errorCount.get()
        );

        result.setMetrics(metrics);
        result.evaluatePass();

        return result;
    }

    /**
     * Clear queue
     */
    private static void clearQueue(RedisTemplate<String, String> redisTemplate) {
        try {
            redisTemplate.delete(QUEUE_NAME);
        } catch (Exception e) {
            logger.error("Failed to clear queue", e);
        }
    }

    /**
     * Print test summary
     */
    private static void printTestSummary(TestResult result) {
        PerformanceMonitor.PerformanceMetrics m = result.getMetrics();

        System.out.println("\n--- Test Results ---");
        System.out.println("Concurrency: " + result.getConcurrency());
        System.out.println("Duration: " + result.getDurationMs() + "ms");
        System.out.println("Produced: " + m.getTotalProduced());
        System.out.println("Processed: " + m.getTotalProcessed());
        System.out.println("Errors: " + m.getErrorCount());
        System.out.println("Success Rate: " + String.format("%.2f%%", m.getSuccessRate()));
        System.out.println("\nPerformance:");
        System.out.println("  Throughput: " + String.format("%.2f tasks/sec", m.getThroughputPerSec()));
        System.out.println("  Avg Latency: " + String.format("%.2fms", m.getAvgLatencyMs()));
        System.out.println("  P50 Latency: " + m.getP50LatencyMs() + "ms");
        System.out.println("  P95 Latency: " + m.getP95LatencyMs() + "ms");
        System.out.println("  P99 Latency: " + m.getP99LatencyMs() + "ms");
        System.out.println("\nResult: " + (result.isPassed() ? "PASS" : "FAIL"));
        System.out.println();
    }

    /**
     * Save JSON report
     */
    private static void saveReport(List<TestResult> results) throws Exception {
        File reportFile = new File("target/redis-queue-report.json");
        reportFile.getParentFile().mkdirs();

        Map<String, Object> report = new HashMap<>();
        report.put("test_suite", "Redis Queue Concurrency PoC");
        report.put("timestamp", System.currentTimeMillis());
        report.put("results", results);

        objectMapper.writeValue(reportFile, report);
        System.out.println("JSON report saved to: " + reportFile.getAbsolutePath());
    }

    /**
     * Evaluate Go/No-Go decision
     */
    private static void evaluateGoNoGo(List<TestResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Go/No-Go Decision");
        System.out.println("=".repeat(80));

        System.out.println("\nThresholds:");
        System.out.println("  Min Throughput: " + MIN_THROUGHPUT_PER_SEC + " tasks/sec");
        System.out.println("  Max P95 Latency: " + MAX_P95_LATENCY_MS + "ms");
        System.out.println("  Max P99 Latency: " + MAX_P99_LATENCY_MS + "ms");
        System.out.println("  Min Success Rate: " + MIN_SUCCESS_RATE + "%");
        System.out.println();

        boolean allPassed = results.stream().allMatch(TestResult::isPassed);
        int passedCount = (int) results.stream().filter(TestResult::isPassed).count();

        System.out.println("Results: " + passedCount + "/" + results.size() + " tests passed");

        if (allPassed) {
            System.out.println("\nDecision: GO");
            System.out.println("Redis queue meets all performance requirements.");
            System.out.println("\nRecommendation:");
            System.out.println("  - Proceed with Redis-based task queue");
            System.out.println("  - Recommended concurrency: 50-100 workers");
            System.out.println("  - Implement dead letter queue for failed tasks");
        } else {
            System.out.println("\nDecision: NO-GO");
            System.out.println("Redis queue does not meet performance requirements.");
            System.out.println("\nConsider:");
            System.out.println("  - Increasing Redis resources");
            System.out.println("  - Optimizing task processing logic");
            System.out.println("  - Alternative: RabbitMQ, Apache Kafka");
        }
    }

    /**
     * Test result data structure
     */
    static class TestResult {
        private int concurrency;
        private long startTime;
        private long endTime;
        private long durationMs;
        private PerformanceMonitor.PerformanceMetrics metrics;
        private boolean passed;

        public void evaluatePass() {
            if (metrics == null) {
                passed = false;
                return;
            }

            passed = metrics.getThroughputPerSec() >= MIN_THROUGHPUT_PER_SEC &&
                    metrics.getP95LatencyMs() <= MAX_P95_LATENCY_MS &&
                    metrics.getP99LatencyMs() <= MAX_P99_LATENCY_MS &&
                    metrics.getSuccessRate() >= MIN_SUCCESS_RATE;
        }

        // Getters and setters
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public PerformanceMonitor.PerformanceMetrics getMetrics() { return metrics; }
        public void setMetrics(PerformanceMonitor.PerformanceMetrics metrics) { this.metrics = metrics; }

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
    }
}
