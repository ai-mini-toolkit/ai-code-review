package com.aicodereview.integration.webhook;

import com.aicodereview.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitLab Webhook signature verifier implementing Secret Token validation.
 * <p>
 * This verifier validates GitLab webhook requests by comparing secret tokens
 * using constant-time comparison. GitLab sends webhooks with the token in the
 * {@code X-Gitlab-Token} header as a plain text secret.
 * </p>
 *
 * <h3>GitLab Token Format:</h3>
 * <pre>
 * X-Gitlab-Token: <secret_token>
 * </pre>
 *
 * <h3>Verification Process:</h3>
 * <ol>
 *   <li>Extract token from header (provided by controller as signature parameter)</li>
 *   <li>Compare token with configured secret using constant-time comparison</li>
 *   <li>Return true if tokens match, false otherwise</li>
 * </ol>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Controller is responsible for extracting header and routing to verifier
 * @Autowired
 * private WebhookVerificationChain verificationChain;
 *
 * // 1. Extract token from request header (controller's responsibility)
 * String token = request.getHeader("X-Gitlab-Token");
 * String payload = request.getBody();  // Raw JSON body
 * String secret = project.getWebhookSecret();
 *
 * // 2. Verify token using the chain (routes to GitLabWebhookVerifier)
 * boolean isValid = verificationChain.verify("gitlab", payload, token, secret);
 * if (!isValid) {
 *     return ResponseEntity.status(401).body("Invalid webhook token");
 * }
 * }</pre>
 *
 * <h3>Security Features:</h3>
 * <ul>
 *   <li>Uses {@link CryptoUtils#constantTimeEquals} to prevent timing attacks</li>
 *   <li>Validates all parameters before comparison</li>
 *   <li>Handles null/empty parameters safely</li>
 *   <li>Logs verification results without exposing tokens</li>
 * </ul>
 *
 * @see WebhookVerifier
 * @see WebhookVerificationChain
 * @see <a href="https://docs.gitlab.com/user/project/integrations/webhooks/">GitLab Webhook Documentation</a>
 * @since 2.3.0
 * @author AI Code Review System
 */
@Component
@Slf4j
public class GitLabWebhookVerifier implements WebhookVerifier {

    private static final String PLATFORM_NAME = "gitlab";

    /**
     * Verifies GitLab webhook token using constant-time comparison.
     *
     * @param payload   the raw webhook payload (request body as string)
     * @param signature the token from X-Gitlab-Token header (plain text secret)
     * @param secret    the shared webhook secret configured in GitLab project settings
     * @return {@code true} if token is valid, {@code false} otherwise
     */
    @Override
    public boolean verify(String payload, String signature, String secret) {
        // Validate input parameters
        if (payload == null || signature == null || secret == null) {
            log.warn("GitLab webhook verification failed: null parameter(s) provided - " +
                    "payload={}, signature={}, secret={}",
                    payload != null, signature != null, secret != null);
            return false;
        }

        // Validate non-empty parameters
        if (payload.isBlank() || signature.isBlank() || secret.isBlank()) {
            log.warn("GitLab webhook verification failed: empty or blank parameter(s) provided");
            return false;
        }

        // GitLab uses simple token comparison
        // signature parameter contains the X-Gitlab-Token header value
        // secret is the configured webhook secret token
        // Use constant-time comparison to prevent timing attacks
        boolean isValid = CryptoUtils.constantTimeEquals(secret, signature);

        if (isValid) {
            log.debug("GitLab webhook verification succeeded");
        } else {
            log.warn("GitLab webhook verification failed: token mismatch");
        }

        return isValid;
    }

    /**
     * Returns the platform identifier for routing.
     *
     * @return {@code "gitlab"}
     */
    @Override
    public String getPlatform() {
        return PLATFORM_NAME;
    }
}
