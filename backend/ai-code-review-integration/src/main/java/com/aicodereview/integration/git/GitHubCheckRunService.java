package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CheckRunResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;

/**
 * Service for creating GitHub Check Runs to display review results on PRs.
 * <p>
 * Creates a completed Check Run via the GitHub Check Runs API with
 * conclusion based on threshold validation and output containing review statistics.
 * </p>
 *
 * @since 6.3.0
 */
public interface GitHubCheckRunService {

    /**
     * Creates a completed GitHub Check Run for the given review task.
     *
     * @param repoUrl          the repository URL (e.g., "https://github.com/owner/repo")
     * @param commitHash       the commit SHA to attach the check run to
     * @param statistics       computed review statistics
     * @param thresholdResult  threshold validation result (determines conclusion)
     * @return response with check run ID, URL, and conclusion
     */
    CheckRunResponseDTO createCompletedCheckRun(String repoUrl,
                                                 String commitHash,
                                                 ReviewStatisticsDTO statistics,
                                                 ThresholdValidationResultDTO thresholdResult);
}
