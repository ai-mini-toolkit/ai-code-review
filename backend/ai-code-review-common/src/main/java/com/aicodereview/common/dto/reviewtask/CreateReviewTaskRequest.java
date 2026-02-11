package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new code review task.
 * <p>
 * Used by WebhookController when processing webhook events.
 * Contains all required information to create a task, with optional PR/MR fields.
 * </p>
 * <p>
 * Validation rules enforce data integrity at API boundary:
 * - All core fields (projectId, taskType, repoUrl, branch, commitHash, author) are required
 * - PR/MR fields (prNumber, prTitle, prDescription) are optional
 * - Maximum lengths match database column constraints
 * </p>
 *
 * @since 2.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewTaskRequest {

    /**
     * Project ID for this task.
     * Must reference an existing project in the database.
     */
    @NotNull(message = "Project ID is required")
    private Long projectId;

    /**
     * Task type based on webhook event source.
     * Must be PUSH, PULL_REQUEST, or MERGE_REQUEST.
     */
    @NotNull(message = "Task type is required")
    private TaskType taskType;

    /**
     * Git repository URL from webhook.
     * Must be non-blank and <= 500 characters.
     */
    @NotBlank(message = "Repository URL is required")
    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    private String repoUrl;

    /**
     * Branch name where changes occurred.
     * Must be non-blank and <= 255 characters.
     */
    @NotBlank(message = "Branch is required")
    @Size(max = 255, message = "Branch must not exceed 255 characters")
    private String branch;

    /**
     * Git commit SHA hash (40 character hex string).
     * Must be non-blank and <= 255 characters.
     */
    @NotBlank(message = "Commit hash is required")
    @Size(max = 255, message = "Commit hash must not exceed 255 characters")
    private String commitHash;

    /**
     * PR/MR number from webhook.
     * Optional - only set for PULL_REQUEST and MERGE_REQUEST tasks.
     */
    private Integer prNumber;

    /**
     * PR/MR title from webhook.
     * Optional - only set for PULL_REQUEST and MERGE_REQUEST tasks.
     */
    private String prTitle;

    /**
     * PR/MR description from webhook.
     * Optional - only set for PULL_REQUEST and MERGE_REQUEST tasks.
     */
    private String prDescription;

    /**
     * Commit author or PR/MR creator.
     * Must be non-blank and <= 255 characters.
     */
    @NotBlank(message = "Author is required")
    @Size(max = 255, message = "Author must not exceed 255 characters")
    private String author;
}
