package com.aicodereview.integration.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;

/**
 * AWS CodeCommit Webhook signature verifier implementing SNS message validation.
 * <p>
 * This verifier validates AWS CodeCommit webhook requests delivered through Amazon SNS.
 * CodeCommit sends webhook events via SNS topics, so we verify SNS message signatures
 * rather than direct CodeCommit signatures.
 * </p>
 *
 * <h3>AWS SNS Message Structure:</h3>
 * <pre>
 * {
 *   "Type": "Notification",
 *   "MessageId": "uuid",
 *   "TopicArn": "arn:aws:sns:region:account:topic",
 *   "Message": "{...CodeCommit event...}",
 *   "Timestamp": "2026-01-15T10:00:00.000Z",
 *   "SignatureVersion": "1",
 *   "Signature": "base64-encoded",
 *   "SigningCertURL": "https://sns.region.amazonaws.com/cert.pem"
 * }
 * </pre>
 *
 * <h3>Verification Process:</h3>
 * <ol>
 *   <li>Parse SNS JSON message</li>
 *   <li>Validate required fields exist</li>
 *   <li>Verify SigningCertURL is from legitimate AWS domain</li>
 *   <li>Validate message Type (Notification, SubscriptionConfirmation, etc.)</li>
 *   <li>Download X.509 certificate from SigningCertURL</li>
 *   <li>Construct canonical string based on message Type</li>
 *   <li>Verify signature using public key (SHA1withRSA or SHA256withRSA)</li>
 * </ol>
 *
 * <h3>Security Considerations:</h3>
 * <ul>
 *   <li>SigningCertURL MUST be from *.amazonaws.com or *.amazonaws.com.cn</li>
 *   <li>Certificate validation prevents SSRF attacks</li>
 *   <li>Only supported message types are processed</li>
 *   <li>Signature verification uses cryptographically secure algorithms</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This implementation validates SNS message structure and
 * certificate URL. Full signature verification requires certificate download and
 * cryptographic operations, which is a complex process. For production use, consider
 * using AWS SDK SNS message validation utilities or implementing full verification
 * with certificate caching.</p>
 *
 * @see WebhookVerifier
 * @see WebhookVerificationChain
 * @see <a href="https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html">AWS SNS Signature Verification</a>
 * @since 2.3.0
 * @author AI Code Review System
 */
@Component
@Slf4j
public class AWSCodeCommitWebhookVerifier implements WebhookVerifier {

    private static final String PLATFORM_NAME = "codecommit";
    private static final String[] VALID_CERT_DOMAINS = {
        ".amazonaws.com",
        ".amazonaws.com.cn"
    };
    private static final String[] SUPPORTED_MESSAGE_TYPES = {
        "Notification",
        "SubscriptionConfirmation",
        "UnsubscribeConfirmation"
    };

    // ObjectMapper is thread-safe and can be reused
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Verifies AWS CodeCommit webhook signature delivered through SNS.
     *
     * @param payload   the raw SNS message payload (JSON string)
     * @param signature not used for SNS (signature is embedded in payload JSON)
     * @param secret    not used for SNS (uses public key verification, not shared secret)
     * @return {@code true} if SNS message signature is valid, {@code false} otherwise
     */
    @Override
    public boolean verify(String payload, String signature, String secret) {
        // Validate input parameters
        if (payload == null || signature == null) {
            log.warn("AWS CodeCommit webhook verification failed: null parameter(s) provided - " +
                    "payload={}, signature={}",
                    payload != null, signature != null);
            return false;
        }

        // Validate non-empty parameters
        if (payload.isBlank() || signature.isBlank()) {
            log.warn("AWS CodeCommit webhook verification failed: empty or blank parameter(s) provided");
            return false;
        }

        try {
            // Parse SNS message JSON
            JsonNode snsMessage = OBJECT_MAPPER.readTree(payload);

            // Validate required fields exist
            if (!snsMessage.has("Type") || !snsMessage.has("SigningCertURL")) {
                log.warn("AWS CodeCommit webhook verification failed: missing required fields (Type or SigningCertURL)");
                return false;
            }

            // Extract message type
            String messageType = snsMessage.get("Type").asText();

            // Validate message type is supported
            if (!isSupportedMessageType(messageType)) {
                log.warn("AWS CodeCommit webhook verification failed: unsupported message type: {}", messageType);
                return false;
            }

            // Extract signing certificate URL
            String signingCertURL = snsMessage.get("SigningCertURL").asText();

            // Validate certificate URL is from legitimate AWS domain (security critical)
            if (!isValidAWSCertURL(signingCertURL)) {
                log.warn("AWS CodeCommit webhook verification failed: invalid certificate URL domain: {}",
                        maskSensitiveURL(signingCertURL));
                return false;
            }

            // TODO: Implement full SNS signature verification
            // This would require:
            // 1. Download certificate from SigningCertURL (with caching)
            // 2. Parse X.509 certificate
            // 3. Construct canonical string based on message Type
            // 4. Extract Signature and SignatureVersion from message
            // 5. Verify signature using public key (SHA1withRSA or SHA256withRSA)
            //
            // For now, we validate the message structure and certificate URL.
            // Full implementation can use AWS SDK SNS utilities or manual crypto operations.

            log.debug("AWS CodeCommit webhook message structure validated (signature verification not yet implemented)");

            // Return false for now - full signature verification not implemented
            // This ensures webhook requests are rejected until full verification is complete
            return false;

        } catch (Exception e) {
            log.error("AWS CodeCommit webhook verification error: {}", e.getMessage());
            log.debug("AWS CodeCommit webhook verification exception details", e);
            return false;
        }
    }

    /**
     * Returns the platform identifier for routing.
     *
     * @return {@code "codecommit"}
     */
    @Override
    public String getPlatform() {
        return PLATFORM_NAME;
    }

    /**
     * Validates that the signing certificate URL is from a legitimate AWS domain.
     * <p>
     * This prevents SSRF attacks by ensuring certificates are only downloaded
     * from trusted AWS domains.
     * </p>
     *
     * @param certURL the certificate URL from SNS message
     * @return {@code true} if URL is from valid AWS domain, {@code false} otherwise
     */
    private boolean isValidAWSCertURL(String certURL) {
        if (certURL == null || certURL.isBlank()) {
            return false;
        }

        try {
            URL url = new URL(certURL);
            String host = url.getHost();

            // Check if host ends with valid AWS domain
            for (String validDomain : VALID_CERT_DOMAINS) {
                if (host.endsWith(validDomain)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.debug("Invalid certificate URL format: {}", certURL, e);
            return false;
        }
    }

    /**
     * Checks if the message type is supported for processing.
     *
     * @param messageType the Type field from SNS message
     * @return {@code true} if message type is supported, {@code false} otherwise
     */
    private boolean isSupportedMessageType(String messageType) {
        if (messageType == null) {
            return false;
        }

        for (String supportedType : SUPPORTED_MESSAGE_TYPES) {
            if (supportedType.equals(messageType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Masks sensitive parts of URL for logging.
     *
     * @param url the URL to mask
     * @return masked URL string
     */
    private String maskSensitiveURL(String url) {
        if (url == null || url.length() <= 20) {
            return "[REDACTED]";
        }
        return url.substring(0, 20) + "...[REDACTED]";
    }
}
