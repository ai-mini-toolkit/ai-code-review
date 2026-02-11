package com.aicodereview.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CryptoUtils}.
 * <p>
 * Tests cover constant-time string comparison functionality,
 * null handling, and edge cases.
 * </p>
 *
 * @since 2.1.0
 */
@DisplayName("CryptoUtils Tests")
class CryptoUtilsTest {

    @Test
    @DisplayName("constantTimeEquals - identical strings should return true")
    void testConstantTimeEquals_SameStrings_ReturnsTrue() {
        String str1 = "sha256=abcdef123456";
        String str2 = "sha256=abcdef123456";

        boolean result = CryptoUtils.constantTimeEquals(str1, str2);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("constantTimeEquals - different strings should return false")
    void testConstantTimeEquals_DifferentStrings_ReturnsFalse() {
        String str1 = "sha256=abcdef123456";
        String str2 = "sha256=fedcba654321";

        boolean result = CryptoUtils.constantTimeEquals(str1, str2);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - both null should return true")
    void testConstantTimeEquals_BothNull_ReturnsTrue() {
        boolean result = CryptoUtils.constantTimeEquals(null, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("constantTimeEquals - first null should return false")
    void testConstantTimeEquals_FirstNull_ReturnsFalse() {
        String str = "sha256=abcdef123456";

        boolean result = CryptoUtils.constantTimeEquals(null, str);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - second null should return false")
    void testConstantTimeEquals_SecondNull_ReturnsFalse() {
        String str = "sha256=abcdef123456";

        boolean result = CryptoUtils.constantTimeEquals(str, null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - different lengths should return false")
    void testConstantTimeEquals_DifferentLength_ReturnsFalse() {
        String short1 = "sha256=abc";
        String long1 = "sha256=abcdef123456";

        boolean result = CryptoUtils.constantTimeEquals(short1, long1);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - empty strings should return true")
    void testConstantTimeEquals_EmptyStrings_ReturnsTrue() {
        boolean result = CryptoUtils.constantTimeEquals("", "");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("constantTimeEquals - one empty, one non-empty should return false")
    void testConstantTimeEquals_OneEmpty_ReturnsFalse() {
        boolean result = CryptoUtils.constantTimeEquals("", "abc");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - case-sensitive comparison")
    void testConstantTimeEquals_CaseSensitive() {
        String lower = "sha256=abcdef";
        String upper = "SHA256=ABCDEF";

        boolean result = CryptoUtils.constantTimeEquals(lower, upper);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - Unicode characters should work correctly")
    void testConstantTimeEquals_UnicodeCharacters() {
        String unicode1 = "signature=你好世界";
        String unicode2 = "signature=你好世界";
        String unicode3 = "signature=こんにちは";

        assertThat(CryptoUtils.constantTimeEquals(unicode1, unicode2)).isTrue();
        assertThat(CryptoUtils.constantTimeEquals(unicode1, unicode3)).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals - whitespace differences should return false")
    void testConstantTimeEquals_WhitespaceDifferences() {
        String withSpace = "sha256=abc def";
        String withoutSpace = "sha256=abcdef";

        boolean result = CryptoUtils.constantTimeEquals(withSpace, withoutSpace);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("CryptoUtils constructor should throw UnsupportedOperationException")
    void testConstructor_ThrowsException() {
        assertThatThrownBy(() -> {
            // Use reflection to access private constructor
            java.lang.reflect.Constructor<CryptoUtils> constructor =
                    CryptoUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
                .hasCauseInstanceOf(UnsupportedOperationException.class)
                .hasStackTraceContaining("Utility class cannot be instantiated");
    }
}
