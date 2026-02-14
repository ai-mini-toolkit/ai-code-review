package com.aicodereview.common.enums;

/**
 * Supported Git hosting platforms.
 */
public enum GitPlatform {
    GITHUB,
    GITLAB,
    AWS_CODECOMMIT;

    /**
     * Detects the Git platform from a repository URL.
     *
     * @param repoUrl the repository URL, or null
     * @return the detected GitPlatform, or null if not recognized
     */
    public static GitPlatform fromRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isEmpty()) {
            return null;
        }
        String lower = repoUrl.toLowerCase();
        if (lower.contains("github.com")) {
            return GITHUB;
        }
        if (lower.contains("gitlab")) {
            return GITLAB;
        }
        if (lower.contains("codecommit")) {
            return AWS_CODECOMMIT;
        }
        return null;
    }
}
