package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;

/**
 * Service for updating AWS CodeCommit PR approval status.
 * <p>
 * Currently a stub — full implementation deferred to a future release
 * (matching AWSCodeCommitClient stub pattern).
 * </p>
 *
 * @since 6.4.0
 */
public interface AWSCodeCommitStatusService {

    /**
     * Updates AWS CodeCommit PR approval status for the given review result.
     *
     * @param repoUrl          the repository URL
     * @param commitHash       the commit SHA
     * @param statistics       computed review statistics
     * @param thresholdResult  threshold validation result
     * @return response DTO; or null if not implemented / on failure
     */
    CommitStatusResponseDTO updateCommitStatus(String repoUrl,
                                                String commitHash,
                                                ReviewStatisticsDTO statistics,
                                                ThresholdValidationResultDTO thresholdResult);
}
