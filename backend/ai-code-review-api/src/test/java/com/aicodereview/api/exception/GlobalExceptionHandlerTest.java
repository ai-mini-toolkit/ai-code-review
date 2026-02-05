package com.aicodereview.api.exception;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Test exception");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("An internal server error occurred");
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Invalid parameter");
    }

    @Test
    void shouldNotExposeInternalExceptionDetails() {
        Exception ex = new RuntimeException("Sensitive database connection string: jdbc://...");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getMessage())
                .doesNotContain("Sensitive")
                .doesNotContain("jdbc://")
                .isEqualTo("An internal server error occurred");
    }

    @Test
    void shouldIncludeTimestampInResponse() {
        Exception ex = new RuntimeException("Test");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
