package com.aicodereview.common.exception;

/**
 * Exception for AI provider authentication errors (HTTP 401).
 *
 * @since 4.1.0
 */
public class AIAuthenticationException extends AIProviderException {

    public AIAuthenticationException(String message) {
        super(401, message);
    }
}
