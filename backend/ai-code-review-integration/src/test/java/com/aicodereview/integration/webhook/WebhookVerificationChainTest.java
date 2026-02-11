package com.aicodereview.integration.webhook;

import com.aicodereview.common.exception.UnsupportedPlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WebhookVerificationChain}.
 * <p>
 * Tests cover platform routing, verification delegation, exception handling,
 * and proper initialization with multiple verifiers.
 * </p>
 *
 * @since 2.1.0
 */
@DisplayName("WebhookVerificationChain Tests")
class WebhookVerificationChainTest {

    private WebhookVerificationChain verificationChain;

    // Mock verifier implementations for testing
    private WebhookVerifier githubVerifier;
    private WebhookVerifier gitlabVerifier;

    @BeforeEach
    void setUp() {
        // Create mock verifiers
        githubVerifier = new MockGitHubVerifier();
        gitlabVerifier = new MockGitLabVerifier();

        // Initialize chain with both verifiers
        verificationChain = new WebhookVerificationChain(
                Arrays.asList(githubVerifier, gitlabVerifier)
        );
    }

    @Test
    @DisplayName("verify - GitHub platform should route to GitHub verifier and return true")
    void testVerify_GitHubPlatform_Success() {
        String payload = "test payload";
        String signature = "valid-github-signature";
        String secret = "github-secret";

        boolean result = verificationChain.verify("github", payload, signature, secret);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - GitLab platform should route to GitLab verifier and return true")
    void testVerify_GitLabPlatform_Success() {
        String payload = "test payload";
        String signature = "valid-gitlab-signature";
        String secret = "gitlab-secret";

        boolean result = verificationChain.verify("gitlab", payload, signature, secret);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - unsupported platform should throw UnsupportedPlatformException")
    void testVerify_UnsupportedPlatform_ThrowsException() {
        String payload = "test payload";
        String signature = "some-signature";
        String secret = "some-secret";

        assertThatThrownBy(() -> verificationChain.verify("bitbucket", payload, signature, secret))
                .isInstanceOf(UnsupportedPlatformException.class)
                .hasMessage("Platform not supported: bitbucket");
    }

    @Test
    @DisplayName("verify - invalid signature should return false")
    void testVerify_InvalidSignature_ReturnsFalse() {
        String payload = "test payload";
        String signature = "invalid-signature";
        String secret = "github-secret";

        boolean result = verificationChain.verify("github", payload, signature, secret);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verify - null platform should throw IllegalArgumentException")
    void testVerify_NullPlatform_ThrowsException() {
        String payload = "test payload";
        String signature = "some-signature";
        String secret = "some-secret";

        assertThatThrownBy(() -> verificationChain.verify(null, payload, signature, secret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All parameters must be non-null");
    }

    @Test
    @DisplayName("verify - null payload should throw IllegalArgumentException")
    void testVerify_NullPayload_ThrowsException() {
        assertThatThrownBy(() -> verificationChain.verify("github", null, "sig", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All parameters must be non-null");
    }

    @Test
    @DisplayName("verify - null signature should throw IllegalArgumentException")
    void testVerify_NullSignature_ThrowsException() {
        assertThatThrownBy(() -> verificationChain.verify("github", "payload", null, "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All parameters must be non-null");
    }

    @Test
    @DisplayName("verify - null secret should throw IllegalArgumentException")
    void testVerify_NullSecret_ThrowsException() {
        assertThatThrownBy(() -> verificationChain.verify("github", "payload", "sig", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All parameters must be non-null");
    }

    @Test
    @DisplayName("initialization - empty verifier list should create empty chain")
    void testInitialization_EmptyList() {
        WebhookVerificationChain emptyChain = new WebhookVerificationChain(Collections.emptyList());

        assertThatThrownBy(() -> emptyChain.verify("github", "payload", "sig", "secret"))
                .isInstanceOf(UnsupportedPlatformException.class);
    }

    @Test
    @DisplayName("initialization - single verifier should work correctly")
    void testInitialization_SingleVerifier() {
        WebhookVerificationChain singleChain = new WebhookVerificationChain(
                Collections.singletonList(githubVerifier)
        );

        boolean result = singleChain.verify("github", "payload", "valid-github-signature", "secret");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify - case-sensitive platform matching")
    void testVerify_CaseSensitivePlatform() {
        // Platform names are case-sensitive
        assertThatThrownBy(() -> verificationChain.verify("GITHUB", "payload", "sig", "secret"))
                .isInstanceOf(UnsupportedPlatformException.class)
                .hasMessage("Platform not supported: GITHUB");
    }

    @Test
    @DisplayName("initialization - duplicate platform should throw IllegalStateException")
    void testInitialization_DuplicatePlatform_ThrowsException() {
        // Create a second GitHub verifier to simulate duplicate platform registration
        WebhookVerifier duplicateGitHubVerifier = new MockGitHubVerifier();

        assertThatThrownBy(() -> new WebhookVerificationChain(
                Arrays.asList(githubVerifier, duplicateGitHubVerifier)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate webhook verifier for platform: github");
    }

    // ========================================
    // Mock Verifier Implementations
    // ========================================

    /**
     * Mock GitHub webhook verifier for testing.
     * Returns true if signature starts with "valid-github-".
     */
    private static class MockGitHubVerifier implements WebhookVerifier {
        @Override
        public boolean verify(String payload, String signature, String secret) {
            if (signature == null) {
                return false;
            }
            // Simple mock logic: valid if signature starts with "valid-github-"
            return signature.startsWith("valid-github-");
        }

        @Override
        public String getPlatform() {
            return "github";
        }
    }

    /**
     * Mock GitLab webhook verifier for testing.
     * Returns true if signature starts with "valid-gitlab-".
     */
    private static class MockGitLabVerifier implements WebhookVerifier {
        @Override
        public boolean verify(String payload, String signature, String secret) {
            if (signature == null) {
                return false;
            }
            // Simple mock logic: valid if signature starts with "valid-gitlab-"
            return signature.startsWith("valid-gitlab-");
        }

        @Override
        public String getPlatform() {
            return "gitlab";
        }
    }
}
