package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
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

import java.util.Map;

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

        String projectName = report.getProjectName();
        String title = "[AI Code Review] 审查超阈值警告 — " + projectName;

        // 5. Send to each enabled platform independently
        if (Boolean.TRUE.equals(config.getDingtalkEnabled())) {
            try {
                String content = buildDingTalkContent(report, thresholdResult);
                dingTalkWebhookService.sendNotification(
                        config.getDingtalkWebhookUrl(), config.getDingtalkSecret(), title, content);
            } catch (Exception e) {
                log.warn("Failed to send DingTalk notification for task {}: {}", taskId, e.getMessage());
            }
        }

        if (Boolean.TRUE.equals(config.getSlackEnabled())) {
            try {
                String content = buildSlackContent(report, thresholdResult);
                slackWebhookService.sendNotification(config.getSlackWebhookUrl(), content);
            } catch (Exception e) {
                log.warn("Failed to send Slack notification for task {}: {}", taskId, e.getMessage());
            }
        }

        if (Boolean.TRUE.equals(config.getLarkEnabled())) {
            try {
                String content = buildLarkContent(report, thresholdResult);
                larkWebhookService.sendNotification(config.getLarkWebhookUrl(), title, content);
            } catch (Exception e) {
                log.warn("Failed to send Lark notification for task {}: {}", taskId, e.getMessage());
            }
        }

        log.info("IM threshold violation notifications processed for task {}", taskId);
    }

    String buildDingTalkContent(ReviewReportDTO report, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append("\u26A0\uFE0F").append(" AI Code Review 超阈值警告\n\n");
        sb.append("**项目**: ").append(report.getProjectName()).append("\n");
        sb.append("**分支**: ").append(report.getBranch()).append("\n");
        sb.append("**提交者**: ").append(report.getAuthor()).append("\n\n");
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
        appendIssueSummary(sb, report.getSummary());

        // Action
        if (thresholdResult.getAction() != null) {
            sb.append("**建议动作**: ").append(thresholdResult.getAction()).append("\n");
        }

        return sb.toString();
    }

    String buildSlackContent(ReviewReportDTO report, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(":warning: *AI Code Review Threshold Violation*\n\n");
        sb.append("*Project:* ").append(report.getProjectName()).append("\n");
        sb.append("*Branch:* ").append(report.getBranch()).append("\n");
        sb.append("*Author:* ").append(report.getAuthor()).append("\n\n");

        // Violations
        sb.append("*Violations:*\n");
        if (thresholdResult.getViolations() != null) {
            for (ThresholdViolationDTO v : thresholdResult.getViolations()) {
                sb.append("\u2022 `").append(v.getRule()).append("` \u2014 actual: ")
                        .append(v.getActual()).append(", limit: ").append(v.getThreshold()).append("\n");
            }
        }
        sb.append("\n");

        // Issue summary (inline)
        ReviewStatisticsDTO summary = report.getSummary();
        if (summary != null && summary.getBySeverity() != null) {
            sb.append("*Issue Summary:* ");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : summary.getBySeverity().entrySet()) {
                if (entry.getValue() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey()).append(": ").append(entry.getValue());
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

    String buildLarkContent(ReviewReportDTO report, ThresholdValidationResultDTO thresholdResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("**项目**: ").append(report.getProjectName()).append("\n");
        sb.append("**分支**: ").append(report.getBranch()).append("\n");
        sb.append("**提交者**: ").append(report.getAuthor()).append("\n\n");

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
        appendIssueSummary(sb, report.getSummary());

        // Action
        if (thresholdResult.getAction() != null) {
            sb.append("**建议动作**: ").append(thresholdResult.getAction()).append("\n");
        }

        return sb.toString();
    }

    private void appendIssueSummary(StringBuilder sb, ReviewStatisticsDTO summary) {
        if (summary == null || summary.getBySeverity() == null) {
            return;
        }
        sb.append("#### 问题统计\n\n");
        sb.append("| 严重性 | 数量 |\n");
        sb.append("|--------|------|\n");
        for (Map.Entry<String, Integer> entry : summary.getBySeverity().entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
            }
        }
        sb.append("\n");
    }
}
