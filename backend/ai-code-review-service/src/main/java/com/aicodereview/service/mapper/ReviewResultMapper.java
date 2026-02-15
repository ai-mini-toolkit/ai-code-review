package com.aicodereview.service.mapper;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.repository.entity.ReviewResultEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for converting between ReviewResultEntity and ReviewResultDTO,
 * including JSON serialization/deserialization for JSONB columns and statistics calculation.
 *
 * @since 5.1.0
 */
@Slf4j
public final class ReviewResultMapper {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private ReviewResultMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Serializes a list of ReviewIssue objects to a JSON string for JSONB storage.
     *
     * @param issues the issues list (may be null or empty)
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    public static String serializeIssues(List<ReviewIssue> issues) {
        try {
            return OBJECT_MAPPER.writeValueAsString(issues != null ? issues : List.of());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize issues to JSON", e);
            throw new IllegalStateException("Failed to serialize issues to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to a list of ReviewIssue objects.
     *
     * @param json the JSON string from JSONB column
     * @return list of ReviewIssue objects
     * @throws IllegalStateException if deserialization fails
     */
    public static List<ReviewIssue> deserializeIssues(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<ReviewIssue>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize issues from JSON: {}", json, e);
            throw new IllegalStateException("Failed to deserialize issues from JSON", e);
        }
    }

    /**
     * Serializes ReviewMetadata to a JSON string for JSONB storage.
     *
     * @param metadata the metadata object (may be null)
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    public static String serializeMetadata(ReviewMetadata metadata) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata to JSON", e);
            throw new IllegalStateException("Failed to serialize metadata to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to a ReviewMetadata object.
     *
     * @param json the JSON string from JSONB column
     * @return ReviewMetadata object, or null if input is empty
     * @throws IllegalStateException if deserialization fails
     */
    public static ReviewMetadata deserializeMetadata(String json) {
        try {
            if (json == null || json.isBlank() || "{}".equals(json)) {
                return null;
            }
            return OBJECT_MAPPER.readValue(json, ReviewMetadata.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize metadata from JSON: {}", json, e);
            throw new IllegalStateException("Failed to deserialize metadata from JSON", e);
        }
    }

    /**
     * Serializes ReviewStatisticsDTO to a JSON string for JSONB storage.
     *
     * @param statistics the statistics object
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    public static String serializeStatistics(ReviewStatisticsDTO statistics) {
        try {
            return OBJECT_MAPPER.writeValueAsString(statistics != null ? statistics : Map.of());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize statistics to JSON", e);
            throw new IllegalStateException("Failed to serialize statistics to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to a ReviewStatisticsDTO object.
     *
     * @param json the JSON string from JSONB column
     * @return ReviewStatisticsDTO object, or empty statistics if input is empty
     * @throws IllegalStateException if deserialization fails
     */
    public static ReviewStatisticsDTO deserializeStatistics(String json) {
        try {
            if (json == null || json.isBlank() || "{}".equals(json)) {
                return ReviewStatisticsDTO.builder().total(0).bySeverity(Map.of()).byCategory(Map.of()).build();
            }
            return OBJECT_MAPPER.readValue(json, ReviewStatisticsDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize statistics from JSON: {}", json, e);
            throw new IllegalStateException("Failed to deserialize statistics from JSON", e);
        }
    }

    /**
     * Calculates issue statistics by grouping counts by severity and category.
     * All enum values are included in the result (zero counts for missing values).
     *
     * @param issues the list of review issues
     * @return computed statistics DTO
     */
    public static ReviewStatisticsDTO calculateStatistics(List<ReviewIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return emptyStatistics();
        }

        Map<IssueSeverity, Integer> severityMap = new EnumMap<>(IssueSeverity.class);
        Map<IssueCategory, Integer> categoryMap = new EnumMap<>(IssueCategory.class);

        // Initialize all enum values to 0
        for (IssueSeverity s : IssueSeverity.values()) {
            severityMap.put(s, 0);
        }
        for (IssueCategory c : IssueCategory.values()) {
            categoryMap.put(c, 0);
        }

        // Count occurrences
        for (ReviewIssue issue : issues) {
            if (issue.getSeverity() != null) {
                severityMap.merge(issue.getSeverity(), 1, Integer::sum);
            }
            if (issue.getCategory() != null) {
                categoryMap.merge(issue.getCategory(), 1, Integer::sum);
            }
        }

        // Convert enum keys to String keys (LinkedHashMap preserves enum declaration order)
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        severityMap.forEach((k, v) -> bySeverity.put(k.name(), v));
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        categoryMap.forEach((k, v) -> byCategory.put(k.name(), v));

        return ReviewStatisticsDTO.builder()
                .total(issues.size())
                .bySeverity(bySeverity)
                .byCategory(byCategory)
                .build();
    }

    /**
     * Returns an empty statistics DTO with all severity and category counts at zero.
     *
     * @return empty statistics
     */
    public static ReviewStatisticsDTO emptyStatistics() {
        Map<String, Integer> bySeverity = new java.util.LinkedHashMap<>();
        for (IssueSeverity s : IssueSeverity.values()) {
            bySeverity.put(s.name(), 0);
        }
        Map<String, Integer> byCategory = new java.util.LinkedHashMap<>();
        for (IssueCategory c : IssueCategory.values()) {
            byCategory.put(c.name(), 0);
        }
        return ReviewStatisticsDTO.builder()
                .total(0)
                .bySeverity(bySeverity)
                .byCategory(byCategory)
                .build();
    }

    /**
     * Converts a ReviewResultEntity to ReviewResultDTO.
     *
     * @param entity     the entity to convert (must not be null)
     * @param issues     the deserialized issues list
     * @param statistics the deserialized/computed statistics
     * @param metadata   the deserialized metadata (may be null for failed reviews)
     * @return the ReviewResultDTO
     */
    public static ReviewResultDTO toDTO(ReviewResultEntity entity,
                                         List<ReviewIssue> issues,
                                         ReviewStatisticsDTO statistics,
                                         ReviewMetadata metadata) {
        return ReviewResultDTO.builder()
                .id(entity.getId())
                .taskId(entity.getReviewTask().getId())
                .issues(issues)
                .statistics(statistics)
                .metadata(metadata)
                .success(entity.getSuccess())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
