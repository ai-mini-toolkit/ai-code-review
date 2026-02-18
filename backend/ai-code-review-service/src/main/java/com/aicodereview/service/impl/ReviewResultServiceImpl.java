package com.aicodereview.service.impl;

import com.aicodereview.common.dto.result.ReviewResultDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.result.ReviewSummaryDTO;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewResultEntity;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.integration.git.AWSCodeCommitStatusService;
import com.aicodereview.integration.git.GitHubCheckRunService;
import com.aicodereview.integration.git.GitLabCommitStatusService;
import com.aicodereview.service.EmailNotificationService;
import com.aicodereview.service.GitCommentNotificationService;
import com.aicodereview.service.ReviewResultService;
import com.aicodereview.service.ThresholdValidationService;
import com.aicodereview.service.mapper.ReviewResultMapper;
import org.springframework.context.annotation.Lazy;
import com.aicodereview.service.mapper.ThresholdMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ThresholdValidationService thresholdValidationService;
    private final GitHubCheckRunService gitHubCheckRunService;
    private final GitLabCommitStatusService gitLabCommitStatusService;
    private final AWSCodeCommitStatusService awsCodeCommitStatusService;
    private final EmailNotificationService emailNotificationService;
    private final GitCommentNotificationService gitCommentNotificationService;

    public ReviewResultServiceImpl(ReviewResultRepository reviewResultRepository,
                                    ReviewTaskRepository reviewTaskRepository,
                                    ThresholdValidationService thresholdValidationService,
                                    GitHubCheckRunService gitHubCheckRunService,
                                    GitLabCommitStatusService gitLabCommitStatusService,
                                    AWSCodeCommitStatusService awsCodeCommitStatusService,
                                    @Lazy EmailNotificationService emailNotificationService,
                                    @Lazy GitCommentNotificationService gitCommentNotificationService) {
        this.reviewResultRepository = reviewResultRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.thresholdValidationService = thresholdValidationService;
        this.gitHubCheckRunService = gitHubCheckRunService;
        this.gitLabCommitStatusService = gitLabCommitStatusService;
        this.awsCodeCommitStatusService = awsCodeCommitStatusService;
        this.emailNotificationService = emailNotificationService;
        this.gitCommentNotificationService = gitCommentNotificationService;
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

        // 5. Threshold validation (after statistics, before task status update)
        ThresholdValidationResultDTO thresholdResult =
                thresholdValidationService.validate(task.getProject().getId(), statistics);
        log.info("Threshold validation for task {}: passed={}", taskId, thresholdResult.isPassed());

        // 6. Serialize JSONB fields
        String issuesJson = ReviewResultMapper.serializeIssues(issues);
        String statisticsJson = ReviewResultMapper.serializeStatistics(statistics);
        String metadataJson = ReviewResultMapper.serializeMetadata(reviewResult.getMetadata());
        String thresholdResultJson = ThresholdMapper.serializeValidationResult(thresholdResult);

        // 7. Build and persist entity
        ReviewResultEntity entity = ReviewResultEntity.builder()
                .reviewTask(task)
                .issues(issuesJson)
                .statistics(statisticsJson)
                .metadata(metadataJson)
                .thresholdResult(thresholdResultJson)
                .success(reviewResult.isSuccess())
                .errorMessage(reviewResult.getErrorMessage())
                .build();

        ReviewResultEntity saved = reviewResultRepository.save(entity);
        log.info("Review result persisted with id: {} for task: {}", saved.getId(), taskId);

        // 8. Update task status to COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        reviewTaskRepository.save(task);
        log.info("ReviewTask {} status updated to COMPLETED", taskId);

        // 9. Platform status update (non-blocking)
        // TODO: Move HTTP calls outside @Transactional to avoid holding DB connection during external API calls
        if (Boolean.TRUE.equals(reviewResult.isSuccess())) {
            String platform = task.getProject().getGitPlatform();
            if ("GitHub".equalsIgnoreCase(platform)) {
                try {
                    gitHubCheckRunService.createCompletedCheckRun(
                            task.getRepoUrl(), task.getCommitHash(), statistics, thresholdResult);
                } catch (Exception e) {
                    log.warn("Failed to create GitHub Check Run for task {}: {}", taskId, e.getMessage());
                }
            } else if ("GitLab".equalsIgnoreCase(platform)) {
                try {
                    gitLabCommitStatusService.updateCommitStatus(
                            task.getRepoUrl(), task.getCommitHash(), statistics, thresholdResult);
                } catch (Exception e) {
                    log.warn("Failed to update GitLab Commit Status for task {}: {}", taskId, e.getMessage());
                }
            } else if ("AWS_CODECOMMIT".equalsIgnoreCase(platform)) {
                try {
                    awsCodeCommitStatusService.updateCommitStatus(
                            task.getRepoUrl(), task.getCommitHash(), statistics, thresholdResult);
                } catch (Exception e) {
                    log.warn("Failed to update AWS CodeCommit status for task {}: {}", taskId, e.getMessage());
                }
            }
        }

        // 10. Email notification (non-blocking)
        // Send violation notification (includes full report + violation banner) OR complete notification, not both
        try {
            if (!thresholdResult.isPassed()) {
                emailNotificationService.sendThresholdViolationNotification(taskId);
            } else {
                emailNotificationService.sendReviewCompleteNotification(taskId);
            }
        } catch (Exception e) {
            log.warn("Failed to send email notification for task {}: {}", taskId, e.getMessage());
        }

        // 11. Git platform comment notification (non-blocking, PR/MR tasks only)
        try {
            gitCommentNotificationService.postReviewComment(taskId);
        } catch (Exception e) {
            log.warn("Failed to post comment notification for task {}: {}", taskId, e.getMessage());
        }

        // 12. Build and return DTO
        return ReviewResultMapper.toDTO(saved, issues, statistics, reviewResult.getMetadata());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewSummaryDTO> listResults(Long projectId, Boolean success, Pageable pageable) {
        log.debug("Listing review results: projectId={}, success={}, pageable={}", projectId, success, pageable);

        Page<ReviewResultEntity> page;
        if (projectId != null && success != null) {
            page = reviewResultRepository.findByProjectIdAndSuccess(projectId, success, pageable);
        } else if (projectId != null) {
            page = reviewResultRepository.findByReviewTaskProjectId(projectId, pageable);
        } else if (success != null) {
            page = reviewResultRepository.findPageBySuccess(success, pageable);
        } else {
            page = reviewResultRepository.findAllWithAssociations(pageable);
        }

        return page.map(ReviewResultMapper::toSummaryDTO);
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
