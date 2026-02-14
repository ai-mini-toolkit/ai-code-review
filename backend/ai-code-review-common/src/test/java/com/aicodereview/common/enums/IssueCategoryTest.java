package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IssueCategory} enum.
 * <p>
 * Validates enum size, display names, and string conversion.
 * The size assertion ensures new enum values require test updates.
 * </p>
 *
 * @since 4.1.0
 */
@DisplayName("IssueCategory Enum Tests")
class IssueCategoryTest {

    @Test
    @DisplayName("Should have exactly 6 issue categories")
    void shouldHaveCorrectNumberOfValues() {
        IssueCategory[] values = IssueCategory.values();
        assertThat(values).hasSize(6);
        assertThat(values).containsExactlyInAnyOrder(
                IssueCategory.SECURITY,
                IssueCategory.PERFORMANCE,
                IssueCategory.MAINTAINABILITY,
                IssueCategory.CORRECTNESS,
                IssueCategory.STYLE,
                IssueCategory.BEST_PRACTICES
        );
    }

    @Test
    @DisplayName("Should have correct display names")
    void shouldHaveCorrectDisplayNames() {
        assertThat(IssueCategory.SECURITY.getDisplayName()).isEqualTo("Security");
        assertThat(IssueCategory.PERFORMANCE.getDisplayName()).isEqualTo("Performance");
        assertThat(IssueCategory.MAINTAINABILITY.getDisplayName()).isEqualTo("Maintainability");
        assertThat(IssueCategory.CORRECTNESS.getDisplayName()).isEqualTo("Correctness");
        assertThat(IssueCategory.STYLE.getDisplayName()).isEqualTo("Code Style");
        assertThat(IssueCategory.BEST_PRACTICES.getDisplayName()).isEqualTo("Best Practices");
    }

    @ParameterizedTest
    @EnumSource(IssueCategory.class)
    @DisplayName("Every IssueCategory should have a non-null display name")
    void shouldHaveNonNullDisplayName(IssueCategory category) {
        assertThat(category.getDisplayName()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Should convert from string via valueOf")
    void shouldConvertFromString() {
        assertThat(IssueCategory.valueOf("SECURITY")).isEqualTo(IssueCategory.SECURITY);
        assertThat(IssueCategory.valueOf("BEST_PRACTICES")).isEqualTo(IssueCategory.BEST_PRACTICES);
    }
}
