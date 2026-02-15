package com.aicodereview.integration.ai.anthropic;

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
 * Anthropic Claude AI provider implementation.
 * <p>
 * Integrates with the Anthropic Messages API ({@code /v1/messages}).
 * Key differences from OpenAI: {@code x-api-key} header, {@code anthropic-version} header,
 * top-level {@code system} parameter, and {@code content[0].text} response format.
 * </p>
 *
 * @since 4.3.0
 */
@Component
@Slf4j
public class AnthropicProvider implements AIProvider {

    private static final String PROVIDER_ID = "anthropic";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final int MAX_RETRIES = 2;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final int timeoutSeconds;
    private final String anthropicVersion;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(
            HttpClient httpClient,
            @Value("${ai.provider.anthropic.api-key:}") String apiKey,
            @Value("${ai.provider.anthropic.api-endpoint:}") String apiEndpoint,
            @Value("${ai.provider.anthropic.model:claude-sonnet-4-5-20250929}") String model,
            @Value("${ai.provider.anthropic.max-tokens:4000}") int maxTokens,
            @Value("${ai.provider.anthropic.temperature:0.3}") double temperature,
            @Value("${ai.provider.anthropic.timeout-seconds:60}") int timeoutSeconds,
            @Value("${ai.provider.anthropic.api-version:2023-06-01}") String anthropicVersion) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.baseUrl = resolveBaseUrl(apiEndpoint);
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.anthropicVersion = anthropicVersion;
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
            log.error("Unexpected error during Anthropic API call", e);
            throw new AIProviderException("Unexpected error during Anthropic API call: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty()
                && anthropicVersion != null && !anthropicVersion.isEmpty();
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
        if (apiEndpoint.endsWith("/")) {
            return apiEndpoint.substring(0, apiEndpoint.length() - 1);
        }
        return apiEndpoint;
    }

    private String buildRequestBody(CodeContext context, String renderedPrompt) {
        try {
            String userMessage = buildUserMessage(context);

            // Anthropic: system is a top-level string, NOT in messages array
            Map<String, Object> requestMap = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "temperature", temperature,
                    "system", renderedPrompt,
                    "messages", List.of(
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
                        .uri(URI.create(baseUrl + MESSAGES_PATH))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", anthropicVersion)
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
                    log.error("Anthropic API authentication failed: HTTP {}, body={}", status, response.body());
                    throw new AIAuthenticationException(
                            "Anthropic API authentication failed (401): " + response.body());
                }
                if (status == 403) {
                    log.error("Anthropic API permission denied: HTTP {}, body={}", status, response.body());
                    throw new AIProviderException(status,
                            "Anthropic API permission denied (403): " + response.body());
                }
                if (status == 404) {
                    log.error("Anthropic API endpoint not found: HTTP {}, body={}", status, response.body());
                    throw new AIProviderException(status,
                            "Anthropic API endpoint not found (404): " + response.body());
                }

                // Retryable errors (429, 5xx, 529)
                if ((status == 429 || status >= 500) && attempt < MAX_RETRIES) {
                    attempt++;
                    long delay = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("Anthropic API returned {}, retrying ({}/{}) after {}ms",
                            status, attempt, MAX_RETRIES, delay);
                    Thread.sleep(delay);
                    continue;
                }

                // Max retries exhausted or non-retryable status
                log.error("Anthropic API final failure: HTTP {}, attempts={}, body={}",
                        status, attempt + 1, response.body());
                if (status == 429) {
                    throw new RateLimitException(
                            "Anthropic API rate limit exceeded after " + (attempt + 1) + " attempts: " + response.body());
                }
                if (status == 408 || status == 504) {
                    throw new AITimeoutException(
                            "Anthropic API timeout (" + status + "): " + response.body());
                }
                throw new AIProviderException(status,
                        "Anthropic API error: HTTP " + status + ": " + response.body());

            } catch (AIProviderException e) {
                throw e;
            } catch (HttpTimeoutException e) {
                throw new AITimeoutException("Anthropic API request timed out", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIProviderException("Anthropic API request interrupted", e);
            } catch (IOException e) {
                throw new AIProviderException("Anthropic API request failed: " + e.getMessage(), e);
            }
        }
    }

    private ReviewResult parseResponse(String responseBody, long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Anthropic response: content[0].text (NOT choices[0].message.content)
            String content = root.path("content").path(0).path("text").asText();

            // Anthropic usage: input_tokens / output_tokens (NOT prompt_tokens / completion_tokens)
            int inputTokens = root.path("usage").path("input_tokens").asInt(0);
            int outputTokens = root.path("usage").path("output_tokens").asInt(0);

            // Parse structured issues from AI content
            JsonNode contentJson = objectMapper.readTree(content);
            List<ReviewIssue> issues = objectMapper.convertValue(
                    contentJson.path("issues"),
                    new TypeReference<List<ReviewIssue>>() {});

            ReviewMetadata metadata = ReviewMetadata.builder()
                    .providerId(PROVIDER_ID)
                    .model(model)
                    .promptTokens(inputTokens)
                    .completionTokens(outputTokens)
                    .durationMs(durationMs)
                    .build();

            log.info("Anthropic review completed: model={}, inputTokens={}, outputTokens={}, duration={}ms, issues={}",
                    model, inputTokens, outputTokens, durationMs, issues.size());

            return ReviewResult.success(issues, metadata);

        } catch (Exception e) {
            log.error("Failed to parse Anthropic API response: {}", e.getMessage());
            return ReviewResult.failed("Failed to parse Anthropic API response: " + e.getMessage());
        }
    }
}
