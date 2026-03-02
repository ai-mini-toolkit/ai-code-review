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

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * AI provider timeout E2E test — Story 9.7 AC#4.
 *
 * <p>Uses a {@link ConfigurableMockAIProvider} with delay to simulate
 * slow AI provider responses. Verifies the system handles timeouts
 * gracefully without hanging indefinitely.</p>
 *
 * @since 9.7.0
 */
@DisplayName("AI Provider Timeout E2E Tests")
@Import(AIProviderTimeoutE2ETest.TimeoutTestConfig.class)
class AIProviderTimeoutE2ETest extends AbstractE2ETest {

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/timeout-repo";

    @TestConfiguration
    static class TimeoutTestConfig {
        @Bean
        public ConfigurableMockAIProvider timeoutTestProvider() {
            return new ConfigurableMockAIProvider("test-timeout-provider");
        }
    }

    @DynamicPropertySource
    static void configureProviders(DynamicPropertyRegistry registry) {
        registry.add("ai.provider.default", () -> "test-timeout-provider");
        registry.add("ai.provider.fallback", () -> "test-timeout-provider");
    }

    @MockBean
    private ReviewContextAssembler contextAssembler;

    @SpyBean
    private WebhookVerificationChain verificationChain;

    @Autowired
    private ConfigurableMockAIProvider timeoutTestProvider;

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

        timeoutTestProvider.reset();

        when(contextAssembler.assembleContext(any())).thenReturn(
                CodeContext.builder()
                        .rawDiff("diff --git a/Timeout.java b/Timeout.java\n+// timeout test")
                        .files(Collections.emptyList())
                        .fileContents(Collections.emptyMap())
                        .statistics(DiffStatistics.builder()
                                .totalFilesChanged(1).totalLinesAdded(1).totalLinesDeleted(0).build())
                        .taskMeta(TaskMetadata.builder()
                                .author("e2e-test-user").branch("main")
                                .commitHash("timeout-sha").taskType(TaskType.PUSH).build())
                        .build());

        doReturn(true).when(verificationChain).verify(eq("codecommit"), any(), any(), any());
    }

    @Test
    @DisplayName("AC#4 — Slow AI provider completes within reasonable time")
    void testSlowProvider_CompletesWithinTimeout() {
        // Provider takes 2 seconds — should still complete within test timeout (30s)
        timeoutTestProvider.setDelay(Duration.ofSeconds(2));
        timeoutTestProvider.setNextResult(ConfigurableMockAIProvider.cleanResult());

        testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        testDataFactory.createCodeReviewPromptTemplate();

        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "slow-sha");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ReviewTask task = assertionHelper.assertTaskCreatedForCommit(
                GITHUB_REPO_URL, "slow-sha", TaskStatus.PENDING);

        reviewTaskService.markTaskStarted(task.getId());
        ReviewTask managedTask = reviewTaskRepository.findById(task.getId()).orElseThrow();

        long start = System.currentTimeMillis();
        ReviewResult result = reviewOrchestrator.review(managedTask);
        long elapsed = System.currentTimeMillis() - start;

        // Should succeed (just slow)
        assertThat(result.isSuccess()).isTrue();

        // Should take at least the delay duration
        assertThat(elapsed).isGreaterThanOrEqualTo(2000);

        // But should complete within a reasonable bound (not hang)
        assertThat(elapsed)
                .as("Slow provider should complete within 15 seconds")
                .isLessThan(15_000);
    }
}
