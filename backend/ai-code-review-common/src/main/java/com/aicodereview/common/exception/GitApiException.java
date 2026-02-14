package com.aicodereview.common.exception;

/**
 * Exception thrown when a Git platform API call fails.
 */
public class GitApiException extends RuntimeException {

    private final int statusCode;

    public GitApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
