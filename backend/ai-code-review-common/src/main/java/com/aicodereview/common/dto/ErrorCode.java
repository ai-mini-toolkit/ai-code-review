package com.aicodereview.common.dto;

import lombok.Getter;

/**
 * Standard error codes for API responses
 */
@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR("ERR_500", "Internal server error"),
    BAD_REQUEST("ERR_400", "Bad request"),
    NOT_FOUND("ERR_404", "Resource not found"),
    UNAUTHORIZED("ERR_401", "Unauthorized"),
    FORBIDDEN("ERR_403", "Forbidden"),
    CONFLICT("ERR_409", "Resource conflict"),
    VALIDATION_ERROR("ERR_422", "Validation error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
