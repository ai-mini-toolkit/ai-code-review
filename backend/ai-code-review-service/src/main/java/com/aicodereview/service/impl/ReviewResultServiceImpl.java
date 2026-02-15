package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewResultEntity;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.service.ReviewResultService;
import com.aicodereview.service.mapper.ReviewResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of ReviewResultService for persisting AI code review results.
 * <p>
 * Handles JSON serialization of issues/statistics/metadata to JSONB columns,
 * statistics calculation, and task status updates within a single transaction.
 * </p>
 *
 * @since 5.1.0
 */
@Slf4j
@Service
@Transactional
public class ReviewResultServiceImpl implements ReviewResultService {

    private final ReviewResultRepository reviewResultRepository;
    private final ReviewTaskRepository reviewTaskRepository;

    public ReviewResultServiceImpl(ReviewResultRepository reviewResultRepository,
                                    ReviewTaskRepository reviewTaskRepository) {
        this.reviewResultRepository = reviewResultRepository;
        this.reviewTaskRepository = reviewTaskRepository;
    }

    @Override
    public ReviewResultDTO saveResult(Long taskId, ReviewResult reviewResult) {
        log.info("Saving review result for task: {}", taskId);

        // 1. Validate taskId exists
        ReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("ReviewTask not found with id: {}", taskId);
                    return new ResourceNotFoundException("ReviewTask", "id", taskId);
                });

        // 2. Validate task is in RUNNING state
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.error("ReviewTask {} is in {} state, expected RUNNING", taskId, task.getStatus());
            throw new IllegalStateException(
                    String.format("ReviewTask %d is in %s state, expected RUNNING", taskId, task.getStatus()));
        }

        // 3. Check for duplicate result
        if (reviewResultRepository.existsByTaskId(taskId)) {
            log.error("ReviewResult already exists for task: {}", taskId);
            throw new DuplicateResourceException("ReviewResult", "taskId", String.valueOf(taskId));
        }

        // 4. Compute statistics from issues
        List<ReviewIssue> issues = reviewResult.getIssues() != null ? reviewResult.getIssues() : List.of();
        ReviewStatisticsDTO statistics = ReviewResultMapper.calculateStatistics(issues);

        // 5. Serialize JSONB fields
        String issuesJson = ReviewResultMapper.serializeIssues(issues);
        String statisticsJson = ReviewResultMapper.serializeStatistics(statistics);
        String metadataJson = ReviewResultMapper.serializeMetadata(reviewResult.getMetadata());

        // 6. Build and persist entity
        ReviewResultEntity entity = ReviewResultEntity.builder()
                .reviewTask(task)
                .issues(issuesJson)
                .statistics(statisticsJson)
                .metadata(metadataJson)
                .success(reviewResult.isSuccess())
                .errorMessage(reviewResult.getErrorMessage())
                .build();

        ReviewResultEntity saved = reviewResultRepository.save(entity);
        log.info("Review result persisted with id: {} for task: {}", saved.getId(), taskId);

        // 7. Update task status to COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        reviewTaskRepository.save(task);
        log.info("ReviewTask {} status updated to COMPLETED", taskId);

        // 8. Build and return DTO
        return ReviewResultMapper.toDTO(saved, issues, statistics, reviewResult.getMetadata());
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResultDTO getResultByTaskId(Long taskId) {
        log.debug("Fetching review result for task: {}", taskId);

        ReviewResultEntity entity = reviewResultRepository.findByReviewTaskId(taskId)
                .orElseThrow(() -> {
                    log.error("ReviewResult not found for task: {}", taskId);
                    return new ResourceNotFoundException("ReviewResult", "taskId", taskId);
                });

        List<ReviewIssue> issues = ReviewResultMapper.deserializeIssues(entity.getIssues());
        ReviewStatisticsDTO statistics = ReviewResultMapper.deserializeStatistics(entity.getStatistics());
        ReviewMetadata metadata = ReviewResultMapper.deserializeMetadata(entity.getMetadata());

        return ReviewResultMapper.toDTO(entity, issues, statistics, metadata);
    }
}
