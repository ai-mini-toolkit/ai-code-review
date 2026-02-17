package com.aicodereview.service.mapper;

import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.common.enums.IssueSeverity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Utility class for serializing/deserializing ThresholdConfigDTO to/from JSON
 * and validating threshold rules.
 *
 * @since 6.1.0
 */
@Slf4j
public final class ThresholdMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ThresholdMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Serializes a ThresholdConfigDTO to a JSON string for JSONB storage.
     *
     * @param config the threshold configuration (may be null)
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    public static String serialize(ThresholdConfigDTO config) {
        try {
            return OBJECT_MAPPER.writeValueAsString(config != null ? config : defaultConfig());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize threshold config to JSON", e);
            throw new IllegalStateException("Failed to serialize threshold config to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to a ThresholdConfigDTO.
     *
     * @param json the JSON string from JSONB column
     * @return ThresholdConfigDTO object, or default config if input is empty
     * @throws IllegalStateException if deserialization fails
     */
    public static ThresholdConfigDTO deserialize(String json) {
        try {
            if (json == null || json.isBlank() || "{}".equals(json)) {
                return defaultConfig();
            }
            return OBJECT_MAPPER.readValue(json, ThresholdConfigDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize threshold config from JSON: {}", json, e);
            throw new IllegalStateException("Failed to deserialize threshold config from JSON", e);
        }
    }

    /**
     * Validates a threshold configuration, throwing IllegalArgumentException for invalid rules.
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if any rule is invalid
     */
    public static void validate(ThresholdConfigDTO config) {
        if (config == null) {
            throw new IllegalArgumentException("Threshold configuration must not be null");
        }
        if (config.getEnabled() == null) {
            throw new IllegalArgumentException("Threshold 'enabled' must not be null");
        }
        if (config.getRules() == null) {
            throw new IllegalArgumentException("Threshold 'rules' must not be null");
        }
        if (config.getAction() == null) {
            throw new IllegalArgumentException("Threshold 'action' must not be null");
        }
        if (!"BLOCK_MERGE".equals(config.getAction()) && !"WARN_ONLY".equals(config.getAction())) {
            throw new IllegalArgumentException(
                    "Threshold 'action' must be BLOCK_MERGE or WARN_ONLY, got: " + config.getAction());
        }

        List<ThresholdRuleDTO> rules = config.getRules();
        for (int i = 0; i < rules.size(); i++) {
            validateRule(rules.get(i), i);
        }
    }

    private static void validateRule(ThresholdRuleDTO rule, int index) {
        boolean hasSeverity = rule.getSeverity() != null;
        boolean hasMaxCount = rule.getMaxCount() != null;
        boolean hasTotalIssues = rule.getTotalIssues() != null;

        // Must be exactly one rule type
        if (hasSeverity && hasTotalIssues) {
            throw new IllegalArgumentException(
                    "Rule[" + index + "]: cannot have both 'severity' and 'totalIssues'");
        }
        if (!hasSeverity && !hasTotalIssues) {
            throw new IllegalArgumentException(
                    "Rule[" + index + "]: must have either 'severity' or 'totalIssues'");
        }

        // Severity rule: severity + maxCount required
        if (hasSeverity) {
            if (!hasMaxCount) {
                throw new IllegalArgumentException(
                        "Rule[" + index + "]: severity rule requires 'maxCount'");
            }
            if (rule.getMaxCount() < 0) {
                throw new IllegalArgumentException(
                        "Rule[" + index + "]: 'maxCount' must be >= 0, got: " + rule.getMaxCount());
            }
            // Validate severity is a known enum value
            try {
                IssueSeverity.valueOf(rule.getSeverity());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Rule[" + index + "]: invalid severity '" + rule.getSeverity()
                                + "'. Valid values: CRITICAL, HIGH, MEDIUM, LOW, INFO");
            }
        }

        // Total issues rule
        if (hasTotalIssues) {
            if (rule.getTotalIssues() < 0) {
                throw new IllegalArgumentException(
                        "Rule[" + index + "]: 'totalIssues' must be >= 0, got: " + rule.getTotalIssues());
            }
        }
    }

    /**
     * Returns the default threshold configuration.
     */
    public static ThresholdConfigDTO defaultConfig() {
        return ThresholdConfigDTO.builder()
                .enabled(false)
                .rules(List.of(
                        ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                        ThresholdRuleDTO.builder().severity("HIGH").maxCount(5).build(),
                        ThresholdRuleDTO.builder().totalIssues(30).build()
                ))
                .action("BLOCK_MERGE")
                .build();
    }
}
