package com.aicodereview.service;

/**
 * Service for sending email notifications after code review completion.
 * <p>
 * Sends review-complete and threshold-violation notifications to configured recipients.
 * Failures are logged but never propagate to callers.
 * </p>
 *
 * @since 7.1.0
 */
public interface EmailNotificationService {

    /**
     * Sends a review completion email notification.
     *
     * @param taskId the review task ID
     */
    void sendReviewCompleteNotification(Long taskId);

    /**
     * Sends a threshold violation email notification with emphasis on violations.
     *
     * @param taskId the review task ID
     */
    void sendThresholdViolationNotification(Long taskId);
}
