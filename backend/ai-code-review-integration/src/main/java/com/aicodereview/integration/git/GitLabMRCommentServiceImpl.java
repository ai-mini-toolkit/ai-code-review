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
 * Implementation of GitLabMRCommentService using the GitLab Merge Request Notes API.
 * <p>
 * Posts review summary notes on Merge Requests via
 * POST /api/v4/projects/{id}/merge_requests/{iid}/notes.
 * </p>
 *
 * @since 7.2.0
 */
@Slf4j
@Component
public class GitLabMRCommentServiceImpl implements GitLabMRCommentService {

    private static final int READ_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final String accessToken;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public GitLabMRCommentServiceImpl(
            HttpClient httpClient,
            @Value("${git.platform.gitlab.token:}") String accessToken,
            @Value("${git.platform.gitlab.base-url:https://gitlab.com}") String baseUrl) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Long postComment(String repoUrl, Integer mrIid, String markdownBody) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("GitLab token not configured, skipping MR comment for {}", repoUrl);
            return null;
        }

        if (mrIid == null) {
            log.warn("MR IID is null, skipping GitLab MR comment for {}", repoUrl);
            return null;
        }

        String projectPath = GitLabUrlUtils.parseProjectPath(repoUrl);
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d/notes",
                baseUrl, projectPath, mrIid);

        try {
            String requestBody = buildRequestBody(markdownBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("PRIVATE-TOKEN", accessToken)
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                Long noteId = parseNoteId(response.body());
                log.info("GitLab MR note posted: id={}, mr={}!{}", noteId, projectPath, mrIid);
                return noteId;
            }

            String truncatedBody = response.body() != null && response.body().length() > 200
                    ? response.body().substring(0, 200) + "..." : response.body();
            log.warn("GitLab MR Notes API returned HTTP {}: {} for {}",
                    status, truncatedBody, url);
            return null;

        } catch (IOException e) {
            log.warn("Failed to post GitLab MR comment for {}!{}: {}", repoUrl, mrIid, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitLab MR comment posting interrupted for {}!{}", repoUrl, mrIid);
            return null;
        }
    }

    String buildRequestBody(String markdownBody) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("body", markdownBody);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build MR note request body", e);
        }
    }

    Long parseNoteId(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            return json.has("id") ? json.get("id").asLong() : null;
        } catch (Exception e) {
            log.warn("Failed to parse MR note response");
            return null;
        }
    }
}
