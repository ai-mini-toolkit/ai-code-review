package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.result.ReviewReportDTO;
import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewSummaryDTO;
import com.aicodereview.service.ReviewReportService;
import com.aicodereview.service.ReviewResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for querying review results and generating reports.
 * <p>
 * Provides endpoints for retrieving individual results, multi-format reports,
 * and paginated listing with optional filtering.
 * </p>
 *
 * @since 5.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewResultController {

    private final ReviewResultService reviewResultService;
    private final ReviewReportService reviewReportService;

    /**
     * Retrieves a complete review result by task ID.
     *
     * @param taskId the review task ID
     * @return the full review result including issues, statistics, and metadata
     */
    @GetMapping("/{taskId}/result")
    public ResponseEntity<ApiResponse<ReviewResultDTO>> getResult(
            @PathVariable("taskId") Long taskId) {
        log.debug("GET /api/v1/reviews/{}/result - Getting review result", taskId);
        ReviewResultDTO result = reviewResultService.getResultByTaskId(taskId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Retrieves a structured review report in the specified format.
     * <p>
     * Supported formats:
     * <ul>
     *   <li><b>json</b> (default) — ApiResponse-wrapped ReviewReportDTO</li>
     *   <li><b>markdown</b> — plain text Markdown</li>
     *   <li><b>html</b> — self-contained HTML with inline styles</li>
     * </ul>
     * </p>
     *
     * @param taskId the review task ID
     * @param format the output format (json, markdown, html)
     * @return the report in the requested format
     */
    @GetMapping("/{taskId}/report")
    public ResponseEntity<?> getReport(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "format", defaultValue = "json") String format) {
        log.debug("GET /api/v1/reviews/{}/report?format={} - Getting review report", taskId, format);

        ReviewReportDTO report = reviewReportService.generateReport(taskId);

        switch (format.toLowerCase()) {
            case "json":
                return ResponseEntity.ok(ApiResponse.success(report));
            case "markdown":
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(reviewReportService.renderMarkdown(report));
            case "html":
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(reviewReportService.renderHtml(report));
            default:
                throw new IllegalArgumentException(
                        "Unsupported format: " + format + ". Supported: json, markdown, html");
        }
    }

    /**
     * Lists review results with pagination and optional filtering.
     * <p>
     * Supports filtering by project ID and/or success status.
     * Pagination parameters (page, size, sort) are resolved automatically by Spring Data.
     * </p>
     *
     * @param projectId optional project ID filter
     * @param success   optional success status filter
     * @param pageable  pagination parameters (page, size, sort)
     * @return paginated list of lightweight review summaries
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReviewSummaryDTO>>> listReviews(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "success", required = false) Boolean success,
            Pageable pageable) {
        log.debug("GET /api/v1/reviews - Listing reviews, projectId={}, success={}, page={}",
                projectId, success, pageable);
        Page<ReviewSummaryDTO> results = reviewResultService.listResults(projectId, success, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
