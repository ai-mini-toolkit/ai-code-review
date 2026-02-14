package com.aicodereview.service.impl;

import com.aicodereview.common.dto.reviewtask.CreateReviewTaskRequest;
import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.QueueService;
import com.aicodereview.service.ReviewTaskService;
import com.aicodereview.service.mapper.ReviewTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ReviewTaskService for managing code review tasks.
 * <p>
 * Provides business logic for task lifecycle management including:
 * - Task creation with automatic priority assignment
 * - Status transitions with timestamp tracking
 * - Retry logic with configurable max retries
 * - Task querying and retrieval
 * </p>
 * <p>
 * All public methods are transactional to ensure data consistency.
 * Logging is performed at key decision points for debugging and audit trail.
 * </p>
 *
 * @since 2.5.0
 */
@Slf4j
@Service
@Transactional
public class ReviewTaskServiceImpl implements ReviewTaskService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ProjectRepository projectRepository;
    private final QueueService queueService;

    @Value("${aicodereview.task.max-retries:3}")
    private int defaultMaxRetries;

    public ReviewTaskServiceImpl(ReviewTaskRepository reviewTaskRepository,
                                  ProjectRepository projectRepository,
                                  QueueService queueService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.projectRepository = projectRepository;
        this.queueService = queueService;
    }

    @Override
    public ReviewTaskDTO createTask(CreateReviewTaskRequest request) {
        log.info("Creating review task for project ID: {}, type: {}, commit: {}",
                request.getProjectId(), request.getTaskType(), request.getCommitHash());

        // Step 1: Validate project exists
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> {
                    log.warn("Project not found with ID: {}", request.getProjectId());
                    return new ResourceNotFoundException("Project", "id", request.getProjectId());
                });

        // Step 1.5: Check for duplicate task (same project + commit)
        Optional<ReviewTask> existingTask = reviewTaskRepository
                .findByProjectIdAndCommitHash(request.getProjectId(), request.getCommitHash());
        if (existingTask.isPresent()) {
            log.info("Task already exists for project {} and commit {} (task ID: {})",
                    request.getProjectId(), request.getCommitHash(), existingTask.get().getId());
            return ReviewTaskMapper.toDTO(existingTask.get());
        }

        // Step 2: Determine priority based on task type
        TaskPriority priority = determinePriority(request.getTaskType());
        log.debug("Assigned priority {} for task type {}", priority, request.getTaskType());

        // Step 3: Create entity using builder
        ReviewTask task = ReviewTask.builder()
                .project(project)
                .taskType(request.getTaskType())
                .repoUrl(request.getRepoUrl())
                .branch(request.getBranch())
                .commitHash(request.getCommitHash())
                .prNumber(request.getPrNumber())
                .prTitle(request.getPrTitle())
                .prDescription(request.getPrDescription())
                .author(request.getAuthor())
                .status(TaskStatus.PENDING)
                .priority(priority)
                .retryCount(0)
                .maxRetries(defaultMaxRetries)
                .build();

        // Step 4: Save to database
        ReviewTask saved = reviewTaskRepository.save(task);

        log.info("Successfully created review task with ID: {}, priority: {}", saved.getId(), priority);

        // Step 5: Enqueue task to Redis priority queue (best-effort, DB is primary record)
        try {
            queueService.enqueue(saved.getId(), priority);
        } catch (Exception e) {
            log.error("Failed to enqueue task {} to Redis queue. Task is saved in DB but not queued. " +
                    "Manual re-queue or reconciliation may be needed.", saved.getId(), e);
        }

        // Step 6: Convert to DTO and return
        return ReviewTaskMapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewTaskDTO getTaskById(Long id) {
        log.debug("Retrieving task by ID: {}", id);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Task not found with ID: {}", id);
                    return new ResourceNotFoundException("ReviewTask", "id", id);
                });

        return ReviewTaskMapper.toDTO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewTaskDTO> getTasksByProjectId(Long projectId) {
        log.debug("Retrieving tasks for project ID: {}", projectId);

        List<ReviewTask> tasks = reviewTaskRepository.findByProjectId(projectId);

        log.debug("Found {} tasks for project ID: {}", tasks.size(), projectId);

        return tasks.stream()
                .map(ReviewTaskMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewTaskDTO> getTasksByStatus(TaskStatus status) {
        log.debug("Retrieving tasks with status: {}", status);

        List<ReviewTask> tasks = reviewTaskRepository.findByStatusOrderByPriorityDescCreatedAtAsc(status);

        log.debug("Found {} tasks with status: {}", tasks.size(), status);

        return tasks.stream()
                .map(ReviewTaskMapper::toDTO)
                .toList();
    }

    @Override
    public ReviewTaskDTO markTaskStarted(Long id) {
        log.info("Marking task {} as started", id);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

        // Validate state transition: only PENDING tasks can be started
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot start task " + id + ": expected status PENDING, but was " + task.getStatus());
        }

        // Update status and set started timestamp
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());

        ReviewTask updated = reviewTaskRepository.save(task);

        log.info("Task {} marked as RUNNING at {}", id, updated.getStartedAt());

        return ReviewTaskMapper.toDTO(updated);
    }

    @Override
    public ReviewTaskDTO markTaskCompleted(Long id) {
        log.info("Marking task {} as completed", id);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

        // Validate state transition: only RUNNING tasks can be completed
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot complete task " + id + ": expected status RUNNING, but was " + task.getStatus());
        }

        // Update status and set completed timestamp
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());

        ReviewTask updated = reviewTaskRepository.save(task);

        log.info("Task {} marked as COMPLETED at {}", id, updated.getCompletedAt());

        return ReviewTaskMapper.toDTO(updated);
    }

    @Override
    public ReviewTaskDTO markTaskFailed(Long id, String errorMessage) {
        log.error("Marking task {} as failed with error: {}", id, errorMessage);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

        // Validate state transition: only RUNNING tasks can fail
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot fail task " + id + ": expected status RUNNING, but was " + task.getStatus());
        }

        // Increment retry count
        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(errorMessage);

        // Check if max retries reached
        if (task.getRetryCount() >= task.getMaxRetries()) {
            // Final failure - mark as FAILED and set completion timestamp
            task.setStatus(TaskStatus.FAILED);
            task.setCompletedAt(Instant.now());

            log.warn("Max retries reached for task {} (retries: {}/{})",
                    id, task.getRetryCount(), task.getMaxRetries());
        } else {
            // Still can retry - revert to PENDING status for re-queuing
            task.setStatus(TaskStatus.PENDING);

            log.info("Task {} will be retried (attempt {}/{})",
                    id, task.getRetryCount(), task.getMaxRetries());
        }

        ReviewTask updated = reviewTaskRepository.save(task);

        // Note: Requeue responsibility moved to RetryService (Story 2.7)
        // RetryService.handleTaskFailure() calls requeueWithDelay() with exponential backoff

        return ReviewTaskMapper.toDTO(updated);
    }

    @Override
    public ReviewTaskDTO markTaskFailedPermanently(Long id, String errorMessage) {
        log.error("Permanently failing task {} with error: {}", id, errorMessage);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

        // Validate state transition: only RUNNING tasks can be failed
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot fail task " + id + ": expected status RUNNING, but was " + task.getStatus());
        }

        // Immediately mark as FAILED — do NOT increment retryCount
        task.setStatus(TaskStatus.FAILED);
        task.setCompletedAt(Instant.now());
        task.setErrorMessage(errorMessage);

        ReviewTask updated = reviewTaskRepository.save(task);

        // Release queue lock (best-effort)
        try {
            queueService.releaseLock(id);
        } catch (Exception e) {
            log.error("Failed to release lock for permanently failed task {}", id, e);
        }

        log.warn("Task {} permanently failed (non-retryable): {}", id, errorMessage);

        return ReviewTaskMapper.toDTO(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRetry(Long id) {
        log.debug("Checking if task {} can be retried", id);

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

        boolean canRetry = task.getRetryCount() < task.getMaxRetries();

        log.debug("Task {} can retry: {} (retries: {}/{})",
                id, canRetry, task.getRetryCount(), task.getMaxRetries());

        return canRetry;
    }

    /**
     * Determines task priority based on task type.
     * <p>
     * Business rule:
     * - PR/MR tasks: HIGH priority (require immediate review before merge)
     * - PUSH tasks: NORMAL priority (batch review acceptable)
     * </p>
     *
     * @param taskType the task type
     * @return HIGH for PR/MR, NORMAL for PUSH
     */
    private TaskPriority determinePriority(TaskType taskType) {
        return switch (taskType) {
            case PULL_REQUEST, MERGE_REQUEST -> TaskPriority.HIGH;
            case PUSH -> TaskPriority.NORMAL;
        };
    }
}
