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
                + "\"SignatureVersion\":\"1\","
                + "\"Signature\":\"dGVzdA==\","
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

    // --- Direct URL validation tests (isValidAWSCertURL) ---

    @Test
    @DisplayName("isValidAWSCertURL - valid SNS HTTPS URL should return true")
    void testIsValidAWSCertURL_ValidSNSUrl_ReturnsTrue() {
        assertThat(verifier.isValidAWSCertURL("https://sns.us-east-1.amazonaws.com/cert.pem")).isTrue();
    }

    @Test
    @DisplayName("isValidAWSCertURL - valid SNS China region URL should return true")
    void testIsValidAWSCertURL_ValidChinaRegionUrl_ReturnsTrue() {
        assertThat(verifier.isValidAWSCertURL("https://sns.cn-north-1.amazonaws.com.cn/cert.pem")).isTrue();
    }

    @Test
    @DisplayName("isValidAWSCertURL - HTTP protocol should be rejected")
    void testIsValidAWSCertURL_HttpProtocol_ReturnsFalse() {
        // HTTP is vulnerable to MITM attacks - must require HTTPS
        assertThat(verifier.isValidAWSCertURL("http://sns.us-east-1.amazonaws.com/cert.pem")).isFalse();
    }

    @Test
    @DisplayName("isValidAWSCertURL - spoofed domain (evil-amazonaws.com) should be rejected")
    void testIsValidAWSCertURL_SpoofedDomain_ReturnsFalse() {
        // Domain suffix attack: evil-amazonaws.com ends with .amazonaws.com but is not AWS
        assertThat(verifier.isValidAWSCertURL("https://sns.us-east-1.evil-amazonaws.com/cert.pem")).isFalse();
    }

    @Test
    @DisplayName("isValidAWSCertURL - non-SNS AWS subdomain should be rejected")
    void testIsValidAWSCertURL_NonSNSSubdomain_ReturnsFalse() {
        assertThat(verifier.isValidAWSCertURL("https://s3.us-east-1.amazonaws.com/cert.pem")).isFalse();
    }

    @Test
    @DisplayName("isValidAWSCertURL - null URL should return false")
    void testIsValidAWSCertURL_NullUrl_ReturnsFalse() {
        assertThat(verifier.isValidAWSCertURL(null)).isFalse();
    }

    // --- SignatureVersion validation tests ---

    @Test
    @DisplayName("verify - missing SignatureVersion field should return false")
    void testVerify_MissingSignatureVersion_ReturnsFalse() {
        // Given: SNS message without SignatureVersion
        String payload = "{"
                + "\"Type\":\"Notification\","
                + "\"MessageId\":\"test-id\","
                + "\"Signature\":\"dGVzdA==\","
                + "\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\""
                + "}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - unsupported SignatureVersion '3' should return false")
    void testVerify_UnsupportedSignatureVersion_ReturnsFalse() {
        // Given: SNS message with unsupported version
        String payload = "{"
                + "\"Type\":\"Notification\","
                + "\"MessageId\":\"test-id\","
                + "\"SignatureVersion\":\"3\","
                + "\"Signature\":\"dGVzdA==\","
                + "\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\""
                + "}";
        String signature = "signature";
        String secret = "not-used";

        // When
        boolean result = verifier.verify(payload, signature, secret);

        // Then
        assertThat(result).isFalse();
    }
}
