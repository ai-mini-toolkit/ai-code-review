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
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * GitHub REST API client for retrieving file content and diffs.
 */
@Component
@Slf4j
public class GitHubApiClient implements GitPlatformClient {

    private static final String API_BASE = "https://api.github.com";
    private static final String ACCEPT_DIFF = "application/vnd.github.diff";
    private static final String ACCEPT_JSON = "application/vnd.github+json";
    private static final int MAX_RETRIES = 2;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public GitHubApiClient(
            HttpClient httpClient,
            @Value("${git.platform.github.token:}") String accessToken) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getFileContent(String repoUrl, String commitHash, String filePath) {
        String ownerRepo = parseOwnerRepo(repoUrl);
        String encodedPath = encodeFilePath(filePath);
        String url = String.format("%s/repos/%s/contents/%s?ref=%s",
                API_BASE, ownerRepo, encodedPath, commitHash);

        String responseBody = executeWithRetry(url, ACCEPT_JSON);

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            String encoding = json.has("encoding") ? json.get("encoding").asText() : "";
            if (!"base64".equals(encoding)) {
                log.warn("Unexpected encoding '{}' for file {}", encoding, filePath);
            }
            String base64Content = json.get("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GitApiException("Failed to parse GitHub file content response", e);
        }
    }

    @Override
    public String getDiff(String repoUrl, String commitHash) {
        String ownerRepo = parseOwnerRepo(repoUrl);
        String url = String.format("%s/repos/%s/commits/%s", API_BASE, ownerRepo, commitHash);
        return executeWithRetry(url, ACCEPT_DIFF);
    }

    @Override
    public String getDiff(String repoUrl, String baseBranch, String headBranch) {
        String ownerRepo = parseOwnerRepo(repoUrl);
        String url = String.format("%s/repos/%s/compare/%s...%s",
                API_BASE, ownerRepo, baseBranch, headBranch);
        return executeWithRetry(url, ACCEPT_DIFF);
    }

    @Override
    public GitPlatform getPlatform() {
        return GitPlatform.GITHUB;
    }

    String parseOwnerRepo(String repoUrl) {
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
            if (path.isEmpty() || !path.contains("/")) {
                throw new IllegalArgumentException("Invalid GitHub repository URL: " + repoUrl);
            }
            return path;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid repository URL: " + repoUrl, e);
        }
    }

    /**
     * Encodes each segment of a file path for use in a URL,
     * preserving '/' as path separators.
     */
    private String encodeFilePath(String filePath) {
        return Arrays.stream(filePath.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    private String executeWithRetry(String url, String acceptHeader) {
        int attempt = 0;
        while (true) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", acceptHeader)
                        .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                        .GET();

                if (accessToken != null && !accessToken.isEmpty()) {
                    builder.header("Authorization", "Bearer " + accessToken);
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
                    log.warn("GitHub API returned {}, retrying ({}/{}) after {}ms",
                            status, attempt, MAX_RETRIES, delay);
                    Thread.sleep(delay);
                    continue;
                }

                throw new GitApiException(status,
                        String.format("GitHub API error: HTTP %d for %s", status, url));

            } catch (GitApiException e) {
                throw e;
            } catch (java.net.http.HttpTimeoutException e) {
                throw new GitApiException("GitHub API request timed out: " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GitApiException("GitHub API request interrupted", e);
            } catch (IOException e) {
                throw new GitApiException("GitHub API request failed: " + url, e);
            }
        }
    }
}
