package com.aicodereview.api;

import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.enums.FailureType;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.QueueService;
import com.aicodereview.service.RetryService;
import com.aicodereview.service.ReviewTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RetryService with real Redis and PostgreSQL.
 * <p>
 * Requires Docker containers to be running (docker-compose up -d).
 * Tests the full retry lifecycle: fail → requeue with delay → dequeue after delay → verify state.
 * </p>
 *
 * @since 2.7.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Retry Integration Tests")
class RetryIntegrationTest {

    @Autowired
    private RetryService retryService;

    @Autowired
    private ReviewTaskService reviewTaskService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Project testProject;

    @BeforeEach
    void setUp() {
        // Clean Redis queue and locks
        redisTemplate.delete(QueueKeys.TASK_QUEUE);
        Set<String> lockKeys = redisTemplate.keys(QueueKeys.TASK_LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }

        // Clean DB (tasks first due to FK constraint)
        reviewTaskRepository.deleteAll();

        // Reuse existing project or create one (handles unique constraint)
        testProject = projectRepository.findByRepoUrl("https://github.com/test/retry-repo")
                .orElseGet(() -> projectRepository.save(Project.builder()
                        .name("retry-test-project")
                        .repoUrl("https://github.com/test/retry-repo")
                        .gitPlatform("GITHUB")
                        .webhookSecret("test-retry-secret")
                        .enabled(true)
                        .build()));
    }

    @Test
    @DisplayName("Full retry lifecycle: retryable failure → requeue with delay → dequeue after delay")
    void testRetryLifecycle_RetryableFailure() throws InterruptedException {
        // Step 1: Create and persist a task
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(testProject)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/test/retry-repo")
                .branch("main")
                .commitHash("retry-test-001")
                .author("tester")
                .status(TaskStatus.RUNNING)
                .priority(TaskPriority.NORMAL)
                .retryCount(0)
                .maxRetries(3)
                .build());

        Long taskId = task.getId();

        // Step 2: Handle retryable failure (RATE_LIMIT)
        retryService.handleTaskFailure(taskId, "429 Too Many Requests", FailureType.RATE_LIMIT);

        // Step 3: Verify DB state
        ReviewTask updated = reviewTaskRepository.findById(taskId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getErrorMessage()).isEqualTo("429 Too Many Requests");

        // Step 4: Verify task is in Redis queue (with delay, so poll until it appears)
        Optional<Long> dequeued = Optional.empty();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            dequeued = queueService.dequeue();
            if (dequeued.isPresent()) break;
            Thread.sleep(200);
        }
        assertThat(dequeued).isPresent().contains(taskId);
    }

    @Test
    @DisplayName("Non-retryable failure: VALIDATION_ERROR → immediate FAILED status")
    void testNonRetryableFailure_ImmediateFailed() {
        // Given: A RUNNING task
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(testProject)
                .taskType(TaskType.PULL_REQUEST)
                .repoUrl("https://github.com/test/retry-repo")
                .branch("feature")
                .commitHash("nonretry-test-001")
                .prNumber(42)
                .author("tester")
                .status(TaskStatus.RUNNING)
                .priority(TaskPriority.HIGH)
                .retryCount(0)
                .maxRetries(3)
                .build());

        Long taskId = task.getId();

        // When: Handle non-retryable failure
        retryService.handleTaskFailure(taskId, "Invalid payload: missing required fields", FailureType.VALIDATION_ERROR);

        // Then: Task should be immediately FAILED
        ReviewTask updated = reviewTaskRepository.findById(taskId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(0); // NOT incremented
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getErrorMessage()).isEqualTo("Invalid payload: missing required fields");

        // And: No task in queue
        assertThat(queueService.getQueueSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Max retries exhaustion: third failure → FAILED status, no requeue")
    void testMaxRetriesExhaustion() {
        // Given: A RUNNING task with 2 retries already
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(testProject)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/test/retry-repo")
                .branch("main")
                .commitHash("maxretry-test-001")
                .author("tester")
                .status(TaskStatus.RUNNING)
                .priority(TaskPriority.NORMAL)
                .retryCount(2)
                .maxRetries(3)
                .build());

        Long taskId = task.getId();

        // When: Handle retryable failure (but max retries reached)
        retryService.handleTaskFailure(taskId, "Third failure", FailureType.RATE_LIMIT);

        // Then: Task should be FAILED (max retries)
        ReviewTask updated = reviewTaskRepository.findById(taskId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(3);
        assertThat(updated.getCompletedAt()).isNotNull();

        // And: No task in queue
        assertThat(queueService.getQueueSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("AUTHENTICATION_ERROR: immediate FAILED, no retry regardless of retryCount")
    void testAuthenticationError_ImmediateFailed() {
        // Given
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(testProject)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/test/retry-repo")
                .branch("main")
                .commitHash("auth-test-001")
                .author("tester")
                .status(TaskStatus.RUNNING)
                .priority(TaskPriority.NORMAL)
                .retryCount(0)
                .maxRetries(3)
                .build());

        // When
        retryService.handleTaskFailure(task.getId(), "401 Unauthorized", FailureType.AUTHENTICATION_ERROR);

        // Then
        ReviewTask updated = reviewTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(0);
        assertThat(queueService.getQueueSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("UNKNOWN error: treated as retryable (conservative approach)")
    void testUnknownError_TreatedAsRetryable() {
        // Given
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(testProject)
                .taskType(TaskType.PUSH)
                .repoUrl("https://github.com/test/retry-repo")
                .branch("main")
                .commitHash("unknown-test-001")
                .author("tester")
                .status(TaskStatus.RUNNING)
                .priority(TaskPriority.NORMAL)
                .retryCount(0)
                .maxRetries(3)
                .build());

        // When
        retryService.handleTaskFailure(task.getId(), "Unexpected NPE", FailureType.UNKNOWN);

        // Then: Task should be PENDING (retryable)
        ReviewTask updated = reviewTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }
}
