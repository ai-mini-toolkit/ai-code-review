package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IssueSeverity} enum.
 * <p>
 * Validates enum size, score values, and ordering.
 * The size assertion ensures new enum values require test updates.
 * </p>
 *
 * @since 4.1.0
 */
@DisplayName("IssueSeverity Enum Tests")
class IssueSeverityTest {

    @Test
    @DisplayName("Should have exactly 5 severity levels")
    void shouldHaveCorrectNumberOfValues() {
        IssueSeverity[] values = IssueSeverity.values();
        assertThat(values).hasSize(5);
        assertThat(values).containsExactlyInAnyOrder(
                IssueSeverity.CRITICAL,
                IssueSeverity.HIGH,
                IssueSeverity.MEDIUM,
                IssueSeverity.LOW,
                IssueSeverity.INFO
        );
    }

    @Test
    @DisplayName("Should have correct scores for each severity")
    void shouldHaveCorrectScores() {
        assertThat(IssueSeverity.CRITICAL.getScore()).isEqualTo(5);
        assertThat(IssueSeverity.HIGH.getScore()).isEqualTo(4);
        assertThat(IssueSeverity.MEDIUM.getScore()).isEqualTo(3);
        assertThat(IssueSeverity.LOW.getScore()).isEqualTo(2);
        assertThat(IssueSeverity.INFO.getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should maintain severity ordering by score")
    void shouldMaintainSeverityOrdering() {
        assertThat(IssueSeverity.CRITICAL.getScore()).isGreaterThan(IssueSeverity.HIGH.getScore());
        assertThat(IssueSeverity.HIGH.getScore()).isGreaterThan(IssueSeverity.MEDIUM.getScore());
        assertThat(IssueSeverity.MEDIUM.getScore()).isGreaterThan(IssueSeverity.LOW.getScore());
        assertThat(IssueSeverity.LOW.getScore()).isGreaterThan(IssueSeverity.INFO.getScore());
    }

    @Test
    @DisplayName("Should convert from string via valueOf")
    void shouldConvertFromString() {
        assertThat(IssueSeverity.valueOf("CRITICAL")).isEqualTo(IssueSeverity.CRITICAL);
        assertThat(IssueSeverity.valueOf("INFO")).isEqualTo(IssueSeverity.INFO);
    }
}
