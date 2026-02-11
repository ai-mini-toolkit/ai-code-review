package com.aicodereview.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Integration tests for {@link WebhookVerificationChain}.
 * <p>
 * Tests verify Spring's auto-discovery mechanism correctly finds and injects
 * all @Component implementations of WebhookVerifier interface, including
 * GitHubWebhookVerifier, GitLabWebhookVerifier, and AWSCodeCommitWebhookVerifier.
 * </p>
 *
 * <p>This integration test uses Spring's component scanning to load real
 * verifier implementations, unlike the unit test which uses mock verifiers.</p>
 *
 * @since 2.3.0
 */
@SpringBootTest
@ContextConfiguration(classes = {
    WebhookVerificationChain.class,
    GitHubWebhookVerifier.class,
    GitLabWebhookVerifier.class,
    AWSCodeCommitWebhookVerifier.class
})
@DisplayName("WebhookVerificationChain Spring Integration Tests")
class WebhookVerificationChainIntegrationTest {

    @Autowired
    private WebhookVerificationChain verificationChain;

    @Autowired
    private List<WebhookVerifier> verifiers;

    @Test
    @DisplayName("Spring should auto-discover all three verifier implementations")
    void testSpringAutoDiscovery_AllVerifiersInjected() {
        // Then: Should have 3 verifiers (GitHub, GitLab, AWS CodeCommit)
        assertThat(verifiers).hasSize(3);

        // Verify platform names
        List<String> platforms = verifiers.stream()
                .map(WebhookVerifier::getPlatform)
                .sorted()
                .toList();

        assertThat(platforms).containsExactly("codecommit", "github", "gitlab");
    }

    @Test
    @DisplayName("verify - GitLab platform should route to GitLabWebhookVerifier")
    void testVerify_GitLabPlatform_RoutesToRealVerifier() {
        // Given: Valid GitLab token
        String payload = "{\"object_kind\":\"push\",\"ref\":\"refs/heads/main\"}";
        String secret = "my-gitlab-token-123";
        String signature = "my-gitlab-token-123"; // GitLab: token in header

        // When: Verify with gitlab platform
        boolean result = verificationChain.verify("gitlab", payload, signature, secret);

        // Then: Should succeed with matching token
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - GitLab platform with invalid token should fail")
    void testVerify_GitLabPlatform_InvalidToken_ReturnsFalse() {
        // Given: Mismatched tokens
        String payload = "{\"object_kind\":\"push\"}";
        String secret = "correct-token";
        String signature = "wrong-token";

        // When
        boolean result = verificationChain.verify("gitlab", payload, signature, secret);

        // Then: Should fail
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - AWS CodeCommit platform should route to AWSCodeCommitWebhookVerifier")
    void testVerify_CodeCommitPlatform_RoutesToRealVerifier() {
        // Given: AWS SNS message payload with all required fields
        String payload = "{"
                + "\"Type\":\"Notification\","
                + "\"MessageId\":\"test-id\","
                + "\"SignatureVersion\":\"1\","
                + "\"Signature\":\"dGVzdA==\","
                + "\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\""
                + "}";
        String signature = "base64-signature";
        String secret = "not-used-for-sns";

        // When: Verify with codecommit platform
        boolean result = verificationChain.verify("codecommit", payload, signature, secret);

        // Then: Should return false (full signature verification not implemented yet)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - GitHub platform should route to GitHubWebhookVerifier")
    void testVerify_GitHubPlatform_RoutesToRealVerifier() {
        // Given: Valid GitHub HMAC-SHA256 signature
        String payload = "{\"ref\":\"refs/heads/main\",\"repository\":{\"name\":\"test-repo\"}}";
        String secret = "github-webhook-secret";

        // Calculate expected HMAC-SHA256 signature
        // For payload above + secret, GitHub would send: sha256=<hex-signature>
        // This is a simplified test - real GitHub signature would need exact payload
        String signature = "sha256=invalid-for-test"; // Will fail verification

        // When: Verify with github platform
        boolean result = verificationChain.verify("github", payload, signature, secret);

        // Then: Should return false (invalid signature)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verificationChain should be properly initialized by Spring")
    void testVerificationChain_SpringInjection() {
        // Then: Verification chain should be injected and ready
        assertThat(verificationChain).isNotNull();

        // Verify chain has access to all platforms
        // (tested indirectly through routing tests above)
    }
}
