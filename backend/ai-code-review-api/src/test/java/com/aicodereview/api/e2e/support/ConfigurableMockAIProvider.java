package com.aicodereview.api.e2e.support;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.integration.ai.AIProvider;

import java.time.Duration;
import java.util.List;

/**
 * Configurable AI provider for E2E tests that supports:
 * <ul>
 *   <li>Setting the next result to return (via {@link #setNextResult(ReviewResult)})</li>
 *   <li>Setting an exception to throw (via {@link #setNextException(AIProviderException)})</li>
 *   <li>Capturing the last rendered prompt for assertion (via {@link #getLastRenderedPrompt()})</li>
 * </ul>
 *
 * <p>This provider does NOT replace the existing {@link MockAIProvider}. It is registered
 * in test-specific {@code @TestConfiguration} classes with a distinct provider ID.</p>
 *
 * @since 9.4.0
 */
public class ConfigurableMockAIProvider implements AIProvider {

    private final String providerId;
    private ReviewResult nextResult = MockAIProvider.FIXED_RESULT;
    private AIProviderException nextException;
    private String lastRenderedPrompt;
    private CodeContext lastCodeContext;
    private Duration delay;

    public ConfigurableMockAIProvider(String providerId) {
        this.providerId = providerId;
    }

    /** Sets the result to return on the next {@link #analyze} call. Clears any pending exception. */
    public void setNextResult(ReviewResult result) {
        this.nextResult = result;
        this.nextException = null;
    }

    /** Sets an exception to throw on the next {@link #analyze} call. */
    public void setNextException(AIProviderException exception) {
        this.nextException = exception;
    }

    /** Sets a delay before returning from {@link #analyze} (simulates slow AI provider). */
    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    /** Returns the rendered prompt from the last {@link #analyze} call. */
    public String getLastRenderedPrompt() {
        return lastRenderedPrompt;
    }

    /** Returns the code context from the last {@link #analyze} call. */
    public CodeContext getLastCodeContext() {
        return lastCodeContext;
    }

    /** Resets captured state and pending exception. Result reverts to FIXED_RESULT. */
    public void reset() {
        this.nextResult = MockAIProvider.FIXED_RESULT;
        this.nextException = null;
        this.lastRenderedPrompt = null;
        this.lastCodeContext = null;
        this.delay = null;
    }

    /**
     * Analyzes the given context and returns the configured result or throws the configured exception.
     *
     * <p><strong>Provider ID override:</strong> If the returned result has metadata, this method
     * sets {@code metadata.providerId} to match {@code this.providerId}, ensuring the result
     * correctly reflects which provider instance actually executed the analysis.</p>
     *
     * <p><strong>Single-shot exception:</strong> {@code nextException} is cleared after throw,
     * so subsequent calls return {@code nextResult}. If using the same instance for both primary
     * and fallback providers (same ID), this behavior may cause unexpected results — use separate
     * instances with distinct IDs for degradation tests.</p>
     */
    @Override
    public ReviewResult analyze(CodeContext context, String renderedPrompt) {
        this.lastCodeContext = context;
        this.lastRenderedPrompt = renderedPrompt;

        if (delay != null) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIProviderException(408, "Provider interrupted during delay", e);
            }
        }

        if (nextException != null) {
            AIProviderException ex = nextException;
            nextException = null; // Single-shot: clear after throw
            throw ex;
        }

        // Ensure metadata.providerId matches this provider instance's actual ID
        if (nextResult != null && nextResult.isSuccess() && nextResult.getMetadata() != null) {
            nextResult.getMetadata().setProviderId(this.providerId);
        }
        return nextResult;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public int getMaxTokens() {
        return 8192;
    }

    // ─── Pre-built result factories ─────────────────────────────────────────

    /** 5 issues: 1 CRITICAL CORRECTNESS + 2 HIGH SECURITY + 1 MEDIUM PERFORMANCE + 1 LOW STYLE */
    public static ReviewResult mixedSeverityResult() {
        return ReviewResult.success(
                List.of(
                        ReviewIssue.builder().severity(IssueSeverity.CRITICAL).category(IssueCategory.CORRECTNESS)
                                .filePath("src/main/java/App.java").line(10)
                                .message("Null pointer dereference").suggestion("Add null check").build(),
                        ReviewIssue.builder().severity(IssueSeverity.HIGH).category(IssueCategory.SECURITY)
                                .filePath("src/main/java/App.java").line(20)
                                .message("SQL injection vulnerability").suggestion("Use parameterized query").build(),
                        ReviewIssue.builder().severity(IssueSeverity.HIGH).category(IssueCategory.SECURITY)
                                .filePath("src/main/java/App.java").line(25)
                                .message("Hardcoded secret detected").suggestion("Use environment variable").build(),
                        ReviewIssue.builder().severity(IssueSeverity.MEDIUM).category(IssueCategory.PERFORMANCE)
                                .filePath("src/main/java/App.java").line(30)
                                .message("N+1 query in loop").suggestion("Batch fetch").build(),
                        ReviewIssue.builder().severity(IssueSeverity.LOW).category(IssueCategory.STYLE)
                                .filePath("src/main/java/App.java").line(40)
                                .message("Magic number").suggestion("Extract constant").build()
                ),
                ReviewMetadata.builder().providerId("test-configurable").model("test-model")
                        .promptTokens(600).completionTokens(300).durationMs(200L).build()
        );
    }

    /** 0 issues — clean code result. */
    public static ReviewResult cleanResult() {
        return ReviewResult.success(
                List.of(),
                ReviewMetadata.builder().providerId("test-configurable").model("test-model")
                        .promptTokens(400).completionTokens(50).durationMs(100L).build()
        );
    }

    /** Failed review result (AI processing error). */
    public static ReviewResult failedResult() {
        return ReviewResult.failed("Test-induced AI processing failure");
    }
}
