package com.aicodereview.service;

/**
 * Service for sending threshold violation notifications via IM platforms
 * (DingTalk, Slack, Lark/Feishu).
 * <p>
 * Only triggered when threshold validation fails — unlike email (both pass/fail)
 * or git comments (always). This avoids unnecessary interruptions to the team.
 * </p>
 *
 * @since 7.3.0
 */
public interface IMNotificationService {

    /**
     * Sends threshold violation notifications to all enabled IM platforms
     * for the given review task.
     * <p>
     * Silently skips if:
     * <ul>
     *   <li>Task not found or has no project</li>
     *   <li>No notification config exists for the project</li>
     *   <li>No IM platforms are enabled</li>
     * </ul>
     * Individual platform failures are logged at WARN level and do not affect other platforms.
     * Never throws exceptions — all failures are handled internally.
     * </p>
     *
     * @param taskId the review task ID
     */
    void sendThresholdViolationNotifications(Long taskId);
}
