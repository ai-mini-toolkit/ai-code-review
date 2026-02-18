package com.aicodereview.common.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for notification configuration data transfer.
 *
 * @since 7.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationConfigDTO {

    private Long id;
    private Long projectId;
    private Boolean emailEnabled;
    private String emailRecipients;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    // Note: smtpPassword is intentionally excluded from DTO for security
    private Instant createdAt;
    private Instant updatedAt;
}
