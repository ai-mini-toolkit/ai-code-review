package com.aicodereview.common.dto.project;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing project. All fields are optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequest {

    @Size(max = 255, message = "Project name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private Boolean enabled;

    @Pattern(regexp = "^(GitHub|GitLab|CodeCommit)$", message = "Git platform must be GitHub, GitLab, or CodeCommit")
    private String gitPlatform;

    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    private String repoUrl;

    private String webhookSecret;
}
