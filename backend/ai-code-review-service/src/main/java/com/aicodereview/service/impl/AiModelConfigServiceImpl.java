package com.aicodereview.service.impl;

import com.aicodereview.common.dto.aimodel.AiModelConfigDTO;
import com.aicodereview.common.dto.aimodel.CreateAiModelRequest;
import com.aicodereview.common.dto.aimodel.TestConnectionResponse;
import com.aicodereview.common.dto.aimodel.UpdateAiModelRequest;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.repository.AiModelConfigRepository;
import com.aicodereview.repository.entity.AiModelConfig;
import com.aicodereview.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AiModelConfigService for AI model configuration CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private final AiModelConfigRepository aiModelConfigRepository;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "::1", "0.0.0.0", "169.254.169.254"
    );

    @Override
    public AiModelConfigDTO createAiModel(CreateAiModelRequest request) {
        log.info("Creating AI model config: {}", request.getName());

        aiModelConfigRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new DuplicateResourceException("AiModelConfig", "name", request.getName());
        });

        AiModelConfig config = AiModelConfig.builder()
                .name(request.getName())
                .provider(request.getProvider())
                .modelName(request.getModelName())
                .apiKey(request.getApiKey())
                .apiEndpoint(request.getApiEndpoint())
                .temperature(request.getTemperature() != null ? request.getTemperature() : new BigDecimal("0.30"))
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 4000)
                .timeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        AiModelConfig saved = aiModelConfigRepository.save(config);
        log.info("AI model config created with id: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiModelConfigDTO> listAiModels(Boolean enabled, String provider) {
        List<AiModelConfig> configs;
        if (provider != null && enabled != null) {
            log.debug("Listing AI models with provider={} and enabled={}", provider, enabled);
            configs = aiModelConfigRepository.findByProviderAndEnabled(provider, enabled);
        } else if (provider != null) {
            log.debug("Listing AI models with provider={}", provider);
            configs = aiModelConfigRepository.findByProvider(provider);
        } else if (enabled != null) {
            log.debug("Listing AI models with enabled={}", enabled);
            configs = aiModelConfigRepository.findByEnabled(enabled);
        } else {
            log.debug("Listing all AI models");
            configs = aiModelConfigRepository.findAll();
        }
        return configs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ai-models", key = "#p0")
    public AiModelConfigDTO getAiModelById(Long id) {
        log.debug("Getting AI model config by id: {}", id);
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AiModelConfig", id));
        return toDTO(config);
    }

    @Override
    @CacheEvict(value = "ai-models", key = "#p0")
    public AiModelConfigDTO updateAiModel(Long id, UpdateAiModelRequest request) {
        log.info("Updating AI model config id: {}", id);
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AiModelConfig", id));

        if (request.getName() != null && !request.getName().equals(config.getName())) {
            aiModelConfigRepository.findByName(request.getName()).ifPresent(existing -> {
                throw new DuplicateResourceException("AiModelConfig", "name", request.getName());
            });
            config.setName(request.getName());
        }

        if (request.getProvider() != null) {
            config.setProvider(request.getProvider());
        }
        if (request.getModelName() != null) {
            config.setModelName(request.getModelName());
        }
        if (request.getApiKey() != null) {
            config.setApiKey(request.getApiKey());
        }
        if (request.getApiEndpoint() != null) {
            config.setApiEndpoint(request.getApiEndpoint());
        }
        if (request.getTemperature() != null) {
            config.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            config.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTimeoutSeconds() != null) {
            config.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }

        AiModelConfig saved = aiModelConfigRepository.save(config);
        log.info("AI model config updated: {}", saved.getId());
        return toDTO(saved);
    }

    @Override
    @CacheEvict(value = "ai-models", key = "#p0")
    public void deleteAiModel(Long id) {
        log.info("Deleting AI model config id: {}", id);
        if (!aiModelConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("AiModelConfig", id);
        }
        aiModelConfigRepository.deleteById(id);
        log.info("AI model config deleted: {}", id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TestConnectionResponse testConnection(Long id) {
        log.info("Testing connection for AI model config id: {}", id);
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AiModelConfig", id));

        if (config.getApiEndpoint() == null || config.getApiEndpoint().isEmpty()) {
            return new TestConnectionResponse(false, "API endpoint not configured", null);
        }

        // Basic SSRF protection: block requests to internal/private addresses
        if (isBlockedEndpoint(config.getApiEndpoint())) {
            log.warn("Blocked connection test to internal address: {}", config.getApiEndpoint());
            return new TestConnectionResponse(false, "Connection test to internal addresses is not allowed", null);
        }

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiEndpoint()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - startTime;

            boolean success = response.statusCode() < 500;
            return new TestConnectionResponse(success,
                    "HTTP " + response.statusCode() + " - " + (success ? "Reachable" : "Server error"),
                    elapsed);
        } catch (HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("Connection test timed out for AI model config id {}: {}", id, e.getMessage());
            return new TestConnectionResponse(false, "Connection timed out", elapsed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("Connection test interrupted for AI model config id {}", id);
            return new TestConnectionResponse(false, "Connection test interrupted", elapsed);
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("Connection test failed for AI model config id {}: {}", id, e.getMessage());
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage(), elapsed);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid API endpoint URL for AI model config id {}: {}", id, e.getMessage());
            return new TestConnectionResponse(false, "Invalid API endpoint URL", null);
        }
    }

    private boolean isBlockedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null) {
                return true;
            }
            String lowerHost = host.toLowerCase();
            if (BLOCKED_HOSTS.contains(lowerHost)) {
                return true;
            }
            // Block private IP ranges: 10.x.x.x, 172.16-31.x.x, 192.168.x.x
            return lowerHost.matches("^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.).*");
        } catch (Exception e) {
            return true;
        }
    }

    private AiModelConfigDTO toDTO(AiModelConfig config) {
        return AiModelConfigDTO.builder()
                .id(config.getId())
                .name(config.getName())
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .apiEndpoint(config.getApiEndpoint())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeoutSeconds(config.getTimeoutSeconds())
                .enabled(config.getEnabled())
                .apiKeyConfigured(config.getApiKey() != null && !config.getApiKey().isEmpty())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
