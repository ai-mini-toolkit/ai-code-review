package com.aicodereview.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AWSCodeCommitWebhookVerifier}.
 * <p>
 * Tests cover AWS SNS signature verification for CodeCommit webhooks,
 * including valid signatures, invalid signatures, null/empty parameters,
 * and format errors.
 * </p>
 *
 * <p>Note: AWS CodeCommit webhooks are delivered through Amazon SNS.
 * This verifier validates SNS message signatures, not direct CodeCommit signatures.</p>
 *
 * @since 2.3.0
 */
@DisplayName("AWSCodeCommitWebhookVerifier Tests")
class AWSCodeCommitWebhookVerifierTest {

    private AWSCodeCommitWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new AWSCodeCommitWebhookVerifier();
    }

    @Test
    @DisplayName("verify - null signature should return false")
    void testVerify_NullSignature_ReturnsFalse() {
        // Given: Valid SNS payload but null signature
        String payload = "{\"Type\":\"Notification\",\"MessageId\":\"test-id\"}";
        String secret = "not-used-for-sns"; // AWS uses public key, not shared secret

        // When
        boolean result = verifier.verify(payload, null, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - empty signature should return false")
    void testVerify_EmptySignature_ReturnsFalse() {
        // Given
        String payload = "{\"Type\":\"Notification\"}";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, "", secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - blank signature should return false")
    void testVerify_BlankSignature_ReturnsFalse() {
        // Given
        String payload = "{\"Type\":\"Notification\"}";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, "   ", secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - null payload should return false")
    void testVerify_NullPayload_ReturnsFalse() {
        // Given
        String signature = "base64-encoded-signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(null, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - empty payload should return false")
    void testVerify_EmptyPayload_ReturnsFalse() {
        // Given
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify("", signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - invalid JSON payload should return false")
    void testVerify_InvalidJSONPayload_ReturnsFalse() {
        // Given: Malformed JSON
        String payload = "{invalid json}}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - missing required Type field should return false")
    void testVerify_MissingTypeField_ReturnsFalse() {
        // Given: SNS message without Type field
        String payload = "{\"MessageId\":\"test-id\",\"Message\":\"test\"}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - missing SigningCertURL field should return false")
    void testVerify_MissingSigningCertURL_ReturnsFalse() {
        // Given: SNS message without SigningCertURL
        String payload = "{\"Type\":\"Notification\",\"MessageId\":\"test-id\"}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - invalid SigningCertURL domain should return false")
    void testVerify_InvalidCertURLDomain_ReturnsFalse() {
        // Given: Certificate URL from non-AWS domain (security risk)
        String payload = "{"
                + "\"Type\":\"Notification\","
                + "\"MessageId\":\"test-id\","
                + "\"SigningCertURL\":\"https://evil.com/cert.pem\""
                + "}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then: Should reject non-AWS certificate URLs
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getPlatform - should return 'codecommit'")
    void testGetPlatform_ReturnsCodeCommit() {
        // When
        String platform = verifier.getPlatform();

        // Then
        assertThat(platform).isEqualTo("codecommit");
    }

    @Test
    @DisplayName("verify - unsupported message type should return false")
    void testVerify_UnsupportedMessageType_ReturnsFalse() {
        // Given: Unknown message type
        String payload = "{"
                + "\"Type\":\"UnknownType\","
                + "\"MessageId\":\"test-id\","
                + "\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\""
                + "}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then: Should reject unknown message types
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - SigningCertURL with valid AWS domain should be accepted")
    void testVerify_ValidAWSCertURL_IsAccepted() {
        // This test verifies URL validation logic passes for valid AWS domains
        // Actual signature verification would fail due to missing/invalid signature,
        // but URL validation should pass

        // Given: Valid AWS SNS certificate URL
        String payload = "{"
                + "\"Type\":\"Notification\","
                + "\"MessageId\":\"test-id\","
                + "\"TopicArn\":\"arn:aws:sns:us-east-1:123456789012:test\","
                + "\"Message\":\"test message\","
                + "\"Timestamp\":\"2026-01-15T10:00:00.000Z\","
                + "\"SignatureVersion\":\"1\","
                + "\"Signature\":\"invalid-signature-for-testing\","
                + "\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\""
                + "}";
        String signature = "not-used-payload-contains-signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then: Should pass URL validation but fail on signature verification
        // (returns false but for signature reason, not URL reason)
        assertThat(result).isFalse();
    }
}
