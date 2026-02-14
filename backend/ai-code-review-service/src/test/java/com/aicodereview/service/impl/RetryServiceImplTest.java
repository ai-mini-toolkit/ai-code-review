package com.aicodereview.service.impl;

import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.FailureType;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.service.QueueService;
import com.aicodereview.service.ReviewTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetryServiceImpl}.
 *
 * @since 2.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetryServiceImpl Unit Tests")
class RetryServiceImplTest {

    @Mock
    private ReviewTaskService reviewTaskService;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private RetryServiceImpl retryService;

    // --- Error Classification Tests ---

    @Nested
    @DisplayName("Error Classification (isRetryable)")
    class ErrorClassificationTests {

        @ParameterizedTest
        @EnumSource(value = FailureType.class, names = {"RATE_LIMIT", "NETWORK_ERROR", "TIMEOUT", "UNKNOWN"})
        @DisplayName("Retryable failure types return true")
        void isRetryable_retryableTypes_returnsTrue(FailureType failureType) {
            assertThat(retryService.isRetryable(failureType)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = FailureType.class, names = {"VALIDATION_ERROR", "AUTHENTICATION_ERROR"})
        @DisplayName("Non-retryable failure types return false")
        void isRetryable_nonRetryableTypes_returnsFalse(FailureType failureType) {
            assertThat(retryService.isRetryable(failureType)).isFalse();
        }
    }

    // --- Delay Calculation Tests ---

    @Nested
    @DisplayName("Delay Calculation (calculateRetryDelaySeconds)")
    class DelayCalculationTests {

        @Test
        @DisplayName("First retry (retryCount=0): base delay is 1s (2^0)")
        void calculateDelay_firstRetry_baseDelayIs1() {
            int delay = retryService.calculateRetryDelaySeconds(0);
            assertThat(delay).isBetween(1, 2); // 1s base + 0 or 1s jitter
        }

        @Test
        @DisplayName("Second retry (retryCount=1): base delay is 2s (2^1)")
        void calculateDelay_secondRetry_baseDelayIs2() {
            int delay = retryService.calculateRetryDelaySeconds(1);
            assertThat(delay).isBetween(2, 3); // 2s base + 0 or 1s jitter
        }

        @Test
        @DisplayName("Third retry (retryCount=2): base delay is 4s (2^2)")
        void calculateDelay_thirdRetry_baseDelayIs4() {
            int delay = retryService.calculateRetryDelaySeconds(2);
            assertThat(delay).isBetween(4, 5); // 4s base + 0 or 1s jitter
        }

        @RepeatedTest(20)
        @DisplayName("Jitter produces values within expected range across multiple runs")
        void calculateDelay_jitter_withinExpectedRange() {
            int delay = retryService.calculateRetryDelaySeconds(0);
            assertThat(delay).isBetween(1, 2);
        }
    }

    // --- handleTaskFailure Tests ---

    @Nested
    @DisplayName("handleTaskFailure - Retryable Errors")
    class RetryableErrorTests {

        @Test
        @DisplayName("RATE_LIMIT failure: calls markTaskFailed and requeueWithDelay")
        void handleFailure_rateLimit_retriesWithDelay() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(1L).status(TaskStatus.PENDING)
                    .priority(TaskPriority.HIGH)
                    .retryCount(1).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(1L, "429 Too Many Requests")).thenReturn(dto);

            retryService.handleTaskFailure(1L, "429 Too Many Requests", FailureType.RATE_LIMIT);

            verify(reviewTaskService).markTaskFailed(1L, "429 Too Many Requests");
            verify(queueService).requeueWithDelay(eq(1L), eq(TaskPriority.HIGH), anyInt());
            verify(reviewTaskService, never()).markTaskFailedPermanently(anyLong(), anyString());
        }

        @Test
        @DisplayName("NETWORK_ERROR failure: calls markTaskFailed and requeueWithDelay")
        void handleFailure_networkError_retriesWithDelay() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(2L).status(TaskStatus.PENDING)
                    .priority(TaskPriority.NORMAL)
                    .retryCount(1).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(2L, "Connection refused")).thenReturn(dto);

            retryService.handleTaskFailure(2L, "Connection refused", FailureType.NETWORK_ERROR);

            verify(reviewTaskService).markTaskFailed(2L, "Connection refused");
            verify(queueService).requeueWithDelay(eq(2L), eq(TaskPriority.NORMAL), anyInt());
        }

        @Test
        @DisplayName("TIMEOUT failure: calls markTaskFailed and requeueWithDelay")
        void handleFailure_timeout_retriesWithDelay() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(3L).status(TaskStatus.PENDING)
                    .priority(TaskPriority.HIGH)
                    .retryCount(1).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(3L, "Request timeout")).thenReturn(dto);

            retryService.handleTaskFailure(3L, "Request timeout", FailureType.TIMEOUT);

            verify(reviewTaskService).markTaskFailed(3L, "Request timeout");
            verify(queueService).requeueWithDelay(eq(3L), eq(TaskPriority.HIGH), anyInt());
        }

        @Test
        @DisplayName("UNKNOWN failure: treated as retryable (conservative)")
        void handleFailure_unknown_treatedAsRetryable() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(4L).status(TaskStatus.PENDING)
                    .priority(TaskPriority.HIGH)
                    .retryCount(1).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(4L, "Unexpected error")).thenReturn(dto);

            retryService.handleTaskFailure(4L, "Unexpected error", FailureType.UNKNOWN);

            verify(reviewTaskService).markTaskFailed(4L, "Unexpected error");
            verify(queueService).requeueWithDelay(eq(4L), eq(TaskPriority.HIGH), anyInt());
        }
    }

    @Nested
    @DisplayName("handleTaskFailure - Non-Retryable Errors")
    class NonRetryableErrorTests {

        @Test
        @DisplayName("VALIDATION_ERROR: calls markTaskFailedPermanently, NO requeue")
        void handleFailure_validationError_permanentFail() {
            retryService.handleTaskFailure(1L, "Invalid payload", FailureType.VALIDATION_ERROR);

            verify(reviewTaskService).markTaskFailedPermanently(1L, "Invalid payload");
            verify(reviewTaskService, never()).markTaskFailed(anyLong(), anyString());
            verify(queueService, never()).requeueWithDelay(anyLong(), any(), anyInt());
        }

        @Test
        @DisplayName("AUTHENTICATION_ERROR: calls markTaskFailedPermanently, NO requeue")
        void handleFailure_authError_permanentFail() {
            retryService.handleTaskFailure(2L, "401 Unauthorized", FailureType.AUTHENTICATION_ERROR);

            verify(reviewTaskService).markTaskFailedPermanently(2L, "401 Unauthorized");
            verify(reviewTaskService, never()).markTaskFailed(anyLong(), anyString());
            verify(queueService, never()).requeueWithDelay(anyLong(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("handleTaskFailure - Max Retries Exhausted")
    class MaxRetriesTests {

        @Test
        @DisplayName("Max retries reached: FAILED status, NO requeue, lock released")
        void handleFailure_maxRetries_noRequeue_lockReleased() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(1L).status(TaskStatus.FAILED)
                    .priority(TaskPriority.HIGH)
                    .retryCount(3).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(1L, "Third failure")).thenReturn(dto);

            retryService.handleTaskFailure(1L, "Third failure", FailureType.RATE_LIMIT);

            verify(reviewTaskService).markTaskFailed(1L, "Third failure");
            verify(queueService, never()).requeueWithDelay(anyLong(), any(), anyInt());
            verify(queueService).releaseLock(1L);
        }

        @Test
        @DisplayName("Max retries reached: Redis lock release failure should not throw")
        void handleFailure_maxRetries_lockReleaseFailure_noException() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(1L).status(TaskStatus.FAILED)
                    .priority(TaskPriority.HIGH)
                    .retryCount(3).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(1L, "Third failure")).thenReturn(dto);
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(queueService).releaseLock(anyLong());

            // Should NOT throw exception
            retryService.handleTaskFailure(1L, "Third failure", FailureType.RATE_LIMIT);

            verify(queueService).releaseLock(1L);
        }
    }

    @Nested
    @DisplayName("handleTaskFailure - Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Null failureType should throw NullPointerException")
        void handleFailure_nullFailureType_throwsNPE() {
            assertThatThrownBy(() ->
                    retryService.handleTaskFailure(1L, "error", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("failureType must not be null");
        }
    }

    @Nested
    @DisplayName("handleTaskFailure - Redis Failure Resilience")
    class RedisResilienceTests {

        @Test
        @DisplayName("Redis requeue failure: DB state still updated, no exception thrown")
        void handleFailure_redisFailure_dbStateStillSaved() {
            ReviewTaskDTO dto = ReviewTaskDTO.builder()
                    .id(1L).status(TaskStatus.PENDING)
                    .priority(TaskPriority.HIGH)
                    .retryCount(1).maxRetries(3).build();
            when(reviewTaskService.markTaskFailed(1L, "Some error")).thenReturn(dto);
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(queueService).requeueWithDelay(anyLong(), any(), anyInt());

            // Should NOT throw exception
            retryService.handleTaskFailure(1L, "Some error", FailureType.RATE_LIMIT);

            verify(reviewTaskService).markTaskFailed(1L, "Some error");
            verify(queueService).requeueWithDelay(eq(1L), eq(TaskPriority.HIGH), anyInt());
        }
    }
}
