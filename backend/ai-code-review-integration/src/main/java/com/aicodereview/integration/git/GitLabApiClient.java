package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import com.aicodereview.common.exception.GitApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitLab REST API client for retrieving file content and diffs.
 */
@Component
@Slf4j
public class GitLabApiClient implements GitPlatformClient {

    private static final int MAX_RETRIES = 2;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final String accessToken;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public GitLabApiClient(
            HttpClient httpClient,
            @Value("${git.platform.gitlab.token:}") String accessToken,
            @Value("${git.platform.gitlab.base-url:https://gitlab.com}") String baseUrl) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getFileContent(String repoUrl, String commitHash, String filePath) {
        String projectId = parseProjectPath(repoUrl);
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        String url = String.format("%s/api/v4/projects/%s/repository/files/%s/raw?ref=%s",
                baseUrl, projectId, encodedPath, commitHash);
        return executeWithRetry(url);
    }

    @Override
    public String getDiff(String repoUrl, String commitHash) {
        String projectId = parseProjectPath(repoUrl);
        String url = String.format("%s/api/v4/projects/%s/repository/commits/%s/diff",
                baseUrl, projectId, commitHash);
        String responseBody = executeWithRetry(url);
        return assembleUnifiedDiff(responseBody);
    }

    @Override
    public String getDiff(String repoUrl, String baseBranch, String headBranch) {
        String projectId = parseProjectPath(repoUrl);
        String encodedBase = URLEncoder.encode(baseBranch, StandardCharsets.UTF_8);
        String encodedHead = URLEncoder.encode(headBranch, StandardCharsets.UTF_8);
        String url = String.format("%s/api/v4/projects/%s/repository/compare?from=%s&to=%s",
                baseUrl, projectId, encodedBase, encodedHead);
        String responseBody = executeWithRetry(url);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode diffs = root.get("diffs");
            if (diffs == null || !diffs.isArray()) {
                return "";
            }
            return assembleUnifiedDiffFromArray(diffs);
        } catch (Exception e) {
            throw new GitApiException("Failed to parse GitLab compare response", e);
        }
    }

    @Override
    public GitPlatform getPlatform() {
        return GitPlatform.GITLAB;
    }

    String parseProjectPath(String repoUrl) {
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

    private String assembleUnifiedDiff(String responseBody) {
        try {
            JsonNode diffArray = objectMapper.readTree(responseBody);
            if (!diffArray.isArray()) {
                return "";
            }
            return assembleUnifiedDiffFromArray(diffArray);
        } catch (Exception e) {
            throw new GitApiException("Failed to parse GitLab diff response", e);
        }
    }

    String assembleUnifiedDiffFromArray(JsonNode diffArray) {
        StringBuilder unified = new StringBuilder();
        for (JsonNode file : diffArray) {
            String oldPath = file.path("old_path").asText("");
            String newPath = file.path("new_path").asText("");
            if (oldPath.isEmpty() && newPath.isEmpty()) {
                log.warn("Skipping diff entry with no old_path or new_path");
                continue;
            }
            boolean isNew = file.path("new_file").asBoolean(false);
            boolean isDeleted = file.path("deleted_file").asBoolean(false);
            boolean isRenamed = file.path("renamed_file").asBoolean(false);

            unified.append("diff --git a/").append(oldPath)
                    .append(" b/").append(newPath).append("\n");

            if (isNew) {
                unified.append("new file mode 100644\n");
            }
            if (isDeleted) {
                unified.append("deleted file mode 100644\n");
            }
            if (isRenamed) {
                unified.append("rename from ").append(oldPath).append("\n");
                unified.append("rename to ").append(newPath).append("\n");
            }

            unified.append("--- ")
                    .append(isNew ? "/dev/null" : "a/" + oldPath).append("\n");
            unified.append("+++ ")
                    .append(isDeleted ? "/dev/null" : "b/" + newPath).append("\n");

            String diffContent = file.path("diff").asText("");
            if (!diffContent.isEmpty()) {
                unified.append(diffContent);
                if (!diffContent.endsWith("\n")) {
                    unified.append("\n");
                }
            }
        }
        return unified.toString();
    }

    private String executeWithRetry(String url) {
        int attempt = 0;
        while (true) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                        .GET();

                if (accessToken != null && !accessToken.isEmpty()) {
                    builder.header("PRIVATE-TOKEN", accessToken);
                }

                HttpResponse<String> response = httpClient.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }

                if ((status == 429 || status >= 500) && attempt < MAX_RETRIES) {
                    attempt++;
                    long delay = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("GitLab API returned {}, retrying ({}/{}) after {}ms",
                            status, attempt, MAX_RETRIES, delay);
                    Thread.sleep(delay);
                    continue;
                }

                throw new GitApiException(status,
                        String.format("GitLab API error: HTTP %d for %s", status, url));

            } catch (GitApiException e) {
                throw e;
            } catch (java.net.http.HttpTimeoutException e) {
                throw new GitApiException("GitLab API request timed out: " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GitApiException("GitLab API request interrupted", e);
            } catch (IOException e) {
                throw new GitApiException("GitLab API request failed: " + url, e);
            }
        }
    }
}
