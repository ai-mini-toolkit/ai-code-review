package com.aicr.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task consumer that processes tasks from Redis queue
 */
public class TaskConsumer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskConsumer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;
    private final String queueName;
    private final AtomicInteger processedCount;
    private final AtomicInteger errorCount;
    private final List<MockReviewTask> completedTasks;
    private volatile boolean running = true;

    public TaskConsumer(RedisTemplate<String, String> redisTemplate,
                        String queueName,
                        AtomicInteger processedCount,
                        AtomicInteger errorCount,
                        List<MockReviewTask> completedTasks) {
        this.redisTemplate = redisTemplate;
        this.queueName = queueName;
        this.processedCount = processedCount;
        this.errorCount = errorCount;
        this.completedTasks = completedTasks;
    }

    @Override
    public void run() {
        while (running) {
            try {
                String taskJson = popTask();
                if (taskJson == null) {
                    // Queue is empty, wait a bit
                    Thread.sleep(50);
                    continue;
                }

                MockReviewTask task = objectMapper.readValue(taskJson, MockReviewTask.class);
                processTask(task);
                processedCount.incrementAndGet();

                synchronized (completedTasks) {
                    completedTasks.add(task);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Consumer error", e);
                errorCount.incrementAndGet();
            }
        }
    }

    /**
     * Pop task from Redis queue (blocking with timeout)
     */
    private String popTask() {
        try {
            // Use rightPop with timeout
            String result = redisTemplate.opsForList().rightPop(queueName, 1, java.util.concurrent.TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            logger.error("Failed to pop task from queue", e);
        }
        return null;
    }

    /**
     * Process a task (simulate work)
     */
    private void processTask(MockReviewTask task) throws InterruptedException {
        task.process();
    }

    /**
     * Stop the consumer
     */
    public void stop() {
        running = false;
    }
}
