package com.aicodereview.integration.webhook;

/**
 * Webhook signature verification interface for multi-platform support.
 * <p>
 * This interface defines the contract for verifying webhook signatures from different
 * Git platforms (GitHub, GitLab, AWS CodeCommit). Each platform has its own signature
 * mechanism, and implementations handle platform-specific verification logic.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Component
 * public class GitHubWebhookVerifier implements WebhookVerifier {
 *     @Override
 *     public boolean verify(String payload, String signature, String secret) {
 *         // Implement HMAC-SHA256 verification for GitHub
 *         String expectedSignature = computeHmacSha256(payload, secret);
 *         return CryptoUtils.constantTimeEquals(signature, expectedSignature);
 *     }
 *
 *     @Override
 *     public String getPlatform() {
 *         return "github";
 *     }
 * }
 * }</pre>
 *
 * <h3>Security Requirements:</h3>
 * <ul>
 *   <li>Signature verification MUST occur before payload parsing</li>
 *   <li>Use constant-time comparison to prevent timing attacks</li>
 *   <li>Return false on verification failure (do not throw exceptions)</li>
 *   <li>Do not log sensitive data (secrets, signatures)</li>
 * </ul>
 *
 * <h3>Supported Platforms:</h3>
 * <ul>
 *   <li><b>github</b>: HMAC-SHA256 with X-Hub-Signature-256 header</li>
 *   <li><b>gitlab</b>: Secret token comparison with X-Gitlab-Token header</li>
 *   <li><b>codecommit</b>: AWS Signature Version 4 verification</li>
 * </ul>
 *
 * @see com.aicodereview.integration.webhook.WebhookVerificationChain
 * @see com.aicodereview.common.util.CryptoUtils#constantTimeEquals(String, String)
 * @since 2.1.0
 * @author AI Code Review System
 */
public interface WebhookVerifier {

    /**
     * Verifies the webhook signature against the payload and secret.
     * <p>
     * This method performs platform-specific signature verification to ensure
     * the webhook request is authentic and has not been tampered with.
     * </p>
     *
     * <h4>Implementation Guidelines:</h4>
     * <ul>
     *   <li>Use {@link com.aicodereview.common.util.CryptoUtils#constantTimeEquals}
     *       for signature comparison to prevent timing attacks</li>
     *   <li>Handle null inputs gracefully (return false, do not throw NPE)</li>
     *   <li>Log verification failures at WARN level without exposing secrets</li>
     *   <li>Return false on verification failure (do not throw exceptions)</li>
     * </ul>
     *
     * @param payload   the raw webhook payload (request body as string)
     * @param signature the signature from webhook request header
     * @param secret    the shared secret configured for this webhook
     * @return {@code true} if signature is valid, {@code false} otherwise
     */
    boolean verify(String payload, String signature, String secret);

    /**
     * Returns the platform identifier for this verifier.
     * <p>
     * The platform name is used by {@link WebhookVerificationChain} to route
     * verification requests to the correct verifier implementation.
     * </p>
     *
     * <h4>Standard Platform Names:</h4>
     * <ul>
     *   <li>"github" - GitHub webhooks</li>
     *   <li>"gitlab" - GitLab webhooks</li>
     *   <li>"codecommit" - AWS CodeCommit webhooks</li>
     * </ul>
     *
     * @return the platform identifier (lowercase, e.g., "github", "gitlab", "codecommit")
     */
    String getPlatform();
}
