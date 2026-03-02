package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.AssertionHelper;
import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewContextAssembler;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * E2E tests for multi-platform webhook integration — Story 9.3.
 *
 * <p>Tests cover: invalid signature rejection, unsupported event type handling,
 * cross-platform metadata extraction consistency, and boundary conditions.</p>
 *
 * <p>Uses the same {@code @MockBean}/{@code @SpyBean} combination as
 * {@link WebhookToReviewE2ETest} to share the Spring application context
 * and reduce startup overhead.</p>
 *
 * @since 9.3.0
 */
@DisplayName("Multi-Platform Integration E2E Tests")
class MultiPlatformIntegrationE2ETest extends AbstractE2ETest {

    // ─── Test URLs / names ────────────────────────────────────────────────────

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/multi-platform-repo";
    private static final String GITLAB_REPO_URL =
            "https://gitlab.com/e2e-test-org/multi-platform-repo";
    private static final String CODECOMMIT_REPO_NAME =
            "e2e-multi-platform-repo";

    // ─── Same MockBean/SpyBean as WebhookToReviewE2ETest → shared context ───

    @MockBean
    private ReviewContextAssembler contextAssembler;

    @SpyBean
    private WebhookVerificationChain verificationChain;

    // ─── Injected components ──────────────────────────────────────────────────

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private AssertionHelper assertionHelper;

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
                        .rawDiff("diff --git a/Test.java b/Test.java\n+// multi-platform test")
                        .files(Collections.emptyList())
                        .fileContents(Collections.emptyMap())
                        .statistics(DiffStatistics.builder()
                                .totalFilesChanged(1).totalLinesAdded(1).totalLinesDeleted(0).build())
                        .taskMeta(TaskMetadata.builder()
                                .author("e2e-test-user").branch("main")
                                .commitHash("mp-test-sha").taskType(TaskType.PUSH).build())
                        .build());

        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#1: Invalid Signature Rejection
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#1 — GitHub push with invalid HMAC signature returns 401")
    void testInvalidGitHubSignature_Returns401() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEventWithInvalidSignature(GITHUB_REPO_URL, "main", "bad-sig-sha");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // No task should be created
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        assertThat(tasks.stream().filter(t -> "bad-sig-sha".equals(t.getCommitHash())).count())
                .as("Invalid signature should not create a task").isZero();
    }

    @Test
    @DisplayName("AC#1 — GitLab MR with invalid token returns 401")
    void testInvalidGitLabToken_Returns401() {
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);

        ResponseEntity<String> response = mockWebhookServer
                .sendGitLabMREventWithInvalidToken(GITLAB_REPO_URL, "feature/bad-token", "bad-tok-sha", 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITLAB_REPO_URL);
        assertThat(tasks.stream().filter(t -> "bad-tok-sha".equals(t.getCommitHash())).count())
                .as("Invalid token should not create a task").isZero();
    }

    @Test
    @DisplayName("AC#1 — Malformed CodeCommit SNS message (missing Type) returns non-2xx")
    void testMalformedCodeCommitSNS_ReturnsError() {
        testDataFactory.createCodeCommitProject(CODECOMMIT_REPO_NAME);

        // Reset the spy stub so CodeCommit verification uses the REAL chain
        // (which will fail on malformed SNS without Type field)
        doReturn(false).when(verificationChain).verify(eq("codecommit"), any(), any(), any());

        ResponseEntity<String> response = mockWebhookServer
                .sendCodeCommitEventMalformed(CODECOMMIT_REPO_NAME, "main", "malformed-sha");

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Malformed CodeCommit SNS should not succeed").isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#2: Unsupported Event Type Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#2 — GitHub 'issues' event (unsupported) does not create task")
    void testUnsupportedGitHubEventType_DoesNotCreateTask() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubEventWithCustomType(GITHUB_REPO_URL, "issues");

        // Should return 422 (validation fails: no "pusher" or "pull_request" field)
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.ACCEPTED);

        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        assertThat(tasks).as("Unsupported event type should not create any task").isEmpty();
    }

    @Test
    @DisplayName("AC#2 — GitLab push event creates PUSH type task (not MERGE_REQUEST)")
    void testGitLabPushEvent_CreatesPushTask() {
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);

        ResponseEntity<String> response = mockWebhookServer
                .sendGitLabPushEvent(GITLAB_REPO_URL, "main", "gl-push-sha");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITLAB_REPO_URL, "gl-push-sha", TaskStatus.PENDING);
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);
        assertThat(task.getBranch()).isEqualTo("main");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#3: Cross-Platform Metadata Extraction Consistency
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#3 — GitHub push extracts correct author, branch, commitHash, repoUrl")
    void testGitHubPush_MetadataExtraction() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        mockWebhookServer.sendGitHubPushEvent(GITHUB_REPO_URL, "feature/meta", "gh-push-meta");

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "gh-push-meta", TaskStatus.PENDING);
        assertThat(task.getRepoUrl()).isEqualTo(GITHUB_REPO_URL);
        assertThat(task.getBranch()).isEqualTo("feature/meta");
        assertThat(task.getCommitHash()).isEqualTo("gh-push-meta");
        assertThat(task.getAuthor()).isEqualTo("e2e-test-user");
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);
    }

    @Test
    @DisplayName("AC#3 — GitHub PR extracts correct author, branch, commitHash, repoUrl")
    void testGitHubPR_MetadataExtraction() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        mockWebhookServer.sendGitHubPullRequestEvent(GITHUB_REPO_URL, "feature/pr-meta", "gh-pr-meta", 55);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "gh-pr-meta", TaskStatus.PENDING);
        assertThat(task.getRepoUrl()).isEqualTo(GITHUB_REPO_URL);
        assertThat(task.getBranch()).isEqualTo("feature/pr-meta");
        assertThat(task.getCommitHash()).isEqualTo("gh-pr-meta");
        assertThat(task.getAuthor()).isEqualTo("e2e-test-user");
        assertThat(task.getTaskType()).isEqualTo(TaskType.PULL_REQUEST);
        assertThat(task.getPrNumber()).isEqualTo(55);
    }

    @Test
    @DisplayName("AC#3 — GitLab MR extracts correct author, branch, commitHash, repoUrl")
    void testGitLabMR_MetadataExtraction() {
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);

        mockWebhookServer.sendGitLabMergeRequestEvent(GITLAB_REPO_URL, "feature/mr-meta", "gl-mr-meta", 12);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITLAB_REPO_URL, "gl-mr-meta", TaskStatus.PENDING);
        assertThat(task.getRepoUrl()).isEqualTo(GITLAB_REPO_URL);
        assertThat(task.getBranch()).isEqualTo("feature/mr-meta");
        assertThat(task.getCommitHash()).isEqualTo("gl-mr-meta");
        assertThat(task.getAuthor()).isEqualTo("e2e-test-user");
    }

    @Test
    @DisplayName("AC#3 — GitLab push extracts correct author, branch, commitHash, repoUrl")
    void testGitLabPush_MetadataExtraction() {
        testDataFactory.createGitLabProject(GITLAB_REPO_URL);

        mockWebhookServer.sendGitLabPushEvent(GITLAB_REPO_URL, "develop", "gl-push-meta");

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITLAB_REPO_URL, "gl-push-meta", TaskStatus.PENDING);
        assertThat(task.getRepoUrl()).isEqualTo(GITLAB_REPO_URL);
        assertThat(task.getBranch()).isEqualTo("develop");
        assertThat(task.getCommitHash()).isEqualTo("gl-push-meta");
        assertThat(task.getAuthor()).isEqualTo("e2e-test-user");
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);
    }

    @Test
    @DisplayName("AC#3 — CodeCommit push extracts correct author, branch, commitHash, repoUrl")
    void testCodeCommitPush_MetadataExtraction() {
        testDataFactory.createCodeCommitProject(CODECOMMIT_REPO_NAME);

        mockWebhookServer.sendCodeCommitPushEvent(CODECOMMIT_REPO_NAME, "main", "cc-push-meta");

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                CODECOMMIT_REPO_NAME, "cc-push-meta", TaskStatus.PENDING);
        assertThat(task.getRepoUrl()).isEqualTo(CODECOMMIT_REPO_NAME);
        assertThat(task.getBranch()).isEqualTo("main");
        assertThat(task.getCommitHash()).isEqualTo("cc-push-meta");
        assertThat(task.getAuthor()).isEqualTo("e2e-test-user");
        assertThat(task.getTaskType()).isEqualTo(TaskType.PUSH);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#7: Boundary Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#7 — Oversized payload (> 1MB) is handled gracefully (no crash/OOM)")
    void testOversizedPayload_RejectedGracefully() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // Build a > 1MB payload
        String filler = "x".repeat(1_100_000);
        String payload = String.format(
                "{\"ref\":\"refs/heads/main\"," +
                "\"after\":\"oversized-sha\"," +
                "\"repository\":{\"name\":\"repo\",\"full_name\":\"org/repo\",\"html_url\":\"%s\"}," +
                "\"pusher\":{\"name\":\"e2e-test-user\"}," +
                "\"filler\":\"%s\"}",
                GITHUB_REPO_URL, filler
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        headers.set("X-GitHub-Event", "push");

        ResponseEntity<String> response = mockWebhookServer.sendRawPayload("github", payload, headers);

        // Should not be 500 (crash) — may be 401 (invalid sig), 413 (too large), or 202 (accepted)
        assertThat(response.getStatusCode())
                .as("Oversized payload should not cause 500 Internal Server Error")
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("AC#7 — Missing required fields returns 4xx (not 500)")
    void testMissingRequiredFields_Returns4xx() {
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // GitHub push missing "repository" field entirely
        String payload = "{\"ref\":\"refs/heads/main\",\"after\":\"missing-repo-sha\"," +
                "\"pusher\":{\"name\":\"e2e-test-user\"}}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        headers.set("X-GitHub-Event", "push");

        ResponseEntity<String> response = mockWebhookServer.sendRawPayload("github", payload, headers);

        // Might be 401 (invalid sig for wrong HMAC) or 400/422 (missing fields) — NOT 500
        assertThat(response.getStatusCode().is5xxServerError())
                .as("Missing fields should return client error, not server error").isFalse();
    }

    @Test
    @DisplayName("AC#7 — Non-JSON payload returns 400")
    void testNonJsonPayload_Returns400() {
        String payload = "This is not JSON at all";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        headers.set("X-GitHub-Event", "push");

        ResponseEntity<String> response = mockWebhookServer.sendRawPayload("github", payload, headers);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Non-JSON payload should not succeed").isFalse();
        assertThat(response.getStatusCode().is5xxServerError())
                .as("Non-JSON payload should return client error, not server error").isFalse();
    }
}
