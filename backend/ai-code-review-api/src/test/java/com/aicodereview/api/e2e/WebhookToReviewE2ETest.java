package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.AssertionHelper;
import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * E2E tests for the Webhook → Task Creation → Review Pipeline flow — Story 9.2.
 *
 * <p>Tests cover two phases:</p>
 * <ol>
 *   <li><strong>Phase 1 (AC#1–4):</strong> HTTP webhook → PENDING task + Redis queue entry.
 *       These are true E2E assertions against the running API server.</li>
 *   <li><strong>Phase 2 (AC#5):</strong> Manual worker simulation via service injection:
 *       {@code markTaskStarted → reviewOrchestrator.review → reviewResultService.saveResult},
 *       verifying the full pipeline produces a COMPLETED task with persisted review results.</li>
 * </ol>
 *
 * <p>No real Git API or AI API calls are made:
 * <ul>
 *   <li>{@link ReviewContextAssembler} is replaced by a {@code @MockBean} returning a
 *       fixed {@link CodeContext}.</li>
 *   <li>{@code MockAIProvider} (registered via {@code AbstractE2ETest.@Import}) returns
 *       {@code MockAIProvider.FIXED_RESULT} (1 HIGH SECURITY + 1 MEDIUM PERFORMANCE issue).</li>
 * </ul>
 *
 * @since 9.2.0
 */
@DisplayName("Webhook to Review Pipeline E2E Tests")
class WebhookToReviewE2ETest extends AbstractE2ETest {

    // ─── Test URLs / names ────────────────────────────────────────────────────

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/webhook-review-repo";
    private static final String GITLAB_REPO_URL =
            "https://gitlab.com/e2e-test-org/webhook-review-repo";
    private static final String CODECOMMIT_REPO_NAME =
            "e2e-webhook-review-repo";

    // ─── MockBean — replaces real git context assembly ────────────────────────

    /**
     * Replaces real {@link ReviewContextAssembler} (which calls GitHub/GitLab APIs)
     * with a Mockito mock that returns a fixed {@link CodeContext}.
     *
     * <p>Note: declaring {@code @MockBean} causes a separate Spring context from
     * {@code E2EFrameworkSetupTest} — startup overhead is expected on first run.</p>
     */
    @MockBean
    private ReviewContextAssembler contextAssembler;

    /**
     * Spies on the real {@link WebhookVerificationChain} so that GitHub and GitLab
     * webhooks exercise real HMAC/token signature verification (MockWebhookServer
     * computes correct signatures for those platforms).
     *
     * <p>Only CodeCommit verification is stubbed to {@code true} because
     * {@code AWSCodeCommitWebhookVerifier.verify()} always returns {@code false}
     * (SNS signature verification not yet implemented — see TODO in that class).
     * Signature correctness for GitHub/GitLab is covered by both the real chain
     * here and by dedicated unit/integration tests.</p>
     */
    @SpyBean
    private WebhookVerificationChain verificationChain;

    // ─── Injected components ──────────────────────────────────────────────────

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
    private ReviewResultRepository reviewResultRepository;

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

        // FK-safe cleanup: review_result → review_task → ai_model_config → project → prompt_template
        testDataFactory.deleteAll();

        // Clean Redis queue and lock keys from previous tests
        redisTemplate.delete(QueueKeys.TASK_QUEUE);
        Set<String> lockKeys = redisTemplate.keys(QueueKeys.TASK_LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }

        // Configure mock context assembler to return a deterministic CodeContext.
        // This avoids real GitHub/GitLab API calls in all tests.
        CodeContext mockContext = CodeContext.builder()
                .rawDiff("diff --git a/Example.java b/Example.java\n"
                        + "--- a/Example.java\n"
                        + "+++ b/Example.java\n"
                        + "@@ -1,3 +1,4 @@\n"
                        + " public class Example {\n"
                        + "+    // E2E test change\n"
                        + " }\n")
                .files(Collections.emptyList())
                .fileContents(Collections.emptyMap())
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(1)
                        .totalLinesAdded(1)
                        .totalLinesDeleted(0)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .author("e2e-test-user")
                        .branch("main")
                        .commitHash("e2e-fixed-sha")
                        .taskType(TaskType.PUSH)
                        .build())
                .build();
        when(contextAssembler.assembleContext(any())).thenReturn(mockContext);

        // Stub ONLY CodeCommit verification — AWSCodeCommitWebhookVerifier always returns false
        // (SNS signature verification not implemented). GitHub and GitLab use real signature
        // verification: MockWebhookServer computes correct HMAC/token for those platforms.
        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    // ─── AC#1: GitHub Push → PENDING task + Redis queue ──────────────────────

    @Test
    @DisplayName("AC#1 — GitHub push webhook creates NORMAL-priority PENDING task and enqueues it")
    void testGitHubPushWebhook_CreatesNormalPriorityTask() {
        // Given: a configured GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: GitHub push webhook is sent
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "push001abc");

        // Then: API accepts the webhook
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: a PENDING PUSH task is created in the DB
        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "push001abc", TaskStatus.PENDING);
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);

        // And: the task ID is present in the Redis priority queue
        Double score = redisTemplate.opsForZSet()
                .score(QueueKeys.TASK_QUEUE, String.valueOf(task.getId()));
        assertThat(score)
                .as("Task %d should be enqueued in Redis '%s'", task.getId(), QueueKeys.TASK_QUEUE)
                .isNotNull();
    }

    // ─── AC#2: GitHub PR → HIGH-priority task ────────────────────────────────

    @Test
    @DisplayName("AC#2 — GitHub PR webhook creates HIGH-priority PULL_REQUEST task")
    void testGitHubPRWebhook_CreatesHighPriorityTask() {
        // Given: a configured GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: GitHub pull_request (opened) webhook is sent
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPullRequestEvent(GITHUB_REPO_URL, "feature/ac2", "pr001sha", 42);

        // Then: API accepts the webhook
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: a PENDING PULL_REQUEST task is created
        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "pr001sha", TaskStatus.PENDING);
        assertThat(task.getTaskType()).isEqualTo(TaskType.PULL_REQUEST);

        // And: task is in Redis queue
        Double score = redisTemplate.opsForZSet()
                .score(QueueKeys.TASK_QUEUE, String.valueOf(task.getId()));
        assertThat(score).isNotNull();
    }

    // ─── AC#3: GitLab MR → task creation ─────────────────────────────────────

    @Test
    @DisplayName("AC#3 — GitLab MR webhook creates PENDING task with correct branch and commit")
    void testGitLabMRWebhook_CreatesTask() {
        // Given: a configured GitLab project
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);

        // When: GitLab merge_request webhook is sent
        ResponseEntity<String> response = mockWebhookServer
                .sendGitLabMergeRequestEvent(GITLAB_REPO_URL, "feature/ac3", "mr001sha", 7);

        // Then: API accepts the webhook
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: a PENDING task is created with correct metadata
        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITLAB_REPO_URL, "mr001sha", TaskStatus.PENDING);
        assertThat(task.getBranch()).isEqualTo("feature/ac3");
        assertThat(task.getCommitHash()).isEqualTo("mr001sha");
    }

    // ─── AC#4: CodeCommit Push → task creation ────────────────────────────────

    @Test
    @DisplayName("AC#4 — AWS CodeCommit push webhook creates PENDING task")
    void testCodeCommitPushWebhook_CreatesTask() {
        // Given: a configured CodeCommit project (repoUrl = repoName for CodeCommit)
        testDataFactory.createCodeCommitProject(CODECOMMIT_REPO_NAME);

        // When: CodeCommit SNS push notification is sent
        ResponseEntity<String> response = mockWebhookServer
                .sendCodeCommitPushEvent(CODECOMMIT_REPO_NAME, "main", "cc001sha");

        // Then: API accepts the webhook
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: a PENDING task is created for the CodeCommit repository
        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                CODECOMMIT_REPO_NAME, "cc001sha", TaskStatus.PENDING);
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);
    }

    // ─── AC#5: Full pipeline — Webhook → COMPLETED + review_result ───────────

    @Test
    @DisplayName("AC#5 — Full pipeline: GitHub push → PENDING → manual worker → COMPLETED with review_result")
    void testGitHubPush_FullReviewPipeline() {
        // Given: a project + prompt template (required by ReviewOrchestrator)
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        // When: GitHub push webhook triggers task creation
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "pipe001sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: task is created as PENDING
        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "pipe001sha", TaskStatus.PENDING);

        // ── Simulate Worker processing ──────────────────────────────────────
        // Step 1: mark task as started (PENDING → RUNNING)
        reviewTaskService.markTaskStarted(task.getId());

        // Step 2: run review (MockContextAssembler + MockAI → FIXED_RESULT)
        // ReviewOrchestrator internally: loads PromptTemplate from DB → renders via Handlebars
        // with the CodeContext → passes rendered prompt to MockAIProvider. MockAIProvider returns
        // FIXED_RESULT regardless of prompt content, so template variable substitution is tested
        // implicitly (Handlebars rendering exceptions would cause review failure).
        // Re-fetch task to get managed entity with all fields
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId())
                .orElseThrow(() -> new AssertionError("Task not found: " + task.getId()));
        ReviewResult result = reviewOrchestrator.review(managedTask);
        assertThat(result.isSuccess()).isTrue();

        // Step 3: persist result (RUNNING → COMPLETED)
        ReviewResultDTO dto = reviewResultService.saveResult(task.getId(), result);

        // Then: task status is COMPLETED
        assertionHelper.awaitTaskStatus(
                "pipe001sha", GITHUB_REPO_URL, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        // And: review_result record exists in DB
        assertionHelper.assertReviewResultExistsForTask(task.getId());

        // And: result matches MockAIProvider.FIXED_RESULT (2 issues)
        assertThat(dto.getSuccess()).isTrue();
        assertThat(dto.getIssues()).hasSize(2);

        // Verify issue 1: HIGH SECURITY
        assertThat(dto.getIssues()).anySatisfy(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.HIGH);
            assertThat(issue.getCategory()).isEqualTo(IssueCategory.SECURITY);
            assertThat(issue.getMessage()).contains("SQL injection");
        });

        // Verify issue 2: MEDIUM PERFORMANCE
        assertThat(dto.getIssues()).anySatisfy(issue -> {
            assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
            assertThat(issue.getCategory()).isEqualTo(IssueCategory.PERFORMANCE);
            assertThat(issue.getMessage()).contains("N+1");
        });
    }

    // ─── AC#6: Priority score ordering ────────────────────────────────────────

    @Test
    @DisplayName("AC#6 — HIGH-priority task Redis score < NORMAL-priority task score (HIGH dequeues first)")
    void testPriorityOrdering_HighBeforeNormal() {
        // Given: a GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: push (NORMAL priority) is created first
        mockWebhookServer.sendGitHubPushEvent(GITHUB_REPO_URL, "main", "prio-push-001");
        ReviewTask pushTask = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "prio-push-001", TaskStatus.PENDING);

        // And: PR (HIGH priority) is created second
        mockWebhookServer.sendGitHubPullRequestEvent(
                GITHUB_REPO_URL, "feature/prio", "prio-pr-001", 99);
        ReviewTask prTask = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "prio-pr-001", TaskStatus.PENDING);

        // Then: HIGH score < NORMAL score (lower score → ZPOPMIN dequeues it first)
        Double highScore = redisTemplate.opsForZSet()
                .score(QueueKeys.TASK_QUEUE, String.valueOf(prTask.getId()));
        Double normalScore = redisTemplate.opsForZSet()
                .score(QueueKeys.TASK_QUEUE, String.valueOf(pushTask.getId()));

        assertThat(highScore)
                .as("HIGH priority task score should be less than NORMAL priority task score")
                .isNotNull()
                .isLessThan(normalScore);
    }

    // ─── AC#7: Idempotency — duplicate webhook ────────────────────────────────

    @Test
    @DisplayName("AC#7 — Duplicate webhook for same commit creates only one ReviewTask (idempotent)")
    void testDuplicateWebhook_IdempotentTaskCreation() {
        // Given: a configured GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: two identical push webhooks are sent for the same commit
        ResponseEntity<String> r1 = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "dup001sha");
        ResponseEntity<String> r2 = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "dup001sha");

        // Then: both requests are accepted (2xx)
        assertThat(r1.getStatusCode().is2xxSuccessful())
                .as("First webhook should be accepted").isTrue();
        assertThat(r2.getStatusCode().is2xxSuccessful())
                .as("Second webhook should also return 2xx (idempotent)").isTrue();

        // And: only ONE task exists for this commit
        List<ReviewTask> allTasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        long countForCommit = allTasks.stream()
                .filter(t -> "dup001sha".equals(t.getCommitHash()))
                .count();
        assertThat(countForCommit)
                .as("Duplicate webhook should not create a second ReviewTask")
                .isEqualTo(1);
    }
}
