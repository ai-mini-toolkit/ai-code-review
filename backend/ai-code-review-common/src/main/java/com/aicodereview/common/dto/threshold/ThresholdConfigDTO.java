package com.aicodereview.common.dto.threshold;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a project's quality threshold configuration.
 * <p>
 * Controls whether code review results are used to block PR/MR merges
 * or just warn about quality issues.
 * </p>
 *
 * @since 6.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdConfigDTO {

    /**
     * Whether threshold enforcement is enabled for this project.
     */
    @NotNull
    private Boolean enabled;

    /**
     * List of threshold rules to evaluate against review results.
     */
    @NotNull
    @Valid
    private List<ThresholdRuleDTO> rules;

    /**
     * Action to take when thresholds are violated.
     * BLOCK_MERGE: Block the PR/MR from merging.
     * WARN_ONLY: Allow merge but add a warning.
     */
    @NotNull
    @Pattern(regexp = "BLOCK_MERGE|WARN_ONLY", message = "action must be BLOCK_MERGE or WARN_ONLY")
    private String action;
}
