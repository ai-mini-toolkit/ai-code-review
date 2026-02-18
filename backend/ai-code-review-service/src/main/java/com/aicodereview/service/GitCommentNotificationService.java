package com.aicodereview.service;

/**
 * Service for posting AI code review summary comments on Git platform PR/MR.
 * <p>
 * Dispatches to platform-specific services (GitHub, GitLab, AWS CodeCommit)
 * based on the project's configured Git platform.
 * </p>
 *
 * @since 7.2.0
 */
public interface GitCommentNotificationService {

    /**
     * Posts a review summary comment on the PR/MR associated with the given task.
     * <p>
     * Silently skips if:
     * <ul>
     *   <li>Task not found or has no project</li>
     *   <li>Task has no PR number (PUSH tasks)</li>
     *   <li>Comment notifications not enabled in project config</li>
     *   <li>Platform token not configured</li>
     * </ul>
     * Never throws exceptions — failures are logged at WARN level.
     * </p>
     *
     * @param taskId the review task ID
     */
    void postReviewComment(Long taskId);
}
