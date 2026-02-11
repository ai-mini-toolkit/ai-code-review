package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.ErrorCode;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST Controller for receiving webhooks from Git platforms (GitHub, GitLab, AWS CodeCommit).
 * <p>
 * This controller handles webhook events, verifies signatures, and enqueues code review tasks.
 * </p>
 *
 * <h3>Processing Flow:</h3>
 * <ol>
 *   <li>Validate platform parameter (github, gitlab, codecommit)</li>
 *   <li>Extract platform-specific signature from headers</li>
 *   <li>Verify signature using WebhookVerificationChain (BEFORE parsing JSON)</li>
 *   <li>Parse JSON payload</li>
 *   <li>Validate required fields based on platform</li>
 *   <li>Enqueue task to Redis (stub for Story 2.5)</li>
 *   <li>Return 202 Accepted</li>
 * </ol>
 *
 * @since 2.4.0
 * @author AI Code Review System
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    // Platform-specific signature header names
    private static final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String GITLAB_TOKEN_HEADER = "X-Gitlab-Token";

    // Supported platforms
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("github", "gitlab", "codecommit");

    // ObjectMapper for JSON parsing (thread-safe)
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebhookVerificationChain verificationChain;

    // TODO: Inject ProjectConfigService to fetch webhook secrets from database (Story 1.5)
    // For now, using hardcoded test secrets in development

    /**
     * Constructor injection of dependencies.
     *
     * @param verificationChain the webhook signature verification chain
     */
    public WebhookController(WebhookVerificationChain verificationChain) {
        this.verificationChain = verificationChain;
    }

    /**
     * Receives webhook events from Git platforms.
     *
     * @param platform the Git platform name (github, gitlab, codecommit)
     * @param payload  the raw webhook payload (JSON string)
     * @param headers  all HTTP request headers
     * @return 202 Accepted with acknowledgment message, or error response
     */
    @PostMapping("/{platform}")
    public ResponseEntity<ApiResponse<String>> receiveWebhook(
            @PathVariable(value = "platform") String platform,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {

        log.info("Received webhook from platform: {}", platform);

        // Step 1: Validate platform parameter
        if (!isPlatformSupported(platform)) {
            log.warn("Unsupported platform: {}", platform);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST,
                            "Unsupported platform: " + platform));
        }

        // Step 2: Extract platform-specific signature from headers
        String signature = extractSignature(platform, headers);
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook signature missing for platform: {}", platform);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED,
                            "Webhook signature missing"));
        }

        // Step 3: Verify signature BEFORE parsing JSON (security critical)
        String secret = getWebhookSecret(platform);
        boolean isValid = verificationChain.verify(platform, payload, signature, secret);
        if (!isValid) {
            log.warn("Webhook signature verification failed for platform: {}", platform);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED,
                            "Invalid webhook signature"));
        }

        // Step 4: Parse JSON payload
        JsonNode event;
        try {
            event = parsePayload(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse webhook payload as JSON: {}", e.getMessage());
            return ResponseEntity.status(422)
                    .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR,
                            "Invalid JSON payload: " + e.getMessage()));
        }

        // Step 5: Validate required fields based on platform
        try {
            validateEvent(platform, event);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook event validation failed: {}", e.getMessage());
            return ResponseEntity.status(422)
                    .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR,
                            e.getMessage()));
        }

        // Step 6: Enqueue task to Redis (stub for Story 2.5)
        enqueueTask(platform, event);

        // Step 7: Return 202 Accepted
        log.info("Webhook processed successfully for platform: {}", platform);
        return ResponseEntity.status(202)
                .body(ApiResponse.success("Webhook received and task enqueued"));
    }

    /**
     * Checks if the platform is supported.
     *
     * @param platform the platform name
     * @return true if platform is supported, false otherwise
     */
    private boolean isPlatformSupported(String platform) {
        return platform != null && SUPPORTED_PLATFORMS.contains(platform.toLowerCase());
    }

    /**
     * Extracts platform-specific signature from request headers.
     *
     * @param platform the platform name
     * @param headers  all request headers (case-insensitive map)
     * @return the signature string, or null if not found
     */
    private String extractSignature(String platform, Map<String, String> headers) {
        return switch (platform.toLowerCase()) {
            case "github" -> getHeaderCaseInsensitive(headers, GITHUB_SIGNATURE_HEADER);
            case "gitlab" -> getHeaderCaseInsensitive(headers, GITLAB_TOKEN_HEADER);
            case "codecommit" -> "dummy"; // AWS SNS signature embedded in payload
            default -> null;
        };
    }

    /**
     * Gets header value in case-insensitive manner.
     *
     * @param headers    the headers map
     * @param headerName the header name to lookup
     * @return the header value, or null if not found
     */
    private String getHeaderCaseInsensitive(Map<String, String> headers, String headerName) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(headerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves webhook secret for the given platform.
     * <p>
     * TODO: Implement database lookup using ProjectConfigService.
     * For now, returns hardcoded test secrets.
     * </p>
     *
     * @param platform the platform name (github, gitlab, codecommit)
     * @return the webhook secret for signature verification
     */
    private String getWebhookSecret(String platform) {
        // TODO: Replace with actual database lookup
        // return projectConfigService.getWebhookSecret(platform);
        return switch (platform.toLowerCase()) {
            case "github" -> "test-github-secret";
            case "gitlab" -> "test-gitlab-token";
            case "codecommit" -> "not-used-for-sns";
            default -> null;
        };
    }

    /**
     * Parses webhook payload as JSON.
     *
     * @param payload the raw JSON string
     * @return JsonNode representing the parsed payload
     * @throws JsonProcessingException if payload is not valid JSON
     */
    private JsonNode parsePayload(String payload) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(payload);
    }

    /**
     * Validates required fields in webhook event based on platform.
     *
     * @param platform the platform name
     * @param event    the parsed JSON event
     * @throws IllegalArgumentException if required fields are missing
     */
    private void validateEvent(String platform, JsonNode event) {
        switch (platform.toLowerCase()) {
            case "github" -> validateGitHubEvent(event);
            case "gitlab" -> validateGitLabEvent(event);
            case "codecommit" -> validateCodeCommitEvent(event);
        }
    }

    /**
     * Validates GitHub webhook event.
     *
     * @param event the parsed event
     * @throws IllegalArgumentException if required fields are missing
     */
    private void validateGitHubEvent(JsonNode event) {
        if (!event.has("repository") ||
            !event.get("repository").has("name") ||
            !event.get("repository").has("full_name")) {
            throw new IllegalArgumentException("Missing required field: repository.name or repository.full_name");
        }
        // Require either pusher (for push events) or pull_request (for PR events)
        if (!event.has("pusher") && !event.has("pull_request")) {
            throw new IllegalArgumentException("Missing required field: pusher or pull_request");
        }
    }

    /**
     * Validates GitLab webhook event.
     *
     * @param event the parsed event
     * @throws IllegalArgumentException if required fields are missing
     */
    private void validateGitLabEvent(JsonNode event) {
        if (!event.has("project") ||
            !event.get("project").has("name") ||
            !event.get("project").has("path_with_namespace")) {
            throw new IllegalArgumentException("Missing required field: project.name or project.path_with_namespace");
        }
        if (!event.has("user_username")) {
            throw new IllegalArgumentException("Missing required field: user_username");
        }
    }

    /**
     * Validates AWS CodeCommit (SNS) webhook event.
     *
     * @param event the parsed SNS message
     * @throws IllegalArgumentException if required fields are missing
     */
    private void validateCodeCommitEvent(JsonNode event) {
        if (!event.has("Message")) {
            throw new IllegalArgumentException("Missing required field: Message (SNS message body)");
        }
    }

    /**
     * Enqueues code review task to Redis.
     * <p>
     * TODO: Will be implemented in Story 2.5 (Redis priority queue).
     * For now, just logs the task.
     * </p>
     *
     * @param platform the platform name
     * @param event    the parsed event
     */
    private void enqueueTask(String platform, JsonNode event) {
        // TODO: Implement in Story 2.5 (Redis priority queue)
        String eventType = extractEventType(platform, event);
        log.info("Task enqueued for platform: {}, event type: {}", platform, eventType);
    }

    /**
     * Extracts event type from webhook payload.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the event type (push, pull_request, merge_request, etc.)
     */
    private String extractEventType(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> event.has("pull_request") ? "pull_request" : "push";
            case "gitlab" -> event.has("object_kind") ?
                    event.get("object_kind").asText() : "unknown";
            case "codecommit" -> "codecommit_event";
            default -> "unknown";
        };
    }
}
