package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for ReviewTask entity.
 * <p>
 * Used for API responses and inter-layer communication.
 * Contains all task information in a flat structure (project ID instead of full Project entity).
 * </p>
 *
 * @since 2.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTaskDTO {

    /**
     * Unique task identifier.
     */
    private Long id;

    /**
     * Associated project ID.
     */
    private Long projectId;

    /**
     * Task type (PUSH, PULL_REQUEST, MERGE_REQUEST).
     */
    private TaskType taskType;

    /**
     * Git repository URL.
     */
    private String repoUrl;

    /**
     * Branch name.
     */
    private String branch;

    /**
     * Git commit SHA hash.
     */
    private String commitHash;

    /**
     * PR/MR number (nullable for PUSH tasks).
     */
    private Integer prNumber;

    /**
     * PR/MR title (nullable for PUSH tasks).
     */
    private String prTitle;

    /**
     * PR/MR description (nullable for PUSH tasks).
     */
    private String prDescription;

    /**
     * Commit author or PR/MR creator.
     */
    private String author;

    /**
     * Current task status.
     */
    private TaskStatus status;

    /**
     * Task priority for queue ordering.
     */
    private TaskPriority priority;

    /**
     * Number of retry attempts made.
     */
    private Integer retryCount;

    /**
     * Maximum retries allowed.
     */
    private Integer maxRetries;

    /**
     * Error message if failed (nullable).
     */
    private String errorMessage;

    /**
     * Task creation timestamp.
     */
    private Instant createdAt;

    /**
     * Task start timestamp (nullable).
     */
    private Instant startedAt;

    /**
     * Task completion timestamp (nullable).
     */
    private Instant completedAt;

    /**
     * Last update timestamp.
     */
    private Instant updatedAt;
}
