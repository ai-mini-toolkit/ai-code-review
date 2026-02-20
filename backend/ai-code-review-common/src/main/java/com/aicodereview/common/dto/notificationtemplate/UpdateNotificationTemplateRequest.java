package com.aicodereview.common.dto.notificationtemplate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing notification template.
 * All fields are optional — only non-null fields will be applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationTemplateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Pattern(
            regexp = "^(EMAIL|GIT_COMMENT|DINGTALK|SLACK|LARK)$",
            message = "Channel must be one of: EMAIL, GIT_COMMENT, DINGTALK, SLACK, LARK"
    )
    private String channel;

    @Size(max = 50000, message = "Template content must not exceed 50000 characters")
    private String templateContent;

    /** Optional JSONB string documenting available template variables. */
    private String variables;

    /** Whether this template is enabled. */
    private Boolean enabled;
}
