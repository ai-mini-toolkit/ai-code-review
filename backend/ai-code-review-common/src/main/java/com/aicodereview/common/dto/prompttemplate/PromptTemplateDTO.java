package com.aicodereview.common.dto.prompttemplate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for prompt template data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateDTO {

    private Long id;
    private String name;
    private String category;
    private String templateContent;
    private Integer version;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
