package com.aicodereview.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the AI provider exception hierarchy.
 *
 * @since 4.1.0
 */
@DisplayName("AIProviderException Hierarchy Tests")
class AIProviderExceptionTest {

    @Test
    @DisplayName("AIProviderException should preserve statusCode and message")
    void aiProviderExceptionShouldPreserveStatusCodeAndMessage() {
        AIProviderException ex = new AIProviderException(500, "Internal server error");

        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getMessage()).isEqualTo("Internal server error");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("AIProviderException with statusCode and cause should preserve both")
    void aiProviderExceptionWithStatusCodeAndCauseShouldPreserveBoth() {
        Throwable cause = new RuntimeException("connection refused");
        AIProviderException ex = new AIProviderException(503, "Service unavailable", cause);

        assertThat(ex.getStatusCode()).isEqualTo(503);
        assertThat(ex.getMessage()).isEqualTo("Service unavailable");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("AIProviderException with cause should set statusCode to -1")
    void aiProviderExceptionWithCauseShouldSetDefaultStatusCode() {
        Throwable cause = new RuntimeException("root cause");
        AIProviderException ex = new AIProviderException("AI call failed", cause);

        assertThat(ex.getStatusCode()).isEqualTo(-1);
        assertThat(ex.getMessage()).isEqualTo("AI call failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("RateLimitException should have statusCode 429")
    void rateLimitExceptionShouldHaveCorrectStatusCode() {
        RateLimitException ex = new RateLimitException("Rate limit exceeded");

        assertThat(ex.getStatusCode()).isEqualTo(429);
        assertThat(ex.getMessage()).isEqualTo("Rate limit exceeded");
        assertThat(ex).isInstanceOf(AIProviderException.class);
    }

    @Test
    @DisplayName("AIAuthenticationException should have statusCode 401")
    void aiAuthenticationExceptionShouldHaveCorrectStatusCode() {
        AIAuthenticationException ex = new AIAuthenticationException("Invalid API key");

        assertThat(ex.getStatusCode()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("Invalid API key");
        assertThat(ex).isInstanceOf(AIProviderException.class);
    }

    @Test
    @DisplayName("AITimeoutException should have statusCode 408")
    void aiTimeoutExceptionShouldHaveCorrectStatusCode() {
        AITimeoutException ex = new AITimeoutException("Request timed out");

        assertThat(ex.getStatusCode()).isEqualTo(408);
        assertThat(ex.getMessage()).isEqualTo("Request timed out");
        assertThat(ex).isInstanceOf(AIProviderException.class);
    }

    @Test
    @DisplayName("AITimeoutException with cause should set statusCode to -1")
    void aiTimeoutExceptionWithCauseShouldSetDefaultStatusCode() {
        Throwable cause = new java.net.SocketTimeoutException("connect timed out");
        AITimeoutException ex = new AITimeoutException("Connection timeout", cause);

        assertThat(ex.getStatusCode()).isEqualTo(-1);
        assertThat(ex.getMessage()).isEqualTo("Connection timeout");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
