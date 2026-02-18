package com.aicodereview.integration.git;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of AWSCodeCommitCommentService.
 * <p>
 * AWS CodeCommit PR comment posting is not yet implemented.
 * This stub logs a warning and returns null. Deferred to future release.
 * </p>
 *
 * @since 7.2.0
 */
@Slf4j
@Component
public class AWSCodeCommitCommentServiceImpl implements AWSCodeCommitCommentService {

    @Override
    public String postComment(String repoUrl, String prId, String markdownBody) {
        log.warn("AWS CodeCommit PR comment not yet implemented, skipping for {} PR {}",
                repoUrl, prId);
        return null;
    }
}
