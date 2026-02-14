package com.aicodereview.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChangeType Enum Tests")
class ChangeTypeTest {

    @Test
    @DisplayName("Should have exactly 4 change types")
    void shouldHaveCorrectNumberOfValues() {
        ChangeType[] values = ChangeType.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactlyInAnyOrder(
                ChangeType.ADD,
                ChangeType.MODIFY,
                ChangeType.DELETE,
                ChangeType.RENAME
        );
    }

    @Test
    @DisplayName("Should convert from string via valueOf")
    void shouldConvertFromString() {
        assertThat(ChangeType.valueOf("ADD")).isEqualTo(ChangeType.ADD);
        assertThat(ChangeType.valueOf("MODIFY")).isEqualTo(ChangeType.MODIFY);
        assertThat(ChangeType.valueOf("DELETE")).isEqualTo(ChangeType.DELETE);
        assertThat(ChangeType.valueOf("RENAME")).isEqualTo(ChangeType.RENAME);
    }
}
