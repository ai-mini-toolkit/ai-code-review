package com.aicodereview.common.constant;

/**
 * Redis key constants for queue operations.
 * <p>
 * Key naming convention follows architecture.md:
 * - Task Queue: {@code task:queue} (Redis Sorted Set)
 * - Processing Lock: {@code task:lock:{taskId}} (Redis String with TTL)
 * - Retry Queue: {@code task:retry:queue} (for future use)
 * </p>
 *
 * @since 2.6.0
 */
public final class QueueKeys {

    private QueueKeys() {
        // Prevent instantiation
    }

    /** Redis Sorted Set key for the priority task queue */
    public static final String TASK_QUEUE = "task:queue";

    /** Prefix for task processing lock keys (append taskId) */
    public static final String TASK_LOCK_PREFIX = "task:lock:";

    /** Redis Sorted Set key for the retry queue (Story 2.7) */
    public static final String TASK_RETRY_QUEUE = "task:retry:queue";

    /**
     * Generates the full Redis key for a task processing lock.
     *
     * @param taskId the task ID
     * @return the lock key in format {@code task:lock:{taskId}}
     */
    public static String taskLockKey(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        return TASK_LOCK_PREFIX + taskId;
    }
}
