package com.aicodereview.common.dto.result;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Structured review report with issues grouped by file, severity, and category.
 * <p>
 * Generated from a persisted ReviewResultDTO via ReviewReportService.
 * Supports rendering to Markdown and HTML formats.
 * </p>
 *
 * @since 5.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReportDTO {

    private Long taskId;
    private String projectName;
    private String branch;
    private String author;
    private Instant reviewedAt;
    private Boolean success;
    private String errorMessage;
    private ReviewStatisticsDTO summary;
    private Map<String, List<ReviewIssue>> issuesByFile;
    private Map<String, List<ReviewIssue>> issuesBySeverity;
    private Map<String, List<ReviewIssue>> issuesByCategory;
    private ReviewMetadata metadata;
}
