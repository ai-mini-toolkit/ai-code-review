package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;

/**
 * Service for updating GitLab Commit Status to display review results on MRs.
 * <p>
 * Creates a commit status via the GitLab Commit Status API with state
 * based on threshold validation and description containing review statistics.
 * </p>
 *
 * @since 6.4.0
 */
public interface GitLabCommitStatusService {

    /**
     * Updates GitLab commit status for the given review result.
     *
     * @param repoUrl          the repository URL (e.g., "https://gitlab.com/owner/repo")
     * @param commitHash       the commit SHA to attach the status to
     * @param statistics       computed review statistics
     * @param thresholdResult  threshold validation result (determines state)
     * @return response with status ID, state, and description; or null on failure
     */
    CommitStatusResponseDTO updateCommitStatus(String repoUrl,
                                                String commitHash,
                                                ReviewStatisticsDTO statistics,
                                                ThresholdValidationResultDTO thresholdResult);
}
