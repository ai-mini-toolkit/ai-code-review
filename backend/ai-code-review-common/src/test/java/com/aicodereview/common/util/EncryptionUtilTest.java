package com.aicodereview.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    private static final String TEST_KEY = "test-encryption-key-32-bytes!!!!";

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
        String original = "my-webhook-secret-123";
        String encrypted = EncryptionUtil.encrypt(original, TEST_KEY);
        String decrypted = EncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String original = "same-secret";
        String encrypted1 = EncryptionUtil.encrypt(original, TEST_KEY);
        String encrypted2 = EncryptionUtil.encrypt(original, TEST_KEY);

        // Different IVs should produce different ciphertexts
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // Both should decrypt to the same value
        assertThat(EncryptionUtil.decrypt(encrypted1, TEST_KEY)).isEqualTo(original);
        assertThat(EncryptionUtil.decrypt(encrypted2, TEST_KEY)).isEqualTo(original);
    }

    @Test
    void shouldHandleEmptyString() {
        assertThat(EncryptionUtil.encrypt("", TEST_KEY)).isEmpty();
        assertThat(EncryptionUtil.decrypt("", TEST_KEY)).isEmpty();
    }

    @Test
    void shouldHandleNullInput() {
        assertThat(EncryptionUtil.encrypt(null, TEST_KEY)).isNull();
        assertThat(EncryptionUtil.decrypt(null, TEST_KEY)).isNull();
    }

    @Test
    void shouldFailWithWrongKey() {
        String original = "my-secret";
        String encrypted = EncryptionUtil.encrypt(original, TEST_KEY);

        assertThatThrownBy(() -> EncryptionUtil.decrypt(encrypted, "wrong-key-that-is-32-bytes!!!!!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldHandleSpecialCharacters() {
        String original = "secret-with-特殊字符-&@#$%^*()";
        String encrypted = EncryptionUtil.encrypt(original, TEST_KEY);
        String decrypted = EncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shouldHandleLongStrings() {
        String original = "a".repeat(1000);
        String encrypted = EncryptionUtil.encrypt(original, TEST_KEY);
        String decrypted = EncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shouldProduceBase64EncodedOutput() {
        String encrypted = EncryptionUtil.encrypt("test", TEST_KEY);
        // Should be valid Base64
        assertThat(encrypted).matches("^[A-Za-z0-9+/]+=*$");
    }
}
