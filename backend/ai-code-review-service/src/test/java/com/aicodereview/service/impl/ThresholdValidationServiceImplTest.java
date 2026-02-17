package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import com.aicodereview.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ThresholdValidationServiceImpl.
 *
 * @since 6.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ThresholdValidationServiceImpl Unit Tests")
class ThresholdValidationServiceImplTest {

    @Mock
    private ProjectService projectService;

    private ThresholdValidationServiceImpl service;

    private static final Long PROJECT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ThresholdValidationServiceImpl(projectService);
    }

    private ReviewStatisticsDTO buildStats(int total, Map<String, Integer> bySeverity) {
        return ReviewStatisticsDTO.builder()
                .total(total)
                .bySeverity(bySeverity)
                .byCategory(Map.of())
                .build();
    }

    private Map<String, Integer> severityMap(int critical, int high, int medium, int low, int info) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("CRITICAL", critical);
        map.put("HIGH", high);
        map.put("MEDIUM", medium);
        map.put("LOW", low);
        map.put("INFO", info);
        return map;
    }

    @Nested
    @DisplayName("AC4: Disabled Thresholds")
    class DisabledThresholds {

        @Test
        @DisplayName("Should return passed=true when thresholds disabled")
        void shouldReturnPassedWhenDisabled() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(false)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(10, severityMap(5, 5, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
            assertThat(result.getAction()).isNull();
        }

        @Test
        @DisplayName("Should return passed=true when enabled is null")
        void shouldReturnPassedWhenEnabledNull() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(null)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(10, severityMap(5, 0, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
            assertThat(result.getAction()).isNull();
        }
    }

    @Nested
    @DisplayName("AC2: Severity Rule Validation")
    class SeverityRuleValidation {

        @Test
        @DisplayName("Should detect CRITICAL severity violation")
        void shouldDetectCriticalViolation() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(2, severityMap(2, 0, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).hasSize(1);
            assertThat(result.getAction()).isEqualTo("BLOCK_MERGE");

            ThresholdViolationDTO violation = result.getViolations().get(0);
            assertThat(violation.getRule()).isEqualTo("CRITICAL <= 0");
            assertThat(violation.getActual()).isEqualTo(2);
            assertThat(violation.getThreshold()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should pass when severity count within limit")
        void shouldPassWhenWithinLimit() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(3, severityMap(0, 3, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
            assertThat(result.getAction()).isNull();
        }

        @Test
        @DisplayName("Should pass when severity count exactly at limit")
        void shouldPassWhenExactlyAtLimit() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(5, severityMap(0, 5, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("Should use getOrDefault for missing severity in bySeverity map")
        void shouldHandleMissingSeverityKey() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            // bySeverity map has no CRITICAL key
            ReviewStatisticsDTO stats = buildStats(2, Map.of("HIGH", 2));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AC3: Total Issues Rule Validation")
    class TotalIssuesRuleValidation {

        @Test
        @DisplayName("Should detect total issues violation")
        void shouldDetectTotalIssuesViolation() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().totalIssues(20).build()))
                    .action("WARN_ONLY")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(25, severityMap(0, 5, 10, 5, 5));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).hasSize(1);
            assertThat(result.getAction()).isEqualTo("WARN_ONLY");

            ThresholdViolationDTO violation = result.getViolations().get(0);
            assertThat(violation.getRule()).isEqualTo("totalIssues <= 20");
            assertThat(violation.getActual()).isEqualTo(25);
            assertThat(violation.getThreshold()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should pass when total issues within limit")
        void shouldPassWhenTotalWithinLimit() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().totalIssues(20).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(15, severityMap(0, 5, 5, 3, 2));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("Should pass when total issues exactly at limit")
        void shouldPassWhenTotalExactlyAtLimit() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().totalIssues(20).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(20, severityMap(0, 5, 5, 5, 5));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AC5: Multi-Rule Validation")
    class MultiRuleValidation {

        @Test
        @DisplayName("Should collect multiple violations from different rules")
        void shouldCollectMultipleViolations() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().severity("HIGH").maxCount(3).build(),
                            ThresholdRuleDTO.builder().totalIssues(20).build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(25, severityMap(2, 5, 10, 5, 3));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).hasSize(3);
            assertThat(result.getAction()).isEqualTo("BLOCK_MERGE");

            assertThat(result.getViolations().get(0).getRule()).isEqualTo("CRITICAL <= 0");
            assertThat(result.getViolations().get(1).getRule()).isEqualTo("HIGH <= 3");
            assertThat(result.getViolations().get(2).getRule()).isEqualTo("totalIssues <= 20");
        }

        @Test
        @DisplayName("Should pass when all rules satisfied")
        void shouldPassWhenAllRulesSatisfied() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build(),
                            ThresholdRuleDTO.builder().totalIssues(30).build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(10, severityMap(0, 3, 4, 2, 1));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
            assertThat(result.getAction()).isNull();
        }

        @Test
        @DisplayName("Should only report violated rules, not passing ones")
        void shouldOnlyReportViolatedRules() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build(),
                            ThresholdRuleDTO.builder().totalIssues(30).build()
                    ))
                    .action("WARN_ONLY")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            // Only CRITICAL violated (1 > 0), HIGH and totalIssues pass
            ReviewStatisticsDTO stats = buildStats(10, severityMap(1, 3, 4, 1, 1));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).hasSize(1);
            assertThat(result.getViolations().get(0).getRule()).isEqualTo("CRITICAL <= 0");
            assertThat(result.getAction()).isEqualTo("WARN_ONLY");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle zero-issue statistics")
        void shouldHandleZeroIssues() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(
                            ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                            ThresholdRuleDTO.builder().totalIssues(20).build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(0, severityMap(0, 0, 0, 0, 0));

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty bySeverity map")
        void shouldHandleEmptyBySeverityMap() {
            ThresholdConfigDTO config = ThresholdConfigDTO.builder()
                    .enabled(true)
                    .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build()))
                    .action("BLOCK_MERGE")
                    .build();
            when(projectService.getThresholds(PROJECT_ID)).thenReturn(config);

            ReviewStatisticsDTO stats = buildStats(0, Map.of());

            ThresholdValidationResultDTO result = service.validate(PROJECT_ID, stats);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }
    }
}
