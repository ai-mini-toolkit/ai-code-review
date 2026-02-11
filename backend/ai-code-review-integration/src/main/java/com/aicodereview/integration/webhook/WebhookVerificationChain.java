package com.aicodereview.integration.webhook;

import com.aicodereview.common.exception.UnsupportedPlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chain of Responsibility pattern implementation for webhook signature verification.
 * <p>
 * This class manages all registered {@link WebhookVerifier} implementations and routes
 * verification requests to the appropriate verifier based on the platform identifier.
 * </p>
 *
 * <h3>Architecture Pattern:</h3>
 * Uses the <b>Chain of Responsibility</b> pattern to delegate verification to platform-specific
 * verifiers. New platform support can be added by simply creating a new {@link WebhookVerifier}
 * implementation with {@code @Component} annotation - no changes to this class required.
 *
 * <h3>Spring Integration:</h3>
 * Spring automatically injects all {@link WebhookVerifier} beans via constructor injection.
 * The chain builds a platform → verifier mapping during initialization.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Autowired
 * private WebhookVerificationChain verificationChain;
 *
 * boolean isValid = verificationChain.verify(
 *     "github",                         // platform
 *     requestBody,                      // payload
 *     request.getHeader("X-Hub-Signature-256"), // signature
 *     project.getWebhookSecret()        // secret
 * );
 *
 * if (!isValid) {
 *     return ResponseEntity.status(401).body("Invalid webhook signature");
 * }
 * }</pre>
 *
 * <h3>Security Considerations:</h3>
 * <ul>
 *   <li>Signature verification occurs BEFORE payload parsing (security requirement)</li>
 *   <li>Logs do not contain sensitive data (secrets, signatures)</li>
 *   <li>Returns false on verification failure (caller decides HTTP response)</li>
 *   <li>Throws exception for unsupported platforms (mapped to HTTP 400)</li>
 * </ul>
 *
 * @see WebhookVerifier
 * @see UnsupportedPlatformException
 * @since 2.1.0
 * @author AI Code Review System
 */
@Component
@Slf4j
public class WebhookVerificationChain {

    private final Map<String, WebhookVerifier> verifierMap;

    /**
     * Constructs the verification chain with all registered verifiers.
     * <p>
     * Spring automatically injects all {@link WebhookVerifier} beans into the list.
     * The constructor builds a map for O(1) lookup by platform name.
     * </p>
     *
     * @param verifiers list of all WebhookVerifier implementations (Spring-injected)
     * @throws IllegalStateException if duplicate platform verifiers are detected
     */
    public WebhookVerificationChain(List<WebhookVerifier> verifiers) {
        this.verifierMap = verifiers.stream()
                .collect(Collectors.toMap(
                        WebhookVerifier::getPlatform,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.error("Duplicate webhook verifier detected for platform: {}. " +
                                     "Existing: {}, Replacement: {}. This is a configuration error.",
                                     existing.getPlatform(),
                                     existing.getClass().getSimpleName(),
                                     replacement.getClass().getSimpleName());
                            throw new IllegalStateException(
                                "Duplicate webhook verifier for platform: " + existing.getPlatform() +
                                ". Found: " + existing.getClass().getName() +
                                " and " + replacement.getClass().getName());
                        }
                ));
        log.info("Initialized webhook verification chain with {} platform(s)",
                verifierMap.size());
        log.debug("Registered platforms: {}", verifierMap.keySet());
    }

    /**
     * Verifies webhook signature using the appropriate platform-specific verifier.
     * <p>
     * This method routes the verification request to the correct verifier based on
     * the platform parameter. If no verifier is registered for the platform,
     * it throws {@link UnsupportedPlatformException}.
     * </p>
     *
     * <h4>Verification Flow:</h4>
     * <ol>
     *   <li>Validate input parameters (non-null)</li>
     *   <li>Look up verifier by platform name</li>
     *   <li>Throw exception if platform not supported</li>
     *   <li>Delegate verification to platform-specific verifier</li>
     *   <li>Log result (success/failure) without exposing secrets</li>
     *   <li>Return verification result</li>
     * </ol>
     *
     * @param platform  the platform identifier (e.g., "github", "gitlab", "codecommit")
     * @param payload   the raw webhook payload (request body as string)
     * @param signature the signature from webhook request header
     * @param secret    the shared secret configured for this webhook
     * @return {@code true} if signature is valid, {@code false} otherwise
     * @throws UnsupportedPlatformException if no verifier registered for the platform
     * @throws IllegalArgumentException if any parameter is null
     */
    public boolean verify(String platform, String payload, String signature, String secret) {
        // Validate input parameters
        if (platform == null || payload == null || signature == null || secret == null) {
            log.warn("Webhook verification called with null parameter(s): platform={}, payload={}, signature={}, secret={}",
                    platform != null, payload != null, signature != null, secret != null);
            throw new IllegalArgumentException("All parameters must be non-null for webhook verification");
        }

        WebhookVerifier verifier = verifierMap.get(platform);
        if (verifier == null) {
            log.error("Unsupported webhook platform: {}", platform);
            throw new UnsupportedPlatformException("Platform not supported: " + platform);
        }

        boolean result = verifier.verify(payload, signature, secret);
        if (result) {
            log.info("Webhook verification succeeded for platform: {}", platform);
        } else {
            log.warn("Webhook verification failed for platform: {}", platform);
        }
        return result;
    }
}
