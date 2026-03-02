package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.AssertionHelper;
import com.aicodereview.api.e2e.support.ConfigurableMockAIProvider;
import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.constant.QueueKeys;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewContextAssembler;
import com.aicodereview.service.ReviewOrchestrator;
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

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * E2E tests for AI provider degradation strategy — Story 9.4.
 *
 * <p>Registers two providers: a "test-primary" that can be configured to fail,
 * and a "test-fallback" that returns configurable results. Tests verify the
 * three-level degradation chain in {@link ReviewOrchestrator}.</p>
 *
 * @since 9.4.0
 */
@DisplayName("AI Provider Degradation E2E Tests")
@Import(AIProviderDegradationE2ETest.DegradationTestConfig.class)
class AIProviderDegradationE2ETest extends AbstractE2ETest {

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/degradation-repo";

    @TestConfiguration
    static class DegradationTestConfig {
        @Bean
        public ConfigurableMockAIProvider testPrimaryProvider() {
            return new ConfigurableMockAIProvider("test-primary");
        }

        @Bean
        public ConfigurableMockAIProvider testFallbackProvider() {
            return new ConfigurableMockAIProvider("test-fallback");
        }
    }

    @DynamicPropertySource
    static void configureProviders(DynamicPropertyRegistry registry) {
        registry.add("ai.provider.default", () -> "test-primary");
        registry.add("ai.provider.fallback", () -> "test-fallback");
    }

    @MockBean
    private ReviewContextAssembler contextAssembler;

    @SpyBean
    private WebhookVerificationChain verificationChain;

    @Autowired
    private ConfigurableMockAIProvider testPrimaryProvider;

    @Autowired
    private ConfigurableMockAIProvider testFallbackProvider;

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private AssertionHelper assertionHelper;

    @Autowired
    private ReviewOrchestrator reviewOrchestrator;

    @Autowired
    private ReviewTaskService reviewTaskService;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

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

        testPrimaryProvider.reset();
        testFallbackProvider.reset();

        when(contextAssembler.assembleContext(any())).thenReturn(
                CodeContext.builder()
                        .rawDiff("diff --git a/Degrade.java b/Degrade.java\n+// degradation test")
                        .files(Collections.emptyList())
                        .fileContents(Collections.emptyMap())
                        .statistics(DiffStatistics.builder()
                                .totalFilesChanged(1).totalLinesAdded(1).totalLinesDeleted(0).build())
                        .taskMeta(TaskMetadata.builder()
                                .author("e2e-test-user").branch("main")
                                .commitHash("degrade-sha").taskType(TaskType.PUSH).build())
                        .build());

        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#4: Primary fails → Fallback succeeds
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#4 — Primary provider fails, fallback succeeds with degradation events")
    void testPrimaryFails_FallbackSucceeds() {
        // Configure: primary throws AIProviderException, fallback returns success
        testPrimaryProvider.setNextException(
                new AIProviderException(429, "Rate limit exceeded (test-induced)"));
        testFallbackProvider.setNextResult(ConfigurableMockAIProvider.mixedSeverityResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "degrade-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "degrade-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);

        // Fallback should succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).hasSize(5);

        // Metadata should reflect the fallback provider that actually executed
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getProviderId())
                .as("metadata.providerId should match fallback provider")
                .isEqualTo("test-fallback");

        // Degradation events should be recorded
        assertThat(result.getMetadata().getDegradationEvents()).isNotEmpty();
        assertThat(result.getMetadata().getDegradationEvents().get(0))
                .contains("test-primary")
                .contains("failed");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#5: Both providers fail → Graceful degradation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#5 — Both providers fail, returns failed ReviewResult gracefully")
    void testBothProvidersFail_GracefulDegradation() {
        // Configure: both throw AIProviderException
        testPrimaryProvider.setNextException(
                new AIProviderException(500, "Primary server error (test-induced)"));
        testFallbackProvider.setNextException(
                new AIProviderException(503, "Fallback unavailable (test-induced)"));

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "allfail-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "allfail-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        ReviewResult result = reviewOrchestrator.review(managedTask);

        // Should return failed result, NOT throw exception
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .as("Error message should indicate all providers failed")
                .containsIgnoringCase("all ai providers failed");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC#6: Prompt Template Rendering Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AC#6 — Rendered prompt contains rawDiff and is non-empty")
    void testPromptRendering_ContainsExpectedContent() {
        // Use fallback success (primary can succeed too — we just need to capture prompt)
        testPrimaryProvider.setNextResult(ConfigurableMockAIProvider.cleanResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "prompt-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "prompt-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();
        reviewOrchestrator.review(managedTask);

        // ConfigurableMockAIProvider captures the rendered prompt
        String renderedPrompt = testPrimaryProvider.getLastRenderedPrompt();
        assertThat(renderedPrompt)
                .as("Rendered prompt should not be null or empty")
                .isNotNull()
                .isNotEmpty();

        // Prompt should contain the test diff content (from contextAssembler mock)
        assertThat(renderedPrompt)
                .as("Rendered prompt should contain the rawDiff content")
                .contains("degradation test");
    }
}
