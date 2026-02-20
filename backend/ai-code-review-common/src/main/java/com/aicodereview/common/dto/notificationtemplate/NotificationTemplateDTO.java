package com.aicodereview.common.dto.notificationtemplate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for notification template data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateDTO {

    private Long id;
    private String name;
    private String channel;
    private String templateContent;
    private String variables;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
