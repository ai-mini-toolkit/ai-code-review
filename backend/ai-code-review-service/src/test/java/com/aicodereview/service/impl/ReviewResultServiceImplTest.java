package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewResultEntity;
import com.aicodereview.repository.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewResultServiceImpl.
 * Uses Mockito to mock repository dependencies and verify business logic.
 *
 * @since 5.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewResultServiceImpl Unit Tests")
class ReviewResultServiceImplTest {

    @Mock
    private ReviewResultRepository reviewResultRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @InjectMocks
    private ReviewResultServiceImpl reviewResultService;

    private ReviewTask testTask;

    @BeforeEach
    void setUp() {
        Project testProject = Project.builder()
                .id(1L)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .enabled(true)
                .build();

        testTask = ReviewTask.builder()
                .id(100L)
                .project(testProject)
                .status(TaskStatus.RUNNING)
                .build();
    }

    @Test
    @DisplayName("Should save successful review result with issues and statistics")
    void shouldSaveSuccessfulResult() {
        // Given
        List<ReviewIssue> issues = List.of(
                ReviewIssue.builder()
                        .severity(IssueSeverity.CRITICAL)
                        .category(IssueCategory.SECURITY)
                        .filePath("UserService.java")
                        .line(42)
                        .message("SQL injection")
                        .suggestion("Use PreparedStatement")
                        .build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH)
                        .category(IssueCategory.PERFORMANCE)
                        .filePath("Controller.java")
                        .line(10)
                        .message("N+1 query")
                        .suggestion("Use batch query")
                        .build()
        );

        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("anthropic")
                .model("claude-sonnet")
                .promptTokens(1000)
                .completionTokens(500)
                .durationMs(2000L)
                .build();

        ReviewResult reviewResult = ReviewResult.success(issues, metadata);

        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewResultRepository.existsByTaskId(100L)).thenReturn(false);
        when(reviewResultRepository.save(any(ReviewResultEntity.class)))
                .thenAnswer(invocation -> {
                    ReviewResultEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    entity.setCreatedAt(Instant.now());
                    return entity;
                });
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

        // When
        ReviewResultDTO result = reviewResultService.saveResult(100L, reviewResult);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getIssues()).hasSize(2);
        assertThat(result.getStatistics().getTotal()).isEqualTo(2);
        assertThat(result.getStatistics().getBySeverity().get("CRITICAL")).isEqualTo(1);
        assertThat(result.getStatistics().getBySeverity().get("HIGH")).isEqualTo(1);
        assertThat(result.getMetadata().getProviderId()).isEqualTo("anthropic");

        // Verify entity saved with correct JSONB
        ArgumentCaptor<ReviewResultEntity> entityCaptor = ArgumentCaptor.forClass(ReviewResultEntity.class);
        verify(reviewResultRepository).save(entityCaptor.capture());
        ReviewResultEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getIssues()).contains("CRITICAL");
        assertThat(savedEntity.getIssues()).contains("SECURITY");
        assertThat(savedEntity.getSuccess()).isTrue();

        // Verify task status updated
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(taskCaptor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should save failed review result with error message")
    void shouldSaveFailedResult() {
        // Given
        ReviewResult failedResult = ReviewResult.failed("API timeout: 30s exceeded");

        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewResultRepository.existsByTaskId(100L)).thenReturn(false);
        when(reviewResultRepository.save(any(ReviewResultEntity.class)))
                .thenAnswer(invocation -> {
                    ReviewResultEntity entity = invocation.getArgument(0);
                    entity.setId(2L);
                    entity.setCreatedAt(Instant.now());
                    return entity;
                });
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

        // When
        ReviewResultDTO result = reviewResultService.saveResult(100L, failedResult);

        // Then
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("API timeout: 30s exceeded");
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getStatistics().getTotal()).isZero();

        // Verify task still updated to COMPLETED (review process finished, result is failure)
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when task does not exist")
    void shouldThrowWhenTaskNotFound() {
        when(reviewTaskRepository.findById(999L)).thenReturn(Optional.empty());

        ReviewResult reviewResult = ReviewResult.success(List.of(), ReviewMetadata.builder().build());

        assertThatThrownBy(() -> reviewResultService.saveResult(999L, reviewResult))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reviewResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when task is not in RUNNING state")
    void shouldThrowWhenTaskNotRunning() {
        testTask.setStatus(TaskStatus.PENDING);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        ReviewResult reviewResult = ReviewResult.success(List.of(), ReviewMetadata.builder().build());

        assertThatThrownBy(() -> reviewResultService.saveResult(100L, reviewResult))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("expected RUNNING");

        verify(reviewResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when result already exists for task")
    void shouldThrowWhenResultAlreadyExists() {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewResultRepository.existsByTaskId(100L)).thenReturn(true);

        ReviewResult reviewResult = ReviewResult.success(List.of(), ReviewMetadata.builder().build());

        assertThatThrownBy(() -> reviewResultService.saveResult(100L, reviewResult))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("taskId");

        verify(reviewResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve result by task ID")
    void shouldGetResultByTaskId() {
        // Given
        ReviewResultEntity entity = ReviewResultEntity.builder()
                .id(1L)
                .reviewTask(testTask)
                .issues("[{\"severity\":\"HIGH\",\"category\":\"PERFORMANCE\",\"filePath\":\"Test.java\",\"line\":5,\"message\":\"issue\",\"suggestion\":\"fix\"}]")
                .statistics("{\"total\":1,\"bySeverity\":{\"HIGH\":1},\"byCategory\":{\"PERFORMANCE\":1}}")
                .metadata("{\"providerId\":\"openai\",\"model\":\"gpt-4\",\"promptTokens\":100,\"completionTokens\":50,\"durationMs\":1000,\"degradationEvents\":[]}")
                .success(true)
                .createdAt(Instant.now())
                .build();

        when(reviewResultRepository.findByReviewTaskId(100L)).thenReturn(Optional.of(entity));

        // When
        ReviewResultDTO result = reviewResultService.getResultByTaskId(100L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(result.getMetadata().getProviderId()).isEqualTo("openai");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when result not found for task")
    void shouldThrowWhenResultNotFound() {
        when(reviewResultRepository.findByReviewTaskId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewResultService.getResultByTaskId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
