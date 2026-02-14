package com.aicodereview.common.exception;

/**
 * Exception for AI provider rate limit errors (HTTP 429).
 *
 * @since 4.1.0
 */
public class RateLimitException extends AIProviderException {

    public RateLimitException(String message) {
        super(429, message);
    }
}
