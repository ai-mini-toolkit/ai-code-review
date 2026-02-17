package com.aicodereview.service;

import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;

/**
 * Service for validating review results against project threshold rules.
 * <p>
 * Loads the project's threshold configuration and evaluates severity-based
 * and total-issues rules against computed review statistics.
 * </p>
 *
 * @since 6.2.0
 */
public interface ThresholdValidationService {

    /**
     * Validates review statistics against the threshold configuration of a project.
     * <p>
     * Returns a passed result with no violations if thresholds are disabled.
     * Otherwise, evaluates each rule and collects violations.
     * </p>
     *
     * @param projectId  the project ID to load threshold config from
     * @param statistics the computed review statistics
     * @return validation result with pass/fail status, violations, and action
     */
    ThresholdValidationResultDTO validate(Long projectId, ReviewStatisticsDTO statistics);
}
