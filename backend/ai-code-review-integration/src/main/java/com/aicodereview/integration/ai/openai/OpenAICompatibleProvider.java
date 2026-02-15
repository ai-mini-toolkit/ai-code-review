package com.aicodereview.integration.ai.openai;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.exception.AIAuthenticationException;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.common.exception.AITimeoutException;
import com.aicodereview.common.exception.RateLimitException;
import com.aicodereview.integration.ai.AIProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible AI provider implementation.
 * <p>
 * Supports both official OpenAI API and any OpenAI-compatible endpoint
 * (e.g., Azure OpenAI, private deployments) via configurable baseUrl.
 * Uses native {@link HttpClient} with retry logic for transient failures.
 * </p>
 *
 * @since 4.2.0
 */
@Component
@Slf4j
public class OpenAICompatibleProvider implements AIProvider {

    private static final String PROVIDER_ID = "openai";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final int MAX_RETRIES = 2;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleProvider(
            HttpClient httpClient,
            @Value("${ai.provider.openai.api-key:}") String apiKey,
            @Value("${ai.provider.openai.api-endpoint:}") String apiEndpoint,
            @Value("${ai.provider.openai.model:gpt-4}") String model,
            @Value("${ai.provider.openai.max-tokens:4000}") int maxTokens,
            @Value("${ai.provider.openai.temperature:0.3}") double temperature,
            @Value("${ai.provider.openai.timeout-seconds:30}") int timeoutSeconds) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.baseUrl = resolveBaseUrl(apiEndpoint);
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ReviewResult analyze(CodeContext context, String renderedPrompt) {
        long startTime = System.currentTimeMillis();

        try {
            String requestBody = buildRequestBody(context, renderedPrompt);
            String responseBody = executeWithRetry(requestBody);
            return parseResponse(responseBody, startTime);
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OpenAI API call", e);
            throw new AIProviderException("Unexpected error during OpenAI API call: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public int getMaxTokens() {
        return maxTokens;
    }

    private String resolveBaseUrl(String apiEndpoint) {
        if (apiEndpoint == null || apiEndpoint.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        // Strip trailing slash to avoid double-slash in URL
        if (apiEndpoint.endsWith("/")) {
            return apiEndpoint.substring(0, apiEndpoint.length() - 1);
        }
        return apiEndpoint;
    }

    private String buildRequestBody(CodeContext context, String renderedPrompt) {
        try {
            String userMessage = buildUserMessage(context);

            Map<String, Object> requestMap = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "temperature", temperature,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", renderedPrompt),
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            return objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            throw new AIProviderException("Failed to build request body: " + e.getMessage(), e);
        }
    }

    private String buildUserMessage(CodeContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.getTaskMeta() != null) {
            sb.append("## Task Info\n");
            if (context.getTaskMeta().getPrTitle() != null) {
                sb.append("PR Title: ").append(context.getTaskMeta().getPrTitle()).append("\n");
            }
            if (context.getTaskMeta().getPrDescription() != null) {
                sb.append("PR Description: ").append(context.getTaskMeta().getPrDescription()).append("\n");
            }
            if (context.getTaskMeta().getAuthor() != null) {
                sb.append("Author: ").append(context.getTaskMeta().getAuthor()).append("\n");
            }
            if (context.getTaskMeta().getBranch() != null) {
                sb.append("Branch: ").append(context.getTaskMeta().getBranch()).append("\n");
            }
            sb.append("\n");
        }

        if (context.getStatistics() != null) {
            sb.append("## Statistics\n");
            sb.append("Files changed: ").append(context.getStatistics().getTotalFilesChanged()).append("\n");
            sb.append("Lines added: ").append(context.getStatistics().getTotalLinesAdded()).append("\n");
            sb.append("Lines deleted: ").append(context.getStatistics().getTotalLinesDeleted()).append("\n\n");
        }

        if (context.getFiles() != null && !context.getFiles().isEmpty()) {
            sb.append("## Changed Files\n");
            for (FileInfo file : context.getFiles()) {
                sb.append("- ").append(file.getPath())
                        .append(" (").append(file.getChangeType())
                        .append(", ").append(file.getLanguage()).append(")\n");
            }
            sb.append("\n");
        }

        if (context.getRawDiff() != null) {
            sb.append("## Diff\n```\n").append(context.getRawDiff()).append("\n```\n\n");
        }

        if (context.getFileContents() != null && !context.getFileContents().isEmpty()) {
            sb.append("## File Contents\n");
            context.getFileContents().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sb.append("### ").append(entry.getKey()).append("\n```\n")
                            .append(entry.getValue()).append("\n```\n\n"));
        }

        return sb.toString();
    }

    private String executeWithRetry(String requestBody) {
        int attempt = 0;
        while (true) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + CHAT_COMPLETIONS_PATH))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }

                // Non-retryable errors — throw immediately
                if (status == 401) {
                    log.error("OpenAI API authentication failed: HTTP {}, body={}", status, response.body());
                    throw new AIAuthenticationException(
                            "OpenAI API authentication failed (401): " + response.body());
                }
                if (status == 403) {
                    log.error("OpenAI API access forbidden: HTTP {}, body={}", status, response.body());
                    throw new AIProviderException(status,
                            "OpenAI API access forbidden (403): " + response.body());
                }
                if (status == 404) {
                    log.error("OpenAI API endpoint not found: HTTP {}, body={}", status, response.body());
                    throw new AIProviderException(status,
                            "OpenAI API endpoint not found (404): " + response.body());
                }

                // Retryable errors (429, 5xx)
                if ((status == 429 || status >= 500) && attempt < MAX_RETRIES) {
                    attempt++;
                    long delay = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("OpenAI API returned {}, retrying ({}/{}) after {}ms",
                            status, attempt, MAX_RETRIES, delay);
                    Thread.sleep(delay);
                    continue;
                }

                // Max retries exhausted or non-retryable status
                log.error("OpenAI API final failure: HTTP {}, attempts={}, body={}",
                        status, attempt + 1, response.body());
                if (status == 429) {
                    throw new RateLimitException(
                            "OpenAI API rate limit exceeded after " + (attempt + 1) + " attempts: " + response.body());
                }
                if (status == 408 || status == 504) {
                    throw new AITimeoutException(
                            "OpenAI API timeout (" + status + "): " + response.body());
                }
                throw new AIProviderException(status,
                        "OpenAI API error: HTTP " + status + ": " + response.body());

            } catch (AIProviderException e) {
                throw e;
            } catch (HttpTimeoutException e) {
                throw new AITimeoutException("OpenAI API request timed out", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIProviderException("OpenAI API request interrupted", e);
            } catch (IOException e) {
                throw new AIProviderException("OpenAI API request failed: " + e.getMessage(), e);
            }
        }
    }

    private ReviewResult parseResponse(String responseBody, long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract AI response content
            String content = root.path("choices").path(0).path("message").path("content").asText();

            // Extract token usage
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

            // Parse structured issues from AI content
            JsonNode contentJson = objectMapper.readTree(content);
            List<ReviewIssue> issues = objectMapper.convertValue(
                    contentJson.path("issues"),
                    new TypeReference<List<ReviewIssue>>() {});

            ReviewMetadata metadata = ReviewMetadata.builder()
                    .providerId(PROVIDER_ID)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .durationMs(durationMs)
                    .build();

            log.info("OpenAI review completed: model={}, promptTokens={}, completionTokens={}, duration={}ms, issues={}",
                    model, promptTokens, completionTokens, durationMs, issues.size());

            return ReviewResult.success(issues, metadata);

        } catch (Exception e) {
            log.error("Failed to parse OpenAI API response: {}", e.getMessage());
            return ReviewResult.failed("Failed to parse OpenAI API response: " + e.getMessage());
        }
    }
}
