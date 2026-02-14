package com.aicodereview.common.exception;

/**
 * Base exception for AI provider errors.
 * <p>
 * Follows the same pattern as {@link GitApiException} with a statusCode field
 * for HTTP status classification.
 * </p>
 *
 * @since 4.1.0
 */
public class AIProviderException extends RuntimeException {

    private final int statusCode;

    public AIProviderException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AIProviderException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
