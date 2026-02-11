package com.aicodereview.common.enums;

/**
 * Task type enum representing the source event that triggered the code review task.
 * <p>
 * Different task types have different priorities and handling requirements:
 * - PULL_REQUEST/MERGE_REQUEST: High priority (immediate review required)
 * - PUSH: Normal priority (batch review acceptable)
 * </p>
 *
 * @since 2.5.0
 */
public enum TaskType {

    /**
     * Code push event - triggered by direct commits to repository.
     * Priority: NORMAL
     */
    PUSH("Push event"),

    /**
     * GitHub Pull Request event - triggered by PR creation/update.
     * Priority: HIGH
     */
    PULL_REQUEST("GitHub Pull Request event"),

    /**
     * GitLab Merge Request event - triggered by MR creation/update.
     * Priority: HIGH
     */
    MERGE_REQUEST("GitLab Merge Request event");

    private final String description;

    TaskType(String description) {
        this.description = description;
    }

    /**
     * Gets the human-readable description of this task type.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }
}
