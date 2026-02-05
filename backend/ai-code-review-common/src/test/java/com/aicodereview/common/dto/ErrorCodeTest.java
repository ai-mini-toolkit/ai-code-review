package com.aicodereview.common.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorCode enum
 */
class ErrorCodeTest {

    @Test
    void shouldHaveCorrectBadRequestCode() {
        assertThat(ErrorCode.BAD_REQUEST.getCode()).isEqualTo("ERR_400");
        assertThat(ErrorCode.BAD_REQUEST.getDefaultMessage()).isEqualTo("Bad request");
    }

    @Test
    void shouldHaveCorrectInternalServerErrorCode() {
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getCode()).isEqualTo("ERR_500");
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()).isEqualTo("Internal server error");
    }

    @Test
    void shouldHaveCorrectNotFoundCode() {
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo("ERR_404");
        assertThat(ErrorCode.NOT_FOUND.getDefaultMessage()).isEqualTo("Resource not found");
    }

    @Test
    void shouldHaveCorrectUnauthorizedCode() {
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo("ERR_401");
        assertThat(ErrorCode.UNAUTHORIZED.getDefaultMessage()).isEqualTo("Unauthorized");
    }

    @Test
    void shouldHaveCorrectForbiddenCode() {
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo("ERR_403");
        assertThat(ErrorCode.FORBIDDEN.getDefaultMessage()).isEqualTo("Forbidden");
    }

    @Test
    void shouldHaveCorrectValidationErrorCode() {
        assertThat(ErrorCode.VALIDATION_ERROR.getCode()).isEqualTo("ERR_422");
        assertThat(ErrorCode.VALIDATION_ERROR.getDefaultMessage()).isEqualTo("Validation error");
    }

    @Test
    void shouldHaveAllDefinedErrorCodes() {
        ErrorCode[] errorCodes = ErrorCode.values();
        assertThat(errorCodes).hasSize(6);
        assertThat(errorCodes).containsExactlyInAnyOrder(
                ErrorCode.BAD_REQUEST,
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.NOT_FOUND,
                ErrorCode.UNAUTHORIZED,
                ErrorCode.FORBIDDEN,
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    void shouldConvertFromString() {
        ErrorCode errorCode = ErrorCode.valueOf("BAD_REQUEST");
        assertThat(errorCode).isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
