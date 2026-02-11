package com.aicodereview.repository;

import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.repository.entity.ReviewTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ReviewTask entity providing CRUD operations and custom queries.
 * <p>
 * This repository supports task management operations for the code review workflow,
 * including task retrieval by project, status, and priority-ordered queue operations.
 * </p>
 * <p>
 * Query method naming follows Spring Data JPA conventions:
 * - findBy* methods return List or Optional
 * - OrderBy* suffix specifies sorting
 * - Multiple OrderBy clauses chain with "And" or "Or"
 * </p>
 *
 * @since 2.5.0
 */
@Repository
public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {

    /**
     * Finds all tasks associated with a specific project.
     * Used for project-level task history and analytics.
     * <p>
     * Uses JOIN FETCH to eagerly load the project association,
     * preventing N+1 queries when mapping to DTO.
     * </p>
     *
     * @param projectId the project ID to query
     * @return list of tasks for the project (empty list if none found)
     */
    @Query("SELECT t FROM ReviewTask t JOIN FETCH t.project WHERE t.project.id = :projectId")
    List<ReviewTask> findByProjectId(@Param("projectId") Long projectId);

    /**
     * Finds all tasks with a specific status.
     * Used for monitoring and debugging (e.g., finding stuck tasks).
     * <p>
     * Uses JOIN FETCH to eagerly load the project association,
     * preventing N+1 queries when mapping to DTO.
     * </p>
     *
     * @param status the task status to filter by
     * @return list of tasks with the given status
     */
    @Query("SELECT t FROM ReviewTask t JOIN FETCH t.project WHERE t.status = :status")
    List<ReviewTask> findByStatus(@Param("status") TaskStatus status);

    /**
     * Finds tasks by status, ordered by priority (descending) and created_at (ascending).
     * <p>
     * This is the primary query for the Redis queue worker:
     * - Fetches PENDING tasks
     * - HIGH priority tasks come first
     * - Within same priority, older tasks come first (FIFO)
     * </p>
     * <p>
     * Uses composite index: idx_review_task_status_priority_created
     * </p>
     * <p>
     * Uses JOIN FETCH to eagerly load the project association,
     * preventing N+1 queries when mapping to DTO.
     * </p>
     *
     * @param status the task status to filter by (typically PENDING)
     * @return list of tasks ordered by priority (HIGH first) and creation time (oldest first)
     */
    @Query("SELECT t FROM ReviewTask t JOIN FETCH t.project WHERE t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<ReviewTask> findByStatusOrderByPriorityDescCreatedAtAsc(@Param("status") TaskStatus status);

    /**
     * Finds a specific task by project ID and commit hash.
     * <p>
     * Used to prevent duplicate task creation for the same commit.
     * If a task already exists for a commit, we don't create a new one.
     * </p>
     *
     * @param projectId  the project ID
     * @param commitHash the Git commit SHA hash
     * @return Optional containing the task if found, empty otherwise
     */
    Optional<ReviewTask> findByProjectIdAndCommitHash(Long projectId, String commitHash);

    /**
     * Finds all tasks for a specific repository URL.
     * <p>
     * Used primarily for integration testing to verify webhook-triggered task creation.
     * </p>
     * <p>
     * Uses JOIN FETCH to eagerly load the project association,
     * preventing N+1 queries when mapping to DTO.
     * </p>
     *
     * @param repoUrl the repository URL
     * @return list of tasks for the repository
     */
    @Query("SELECT t FROM ReviewTask t JOIN FETCH t.project WHERE t.repoUrl = :repoUrl")
    List<ReviewTask> findByRepoUrl(@Param("repoUrl") String repoUrl);
}
