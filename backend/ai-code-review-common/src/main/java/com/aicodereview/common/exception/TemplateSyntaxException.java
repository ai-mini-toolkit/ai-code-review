package com.aicodereview.common.exception;

/**
 * Exception thrown when a Mustache template has invalid syntax.
 */
public class TemplateSyntaxException extends RuntimeException {

    public TemplateSyntaxException(String message) {
        super(message);
    }

    public TemplateSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
