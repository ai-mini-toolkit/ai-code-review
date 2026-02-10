package com.aicodereview.common.dto.prompttemplate;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new prompt template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePromptTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "^(security|performance|maintainability|correctness|style|best_practices)$",
            message = "Category must be one of: security, performance, maintainability, correctness, style, best_practices")
    private String category;

    @NotBlank(message = "Template content is required")
    @Size(max = 10000, message = "Template content must not exceed 10000 characters")
    private String templateContent;

    @Min(value = 1, message = "Version must be at least 1")
    private Integer version;

    private Boolean enabled;
}
