package com.aicodereview.service;

import com.aicodereview.common.dto.reviewtask.CreateReviewTaskRequest;
import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.exception.ResourceNotFoundException;

import java.util.List;

/**
 * Service interface for managing code review tasks.
 * <p>
 * Provides business logic for task lifecycle management:
 * - Task creation from webhook events
 * - Task status transitions (PENDING → RUNNING → COMPLETED/FAILED)
 * - Task retry logic and failure handling
 * - Task querying and retrieval
 * </p>
 * <p>
 * All methods return DTOs (not entities) to decouple service layer from persistence layer.
 * </p>
 *
 * @since 2.5.0
 */
public interface ReviewTaskService {

    /**
     * Creates a new review task from webhook event.
     * <p>
     * Automatically configures task based on taskType:
     * - Initial status: PENDING
     * - Priority: HIGH for PR/MR, NORMAL for PUSH
     * - retry_count: 0
     * - max_retries: 3
     * </p>
     * <p>
     * Business rules:
     * - Validates projectId exists (throws ResourceNotFoundException if not)
     * - Sets priority automatically based on taskType
     * - Initializes timestamps (created_at auto-populated by JPA)
     * </p>
     *
     * @param request the task creation request containing all task details
     * @return the created task DTO with generated ID and timestamps
     * @throws ResourceNotFoundException if project with given projectId does not exist
     */
    ReviewTaskDTO createTask(CreateReviewTaskRequest request);

    /**
     * Retrieves a task by its ID.
     *
     * @param id the task ID to retrieve
     * @return the task DTO
     * @throws ResourceNotFoundException if task with given ID does not exist
     */
    ReviewTaskDTO getTaskById(Long id);

    /**
     * Retrieves all tasks associated with a specific project.
     * <p>
     * Used for project-level task history and analytics.
     * Returns tasks in natural order (by ID).
     * </p>
     *
     * @param projectId the project ID to query
     * @return list of task DTOs for the project (empty list if no tasks found)
     */
    List<ReviewTaskDTO> getTasksByProjectId(Long projectId);

    /**
     * Retrieves tasks by status, ordered by priority (DESC) and created_at (ASC).
     * <p>
     * This is the primary method for queue workers to fetch next task:
     * - HIGH priority tasks are returned first
     * - Within same priority, older tasks come first (FIFO)
     * </p>
     * <p>
     * Example: getTasksByStatus(TaskStatus.PENDING) returns all pending tasks
     * in queue order.
     * </p>
     *
     * @param status the task status to filter by (typically PENDING for queue operations)
     * @return list of task DTOs ordered by priority and creation time
     */
    List<ReviewTaskDTO> getTasksByStatus(TaskStatus status);

    /**
     * Marks a task as started (status = RUNNING, started_at = now).
     * <p>
     * Called by worker when it picks up a task from the queue.
     * Updates status to RUNNING and sets startedAt timestamp.
     * </p>
     *
     * @param id the task ID to mark as started
     * @return the updated task DTO with new status and timestamp
     * @throws ResourceNotFoundException if task with given ID does not exist
     */
    ReviewTaskDTO markTaskStarted(Long id);

    /**
     * Marks a task as completed (status = COMPLETED, completed_at = now).
     * <p>
     * Called by worker when task execution succeeds without errors.
     * Updates status to COMPLETED and sets completedAt timestamp.
     * </p>
     *
     * @param id the task ID to mark as completed
     * @return the updated task DTO with new status and timestamp
     * @throws ResourceNotFoundException if task with given ID does not exist
     */
    ReviewTaskDTO markTaskCompleted(Long id);

    /**
     * Marks a task as failed and increments retry_count.
     * <p>
     * Called by worker when task execution fails.
     * Increments retry_count and sets error_message.
     * </p>
     * <p>
     * If retry_count >= max_retries after increment:
     * - Sets status to FAILED
     * - Sets completedAt timestamp
     * - Logs warning "Max retries reached for task {id}"
     * </p>
     * <p>
     * If retry_count < max_retries after increment:
     * - Status remains PENDING (or reverts to PENDING if was RUNNING)
     * - Task can be retried
     * </p>
     *
     * @param id           the task ID to mark as failed
     * @param errorMessage the error message describing the failure
     * @return the updated task DTO with incremented retry_count and error_message
     * @throws ResourceNotFoundException if task with given ID does not exist
     */
    ReviewTaskDTO markTaskFailed(Long id, String errorMessage);

    /**
     * Checks if a task can be retried.
     * <p>
     * Returns true if retry_count < max_retries, false otherwise.
     * </p>
     * <p>
     * Used by retry logic to determine if failed task should be re-queued.
     * </p>
     *
     * @param id the task ID to check
     * @return true if task can be retried, false if max retries exhausted
     * @throws ResourceNotFoundException if task with given ID does not exist
     */
    boolean canRetry(Long id);
}
