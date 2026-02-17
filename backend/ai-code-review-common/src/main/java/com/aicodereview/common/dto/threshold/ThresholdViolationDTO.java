package com.aicodereview.common.dto.threshold;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single threshold rule violation.
 * <p>
 * Captures the rule description (e.g., "CRITICAL <= 0" or "totalIssues <= 20"),
 * the actual value found in review results, and the threshold limit.
 * </p>
 *
 * @since 6.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdViolationDTO {

    /**
     * Rule description, e.g., "CRITICAL <= 0" or "totalIssues <= 20".
     */
    private String rule;

    /**
     * Actual value found in review results.
     */
    private int actual;

    /**
     * Threshold limit from the rule configuration.
     */
    private int threshold;
}
