package com.aicodereview.common.dto.aimodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object for AiModelConfig entity.
 * Note: api_key is never exposed; only apiKeyConfigured boolean is returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfigDTO {

    private Long id;
    private String name;
    private String provider;
    private String modelName;
    private String apiEndpoint;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private Boolean enabled;
    private Boolean apiKeyConfigured;
    private Instant createdAt;
    private Instant updatedAt;
}
