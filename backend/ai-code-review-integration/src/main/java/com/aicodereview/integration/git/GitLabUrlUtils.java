package com.aicodereview.integration.git;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared utility for GitLab URL parsing.
 * <p>
 * Extracts URL-encoded project path from GitLab repository URLs.
 * Used by both GitLabApiClient and GitLabCommitStatusServiceImpl.
 * </p>
 *
 * @since 6.4.0
 */
public final class GitLabUrlUtils {

    private GitLabUrlUtils() {
        // utility class
    }

    /**
     * Extracts URL-encoded project path from a GitLab repository URL.
     *
     * @param repoUrl the repository URL (e.g., "https://gitlab.com/owner/repo")
     * @return URL-encoded project path (e.g., "owner%2Frepo")
     * @throws IllegalArgumentException if the URL is null, empty, or invalid
     */
    public static String parseProjectPath(String repoUrl) {
        if (repoUrl == null || repoUrl.isEmpty()) {
            throw new IllegalArgumentException("Repository URL must not be null or empty");
        }
        try {
            URI uri = URI.create(repoUrl);
            String path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Invalid GitLab repository URL: " + repoUrl);
            }
            return URLEncoder.encode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid repository URL: " + repoUrl, e);
        }
    }
}
