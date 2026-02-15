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
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ReviewReportService for generating structured reports
 * and rendering them to Markdown/HTML formats.
 *
 * @since 5.2.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReviewReportServiceImpl implements ReviewReportService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final ReviewResultService reviewResultService;
    private final ReviewTaskRepository reviewTaskRepository;

    public ReviewReportServiceImpl(ReviewResultService reviewResultService,
                                    ReviewTaskRepository reviewTaskRepository) {
        this.reviewResultService = reviewResultService;
        this.reviewTaskRepository = reviewTaskRepository;
    }

    @Override
    public ReviewReportDTO generateReport(Long taskId) {
        log.info("Generating review report for task: {}", taskId);

        // 1. Fetch persisted result
        ReviewResultDTO result = reviewResultService.getResultByTaskId(taskId);

        // 2. Fetch task metadata
        ReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", taskId));

        // 3. Group and sort issues
        List<ReviewIssue> issues = result.getIssues() != null ? result.getIssues() : List.of();

        Map<String, List<ReviewIssue>> issuesByFile = groupByFile(issues);
        Map<String, List<ReviewIssue>> issuesBySeverity = groupBySeverity(issues);
        Map<String, List<ReviewIssue>> issuesByCategory = groupByCategory(issues);

        // 4. Build report
        ReviewReportDTO report = ReviewReportDTO.builder()
                .taskId(taskId)
                .projectName(task.getProject().getName())
                .branch(task.getBranch())
                .author(task.getAuthor())
                .reviewedAt(result.getCreatedAt())
                .success(result.getSuccess())
                .errorMessage(result.getErrorMessage())
                .summary(result.getStatistics())
                .issuesByFile(issuesByFile)
                .issuesBySeverity(issuesBySeverity)
                .issuesByCategory(issuesByCategory)
                .metadata(result.getMetadata())
                .build();

        log.info("Report generated for task: {} with {} issues", taskId, issues.size());
        return report;
    }

    @Override
    public String renderMarkdown(ReviewReportDTO report) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Code Review Report\n\n");
        sb.append("**Project:** ").append(report.getProjectName())
                .append(" | **Branch:** ").append(report.getBranch())
                .append(" | **Author:** ").append(report.getAuthor()).append("\n");
        sb.append("**Reviewed:** ").append(formatInstant(report.getReviewedAt()))
                .append(" | **Status:** ").append(Boolean.TRUE.equals(report.getSuccess()) ? "Passed" : "Failed").append("\n\n");

        if (!Boolean.TRUE.equals(report.getSuccess())) {
            sb.append("## Error\n\n");
            sb.append(report.getErrorMessage()).append("\n\n");
            return sb.toString();
        }

        // Summary table
        renderMarkdownSummary(sb, report.getSummary());

        // Issues by file
        renderMarkdownIssuesByFile(sb, report.getIssuesByFile());

        // Metadata
        renderMarkdownMetadata(sb, report.getMetadata());

        return sb.toString();
    }

    @Override
    public String renderHtml(ReviewReportDTO report) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<title>Code Review Report</title>\n</head>\n<body style=\"font-family:Arial,sans-serif;max-width:800px;margin:0 auto;padding:20px;\">\n");

        // Header
        sb.append("<h1 style=\"border-bottom:2px solid #333;padding-bottom:10px;\">Code Review Report</h1>\n");
        sb.append("<p><strong>Project:</strong> ").append(escapeHtml(report.getProjectName()))
                .append(" | <strong>Branch:</strong> ").append(escapeHtml(report.getBranch()))
                .append(" | <strong>Author:</strong> ").append(escapeHtml(report.getAuthor())).append("</p>\n");
        sb.append("<p><strong>Reviewed:</strong> ").append(formatInstant(report.getReviewedAt()))
                .append(" | <strong>Status:</strong> <span style=\"color:")
                .append(Boolean.TRUE.equals(report.getSuccess()) ? "#28a745" : "#dc3545").append(";font-weight:bold;\">")
                .append(Boolean.TRUE.equals(report.getSuccess()) ? "Passed" : "Failed").append("</span></p>\n");

        if (!Boolean.TRUE.equals(report.getSuccess())) {
            sb.append("<div style=\"background:#f8d7da;border:1px solid #f5c6cb;padding:12px;border-radius:4px;margin:16px 0;\">\n");
            sb.append("<strong>Error:</strong> ").append(escapeHtml(report.getErrorMessage())).append("\n</div>\n");
            sb.append("</body>\n</html>");
            return sb.toString();
        }

        // Summary table
        renderHtmlSummary(sb, report.getSummary());

        // Issues by file
        renderHtmlIssuesByFile(sb, report.getIssuesByFile());

        // Metadata
        renderHtmlMetadata(sb, report.getMetadata());

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ---- Grouping helpers ----

    private Map<String, List<ReviewIssue>> groupByFile(List<ReviewIssue> issues) {
        Map<String, List<ReviewIssue>> grouped = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getFilePath() != null ? i.getFilePath() : "(unknown)",
                        LinkedHashMap::new,
                        Collectors.toList()));
        // Sort each group by severity descending
        grouped.values().forEach(this::sortBySeverityDesc);
        return grouped;
    }

    private Map<String, List<ReviewIssue>> groupBySeverity(List<ReviewIssue> issues) {
        Map<String, List<ReviewIssue>> grouped = new LinkedHashMap<>();
        for (IssueSeverity severity : IssueSeverity.values()) {
            grouped.put(severity.name(), new ArrayList<>());
        }
        for (ReviewIssue issue : issues) {
            if (issue.getSeverity() != null) {
                grouped.get(issue.getSeverity().name()).add(issue);
            }
        }
        return grouped;
    }

    private Map<String, List<ReviewIssue>> groupByCategory(List<ReviewIssue> issues) {
        Map<String, List<ReviewIssue>> grouped = new LinkedHashMap<>();
        for (IssueCategory category : IssueCategory.values()) {
            grouped.put(category.name(), new ArrayList<>());
        }
        for (ReviewIssue issue : issues) {
            if (issue.getCategory() != null) {
                grouped.get(issue.getCategory().name()).add(issue);
            }
        }
        // Sort each group by severity descending
        grouped.values().forEach(this::sortBySeverityDesc);
        return grouped;
    }

    private void sortBySeverityDesc(List<ReviewIssue> issues) {
        issues.sort((a, b) -> {
            int scoreA = a.getSeverity() != null ? a.getSeverity().getScore() : 0;
            int scoreB = b.getSeverity() != null ? b.getSeverity().getScore() : 0;
            return Integer.compare(scoreB, scoreA);
        });
    }

    // ---- Markdown rendering helpers ----

    private void renderMarkdownSummary(StringBuilder sb, ReviewStatisticsDTO summary) {
        sb.append("## Summary\n\n");
        sb.append("| Severity | Count |\n");
        sb.append("|----------|-------|\n");
        if (summary != null && summary.getBySeverity() != null) {
            for (IssueSeverity severity : IssueSeverity.values()) {
                int count = summary.getBySeverity().getOrDefault(severity.name(), 0);
                sb.append("| ").append(severity.name()).append(" | ").append(count).append(" |\n");
            }
        }
        int total = summary != null ? summary.getTotal() : 0;
        sb.append("\n**Total Issues:** ").append(total).append("\n\n");
    }

    private void renderMarkdownIssuesByFile(StringBuilder sb, Map<String, List<ReviewIssue>> issuesByFile) {
        if (issuesByFile == null || issuesByFile.isEmpty()) {
            return;
        }
        sb.append("## Issues by File\n\n");
        for (Map.Entry<String, List<ReviewIssue>> entry : issuesByFile.entrySet()) {
            List<ReviewIssue> fileIssues = entry.getValue();
            sb.append("### ").append(entry.getKey())
                    .append(" (").append(fileIssues.size()).append(fileIssues.size() == 1 ? " issue" : " issues").append(")\n\n");
            sb.append("| # | Severity | Category | Line | Message | Suggestion |\n");
            sb.append("|---|----------|----------|------|---------|------------|\n");
            int num = 1;
            for (ReviewIssue issue : fileIssues) {
                sb.append("| ").append(num++)
                        .append(" | ").append(issue.getSeverity() != null ? issue.getSeverity().name() : "-")
                        .append(" | ").append(issue.getCategory() != null ? issue.getCategory().name() : "-")
                        .append(" | ").append(issue.getLine() != null ? issue.getLine() : "-")
                        .append(" | ").append(escapeMarkdownCell(issue.getMessage() != null ? issue.getMessage() : "-"))
                        .append(" | ").append(escapeMarkdownCell(issue.getSuggestion() != null ? issue.getSuggestion() : "-"))
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    private void renderMarkdownMetadata(StringBuilder sb, ReviewMetadata metadata) {
        if (metadata == null) {
            return;
        }
        sb.append("## Review Metadata\n\n");
        sb.append("- **Provider:** ").append(metadata.getProviderId()).append("\n");
        sb.append("- **Model:** ").append(metadata.getModel()).append("\n");
        sb.append("- **Tokens:** ").append(metadata.getPromptTokens())
                .append(" prompt + ").append(metadata.getCompletionTokens()).append(" completion\n");
        sb.append("- **Duration:** ").append(metadata.getDurationMs()).append("ms\n");
    }

    // ---- HTML rendering helpers ----

    private void renderHtmlSummary(StringBuilder sb, ReviewStatisticsDTO summary) {
        sb.append("<h2>Summary</h2>\n");
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin-bottom:20px;\">\n");
        sb.append("<tr style=\"background:#f8f9fa;\"><th style=\"border:1px solid #dee2e6;padding:8px;text-align:left;\">Severity</th>");
        sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;text-align:right;\">Count</th></tr>\n");
        if (summary != null && summary.getBySeverity() != null) {
            for (IssueSeverity severity : IssueSeverity.values()) {
                int count = summary.getBySeverity().getOrDefault(severity.name(), 0);
                sb.append("<tr><td style=\"border:1px solid #dee2e6;padding:8px;color:")
                        .append(severityColor(severity)).append(";font-weight:bold;\">")
                        .append(severity.name()).append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;text-align:right;\">")
                        .append(count).append("</td></tr>\n");
            }
        }
        sb.append("</table>\n");
        int total = summary != null ? summary.getTotal() : 0;
        sb.append("<p><strong>Total Issues:</strong> ").append(total).append("</p>\n");
    }

    private void renderHtmlIssuesByFile(StringBuilder sb, Map<String, List<ReviewIssue>> issuesByFile) {
        if (issuesByFile == null || issuesByFile.isEmpty()) {
            return;
        }
        sb.append("<h2>Issues by File</h2>\n");
        for (Map.Entry<String, List<ReviewIssue>> entry : issuesByFile.entrySet()) {
            List<ReviewIssue> fileIssues = entry.getValue();
            sb.append("<h3>").append(escapeHtml(entry.getKey()))
                    .append(" (").append(fileIssues.size()).append(fileIssues.size() == 1 ? " issue" : " issues").append(")</h3>\n");
            sb.append("<table style=\"border-collapse:collapse;width:100%;margin-bottom:16px;\">\n");
            sb.append("<tr style=\"background:#f8f9fa;\">");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">#</th>");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">Severity</th>");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">Category</th>");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">Line</th>");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">Message</th>");
            sb.append("<th style=\"border:1px solid #dee2e6;padding:8px;\">Suggestion</th></tr>\n");
            int num = 1;
            for (ReviewIssue issue : fileIssues) {
                String color = issue.getSeverity() != null ? severityColor(issue.getSeverity()) : "#333";
                sb.append("<tr><td style=\"border:1px solid #dee2e6;padding:8px;\">").append(num++).append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;color:").append(color).append(";font-weight:bold;\">")
                        .append(issue.getSeverity() != null ? issue.getSeverity().name() : "-").append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;\">")
                        .append(issue.getCategory() != null ? issue.getCategory().name() : "-").append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;\">")
                        .append(issue.getLine() != null ? issue.getLine() : "-").append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;\">")
                        .append(escapeHtml(issue.getMessage() != null ? issue.getMessage() : "-")).append("</td>");
                sb.append("<td style=\"border:1px solid #dee2e6;padding:8px;\">")
                        .append(escapeHtml(issue.getSuggestion() != null ? issue.getSuggestion() : "-")).append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }
    }

    private void renderHtmlMetadata(StringBuilder sb, ReviewMetadata metadata) {
        if (metadata == null) {
            return;
        }
        sb.append("<h2>Review Metadata</h2>\n");
        sb.append("<ul>\n");
        sb.append("<li><strong>Provider:</strong> ").append(escapeHtml(metadata.getProviderId())).append("</li>\n");
        sb.append("<li><strong>Model:</strong> ").append(escapeHtml(metadata.getModel())).append("</li>\n");
        sb.append("<li><strong>Tokens:</strong> ").append(metadata.getPromptTokens())
                .append(" prompt + ").append(metadata.getCompletionTokens()).append(" completion</li>\n");
        sb.append("<li><strong>Duration:</strong> ").append(metadata.getDurationMs()).append("ms</li>\n");
        sb.append("</ul>\n");
    }

    // ---- Utility helpers ----

    private String severityColor(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#dc3545";
            case HIGH -> "#fd7e14";
            case MEDIUM -> "#ffc107";
            case LOW -> "#17a2b8";
            case INFO -> "#6c757d";
        };
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATETIME_FORMATTER.format(instant);
    }

    private String escapeMarkdownCell(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|");
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
