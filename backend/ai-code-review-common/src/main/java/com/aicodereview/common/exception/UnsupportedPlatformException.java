package com.aicodereview.common.exception;

/**
 * Exception thrown when attempting to verify a webhook from an unsupported platform.
 * <p>
 * This exception is thrown by {@link com.aicodereview.integration.webhook.WebhookVerificationChain}
 * when no verifier is registered for the specified platform name.
 * </p>
 *
 * <h3>HTTP Response:</h3>
 * This exception should be mapped to HTTP 400 Bad Request by the global exception handler,
 * indicating that the client provided an invalid or unsupported platform identifier.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * if (!verifierMap.containsKey(platform)) {
 *     throw new UnsupportedPlatformException("Platform not supported: " + platform);
 * }
 * }</pre>
 *
 * @see com.aicodereview.integration.webhook.WebhookVerificationChain
 * @since 2.1.0
 * @author AI Code Review System
 */
public class UnsupportedPlatformException extends RuntimeException {

    /**
     * Constructs a new UnsupportedPlatformException with the specified detail message.
     *
     * @param message the detail message explaining which platform is unsupported
     */
    public UnsupportedPlatformException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnsupportedPlatformException with the specified detail message and cause.
     *
     * @param message the detail message explaining which platform is unsupported
     * @param cause   the cause of this exception
     */
    public UnsupportedPlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
