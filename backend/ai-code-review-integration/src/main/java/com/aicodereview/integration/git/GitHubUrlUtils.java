package com.aicodereview.integration.git;

import java.net.URI;

/**
 * Shared utility for GitHub URL parsing.
 * <p>
 * Extracts owner/repo from GitHub repository URLs. Used by both
 * GitHubApiClient and GitHubCheckRunServiceImpl.
 * </p>
 *
 * @since 6.3.0
 */
public final class GitHubUrlUtils {

    private GitHubUrlUtils() {
        // utility class
    }

    /**
     * Extracts "owner/repo" from a GitHub repository URL.
     *
     * @param repoUrl the repository URL (e.g., "https://github.com/owner/repo")
     * @return owner/repo string (e.g., "owner/repo")
     * @throws IllegalArgumentException if the URL is null, empty, or invalid
     */
    public static String parseOwnerRepo(String repoUrl) {
        if (repoUrl == null || repoUrl.isEmpty()) {
            throw new IllegalArgumentException("Repository URL must not be null or empty");
        }
        URI uri = URI.create(repoUrl);
        String path = uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.isEmpty() || !path.contains("/")) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repoUrl);
        }
        return path;
    }
}
