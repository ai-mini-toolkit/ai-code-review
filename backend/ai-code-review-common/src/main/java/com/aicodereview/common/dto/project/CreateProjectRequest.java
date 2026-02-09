package com.aicodereview.common.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 255, message = "Project name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private Boolean enabled;

    @NotBlank(message = "Git platform is required")
    @Pattern(regexp = "^(GitHub|GitLab|CodeCommit)$", message = "Git platform must be GitHub, GitLab, or CodeCommit")
    private String gitPlatform;

    @NotBlank(message = "Repository URL is required")
    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    private String repoUrl;

    @NotBlank(message = "Webhook secret is required")
    private String webhookSecret;
}
