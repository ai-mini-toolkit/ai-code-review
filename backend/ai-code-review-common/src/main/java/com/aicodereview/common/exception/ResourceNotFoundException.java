package com.aicodereview.common.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " not found with id: " + id);
    }

    public ResourceNotFoundException(String resourceType, String fieldName, Long fieldValue) {
        super(String.format("%s not found with %s: %s", resourceType, fieldName, fieldValue));
    }

    public ResourceNotFoundException(String resourceType, String fieldName, String fieldValue) {
        super(String.format("%s not found with %s: %s", resourceType, fieldName, fieldValue));
    }
}
