package com.aicodereview.service;

import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.exception.ResourceNotFoundException;

/**
 * Service for generating structured review reports and rendering them to different formats.
 * <p>
 * Consumes persisted review results (via ReviewResultService) and produces
 * grouped, sorted reports suitable for API responses, notifications, and emails.
 * </p>
 *
 * @since 5.2.0
 */
public interface ReviewReportService {

    /**
     * Generates a structured report from a persisted review result.
     * <p>
     * Issues are grouped by file, severity, and category. Within each group,
     * issues are sorted by severity descending (CRITICAL first).
     * </p>
     *
     * @param taskId the review task ID
     * @return the structured report DTO
     * @throws ResourceNotFoundException if no result exists for the task
     */
    ReviewReportDTO generateReport(Long taskId);

    /**
     * Renders a report to Markdown format.
     *
     * @param report the structured report DTO
     * @return Markdown string
     */
    String renderMarkdown(ReviewReportDTO report);

    /**
     * Renders a report to self-contained HTML format (inline CSS, no external dependencies).
     *
     * @param report the structured report DTO
     * @return HTML string
     */
    String renderHtml(ReviewReportDTO report);
}
