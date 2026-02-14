package com.aicodereview.common.enums;

/**
 * Classification of task failure types for retry decision-making.
 * <p>
 * Determines whether a failed task should be retried with exponential backoff
 * or immediately marked as permanently failed.
 * </p>
 * <p>
 * Retry rules:
 * - Retryable: RATE_LIMIT, NETWORK_ERROR, TIMEOUT, UNKNOWN
 * - Non-retryable: VALIDATION_ERROR, AUTHENTICATION_ERROR
 * </p>
 *
 * @since 2.7.0
 */
public enum FailureType {

    /**
     * AI API rate limit exceeded (HTTP 429).
     * Retryable with exponential backoff.
     */
    RATE_LIMIT("AI API rate limit exceeded", true),

    /**
     * Network connection failure (DNS, connection refused, etc.).
     * Retryable with exponential backoff.
     */
    NETWORK_ERROR("Network connection failure", true),

    /**
     * Request timeout (exceeded configured timeout threshold).
     * Retryable with exponential backoff.
     */
    TIMEOUT("Request timeout", true),

    /**
     * Invalid input or payload (HTTP 400).
     * Not retryable — the same input will produce the same error.
     */
    VALIDATION_ERROR("Invalid input or payload", false),

    /**
     * Authentication or authorization failure (HTTP 401/403).
     * Not retryable — requires credential fix, not a transient issue.
     */
    AUTHENTICATION_ERROR("Authentication or authorization failure", false),

    /**
     * Unclassified error.
     * Treated as retryable (conservative approach — better to retry than lose tasks).
     */
    UNKNOWN("Unclassified error", true);

    private final String description;
    private final boolean retryable;

    FailureType(String description, boolean retryable) {
        this.description = description;
        this.retryable = retryable;
    }

    /**
     * Gets the human-readable description of this failure type.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Determines whether this failure type allows retry.
     *
     * @return true if the error is transient and retryable, false if permanent
     */
    public boolean isRetryable() {
        return retryable;
    }
}
