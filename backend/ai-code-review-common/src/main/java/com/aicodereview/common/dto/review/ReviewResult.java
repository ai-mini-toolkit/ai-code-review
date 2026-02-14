package com.aicodereview.common.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * The main output of an AI code review.
 * <p>
 * Use static factories:
 * <ul>
 *   <li>{@link #success(List, ReviewMetadata)} for successful reviews</li>
 *   <li>{@link #failed(String)} for failed reviews</li>
 * </ul>
 * </p>
 *
 * @since 4.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResult {

    private List<ReviewIssue> issues;
    private ReviewMetadata metadata;
    private boolean success;
    private String errorMessage;

    public static ReviewResult success(List<ReviewIssue> issues, ReviewMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null for successful reviews");
        return ReviewResult.builder()
                .issues(issues != null ? issues : List.of())
                .metadata(metadata)
                .success(true)
                .build();
    }

    public static ReviewResult failed(String errorMessage) {
        return ReviewResult.builder()
                .issues(List.of())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
