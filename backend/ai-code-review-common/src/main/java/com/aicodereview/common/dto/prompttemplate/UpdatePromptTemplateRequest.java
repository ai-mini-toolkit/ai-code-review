package com.aicodereview.common.dto.prompttemplate;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing prompt template. All fields are optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePromptTemplateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Pattern(regexp = "^(security|performance|maintainability|correctness|style|best_practices)$",
            message = "Category must be one of: security, performance, maintainability, correctness, style, best_practices")
    private String category;

    @Size(max = 10000, message = "Template content must not exceed 10000 characters")
    private String templateContent;

    @Min(value = 1, message = "Version must be at least 1")
    private Integer version;

    private Boolean enabled;
}
