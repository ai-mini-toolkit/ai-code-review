package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.AssertionHelper;
import com.aicodereview.api.e2e.support.ConfigurableMockAIProvider;
import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewContextAssembler;
import com.aicodereview.service.ReviewOrchestrator;
import com.aicodereview.service.ReviewResultService;
import com.aicodereview.service.ReviewTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * E2E tests for AI review result quality — statistics, thresholds, and
 * configurable AI responses — Story 9.4.
 *
 * <p>Registers a {@link ConfigurableMockAIProvider} as the default AI provider,
 * allowing tests to set different review results per scenario.</p>
 *
 * @since 9.4.0
 */
@DisplayName("AI Review Quality E2E Tests")
@Import(AIReviewQualityE2ETest.QualityTestConfig.class)
class AIReviewQualityE2ETest extends AbstractE2ETest {

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/ai-quality-repo";

    @TestConfiguration
    static class QualityTestConfig {
        @Bean
        public ConfigurableMockAIProvider testConfigurableProvider() {
            return new ConfigurableMockAIProvider("test-configurable");
        }
    }

    @DynamicPropertySource
    static void configureTestProviders(DynamicPropertyRegistry registry) {
        registry.add("ai.provider.default", () -> "test-configurable");
        registry.add("ai.provider.fallback", () -> "test-configurable");
    }

    @MockBean
    private ReviewContextAssembler contextAssembler;

    @SpyBean
    private WebhookVerificationChain verificationChain;

    @Autowired
    private ConfigurableMockAIProvider testConfigurableProvider;

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
    private ProjectRepository projectRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private MockWebhookServer mockWebhookServer;

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

        testConfigurableProvider.reset();

        when(contextAssembler.assembleContext(any())).thenReturn(
                CodeContext.builder()
                        .rawDiff("diff --git a/Quality.java b/Quality.java\n+// quality test")
                        .files(Collections.emptyList())
                        .fileContents(Collections.emptyMap())
                        .statistics(DiffStatistics.builder()
                                .totalFilesChanged(1).totalLinesAdded(1).totalLinesDeleted(0).build())
                        .taskMeta(TaskMetadata.builder()
                                .author("e2e-test-user").branch("main")
                                .commitHash("quality-sha").taskType(TaskType.PUSH).build())
                        .build());

        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#1: Configurable MockAIProvider — Multi-scenario ReviewResult
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#1 — Mixed severity result produces correct issue fields")
    void testMixedSeverityResult_CorrectIssueFields() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.mixedSeverityResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "mixed-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "mixed-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).hasSize(5);
        assertThat(result.getIssues().get(0).getSeverity().name()).isEqualTo("CRITICAL");
        assertThat(result.getIssues().get(0).getCategory().name()).isEqualTo("CORRECTNESS");
    }

    @Test
    @DisplayName("AC#1 — Clean result produces zero issues")
    void testCleanResult_ZeroIssues() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.cleanResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "clean-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "clean-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#2: ReviewResult Statistics Calculation Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#2 — 5-issue result produces correct statistics breakdown")
    void testMixedResult_CorrectStatistics() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.mixedSeverityResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "stats-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "stats-sha", TaskStatus.PENDING);

        // Full pipeline: webhook → markStarted → review → saveResult
        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        ReviewResultDTO dto = reviewResultService.saveResult(task.getId(), result);

        assertionHelper.awaitTaskStatus(
                "stats-sha", GITHUB_REPO_URL, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        // Verify statistics
        assertThat(dto.getStatistics()).isNotNull();
        assertThat(dto.getStatistics().getTotal()).isEqualTo(5);
        assertThat(dto.getStatistics().getBySeverity()).containsEntry("CRITICAL", 1);
        assertThat(dto.getStatistics().getBySeverity()).containsEntry("HIGH", 2);
        assertThat(dto.getStatistics().getBySeverity()).containsEntry("MEDIUM", 1);
        assertThat(dto.getStatistics().getBySeverity()).containsEntry("LOW", 1);
        assertThat(dto.getStatistics().getByCategory()).containsEntry("SECURITY", 2);
        assertThat(dto.getStatistics().getByCategory()).containsEntry("CORRECTNESS", 1);
        assertThat(dto.getStatistics().getByCategory()).containsEntry("PERFORMANCE", 1);
        assertThat(dto.getStatistics().getByCategory()).containsEntry("STYLE", 1);
    }

    @Test
    @DisplayName("AC#2 — 0-issue result produces empty statistics")
    void testCleanResult_EmptyStatistics() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.cleanResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "empty-stats-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "empty-stats-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        ReviewResultDTO dto = reviewResultService.saveResult(task.getId(), result);

        assertThat(dto.getStatistics()).isNotNull();
        assertThat(dto.getStatistics().getTotal()).isZero();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#3: Threshold Validation Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#3 — Threshold CRITICAL<=0 fails when result has CRITICAL issues")
    void testThresholdViolation_CriticalExceedsLimit() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.mixedSeverityResult());

        // Create project WITH threshold config: CRITICAL <= 0, action=BLOCK_MERGE
        String thresholdsJson = "{\"enabled\":true,\"action\":\"BLOCK_MERGE\",\"rules\":" +
                "[{\"severity\":\"CRITICAL\",\"maxCount\":0}]}";
        Project project = testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        project.setThresholds(thresholdsJson);
        projectRepository.save(project);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "threshold-fail-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "threshold-fail-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        ReviewResultDTO dto = reviewResultService.saveResult(task.getId(), result);

        // Threshold should FAIL: mixedSeverityResult has 1 CRITICAL, but limit is 0
        assertThat(dto.getThresholdResult()).isNotNull();
        assertThat(dto.getThresholdResult().isPassed()).isFalse();
        assertThat(dto.getThresholdResult().getAction()).isEqualTo("BLOCK_MERGE");
        assertThat(dto.getThresholdResult().getViolations()).isNotEmpty();
    }

    @Test
    @DisplayName("AC#3 — Threshold passes when result has no CRITICAL issues")
    void testThresholdPass_NoCriticalIssues() {
        testConfigurableProvider.setNextResult(ConfigurableMockAIProvider.cleanResult());

        String thresholdsJson = "{\"enabled\":true,\"action\":\"BLOCK_MERGE\",\"rules\":" +
                "[{\"severity\":\"CRITICAL\",\"maxCount\":0}]}";
        Project project = testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        project.setThresholds(thresholdsJson);
        projectRepository.save(project);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "threshold-pass-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "threshold-pass-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        ReviewResultDTO dto = reviewResultService.saveResult(task.getId(), result);

        assertThat(dto.getThresholdResult()).isNotNull();
        assertThat(dto.getThresholdResult().isPassed()).isTrue();
        assertThat(dto.getThresholdResult().getViolations()).isEmpty();
    }
}
