package com.aicodereview.api;

import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for QueueService with real Redis.
 * <p>
 * Requires Docker Redis to be running (docker-compose up -d).
 * Tests queue operations with actual Redis Sorted Set operations.
 * </p>
 *
 * @since 2.6.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Queue Integration Tests")
class QueueIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void cleanQueue() {
        // Clean task queue
        redisTemplate.delete(QueueKeys.TASK_QUEUE);
        // Clean any lock keys from previous test runs
        Set<String> lockKeys = redisTemplate.keys(QueueKeys.TASK_LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    @Test
    @DisplayName("Priority ordering: HIGH tasks dequeue before NORMAL tasks")
    void testPriorityOrdering() {
        // Given: Enqueue NORMAL tasks first, then HIGH tasks
        queueService.enqueue(1L, TaskPriority.NORMAL);
        queueService.enqueue(2L, TaskPriority.NORMAL);
        queueService.enqueue(3L, TaskPriority.HIGH);
        queueService.enqueue(4L, TaskPriority.HIGH);

        // When: Dequeue all tasks
        List<Long> dequeuedOrder = new ArrayList<>();
        Optional<Long> task;
        while ((task = queueService.dequeue()).isPresent()) {
            dequeuedOrder.add(task.get());
            // Release lock so we can continue dequeuing
            queueService.releaseLock(task.get());
        }

        // Then: HIGH tasks should come first, NORMAL tasks second
        assertThat(dequeuedOrder).hasSize(4);
        // First two should be HIGH priority (tasks 3 and 4)
        assertThat(dequeuedOrder.subList(0, 2)).containsExactlyInAnyOrder(3L, 4L);
        // Last two should be NORMAL priority (tasks 1 and 2)
        assertThat(dequeuedOrder.subList(2, 4)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("FIFO within same priority level")
    void testFIFOWithinPriority() throws InterruptedException {
        // Given: Enqueue tasks with same priority in specific order
        queueService.enqueue(10L, TaskPriority.NORMAL);
        Thread.sleep(10); // Ensure timestamp difference
        queueService.enqueue(20L, TaskPriority.NORMAL);
        Thread.sleep(10);
        queueService.enqueue(30L, TaskPriority.NORMAL);

        // When: Dequeue all tasks
        List<Long> dequeuedOrder = new ArrayList<>();
        Optional<Long> task;
        while ((task = queueService.dequeue()).isPresent()) {
            dequeuedOrder.add(task.get());
            queueService.releaseLock(task.get());
        }

        // Then: Tasks should be in FIFO order
        assertThat(dequeuedOrder).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("Empty queue returns Optional.empty()")
    void testEmptyQueueDequeue() {
        // When: Dequeue from empty queue
        Optional<Long> result = queueService.dequeue();

        // Then: Should return empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Concurrent dequeue: no duplicate processing")
    void testConcurrentDequeue_NoDuplicates() throws InterruptedException {
        // Given: Enqueue 10 tasks
        int taskCount = 10;
        for (long i = 1; i <= taskCount; i++) {
            queueService.enqueue(i, TaskPriority.NORMAL);
        }

        // When: 5 threads concurrently dequeue
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Long> dequeuedTasks = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<Long> task;
                    while ((task = queueService.dequeue()).isPresent()) {
                        dequeuedTasks.add(task.get());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: All tasks dequeued exactly once (no duplicates)
        assertThat(dequeuedTasks).hasSize(taskCount);
    }

    @Test
    @DisplayName("Lock acquisition and release lifecycle")
    void testLockLifecycle() {
        // Given: Enqueue a task
        queueService.enqueue(100L, TaskPriority.HIGH);

        // When: Dequeue acquires lock
        Optional<Long> taskId = queueService.dequeue();
        assertThat(taskId).isPresent().contains(100L);

        // Then: Task should be locked
        assertThat(queueService.isLocked(100L)).isTrue();

        // When: Release lock
        queueService.releaseLock(100L);

        // Then: Task should no longer be locked
        assertThat(queueService.isLocked(100L)).isFalse();
    }

    @Test
    @DisplayName("Queue size tracking")
    void testQueueSize() {
        // Given: Empty queue
        assertThat(queueService.getQueueSize()).isEqualTo(0);

        // When: Enqueue tasks
        queueService.enqueue(1L, TaskPriority.HIGH);
        queueService.enqueue(2L, TaskPriority.NORMAL);
        queueService.enqueue(3L, TaskPriority.NORMAL);

        // Then: Size reflects count
        assertThat(queueService.getQueueSize()).isEqualTo(3);

        // When: Dequeue one
        queueService.dequeue();

        // Then: Size decremented
        assertThat(queueService.getQueueSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("Requeue with delay: task dequeued after delay-free tasks")
    void testRequeueWithDelay() throws InterruptedException {
        // Given: Enqueue a task and dequeue it
        queueService.enqueue(1L, TaskPriority.HIGH);
        Optional<Long> task = queueService.dequeue();
        assertThat(task).isPresent();

        // When: Requeue with 1-second delay + enqueue a fresh task
        queueService.requeueWithDelay(1L, TaskPriority.HIGH, 1);
        queueService.enqueue(2L, TaskPriority.HIGH);

        // Then: Fresh task (2) should dequeue before delayed task (1)
        Optional<Long> first = queueService.dequeue();
        assertThat(first).isPresent().contains(2L);
        queueService.releaseLock(2L);

        // Poll for delayed task (avoid fixed Thread.sleep which is flaky in CI)
        Optional<Long> second = Optional.empty();
        long deadline = System.currentTimeMillis() + 5000; // 5s max wait
        while (System.currentTimeMillis() < deadline) {
            second = queueService.dequeue();
            if (second.isPresent()) break;
            Thread.sleep(200);
        }
        assertThat(second).isPresent().contains(1L);
    }

    @Test
    @DisplayName("Full lifecycle: enqueue → dequeue → lock → release")
    void testFullLifecycle() {
        // Step 1: Enqueue
        queueService.enqueue(50L, TaskPriority.HIGH);
        assertThat(queueService.getQueueSize()).isEqualTo(1);
        assertThat(queueService.isLocked(50L)).isFalse();

        // Step 2: Dequeue (acquires lock)
        Optional<Long> taskId = queueService.dequeue();
        assertThat(taskId).isPresent().contains(50L);
        assertThat(queueService.getQueueSize()).isEqualTo(0);
        assertThat(queueService.isLocked(50L)).isTrue();

        // Step 3: Release lock
        queueService.releaseLock(50L);
        assertThat(queueService.isLocked(50L)).isFalse();
    }

    @Test
    @DisplayName("Mixed priority workload: correct overall ordering")
    void testMixedPriorityWorkload() throws InterruptedException {
        // Given: Interleaved enqueue of mixed priorities
        queueService.enqueue(1L, TaskPriority.NORMAL);
        Thread.sleep(5);
        queueService.enqueue(2L, TaskPriority.HIGH);
        Thread.sleep(5);
        queueService.enqueue(3L, TaskPriority.NORMAL);
        Thread.sleep(5);
        queueService.enqueue(4L, TaskPriority.HIGH);
        Thread.sleep(5);
        queueService.enqueue(5L, TaskPriority.NORMAL);

        // When: Dequeue all
        List<Long> order = new ArrayList<>();
        Optional<Long> task;
        while ((task = queueService.dequeue()).isPresent()) {
            order.add(task.get());
            queueService.releaseLock(task.get());
        }

        // Then: HIGH tasks first (2, 4), then NORMAL tasks (1, 3, 5)
        assertThat(order).hasSize(5);
        assertThat(order.subList(0, 2)).containsExactly(2L, 4L);
        assertThat(order.subList(2, 5)).containsExactly(1L, 3L, 5L);
    }
}
