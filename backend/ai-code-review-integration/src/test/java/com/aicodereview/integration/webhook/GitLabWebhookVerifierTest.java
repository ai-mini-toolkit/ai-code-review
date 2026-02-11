package com.aicodereview.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitLabWebhookVerifier}.
 * <p>
 * Tests cover GitLab Secret Token verification, including valid tokens,
 * invalid tokens, null/empty parameters, and edge cases.
 * </p>
 *
 * @since 2.3.0
 */
@DisplayName("GitLabWebhookVerifier Tests")
class GitLabWebhookVerifierTest {

    private GitLabWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GitLabWebhookVerifier();
    }

    @Test
    @DisplayName("verify - valid GitLab token should return true")
    void testVerify_ValidToken_ReturnsTrue() {
        // Given: Known payload and secret token
        String payload = "{\"object_kind\":\"push\",\"ref\":\"refs/heads/main\"}";
        String secret = "my-gitlab-secret-token-123";
        String signature = "my-gitlab-secret-token-123"; // GitLab: signature IS the token

        // When: Verify with matching token
        boolean result = verifier.verify(payload, signature, secret);

        // Then: Verification should succeed
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - invalid token should return false")
    void testVerify_InvalidToken_ReturnsFalse() {
        // Given
        String payload = "{\"object_kind\":\"merge_request\"}";
        String secret = "correct-token";
        String signature = "wrong-token";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - null signature should return false")
    void testVerify_NullSignature_ReturnsFalse() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String secret = "secret";

        // When
        boolean result = verifier.verify(payload, null, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - empty signature should return false")
    void testVerify_EmptySignature_ReturnsFalse() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String secret = "secret";

        // When
        boolean result = verifier.verify(payload, "", secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - blank signature should return false")
    void testVerify_BlankSignature_ReturnsFalse() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String secret = "secret";

        // When
        boolean result = verifier.verify(payload, "   ", secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - null payload should return false")
    void testVerify_NullPayload_ReturnsFalse() {
        // Given
        String secret = "secret";
        String signature = "token";

        // When
        boolean result = verifier.verify(null, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - null secret should return false")
    void testVerify_NullSecret_ReturnsFalse() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String signature = "token";

        // When
        boolean result = verifier.verify(payload, signature, null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - empty secret should return false")
    void testVerify_EmptySecret_ReturnsFalse() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String signature = "token";

        // When
        boolean result = verifier.verify(payload, signature, "");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - empty payload should return false")
    void testVerify_EmptyPayload_ReturnsFalse() {
        // Given
        String secret = "secret";
        String signature = "secret";

        // When
        boolean result = verifier.verify("", signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getPlatform - should return 'gitlab'")
    void testGetPlatform_ReturnsGitLab() {
        // When
        String platform = verifier.getPlatform();

        // Then
        assertThat(platform).isEqualTo("gitlab");
    }

    @Test
    @DisplayName("verify - realistic GitLab webhook payload should pass")
    void testVerify_RealisticGitLabPayload_ReturnsTrue() {
        // Given: Realistic GitLab webhook payload
        String payload = "{\"object_kind\":\"push\",\"before\":\"95790bf891e76fee\",\"after\":\"da1560886d4f094c\"}";
        String secret = "gitlab-webhook-secret-2024";
        String signature = "gitlab-webhook-secret-2024";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then: Should validate successfully
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - different token lengths should work correctly")
    void testVerify_DifferentTokenLengths_WorksCorrectly() {
        // Given: Short token
        String payload = "{\"test\":\"data\"}";
        String shortSecret = "abc";
        String shortSignature = "abc";

        // When: Verify short token
        boolean shortResult = verifier.verify(payload, shortSignature, shortSecret);

        // Then
        assertThat(shortResult).isTrue();

        // Given: Long token
        String longSecret = "very-long-gitlab-webhook-secret-token-with-many-characters-123456789";
        String longSignature = "very-long-gitlab-webhook-secret-token-with-many-characters-123456789";

        // When: Verify long token
        boolean longResult = verifier.verify(payload, longSignature, longSecret);

        // Then
        assertThat(longResult).isTrue();
    }
}
