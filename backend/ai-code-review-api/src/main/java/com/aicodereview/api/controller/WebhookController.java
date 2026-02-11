package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.ErrorCode;
import com.aicodereview.common.dto.project.ProjectDTO;
import com.aicodereview.common.dto.reviewtask.CreateReviewTaskRequest;
import com.aicodereview.common.dto.reviewtask.ReviewTaskDTO;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.aicodereview.service.ProjectService;
import com.aicodereview.service.ReviewTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // ObjectMapper for JSON parsing (thread-safe, with JavaTimeModule for timestamp support)
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final WebhookVerificationChain verificationChain;
    private final ReviewTaskService reviewTaskService;
    private final ProjectService projectService;

    // Webhook secrets injected from configuration (environment variables or application.yml)
    @Value("${webhook.secrets.github}")
    private String githubSecret;

    @Value("${webhook.secrets.gitlab}")
    private String gitlabSecret;

    @Value("${webhook.secrets.codecommit}")
    private String codecommitSecret;

    /**
     * Constructor injection of dependencies.
     *
     * @param verificationChain the webhook signature verification chain
     * @param reviewTaskService service for managing review tasks
     * @param projectService service for project management
     */
    public WebhookController(WebhookVerificationChain verificationChain,
                             ReviewTaskService reviewTaskService,
                             ProjectService projectService) {
        this.verificationChain = verificationChain;
        this.reviewTaskService = reviewTaskService;
        this.projectService = projectService;
    }

    /**
     * Receives webhook events from Git platforms.
     *
     * @param platform the Git platform name (github, gitlab, codecommit)
     * @param payload  the raw webhook payload (JSON string)
     * @param headers  all HTTP request headers
     * @return 202 Accepted with acknowledgment message, or error response
     * @throws JsonProcessingException if payload is not valid JSON (handled by GlobalExceptionHandler)
     */
    @PostMapping("/{platform}")
    public ResponseEntity<ApiResponse<String>> receiveWebhook(
            @PathVariable(value = "platform") String platform,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) throws JsonProcessingException {

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

        // Step 4: Parse JSON payload (JsonProcessingException handled by GlobalExceptionHandler)
        JsonNode event = parsePayload(payload);

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
        if (platform == null) {
            return null;
        }

        return switch (platform.toLowerCase()) {
            case "github" -> getHeaderCaseInsensitive(headers, GITHUB_SIGNATURE_HEADER);
            case "gitlab" -> getHeaderCaseInsensitive(headers, GITLAB_TOKEN_HEADER);
            case "codecommit" -> "AWS_SNS_SIGNATURE_IN_PAYLOAD"; // AWS SNS signature embedded in payload
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
     * Secrets are injected from configuration (environment variables or application.yml).
     * This allows different secrets per environment without hardcoding them in source code.
     * </p>
     * <p>
     * Note: Per-project webhook secrets (from Project entity) will be implemented in a future story.
     * Current implementation uses platform-level secrets which is acceptable for initial deployment.
     * </p>
     *
     * @param platform the platform name (github, gitlab, codecommit)
     * @return the webhook secret for signature verification
     * @throws IllegalArgumentException if platform is null or secret not configured
     */
    private String getWebhookSecret(String platform) {
        if (platform == null) {
            throw new IllegalArgumentException("Platform cannot be null");
        }

        return switch (platform.toLowerCase()) {
            case "github" -> githubSecret;
            case "gitlab" -> gitlabSecret;
            case "codecommit" -> codecommitSecret;
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
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
     * Creates review task from webhook event and persists to database.
     * <p>
     * Processing steps:
     * 1. Extract repository URL from event
     * 2. Find project by repoUrl (throws 404 if not found)
     * 3. Determine TaskType from platform and event
     * 4. Extract task details (branch, commitHash, author, PR info)
     * 5. Create CreateReviewTaskRequest
     * 6. Call reviewTaskService.createTask() to persist
     * 7. Log success with task ID
     * </p>
     *
     * @param platform the platform name (github, gitlab, codecommit)
     * @param event    the parsed webhook event
     * @throws ResourceNotFoundException if project with repoUrl not found (propagated to caller)
     * @throws RuntimeException if task creation fails (logged and re-thrown)
     */
    private void enqueueTask(String platform, JsonNode event) {
        try {
            // Step 1: Extract repository URL
            String repoUrl = extractRepoUrl(platform, event);
            log.debug("Extracted repoUrl: {} from platform: {}", repoUrl, platform);

            // Step 2: Find project by repoUrl
            ProjectDTO project;
            try {
                project = projectService.findByRepoUrl(repoUrl);
                log.debug("Found project ID: {} for repoUrl: {}", project.getId(), repoUrl);
            } catch (ResourceNotFoundException e) {
                log.warn("Project not found for repoUrl: {}", repoUrl);
                throw e; // Propagate 404 to controller (will be handled by GlobalExceptionHandler)
            }

            // Step 3: Determine TaskType from platform and event
            TaskType taskType = determineTaskType(platform, event);
            log.debug("Determined taskType: {} for platform: {}", taskType, platform);

            // Step 4: Extract task details
            String branch = extractBranch(platform, event);
            String commitHash = extractCommitHash(platform, event);
            String author = extractAuthor(platform, event);
            Integer prNumber = extractPrNumber(platform, event);
            String prTitle = extractPrTitle(platform, event);
            String prDescription = extractPrDescription(platform, event);

            // Step 5: Build CreateReviewTaskRequest
            CreateReviewTaskRequest request = CreateReviewTaskRequest.builder()
                    .projectId(project.getId())
                    .taskType(taskType)
                    .repoUrl(repoUrl)
                    .branch(branch)
                    .commitHash(commitHash)
                    .author(author)
                    .prNumber(prNumber)
                    .prTitle(prTitle)
                    .prDescription(prDescription)
                    .build();

            // Step 6: Create review task
            ReviewTaskDTO task = reviewTaskService.createTask(request);

            log.info("Review task created successfully - ID: {}, project: {}, type: {}, commit: {}",
                    task.getId(), project.getName(), taskType, commitHash);

        } catch (ResourceNotFoundException e) {
            // Project not found - propagate to GlobalExceptionHandler (will return 404)
            throw e;
        } catch (Exception e) {
            // Unexpected error during task creation
            log.error("Failed to create review task for platform: {}, error: {}", platform, e.getMessage(), e);
            throw new RuntimeException("Failed to create review task", e);
        }
    }

    /**
     * Extracts repository URL from webhook event.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the repository URL (normalized)
     */
    private String extractRepoUrl(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> event.path("repository").path("html_url").asText();
            case "gitlab" -> event.path("project").path("web_url").asText();
            case "codecommit" -> {
                // CodeCommit events are wrapped in SNS, extract from Message
                String message = event.path("Message").asText();
                try {
                    JsonNode codecommitEvent = OBJECT_MAPPER.readTree(message);
                    yield codecommitEvent.path("repositoryName").asText();
                } catch (Exception e) {
                    log.warn("Failed to parse CodeCommit message: {}", e.getMessage());
                    yield "";
                }
            }
            default -> "";
        };
    }

    /**
     * Determines TaskType from platform and event structure.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the TaskType enum value
     */
    private TaskType determineTaskType(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> event.has("pull_request") ? TaskType.PULL_REQUEST : TaskType.PUSH;
            case "gitlab" -> {
                String objectKind = event.path("object_kind").asText();
                if ("merge_request".equals(objectKind)) {
                    yield TaskType.MERGE_REQUEST;
                } else {
                    yield TaskType.PUSH;
                }
            }
            case "codecommit" -> TaskType.PUSH; // CodeCommit only supports push events
            default -> TaskType.PUSH;
        };
    }

    /**
     * Extracts branch name from webhook event.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the branch name
     */
    private String extractBranch(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    yield event.path("pull_request").path("head").path("ref").asText();
                } else {
                    String ref = event.path("ref").asText();
                    yield ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
                }
            }
            case "gitlab" -> {
                if (event.has("merge_request")) {
                    yield event.path("merge_request").path("source_branch").asText();
                } else {
                    String ref = event.path("ref").asText();
                    yield ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
                }
            }
            case "codecommit" -> {
                String message = event.path("Message").asText();
                try {
                    JsonNode codecommitEvent = OBJECT_MAPPER.readTree(message);
                    String ref = codecommitEvent.path("referenceFullName").asText();
                    yield ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
                } catch (Exception e) {
                    log.error("Failed to extract branch from CodeCommit event: {}", e.getMessage(), e);
                    throw new IllegalArgumentException("Invalid CodeCommit webhook payload: unable to extract branch", e);
                }
            }
            default -> {
                log.error("Unsupported platform for branch extraction: {}", platform);
                throw new IllegalArgumentException("Unsupported platform: " + platform);
            }
        };
    }

    /**
     * Extracts commit hash from webhook event.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the commit SHA/hash
     */
    private String extractCommitHash(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    yield event.path("pull_request").path("head").path("sha").asText();
                } else {
                    yield event.path("after").asText();
                }
            }
            case "gitlab" -> {
                if (event.has("merge_request")) {
                    yield event.path("merge_request").path("last_commit").path("id").asText();
                } else {
                    yield event.path("after").asText();
                }
            }
            case "codecommit" -> {
                String message = event.path("Message").asText();
                try {
                    JsonNode codecommitEvent = OBJECT_MAPPER.readTree(message);
                    yield codecommitEvent.path("newCommitId").asText();
                } catch (Exception e) {
                    log.error("Failed to extract commit hash from CodeCommit event: {}", e.getMessage(), e);
                    throw new IllegalArgumentException("Invalid CodeCommit webhook payload: unable to extract commit hash", e);
                }
            }
            default -> {
                log.error("Unsupported platform for commit hash extraction: {}", platform);
                throw new IllegalArgumentException("Unsupported platform: " + platform);
            }
        };
    }

    /**
     * Extracts author username from webhook event.
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the author username
     */
    private String extractAuthor(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    yield event.path("pull_request").path("user").path("login").asText();
                } else {
                    yield event.path("pusher").path("name").asText();
                }
            }
            case "gitlab" -> event.path("user_username").asText();
            case "codecommit" -> {
                String message = event.path("Message").asText();
                try {
                    JsonNode codecommitEvent = OBJECT_MAPPER.readTree(message);
                    String author = codecommitEvent.path("author").asText();
                    if (author == null || author.isEmpty()) {
                        log.error("Author field is missing or empty in CodeCommit event");
                        throw new IllegalArgumentException("Invalid CodeCommit webhook payload: author field is missing");
                    }
                    yield author;
                } catch (Exception e) {
                    log.error("Failed to extract author from CodeCommit event: {}", e.getMessage(), e);
                    if (e instanceof IllegalArgumentException iae) {
                        throw iae;
                    }
                    throw new IllegalArgumentException("Invalid CodeCommit webhook payload: unable to extract author", e);
                }
            }
            default -> {
                log.error("Unsupported platform for author extraction: {}", platform);
                throw new IllegalArgumentException("Unsupported platform: " + platform);
            }
        };
    }

    /**
     * Extracts PR/MR number from webhook event (null for push events).
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the PR/MR number, or null if not applicable
     */
    private Integer extractPrNumber(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    JsonNode numberNode = event.path("pull_request").path("number");
                    if (!numberNode.isInt()) {
                        log.warn("GitHub PR number is not an integer: {}", numberNode.asText());
                        yield null;
                    }
                    int prNumber = numberNode.asInt(0);
                    if (prNumber <= 0) {
                        log.warn("GitHub PR number is invalid: {}", prNumber);
                        yield null;
                    }
                    yield prNumber;
                } else {
                    yield null;
                }
            }
            case "gitlab" -> {
                if (event.has("merge_request")) {
                    JsonNode iidNode = event.path("merge_request").path("iid");
                    if (!iidNode.isInt()) {
                        log.warn("GitLab MR iid is not an integer: {}", iidNode.asText());
                        yield null;
                    }
                    int mrNumber = iidNode.asInt(0);
                    if (mrNumber <= 0) {
                        log.warn("GitLab MR iid is invalid: {}", mrNumber);
                        yield null;
                    }
                    yield mrNumber;
                } else {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Extracts PR/MR title from webhook event (null for push events).
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the PR/MR title, or null if not applicable
     */
    private String extractPrTitle(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    yield event.path("pull_request").path("title").asText();
                } else {
                    yield null;
                }
            }
            case "gitlab" -> {
                if (event.has("merge_request")) {
                    yield event.path("merge_request").path("title").asText();
                } else {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Extracts PR/MR description from webhook event (null for push events).
     *
     * @param platform the platform name
     * @param event    the parsed event
     * @return the PR/MR description, or null if not applicable
     */
    private String extractPrDescription(String platform, JsonNode event) {
        return switch (platform.toLowerCase()) {
            case "github" -> {
                if (event.has("pull_request")) {
                    yield event.path("pull_request").path("body").asText();
                } else {
                    yield null;
                }
            }
            case "gitlab" -> {
                if (event.has("merge_request")) {
                    yield event.path("merge_request").path("description").asText();
                } else {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
