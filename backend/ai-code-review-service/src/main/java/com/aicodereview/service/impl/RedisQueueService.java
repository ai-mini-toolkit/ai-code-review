package com.aicodereview.service.impl;

import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-based implementation of {@link QueueService} using Sorted Set for priority queue.
 * <p>
 * Score formula: {@code (MAX_PRIORITY_SCORE - priority.getPriorityScore()) * PRIORITY_MULTIPLIER + timestampMillis}
 * </p>
 * <p>
 * Examples with ZPOPMIN (lowest score dequeued first):
 * - HIGH (100) at T=1738800000000: score = (100-100)*1e13 + 1738800000000 = 1738800000000
 * - NORMAL (50) at T=1738800000000: score = (100-50)*1e13 + 1738800000000 = 501738800000000
 * </p>
 * <p>
 * Result: HIGH tasks ALWAYS dequeue before NORMAL tasks.
 * Within same priority: earlier tasks dequeue first (FIFO).
 * </p>
 *
 * @since 2.6.0
 */
@Slf4j
@Service
public class RedisQueueService implements QueueService {

    private static final int MAX_PRIORITY_SCORE = TaskPriority.HIGH.getPriorityScore();
    private static final double PRIORITY_MULTIPLIER = 1e13;
    private static final Duration LOCK_TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, Object> redisTemplate;
    private final String workerId;

    public RedisQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.workerId = resolveWorkerId();
    }

    @Override
    public void enqueue(Long taskId, TaskPriority priority) {
        double score = calculateScore(priority, Instant.now());
        redisTemplate.opsForZSet().add(QueueKeys.TASK_QUEUE, String.valueOf(taskId), score);
        log.info("Enqueued task {} with priority {} (score: {})", taskId, priority, score);
    }

    @Override
    public Optional<Long> dequeue() {
        ZSetOperations.TypedTuple<Object> tuple =
                redisTemplate.opsForZSet().popMin(QueueKeys.TASK_QUEUE);

        if (tuple == null || tuple.getValue() == null) {
            log.debug("Queue is empty, no task to dequeue");
            return Optional.empty();
        }

        Long taskId;
        try {
            taskId = Long.parseLong(tuple.getValue().toString());
        } catch (NumberFormatException e) {
            log.error("Invalid taskId in queue: '{}'. Discarding corrupted entry.", tuple.getValue(), e);
            return Optional.empty();
        }

        // Acquire processing lock
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(QueueKeys.taskLockKey(taskId), workerId, LOCK_TTL);

        if (Boolean.FALSE.equals(locked)) {
            log.warn("Failed to acquire lock for task {}, re-enqueuing", taskId);
            // Re-enqueue with original score if lock acquisition fails
            redisTemplate.opsForZSet().add(QueueKeys.TASK_QUEUE,
                    String.valueOf(taskId), tuple.getScore());
            return Optional.empty();
        }

        log.info("Dequeued task {} (score: {})", taskId, tuple.getScore());
        return Optional.of(taskId);
    }

    @Override
    public void requeueWithDelay(Long taskId, TaskPriority priority, int delaySeconds) {
        releaseLock(taskId);

        long futureTimestamp = Instant.now().plusSeconds(delaySeconds).toEpochMilli();
        double score = (MAX_PRIORITY_SCORE - priority.getPriorityScore()) * PRIORITY_MULTIPLIER + futureTimestamp;
        redisTemplate.opsForZSet().add(QueueKeys.TASK_QUEUE, String.valueOf(taskId), score);

        log.info("Requeued task {} with {}s delay (score: {})", taskId, delaySeconds, score);
    }

    @Override
    public long getQueueSize() {
        Long size = redisTemplate.opsForZSet().zCard(QueueKeys.TASK_QUEUE);
        return size != null ? size : 0;
    }

    @Override
    public boolean isLocked(Long taskId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(QueueKeys.taskLockKey(taskId)));
    }

    @Override
    public void releaseLock(Long taskId) {
        Boolean deleted = redisTemplate.delete(QueueKeys.taskLockKey(taskId));
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Released lock for task {}", taskId);
        }
    }

    /**
     * Calculates the Redis Sorted Set score for a task.
     * <p>
     * Formula: (MAX_PRIORITY_SCORE - priority.getPriorityScore()) * PRIORITY_MULTIPLIER + timestampMillis
     * </p>
     */
    double calculateScore(TaskPriority priority, Instant timestamp) {
        return (MAX_PRIORITY_SCORE - priority.getPriorityScore()) * PRIORITY_MULTIPLIER
                + timestamp.toEpochMilli();
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (UnknownHostException e) {
            return "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
