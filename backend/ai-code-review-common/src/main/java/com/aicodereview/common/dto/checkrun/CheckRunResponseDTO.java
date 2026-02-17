package com.aicodereview.common.dto.checkrun;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response from GitHub Check Runs API after creation.
 * <p>
 * Contains the check run ID, HTML URL, and conclusion.
 * </p>
 *
 * @since 6.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRunResponseDTO {

    /**
     * GitHub Check Run ID (for subsequent updates if needed).
     */
    private Long id;

    /**
     * HTML URL to the check run details page on GitHub.
     */
    private String htmlUrl;

    /**
     * Conclusion of the check run: "success", "failure", or "neutral".
     */
    private String conclusion;
}
