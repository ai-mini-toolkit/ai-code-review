package com.aicodereview.repository.entity;

import com.aicodereview.repository.converter.SmtpPasswordConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entity representing per-project notification configuration.
 *
 * @since 7.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_config")
@EntityListeners(AuditingEntityListener.class)
public class NotificationConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = false;

    @Column(name = "email_recipients", length = 1000)
    private String emailRecipients;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Convert(converter = SmtpPasswordConverter.class)
    @Column(name = "smtp_password", length = 500)
    private String smtpPassword;

    @Column(name = "comment_enabled", nullable = false)
    @Builder.Default
    private Boolean commentEnabled = false;

    @Column(name = "dingtalk_enabled", nullable = false)
    @Builder.Default
    private Boolean dingtalkEnabled = false;

    @Column(name = "dingtalk_webhook_url", length = 500)
    private String dingtalkWebhookUrl;

    @Convert(converter = SmtpPasswordConverter.class)
    @Column(name = "dingtalk_secret", length = 500)
    private String dingtalkSecret;

    @Column(name = "slack_enabled", nullable = false)
    @Builder.Default
    private Boolean slackEnabled = false;

    @Column(name = "slack_webhook_url", length = 500)
    private String slackWebhookUrl;

    @Column(name = "lark_enabled", nullable = false)
    @Builder.Default
    private Boolean larkEnabled = false;

    @Column(name = "lark_webhook_url", length = 500)
    private String larkWebhookUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
