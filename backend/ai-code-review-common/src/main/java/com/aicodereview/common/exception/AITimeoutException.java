package com.aicodereview.common.exception;

/**
 * Exception for AI provider timeout errors (HTTP 408).
 *
 * @since 4.1.0
 */
public class AITimeoutException extends AIProviderException {

    public AITimeoutException(String message) {
        super(408, message);
    }

    public AITimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
