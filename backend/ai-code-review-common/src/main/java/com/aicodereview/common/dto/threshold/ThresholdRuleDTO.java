package com.aicodereview.common.dto.threshold;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single threshold rule for quality gate evaluation.
 * <p>
 * Two rule types are supported:
 * <ul>
 *   <li><b>Severity rule</b>: severity + maxCount (both non-null)</li>
 *   <li><b>Total issues rule</b>: totalIssues (non-null)</li>
 * </ul>
 * </p>
 *
 * @since 6.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdRuleDTO {

    /**
     * Issue severity level (CRITICAL, HIGH, MEDIUM, LOW, INFO).
     * Required for severity-based rules, null for total-issues rules.
     */
    private String severity;

    /**
     * Maximum allowed count for the specified severity.
     * Must be >= 0. Required for severity-based rules.
     */
    @Min(0)
    private Integer maxCount;

    /**
     * Maximum total issues allowed across all severities.
     * Must be >= 0. Required for total-issues rules.
     */
    @Min(value = 0)
    private Integer totalIssues;
}
