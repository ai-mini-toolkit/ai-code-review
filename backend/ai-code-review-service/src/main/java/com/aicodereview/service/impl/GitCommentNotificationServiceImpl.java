package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.integration.git.AWSCodeCommitCommentService;
import com.aicodereview.integration.git.GitHubPRCommentService;
import com.aicodereview.integration.git.GitLabMRCommentService;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.GitCommentNotificationService;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of GitCommentNotificationService.
 * <p>
 * Builds a condensed Markdown comment body and posts it to the PR/MR
 * via the appropriate platform service (GitHub, GitLab, or AWS CodeCommit).
 * </p>
 *
 * @since 7.2.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class GitCommentNotificationServiceImpl implements GitCommentNotificationService {

    private static final int MAX_COMMENT_LENGTH = 65000;
    private static final int TOP_ISSUES_LIMIT = 5;

    private final NotificationConfigRepository notificationConfigRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewReportService reviewReportService;
    private final ReviewResultService reviewResultService;
    private final GitHubPRCommentService gitHubPRCommentService;
    private final GitLabMRCommentService gitLabMRCommentService;
    private final AWSCodeCommitCommentService awsCodeCommitCommentService;

    public GitCommentNotificationServiceImpl(
            NotificationConfigRepository notificationConfigRepository,
            ReviewTaskRepository reviewTaskRepository,
            ReviewReportService reviewReportService,
            @Lazy ReviewResultService reviewResultService,
            GitHubPRCommentService gitHubPRCommentService,
            GitLabMRCommentService gitLabMRCommentService,
            AWSCodeCommitCommentService awsCodeCommitCommentService) {
        this.notificationConfigRepository = notificationConfigRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewReportService = reviewReportService;
        this.reviewResultService = reviewResultService;
        this.gitHubPRCommentService = gitHubPRCommentService;
        this.gitLabMRCommentService = gitLabMRCommentService;
        this.awsCodeCommitCommentService = awsCodeCommitCommentService;
    }

    @Override
    public void postReviewComment(Long taskId) {
        try {
            // 1. Look up task
            ReviewTask task = reviewTaskRepository.findById(taskId).orElse(null);
            if (task == null || task.getProject() == null) {
                log.debug("Task {} not found or has no project, skipping comment notification", taskId);
                return;
            }

            // 2. Check PR number exists (only PR/MR tasks)
            if (task.getPrNumber() == null) {
                log.debug("Task {} has no PR number (PUSH task), skipping comment notification", taskId);
                return;
            }

            Long projectId = task.getProject().getId();

            // 3. Check notification config
            NotificationConfigEntity config = notificationConfigRepository.findByProjectId(projectId)
                    .orElse(null);
            if (config == null || !Boolean.TRUE.equals(config.getCommentEnabled())) {
                log.debug("Comment notification not enabled for project {}, skipping", projectId);
                return;
            }

            // 4. Generate report
            ReviewReportDTO report = reviewReportService.generateReport(taskId);

            // 5. Get threshold result
            ReviewResultDTO result = reviewResultService.getResultByTaskId(taskId);
            ThresholdValidationResultDTO thresholdResult = result.getThresholdResult();

            // 6. Build comment body
            String commentBody = buildCommentBody(report, thresholdResult);

            // 7. Dispatch to platform
            String platform = task.getProject().getGitPlatform();
            dispatchComment(platform, task, commentBody);

            log.info("Comment notification sent for task {} on platform {}", taskId, platform);

        } catch (Exception e) {
            log.warn("Failed to post comment notification for task {}: {}", taskId, e.getMessage());
        }
    }

    private void dispatchComment(String platform, ReviewTask task, String commentBody) {
        if ("GitHub".equalsIgnoreCase(platform)) {
            gitHubPRCommentService.postComment(
                    task.getRepoUrl(), task.getPrNumber(), commentBody);
        } else if ("GitLab".equalsIgnoreCase(platform)) {
            gitLabMRCommentService.postComment(
                    task.getRepoUrl(), task.getPrNumber(), commentBody);
        } else if ("AWS_CODECOMMIT".equalsIgnoreCase(platform)) {
            awsCodeCommitCommentService.postComment(
                    task.getRepoUrl(), String.valueOf(task.getPrNumber()), commentBody);
        } else {
            log.warn("Unsupported platform for comment notification: {}", platform);
        }
    }

    String buildCommentBody(ReviewReportDTO report, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("## \uD83E\uDD16 AI Code Review\n\n");

        // Status
        boolean passed = Boolean.TRUE.equals(report.getSuccess());
        sb.append("**Status:** ").append(passed ? "Passed" : "Failed").append("\n");
        sb.append("**Branch:** ").append(report.getBranch() != null ? report.getBranch() : "N/A").append("\n");
        sb.append("**Author:** ").append(report.getAuthor() != null ? report.getAuthor() : "N/A").append("\n\n");

        // Summary table
        ReviewStatisticsDTO summary = report.getSummary();
        if (summary != null) {
            sb.append("### Summary\n\n");
            sb.append("| Severity | Count |\n");
            sb.append("|----------|-------|\n");
            Map<String, Integer> bySeverity = summary.getBySeverity();
            if (bySeverity != null) {
                bySeverity.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> {
                            if (e.getValue() > 0) {
                                sb.append("| ").append(e.getKey())
                                        .append(" | ").append(e.getValue()).append(" |\n");
                            }
                        });
            }
            sb.append("\n**Total Issues:** ").append(summary.getTotal()).append("\n\n");
        }

        // Threshold result
        if (thresholdResult != null) {
            sb.append("### Threshold Validation\n\n");
            if (thresholdResult.isPassed()) {
                sb.append("Threshold: **Passed**\n\n");
            } else {
                int violationCount = thresholdResult.getViolations() != null
                        ? thresholdResult.getViolations().size() : 0;
                sb.append("Threshold: **FAILED** \u2014 ").append(violationCount).append(" violation(s)\n\n");
                if (thresholdResult.getViolations() != null) {
                    for (ThresholdViolationDTO v : thresholdResult.getViolations()) {
                        sb.append("- **").append(v.getRule()).append("** \u2014 actual: ")
                                .append(v.getActual()).append(", limit: ")
                                .append(v.getThreshold()).append("\n");
                    }
                    sb.append("\n");
                }
                if (thresholdResult.getAction() != null) {
                    sb.append("**Action:** ").append(thresholdResult.getAction()).append("\n\n");
                }
            }
        }

        // Top issues (CRITICAL + HIGH, up to 5)
        List<ReviewIssue> topIssues = collectTopIssues(report);
        if (!topIssues.isEmpty()) {
            sb.append("### Top Issues\n\n");
            for (ReviewIssue issue : topIssues) {
                sb.append("- **[").append(issue.getSeverity()).append("]** ");
                if (issue.getFilePath() != null) {
                    sb.append("`").append(issue.getFilePath());
                    if (issue.getLine() != null) {
                        sb.append(":").append(issue.getLine());
                    }
                    sb.append("` \u2014 ");
                }
                sb.append(issue.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        // Footer
        sb.append("---\n*Generated by AI Code Review*\n");

        String body = sb.toString();
        if (body.length() > MAX_COMMENT_LENGTH) {
            body = body.substring(0, MAX_COMMENT_LENGTH - 20) + "\n\n...(truncated)\n";
        }
        return body;
    }

    List<ReviewIssue> collectTopIssues(ReviewReportDTO report) {
        List<ReviewIssue> highSeverity = new ArrayList<>();

        Map<String, List<ReviewIssue>> issuesBySeverity = report.getIssuesBySeverity();
        if (issuesBySeverity != null) {
            List<ReviewIssue> criticalIssues = issuesBySeverity.get("CRITICAL");
            if (criticalIssues != null) {
                highSeverity.addAll(criticalIssues);
            }
            List<ReviewIssue> highIssues = issuesBySeverity.get("HIGH");
            if (highIssues != null) {
                highSeverity.addAll(highIssues);
            }
        }

        // Sort CRITICAL first, then HIGH (descending by score)
        highSeverity.sort(Comparator.comparing(
                (ReviewIssue issue) -> issue.getSeverity() != null ? issue.getSeverity().getScore() : 0,
                Comparator.reverseOrder()));

        if (highSeverity.size() > TOP_ISSUES_LIMIT) {
            return List.copyOf(highSeverity.subList(0, TOP_ISSUES_LIMIT));
        }
        return List.copyOf(highSeverity);
    }
}
