package com.aicodereview.service.impl;

import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.FailureType;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.service.QueueService;
import com.aicodereview.service.RetryService;
import com.aicodereview.service.ReviewTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link RetryService} for task retry orchestration.
 * <p>
 * Retry strategy:
 * - Retryable errors (RATE_LIMIT, NETWORK_ERROR, TIMEOUT, UNKNOWN):
 *   Update DB state, requeue with exponential backoff delay
 * - Non-retryable errors (VALIDATION_ERROR, AUTHENTICATION_ERROR):
 *   Immediately mark task as permanently failed
 * </p>
 * <p>
 * Exponential backoff: 2^retryCount seconds + random jitter (0-1s).
 * Redis requeue is best-effort — DB is the primary record.
 * </p>
 *
 * @since 2.7.0
 */
@Slf4j
@Service
public class RetryServiceImpl implements RetryService {

    private final ReviewTaskService reviewTaskService;
    private final QueueService queueService;

    public RetryServiceImpl(ReviewTaskService reviewTaskService, QueueService queueService) {
        this.reviewTaskService = reviewTaskService;
        this.queueService = queueService;
    }

    @Override
    public void handleTaskFailure(Long taskId, String errorMessage, FailureType failureType) {
        Objects.requireNonNull(failureType, "failureType must not be null");

        if (!isRetryable(failureType)) {
            log.warn("Non-retryable failure for task {}: type={}, error={}", taskId, failureType, errorMessage);
            reviewTaskService.markTaskFailedPermanently(taskId, errorMessage);
            return;
        }

        // Retryable error: update DB state (increment retry_count, check max)
        ReviewTaskDTO updated = reviewTaskService.markTaskFailed(taskId, errorMessage);

        if (updated.getStatus() == TaskStatus.PENDING) {
            // Task still has retries left — requeue with exponential backoff delay
            int delay = calculateRetryDelaySeconds(updated.getRetryCount() - 1);
            try {
                queueService.requeueWithDelay(taskId, updated.getPriority(), delay);
                log.info("Requeued task {} with {}s delay (attempt {}/{})",
                        taskId, delay, updated.getRetryCount(), updated.getMaxRetries());
            } catch (Exception e) {
                log.error("Failed to requeue task {} to Redis. DB state saved but not queued.", taskId, e);
            }
        } else {
            // Max retries exhausted → FAILED — release lock to prevent orphaned Redis keys
            log.warn("Task {} permanently failed after {} retries: {}", taskId, updated.getRetryCount(), errorMessage);
            try {
                queueService.releaseLock(taskId);
            } catch (Exception e) {
                log.error("Failed to release lock for max-retried task {}", taskId, e);
            }
        }
    }

    @Override
    public int calculateRetryDelaySeconds(int retryCount) {
        int baseDelay = (int) Math.pow(2, retryCount); // 1, 2, 4
        int jitter = ThreadLocalRandom.current().nextInt(0, 2); // 0 or 1 (~50% chance)
        return baseDelay + jitter;
    }

    @Override
    public boolean isRetryable(FailureType failureType) {
        return failureType.isRetryable();
    }
}
