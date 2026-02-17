package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewSummaryDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
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
import com.aicodereview.service.ThresholdValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ThresholdValidationService thresholdValidationService;

    @InjectMocks
    private ReviewResultServiceImpl reviewResultService;

    private ReviewTask testTask;
    private static final ThresholdValidationResultDTO PASSED_RESULT = ThresholdValidationResultDTO.builder()
            .passed(true).violations(List.of()).action(null).build();

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
        when(thresholdValidationService.validate(eq(1L), any())).thenReturn(PASSED_RESULT);
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
        when(thresholdValidationService.validate(eq(1L), any())).thenReturn(PASSED_RESULT);
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

    @Nested
    @DisplayName("listResults - Paginated Query Tests")
    class ListResultsTests {

        private ReviewResultEntity createEntity(Long id, Long taskId, String projectName, boolean success) {
            Project project = Project.builder().id(taskId).name(projectName).build();
            ReviewTask task = ReviewTask.builder().id(taskId).project(project).branch("main").author("dev@test.com").build();
            return ReviewResultEntity.builder()
                    .id(id)
                    .reviewTask(task)
                    .statistics("{\"total\":2,\"bySeverity\":{},\"byCategory\":{}}")
                    .success(success)
                    .createdAt(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("Should call findAll when no filters provided")
        void shouldCallFindAllWhenNoFilters() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReviewResultEntity> page = new PageImpl<>(
                    List.of(createEntity(1L, 100L, "Project A", true)),
                    pageable, 1);
            when(reviewResultRepository.findAllWithAssociations(pageable)).thenReturn(page);

            Page<ReviewSummaryDTO> result = reviewResultService.listResults(null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getProjectName()).isEqualTo("Project A");
            verify(reviewResultRepository).findAllWithAssociations(pageable);
            verify(reviewResultRepository, never()).findByReviewTaskProjectId(any(), any());
        }

        @Test
        @DisplayName("Should call findByReviewTaskProjectId when only projectId provided")
        void shouldFilterByProjectIdOnly() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReviewResultEntity> page = new PageImpl<>(
                    List.of(createEntity(1L, 100L, "Project A", true)),
                    pageable, 1);
            when(reviewResultRepository.findByReviewTaskProjectId(eq(1L), eq(pageable))).thenReturn(page);

            Page<ReviewSummaryDTO> result = reviewResultService.listResults(1L, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(reviewResultRepository).findByReviewTaskProjectId(1L, pageable);
        }

        @Test
        @DisplayName("Should call findPageBySuccess when only success provided")
        void shouldFilterBySuccessOnly() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReviewResultEntity> page = new PageImpl<>(
                    List.of(createEntity(1L, 100L, "Project A", true)),
                    pageable, 1);
            when(reviewResultRepository.findPageBySuccess(eq(true), eq(pageable))).thenReturn(page);

            Page<ReviewSummaryDTO> result = reviewResultService.listResults(null, true, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getSuccess()).isTrue();
            verify(reviewResultRepository).findPageBySuccess(true, pageable);
        }

        @Test
        @DisplayName("Should call findByProjectIdAndSuccess when both filters provided")
        void shouldFilterByBothProjectIdAndSuccess() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReviewResultEntity> page = new PageImpl<>(
                    List.of(createEntity(1L, 100L, "Project A", false)),
                    pageable, 1);
            when(reviewResultRepository.findByProjectIdAndSuccess(eq(1L), eq(false), eq(pageable))).thenReturn(page);

            Page<ReviewSummaryDTO> result = reviewResultService.listResults(1L, false, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getSuccess()).isFalse();
            verify(reviewResultRepository).findByProjectIdAndSuccess(1L, false, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no results match")
        void shouldReturnEmptyPageWhenNoResults() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReviewResultEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(reviewResultRepository.findByReviewTaskProjectId(eq(999L), eq(pageable))).thenReturn(emptyPage);

            Page<ReviewSummaryDTO> result = reviewResultService.listResults(999L, null, pageable);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Threshold Validation Integration (Story 6.2)")
    class ThresholdValidationIntegration {

        @Test
        @DisplayName("Should invoke threshold validation during saveResult and persist result")
        void shouldInvokeThresholdValidationAndPersistResult() {
            // Given - threshold validation returns violations
            ThresholdValidationResultDTO thresholdResult = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("CRITICAL <= 0")
                                    .actual(1)
                                    .threshold(0)
                                    .build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();

            List<ReviewIssue> issues = List.of(
                    ReviewIssue.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .category(IssueCategory.SECURITY)
                            .filePath("Vuln.java")
                            .line(10)
                            .message("SQL injection")
                            .suggestion("Use parameterized query")
                            .build()
            );
            ReviewResult reviewResult = ReviewResult.success(issues, ReviewMetadata.builder()
                    .providerId("openai").model("gpt-4").build());

            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
            when(reviewResultRepository.existsByTaskId(100L)).thenReturn(false);
            when(thresholdValidationService.validate(eq(1L), any())).thenReturn(thresholdResult);
            when(reviewResultRepository.save(any(ReviewResultEntity.class)))
                    .thenAnswer(invocation -> {
                        ReviewResultEntity entity = invocation.getArgument(0);
                        entity.setId(10L);
                        entity.setCreatedAt(Instant.now());
                        return entity;
                    });
            when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

            // When
            ReviewResultDTO result = reviewResultService.saveResult(100L, reviewResult);

            // Then - threshold validation was invoked
            verify(thresholdValidationService).validate(eq(1L), any());

            // Entity stored with thresholdResult JSON
            ArgumentCaptor<ReviewResultEntity> entityCaptor = ArgumentCaptor.forClass(ReviewResultEntity.class);
            verify(reviewResultRepository).save(entityCaptor.capture());
            ReviewResultEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity.getThresholdResult()).isNotNull();
            assertThat(savedEntity.getThresholdResult()).contains("BLOCK_MERGE");
            assertThat(savedEntity.getThresholdResult()).contains("CRITICAL <= 0");

            // DTO contains deserialized threshold result
            assertThat(result.getThresholdResult()).isNotNull();
            assertThat(result.getThresholdResult().isPassed()).isFalse();
            assertThat(result.getThresholdResult().getViolations()).hasSize(1);
            assertThat(result.getThresholdResult().getAction()).isEqualTo("BLOCK_MERGE");
        }

        @Test
        @DisplayName("Should persist null thresholdResult for passed validation with no action")
        void shouldHandlePassedValidation() {
            ReviewResult reviewResult = ReviewResult.success(List.of(), ReviewMetadata.builder().build());

            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
            when(reviewResultRepository.existsByTaskId(100L)).thenReturn(false);
            when(thresholdValidationService.validate(eq(1L), any())).thenReturn(PASSED_RESULT);
            when(reviewResultRepository.save(any(ReviewResultEntity.class)))
                    .thenAnswer(invocation -> {
                        ReviewResultEntity entity = invocation.getArgument(0);
                        entity.setId(11L);
                        entity.setCreatedAt(Instant.now());
                        return entity;
                    });
            when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

            ReviewResultDTO result = reviewResultService.saveResult(100L, reviewResult);

            // Even passed result is persisted (not null — the validation ran)
            ArgumentCaptor<ReviewResultEntity> entityCaptor = ArgumentCaptor.forClass(ReviewResultEntity.class);
            verify(reviewResultRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getThresholdResult()).isNotNull();
            assertThat(entityCaptor.getValue().getThresholdResult()).contains("\"passed\":true");
        }
    }
}
