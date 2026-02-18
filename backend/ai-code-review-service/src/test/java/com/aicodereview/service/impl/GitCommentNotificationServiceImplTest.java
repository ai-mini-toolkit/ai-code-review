package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.integration.git.AWSCodeCommitCommentService;
import com.aicodereview.integration.git.GitHubPRCommentService;
import com.aicodereview.integration.git.GitLabMRCommentService;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitCommentNotificationServiceImpl.
 *
 * @since 7.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitCommentNotificationServiceImpl Unit Tests")
class GitCommentNotificationServiceImplTest {

    @Mock
    private NotificationConfigRepository notificationConfigRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private ReviewReportService reviewReportService;

    @Mock
    private ReviewResultService reviewResultService;

    @Mock
    private GitHubPRCommentService gitHubPRCommentService;

    @Mock
    private GitLabMRCommentService gitLabMRCommentService;

    @Mock
    private AWSCodeCommitCommentService awsCodeCommitCommentService;

    private GitCommentNotificationServiceImpl service;

    private static final Long TASK_ID = 100L;
    private static final Long PROJECT_ID = 1L;
    private static final Integer PR_NUMBER = 42;

    @BeforeEach
    void setUp() {
        service = new GitCommentNotificationServiceImpl(
                notificationConfigRepository,
                reviewTaskRepository,
                reviewReportService,
                reviewResultService,
                gitHubPRCommentService,
                gitLabMRCommentService,
                awsCodeCommitCommentService);
    }

    private Project buildProject(String platform) {
        return Project.builder().id(PROJECT_ID).name("Test Project").gitPlatform(platform).build();
    }

    private ReviewTask buildTask(String platform, Integer prNumber) {
        return ReviewTask.builder()
                .id(TASK_ID)
                .project(buildProject(platform))
                .repoUrl("https://github.com/owner/repo")
                .prNumber(prNumber)
                .build();
    }

    private NotificationConfigEntity buildConfig(boolean commentEnabled) {
        return NotificationConfigEntity.builder()
                .id(1L)
                .project(Project.builder().id(PROJECT_ID).build())
                .commentEnabled(commentEnabled)
                .emailEnabled(false)
                .build();
    }

    private ReviewReportDTO buildReport(boolean success) {
        return ReviewReportDTO.builder()
                .taskId(TASK_ID)
                .projectName("Test Project")
                .branch("main")
                .author("dev@test.com")
                .success(success)
                .summary(ReviewStatisticsDTO.builder()
                        .total(3)
                        .bySeverity(Map.of("HIGH", 1, "MEDIUM", 2))
                        .byCategory(Map.of())
                        .build())
                .issuesBySeverity(Map.of(
                        "HIGH", List.of(ReviewIssue.builder()
                                .severity(IssueSeverity.HIGH)
                                .category(IssueCategory.CORRECTNESS)
                                .filePath("src/Main.java")
                                .line(10)
                                .message("Potential null pointer")
                                .build()),
                        "MEDIUM", List.of(ReviewIssue.builder()
                                .severity(IssueSeverity.MEDIUM)
                                .category(IssueCategory.STYLE)
                                .filePath("src/Util.java")
                                .line(20)
                                .message("Unused variable")
                                .build())))
                .build();
    }

    private ReviewResultDTO buildResultDTO(boolean thresholdPassed) {
        return ReviewResultDTO.builder()
                .taskId(TASK_ID)
                .thresholdResult(ThresholdValidationResultDTO.builder()
                        .passed(thresholdPassed)
                        .violations(thresholdPassed ? List.of() :
                                List.of(ThresholdViolationDTO.builder()
                                        .rule("HIGH <= 0").actual(1).threshold(0).build()))
                        .action(thresholdPassed ? null : "BLOCK_MERGE")
                        .build())
                .build();
    }

    @Nested
    @DisplayName("postReviewComment")
    class PostReviewComment {

        @Test
        @DisplayName("Should post GitHub PR comment when all conditions met")
        void shouldPostGitHubComment() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask("GitHub", PR_NUMBER)));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true)));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(true));
            when(reviewResultService.getResultByTaskId(TASK_ID)).thenReturn(buildResultDTO(true));

            service.postReviewComment(TASK_ID);

            verify(gitHubPRCommentService).postComment(eq("https://github.com/owner/repo"), eq(PR_NUMBER), any());
            verifyNoInteractions(gitLabMRCommentService);
            verifyNoInteractions(awsCodeCommitCommentService);
        }

        @Test
        @DisplayName("Should post GitLab MR comment when platform is GitLab")
        void shouldPostGitLabComment() {
            ReviewTask task = buildTask("GitLab", PR_NUMBER);
            task.setRepoUrl("https://gitlab.com/group/project");
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true)));
            when(reviewReportService.generateReport(TASK_ID)).thenReturn(buildReport(true));
            when(reviewResultService.getResultByTaskId(TASK_ID)).thenReturn(buildResultDTO(true));

            service.postReviewComment(TASK_ID);

            verify(gitLabMRCommentService).postComment(eq("https://gitlab.com/group/project"), eq(PR_NUMBER), any());
            verifyNoInteractions(gitHubPRCommentService);
        }

        @Test
        @DisplayName("Should skip when task has no PR number (PUSH task)")
        void shouldSkipWhenNoPrNumber() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask("GitHub", null)));

            service.postReviewComment(TASK_ID);

            verifyNoInteractions(notificationConfigRepository);
            verifyNoInteractions(gitHubPRCommentService);
        }

        @Test
        @DisplayName("Should skip when task not found")
        void shouldSkipWhenTaskNotFound() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            service.postReviewComment(TASK_ID);

            verifyNoInteractions(gitHubPRCommentService);
        }

        @Test
        @DisplayName("Should skip when comment not enabled")
        void shouldSkipWhenNotEnabled() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask("GitHub", PR_NUMBER)));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(false)));

            service.postReviewComment(TASK_ID);

            verifyNoInteractions(gitHubPRCommentService);
        }

        @Test
        @DisplayName("Should skip when no notification config")
        void shouldSkipWhenNoConfig() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask("GitHub", PR_NUMBER)));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

            service.postReviewComment(TASK_ID);

            verifyNoInteractions(gitHubPRCommentService);
        }

        @Test
        @DisplayName("Should not propagate exceptions from platform service")
        void shouldNotPropagateExceptions() {
            when(reviewTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(buildTask("GitHub", PR_NUMBER)));
            when(notificationConfigRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(buildConfig(true)));
            when(reviewReportService.generateReport(TASK_ID)).thenThrow(new RuntimeException("Report error"));

            // Should not throw
            service.postReviewComment(TASK_ID);
        }
    }

    @Nested
    @DisplayName("buildCommentBody")
    class BuildCommentBody {

        @Test
        @DisplayName("Should contain AI header")
        void shouldContainHeader() {
            String body = service.buildCommentBody(buildReport(true),
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body).contains("AI Code Review");
        }

        @Test
        @DisplayName("Should contain summary table")
        void shouldContainSummaryTable() {
            String body = service.buildCommentBody(buildReport(true),
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body).contains("| Severity | Count |");
            assertThat(body).contains("HIGH");
            assertThat(body).contains("Total Issues:** 3");
        }

        @Test
        @DisplayName("Should show passed threshold")
        void shouldShowPassedThreshold() {
            String body = service.buildCommentBody(buildReport(true),
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body).contains("Threshold: **Passed**");
        }

        @Test
        @DisplayName("Should show failed threshold with violations")
        void shouldShowFailedThreshold() {
            ThresholdValidationResultDTO thresholdResult = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder().rule("HIGH <= 0").actual(1).threshold(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            String body = service.buildCommentBody(buildReport(false), thresholdResult);
            assertThat(body).contains("Threshold: **FAILED**");
            assertThat(body).contains("1 violation(s)");
            assertThat(body).contains("HIGH <= 0");
            assertThat(body).contains("BLOCK_MERGE");
        }

        @Test
        @DisplayName("Should contain top issues")
        void shouldContainTopIssues() {
            String body = service.buildCommentBody(buildReport(true),
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body).contains("Top Issues");
            assertThat(body).contains("Potential null pointer");
        }

        @Test
        @DisplayName("Should contain footer")
        void shouldContainFooter() {
            String body = service.buildCommentBody(buildReport(true),
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body).contains("Generated by AI Code Review");
        }

        @Test
        @DisplayName("Should truncate when exceeding max length")
        void shouldTruncateWhenTooLong() {
            // Create report with very long content
            StringBuilder longMessage = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longMessage.append("Very long issue description ");
            }
            ReviewReportDTO report = ReviewReportDTO.builder()
                    .taskId(TASK_ID)
                    .projectName("Test")
                    .branch("main")
                    .success(true)
                    .summary(ReviewStatisticsDTO.builder().total(1).bySeverity(Map.of()).byCategory(Map.of()).build())
                    .issuesBySeverity(Map.of("HIGH", List.of(
                            ReviewIssue.builder().severity(IssueSeverity.HIGH)
                                    .message(longMessage.toString()).build())))
                    .build();
            String body = service.buildCommentBody(report,
                    ThresholdValidationResultDTO.builder().passed(true).build());
            assertThat(body.length()).isLessThanOrEqualTo(65000);
            assertThat(body).contains("truncated");
        }
    }

    @Nested
    @DisplayName("collectTopIssues")
    class CollectTopIssues {

        @Test
        @DisplayName("Should collect CRITICAL and HIGH issues")
        void shouldCollectCriticalAndHigh() {
            ReviewReportDTO report = buildReport(true);
            List<ReviewIssue> topIssues = service.collectTopIssues(report);
            assertThat(topIssues).hasSize(1);
            assertThat(topIssues.get(0).getSeverity()).isEqualTo(IssueSeverity.HIGH);
        }

        @Test
        @DisplayName("Should limit to 5 issues")
        void shouldLimitToFive() {
            List<ReviewIssue> manyIssues = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                manyIssues.add(ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH)
                        .message("Issue " + i)
                        .build());
            }
            ReviewReportDTO report = ReviewReportDTO.builder()
                    .issuesBySeverity(Map.of("HIGH", manyIssues))
                    .build();
            List<ReviewIssue> topIssues = service.collectTopIssues(report);
            assertThat(topIssues).hasSize(5);
        }

        @Test
        @DisplayName("Should return empty list when no high severity issues")
        void shouldReturnEmptyWhenNoHighSeverity() {
            ReviewReportDTO report = ReviewReportDTO.builder()
                    .issuesBySeverity(Map.of("LOW", List.of(
                            ReviewIssue.builder().severity(IssueSeverity.LOW).message("Minor").build())))
                    .build();
            List<ReviewIssue> topIssues = service.collectTopIssues(report);
            assertThat(topIssues).isEmpty();
        }

        @Test
        @DisplayName("Should handle null issuesBySeverity")
        void shouldHandleNullMap() {
            ReviewReportDTO report = ReviewReportDTO.builder().issuesBySeverity(null).build();
            List<ReviewIssue> topIssues = service.collectTopIssues(report);
            assertThat(topIssues).isEmpty();
        }
    }
}
