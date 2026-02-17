package com.aicodereview.common.dto.project;

import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for Project entity.
 * Note: webhook_secret is never exposed; only webhookSecretConfigured boolean is returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {

    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    private String gitPlatform;
    private String repoUrl;
    private Boolean webhookSecretConfigured;
    private ThresholdConfigDTO thresholds;
    private Instant createdAt;
    private Instant updatedAt;
}
