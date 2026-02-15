package com.aicodereview.service;

import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.Language;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.AIAuthenticationException;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.common.exception.RateLimitException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.common.exception.UnsupportedPlatformException;
import com.aicodereview.integration.ai.AIProvider;
import com.aicodereview.integration.ai.AIProviderFactory;
import com.aicodereview.repository.PromptTemplateRepository;
import com.aicodereview.repository.entity.PromptTemplate;
import com.aicodereview.repository.entity.ReviewTask;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {

    @Mock
    private ReviewContextAssembler contextAssembler;

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @Mock
    private AIProviderFactory providerFactory;

    @Mock
    private AIProvider primaryProvider;

    @Mock
    private AIProvider fallbackProvider;

    private SimpleMeterRegistry meterRegistry;
    private ReviewOrchestrator orchestrator;

    private static final String PRIMARY_PROVIDER_ID = "openai";
    private static final String FALLBACK_PROVIDER_ID = "anthropic";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orchestrator = new ReviewOrchestrator(
                contextAssembler, promptTemplateRepository, providerFactory,
                meterRegistry, FALLBACK_PROVIDER_ID);
    }

    // ========== Normal Flow Tests (AC1, AC2) ==========

    @Nested
    @DisplayName("Normal Flow (AC1, AC2)")
    class NormalFlow {

        @Test
        @DisplayName("Successful review: context assembly → prompt render → AI call → success")
        void shouldCompleteNormalReviewFlow() {
            // Arrange
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("Review this: {{rawDiff}}");
            ReviewResult expectedResult = buildSuccessResult(PRIMARY_PROVIDER_ID);

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString())).thenReturn(expectedResult);

            // Act
            ReviewResult result = orchestrator.review(task);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIssues()).hasSize(1);
            assertThat(result.getMetadata().getProviderId()).isEqualTo(PRIMARY_PROVIDER_ID);

            verify(contextAssembler).assembleContext(task);
            verify(promptTemplateRepository).findByCategoryAndEnabled("code-review", true);
            verify(primaryProvider).analyze(eq(codeContext), anyString());
            // Fallback should NOT be called
            verify(fallbackProvider, never()).analyze(any(), anyString());
        }

        @Test
        @DisplayName("Unexpected exception from context assembler returns ReviewResult.failed()")
        void shouldReturnFailedOnUnexpectedAssemblerException() {
            ReviewTask task = buildReviewTask();

            when(contextAssembler.assembleContext(task))
                    .thenThrow(new RuntimeException("Unexpected assembler failure"));

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unexpected assembler failure");
            verify(providerFactory, never()).getDefaultProvider();
        }

        @Test
        @DisplayName("Primary succeeds without invoking fallback — verify fallback never called")
        void shouldNotInvokeFallbackOnPrimarySuccess() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenReturn(buildSuccessResult(PRIMARY_PROVIDER_ID));

            orchestrator.review(task);

            verify(providerFactory, times(1)).getDefaultProvider();
            verify(providerFactory, never()).getProvider(FALLBACK_PROVIDER_ID);
        }
    }

    // ========== Prompt Rendering Tests (AC3) ==========

    @Nested
    @DisplayName("Prompt Rendering (AC3)")
    class PromptRendering {

        @Test
        @DisplayName("Renders Handlebars template with CodeContext variables")
        void shouldRenderTemplateWithHandlebarsVariables() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("Diff: {{rawDiff}}, Stats: {{statistics}}");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenReturn(buildSuccessResult(PRIMARY_PROVIDER_ID));

            orchestrator.review(task);

            // Verify the rendered prompt contains actual CodeContext data (not raw placeholders)
            verify(primaryProvider).analyze(eq(codeContext), argThat(prompt -> {
                assertThat(prompt).contains("--- a/file.java");  // rawDiff content
                assertThat(prompt).doesNotContain("{{rawDiff}}"); // placeholder should be replaced
                return true;
            }));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when no enabled template found")
        void shouldThrowWhenNoEnabledTemplate() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> orchestrator.review(task))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("code-review");
        }
    }

    // ========== Fallback Strategy Tests (AC4, AC5) ==========

    @Nested
    @DisplayName("Fallback Strategy (AC4, AC5)")
    class FallbackStrategy {

        @Test
        @DisplayName("Primary fails → fallback succeeds")
        void shouldFallbackOnPrimaryFailure() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");
            ReviewResult fallbackResult = buildSuccessResult(FALLBACK_PROVIDER_ID);

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Server error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString())).thenReturn(fallbackResult);

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMetadata().getDegradationEvents()).isNotEmpty();
            assertThat(result.getMetadata().getDegradationEvents().get(0))
                    .contains("openai").contains("failed");

            verify(primaryProvider, times(1)).analyze(eq(codeContext), anyString());
            verify(fallbackProvider, times(1)).analyze(eq(codeContext), anyString());
        }

        @Test
        @DisplayName("Both providers fail → ReviewResult.failed()")
        void shouldReturnFailedWhenBothProvidersFail() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Primary error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Fallback error"));

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("All AI providers failed");
        }

        @Test
        @DisplayName("Fallback skipped when same as primary")
        void shouldSkipFallbackWhenSameAsPrimary() {
            // Create orchestrator with same fallback as primary
            ReviewOrchestrator sameProviderOrchestrator = new ReviewOrchestrator(
                    contextAssembler, promptTemplateRepository, providerFactory,
                    meterRegistry, PRIMARY_PROVIDER_ID); // fallback = primary

            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Server error"));

            ReviewResult result = sameProviderOrchestrator.review(task);

            assertThat(result.isSuccess()).isFalse();
            // Fallback should NOT be attempted
            verify(providerFactory, never()).getProvider(anyString());
        }

        @Test
        @DisplayName("Fallback skipped when provider is unavailable")
        void shouldSkipFallbackWhenUnavailable() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Server error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(false);

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isFalse();
            verify(fallbackProvider, never()).analyze(any(), anyString());
        }

        @Test
        @DisplayName("Fallback skipped when provider is not registered")
        void shouldSkipFallbackWhenNotRegistered() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Server error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID))
                    .thenThrow(new UnsupportedPlatformException("Not registered"));

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("AIAuthenticationException triggers fallback")
        void shouldFallbackOnAuthenticationException() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIAuthenticationException("Invalid API key"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString()))
                    .thenReturn(buildSuccessResult(FALLBACK_PROVIDER_ID));

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isTrue();
            verify(fallbackProvider, times(1)).analyze(eq(codeContext), anyString());
        }

        @Test
        @DisplayName("RateLimitException (after internal retries exhausted) triggers fallback")
        void shouldFallbackOnRateLimitException() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new RateLimitException("Rate limit exceeded after 3 attempts"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString()))
                    .thenReturn(buildSuccessResult(FALLBACK_PROVIDER_ID));

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Degradation events recorded in metadata on fallback success")
        void shouldRecordDegradationEventsInMetadata() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");
            ReviewResult fallbackResult = buildSuccessResult(FALLBACK_PROVIDER_ID);

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "Primary error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString())).thenReturn(fallbackResult);

            ReviewResult result = orchestrator.review(task);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMetadata().getDegradationEvents()).hasSize(1);
            assertThat(result.getMetadata().getDegradationEvents().get(0))
                    .contains("openai").contains("Primary error");
        }
    }

    // ========== Metrics Tests (AC6) ==========

    @Nested
    @DisplayName("Metrics (AC6)")
    class Metrics {

        @Test
        @DisplayName("Records success counter on successful review")
        void shouldRecordSuccessCounter() {
            ReviewTask task = buildReviewTask();
            setupSuccessfulFlow(task);

            orchestrator.review(task);

            Counter successCounter = meterRegistry.find("ai.review.success").counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Records failure counter when all providers fail")
        void shouldRecordFailureCounter() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "error"));

            orchestrator.review(task);

            Counter failureCounter = meterRegistry.find("ai.review.failure").counter();
            assertThat(failureCounter).isNotNull();
            assertThat(failureCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Records review duration timer")
        void shouldRecordDurationTimer() {
            ReviewTask task = buildReviewTask();
            setupSuccessfulFlow(task);

            orchestrator.review(task);

            Timer timer = meterRegistry.find("ai.review.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Records provider used counter with tag")
        void shouldRecordProviderUsedCounter() {
            ReviewTask task = buildReviewTask();
            setupSuccessfulFlow(task);

            orchestrator.review(task);

            Counter providerUsed = meterRegistry.find("ai.review.provider.used")
                    .tag("provider", PRIMARY_PROVIDER_ID)
                    .counter();
            assertThat(providerUsed).isNotNull();
            assertThat(providerUsed.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Records degradation counter on fallback")
        void shouldRecordDegradationCounter() {
            ReviewTask task = buildReviewTask();
            CodeContext codeContext = buildCodeContext();
            PromptTemplate template = buildPromptTemplate("prompt");

            when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
            when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                    .thenReturn(List.of(template));
            when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
            when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
            when(primaryProvider.analyze(eq(codeContext), anyString()))
                    .thenThrow(new AIProviderException(500, "error"));
            when(providerFactory.getProvider(FALLBACK_PROVIDER_ID)).thenReturn(fallbackProvider);
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(fallbackProvider.analyze(eq(codeContext), anyString()))
                    .thenReturn(buildSuccessResult(FALLBACK_PROVIDER_ID));

            orchestrator.review(task);

            Counter degradationCounter = meterRegistry.find("ai.review.degradation")
                    .tag("from", PRIMARY_PROVIDER_ID)
                    .tag("to", FALLBACK_PROVIDER_ID)
                    .counter();
            assertThat(degradationCounter).isNotNull();
            assertThat(degradationCounter.count()).isEqualTo(1.0);
        }
    }

    // ========== Helper Methods ==========

    private void setupSuccessfulFlow(ReviewTask task) {
        CodeContext codeContext = buildCodeContext();
        PromptTemplate template = buildPromptTemplate("Review: {{rawDiff}}");

        when(contextAssembler.assembleContext(task)).thenReturn(codeContext);
        when(promptTemplateRepository.findByCategoryAndEnabled("code-review", true))
                .thenReturn(List.of(template));
        when(providerFactory.getDefaultProvider()).thenReturn(primaryProvider);
        when(primaryProvider.getProviderId()).thenReturn(PRIMARY_PROVIDER_ID);
        when(primaryProvider.analyze(eq(codeContext), anyString()))
                .thenReturn(buildSuccessResult(PRIMARY_PROVIDER_ID));
    }

    private ReviewTask buildReviewTask() {
        return ReviewTask.builder()
                .id(1L)
                .repoUrl("https://github.com/owner/repo")
                .branch("feature/test")
                .commitHash("abc123def456")
                .author("testuser")
                .taskType(TaskType.PULL_REQUEST)
                .prTitle("Test PR")
                .prDescription("Test description")
                .build();
    }

    private CodeContext buildCodeContext() {
        return CodeContext.builder()
                .rawDiff("--- a/file.java\n+++ b/file.java\n@@ -1,3 +1,3 @@\n-old\n+new")
                .files(List.of(FileInfo.builder()
                        .path("src/Main.java")
                        .changeType(ChangeType.MODIFY)
                        .language(Language.JAVA)
                        .build()))
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(1)
                        .totalLinesAdded(1)
                        .totalLinesDeleted(1)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .prTitle("Test PR")
                        .author("testuser")
                        .branch("feature/test")
                        .build())
                .fileContents(Map.of("src/Main.java", "public class Main {}"))
                .build();
    }

    private PromptTemplate buildPromptTemplate(String content) {
        return PromptTemplate.builder()
                .id(1L)
                .name("default-code-review")
                .category("code-review")
                .templateContent(content)
                .version(1)
                .enabled(true)
                .build();
    }

    private ReviewResult buildSuccessResult(String providerId) {
        ReviewIssue issue = ReviewIssue.builder()
                .severity(IssueSeverity.HIGH)
                .category(IssueCategory.CORRECTNESS)
                .filePath("src/Main.java")
                .line(42)
                .message("Null pointer risk")
                .suggestion("Add null check")
                .build();

        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId(providerId)
                .model("test-model")
                .promptTokens(1500)
                .completionTokens(800)
                .durationMs(5000)
                .build();

        return ReviewResult.success(List.of(issue), metadata);
    }
}
