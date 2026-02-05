package com.aicodereview.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized API response wrapper for all REST endpoints.
 * All controllers MUST return ApiResponse<T>.
 *
 * @param <T> The type of data being returned
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    /**
     * Create a successful response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    /**
     * Create a successful response without data
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object details) {
        ErrorDetail error = new ErrorDetail(code.getCode(), message, details);
        return new ApiResponse<>(false, null, error, Instant.now());
    }

    /**
     * Create an error response without details
     */
    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }
}
