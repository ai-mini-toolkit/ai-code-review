package com.aicodereview.repository.entity;

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
 * Entity representing an AI code review result.
 * <p>
 * Each result has a 1:1 relationship with a ReviewTask. JSONB columns
 * (issues, statistics, metadata) are stored as String and serialized/deserialized
 * via Jackson ObjectMapper in the service layer.
 * </p>
 *
 * @since 5.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "review_result")
@EntityListeners(AuditingEntityListener.class)
public class ReviewResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Associated review task (1:1 relationship).
     * Lazy-loaded to avoid unnecessary joins.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "fk_review_result_task"))
    private ReviewTask reviewTask;

    /**
     * JSONB array of ReviewIssue objects serialized as JSON string.
     */
    @Column(name = "issues", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String issues = "[]";

    /**
     * JSONB object with aggregated issue counts by severity and category.
     */
    @Column(name = "statistics", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String statistics = "{}";

    /**
     * JSONB object with review execution metadata (provider, model, tokens, duration).
     */
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String metadata = "{}";

    /**
     * Whether the AI review completed successfully.
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    /**
     * Error message if review failed (nullable).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
