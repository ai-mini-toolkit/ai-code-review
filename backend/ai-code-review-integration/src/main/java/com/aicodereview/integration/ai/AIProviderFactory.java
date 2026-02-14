package com.aicodereview.integration.ai;

import com.aicodereview.common.exception.UnsupportedPlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting AI providers by ID.
 * <p>
 * Follows the same pattern as {@link com.aicodereview.integration.git.GitPlatformClientFactory}:
 * constructor injection via {@code List<AIProvider>}, internal Map-based lookup.
 * </p>
 *
 * @since 4.1.0
 */
@Component
@Slf4j
public class AIProviderFactory {

    private final Map<String, AIProvider> providerMap;
    private final String defaultProviderId;

    public AIProviderFactory(List<AIProvider> providers,
                             @Value("${ai.provider.default:openai}") String defaultProviderId) {
        this.defaultProviderId = defaultProviderId;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        AIProvider::getProviderId,
                        Function.identity(),
                        (existing, duplicate) -> {
                            log.error("Duplicate AI provider ID '{}': existing={}, duplicate={}. Keeping existing.",
                                    existing.getProviderId(),
                                    existing.getClass().getSimpleName(),
                                    duplicate.getClass().getSimpleName());
                            throw new IllegalStateException(
                                    "Duplicate AI provider registered with id: " + existing.getProviderId());
                        }
                ));
        log.info("Initialized AI provider factory with {} provider(s): {}",
                providerMap.size(), providerMap.keySet());
    }

    /**
     * Gets an AI provider by its unique identifier.
     *
     * @param providerId the provider ID (e.g., "openai", "anthropic")
     * @return the matching AI provider
     * @throws UnsupportedPlatformException if no provider is registered with the given ID
     */
    public AIProvider getProvider(String providerId) {
        AIProvider provider = providerMap.get(providerId);
        if (provider == null) {
            throw new UnsupportedPlatformException("No AI provider registered with id: " + providerId);
        }
        return provider;
    }

    /**
     * Gets the default AI provider as configured by {@code ai.provider.default}.
     *
     * @return the default AI provider
     * @throws UnsupportedPlatformException if the default provider is not registered
     */
    public AIProvider getDefaultProvider() {
        return getProvider(defaultProviderId);
    }
}
