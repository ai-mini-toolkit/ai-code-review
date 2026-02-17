package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CheckRunOutputDTO;
import com.aicodereview.common.dto.checkrun.CheckRunResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementation of GitHubCheckRunService using the GitHub Check Runs API.
 * <p>
 * Creates a completed Check Run in a single POST request. Uses the same
 * HttpClient bean and token configuration as GitHubApiClient.
 * </p>
 *
 * @since 6.3.0
 */
@Slf4j
@Component
public class GitHubCheckRunServiceImpl implements GitHubCheckRunService {

    private static final String API_BASE = "https://api.github.com";
    private static final String ACCEPT_JSON = "application/vnd.github+json";
    private static final String CHECK_RUN_NAME = "AI Code Review";
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int MAX_SUMMARY_LENGTH = 65535;

    private final HttpClient httpClient;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public GitHubCheckRunServiceImpl(
            HttpClient httpClient,
            @Value("${git.platform.github.token:}") String accessToken) {
        this.httpClient = httpClient;
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CheckRunResponseDTO createCompletedCheckRun(String repoUrl,
                                                        String commitHash,
                                                        ReviewStatisticsDTO statistics,
                                                        ThresholdValidationResultDTO thresholdResult) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("GitHub token not configured, skipping Check Run creation for {}", repoUrl);
            return null;
        }

        String ownerRepo = parseOwnerRepo(repoUrl);
        String conclusion = mapConclusion(thresholdResult);
        CheckRunOutputDTO output = buildOutput(statistics, thresholdResult);

        String url = String.format("%s/repos/%s/check-runs", API_BASE, ownerRepo);

        try {
            String requestBody = buildRequestBody(commitHash, conclusion, output);

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
                CheckRunResponseDTO result = parseResponse(response.body(), conclusion);
                log.info("GitHub Check Run created: id={}, conclusion={}, url={}",
                        result.getId(), result.getConclusion(), result.getHtmlUrl());
                return result;
            }

            log.warn("GitHub Check Runs API returned HTTP {}: {} for {}",
                    status, response.body(), url);
            return null;

        } catch (IOException e) {
            log.warn("Failed to create GitHub Check Run for {}: {}", repoUrl, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitHub Check Run creation interrupted for {}", repoUrl);
            return null;
        }
    }

    /**
     * Maps threshold validation result to GitHub Check Run conclusion.
     */
    String mapConclusion(ThresholdValidationResultDTO thresholdResult) {
        if (thresholdResult.isPassed()) {
            return "success";
        }
        if ("BLOCK_MERGE".equals(thresholdResult.getAction())) {
            return "failure";
        }
        return "neutral";
    }

    /**
     * Builds the Check Run output with review summary in Markdown format.
     */
    CheckRunOutputDTO buildOutput(ReviewStatisticsDTO statistics,
                                   ThresholdValidationResultDTO thresholdResult) {
        String status = thresholdResult.isPassed() ? "Passed" : "Failed";
        String title = CHECK_RUN_NAME + " \u2014 " + status;

        StringBuilder summary = new StringBuilder();
        summary.append("### Review Summary\n\n");
        summary.append("**Total Issues:** ").append(statistics.getTotal()).append("\n\n");

        // Severity breakdown table
        Map<String, Integer> bySeverity = statistics.getBySeverity();
        if (bySeverity != null && !bySeverity.isEmpty()) {
            summary.append("| Severity | Count |\n");
            summary.append("|----------|-------|\n");
            for (Map.Entry<String, Integer> entry : bySeverity.entrySet()) {
                if (entry.getValue() > 0) {
                    summary.append("| ").append(entry.getKey())
                            .append(" | ").append(entry.getValue()).append(" |\n");
                }
            }
            summary.append("\n");
        }

        // Threshold violations
        List<ThresholdViolationDTO> violations = thresholdResult.getViolations();
        if (violations != null && !violations.isEmpty()) {
            summary.append("### Threshold Violations\n\n");
            for (ThresholdViolationDTO v : violations) {
                summary.append("- **").append(v.getRule()).append("** \u2014 actual: ")
                        .append(v.getActual()).append(", limit: ")
                        .append(v.getThreshold()).append("\n");
            }
            summary.append("\n**Action:** ").append(thresholdResult.getAction()).append("\n");
        }

        String summaryText = summary.toString();
        if (summaryText.length() > MAX_SUMMARY_LENGTH) {
            summaryText = summaryText.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
        }

        return CheckRunOutputDTO.builder()
                .title(title)
                .summary(summaryText)
                .text("")
                .build();
    }

    private String buildRequestBody(String commitHash, String conclusion, CheckRunOutputDTO output) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("name", CHECK_RUN_NAME);
            root.put("head_sha", commitHash);
            root.put("status", "completed");
            root.put("conclusion", conclusion);
            root.put("completed_at", Instant.now().toString());

            ObjectNode outputNode = objectMapper.createObjectNode();
            outputNode.put("title", output.getTitle());
            outputNode.put("summary", output.getSummary());
            outputNode.put("text", output.getText() != null ? output.getText() : "");
            root.set("output", outputNode);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Check Run request body", e);
        }
    }

    private CheckRunResponseDTO parseResponse(String responseBody, String conclusion) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            return CheckRunResponseDTO.builder()
                    .id(json.has("id") ? json.get("id").asLong() : null)
                    .htmlUrl(json.has("html_url") ? json.get("html_url").asText() : null)
                    .conclusion(conclusion)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Check Run response, returning minimal result");
            return CheckRunResponseDTO.builder().conclusion(conclusion).build();
        }
    }

    /**
     * Extracts "owner/repo" from a GitHub repository URL.
     * Delegates to shared utility {@link GitHubUrlUtils#parseOwnerRepo(String)}.
     */
    String parseOwnerRepo(String repoUrl) {
        return GitHubUrlUtils.parseOwnerRepo(repoUrl);
    }
}
