package com.aicodereview.common.dto.checkrun;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response from GitLab Commit Status API after creation.
 * <p>
 * Contains the commit status ID, state, and description.
 * </p>
 *
 * @since 6.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitStatusResponseDTO {

    /**
     * GitLab Commit Status ID.
     */
    private Long id;

    /**
     * State of the commit status: "success", "failed", or "pending".
     */
    private String state;

    /**
     * Human-readable description (max 255 characters per GitLab API).
     */
    private String description;
}
