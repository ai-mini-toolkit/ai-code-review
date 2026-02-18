package com.aicodereview.integration.git;

/**
 * Service for posting comments on GitHub Pull Requests.
 *
 * @since 7.2.0
 */
public interface GitHubPRCommentService {

    /**
     * Posts a comment on a GitHub Pull Request.
     *
     * @param repoUrl    the repository URL (e.g., "https://github.com/owner/repo")
     * @param prNumber   the pull request number
     * @param markdownBody the comment body in Markdown format
     * @return the created comment ID, or null if posting failed
     */
    Long postComment(String repoUrl, Integer prNumber, String markdownBody);
}
