package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FailureType} enum.
 * <p>
 * Validates enum size, descriptions, and retryable classification.
 * The size assertion ensures new enum values require test updates.
 * </p>
 *
 * @since 2.7.0
 */
@DisplayName("FailureType Enum Tests")
class FailureTypeTest {

    @Test
    @DisplayName("Should have exactly 6 failure types")
    void shouldHaveCorrectNumberOfValues() {
        FailureType[] values = FailureType.values();
        assertThat(values).hasSize(6);
        assertThat(values).containsExactlyInAnyOrder(
                FailureType.RATE_LIMIT,
                FailureType.NETWORK_ERROR,
                FailureType.TIMEOUT,
                FailureType.VALIDATION_ERROR,
                FailureType.AUTHENTICATION_ERROR,
                FailureType.UNKNOWN
        );
    }

    @ParameterizedTest
    @EnumSource(FailureType.class)
    @DisplayName("Every FailureType should have a non-null description")
    void shouldHaveNonNullDescription(FailureType failureType) {
        assertThat(failureType.getDescription()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Retryable types: RATE_LIMIT, NETWORK_ERROR, TIMEOUT, UNKNOWN")
    void shouldClassifyRetryableTypesCorrectly() {
        assertThat(FailureType.RATE_LIMIT.isRetryable()).isTrue();
        assertThat(FailureType.NETWORK_ERROR.isRetryable()).isTrue();
        assertThat(FailureType.TIMEOUT.isRetryable()).isTrue();
        assertThat(FailureType.UNKNOWN.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("Non-retryable types: VALIDATION_ERROR, AUTHENTICATION_ERROR")
    void shouldClassifyNonRetryableTypesCorrectly() {
        assertThat(FailureType.VALIDATION_ERROR.isRetryable()).isFalse();
        assertThat(FailureType.AUTHENTICATION_ERROR.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("Should convert from string via valueOf")
    void shouldConvertFromString() {
        assertThat(FailureType.valueOf("RATE_LIMIT")).isEqualTo(FailureType.RATE_LIMIT);
        assertThat(FailureType.valueOf("UNKNOWN")).isEqualTo(FailureType.UNKNOWN);
    }
}
