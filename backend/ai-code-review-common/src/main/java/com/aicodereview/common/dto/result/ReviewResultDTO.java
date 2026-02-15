package com.aicodereview.common.dto.result;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for review results.
 * <p>
 * Contains the deserialized issues list, computed statistics,
 * execution metadata, and success/error information.
 * </p>
 *
 * @since 5.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResultDTO {

    private Long id;
    private Long taskId;
    private List<ReviewIssue> issues;
    private ReviewStatisticsDTO statistics;
    private ReviewMetadata metadata;
    private Boolean success;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
