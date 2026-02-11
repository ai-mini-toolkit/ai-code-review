package com.aicodereview.common.enums;

/**
 * Task priority enum used for queue ordering and scheduling.
 * <p>
 * Priority determines processing order in the Redis priority queue:
 * - HIGH (100): PR/MR tasks - require immediate review
 * - NORMAL (50): Push tasks - can be processed in batch
 * </p>
 * <p>
 * Priority scores are used in Redis Sorted Set score calculations to ensure
 * high priority tasks are always processed before normal priority tasks.
 * </p>
 *
 * @since 2.5.0
 */
public enum TaskPriority {

    /**
     * High priority for pull/merge requests.
     * These tasks require immediate review before code can be merged.
     * Score: 100
     */
    HIGH("High priority (PR/MR)", 100),

    /**
     * Normal priority for push events.
     * These tasks can be processed in batch mode.
     * Score: 50
     */
    NORMAL("Normal priority (Push)", 50);

    private final String description;
    private final int priorityScore;

    TaskPriority(String description, int priorityScore) {
        this.description = description;
        this.priorityScore = priorityScore;
    }

    /**
     * Gets the human-readable description of this priority level.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the numeric priority score used for queue ordering.
     * Higher scores = higher priority.
     *
     * @return the priority score (100 for HIGH, 50 for NORMAL)
     */
    public int getPriorityScore() {
        return priorityScore;
    }
}
