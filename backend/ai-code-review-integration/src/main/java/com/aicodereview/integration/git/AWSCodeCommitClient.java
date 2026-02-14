package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import org.springframework.stereotype.Component;

/**
 * AWS CodeCommit API client stub.
 * Full implementation deferred to a future release.
 */
@Component
public class AWSCodeCommitClient implements GitPlatformClient {

    @Override
    public String getFileContent(String repoUrl, String commitHash, String filePath) {
        throw new UnsupportedOperationException(
                "AWS CodeCommit API client not yet implemented");
    }

    @Override
    public String getDiff(String repoUrl, String commitHash) {
        throw new UnsupportedOperationException(
                "AWS CodeCommit API client not yet implemented");
    }

    @Override
    public String getDiff(String repoUrl, String baseBranch, String headBranch) {
        throw new UnsupportedOperationException(
                "AWS CodeCommit API client not yet implemented");
    }

    @Override
    public GitPlatform getPlatform() {
        return GitPlatform.AWS_CODECOMMIT;
    }
}
