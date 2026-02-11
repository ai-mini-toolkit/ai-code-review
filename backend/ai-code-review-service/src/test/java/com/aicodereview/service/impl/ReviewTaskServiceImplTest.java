package com.aicodereview.service.impl;

import com.aicodereview.common.dto.reviewtask.CreateReviewTaskRequest;
import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewTaskServiceImpl.
 * <p>
 * Uses Mockito to mock repository dependencies and verify business logic.
 * Covers all public methods including:
 * - Task creation with priority assignment
 * - Status transitions (PENDING → RUNNING → COMPLETED/FAILED)
 * - Retry logic and failure handling
 * - Task querying and retrieval
 * </p>
 *
 * @since 2.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewTaskServiceImpl Unit Tests")
class ReviewTaskServiceImplTest {

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ReviewTaskServiceImpl reviewTaskService;

    private Project testProject;
    private ReviewTask testTask;
    private CreateReviewTaskRequest testRequest;

    @BeforeEach
    void setUp() {
        // Inject @Value field that @InjectMocks doesn't handle
        ReflectionTestUtils.setField(reviewTaskService, "defaultMaxRetries", 3);

        // Set up test project
        testProject = Project.builder()
                .id(1L)
                .name("Test Project")
                .repoUrl("https://github.com/user/repo")
                .enabled(true)
                .build();

        // Set up test task
        testTask = ReviewTask.builder()
                .id(100L)
                .project(testProject)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/user/repo")
                .branch("main")
                .commitHash("abc123")
                .author("testuser")
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.NORMAL)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(Instant.now())
                .build();

        // Set up test request
        testRequest = CreateReviewTaskRequest.builder()
                .projectId(1L)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/user/repo")
                .branch("main")
                .commitHash("abc123")
                .author("testuser")
                .build();
    }

    @Test
    @DisplayName("createTask - success with PUSH event should assign NORMAL priority")
    void testCreateTask_Success() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(reviewTaskRepository.findByProjectIdAndCommitHash(1L, "abc123")).thenReturn(Optional.empty());
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

        // When
        ReviewTaskDTO result = reviewTaskService.createTask(testRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getProjectId()).isEqualTo(1L);
        assertThat(result.getTaskType()).isEqualTo(TaskType.PUSH);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.getPriority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(result.getRetryCount()).isEqualTo(0);
        assertThat(result.getMaxRetries()).isEqualTo(3);

        // Verify project lookup
        verify(projectRepository).findById(1L);

        // Verify task saved with correct priority and maxRetries
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        ReviewTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getProject()).isEqualTo(testProject);
        assertThat(savedTask.getPriority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(savedTask.getMaxRetries()).isEqualTo(3);
        assertThat(savedTask.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("createTask - PULL_REQUEST should assign HIGH priority")
    void testCreateTask_PullRequest_HighPriority() {
        // Given
        testRequest.setTaskType(TaskType.PULL_REQUEST);
        testRequest.setPrNumber(42);
        testRequest.setPrTitle("Test PR");

        testTask.setTaskType(TaskType.PULL_REQUEST);
        testTask.setPriority(TaskPriority.HIGH);
        testTask.setPrNumber(42);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(reviewTaskRepository.findByProjectIdAndCommitHash(1L, "abc123")).thenReturn(Optional.empty());
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

        // When
        ReviewTaskDTO result = reviewTaskService.createTask(testRequest);

        // Then
        assertThat(result.getTaskType()).isEqualTo(TaskType.PULL_REQUEST);
        assertThat(result.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.getPrNumber()).isEqualTo(42);

        // Verify priority set to HIGH for PR
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("createTask - MERGE_REQUEST should assign HIGH priority")
    void testCreateTask_MergeRequest_HighPriority() {
        // Given
        testRequest.setTaskType(TaskType.MERGE_REQUEST);
        testRequest.setPrNumber(15);

        testTask.setTaskType(TaskType.MERGE_REQUEST);
        testTask.setPriority(TaskPriority.HIGH);
        testTask.setPrNumber(15);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(reviewTaskRepository.findByProjectIdAndCommitHash(1L, "abc123")).thenReturn(Optional.empty());
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(testTask);

        // When
        ReviewTaskDTO result = reviewTaskService.createTask(testRequest);

        // Then
        assertThat(result.getTaskType()).isEqualTo(TaskType.MERGE_REQUEST);
        assertThat(result.getPriority()).isEqualTo(TaskPriority.HIGH);

        // Verify priority
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("createTask - project not found should throw ResourceNotFoundException")
    void testCreateTask_ProjectNotFound() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.createTask(testRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: 1");

        // Verify no task saved
        verify(reviewTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("getTaskById - success should return task DTO")
    void testGetTaskById_Success() {
        // Given
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When
        ReviewTaskDTO result = reviewTaskService.getTaskById(100L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getCommitHash()).isEqualTo("abc123");
        verify(reviewTaskRepository).findById(100L);
    }

    @Test
    @DisplayName("getTaskById - task not found should throw ResourceNotFoundException")
    void testGetTaskById_NotFound() {
        // Given
        when(reviewTaskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.getTaskById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ReviewTask not found with id: 999");
    }

    @Test
    @DisplayName("getTasksByProjectId - should return all tasks for project")
    void testGetTasksByProjectId() {
        // Given
        ReviewTask task1 = ReviewTask.builder().id(1L).project(testProject).commitHash("abc123").build();
        ReviewTask task2 = ReviewTask.builder().id(2L).project(testProject).commitHash("def456").build();
        when(reviewTaskRepository.findByProjectId(1L)).thenReturn(Arrays.asList(task1, task2));

        // When
        List<ReviewTaskDTO> results = reviewTaskService.getTasksByProjectId(1L);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(1L);
        assertThat(results.get(1).getId()).isEqualTo(2L);
        verify(reviewTaskRepository).findByProjectId(1L);
    }

    @Test
    @DisplayName("getTasksByStatus - should return tasks ordered by priority DESC and created_at ASC")
    void testGetTasksByStatus_OrderedByPriority() {
        // Given: 3 tasks with different priorities and creation times
        ReviewTask highPriorityTask = ReviewTask.builder()
                .id(1L)
                .project(testProject)
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.PENDING)
                .createdAt(Instant.now().minusSeconds(100))
                .build();

        ReviewTask normalPriorityOld = ReviewTask.builder()
                .id(2L)
                .project(testProject)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.PENDING)
                .createdAt(Instant.now().minusSeconds(200))
                .build();

        ReviewTask normalPriorityNew = ReviewTask.builder()
                .id(3L)
                .project(testProject)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.PENDING)
                .createdAt(Instant.now().minusSeconds(50))
                .build();

        // Repository should return in correct order: HIGH first, then NORMAL (oldest first)
        when(reviewTaskRepository.findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus.PENDING))
                .thenReturn(Arrays.asList(highPriorityTask, normalPriorityOld, normalPriorityNew));

        // When
        List<ReviewTaskDTO> results = reviewTaskService.getTasksByStatus(TaskStatus.PENDING);

        // Then: Verify order is correct
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isEqualTo(1L); // HIGH priority
        assertThat(results.get(0).getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(results.get(1).getId()).isEqualTo(2L); // NORMAL priority, older
        assertThat(results.get(2).getId()).isEqualTo(3L); // NORMAL priority, newer

        verify(reviewTaskRepository).findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus.PENDING);
    }

    @Test
    @DisplayName("markTaskStarted - should update status to RUNNING and set startedAt")
    void testMarkTaskStarted() {
        // Given
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ReviewTaskDTO result = reviewTaskService.markTaskStarted(100L);

        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(result.getStartedAt()).isNotNull();

        // Verify task updated
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        ReviewTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(savedTask.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markTaskCompleted - should update status to COMPLETED and set completedAt")
    void testMarkTaskCompleted() {
        // Given
        testTask.setStatus(TaskStatus.RUNNING);
        testTask.setStartedAt(Instant.now().minusSeconds(60));

        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ReviewTaskDTO result = reviewTaskService.markTaskCompleted(100L);

        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();

        // Verify task updated
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        ReviewTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(savedTask.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markTaskFailed - first failure should increment retry count and keep status PENDING")
    void testMarkTaskFailed_FirstTime() {
        // Given: Task with 0 retries
        testTask.setRetryCount(0);
        testTask.setMaxRetries(3);
        testTask.setStatus(TaskStatus.RUNNING);

        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ReviewTaskDTO result = reviewTaskService.markTaskFailed(100L, "Connection timeout");

        // Then
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING); // Should be PENDING for retry
        assertThat(result.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(result.getCompletedAt()).isNull(); // Not final failure yet

        // Verify task updated
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        ReviewTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getRetryCount()).isEqualTo(1);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(savedTask.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("markTaskFailed - max retries reached should set status to FAILED and set completedAt")
    void testMarkTaskFailed_MaxRetries() {
        // Given: Task already has 2 retries (3rd attempt will fail)
        testTask.setRetryCount(2);
        testTask.setMaxRetries(3);
        testTask.setStatus(TaskStatus.RUNNING);

        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(reviewTaskRepository.save(any(ReviewTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ReviewTaskDTO result = reviewTaskService.markTaskFailed(100L, "Persistent error");

        // Then
        assertThat(result.getRetryCount()).isEqualTo(3); // Incremented to 3
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED); // Final failure
        assertThat(result.getErrorMessage()).isEqualTo("Persistent error");
        assertThat(result.getCompletedAt()).isNotNull(); // Final failure timestamp

        // Verify task updated
        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(taskCaptor.capture());
        ReviewTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getRetryCount()).isEqualTo(3);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(savedTask.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("canRetry - should return true when retry count < max retries")
    void testCanRetry_True() {
        // Given
        testTask.setRetryCount(1);
        testTask.setMaxRetries(3);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When
        boolean canRetry = reviewTaskService.canRetry(100L);

        // Then
        assertThat(canRetry).isTrue();
    }

    @Test
    @DisplayName("canRetry - should return false when retry count >= max retries")
    void testCanRetry_False() {
        // Given
        testTask.setRetryCount(3);
        testTask.setMaxRetries(3);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When
        boolean canRetry = reviewTaskService.canRetry(100L);

        // Then
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("canRetry - task not found should throw ResourceNotFoundException")
    void testCanRetry_TaskNotFound() {
        // Given
        when(reviewTaskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.canRetry(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ReviewTask not found with id: 999");
    }

    // --- Duplicate detection tests (M2 fix) ---

    @Test
    @DisplayName("createTask - duplicate commit should return existing task without creating new one")
    void testCreateTask_DuplicateCommit_ReturnsExisting() {
        // Given: Task already exists for this project + commit
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(reviewTaskRepository.findByProjectIdAndCommitHash(1L, "abc123"))
                .thenReturn(Optional.of(testTask));

        // When
        ReviewTaskDTO result = reviewTaskService.createTask(testRequest);

        // Then: Should return existing task
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);

        // And: Should NOT save a new task
        verify(reviewTaskRepository, never()).save(any());
    }

    // --- State transition validation tests (H2 fix) ---

    @Test
    @DisplayName("markTaskStarted - non-PENDING task should throw IllegalStateException")
    void testMarkTaskStarted_NonPendingTask_ThrowsException() {
        // Given: Task is already RUNNING
        testTask.setStatus(TaskStatus.RUNNING);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.markTaskStarted(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected status PENDING");
    }

    @Test
    @DisplayName("markTaskCompleted - non-RUNNING task should throw IllegalStateException")
    void testMarkTaskCompleted_NonRunningTask_ThrowsException() {
        // Given: Task is PENDING (not RUNNING)
        testTask.setStatus(TaskStatus.PENDING);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.markTaskCompleted(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected status RUNNING");
    }

    @Test
    @DisplayName("markTaskFailed - non-RUNNING task should throw IllegalStateException")
    void testMarkTaskFailed_NonRunningTask_ThrowsException() {
        // Given: Task is PENDING (not RUNNING)
        testTask.setStatus(TaskStatus.PENDING);
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

        // When/Then
        assertThatThrownBy(() -> reviewTaskService.markTaskFailed(100L, "error"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected status RUNNING");
    }
}
