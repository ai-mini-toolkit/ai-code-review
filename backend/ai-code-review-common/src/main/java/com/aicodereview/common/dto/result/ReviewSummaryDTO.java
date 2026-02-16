package com.aicodereview.common.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lightweight DTO for review result listing (without full issues array).
 * <p>
 * Used by paginated list endpoints to avoid loading heavy JSONB data.
 * </p>
 *
 * @since 5.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDTO {

    private Long resultId;
    private Long taskId;
    private String projectName;
    private String branch;
    private String author;
    private Boolean success;
    private String errorMessage;
    private int totalIssues;
    private Instant createdAt;
}
