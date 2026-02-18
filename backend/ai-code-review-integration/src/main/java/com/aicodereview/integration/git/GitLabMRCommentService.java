package com.aicodereview.integration.git;

/**
 * Service for posting comments (notes) on GitLab Merge Requests.
 *
 * @since 7.2.0
 */
public interface GitLabMRCommentService {

    /**
     * Posts a note on a GitLab Merge Request.
     *
     * @param repoUrl      the repository URL (e.g., "https://gitlab.com/group/project")
     * @param mrIid        the merge request IID (internal ID within the project)
     * @param markdownBody the note body in Markdown format
     * @return the created note ID, or null if posting failed
     */
    Long postComment(String repoUrl, Integer mrIid, String markdownBody);
}
