package com.aicr.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task producer that pushes tasks to Redis queue
 */
public class TaskProducer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskProducer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;
    private final String queueName;
    private final int taskCount;
    private final AtomicInteger producedCount;
    private final AtomicInteger errorCount;

    public TaskProducer(RedisTemplate<String, String> redisTemplate,
                        String queueName,
                        int taskCount,
                        AtomicInteger producedCount,
                        AtomicInteger errorCount) {
        this.redisTemplate = redisTemplate;
        this.queueName = queueName;
        this.taskCount = taskCount;
        this.producedCount = producedCount;
        this.errorCount = errorCount;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < taskCount; i++) {
                MockReviewTask task = createTask(i);
                pushTask(task);
                producedCount.incrementAndGet();

                // Small delay to simulate realistic production rate
                if (i % 10 == 0 && i > 0) {
                    Thread.sleep(10);
                }
            }
        } catch (Exception e) {
            logger.error("Producer error", e);
            errorCount.incrementAndGet();
        }
    }

    /**
     * Create a mock task
     */
    private MockReviewTask createTask(int index) {
        String[] dimensions = {
                "code_quality",
                "security",
                "performance",
                "best_practices",
                "maintainability",
                "documentation"
        };

        String dimension = dimensions[index % dimensions.length];
        return new MockReviewTask(
                "test-repo",
                "commit-" + System.currentTimeMillis(),
                "src/main/java/TestFile" + index + ".java",
                dimension
        );
    }

    /**
     * Push task to Redis queue
     */
    private void pushTask(MockReviewTask task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().leftPush(queueName, taskJson);
        } catch (Exception e) {
            logger.error("Failed to push task: " + task.getTaskId(), e);
            errorCount.incrementAndGet();
        }
    }
}
