package com.aicodereview.service;

import com.aicodereview.common.enums.FailureType;

/**
 * Service interface for task retry orchestration.
 * <p>
 * Handles the retry decision logic for failed tasks:
 * - Classifies errors as retryable or non-retryable
 * - Calculates exponential backoff delay with jitter
 * - Orchestrates DB state updates and queue requeue operations
 * </p>
 * <p>
 * Retry delay formula: {@code 2^retryCount} seconds + random jitter (0-1s).
 * Delay sequence: 1s, 2s, 4s (for max_retries=3).
 * </p>
 *
 * @since 2.7.0
 */
public interface RetryService {

    /**
     * Handles a task failure by classifying the error and deciding retry strategy.
     * <p>
     * For retryable errors: updates DB state via {@code markTaskFailed()},
     * then requeues with exponential backoff delay.
     * For non-retryable errors: immediately marks task as permanently failed.
     * </p>
     *
     * @param taskId       the failed task ID
     * @param errorMessage the error description
     * @param failureType  the classified failure type
     */
    void handleTaskFailure(Long taskId, String errorMessage, FailureType failureType);

    /**
     * Calculates retry delay in seconds using exponential backoff with jitter.
     * <p>
     * Formula: {@code 2^retryCount + jitter(0-1s)}
     * </p>
     *
     * @param retryCount the current retry count (0-based, before this attempt)
     * @return delay in seconds (minimum 1)
     */
    int calculateRetryDelaySeconds(int retryCount);

    /**
     * Determines if a failure type allows retry.
     *
     * @param failureType the failure type to check
     * @return true if retryable, false if permanent failure
     */
    boolean isRetryable(FailureType failureType);
}
