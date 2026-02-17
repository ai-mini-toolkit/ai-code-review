package com.aicodereview.common.dto.checkrun;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the output section of a GitHub Check Run.
 * <p>
 * Contains the title, summary (Markdown), and optional detailed text
 * displayed on the GitHub Check Run details page.
 * </p>
 *
 * @since 6.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRunOutputDTO {

    /**
     * Short title for the check run output (e.g., "AI Code Review — Passed").
     */
    private String title;

    /**
     * Markdown-formatted summary with issue statistics and violation details.
     * Max 65535 characters per GitHub API.
     */
    private String summary;

    /**
     * Optional detailed text (additional context beyond the summary).
     * Max 65535 characters per GitHub API.
     */
    private String text;
}
