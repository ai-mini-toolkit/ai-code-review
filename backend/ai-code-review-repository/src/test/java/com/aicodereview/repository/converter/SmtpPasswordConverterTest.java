package com.aicodereview.repository.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SmtpPasswordConverter.
 *
 * @since 7.1.0
 */
@DisplayName("SmtpPasswordConverter Unit Tests")
class SmtpPasswordConverterTest {

    private SmtpPasswordConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SmtpPasswordConverter();
        converter.setEncryptionKey("test-encryption-key-32characters!");
    }

    @Test
    @DisplayName("Should encrypt and decrypt password round-trip")
    void shouldEncryptDecryptRoundTrip() {
        String password = "my-smtp-password-123";

        String encrypted = converter.convertToDatabaseColumn(password);
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(password);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(password);
    }

    @Test
    @DisplayName("Should return null for null input on encrypt")
    void shouldReturnNullOnEncryptNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("Should return null for null input on decrypt")
    void shouldReturnNullOnDecryptNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext (random IV)")
    void shouldProduceDifferentCiphertext() {
        String password = "test-password";
        String encrypted1 = converter.convertToDatabaseColumn(password);
        String encrypted2 = converter.convertToDatabaseColumn(password);

        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // Both should decrypt to same value
        assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(password);
        assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(password);
    }

    @Test
    @DisplayName("Should handle empty string password")
    void shouldHandleEmptyString() {
        String encrypted = converter.convertToDatabaseColumn("");
        assertThat(encrypted).isNotNull();
        assertThat(converter.convertToEntityAttribute(encrypted)).isEmpty();
    }
}
