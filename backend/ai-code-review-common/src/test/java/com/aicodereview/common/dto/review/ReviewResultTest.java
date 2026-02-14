package com.aicodereview.common.dto.review;

import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReviewResult}, {@link ReviewIssue}, and {@link ReviewMetadata}.
 *
 * @since 4.1.0
 */
@DisplayName("ReviewResult DTO Tests")
class ReviewResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success() factory should create a successful result")
    void successFactoryShouldCreateSuccessfulResult() {
        ReviewIssue issue = ReviewIssue.builder()
                .severity(IssueSeverity.HIGH)
                .category(IssueCategory.SECURITY)
                .filePath("src/Main.java")
                .line(42)
                .message("SQL injection vulnerability")
                .suggestion("Use parameterized queries")
                .build();

        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("openai")
                .model("gpt-4")
                .promptTokens(1000)
                .completionTokens(500)
                .durationMs(2500)
                .build();

        ReviewResult result = ReviewResult.success(List.of(issue), metadata);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(result.getMetadata().getProviderId()).isEqualTo("openai");
    }

    @Test
    @DisplayName("success() with null issues should default to empty list")
    void successWithNullIssuesShouldDefaultToEmptyList() {
        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("anthropic")
                .model("claude-3")
                .build();

        ReviewResult result = ReviewResult.success(null, metadata);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("success() with null metadata should throw NullPointerException")
    void successWithNullMetadataShouldThrow() {
        assertThatThrownBy(() -> ReviewResult.success(List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadata must not be null");
    }

    @Test
    @DisplayName("failed() factory should create a failed result")
    void failedFactoryShouldCreateFailedResult() {
        ReviewResult result = ReviewResult.failed("API call timed out");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("API call timed out");
        assertThat(result.getIssues()).isNotNull().isEmpty();
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    @DisplayName("ReviewIssue builder should populate and return all fields")
    void reviewIssueBuilderShouldPopulateAllFields() {
        ReviewIssue issue = ReviewIssue.builder()
                .severity(IssueSeverity.CRITICAL)
                .category(IssueCategory.SECURITY)
                .filePath("src/main/java/Auth.java")
                .line(55)
                .message("Hardcoded credentials")
                .suggestion("Use environment variables")
                .build();

        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
        assertThat(issue.getCategory()).isEqualTo(IssueCategory.SECURITY);
        assertThat(issue.getFilePath()).isEqualTo("src/main/java/Auth.java");
        assertThat(issue.getLine()).isEqualTo(55);
        assertThat(issue.getMessage()).isEqualTo("Hardcoded credentials");
        assertThat(issue.getSuggestion()).isEqualTo("Use environment variables");
    }

    @Test
    @DisplayName("ReviewIssue should support nullable filePath and line")
    void reviewIssueShouldSupportNullableFields() {
        ReviewIssue issue = ReviewIssue.builder()
                .severity(IssueSeverity.INFO)
                .category(IssueCategory.BEST_PRACTICES)
                .message("Consider adding more tests")
                .build();

        assertThat(issue.getFilePath()).isNull();
        assertThat(issue.getLine()).isNull();
        assertThat(issue.getSuggestion()).isNull();
        assertThat(issue.getMessage()).isEqualTo("Consider adding more tests");
    }

    @Test
    @DisplayName("ReviewMetadata degradationEvents should default to empty list")
    void reviewMetadataDegradationEventsShouldDefaultToEmptyList() {
        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("openai")
                .model("gpt-4")
                .build();

        assertThat(metadata.getDegradationEvents()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("ReviewMetadata should store degradation events")
    void reviewMetadataShouldStoreDegradationEvents() {
        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("openai")
                .model("gpt-4")
                .degradationEvents(List.of("Primary provider timeout", "Fell back to secondary"))
                .build();

        assertThat(metadata.getDegradationEvents()).hasSize(2);
        assertThat(metadata.getDegradationEvents()).contains("Primary provider timeout");
    }

    @Test
    @DisplayName("ReviewResult should serialize and deserialize via Jackson")
    void shouldSerializeAndDeserializeViaJackson() throws Exception {
        ReviewIssue issue = ReviewIssue.builder()
                .severity(IssueSeverity.MEDIUM)
                .category(IssueCategory.PERFORMANCE)
                .filePath("src/Service.java")
                .line(100)
                .message("N+1 query detected")
                .suggestion("Use JOIN FETCH")
                .build();

        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("openai")
                .model("gpt-4")
                .promptTokens(2000)
                .completionTokens(800)
                .durationMs(3000)
                .degradationEvents(List.of())
                .build();

        ReviewResult original = ReviewResult.success(List.of(issue), metadata);

        String json = objectMapper.writeValueAsString(original);
        ReviewResult deserialized = objectMapper.readValue(json, ReviewResult.class);

        assertThat(deserialized.isSuccess()).isTrue();
        assertThat(deserialized.getIssues()).hasSize(1);
        assertThat(deserialized.getIssues().get(0).getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(deserialized.getIssues().get(0).getCategory()).isEqualTo(IssueCategory.PERFORMANCE);
        assertThat(deserialized.getIssues().get(0).getFilePath()).isEqualTo("src/Service.java");
        assertThat(deserialized.getIssues().get(0).getLine()).isEqualTo(100);
        assertThat(deserialized.getIssues().get(0).getMessage()).isEqualTo("N+1 query detected");
        assertThat(deserialized.getIssues().get(0).getSuggestion()).isEqualTo("Use JOIN FETCH");
        assertThat(deserialized.getMetadata().getProviderId()).isEqualTo("openai");
        assertThat(deserialized.getMetadata().getModel()).isEqualTo("gpt-4");
        assertThat(deserialized.getMetadata().getPromptTokens()).isEqualTo(2000);
        assertThat(deserialized.getMetadata().getCompletionTokens()).isEqualTo(800);
        assertThat(deserialized.getMetadata().getDurationMs()).isEqualTo(3000);
    }

    @Test
    @DisplayName("Failed ReviewResult should serialize and deserialize via Jackson")
    void failedResultShouldSerializeAndDeserialize() throws Exception {
        ReviewResult original = ReviewResult.failed("Connection refused");

        String json = objectMapper.writeValueAsString(original);
        ReviewResult deserialized = objectMapper.readValue(json, ReviewResult.class);

        assertThat(deserialized.isSuccess()).isFalse();
        assertThat(deserialized.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(deserialized.getIssues()).isEmpty();
    }
}
