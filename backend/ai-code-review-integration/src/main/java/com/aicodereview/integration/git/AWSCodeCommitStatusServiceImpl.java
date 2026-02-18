package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of AWSCodeCommitStatusService.
 * <p>
 * Full implementation deferred to a future release. Logs a warning
 * and returns null without throwing exceptions.
 * </p>
 *
 * @since 6.4.0
 */
@Slf4j
@Component
public class AWSCodeCommitStatusServiceImpl implements AWSCodeCommitStatusService {

    @Override
    public CommitStatusResponseDTO updateCommitStatus(String repoUrl,
                                                       String commitHash,
                                                       ReviewStatisticsDTO statistics,
                                                       ThresholdValidationResultDTO thresholdResult) {
        log.warn("AWS CodeCommit status update not yet implemented, skipping for {}", repoUrl);
        return null;
    }
}
