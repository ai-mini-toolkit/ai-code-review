package com.aicodereview.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entity representing a notification template for multi-channel notifications.
 * Supports channels: EMAIL, GIT_COMMENT, DINGTALK, SLACK, LARK.
 * Template content uses Mustache/Handlebars format.
 *
 * @since 7.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_template")
@EntityListeners(AuditingEntityListener.class)
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "template_content", columnDefinition = "TEXT", nullable = false)
    private String templateContent;

    /** JSONB documentation of available template variables and their descriptions. May be null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
