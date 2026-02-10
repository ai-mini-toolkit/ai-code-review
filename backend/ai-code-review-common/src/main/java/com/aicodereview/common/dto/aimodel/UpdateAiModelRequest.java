package com.aicodereview.common.dto.aimodel;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for updating an existing AI model configuration. All fields are optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAiModelRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Pattern(regexp = "^(openai|anthropic|custom)$", message = "Provider must be openai, anthropic, or custom")
    private String provider;

    @Size(max = 100, message = "Model name must not exceed 100 characters")
    private String modelName;

    private String apiKey;

    @Size(max = 500, message = "API endpoint must not exceed 500 characters")
    @Pattern(regexp = "^https?://.+", message = "API endpoint must start with http:// or https://")
    private String apiEndpoint;

    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Temperature must not exceed 2.0")
    private BigDecimal temperature;

    @Min(value = 1, message = "Max tokens must be at least 1")
    @Max(value = 128000, message = "Max tokens must not exceed 128000")
    private Integer maxTokens;

    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 300, message = "Timeout must not exceed 300 seconds")
    private Integer timeoutSeconds;

    private Boolean enabled;
}
