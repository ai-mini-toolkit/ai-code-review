package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.service.ProjectService;
import com.aicodereview.service.ThresholdValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ThresholdValidationService.
 * <p>
 * Evaluates review statistics against project threshold rules:
 * severity-based rules check bySeverity counts, total-issues rules check the total count.
 * </p>
 *
 * @since 6.2.0
 */
@Slf4j
@Service
public class ThresholdValidationServiceImpl implements ThresholdValidationService {

    private final ProjectService projectService;

    public ThresholdValidationServiceImpl(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ThresholdValidationResultDTO validate(Long projectId, ReviewStatisticsDTO statistics) {
        log.info("Validating thresholds for project: {}", projectId);

        ThresholdConfigDTO config = projectService.getThresholds(projectId);

        // AC4: If thresholds disabled, skip validation entirely
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.info("Thresholds disabled for project: {}, skipping validation", projectId);
            return ThresholdValidationResultDTO.builder()
                    .passed(true)
                    .violations(List.of())
                    .action(null)
                    .build();
        }

        List<ThresholdViolationDTO> violations = new ArrayList<>();

        if (config.getRules() == null || config.getRules().isEmpty()) {
            log.warn("Thresholds enabled but no rules configured for project: {}", projectId);
            return ThresholdValidationResultDTO.builder()
                    .passed(true)
                    .action(null)
                    .build();
        }

        for (ThresholdRuleDTO rule : config.getRules()) {
            if (rule.getSeverity() != null) {
                // AC2: Severity-based rule
                int actual = statistics.getBySeverity()
                        .getOrDefault(rule.getSeverity(), 0);
                if (actual > rule.getMaxCount()) {
                    log.debug("Threshold violated for project {}: {} actual={} > maxCount={}",
                            projectId, rule.getSeverity(), actual, rule.getMaxCount());
                    violations.add(ThresholdViolationDTO.builder()
                            .rule(rule.getSeverity() + " <= " + rule.getMaxCount())
                            .actual(actual)
                            .threshold(rule.getMaxCount())
                            .build());
                }
            } else if (rule.getTotalIssues() != null) {
                // AC3: Total issues rule
                int actual = statistics.getTotal();
                if (actual > rule.getTotalIssues()) {
                    log.debug("Threshold violated for project {}: totalIssues actual={} > limit={}",
                            projectId, actual, rule.getTotalIssues());
                    violations.add(ThresholdViolationDTO.builder()
                            .rule("totalIssues <= " + rule.getTotalIssues())
                            .actual(actual)
                            .threshold(rule.getTotalIssues())
                            .build());
                }
            }
        }

        // AC5: passed = true only if no violations; action from config when violations exist
        boolean passed = violations.isEmpty();
        String action = passed ? null : config.getAction();

        log.info("Threshold validation for project {}: passed={}, violations={}",
                projectId, passed, violations.size());

        return ThresholdValidationResultDTO.builder()
                .passed(passed)
                .violations(violations)
                .action(action)
                .build();
    }
}
