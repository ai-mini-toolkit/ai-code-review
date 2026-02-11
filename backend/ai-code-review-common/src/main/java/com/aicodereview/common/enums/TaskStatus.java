package com.aicodereview.common.enums;

/**
 * Task status enum representing the current lifecycle state of a code review task.
 * <p>
 * Task lifecycle: PENDING → RUNNING → (COMPLETED | FAILED)
 * </p>
 * <p>
 * Status transitions:
 * - PENDING: Task created and waiting in queue
 * - RUNNING: Task picked up by worker and actively being processed
 * - COMPLETED: Task successfully finished without errors
 * - FAILED: Task failed after all retry attempts exhausted
 * </p>
 *
 * @since 2.5.0
 */
public enum TaskStatus {

    /**
     * Task is waiting in queue for processing.
     * Initial status when task is created.
     */
    PENDING("Waiting for processing"),

    /**
     * Task is currently being executed by a worker.
     * Set when worker calls markTaskStarted().
     */
    RUNNING("Currently executing"),

    /**
     * Task completed successfully.
     * Set when worker calls markTaskCompleted().
     */
    COMPLETED("Successfully completed"),

    /**
     * Task failed after all retry attempts.
     * Set when worker calls markTaskFailed() and retry_count >= max_retries.
     */
    FAILED("Failed after retries");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    /**
     * Gets the human-readable description of this task status.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }
}
