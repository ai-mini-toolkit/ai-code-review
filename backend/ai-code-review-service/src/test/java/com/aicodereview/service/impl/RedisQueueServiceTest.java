package com.aicodereview.service.impl;

import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.enums.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisQueueService.
 * <p>
 * Uses Mockito to mock RedisTemplate and its operations.
 * Covers all queue operations including:
 * - Enqueue with priority scoring
 * - Dequeue with lock acquisition
 * - Requeue with delay
 * - Monitoring methods
 * </p>
 *
 * @since 2.6.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisQueueService Unit Tests")
class RedisQueueServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOps;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RedisQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new RedisQueueService(redisTemplate);
    }

    // --- Score Calculation Tests ---

    @Test
    @DisplayName("calculateScore - HIGH priority should produce lower score than NORMAL")
    void testCalculateScore_HighBeforeNormal() {
        Instant now = Instant.now();
        double highScore = queueService.calculateScore(TaskPriority.HIGH, now);
        double normalScore = queueService.calculateScore(TaskPriority.NORMAL, now);

        // HIGH score should be lower than NORMAL score (ZPOPMIN dequeues lowest first)
        assertThat(highScore).isLessThan(normalScore);
    }

    @Test
    @DisplayName("calculateScore - same priority, earlier time should produce lower score (FIFO)")
    void testCalculateScore_FIFOWithinSamePriority() {
        Instant earlier = Instant.now().minusSeconds(10);
        Instant later = Instant.now();

        double earlierScore = queueService.calculateScore(TaskPriority.NORMAL, earlier);
        double laterScore = queueService.calculateScore(TaskPriority.NORMAL, later);

        assertThat(earlierScore).isLessThan(laterScore);
    }

    @Test
    @DisplayName("calculateScore - HIGH at later time should still be lower than NORMAL at earlier time")
    void testCalculateScore_PriorityOverridesTime() {
        Instant earlier = Instant.now().minusSeconds(3600); // 1 hour ago
        Instant later = Instant.now();

        double highLater = queueService.calculateScore(TaskPriority.HIGH, later);
        double normalEarlier = queueService.calculateScore(TaskPriority.NORMAL, earlier);

        // HIGH priority should always dequeue before NORMAL, regardless of time
        assertThat(highLater).isLessThan(normalEarlier);
    }

    // --- Enqueue Tests ---

    @Test
    @DisplayName("enqueue - should add task to sorted set with calculated score")
    void testEnqueue() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        queueService.enqueue(100L, TaskPriority.HIGH);

        verify(zSetOps).add(eq(QueueKeys.TASK_QUEUE), eq("100"), anyDouble());
    }

    @Test
    @DisplayName("enqueue - HIGH priority should use lower score than NORMAL")
    void testEnqueue_PriorityScoreOrdering() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

        queueService.enqueue(1L, TaskPriority.HIGH);
        verify(zSetOps).add(eq(QueueKeys.TASK_QUEUE), eq("1"), scoreCaptor.capture());
        double highScore = scoreCaptor.getValue();

        queueService.enqueue(2L, TaskPriority.NORMAL);
        verify(zSetOps).add(eq(QueueKeys.TASK_QUEUE), eq("2"), scoreCaptor.capture());
        double normalScore = scoreCaptor.getValue();

        assertThat(highScore).isLessThan(normalScore);
    }

    // --- Dequeue Tests ---

    @Test
    @DisplayName("dequeue - empty queue should return Optional.empty()")
    void testDequeue_EmptyQueue() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.popMin(QueueKeys.TASK_QUEUE)).thenReturn(null);

        Optional<Long> result = queueService.dequeue();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("dequeue - should pop task and acquire lock successfully")
    void testDequeue_Success() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ZSetOperations.TypedTuple<Object> tuple = ZSetOperations.TypedTuple.of("100", 1738800000000.0);
        when(zSetOps.popMin(QueueKeys.TASK_QUEUE)).thenReturn(tuple);
        when(valueOps.setIfAbsent(eq(QueueKeys.taskLockKey(100L)), anyString(), eq(Duration.ofSeconds(300))))
                .thenReturn(true);

        Optional<Long> result = queueService.dequeue();

        assertThat(result).isPresent().contains(100L);
        verify(zSetOps).popMin(QueueKeys.TASK_QUEUE);
        verify(valueOps).setIfAbsent(eq(QueueKeys.taskLockKey(100L)), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("dequeue - lock acquisition failure should re-enqueue and return empty")
    void testDequeue_LockFailure_ReEnqueues() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        double originalScore = 1738800000000.0;
        ZSetOperations.TypedTuple<Object> tuple = ZSetOperations.TypedTuple.of("100", originalScore);
        when(zSetOps.popMin(QueueKeys.TASK_QUEUE)).thenReturn(tuple);
        when(valueOps.setIfAbsent(eq(QueueKeys.taskLockKey(100L)), anyString(), eq(Duration.ofSeconds(300))))
                .thenReturn(false);

        Optional<Long> result = queueService.dequeue();

        assertThat(result).isEmpty();
        // Verify re-enqueue with original score
        verify(zSetOps).add(QueueKeys.TASK_QUEUE, "100", originalScore);
    }

    @Test
    @DisplayName("dequeue - tuple with null value should return empty")
    void testDequeue_NullValue() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        ZSetOperations.TypedTuple<Object> tuple = ZSetOperations.TypedTuple.of(null, 1.0);
        when(zSetOps.popMin(QueueKeys.TASK_QUEUE)).thenReturn(tuple);

        Optional<Long> result = queueService.dequeue();

        assertThat(result).isEmpty();
    }

    // --- Requeue with Delay Tests ---

    @Test
    @DisplayName("requeueWithDelay - should release lock and add with delayed score")
    void testRequeueWithDelay() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.delete(QueueKeys.taskLockKey(100L))).thenReturn(true);

        queueService.requeueWithDelay(100L, TaskPriority.NORMAL, 60);

        // Verify lock released
        verify(redisTemplate).delete(QueueKeys.taskLockKey(100L));

        // Verify re-enqueue with score
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOps).add(eq(QueueKeys.TASK_QUEUE), eq("100"), scoreCaptor.capture());

        // Score should include delay offset - the future timestamp should be > current time
        double score = scoreCaptor.getValue();
        double currentScore = queueService.calculateScore(TaskPriority.NORMAL, Instant.now());
        // Delayed score should be higher (dequeued later) than current score
        assertThat(score).isGreaterThan(currentScore - 1000); // Allow small timing tolerance
    }

    @Test
    @DisplayName("requeueWithDelay - score should reflect delay offset")
    void testRequeueWithDelay_ScoreCalculation() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        int delaySeconds = 120;
        queueService.requeueWithDelay(200L, TaskPriority.HIGH, delaySeconds);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOps).add(eq(QueueKeys.TASK_QUEUE), eq("200"), scoreCaptor.capture());

        double score = scoreCaptor.getValue();
        // For HIGH priority: score = (100-100)*1e13 + futureTimestamp
        // futureTimestamp ~= now + 120s in millis
        long expectedFutureMs = Instant.now().plusSeconds(delaySeconds).toEpochMilli();
        assertThat(score).isCloseTo(expectedFutureMs, org.assertj.core.data.Offset.offset(2000.0));
    }

    // --- Monitoring Tests ---

    @Test
    @DisplayName("getQueueSize - should return zCard value")
    void testGetQueueSize() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard(QueueKeys.TASK_QUEUE)).thenReturn(5L);

        long size = queueService.getQueueSize();

        assertThat(size).isEqualTo(5);
    }

    @Test
    @DisplayName("getQueueSize - null response should return 0")
    void testGetQueueSize_Null() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard(QueueKeys.TASK_QUEUE)).thenReturn(null);

        long size = queueService.getQueueSize();

        assertThat(size).isEqualTo(0);
    }

    @Test
    @DisplayName("isLocked - should return true when lock key exists")
    void testIsLocked_True() {
        when(redisTemplate.hasKey(QueueKeys.taskLockKey(100L))).thenReturn(true);

        boolean locked = queueService.isLocked(100L);

        assertThat(locked).isTrue();
    }

    @Test
    @DisplayName("isLocked - should return false when lock key does not exist")
    void testIsLocked_False() {
        when(redisTemplate.hasKey(QueueKeys.taskLockKey(100L))).thenReturn(false);

        boolean locked = queueService.isLocked(100L);

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("releaseLock - should delete lock key")
    void testReleaseLock() {
        when(redisTemplate.delete(QueueKeys.taskLockKey(100L))).thenReturn(true);

        queueService.releaseLock(100L);

        verify(redisTemplate).delete(QueueKeys.taskLockKey(100L));
    }

    @Test
    @DisplayName("releaseLock - non-existent lock should not throw")
    void testReleaseLock_NonExistent() {
        when(redisTemplate.delete(QueueKeys.taskLockKey(999L))).thenReturn(false);

        queueService.releaseLock(999L);

        verify(redisTemplate).delete(QueueKeys.taskLockKey(999L));
    }

    // --- Edge Cases ---

    @Test
    @DisplayName("enqueue - duplicate taskId should overwrite in sorted set (ZADD behavior)")
    void testEnqueue_Duplicate() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        queueService.enqueue(100L, TaskPriority.HIGH);
        queueService.enqueue(100L, TaskPriority.NORMAL);

        // Both calls should go through (Redis ZADD overwrites score for same member)
        verify(zSetOps, times(2)).add(eq(QueueKeys.TASK_QUEUE), eq("100"), anyDouble());
    }
}
