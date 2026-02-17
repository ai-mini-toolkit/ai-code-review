package com.aicodereview.common.dto.threshold;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of evaluating review statistics against project threshold rules.
 * <p>
 * Contains pass/fail status, list of violated rules, and the configured action
 * (BLOCK_MERGE or WARN_ONLY) when violations exist.
 * </p>
 *
 * @since 6.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdValidationResultDTO {

    /**
     * Whether all threshold rules passed.
     */
    private boolean passed;

    /**
     * List of threshold rule violations (empty if all rules passed).
     */
    private List<ThresholdViolationDTO> violations;

    /**
     * Action from ThresholdConfigDTO: BLOCK_MERGE or WARN_ONLY.
     * Null when all rules passed (no action needed).
     */
    private String action;
}
