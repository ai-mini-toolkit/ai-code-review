package com.aicodereview.integration.webhook;

import com.aicodereview.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * GitHub Webhook signature verifier implementing HMAC-SHA256 validation.
 * <p>
 * This verifier validates GitHub webhook requests by computing and comparing
 * HMAC-SHA256 signatures using the webhook secret. GitHub sends webhooks with
 * the signature in the {@code X-Hub-Signature-256} header in the format:
 * {@code sha256=<64-character-hex-digest>}
 * </p>
 *
 * <h3>GitHub Signature Format:</h3>
 * <pre>
 * X-Hub-Signature-256: sha256=<hex_digest>
 * </pre>
 *
 * <h3>Verification Process:</h3>
 * <ol>
 *   <li>Extract signature from header (format: "sha256=<hex>")</li>
 *   <li>Compute HMAC-SHA256(payload, secret)</li>
 *   <li>Format as "sha256=<hex>"</li>
 *   <li>Use constant-time comparison to prevent timing attacks</li>
 *   <li>Return true if signatures match, false otherwise</li>
 * </ol>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Autowired
 * private WebhookVerificationChain verificationChain;
 *
 * String platform = "github";
 * String payload = request.getBody();  // Raw JSON body
 * String signature = request.getHeader("X-Hub-Signature-256");
 * String secret = project.getWebhookSecret();
 *
 * boolean isValid = verificationChain.verify(platform, payload, signature, secret);
 * if (!isValid) {
 *     return ResponseEntity.status(401).body("Invalid webhook signature");
 * }
 * }</pre>
 *
 * <h3>Security Features:</h3>
 * <ul>
 *   <li>Uses {@link CryptoUtils#constantTimeEquals} to prevent timing attacks</li>
 *   <li>Validates signature format before computation</li>
 *   <li>Handles null/empty parameters safely</li>
 *   <li>Logs verification results without exposing secrets</li>
 * </ul>
 *
 * @see WebhookVerifier
 * @see WebhookVerificationChain
 * @see <a href="https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries">GitHub Webhook Security</a>
 * @since 2.2.0
 * @author AI Code Review System
 */
@Component
@Slf4j
public class GitHubWebhookVerifier implements WebhookVerifier {

    private static final String PLATFORM_NAME = "github";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Verifies GitHub webhook signature using HMAC-SHA256.
     *
     * @param payload   the raw webhook payload (request body as string)
     * @param signature the signature from X-Hub-Signature-256 header (format: "sha256=<hex>")
     * @param secret    the shared webhook secret configured in GitHub repository settings
     * @return {@code true} if signature is valid, {@code false} otherwise
     */
    @Override
    public boolean verify(String payload, String signature, String secret) {
        // Validate input parameters
        if (payload == null || signature == null || secret == null) {
            log.warn("GitHub webhook verification called with null parameter(s): " +
                    "payload={}, signature={}, secret={}",
                    payload != null, signature != null, secret != null);
            return false;
        }

        // Validate signature format
        if (signature.isEmpty() || !signature.startsWith(SIGNATURE_PREFIX)) {
            log.warn("GitHub webhook signature has invalid format: expected 'sha256=<hex>', got prefix: {}",
                    signature.isEmpty() ? "(empty)" : signature.substring(0, Math.min(7, signature.length())));
            return false;
        }

        // Compute expected signature
        String expectedSignature = computeHmacSha256(payload, secret);
        if (expectedSignature == null) {
            log.error("Failed to compute HMAC-SHA256 for GitHub webhook verification");
            return false;
        }

        // Use constant-time comparison to prevent timing attacks
        boolean isValid = CryptoUtils.constantTimeEquals(expectedSignature, signature);

        if (isValid) {
            log.info("GitHub webhook verification succeeded");
        } else {
            log.warn("GitHub webhook verification failed: signature mismatch");
        }

        return isValid;
    }

    /**
     * Returns the platform identifier for routing.
     *
     * @return {@code "github"}
     */
    @Override
    public String getPlatform() {
        return PLATFORM_NAME;
    }

    /**
     * Computes HMAC-SHA256 signature in GitHub format.
     * <p>
     * Computes {@code HMAC-SHA256(payload, secret)} and formats the result
     * as {@code "sha256=<lowercase-hex-digest>"} to match GitHub's signature format.
     * </p>
     *
     * @param payload the payload to sign
     * @param secret  the HMAC secret key
     * @return the signature in format "sha256=<hex>", or {@code null} if computation fails
     */
    private String computeHmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert to lowercase hexadecimal string (GitHub uses lowercase)
            String hex = bytesToHex(hmacBytes);
            return SIGNATURE_PREFIX + hex;
        } catch (Exception e) {
            log.error("Failed to compute HMAC-SHA256: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return lowercase hexadecimal representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
