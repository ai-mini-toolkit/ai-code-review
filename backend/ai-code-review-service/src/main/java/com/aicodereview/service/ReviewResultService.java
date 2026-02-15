package com.aicodereview.service;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.exception.ResourceNotFoundException;

/**
 * Service interface for persisting and retrieving AI code review results.
 * <p>
 * Called by ReviewOrchestrator after review completes (success or failure).
 * Results are stored in PostgreSQL with JSONB columns for issues, statistics, and metadata.
 * </p>
 *
 * @since 5.1.0
 */
public interface ReviewResultService {

    /**
     * Persists an AI review result to the database.
     * <p>
     * This method:
     * 1. Validates the task exists
     * 2. Serializes issues, statistics, and metadata to JSON
     * 3. Calculates statistics from the issues list
     * 4. Saves the ReviewResultEntity
     * 5. Updates the ReviewTask status to COMPLETED
     * </p>
     *
     * @param taskId       the review task ID
     * @param reviewResult the AI review result (success or failure)
     * @return the persisted result as DTO
     * @throws ResourceNotFoundException if taskId does not exist
     */
    ReviewResultDTO saveResult(Long taskId, ReviewResult reviewResult);

    /**
     * Retrieves a review result by task ID.
     *
     * @param taskId the review task ID
     * @return the review result DTO
     * @throws ResourceNotFoundException if no result exists for the task
     */
    ReviewResultDTO getResultByTaskId(Long taskId);
}
