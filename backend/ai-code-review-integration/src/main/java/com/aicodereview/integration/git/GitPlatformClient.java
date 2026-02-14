package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;

/**
 * Interface for retrieving file content and diffs from Git hosting platforms.
 */
public interface GitPlatformClient {

    /**
     * Retrieves the content of a file at a specific commit.
     *
     * @param repoUrl    the repository URL
     * @param commitHash the commit SHA
     * @param filePath   the file path within the repository
     * @return the file content as a string
     */
    String getFileContent(String repoUrl, String commitHash, String filePath);

    /**
     * Retrieves the unified diff for a single commit.
     *
     * @param repoUrl    the repository URL
     * @param commitHash the commit SHA
     * @return the unified diff content
     */
    String getDiff(String repoUrl, String commitHash);

    /**
     * Retrieves the unified diff comparing two branches.
     *
     * @param repoUrl    the repository URL
     * @param baseBranch the base branch name
     * @param headBranch the head branch name
     * @return the unified diff content
     */
    String getDiff(String repoUrl, String baseBranch, String headBranch);

    /**
     * Returns the platform this client supports.
     */
    GitPlatform getPlatform();
}
