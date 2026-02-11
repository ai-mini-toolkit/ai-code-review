package com.aicodereview.repository.entity;

import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * ReviewTask entity representing a code review task.
 * <p>
 * Tasks are created from webhook events (push, pull_request, merge_request)
 * and tracked throughout their lifecycle (PENDING → RUNNING → COMPLETED/FAILED).
 * </p>
 * <p>
 * Each task is associated with a Project and contains all necessary information
 * to perform the code review, including repository details, commit hash, and
 * PR/MR metadata if applicable.
 * </p>
 *
 * @since 2.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "review_task")
@EntityListeners(AuditingEntityListener.class)
public class ReviewTask {

    /**
     * Unique identifier for the task.
     * Auto-generated using database sequence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Associated project for this review task.
     * Lazy-loaded to avoid N+1 queries.
     * Cascade delete ensures tasks are removed when project is deleted.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_review_task_project"))
    private Project project;

    /**
     * Type of task based on webhook event source.
     * PUSH, PULL_REQUEST, or MERGE_REQUEST.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    /**
     * Git repository URL from webhook event.
     * Used to clone repository and fetch code changes.
     */
    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    /**
     * Branch name where changes occurred.
     * Example: main, develop, feature/user-auth
     */
    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    /**
     * Git commit SHA hash (40 character hex string).
     * Used to fetch exact commit diff from repository.
     */
    @Column(name = "commit_hash", nullable = false, length = 255)
    private String commitHash;

    /**
     * Pull Request or Merge Request number.
     * Nullable for PUSH tasks (only set for PR/MR events).
     */
    @Column(name = "pr_number")
    private Integer prNumber;

    /**
     * PR/MR title from webhook.
     * Nullable for PUSH tasks.
     */
    @Column(name = "pr_title", columnDefinition = "TEXT")
    private String prTitle;

    /**
     * PR/MR description/body from webhook.
     * Nullable for PUSH tasks.
     */
    @Column(name = "pr_description", columnDefinition = "TEXT")
    private String prDescription;

    /**
     * Commit author or PR/MR creator.
     * Used for notifications and audit trail.
     */
    @Column(name = "author", nullable = false, length = 255)
    private String author;

    /**
     * Current task status in lifecycle.
     * PENDING → RUNNING → (COMPLETED | FAILED)
     * Defaults to PENDING when task is created.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * Task priority for queue ordering.
     * HIGH for PR/MR (immediate review), NORMAL for PUSH (batch review).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority;

    /**
     * Number of retry attempts made for this task.
     * Incremented each time markTaskFailed() is called.
     * Defaults to 0.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum number of retries allowed before giving up.
     * Defaults to 3.
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * Error message if task failed.
     * Used for debugging and retry decision logic.
     * Nullable (only set when status = FAILED).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Task creation timestamp (when webhook was received).
     * Auto-populated by JPA auditing.
     * Immutable after creation.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Task execution start timestamp (when worker picked up task).
     * Set by markTaskStarted() method in service layer.
     * Nullable (only set when task starts processing).
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Task completion timestamp (success or final failure).
     * Set by markTaskCompleted() or markTaskFailed() when retry_count >= max_retries.
     * Nullable (only set when task reaches terminal state).
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Last update timestamp.
     * Auto-updated by JPA auditing on any entity modification.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
