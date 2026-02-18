package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
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
 * Implementation of GitLabCommitStatusService using the GitLab Commit Status API.
 * <p>
 * Creates a commit status via POST /api/v4/projects/{id}/statuses/{sha}.
 * Uses the same HttpClient bean and token configuration as GitLabApiClient.
 * </p>
 *
 * @since 6.4.0
 */
@Slf4j
@Component
public class GitLabCommitStatusServiceImpl implements GitLabCommitStatusService {

    private static final String STATUS_NAME = "AI Code Review";
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final HttpClient httpClient;
    private final String accessToken;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public GitLabCommitStatusServiceImpl(
            HttpClient httpClient,
            @Value("${git.platform.gitlab.token:}") String accessToken,
            @Value("${git.platform.gitlab.base-url:https://gitlab.com}") String baseUrl) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CommitStatusResponseDTO updateCommitStatus(String repoUrl,
                                                       String commitHash,
                                                       ReviewStatisticsDTO statistics,
                                                       ThresholdValidationResultDTO thresholdResult) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("GitLab token not configured, skipping Commit Status update for {}", repoUrl);
            return null;
        }

        String projectId = parseProjectPath(repoUrl);
        String state = mapState(thresholdResult);
        String description = buildDescription(statistics, thresholdResult);

        String url = String.format("%s/api/v4/projects/%s/statuses/%s",
                baseUrl, projectId, commitHash);

        try {
            String requestBody = buildRequestBody(state, description);

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
                CommitStatusResponseDTO result = parseResponse(response.body(), state);
                log.info("GitLab Commit Status created: id={}, state={}, repo={}",
                        result.getId(), result.getState(), repoUrl);
                return result;
            }

            log.warn("GitLab Commit Status API returned HTTP {}: {} for {}",
                    status, response.body(), url);
            return null;

        } catch (IOException e) {
            log.warn("Failed to update GitLab Commit Status for {}: {}", repoUrl, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitLab Commit Status update interrupted for {}", repoUrl);
            return null;
        }
    }

    /**
     * Maps threshold validation result to GitLab Commit Status state.
     */
    String mapState(ThresholdValidationResultDTO thresholdResult) {
        if (thresholdResult.isPassed()) {
            return "success";
        }
        if ("BLOCK_MERGE".equals(thresholdResult.getAction())) {
            return "failed";
        }
        // WARN_ONLY: use success state with warning in description
        return "success";
    }

    /**
     * Builds the commit status description (max 255 characters).
     */
    String buildDescription(ReviewStatisticsDTO statistics,
                             ThresholdValidationResultDTO thresholdResult) {
        int violationCount = thresholdResult.getViolations() != null
                ? thresholdResult.getViolations().size() : 0;

        String desc;
        if (thresholdResult.isPassed()) {
            desc = String.format("AI Code Review passed \u2014 %d issues found", statistics.getTotal());
        } else if ("WARN_ONLY".equals(thresholdResult.getAction())) {
            desc = String.format("AI Code Review warning \u2014 %d threshold violations, %d issues found",
                    violationCount, statistics.getTotal());
        } else {
            desc = String.format("AI Code Review failed \u2014 %d threshold violations",
                    violationCount);
        }

        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
        }
        return desc;
    }

    String buildRequestBody(String state, String description) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("state", state);
            root.put("name", STATUS_NAME);
            root.put("description", description);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Commit Status request body", e);
        }
    }

    private CommitStatusResponseDTO parseResponse(String responseBody, String state) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            return CommitStatusResponseDTO.builder()
                    .id(json.has("id") ? json.get("id").asLong() : null)
                    .state(state)
                    .description(json.has("description") ? json.get("description").asText() : null)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Commit Status response, returning minimal result");
            return CommitStatusResponseDTO.builder().state(state).build();
        }
    }

    /**
     * Extracts URL-encoded project path from a GitLab repository URL.
     * Delegates to shared utility {@link GitLabUrlUtils#parseProjectPath(String)}.
     */
    String parseProjectPath(String repoUrl) {
        return GitLabUrlUtils.parseProjectPath(repoUrl);
    }
}
