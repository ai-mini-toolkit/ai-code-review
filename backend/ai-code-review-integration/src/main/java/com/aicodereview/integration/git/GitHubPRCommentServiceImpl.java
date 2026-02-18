package com.aicodereview.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Implementation of GitHubPRCommentService using the GitHub Issues Comments API.
 * <p>
 * Posts review summary comments on Pull Requests via
 * POST /repos/{owner}/{repo}/issues/{number}/comments.
 * GitHub treats PRs as a subtype of Issues, so the Issues API is used.
 * </p>
 *
 * @since 7.2.0
 */
@Slf4j
@Component
public class GitHubPRCommentServiceImpl implements GitHubPRCommentService {

    private static final String API_BASE = "https://api.github.com";
    private static final String ACCEPT_JSON = "application/vnd.github+json";
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public GitHubPRCommentServiceImpl(
            HttpClient httpClient,
            @Value("${git.platform.github.token:}") String accessToken) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Long postComment(String repoUrl, Integer prNumber, String markdownBody) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("GitHub token not configured, skipping PR comment for {}", repoUrl);
            return null;
        }

        String ownerRepo = GitHubUrlUtils.parseOwnerRepo(repoUrl);
        String url = String.format("%s/repos/%s/issues/%d/comments", API_BASE, ownerRepo, prNumber);

        try {
            String requestBody = buildRequestBody(markdownBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", ACCEPT_JSON)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                Long commentId = parseCommentId(response.body());
                log.info("GitHub PR comment posted: id={}, pr={}#{}", commentId, ownerRepo, prNumber);
                return commentId;
            }

            log.warn("GitHub Issues Comments API returned HTTP {}: {} for {}",
                    status, response.body(), url);
            return null;

        } catch (IOException e) {
            log.warn("Failed to post GitHub PR comment for {}#{}: {}", repoUrl, prNumber, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitHub PR comment posting interrupted for {}#{}", repoUrl, prNumber);
            return null;
        }
    }

    String buildRequestBody(String markdownBody) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("body", markdownBody);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build PR comment request body", e);
        }
    }

    Long parseCommentId(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            return json.has("id") ? json.get("id").asLong() : null;
        } catch (Exception e) {
            log.warn("Failed to parse PR comment response");
            return null;
        }
    }
}
