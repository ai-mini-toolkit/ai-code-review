package com.aicodereview.repository;

import com.aicodereview.repository.entity.ReviewResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ReviewResultEntity providing CRUD operations and custom queries.
 * <p>
 * Supports result retrieval by task ID and success status filtering.
 * </p>
 *
 * @since 5.1.0
 */
@Repository
public interface ReviewResultRepository extends JpaRepository<ReviewResultEntity, Long> {

    /**
     * Finds review result by associated task ID (1:1 relationship).
     *
     * @param taskId the review task ID
     * @return Optional containing the result if found
     */
    @Query("SELECT r FROM ReviewResultEntity r JOIN FETCH r.reviewTask WHERE r.reviewTask.id = :taskId")
    Optional<ReviewResultEntity> findByReviewTaskId(@Param("taskId") Long taskId);

    /**
     * Checks if a review result already exists for the given task ID.
     *
     * @param taskId the review task ID
     * @return true if a result exists for the task
     */
    @Query("SELECT COUNT(r) > 0 FROM ReviewResultEntity r WHERE r.reviewTask.id = :taskId")
    boolean existsByTaskId(@Param("taskId") Long taskId);

    /**
     * Finds all review results filtered by success status.
     *
     * @param success true for successful reviews, false for failed
     * @return list of results matching the success status
     */
    @Query("SELECT r FROM ReviewResultEntity r JOIN FETCH r.reviewTask WHERE r.success = :success ORDER BY r.createdAt DESC")
    List<ReviewResultEntity> findBySuccess(@Param("success") Boolean success);
}
