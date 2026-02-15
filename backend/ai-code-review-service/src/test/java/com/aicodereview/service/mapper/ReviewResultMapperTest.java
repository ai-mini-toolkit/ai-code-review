package com.aicodereview.service.mapper;

import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReviewResultMapper.
 * Covers JSON serialization/deserialization round-trips and statistics calculation.
 *
 * @since 5.1.0
 */
@DisplayName("ReviewResultMapper Unit Tests")
class ReviewResultMapperTest {

    @Nested
    @DisplayName("Issues Serialization")
    class IssuesSerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize issues round-trip")
        void shouldRoundTripIssues() {
            List<ReviewIssue> issues = List.of(
                    ReviewIssue.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .category(IssueCategory.SECURITY)
                            .filePath("UserService.java")
                            .line(42)
                            .message("SQL injection vulnerability")
                            .suggestion("Use PreparedStatement")
                            .build(),
                    ReviewIssue.builder()
                            .severity(IssueSeverity.MEDIUM)
                            .category(IssueCategory.PERFORMANCE)
                            .filePath("Controller.java")
                            .line(15)
                            .message("N+1 query in loop")
                            .suggestion("Use batch query")
                            .build()
            );

            String json = ReviewResultMapper.serializeIssues(issues);
            List<ReviewIssue> deserialized = ReviewResultMapper.deserializeIssues(json);

            assertThat(deserialized).hasSize(2);
            assertThat(deserialized.get(0).getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
            assertThat(deserialized.get(0).getCategory()).isEqualTo(IssueCategory.SECURITY);
            assertThat(deserialized.get(0).getFilePath()).isEqualTo("UserService.java");
            assertThat(deserialized.get(0).getLine()).isEqualTo(42);
            assertThat(deserialized.get(0).getMessage()).isEqualTo("SQL injection vulnerability");
            assertThat(deserialized.get(0).getSuggestion()).isEqualTo("Use PreparedStatement");
            assertThat(deserialized.get(1).getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        }

        @Test
        @DisplayName("Should serialize null issues to empty array")
        void shouldSerializeNullToEmptyArray() {
            String json = ReviewResultMapper.serializeIssues(null);
            assertThat(json).isEqualTo("[]");
        }

        @Test
        @DisplayName("Should serialize empty list to empty array")
        void shouldSerializeEmptyList() {
            String json = ReviewResultMapper.serializeIssues(List.of());
            assertThat(json).isEqualTo("[]");
        }

        @Test
        @DisplayName("Should deserialize null/blank to empty list")
        void shouldDeserializeNullToEmptyList() {
            assertThat(ReviewResultMapper.deserializeIssues(null)).isEmpty();
            assertThat(ReviewResultMapper.deserializeIssues("")).isEmpty();
            assertThat(ReviewResultMapper.deserializeIssues("  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Metadata Serialization")
    class MetadataSerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize metadata round-trip")
        void shouldRoundTripMetadata() {
            ReviewMetadata metadata = ReviewMetadata.builder()
                    .providerId("anthropic")
                    .model("claude-sonnet-4-5-20250929")
                    .promptTokens(3000)
                    .completionTokens(800)
                    .durationMs(2500L)
                    .degradationEvents(List.of("Fallback from openai to anthropic"))
                    .build();

            String json = ReviewResultMapper.serializeMetadata(metadata);
            ReviewMetadata deserialized = ReviewResultMapper.deserializeMetadata(json);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.getProviderId()).isEqualTo("anthropic");
            assertThat(deserialized.getModel()).isEqualTo("claude-sonnet-4-5-20250929");
            assertThat(deserialized.getPromptTokens()).isEqualTo(3000);
            assertThat(deserialized.getCompletionTokens()).isEqualTo(800);
            assertThat(deserialized.getDurationMs()).isEqualTo(2500L);
            assertThat(deserialized.getDegradationEvents()).containsExactly("Fallback from openai to anthropic");
        }

        @Test
        @DisplayName("Should serialize null metadata to empty object")
        void shouldSerializeNullMetadata() {
            String json = ReviewResultMapper.serializeMetadata(null);
            assertThat(json).isEqualTo("{}");
        }

        @Test
        @DisplayName("Should deserialize empty/null metadata to null")
        void shouldDeserializeEmptyMetadataToNull() {
            assertThat(ReviewResultMapper.deserializeMetadata(null)).isNull();
            assertThat(ReviewResultMapper.deserializeMetadata("{}")).isNull();
            assertThat(ReviewResultMapper.deserializeMetadata("")).isNull();
        }
    }

    @Nested
    @DisplayName("Statistics Serialization")
    class StatisticsSerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize statistics round-trip")
        void shouldRoundTripStatistics() {
            ReviewStatisticsDTO stats = ReviewStatisticsDTO.builder()
                    .total(5)
                    .bySeverity(Map.of("CRITICAL", 1, "HIGH", 2, "MEDIUM", 2, "LOW", 0, "INFO", 0))
                    .byCategory(Map.of("SECURITY", 1, "PERFORMANCE", 2, "MAINTAINABILITY", 2,
                            "CORRECTNESS", 0, "STYLE", 0, "BEST_PRACTICES", 0))
                    .build();

            String json = ReviewResultMapper.serializeStatistics(stats);
            ReviewStatisticsDTO deserialized = ReviewResultMapper.deserializeStatistics(json);

            assertThat(deserialized.getTotal()).isEqualTo(5);
            assertThat(deserialized.getBySeverity().get("CRITICAL")).isEqualTo(1);
            assertThat(deserialized.getBySeverity().get("HIGH")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Statistics Calculation")
    class StatisticsCalculationTests {

        @Test
        @DisplayName("Should calculate statistics from issues list")
        void shouldCalculateStatisticsFromIssues() {
            List<ReviewIssue> issues = List.of(
                    ReviewIssue.builder().severity(IssueSeverity.CRITICAL).category(IssueCategory.SECURITY).build(),
                    ReviewIssue.builder().severity(IssueSeverity.CRITICAL).category(IssueCategory.SECURITY).build(),
                    ReviewIssue.builder().severity(IssueSeverity.HIGH).category(IssueCategory.PERFORMANCE).build(),
                    ReviewIssue.builder().severity(IssueSeverity.MEDIUM).category(IssueCategory.MAINTAINABILITY).build(),
                    ReviewIssue.builder().severity(IssueSeverity.MEDIUM).category(IssueCategory.MAINTAINABILITY).build(),
                    ReviewIssue.builder().severity(IssueSeverity.MEDIUM).category(IssueCategory.MAINTAINABILITY).build()
            );

            ReviewStatisticsDTO stats = ReviewResultMapper.calculateStatistics(issues);

            assertThat(stats.getTotal()).isEqualTo(6);
            assertThat(stats.getBySeverity()).containsEntry("CRITICAL", 2);
            assertThat(stats.getBySeverity()).containsEntry("HIGH", 1);
            assertThat(stats.getBySeverity()).containsEntry("MEDIUM", 3);
            assertThat(stats.getBySeverity()).containsEntry("LOW", 0);
            assertThat(stats.getBySeverity()).containsEntry("INFO", 0);
            assertThat(stats.getByCategory()).containsEntry("SECURITY", 2);
            assertThat(stats.getByCategory()).containsEntry("PERFORMANCE", 1);
            assertThat(stats.getByCategory()).containsEntry("MAINTAINABILITY", 3);
            assertThat(stats.getByCategory()).containsEntry("CORRECTNESS", 0);
            assertThat(stats.getByCategory()).containsEntry("STYLE", 0);
            assertThat(stats.getByCategory()).containsEntry("BEST_PRACTICES", 0);
        }

        @Test
        @DisplayName("Should return empty statistics for null issues")
        void shouldReturnEmptyStatsForNull() {
            ReviewStatisticsDTO stats = ReviewResultMapper.calculateStatistics(null);

            assertThat(stats.getTotal()).isZero();
            assertThat(stats.getBySeverity()).containsEntry("CRITICAL", 0);
            assertThat(stats.getByCategory()).containsEntry("SECURITY", 0);
        }

        @Test
        @DisplayName("Should return empty statistics for empty list")
        void shouldReturnEmptyStatsForEmptyList() {
            ReviewStatisticsDTO stats = ReviewResultMapper.calculateStatistics(List.of());

            assertThat(stats.getTotal()).isZero();
            // All severity values should be 0
            for (IssueSeverity s : IssueSeverity.values()) {
                assertThat(stats.getBySeverity()).containsEntry(s.name(), 0);
            }
            // All category values should be 0
            for (IssueCategory c : IssueCategory.values()) {
                assertThat(stats.getByCategory()).containsEntry(c.name(), 0);
            }
        }
    }
}
