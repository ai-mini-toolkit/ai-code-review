package com.aicodereview.integration.git;

/**
 * Service for posting comments on AWS CodeCommit Pull Requests.
 *
 * @since 7.2.0
 */
public interface AWSCodeCommitCommentService {

    /**
     * Posts a comment on an AWS CodeCommit Pull Request.
     *
     * @param repoUrl      the repository URL
     * @param prId         the pull request ID
     * @param markdownBody the comment body in Markdown format
     * @return the created comment ID, or null if posting failed
     */
    String postComment(String repoUrl, String prId, String markdownBody);
}
