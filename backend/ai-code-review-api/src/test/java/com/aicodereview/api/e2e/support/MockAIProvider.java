package com.aicodereview.api.e2e.support;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.integration.ai.AIProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Test configuration that replaces all real {@link AIProvider} beans with a
 * deterministic mock, preventing real API calls during E2E tests.
 *
 * <p>Import this configuration into E2E test classes that trigger the AI
 * review pipeline:</p>
 * <pre>
 * &#64;Import(MockAIProvider.MockAIConfiguration.class)
 * class MyE2ETest extends AbstractE2ETest { ... }
 * </pre>
 *
 * <p>The mock always returns a fixed {@link ReviewResult} with two sample
 * issues, making test assertions deterministic regardless of code content.</p>
 *
 * @since 9.1.0
 */
public class MockAIProvider implements AIProvider {

    /** Fixed result returned by the mock — deterministic for assertions. */
    public static final ReviewResult FIXED_RESULT = ReviewResult.success(
            List.of(
                    ReviewIssue.builder()
                            .severity(IssueSeverity.HIGH)
                            .category(IssueCategory.SECURITY)
                            .filePath("src/main/java/Example.java")
                            .line(42)
                            .message("Potential SQL injection vulnerability detected")
                            .suggestion("Use parameterized queries or PreparedStatement")
                            .build(),
                    ReviewIssue.builder()
                            .severity(IssueSeverity.MEDIUM)
                            .category(IssueCategory.PERFORMANCE)
                            .filePath("src/main/java/Example.java")
                            .line(78)
                            .message("N+1 query pattern detected in loop")
                            .suggestion("Consider fetching related entities in a single query")
                            .build()
            ),
            ReviewMetadata.builder()
                    .providerId("mock-ai")
                    .model("mock-model-1.0")
                    .promptTokens(500)
                    .completionTokens(200)
                    .durationMs(150L)
                    .build()
    );

    @Override
    public ReviewResult analyze(CodeContext context, String renderedPrompt) {
        return FIXED_RESULT;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProviderId() {
        return "mock-ai";
    }

    @Override
    public int getMaxTokens() {
        return 8192;
    }

    // ─── Spring TestConfiguration ─────────────────────────────────────────────

    /**
     * Inner {@link TestConfiguration} that registers the mock as the primary
     * {@link AIProvider} bean, overriding any real providers in the context.
     */
    @TestConfiguration
    public static class MockAIConfiguration {

        @Bean
        @Primary
        public AIProvider mockAiProvider() {
            return new MockAIProvider();
        }
    }
}
