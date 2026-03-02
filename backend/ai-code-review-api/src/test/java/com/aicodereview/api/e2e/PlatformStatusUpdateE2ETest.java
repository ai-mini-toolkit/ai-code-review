package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.AssertionHelper;
import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.integration.git.GitHubCheckRunService;
import com.aicodereview.integration.git.GitLabCommitStatusService;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.GitCommentNotificationService;
import com.aicodereview.service.ReviewContextAssembler;
import com.aicodereview.service.ReviewOrchestrator;
import com.aicodereview.service.ReviewResultService;
import com.aicodereview.service.ReviewTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E2E tests for platform-specific status updates and notifications — Story 9.3.
 *
 * <p>Tests verify that after a full review pipeline completes,
 * {@code ReviewResultService.saveResult()} triggers the appropriate
 * platform status update and notification services.</p>
 *
 * <p>This test class has additional {@code @MockBean} annotations beyond
 * {@link WebhookToReviewE2ETest}, which creates a separate Spring context.
 * This is necessary because the real services would call external APIs
 * (GitHub Check Runs, GitLab Commit Status, etc.).</p>
 *
 * @since 9.3.0
 */
@DisplayName("Platform Status Update & Notification E2E Tests")
class PlatformStatusUpdateE2ETest extends AbstractE2ETest {

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/status-update-repo";
    private static final String GITLAB_REPO_URL =
            "https://gitlab.com/e2e-test-org/status-update-repo";

    // ─── MockBean/SpyBean — replaces real services ───────────────────────────

    @MockBean
    private ReviewContextAssembler contextAssembler;

    @SpyBean
    private WebhookVerificationChain verificationChain;

    /** Mock to prevent real GitHub Check Runs API calls. */
    @MockBean
    private GitHubCheckRunService gitHubCheckRunService;

    /** Mock to prevent real GitLab Commit Status API calls. */
    @MockBean
    private GitLabCommitStatusService gitLabCommitStatusService;

    /** Mock to prevent real PR/MR comment API calls and notification config lookups. */
    @MockBean
    private GitCommentNotificationService gitCommentNotificationService;

    // ─── Injected components for pipeline simulation ─────────────────────────

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private AssertionHelper assertionHelper;

    @Autowired
    private ReviewOrchestrator reviewOrchestrator;

    @Autowired
    private ReviewTaskService reviewTaskService;

    @Autowired
    private ReviewResultService reviewResultService;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private MockWebhookServer mockWebhookServer;

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        mockWebhookServer = new MockWebhookServer(
                restTemplate,
                TestDataFactory.DEFAULT_GITHUB_SECRET,
                TestDataFactory.DEFAULT_GITLAB_TOKEN
        );

        testDataFactory.deleteAll();
        redisTemplate.delete(QueueKeys.TASK_QUEUE);
        Set<String> lockKeys = redisTemplate.keys(QueueKeys.TASK_LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }

        when(contextAssembler.assembleContext(any())).thenReturn(
                CodeContext.builder()
                        .rawDiff("diff --git a/Status.java b/Status.java\n+// status update test")
                        .files(Collections.emptyList())
                        .fileContents(Collections.emptyMap())
                        .statistics(DiffStatistics.builder()
                                .totalFilesChanged(1).totalLinesAdded(1).totalLinesDeleted(0).build())
                        .taskMeta(TaskMetadata.builder()
                                .author("e2e-test-user").branch("main")
                                .commitHash("status-test-sha").taskType(TaskType.PUSH).build())
                        .build());

        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#4: GitHub Check Runs Status Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#4 — GitHub PR full pipeline triggers Check Run status update")
    void testGitHubPR_FullPipeline_TriggersCheckRun() {
        // Given: a GitHub project + prompt template
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        // When: GitHub PR webhook triggers task creation
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPullRequestEvent(GITHUB_REPO_URL, "feature/check-run", "cr-sha001", 77);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "cr-sha001", TaskStatus.PENDING);

        // Simulate Worker: PENDING → RUNNING → review → saveResult → COMPLETED
        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        assertThat(result.isSuccess()).isTrue();
        reviewResultService.saveResult(task.getId(), result);

        // Then: task reaches COMPLETED
        assertionHelper.awaitTaskStatus(
                "cr-sha001", GITHUB_REPO_URL, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        // And: GitHub Check Run service was called with correct repoUrl and commitHash
        verify(gitHubCheckRunService).createCompletedCheckRun(
                eq(GITHUB_REPO_URL), eq("cr-sha001"), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#5: GitLab Commit Status Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#5 — GitLab MR full pipeline triggers Commit Status update")
    void testGitLabMR_FullPipeline_TriggersCommitStatus() {
        // Given: a GitLab project + prompt template
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        // When: GitLab MR webhook triggers task creation
        ResponseEntity<String> response = mockWebhookServer
                .sendGitLabMergeRequestEvent(GITLAB_REPO_URL, "feature/commit-status", "cs-sha001", 33);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITLAB_REPO_URL, "cs-sha001", TaskStatus.PENDING);

        // Simulate Worker pipeline
        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        assertThat(result.isSuccess()).isTrue();
        reviewResultService.saveResult(task.getId(), result);

        // Then: task reaches COMPLETED
        assertionHelper.awaitTaskStatus(
                "cs-sha001", GITLAB_REPO_URL, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        // And: GitLab Commit Status service was called with correct repoUrl and commitHash
        verify(gitLabCommitStatusService).updateCommitStatus(
                eq(GITLAB_REPO_URL), eq("cs-sha001"), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#6: PR/MR Comment Notification Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#6 — PR review completion triggers comment notification service")
    void testPRReview_TriggersCommentNotification() {
        // Given: a GitHub project with PR + prompt template
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        // When: GitHub PR webhook triggers task creation
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPullRequestEvent(GITHUB_REPO_URL, "feature/comment", "cm-sha001", 88);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "cm-sha001", TaskStatus.PENDING);

        // Simulate Worker pipeline
        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        assertThat(result.isSuccess()).isTrue();
        reviewResultService.saveResult(task.getId(), result);

        // Then: task reaches COMPLETED
        assertionHelper.awaitTaskStatus(
                "cm-sha001", GITHUB_REPO_URL, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        // And: Comment notification service was called with the task ID.
        // NOTE: GitCommentNotificationService is mocked entirely because the real
        // implementation requires NotificationConfigEntity.commentEnabled=true (checked
        // at GitCommentNotificationServiceImpl:90-95). Full comment body content
        // verification (severity distribution, issue count) requires either:
        //   a) Creating NotificationConfigEntity test data + mocking lower-level
        //      GitHubPRCommentService with ArgumentCaptor, OR
        //   b) Dedicated unit tests for GitCommentNotificationServiceImpl.buildCommentBody()
        // This E2E test validates the integration trigger path; body format is a unit concern.
        verify(gitCommentNotificationService).postReviewComment(eq(task.getId()));
    }
}
