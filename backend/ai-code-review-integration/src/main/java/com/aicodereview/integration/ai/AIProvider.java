package com.aicodereview.integration.ai;

import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;

/**
 * Interface for AI code review providers.
 * <p>
 * Implementations wrap specific AI APIs (OpenAI, Anthropic, etc.)
 * and translate between our domain model and the provider's API format.
 * </p>
 *
 * @since 4.1.0
 */
public interface AIProvider {

    /**
     * Analyzes code using this AI provider.
     *
     * @param context        the assembled code context (rawDiff, files, statistics)
     * @param renderedPrompt the fully rendered prompt string (template already resolved)
     * @return structured review result with issues and metadata
     */
    ReviewResult analyze(CodeContext context, String renderedPrompt);

    /**
     * Checks if this provider is currently available (configured and reachable).
     *
     * @return true if the provider can accept requests
     */
    boolean isAvailable();

    /**
     * Returns the unique provider identifier (e.g., "openai", "anthropic").
     *
     * @return the provider ID string
     */
    String getProviderId();

    /**
     * Returns the maximum token limit for this provider's model.
     *
     * @return the max token count
     */
    int getMaxTokens();
}
