package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.integration.im.DingTalkWebhookService;
import com.aicodereview.integration.im.LarkWebhookService;
import com.aicodereview.integration.im.SlackWebhookService;
import com.aicodereview.repository.NotificationConfigRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.NotificationConfigEntity;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.IMNotificationService;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Implementation of IMNotificationService for sending threshold violation alerts
 * to DingTalk, Slack, and Lark (Feishu).
 *
 * @since 7.3.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class IMNotificationServiceImpl implements IMNotificationService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final NotificationConfigRepository notificationConfigRepository;
    private final ReviewReportService reviewReportService;
    private final ReviewResultService reviewResultService;
    private final DingTalkWebhookService dingTalkWebhookService;
    private final SlackWebhookService slackWebhookService;
    private final LarkWebhookService larkWebhookService;

    public IMNotificationServiceImpl(
            ReviewTaskRepository reviewTaskRepository,
            NotificationConfigRepository notificationConfigRepository,
            ReviewReportService reviewReportService,
            @Lazy ReviewResultService reviewResultService,
            DingTalkWebhookService dingTalkWebhookService,
            SlackWebhookService slackWebhookService,
            LarkWebhookService larkWebhookService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.notificationConfigRepository = notificationConfigRepository;
        this.reviewReportService = reviewReportService;
        this.reviewResultService = reviewResultService;
        this.dingTalkWebhookService = dingTalkWebhookService;
        this.slackWebhookService = slackWebhookService;
        this.larkWebhookService = larkWebhookService;
    }

    @Override
    public void sendThresholdViolationNotifications(Long taskId) {
        log.debug("Preparing IM threshold violation notifications for task: {}", taskId);

        // 1. Look up task
        ReviewTask task = reviewTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getProject() == null) {
            log.debug("Task {} not found or has no project, skipping IM notification", taskId);
            return;
        }
        Long projectId = task.getProject().getId();

        // 2. Look up notification config
        NotificationConfigEntity config = notificationConfigRepository.findByProjectId(projectId)
                .orElse(null);
        if (config == null) {
            log.debug("No notification config for project {}, skipping IM notification", projectId);
            return;
        }

        // 3. Check if any IM platform is enabled
        boolean anyEnabled = Boolean.TRUE.equals(config.getDingtalkEnabled())
                || Boolean.TRUE.equals(config.getSlackEnabled())
                || Boolean.TRUE.equals(config.getLarkEnabled());
        if (!anyEnabled) {
            log.debug("No IM platforms enabled for project {}, skipping", projectId);
            return;
        }

        // 4. Generate report and get result
        ReviewReportDTO report = reviewReportService.generateReport(taskId);
        ReviewResultDTO result = reviewResultService.getResultByTaskId(taskId);
        ThresholdValidationResultDTO thresholdResult = result.getThresholdResult();

        // H1 fix: guard against null thresholdResult (data integrity or race condition)
        if (thresholdResult == null) {
            log.warn("ThresholdValidationResult is null for task {}, skipping IM notification", taskId);
            return;
        }

        // H2 fix: resolve report fields defensively to avoid "null" in notification text
        String projectName = report.getProjectName() != null ? report.getProjectName() : "Unknown Project";
        String branch = report.getBranch() != null ? report.getBranch() : "unknown";
        String author = report.getAuthor() != null ? report.getAuthor() : "unknown";

        String title = "[AI Code Review] 审查超阈值警告 — " + projectName;

        // 5. Send to each enabled platform independently
        if (Boolean.TRUE.equals(config.getDingtalkEnabled())) {
            try {
                String content = buildDingTalkContent(projectName, branch, author, report.getSummary(), thresholdResult);
                boolean sent = dingTalkWebhookService.sendNotification(
                        config.getDingtalkWebhookUrl(), config.getDingtalkSecret(), title, content);
                if (!sent) {
                    log.warn("DingTalk notification was not delivered for task {}", taskId);
                }
            } catch (Exception e) {
                log.warn("Failed to send DingTalk notification for task {}: {}", taskId, e.getMessage());
            }
        }

        if (Boolean.TRUE.equals(config.getSlackEnabled())) {
            try {
                String content = buildSlackContent(projectName, branch, author, report.getSummary(), thresholdResult);
                boolean sent = slackWebhookService.sendNotification(config.getSlackWebhookUrl(), content);
                if (!sent) {
                    log.warn("Slack notification was not delivered for task {}", taskId);
                }
            } catch (Exception e) {
                log.warn("Failed to send Slack notification for task {}: {}", taskId, e.getMessage());
            }
        }

        if (Boolean.TRUE.equals(config.getLarkEnabled())) {
            try {
                String content = buildLarkContent(projectName, branch, author, report.getSummary(), thresholdResult);
                boolean sent = larkWebhookService.sendNotification(config.getLarkWebhookUrl(), title, content);
                if (!sent) {
                    log.warn("Lark notification was not delivered for task {}", taskId);
                }
            } catch (Exception e) {
                log.warn("Failed to send Lark notification for task {}: {}", taskId, e.getMessage());
            }
        }

        log.info("IM threshold violation notifications processed for task {}", taskId);
    }

    String buildDingTalkContent(String projectName, String branch, String author,
                                ReviewStatisticsDTO summary, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append("\u26A0\uFE0F").append(" AI Code Review 超阈值警告\n\n");
        sb.append("**项目**: ").append(projectName).append("\n");
        sb.append("**分支**: ").append(branch).append("\n");
        sb.append("**提交者**: ").append(author).append("\n\n");
        sb.append("---\n\n");

        // Violations
        sb.append("#### 阈值违规\n\n");
        if (thresholdResult.getViolations() != null) {
            for (ThresholdViolationDTO v : thresholdResult.getViolations()) {
                sb.append("- **").append(v.getRule()).append("** — 实际: ")
                        .append(v.getActual()).append(", 限制: ").append(v.getThreshold()).append("\n");
            }
        }
        sb.append("\n");

        // Issue summary
        appendIssueSummary(sb, summary);

        // Action
        if (thresholdResult.getAction() != null) {
            sb.append("**建议动作**: ").append(thresholdResult.getAction()).append("\n");
        }

        return sb.toString();
    }

    String buildSlackContent(String projectName, String branch, String author,
                             ReviewStatisticsDTO summary, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(":warning: *AI Code Review Threshold Violation*\n\n");
        sb.append("*Project:* ").append(projectName).append("\n");
        sb.append("*Branch:* ").append(branch).append("\n");
        sb.append("*Author:* ").append(author).append("\n\n");

        // Violations
        sb.append("*Violations:*\n");
        if (thresholdResult.getViolations() != null) {
            for (ThresholdViolationDTO v : thresholdResult.getViolations()) {
                sb.append("\u2022 `").append(v.getRule()).append("` \u2014 actual: ")
                        .append(v.getActual()).append(", limit: ").append(v.getThreshold()).append("\n");
            }
        }
        sb.append("\n");

        // M2 fix: issue summary inline — sorted by IssueSeverity score (descending)
        if (summary != null && summary.getBySeverity() != null) {
            sb.append("*Issue Summary:* ");
            boolean first = true;
            for (IssueSeverity sev : IssueSeverity.values()) {  // CRITICAL→HIGH→MEDIUM→LOW→INFO
                Integer count = summary.getBySeverity().get(sev.name());
                if (count != null && count > 0) {
                    if (!first) sb.append(", ");
                    sb.append(sev.name()).append(": ").append(count);
                    first = false;
                }
            }
            sb.append("\n");
        }

        // Action
        if (thresholdResult.getAction() != null) {
            sb.append("*Action:* ").append(thresholdResult.getAction()).append("\n");
        }

        return sb.toString();
    }

    String buildLarkContent(String projectName, String branch, String author,
                            ReviewStatisticsDTO summary, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("**项目**: ").append(projectName).append("\n");
        sb.append("**分支**: ").append(branch).append("\n");
        sb.append("**提交者**: ").append(author).append("\n\n");

        // Violations
        sb.append("**阈值违规**\n");
        if (thresholdResult.getViolations() != null) {
            for (ThresholdViolationDTO v : thresholdResult.getViolations()) {
                sb.append("- **").append(v.getRule()).append("** — 实际: ")
                        .append(v.getActual()).append(", 限制: ").append(v.getThreshold()).append("\n");
            }
        }
        sb.append("\n");

        // Issue summary
        appendIssueSummary(sb, summary);

        // Action
        if (thresholdResult.getAction() != null) {
            sb.append("**建议动作**: ").append(thresholdResult.getAction()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Appends a severity-sorted issue summary table.
     * M2 fix: iterates IssueSeverity enum in declared order (CRITICAL→HIGH→MEDIUM→LOW→INFO)
     * to guarantee deterministic output regardless of Map iteration order.
     */
    private void appendIssueSummary(StringBuilder sb, ReviewStatisticsDTO summary) {
        if (summary == null || summary.getBySeverity() == null) {
            return;
        }
        sb.append("#### 问题统计\n\n");
        sb.append("| 严重性 | 数量 |\n");
        sb.append("|--------|------|\n");
        for (IssueSeverity sev : IssueSeverity.values()) {  // CRITICAL→HIGH→MEDIUM→LOW→INFO
            Integer count = summary.getBySeverity().get(sev.name());
            if (count != null && count > 0) {
                sb.append("| ").append(sev.name()).append(" | ").append(count).append(" |\n");
            }
        }
        sb.append("\n");
    }
}
