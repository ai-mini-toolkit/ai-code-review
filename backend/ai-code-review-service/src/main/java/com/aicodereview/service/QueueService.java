package com.aicodereview.service;

import com.aicodereview.common.enums.TaskPriority;

import java.util.Optional;

/**
 * Service interface for Redis priority queue operations.
 * <p>
 * Manages a priority queue backed by Redis Sorted Set for code review tasks.
 * Score formula: {@code (MAX_PRIORITY - priority.getPriorityScore()) * 1e13 + timestampMillis}
 * </p>
 * <p>
 * This ensures HIGH priority tasks (score 100) are always dequeued before
 * NORMAL priority tasks (score 50), with FIFO ordering within the same priority level.
 * ZPOPMIN retrieves the lowest score first, which corresponds to the highest priority + earliest time.
 * </p>
 * <p>
 * Processing locks use Redis String keys with TTL to prevent duplicate processing
 * in distributed environments. Lock key format: {@code task:lock:{taskId}}
 * </p>
 *
 * @since 2.6.0
 */
public interface QueueService {

    /**
     * Adds a task to the priority queue.
     * <p>
     * Score calculation:
     * {@code (MAX_PRIORITY - priority.getPriorityScore()) * 1e13 + System.currentTimeMillis()}
     * </p>
     *
     * @param taskId   the task ID to enqueue
     * @param priority the task priority determining queue position
     */
    void enqueue(Long taskId, TaskPriority priority);

    /**
     * Atomically pops the highest-priority task from the queue and acquires a processing lock.
     * <p>
     * Uses ZPOPMIN to retrieve and remove the task with the lowest score
     * (highest priority + earliest timestamp). After dequeue, a processing lock
     * is acquired with 300-second TTL to prevent duplicate processing.
     * </p>
     * <p>
     * If lock acquisition fails (another worker already holds the lock),
     * the task is re-enqueued and empty is returned.
     * </p>
     *
     * @return Optional containing the taskId if a task was dequeued, or empty if queue is empty
     */
    Optional<Long> dequeue();

    /**
     * Re-adds a task to the queue with a delay offset for retry purposes.
     * <p>
     * Releases any existing processing lock before re-enqueue.
     * The new score includes a delay offset: current timestamp + delaySeconds * 1000.
     * </p>
     *
     * @param taskId       the task ID to requeue
     * @param priority     the task priority for score calculation
     * @param delaySeconds delay in seconds before the task becomes eligible for dequeue
     */
    void requeueWithDelay(Long taskId, TaskPriority priority, int delaySeconds);

    /**
     * Returns the current number of tasks in the queue.
     *
     * @return queue size, or 0 if queue is empty
     */
    long getQueueSize();

    /**
     * Checks if a task has an active processing lock.
     *
     * @param taskId the task ID to check
     * @return true if the task has an active lock, false otherwise
     */
    boolean isLocked(Long taskId);

    /**
     * Manually releases a task's processing lock.
     * <p>
     * Called after task processing completes (success or final failure)
     * to allow the task to be re-processed if needed.
     * </p>
     *
     * @param taskId the task ID whose lock should be released
     */
    void releaseLock(Long taskId);
}
