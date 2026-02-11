package com.aicodereview.service.mapper;

import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.repository.entity.ReviewTask;

/**
 * Utility class for converting between ReviewTask entity and ReviewTaskDTO.
 * <p>
 * Provides static methods for bidirectional mapping:
 * - toDTO: Entity → DTO (for API responses)
 * - toEntity: DTO → Entity (rarely used, prefer Builder for entity creation)
 * </p>
 * <p>
 * Note: toEntity() is provided for completeness but typically not used.
 * Entity creation should use ReviewTask.builder() in service layer for better control
 * over associations (e.g., setting Project reference).
 * </p>
 *
 * @since 2.5.0
 */
public final class ReviewTaskMapper {

    // Private constructor to prevent instantiation
    private ReviewTaskMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts ReviewTask entity to ReviewTaskDTO.
     * <p>
     * Maps all entity fields to DTO fields.
     * Project entity is reduced to projectId for DTO.
     * </p>
     *
     * @param entity the ReviewTask entity to convert (must not be null)
     * @return the ReviewTaskDTO
     * @throws IllegalArgumentException if entity is null
     */
    public static ReviewTaskDTO toDTO(ReviewTask entity) {
        if (entity == null) {
            throw new IllegalArgumentException("ReviewTask entity cannot be null");
        }

        return ReviewTaskDTO.builder()
                .id(entity.getId())
                .projectId(entity.getProject() != null ? entity.getProject().getId() : null)
                .taskType(entity.getTaskType())
                .repoUrl(entity.getRepoUrl())
                .branch(entity.getBranch())
                .commitHash(entity.getCommitHash())
                .prNumber(entity.getPrNumber())
                .prTitle(entity.getPrTitle())
                .prDescription(entity.getPrDescription())
                .author(entity.getAuthor())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .retryCount(entity.getRetryCount())
                .maxRetries(entity.getMaxRetries())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts ReviewTaskDTO to ReviewTask entity (partial).
     * <p>
     * Note: This method does NOT set the Project association.
     * Use ReviewTask.builder() in service layer for proper entity construction.
     * </p>
     * <p>
     * This method is provided for testing purposes and should rarely be used in production code.
     * </p>
     *
     * @param dto the ReviewTaskDTO to convert (must not be null)
     * @return the ReviewTask entity (without Project association)
     * @throws IllegalArgumentException if dto is null
     */
    public static ReviewTask toEntity(ReviewTaskDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ReviewTaskDTO cannot be null");
        }

        return ReviewTask.builder()
                .id(dto.getId())
                // Note: Project association NOT set - must be set separately
                .taskType(dto.getTaskType())
                .repoUrl(dto.getRepoUrl())
                .branch(dto.getBranch())
                .commitHash(dto.getCommitHash())
                .prNumber(dto.getPrNumber())
                .prTitle(dto.getPrTitle())
                .prDescription(dto.getPrDescription())
                .author(dto.getAuthor())
                .status(dto.getStatus())
                .priority(dto.getPriority())
                .retryCount(dto.getRetryCount())
                .maxRetries(dto.getMaxRetries())
                .errorMessage(dto.getErrorMessage())
                .createdAt(dto.getCreatedAt())
                .startedAt(dto.getStartedAt())
                .completedAt(dto.getCompletedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
