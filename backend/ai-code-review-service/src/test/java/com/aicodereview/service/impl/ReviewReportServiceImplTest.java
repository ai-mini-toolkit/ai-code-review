package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReviewReportServiceImpl.
 *
 * @since 5.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewReportServiceImpl Unit Tests")
class ReviewReportServiceImplTest {

    @Mock
    private ReviewResultService reviewResultService;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @InjectMocks
    private ReviewReportServiceImpl reviewReportService;

    private ReviewTask testTask;
    private ReviewResultDTO testResult;
    private List<ReviewIssue> testIssues;
    private ReviewMetadata testMetadata;

    @BeforeEach
    void setUp() {
        Project testProject = Project.builder()
                .id(1L)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .enabled(true)
                .build();

        testTask = ReviewTask.builder()
                .id(100L)
                .project(testProject)
                .branch("main")
                .author("developer@test.com")
                .build();

        // NOTE: HIGH is intentionally placed BEFORE CRITICAL for UserService.java
        // to verify that sortBySeverityDesc() actually reorders them
        testIssues = List.of(
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH)
                        .category(IssueCategory.PERFORMANCE)
                        .filePath("UserService.java")
                        .line(88)
                        .message("N+1 query in loop")
                        .suggestion("Use batch query")
                        .build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.CRITICAL)
                        .category(IssueCategory.SECURITY)
                        .filePath("UserService.java")
                        .line(42)
                        .message("SQL injection vulnerability")
                        .suggestion("Use PreparedStatement")
                        .build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.LOW)
                        .category(IssueCategory.STYLE)
                        .filePath("Controller.java")
                        .line(30)
                        .message("Inconsistent naming")
                        .suggestion("Use camelCase")
                        .build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.MEDIUM)
                        .category(IssueCategory.MAINTAINABILITY)
                        .filePath("Controller.java")
                        .line(15)
                        .message("Method too complex")
                        .suggestion("Extract helper method")
                        .build()
        );

        testMetadata = ReviewMetadata.builder()
                .providerId("anthropic")
                .model("claude-sonnet")
                .promptTokens(1000)
                .completionTokens(500)
                .durationMs(2000L)
                .degradationEvents(List.of())
                .build();

        ReviewStatisticsDTO stats = ReviewStatisticsDTO.builder()
                .total(4)
                .bySeverity(Map.of("CRITICAL", 1, "HIGH", 1, "MEDIUM", 1, "LOW", 1, "INFO", 0))
                .byCategory(Map.of("SECURITY", 1, "PERFORMANCE", 1, "MAINTAINABILITY", 1,
                        "CORRECTNESS", 0, "STYLE", 1, "BEST_PRACTICES", 0))
                .build();

        testResult = ReviewResultDTO.builder()
                .id(1L)
                .taskId(100L)
                .issues(testIssues)
                .statistics(stats)
                .metadata(testMetadata)
                .success(true)
                .createdAt(Instant.parse("2026-02-15T10:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("generateReport()")
    class GenerateReportTests {

        @Test
        @DisplayName("Should generate complete report with grouped issues")
        void shouldGenerateCompleteReport() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getTaskId()).isEqualTo(100L);
            assertThat(report.getProjectName()).isEqualTo("Test Project");
            assertThat(report.getBranch()).isEqualTo("main");
            assertThat(report.getAuthor()).isEqualTo("developer@test.com");
            assertThat(report.getSuccess()).isTrue();
            assertThat(report.getSummary().getTotal()).isEqualTo(4);
            assertThat(report.getMetadata().getProviderId()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("Should group issues by file correctly")
        void shouldGroupIssuesByFile() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getIssuesByFile()).hasSize(2);
            assertThat(report.getIssuesByFile().get("UserService.java")).hasSize(2);
            assertThat(report.getIssuesByFile().get("Controller.java")).hasSize(2);

            // Verify sorted by severity descending within file group
            // Input has HIGH before CRITICAL — sort must reorder
            List<ReviewIssue> userServiceIssues = report.getIssuesByFile().get("UserService.java");
            assertThat(userServiceIssues.get(0).getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
            assertThat(userServiceIssues.get(1).getSeverity()).isEqualTo(IssueSeverity.HIGH);

            // Input has LOW before MEDIUM — sort must reorder
            List<ReviewIssue> controllerIssues = report.getIssuesByFile().get("Controller.java");
            assertThat(controllerIssues.get(0).getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
            assertThat(controllerIssues.get(1).getSeverity()).isEqualTo(IssueSeverity.LOW);
        }

        @Test
        @DisplayName("Should group issues by severity with all enum values")
        void shouldGroupIssuesBySeverity() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getIssuesBySeverity()).hasSize(5); // All IssueSeverity values
            assertThat(report.getIssuesBySeverity().get("CRITICAL")).hasSize(1);
            assertThat(report.getIssuesBySeverity().get("HIGH")).hasSize(1);
            assertThat(report.getIssuesBySeverity().get("MEDIUM")).hasSize(1);
            assertThat(report.getIssuesBySeverity().get("LOW")).hasSize(1);
            assertThat(report.getIssuesBySeverity().get("INFO")).isEmpty();
        }

        @Test
        @DisplayName("Should group issues by category with all enum values")
        void shouldGroupIssuesByCategory() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getIssuesByCategory()).hasSize(6); // All IssueCategory values
            assertThat(report.getIssuesByCategory().get("SECURITY")).hasSize(1);
            assertThat(report.getIssuesByCategory().get("PERFORMANCE")).hasSize(1);
            assertThat(report.getIssuesByCategory().get("MAINTAINABILITY")).hasSize(1);
            assertThat(report.getIssuesByCategory().get("CORRECTNESS")).isEmpty();
            assertThat(report.getIssuesByCategory().get("STYLE")).hasSize(1);
            assertThat(report.getIssuesByCategory().get("BEST_PRACTICES")).isEmpty();
        }

        @Test
        @DisplayName("Should generate report for failed review with empty groups")
        void shouldGenerateFailedReport() {
            ReviewResultDTO failedResult = ReviewResultDTO.builder()
                    .id(2L)
                    .taskId(100L)
                    .issues(List.of())
                    .statistics(ReviewStatisticsDTO.builder()
                            .total(0)
                            .bySeverity(Map.of())
                            .byCategory(Map.of())
                            .build())
                    .success(false)
                    .errorMessage("API timeout")
                    .createdAt(Instant.now())
                    .build();

            when(reviewResultService.getResultByTaskId(100L)).thenReturn(failedResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getSuccess()).isFalse();
            assertThat(report.getErrorMessage()).isEqualTo("API timeout");
            assertThat(report.getIssuesByFile()).isEmpty();
            assertThat(report.getIssuesBySeverity()).containsKey("CRITICAL");
            assertThat(report.getIssuesBySeverity().get("CRITICAL")).isEmpty();
            assertThat(report.getSummary().getTotal()).isZero();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when result not found")
        void shouldThrowWhenResultNotFound() {
            when(reviewResultService.getResultByTaskId(999L))
                    .thenThrow(new ResourceNotFoundException("ReviewResult", "taskId", 999L));

            assertThatThrownBy(() -> reviewReportService.generateReport(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should handle empty issues list")
        void shouldHandleEmptyIssues() {
            ReviewResultDTO emptyResult = ReviewResultDTO.builder()
                    .id(3L)
                    .taskId(100L)
                    .issues(List.of())
                    .statistics(ReviewStatisticsDTO.builder()
                            .total(0)
                            .bySeverity(Map.of("CRITICAL", 0, "HIGH", 0, "MEDIUM", 0, "LOW", 0, "INFO", 0))
                            .byCategory(Map.of("SECURITY", 0, "PERFORMANCE", 0, "MAINTAINABILITY", 0,
                                    "CORRECTNESS", 0, "STYLE", 0, "BEST_PRACTICES", 0))
                            .build())
                    .metadata(testMetadata)
                    .success(true)
                    .createdAt(Instant.now())
                    .build();

            when(reviewResultService.getResultByTaskId(100L)).thenReturn(emptyResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));

            ReviewReportDTO report = reviewReportService.generateReport(100L);

            assertThat(report.getSuccess()).isTrue();
            assertThat(report.getIssuesByFile()).isEmpty();
            assertThat(report.getSummary().getTotal()).isZero();
        }
    }

    @Nested
    @DisplayName("renderMarkdown()")
    class RenderMarkdownTests {

        @Test
        @DisplayName("Should render Markdown with tables and headers")
        void shouldRenderMarkdown() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
            ReviewReportDTO report = reviewReportService.generateReport(100L);

            String markdown = reviewReportService.renderMarkdown(report);

            assertThat(markdown).contains("# Code Review Report");
            assertThat(markdown).contains("**Project:** Test Project");
            assertThat(markdown).contains("**Branch:** main");
            assertThat(markdown).contains("**Author:** developer@test.com");
            assertThat(markdown).contains("**Status:** Passed");
            assertThat(markdown).contains("## Summary");
            assertThat(markdown).contains("| CRITICAL | 1 |");
            assertThat(markdown).contains("**Total Issues:** 4");
            assertThat(markdown).contains("## Issues by File");
            assertThat(markdown).contains("### UserService.java (2 issues)");
            assertThat(markdown).contains("### Controller.java (2 issues)");
            assertThat(markdown).contains("SQL injection vulnerability");
            assertThat(markdown).contains("## Review Metadata");
            assertThat(markdown).contains("**Provider:** anthropic");
        }

        @Test
        @DisplayName("Should render failed report with error only")
        void shouldRenderFailedMarkdown() {
            ReviewReportDTO failedReport = ReviewReportDTO.builder()
                    .taskId(100L)
                    .projectName("Test Project")
                    .branch("main")
                    .author("dev@test.com")
                    .reviewedAt(Instant.parse("2026-02-15T10:00:00Z"))
                    .success(false)
                    .errorMessage("API timeout: 30s exceeded")
                    .build();

            String markdown = reviewReportService.renderMarkdown(failedReport);

            assertThat(markdown).contains("**Status:** Failed");
            assertThat(markdown).contains("## Error");
            assertThat(markdown).contains("API timeout: 30s exceeded");
            assertThat(markdown).doesNotContain("## Summary");
            assertThat(markdown).doesNotContain("## Issues by File");
        }
    }

    @Nested
    @DisplayName("renderHtml()")
    class RenderHtmlTests {

        @Test
        @DisplayName("Should render HTML with tables and inline styles")
        void shouldRenderHtml() {
            when(reviewResultService.getResultByTaskId(100L)).thenReturn(testResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
            ReviewReportDTO report = reviewReportService.generateReport(100L);

            String html = reviewReportService.renderHtml(report);

            assertThat(html).contains("<!DOCTYPE html>");
            assertThat(html).contains("<h1");
            assertThat(html).contains("Code Review Report");
            assertThat(html).contains("<table");
            assertThat(html).contains("style=");
            assertThat(html).contains("#dc3545"); // CRITICAL color
            assertThat(html).contains("UserService.java");
            assertThat(html).contains("SQL injection vulnerability");
            assertThat(html).contains("</html>");
        }

        @Test
        @DisplayName("Should render failed HTML with error div")
        void shouldRenderFailedHtml() {
            ReviewReportDTO failedReport = ReviewReportDTO.builder()
                    .taskId(100L)
                    .projectName("Test Project")
                    .branch("main")
                    .author("dev@test.com")
                    .reviewedAt(Instant.parse("2026-02-15T10:00:00Z"))
                    .success(false)
                    .errorMessage("API timeout")
                    .build();

            String html = reviewReportService.renderHtml(failedReport);

            assertThat(html).contains("Failed");
            assertThat(html).contains("#dc3545"); // Red for failed
            assertThat(html).contains("API timeout");
            assertThat(html).doesNotContain("<h2>Summary</h2>");
        }

        @Test
        @DisplayName("Should escape HTML special characters")
        void shouldEscapeHtmlCharacters() {
            ReviewIssue xssIssue = ReviewIssue.builder()
                    .severity(IssueSeverity.HIGH)
                    .category(IssueCategory.SECURITY)
                    .filePath("Test.java")
                    .line(1)
                    .message("Found <script>alert('xss')</script> in code")
                    .suggestion("Remove <script> tags")
                    .build();

            ReviewResultDTO xssResult = ReviewResultDTO.builder()
                    .id(1L)
                    .taskId(100L)
                    .issues(List.of(xssIssue))
                    .statistics(ReviewStatisticsDTO.builder().total(1)
                            .bySeverity(Map.of("HIGH", 1))
                            .byCategory(Map.of("SECURITY", 1)).build())
                    .metadata(testMetadata)
                    .success(true)
                    .createdAt(Instant.now())
                    .build();

            when(reviewResultService.getResultByTaskId(100L)).thenReturn(xssResult);
            when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(testTask));
            ReviewReportDTO report = reviewReportService.generateReport(100L);

            String html = reviewReportService.renderHtml(report);

            assertThat(html).contains("&lt;script&gt;");
            assertThat(html).doesNotContain("<script>alert");
        }
    }
}
